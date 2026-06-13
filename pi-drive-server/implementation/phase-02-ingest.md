# Phase 2: Ingest API (the Android contract)

**Goal:** Implement the exact wire contract the Android app already speaks: authenticated `POST /telemetry` accepting single snapshot, bare array, or `{snapshots:[...]}`, optionally zstd/gzip-compressed; auto-register vehicle by VIN; idempotent upsert by `(vin, time)`; insert events; plus `GET /telemetry/latest`. This is the heart of the server.

**Depends on:** Phase 1.

**Reference:** `../REQUIREMENTS.md` §5 (full contract), and **must match** `../pi-drive-android/implementation/phase-07-telemetry.md` "Server API Contract" — do not change paths, headers, or the snake_case payload. Fixes Java bugs #1 (`==`), #2 (long), #6 (concat), #7 (no write confirmation).

---

## Step 2.1 — API-key auth + zstd/gzip body decoding

**What to build:**

1. **`app/auth.py`** — `require_api_key` decorator. Resolves the key from `Authorization: Bearer <key>` (primary) or `X-API-Key`. Constant-time compare against config `api_key`. On failure → `401` JSON `{"error":"..."}`. Log tag `Auth`. Health endpoints stay exempt.
2. **`app/compression.py`** — `read_request_body(request)`:
   - If `Content-Encoding: zstd` → decompress with `zstandard` (streaming decompressor, bounded output).
   - If `gzip` → `gzip` decode.
   - Else raw.
   - Enforce `config.max_body_bytes` on the **decompressed** size → `413` if exceeded (zip-bomb guard).
   - Returns `bytes`; caller does JSON parse.

**Tests:**
- `require_api_key`: valid Bearer → allowed; valid `X-API-Key` → allowed; missing/wrong → 401; health route unaffected.
- `read_request_body`: plain JSON passes through; zstd-compressed round-trips; oversized decompressed → 413.

---

## Step 2.2 — Telemetry Pydantic schemas + write path

**What to build:**

1. **`app/schemas/telemetry.py`** (Pydantic v2, snake_case, `extra="allow"` on the OBD/top level so unknown keys are captured):
   - `LocationIn(lat, lng, speed_gps)` — all optional.
   - `ObdIn(speed_kmh, rpm, coolant_temp_c, intake_air_temp_c, throttle_pct, fuel_level_pct, oil_temp_c, maf_gps, fuel_rate_lph, battery_voltage)` — all optional, **correctly typed** (`rpm: int | None`, rest `float | None`). `model_extra` collects unknown OBD keys → goes to `extra`.
   - `EventIn(strategy, type, timestamp, duration_ms?, rate_mph_s?, peak_g?, peak_accel_mps2?, start_speed_mph?, end_speed_mph?, sources?)`.
   - `TelemetryIn(timestamp, vin, device_id?, location?, obd?, calculated{fuel_economy_mpg?, fuel_economy_kml?}?, accel_mps2?, events: list[EventIn] = [])`. Validator: `vin` non-blank (else 422/400); `timestamp` parses to aware datetime.
   - `BatchIn` parsing helper that accepts single object, bare list, or `{"snapshots":[...]}` and normalizes to `list[TelemetryIn]`.
   - A `to_orm_columns()` mapping flattening `TelemetryIn` → the `telemetry` row dict (nested → flat columns; leftover unknowns → `extra`).
2. **Implement `telemetry_service.upsert_snapshot` and `insert_events`:**
   - `upsert_snapshot`: Postgres `INSERT ... ON CONFLICT (vin, time) DO UPDATE` (idempotent re-upload). Uses parameterized ORM/Core — **never string concatenation**.
   - `insert_events`: bulk insert events for the snapshot.
   - A combined `ingest_batch(items: list[TelemetryIn], *, device_id_header)` that, in **one transaction**: validates `device_id` header vs body agreement (400 on mismatch), upserts each vehicle (`vehicle_service.upsert_vehicle`), upserts each snapshot, inserts events; returns `(accepted_count, set_of_vins)`.

**Tests (`tests/test_telemetry_schema.py` unit; `tests/test_ingest_service.py` integration):**
- Float fields survive as floats (`throttle_pct=22.5` not `22`); `rpm` is int. (Java bug #2 regression guard.)
- Unknown `obd` key → lands in `extra`, not dropped.
- Blank/missing `vin` → validation error.
- `ingest_batch` upsert idempotency: same `(vin, timestamp)` twice → one row, second is update.
- New VIN → vehicle auto-registered with `first_seen`/`last_seen`.
- Batch shapes: single, list, `{snapshots:[]}` all normalize.

---

## Step 2.3 — `POST /telemetry`, `/telemetry/latest`, contract tests

**What to build in `app/api/telemetry.py`** (blueprint, all `@require_api_key` except where noted):

1. **`POST /telemetry`** — read body via `compression.read_request_body`; parse to `BatchIn`; call `telemetry_service.ingest_batch(..., device_id_header=request.headers.get("X-Device-Id"))`; return `200 {"ok": true, "accepted": n, "vehicles": [...]}`. Map errors: validation→`422`, blank vin→`400`, key→`401`, too-large→`413`.
2. **`GET /telemetry/latest?vin=`** — `telemetry_service.latest_timestamp(vin)` → `200 {"vin":..., "latest_timestamp":"..."}` or `404`.
3. Confirm `GET /health` (Phase 0) already satisfies the app's Test button.

**Contract tests (`tests/test_ingest_api.py`, integration — assert the Android contract):**
- POST the exact example payload from `../REQUIREMENTS.md` §5.5 with `Authorization: Bearer <key>` and `X-Device-Id` → 200, `accepted=1`, vehicle + telemetry + 2 events stored.
- POST a 3-item batch (bare array) → `accepted=3`.
- POST the same payload **zstd-compressed** with `Content-Encoding: zstd` → 200, stored once.
- Re-POST identical payload → still one telemetry row (idempotent).
- POST with bad key → 401. POST missing vin → 400. POST malformed JSON → 422.
- `GET /telemetry/latest?vin=...` → returns the stored timestamp; unknown vin → 404.

**Verify (manual):**
```bash
curl -sX POST localhost:8080/telemetry \
  -H "Authorization: Bearer $API_KEY" -H "X-Device-Id: pd-test" \
  -H "Content-Type: application/json" \
  --data @sample_payload.json
# → {"ok":true,"accepted":1,"vehicles":["1G1JC524417100001"]}
curl -s "localhost:8080/telemetry/latest?vin=1G1JC524417100001" -H "Authorization: Bearer $API_KEY"
```

**Estimated size:** ~1.1k lines across the phase.
