# Phase 3: Management & Query REST API

**Goal:** The RESTful management surface the UI (Phase 5) and API users need: vehicles CRUD, telemetry query + single read, telemetry edit/delete, events, and stats. Proper verbs, status codes, and pagination throughout.

**Depends on:** Phase 1 (services), Phase 2 (ingest write path reused for manual entry).

**Reference:** `../REQUIREMENTS.md` §6. Fills the Java server's gaps: read telemetry (#planned), delete car (#commented-out), delete telemetry (#future).

---

## Step 3.1 — Vehicles CRUD

**What to build in `app/api/vehicles.py`** (blueprint `/api/v1/vehicles`, all `@require_api_key`):

1. `GET /api/v1/vehicles` — list with summary (join continuous-aggregate / counts: sample_count, first_seen, last_seen, event_count). Paginated (`limit`, `offset`; response includes `total`, `limit`, `offset`).
2. `GET /api/v1/vehicles/{vin}` — one vehicle + summary; `404` if unknown.
3. `PATCH /api/v1/vehicles/{vin}` — update mutable metadata (`make`, `model`, `year`, `nickname`) via `VehicleUpdate` Pydantic schema; ignore/reject attempts to change `vin`; `200` with updated entity.
4. `DELETE /api/v1/vehicles/{vin}` — requires `?confirm=true` (else `400` explaining the guard); cascades telemetry + events (DB FK `ON DELETE CASCADE`); `204`.

Implement the corresponding `vehicle_service` methods (`list` with summary, `update_metadata`, `delete`) and `app/schemas/vehicle.py`.

**Tests (integration):**
- List paginates; summary counts correct.
- PATCH nickname → persisted; PATCH vin → ignored/400.
- DELETE without confirm → 400; with confirm → 204 and telemetry/events gone.

---

## Step 3.2 — Telemetry query + single read + pagination

**What to build in `app/api/telemetry.py`** (extend the Phase 2 blueprint):

1. `GET /api/v1/telemetry` — query params: `vin` (**required**), `start`, `end` (ISO 8601), `fields` (comma list → column subset), `order` (`asc|desc`, default desc), `limit` (capped, default 100), `offset` or keyset cursor (`before`/`after` time). Returns `{ rows: [...], total?, limit, offset, has_more }`. Reads via `telemetry_service.query(...)` — parameterized, time-bounded (no hardcoded ranges; Java bug #3).
2. `GET /api/v1/telemetry/{vin}/{timestamp}` — one snapshot (timestamp ISO 8601, URL-encoded); `404` if absent.
3. Implement `telemetry_service.query` and `get_one` against the hypertable (leveraging the `(vin, time desc)` index).

**Tests (integration):**
- Insert 200 rows → `GET ?vin=&limit=50` returns 50 + `has_more`; `offset`/cursor walks the rest.
- `start`/`end` filters bound results; `fields=speed_kmh,rpm` returns only those columns (+ key).
- Single read returns exact row; unknown → 404.

---

## Step 3.3 — Telemetry edit/delete + events + stats

**What to build:**

1. **Telemetry mutation** (`app/api/telemetry.py`):
   - `POST /api/v1/telemetry` — manual single-row create; reuses `TelemetryIn` + `telemetry_service.upsert_snapshot` with `source=manual`; `201`.
   - `PATCH /api/v1/telemetry/{vin}/{timestamp}` — partial field update of one snapshot; `200`.
   - `DELETE /api/v1/telemetry/{vin}/{timestamp}` — delete one; `204`.
   - `DELETE /api/v1/telemetry?vin=&start=&end=&confirm=true` — bulk range delete; returns deleted count.
   Implement `telemetry_service.update_one`, `delete_one`, `delete_range`.
2. **Events** (`app/api/events.py`): `GET /api/v1/events?vin=&start=&end=` (paginated); `DELETE /api/v1/events/{id}` → `204`. Implement `event_service` (or fold into telemetry_service) methods.
3. **Stats** (`app/api/stats.py`): `GET /api/v1/stats` (fleet rollup) and `GET /api/v1/stats/{vin}?start=&end=` reading `stats_service` (continuous aggregates): sample_count, avg/max speed, avg MPG, event counts by type, coverage span. Implement `stats_service.fleet_stats` / `vehicle_stats`.

**Tests (integration):**
- Manual POST → row with `source=manual`; PATCH changes a field; DELETE removes it; range delete returns count.
- Events list paginates; delete works.
- Stats match hand-computed values over a small seeded dataset; stats served from the aggregate are consistent with raw `count(*)`.

**Verify:** `curl` each endpoint; confirm status codes and pagination metadata.

**Estimated size:** ~1.3k lines across the phase.
