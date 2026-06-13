# Pi Drive Server — Requirements

> **Audience:** The developer (human or AI) building the Pi Drive telemetry server in Python + Flask.
> **Companion docs:** `SERVER_DEVELOPER_DOCS.md` (post-mortem of the original Java/Spring server — design lessons, what to keep, what to fix) and `implementation/` (the phased build plan). The Android client contract this server must satisfy lives in `../pi-drive-android/REQUIREMENTS.md` §5.4 and `../pi-drive-android/implementation/phase-07-telemetry.md`.

---

## 1. Purpose

Pi Drive Server is the backend for the Pi Drive Android app. The app reads OBD-II data from a vehicle, batches it, and POSTs JSON telemetry snapshots to a configurable server URL. This server's jobs are:

1. **Ingest** telemetry from the Android app (live stream + deferred offline batches), exactly matching the wire contract the app already implements.
2. **Persist** that telemetry as time-series data in one datastore, queryable quickly across a full year.
3. **Manage** the data: a small web UI and a RESTful API for viewing high-level stats, manual data entry, CSV import/export, and manual edit/removal of records.
4. **Operate** cleanly in a container and on Kubernetes, with a health endpoint for liveness/readiness probes and a backup/restore path for the database.

This is a **single-tenant, small-fleet** system: 1–3 vehicles, one shared deployment. It is intentionally minimal — not a fleet-management SaaS — but it is built to be modular so additional services can grow around the same Postgres instance later.

### Non-goals

- No multi-user accounts, RBAC, or per-org isolation. One static API key guards write/management access.
- No real-time websocket/push to dashboards. The UI is server-rendered and refreshed on load.
- No heavy analytics engine. High-level aggregates only; deep analysis can be done later directly against Postgres or a BI tool.

---

## 2. Technology Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Python 3.12 | |
| Web framework | Flask 3.x | App-factory pattern, blueprints |
| WSGI server | Gunicorn | Production; Flask dev server for local only |
| ORM | SQLAlchemy 2.x | Typed `Mapped[]` models |
| Migrations | Alembic | Schema + Timescale hypertable setup |
| Validation | Pydantic v2 | Request/response schemas, decouples wire format from ORM |
| Database | **PostgreSQL 16 + TimescaleDB** | Single datastore. Telemetry is a hypertable; continuous aggregates power the stats UI. |
| Compression | `zstandard` | Decompress `Content-Encoding: zstd` request bodies |
| Templating | Jinja2 (built into Flask) | **UI is pure Flask** — server-rendered HTML, no SPA framework |
| Charts (UI) | Server-rendered or Chart.js via CDN | Minimal; high-level stats only. No build step. |
| Testing | pytest + a disposable Postgres | Timescale image for integration tests |
| Container | Docker | Multi-stage build |
| Orchestration | Kubernetes | Example manifests provided |

**Why one datastore (Postgres + TimescaleDB) and not InfluxDB or plain relational?**
The workload is high-volume time-series (many samples/sec per car) but only 1–3 cars, and the requirements demand manual edit/removal, CSV round-tripping, ad-hoc relational queries, and DB backup/restore — all awkward in InfluxDB. Plain Postgres handles the relational needs but degrades on year-long time-series scans. TimescaleDB is a Postgres **extension**: we keep a single, familiar, transactional, SQL datastore (one container, one backup target, room for future services) while getting hypertable partitioning and continuous aggregates that keep a year of telemetry fast to query. See `SERVER_DEVELOPER_DOCS.md` §"Design Decision 1" — this directly addresses the original server's InfluxDB-only fragility.

---

## 3. Module Structure

```
pi-drive-server/
├── REQUIREMENTS.md                 ← this file
├── SERVER_DEVELOPER_DOCS.md        ← lessons from the Java server
├── pyproject.toml / requirements.txt
├── wsgi.py                         ← gunicorn entrypoint (creates app via factory)
├── app/
│   ├── __init__.py                 ← create_app() factory; registers blueprints, extensions, error handlers
│   ├── config.py                   ← Config classes; loads + validates env vars at startup (fail loud)
│   ├── extensions.py               ← db (SQLAlchemy), migrate (Alembic) singletons
│   ├── logging.py                  ← structured logging setup; consistent log tags
│   ├── auth.py                     ← @require_api_key decorator (Bearer + X-API-Key)
│   ├── compression.py              ← zstd / gzip request-body decoding
│   ├── errors.py                   ← JSON + HTML error handlers (400/401/404/409/413/422/500)
│   ├── db/
│   │   ├── models.py               ← Vehicle, Telemetry, DrivingEvent ORM models
│   │   └── timescale.py            ← hypertable + continuous-aggregate DDL helpers
│   ├── schemas/
│   │   ├── telemetry.py            ← Pydantic: incoming payload, batch, query params, edit
│   │   └── vehicle.py              ← Pydantic: vehicle read/update
│   ├── services/                   ← business logic; the ONLY layer that touches the DB
│   │   ├── vehicle_service.py
│   │   ├── telemetry_service.py
│   │   ├── stats_service.py
│   │   ├── csv_service.py
│   │   └── backup_service.py
│   ├── api/                        ← JSON REST blueprints
│   │   ├── health.py               ← GET /health, /healthz, /readyz
│   │   ├── telemetry.py            ← POST/GET/PATCH/DELETE telemetry, /telemetry/latest
│   │   ├── vehicles.py             ← GET/PATCH/DELETE vehicles
│   │   └── admin.py                ← CSV import/export, DB backup/restore
│   └── web/                        ← Jinja UI blueprint (pure Flask)
│       ├── routes.py
│       ├── templates/
│       └── static/
├── migrations/                     ← Alembic
├── tests/
└── deploy/
    ├── Dockerfile
    ├── docker-compose.yml          ← server + timescaledb, for local/prod-like runs
    ├── .env.example
    └── k8s/                        ← example Kubernetes deployment (see §10)
```

**Architectural rule (fixes the Java server's biggest gap):** routes never touch the database directly. `api/` and `web/` blueprints call `services/`; only `services/` use the ORM. This gives one place to add validation, caching, and business rules. See `SERVER_DEVELOPER_DOCS.md` §"Design Decision 5".

---

## 4. Data Model

### 4.1 Vehicle

Identity is **VIN-first** (the Android payload carries `vin`), with `device_id` tracked as the most recent reporting device. No hash-based IDs (the Java server's one-car-per-make/model/year limitation is gone). Vehicles are **auto-registered** on first telemetry for a new VIN.

| Column | Type | Notes |
|---|---|---|
| `vin` | `text` PK | Vehicle Identification Number. Primary key. |
| `device_id` | `text` | Last device that reported for this VIN (from `X-Device-Id` / payload). |
| `make` | `text` nullable | Optional, user-editable metadata. |
| `model` | `text` nullable | Optional, user-editable metadata. |
| `year` | `int` nullable | Optional, user-editable metadata. |
| `nickname` | `text` nullable | User-editable display name for the UI. |
| `first_seen` | `timestamptz` | First telemetry timestamp received. |
| `last_seen` | `timestamptz` | Most recent telemetry timestamp received. |
| `created_at` | `timestamptz` | Row creation. |

Vehicle metadata (`make`/`model`/`year`/`nickname`) is **mutable** — unlike the Java server, where car identity was immutable because it was baked into Influx tags. Here, telemetry references the vehicle by `vin` foreign key, so editing display metadata never corrupts historical data.

### 4.2 Telemetry (hypertable)

One row per snapshot. Known signals are explicit, typed columns (fast queries, clean CSV, easy edit); anything unrecognized is preserved in `extra` JSONB for forward-compatibility as the app adds PIDs.

| Column | Type | Source field (wire) |
|---|---|---|
| `time` | `timestamptz` | `timestamp` (ISO 8601). Hypertable partition key. |
| `vin` | `text` FK → vehicle | `vin` |
| `device_id` | `text` nullable | `device_id` / `X-Device-Id` |
| `lat` | `double` nullable | `location.lat` |
| `lng` | `double` nullable | `location.lng` |
| `speed_gps` | `double` nullable | `location.speed_gps` |
| `speed_kmh` | `double` nullable | `obd.speed_kmh` |
| `rpm` | `int` nullable | `obd.rpm` |
| `coolant_temp_c` | `double` nullable | `obd.coolant_temp_c` |
| `intake_air_temp_c` | `double` nullable | `obd.intake_air_temp_c` |
| `throttle_pct` | `double` nullable | `obd.throttle_pct` |
| `fuel_level_pct` | `double` nullable | `obd.fuel_level_pct` |
| `oil_temp_c` | `double` nullable | `obd.oil_temp_c` |
| `maf_gps` | `double` nullable | `obd.maf_gps` |
| `fuel_rate_lph` | `double` nullable | `obd.fuel_rate_lph` |
| `battery_voltage` | `double` nullable | `obd.battery_voltage` |
| `fuel_economy_mpg` | `double` nullable | `calculated.fuel_economy_mpg` |
| `fuel_economy_kml` | `double` nullable | `calculated.fuel_economy_kml` |
| `accel_mps2` | `double` nullable | `accel_mps2` |
| `extra` | `jsonb` nullable | Any unrecognized fields, preserved verbatim. |
| `source` | `text` | `device` \| `manual` \| `csv` — provenance for UI/audit. |

- **Primary key / uniqueness:** `(vin, time)`. This is the **idempotency key** — re-uploading the same `(vin, timestamp)` snapshot is a safe upsert (the app retries offline batches; duplicates must not multiply rows). This directly satisfies the Phase 7 contract "Idempotent by `(vin, timestamp)`".
- **Hypertable:** partitioned on `time`. A retention policy and continuous aggregates are configured in Phase 1.
- **Type handling fix:** floats are stored as floats, ints as ints (the Java server truncated everything to `long` — see `SERVER_DEVELOPER_DOCS.md` Known Bug #2). Pydantic coerces per the typed schema before write.

### 4.3 DrivingEvent

The payload's `events[]` array. Each event is linked to its vehicle and to the nearest telemetry timestamp.

| Column | Type | Wire field |
|---|---|---|
| `id` | `bigint` PK | generated |
| `vin` | `text` FK → vehicle | parent payload `vin` |
| `time` | `timestamptz` | `events[].timestamp` |
| `strategy` | `text` | `ACCELERATION` \| `G_FORCE` |
| `type` | `text` | e.g. `HARD_BRAKE`, `HARD_ACCEL` |
| `duration_ms` | `int` nullable | `duration_ms` |
| `rate_mph_s` | `double` nullable | `rate_mph_s` (ACCELERATION strategy) |
| `peak_g` | `double` nullable | `peak_g` (G_FORCE strategy) |
| `peak_accel_mps2` | `double` nullable | `peak_accel_mps2` |
| `start_speed_mph` | `double` nullable | `start_speed_mph` |
| `end_speed_mph` | `double` nullable | `end_speed_mph` |
| `sources` | `text[]` | `sources` (e.g. `["OBD","GPS"]`) |
| `source` | `text` | `device` \| `manual` \| `csv` |

---

## 5. Wire Contract — Android Ingest API

These three endpoints **must match the Android client exactly** (`phase-07-telemetry.md` "Server API Contract"). Responses are **plain JSON — no HAL `_links`** (the Java server's HATEOAS is dropped; the client hardcodes endpoints and the docs confirm it never used `_links`).

### 5.1 Authentication

A single static API key (chosen storage model) guards all write and management endpoints. The server accepts the key two ways:

- `Authorization: Bearer <API_KEY>` — **primary**, this is what the Android app sends.
- `X-API-Key: <API_KEY>` — convenience for `curl`/scripts/UI.

If `API_KEY` is unset in the environment, the server **refuses to start** (fail loud — no accidentally-open deployments). Health endpoints are exempt from auth. `401` on missing/blank/wrong key; the body is JSON `{ "error": "..." }`.

### 5.2 `POST /telemetry`

Ingest one snapshot **or** a batch.

- **Headers:** `Authorization: Bearer {apiKey}`; optional `X-Device-Id: {deviceId}`; `Content-Type: application/json`; optional `Content-Encoding: zstd` (or `gzip`).
- **Body — single:** the `TelemetryPayload` JSON object (§5.5).
- **Body — batch:** a JSON array of `TelemetryPayload` objects, **or** an object `{ "snapshots": [ ... ] }`. The server accepts all three shapes (single, bare array, wrapped array) so it matches whatever the app's batching emits.
- **Decompression:** if `Content-Encoding` is `zstd` or `gzip`, decode before parsing. Enforce a max decompressed size (`413` if exceeded).
- **Behavior:** auto-register the vehicle on first sight of a VIN; upsert each snapshot by `(vin, time)`; insert events. The whole batch is one transaction.
- **`vin` is required** on every snapshot. Blank/missing VIN → `400`.
- **Response `200 OK`:** `{ "ok": true, "accepted": <n>, "vehicles": ["<vin>", ...] }`. (The Java server returned the parent car entity and gave no confirmation of what was written — Known Bug #7. We return a real count.)
- **Errors:** `400` malformed/blank VIN · `401` bad key · `413` body too large · `422` schema validation failed (Pydantic detail included).

### 5.3 `GET /telemetry/latest?vin={vin}`

Most recent stored timestamp for a VIN. The app uses this for "last synced" display and to know its sync offset.

- **Response `200`:** `{ "vin": "...", "latest_timestamp": "2026-05-25T14:30:00Z" }`
- **Response `404`:** no telemetry for that VIN yet.

### 5.4 `GET /health`

Liveness / connection test. Used by the app's settings "Test" button **and** by Kubernetes probes.

- **Auth:** none (probes must not need a key). The app may send a key; it's ignored here.
- **Response `200`:** `{ "status": "ok" }`.
- Also exposed as `/healthz` (liveness) and `/readyz` (readiness — `/readyz` additionally checks DB connectivity and returns `503` if the DB is unreachable). See §9.

### 5.5 TelemetryPayload schema (wire)

Canonical JSON the app sends (from `../pi-drive-android/README.md`). All keys are **snake_case**. Nested objects and individual fields may be absent if a signal is disabled or unsupported; `vin` and `timestamp` are always present.

```json
{
  "timestamp": "2026-05-24T22:15:30.123Z",
  "device_id": "pd-rxv7a3-k9892",
  "vin": "1G1JC524417100001",
  "location": { "lat": 37.7749, "lng": -122.4194, "speed_gps": 65.2 },
  "obd": {
    "speed_kmh": 105, "rpm": 2400, "coolant_temp_c": 92,
    "intake_air_temp_c": 35, "throttle_pct": 22.5, "fuel_level_pct": 68.0,
    "oil_temp_c": 95, "maf_gps": 12.5, "fuel_rate_lph": null, "battery_voltage": 14.2
  },
  "calculated": { "fuel_economy_mpg": 28.5, "fuel_economy_kml": 12.1 },
  "accel_mps2": 0.45,
  "events": [
    { "strategy": "ACCELERATION", "type": "HARD_BRAKE", "timestamp": "...",
      "duration_ms": 1200, "rate_mph_s": -11.2, "peak_accel_mps2": -5.0,
      "start_speed_mph": 59, "end_speed_mph": 38, "sources": ["OBD","GPS"] },
    { "strategy": "G_FORCE", "type": "HARD_BRAKE", "timestamp": "...",
      "duration_ms": 1200, "peak_g": 0.51, "peak_accel_mps2": -5.0,
      "start_speed_mph": 59, "end_speed_mph": 38, "sources": ["OBD","GPS","ACCELEROMETER"] }
  ]
}
```

`device_id` may arrive in the body or as the `X-Device-Id` header; if both are present they must agree (else `400`). Unknown top-level or `obd` keys are not rejected — they are preserved in `extra`.

---

## 6. Management REST API

All management endpoints require the API key (§5.1), live under `/api/v1`, return plain JSON, use proper HTTP verbs and status codes, and paginate list responses.

### 6.1 Vehicles

| Method & path | Purpose |
|---|---|
| `GET /api/v1/vehicles` | List vehicles with summary stats (sample count, first/last seen, event count). Paginated. |
| `GET /api/v1/vehicles/{vin}` | Single vehicle + summary. `404` if unknown. |
| `PATCH /api/v1/vehicles/{vin}` | Edit mutable metadata (`make`, `model`, `year`, `nickname`). |
| `DELETE /api/v1/vehicles/{vin}` | Delete a vehicle **and all its telemetry + events** (cascade). Requires `?confirm=true`. This is the `DELETE /cars` the Java server left unbuilt. |

### 6.2 Telemetry

| Method & path | Purpose |
|---|---|
| `GET /api/v1/telemetry` | Query rows. Filters: `vin` (required), `start`, `end` (ISO 8601), `fields` (subset), `limit`, `offset`/cursor, `order`. Returns rows + pagination metadata. (The Java server had **no** telemetry read endpoint — this is the planned-but-never-built `GET /cars/{id}/telemetry`.) |
| `GET /api/v1/telemetry/{vin}/{timestamp}` | Single snapshot. |
| `POST /api/v1/telemetry` | **Manual single-row entry** (UI form / API). Same validation as ingest; `source = manual`. |
| `PATCH /api/v1/telemetry/{vin}/{timestamp}` | Edit field values of one snapshot. |
| `DELETE /api/v1/telemetry/{vin}/{timestamp}` | Remove one snapshot. |
| `DELETE /api/v1/telemetry?vin=&start=&end=` | Bulk delete by range (requires `?confirm=true`). |

Note: the live ingest endpoint stays at `POST /telemetry` (§5.2) to match the app's configured base URL. The `/api/v1/telemetry` POST is the management/manual-entry path. Both funnel through `telemetry_service`.

### 6.3 Events

| Method & path | Purpose |
|---|---|
| `GET /api/v1/events?vin=&start=&end=` | List driving events. Paginated. |
| `DELETE /api/v1/events/{id}` | Remove one event. |

### 6.4 Stats

| Method & path | Purpose |
|---|---|
| `GET /api/v1/stats` | Fleet-wide high-level stats (totals, per-vehicle rollups) — the JSON behind the UI dashboard. |
| `GET /api/v1/stats/{vin}` | Per-vehicle stats over an optional time range (sample count, avg/max speed, avg MPG, event counts, data coverage). Served from Timescale continuous aggregates where possible. |

---

## 7. CSV Import / Export

CSV moves **telemetry data**, not database backups (those are §8). Header row maps to telemetry columns (§4.2); the `vin` and `time` columns are required.

| Method & path | Purpose |
|---|---|
| `POST /api/v1/telemetry/import` (multipart `file=`) | Upload a CSV of telemetry rows. Validates each row; upserts by `(vin, time)`; auto-registers vehicles; `source = csv`. Returns `{ imported, skipped, errors: [{row, reason}] }`. Partial success is reported, not silently dropped. |
| `GET /api/v1/telemetry/export?vin=&start=&end=` | Download matching telemetry as CSV (streamed for large ranges). |

The web UI exposes both as a file-upload form and a download button (§9). Round-trip safe: a file exported by this endpoint re-imports without loss.

---

## 8. Database Backup / Restore

Full-database export/import (the user explicitly wants this distinct from telemetry CSV). Backups are guarded by the API key and intended for an operator.

| Method & path | Purpose |
|---|---|
| `GET /api/v1/admin/backup` | Stream a full database dump (`pg_dump` custom/compressed format) as a download. |
| `POST /api/v1/admin/restore` (multipart `file=`) | Restore from a dump. Destructive — requires `?confirm=true`; refuses unless explicitly confirmed. Runs `pg_restore`. |

The UI surfaces "Download backup" and a guarded "Restore from backup" with a confirmation step. Document in the UI and docs that restore replaces existing data.

---

## 9. Web UI (pure Flask / Jinja)

Server-rendered HTML only — **no SPA framework**, no client build step. Optional progressive enhancement via a single CDN `<script>` (e.g. Chart.js) for the stat sparklines, but the pages must be fully functional and readable without JS. Styling is a small static CSS file matching Pi Drive's warm-dark aesthetic (reuse the app's accent `#D88A30` on `#201D19`; see `../pi-drive-android/ui-handoff/.../pd-tokens.jsx`).

### 9.1 Pages

| Route | Page | Contents |
|---|---|---|
| `GET /` | **Dashboard** | Fleet high-level stats: total vehicles, total samples, samples last 24 h / 7 d, total driving events, storage span (oldest→newest), per-vehicle summary cards (nickname, last seen, sample count, recent event count, mini MPG/speed sparkline). |
| `GET /vehicles/{vin}` | **Vehicle detail** | Vehicle metadata (editable), stat rollups over a selectable range, recent telemetry table (paginated), recent events, links to export. |
| `GET /vehicles/{vin}/telemetry` | **Telemetry browser** | Paginated, sortable, filterable table of raw snapshots. Per-row **Edit** and **Delete** actions. |
| `GET /telemetry/new` / `GET /vehicles/{vin}/telemetry/new` | **Manual entry form** | Form to add one snapshot by hand (all known fields). |
| `GET /telemetry/{vin}/{timestamp}/edit` | **Edit form** | Edit one snapshot's values. |
| `GET /import` | **CSV upload** | Upload form → calls import endpoint → shows result summary (imported/skipped/errors). |
| `GET /admin` | **Admin** | Download CSV export; download DB backup; restore-from-backup (guarded); retention info; API-key reminder (not the key itself). |

### 9.2 UI behavior

- **Auth for the UI:** **no login by default** — the UI is for a trusted network, and the operator does not want to log in (decision 2026-06-11; the REST API stays key-guarded regardless). An optional API-key login form (signed session cookie, no user accounts) can be re-enabled for exposed deployments with the config flag `UI_REQUIRE_AUTH=true`.
- **CSRF:** protect all UI form POSTs (Flask-WTF or manual token). API (token-auth) endpoints are CSRF-exempt.
- **Flash messages** for create/edit/delete/import outcomes.
- **Pagination + empty states** everywhere a list can be long or empty.

---

## 10. Containerization & Kubernetes

### 10.1 Container

- **Multi-stage `Dockerfile`**: build deps in a builder stage, slim runtime image (`python:3.12-slim`), non-root user, `gunicorn` entrypoint, `EXPOSE 8080`.
- Includes `pg_dump`/`pg_restore` client binaries (for §8) — install `postgresql-client`.
- **`docker-compose.yml`** for local/prod-like runs: `pidrive-server` + `timescaledb` (named volume for persistence). A `.env.example` documents every variable.
- The server runs DB migrations on startup (or via an init/Job) before serving.

### 10.2 Configuration (env vars)

All config via environment, validated at startup; missing required vars → refuse to start (fail loud).

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | yes | `postgresql+psycopg://user:pass@host:5432/pidrive` |
| `API_KEY` | yes | Static key for ingest + management. |
| `API_KEY_FILE` | alt | Path to a file holding the key (Docker/K8s secrets). One of `API_KEY`/`API_KEY_FILE` required. |
| `SECRET_KEY` | yes | Flask session signing. |
| `UI_REQUIRE_AUTH` | no (`false`) | Opt-in: gate UI pages behind the API-key login form. Off by default — no login needed. |
| `MAX_BODY_BYTES` | no | Max decompressed request body (default e.g. 10 MB). |
| `TELEMETRY_RETENTION_DAYS` | no | Timescale retention policy; unset = keep forever. |
| `LOG_LEVEL` | no (`INFO`) | |
| `LOG_FORMAT` | no (`json` in prod, `text` in dev) | `json` emits one JSON object per line for Alloy/Loki ingestion (§10.4). |
| `ENV` | no (`prod`) | `dev`/`prod` — becomes the `env` log label. |
| `GUNICORN_WORKERS` | no | |

### 10.3 Kubernetes example (`deploy/k8s/`)

Provide a complete, applyable example:

- `namespace.yaml`
- `configmap.yaml` — non-secret config.
- `secret.example.yaml` — `API_KEY`, `DATABASE_URL` creds, `SECRET_KEY` (documented as an example; user supplies real values).
- `postgres-statefulset.yaml` + `postgres-service.yaml` — TimescaleDB with a `PersistentVolumeClaim` (the user's "view a year quickly" + durability requirement). Alternatively documented option to point `DATABASE_URL` at a managed Postgres.
- `server-deployment.yaml` — the Flask/gunicorn app; env from ConfigMap+Secret; **`livenessProbe` → `/healthz`**, **`readinessProbe` → `/readyz`**; resource requests/limits; `replicas` configurable.
- `server-service.yaml` — ClusterIP.
- `ingress.yaml` — example TLS ingress.
- `migration-job.yaml` — runs Alembic migrations as a one-shot Job / init container.
- `kustomization.yaml` — ties it together.

Health probe contract (§5.4 / §9): `/healthz` is always-200 liveness; `/readyz` checks the DB and returns `503` when the DB is down so K8s holds traffic.

### 10.4 Observability — logs to Grafana Loki via Alloy

The server's logs are shipped to a Grafana **Loki** instance for searchable, centralized diagnostics, using **Grafana Alloy** as the collector. Goal: know the server is healthy — and especially when it is not — searchable by request, vehicle, device, endpoint, and status. Log generously on lifecycle and errors; **do not** log a line per telemetry record ingested — log batch-level summaries only (`event=ingest_batch`, fields: accepted count, vehicles, latency).

**The server itself does not talk to Loki.** It writes **structured JSON logs to stdout** (one JSON object per line when `LOG_FORMAT=json`). Alloy collects those logs and forwards them to Loki. This keeps the app decoupled from the log backend and gives buffering/retries for free.

**Collection topology:**
- **Kubernetes:** run Alloy as a sidecar (or a cluster `DaemonSet`) that tails the server container's stdout (`loki.source.file` / pod logs), parses the JSON (`loki.process`), and forwards to Loki (`loki.write`).
- **docker-compose:** an `alloy` service tails the `pidrive-server` container logs and forwards to a `loki` service; an optional `grafana` service for viewing.
- **Offline buffering ("cache and reupload"):** `loki.write` is configured with a **WAL** (`wal { enabled = true }`) plus backoff retries, so if Loki is unavailable Alloy buffers on disk and resends on recovery. This satisfies the durability requirement without any server-side queue.

**Shared label / metadata schema (identical to the Android app, §5.12.3 of the app spec — keep in sync):**
- **Labels (static, low cardinality):** `app="pi-drive-server"`, `component` (`api`|`web`|`ingest`), `level`, `env`.
- **Structured metadata (per-line, queryable):** `request_id`, `vin`, `device_id`, `endpoint`, `status`, `logger` (the log tag, e.g. `Ingest`).

**Structured log fields (each JSON line):** `timestamp` (RFC3339), `level`, `logger`/tag, `message`, `request_id`, plus any of `vin`/`device_id`/`endpoint`/`status`/`event`/`duration_ms` when relevant. A request-id is generated per request (already used by the error handlers, §0.2) and attached to every line in that request's scope.

**Deliverables:** Alloy config (`deploy/observability/alloy-config.alloy`), Loki + Grafana compose services, K8s manifests for Alloy (+ optional Loki/Grafana or doc to point at an existing stack), and a `deploy/observability/grafana-queries.md` with canonical LogQL (errors by endpoint, 5xx rate, slow ingest, per-vehicle errors).

---

## 11. Code Quality & Maintainability

Mirrors the Android project's philosophy (`../CLAUDE.md`):

- **Document everything.** Every module, public function, service method, and route gets a docstring explaining *what and why*. Non-obvious logic gets inline comments. The schema and wire contract are documented next to the code.
- **Modular & single-responsibility.** Routes → services → models. No DB access in routes. Each service has one clear domain. Pydantic schemas decouple wire format from storage.
- **Explicit over implicit.** Typed SQLAlchemy `Mapped[]`, Pydantic models, named config, enums for `source`/`strategy`. No magic string casting (the Java server's `==` and blanket-`long` bugs do not recur).
- **Fail loud in dev, graceful in prod.** Validate config and API key presence at startup. Ingest errors return precise `4xx` with JSON detail; never 500 on bad client input. Internal errors are logged with a trace id and return a generic `500`.
- **Consistent log tags** for filtering: `PiDriveServer`, `Ingest`, `TelemetryService`, `VehicleService`, `CsvService`, `BackupService`, `Auth`.
- **Tested.** Unit tests for services/schemas; integration tests for endpoints against a real Timescale container; the ingest contract has tests asserting it matches the Android payload (single, batch, zstd, idempotency, auto-register, type fidelity).

---

## 12. Acceptance Criteria

The server is "done" when:

1. The Android app, pointed at the server with its API key, streams live and uploads offline batches successfully — single, batched, and zstd-compressed — with no client changes. `(vin, timestamp)` re-uploads do not duplicate rows.
2. `GET /health` / `/healthz` / `/readyz` behave per §5.4/§9 and the app's "Test" button reports healthy.
3. Float and int signals are stored with full fidelity (no truncation).
4. A user can, from the web UI: see fleet + per-vehicle high-level stats; browse, manually add, edit, and delete telemetry; upload and download telemetry CSV; download a DB backup and restore from one.
5. Every UI action has an equivalent authenticated REST endpoint.
6. `docker compose up` brings up server + TimescaleDB and serves the app; the K8s manifests deploy cleanly with working liveness/readiness probes.
7. Code is documented, modular (routes→services→models), and the test suite passes against a real Timescale database.
8. The server emits structured JSON logs; Alloy ships them to Loki (buffering via WAL when Loki is down) and they are searchable in Grafana by `request_id`, `vin`, `endpoint`, `status`, and `level` — without a log line per ingested telemetry record (§10.4).
