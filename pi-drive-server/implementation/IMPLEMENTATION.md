# Pi Drive Server — Master Implementation Plan

9 phases, 27 steps. Each step is roughly one PR (one cohesive, testable unit of work).
See `PROGRESS.md` for current state. Load individual `phase-NN-*.md` for step details.
Product spec: `../REQUIREMENTS.md`. Lessons from the original Java server: `../SERVER_DEVELOPER_DOCS.md`.

> **Status (2026-06-13): ✅ COMPLETE.** All 9 phases (0–8) are implemented and verified end-to-end — 175/175 tests against real TimescaleDB, ruff clean, `scripts/e2e-test.sh` 17/17 on the production image, and the Alloy→Loki pipeline (incl. WAL buffering) confirmed live. See `PROGRESS.md`.

## Dependency Graph

```
Phase 0: Bootstrap ─────────────┐
 (app factory, config, health,  │
  Docker dev, pytest)           v
Phase 1: Data Layer ────────────┬──> Phase 2: Ingest API  (the Android contract)
 (Postgres+Timescale, models,   │         │
  migrations, repositories)     │         v
                                │   Phase 3: Management & Query REST API
                                │         │
                                │         v
                                │   Phase 4: CSV Import/Export + Manual Entry
                                │         │
                                │         v
                                │   Phase 5: Web UI (pure Flask / Jinja)
                                │         │
                                │         v
                                └──>Phase 6: Backup / Restore + Retention
                                          │
                                          v
                                  Phase 7: Containerization & Kubernetes
                                          │
                                          v
                                  Phase 8: Observability (Alloy → Loki)
```

Phases 2→6 build on the Phase 1 data layer; the API phases (2,3,4) come before the UI (5) so every UI action has a tested endpoint behind it. Phase 8 (observability) builds on the JSON logging established in Phase 0 and the container/K8s deployment from Phase 7.

## Phase Overview

| Phase | File | Steps | What it delivers |
|---|---|---|---|
| **0** | `phase-00-bootstrap.md` | 0.1 – 0.3 | Flask app factory, config validation, logging, `/health`, pytest, dev Docker |
| **1** | `phase-01-data-layer.md` | 1.1 – 1.3 | SQLAlchemy models, Alembic, TimescaleDB hypertable + continuous aggregates, service layer scaffold |
| **2** | `phase-02-ingest.md` | 2.1 – 2.3 | `POST /telemetry` (single/batch/zstd), auth, auto-register, idempotent upsert, `/telemetry/latest`, `/health` |
| **3** | `phase-03-management-api.md` | 3.1 – 3.3 | Vehicles CRUD, telemetry query/edit/delete, events, stats endpoints, pagination |
| **4** | `phase-04-csv.md` | 4.1 – 4.2 | CSV telemetry import/export, manual single-row entry endpoint |
| **5** | `phase-05-web-ui.md` | 5.1 – 5.4 | Dashboard, vehicle detail, telemetry browser + edit/delete, manual-entry + CSV-upload forms, UI auth |
| **6** | `phase-06-backup.md` | 6.1 – 6.2 | DB backup/restore (pg_dump), retention policy, admin page |
| **7** | `phase-07-deploy.md` | 7.1 – 7.3 | Production Dockerfile, docker-compose, full K8s manifests with probes, CI |
| **8** | `phase-08-observability.md` | 8.1 – 8.3 | Structured JSON logging + request instrumentation, Alloy+Loki+Grafana (compose), Alloy on K8s + Grafana queries |

## Conventions

- **Python package root:** `app/`
- **Layering rule (hard):** `api/` and `web/` blueprints call `services/`; only `services/` touch the ORM. No DB access in route handlers.
- **Wire format:** snake_case JSON, plain (no HAL). Pydantic decouples wire schema from ORM models.
- **Identity:** VIN-first; vehicles auto-register on first telemetry; idempotency key is `(vin, time)`.
- **Auth:** static API key — `Authorization: Bearer <key>` (primary, what the app sends) or `X-API-Key`. Health endpoints exempt.
- **Log tags:** `PiDriveServer`, `Ingest`, `TelemetryService`, `VehicleService`, `CsvService`, `BackupService`, `Auth`.
- **Logging for Loki (Phase 8):** structured JSON to stdout (`LOG_FORMAT=json`); Alloy ships to Loki. Labels stay static/low-cardinality (`app`, `component`, `level`, `env`); identifiers (`request_id`, `vin`, `device_id`, `endpoint`, `status`, `logger`) go in structured metadata. Schema is **shared with the Android app** (`pi-drive-android` Phase 13) — keep them identical.
- **Fail loud:** validate required env (`DATABASE_URL`, `API_KEY`/`API_KEY_FILE`, `SECRET_KEY`) at startup; refuse to boot if missing.
- **Verify every step:** run `pytest` (unit + integration against a real Timescale container) and the step's stated manual check (`curl`/compose) before marking done.

## Known-bug guardrails (carried from the Java server)

Each is fixed by an explicit step; see `../SERVER_DEVELOPER_DOCS.md` "Known Bugs and TODOs":

| Old bug | Fixed in |
|---|---|
| Everything cast to `long` (float truncation) | 1.1 (typed columns) + 2.2 (Pydantic coercion) |
| Car registry vanishes after 1 year (hardcoded range) | 1.1 (relational vehicle table) |
| One car per make/model/year (hash ID) | 1.1 (VIN primary key) |
| No telemetry read endpoint | 3.2 |
| No delete car / delete telemetry | 3.1, 3.3 |
| Line-protocol string concatenation (injection) | 2.2 (ORM/parameterized writes) |
| No input validation | 2.2 (Pydantic) |
| Ingest response gave no write confirmation | 2.3 (`{ok, accepted, vehicles}`) |
| No service layer | 1.3 (services scaffold) |

## How to Use This Plan

**AI agent:** Read `PROGRESS.md` → load the phase file for the current step → implement → test → update `PROGRESS.md`.

**Human developer:** Use `PROGRESS.md` as the roadmap. Each phase file has enough detail to implement without re-reading the full `REQUIREMENTS.md`. Check off steps as you go.
