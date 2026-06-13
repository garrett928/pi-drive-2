# Pi Drive Server — Implementation Progress

Last updated: 2026-06-13

## Current State

**Active phase:** ✅ COMPLETE — all phases (0–8) done.
**Active step:** —
**Project state:** The server is feature-complete and verified end-to-end. Full Android wire contract + management API + CSV import/export + web UI (no-login by default) + DB backup/restore + retention + production container/Kubernetes/CI + observability (structured logs → Alloy → Loki). **175/175 tests pass** (L1 unit, L2 integration + L3 e2e against real TimescaleDB) and **ruff is clean**. `scripts/e2e-test.sh` = **17/17** on the production gunicorn image (migrations-on-boot, ingest contract, CSV round-trip, backup→wipe→restore through the image's pg16 client, no-auth dashboard). The observability pipeline was verified live: JSON logs parsed by Alloy into the §10.4 label/metadata schema (labels `app/component/level/env`; metadata `request_id/vin/device_id/endpoint/status/logger`), queryable in Loki by request_id/component/status, and **WAL buffering confirmed across a Loki outage** (a line emitted while Loki was down backfilled on recovery). UI screenshots in `screenshots/`.

**Notable fixes found via real execution:** (1) a newer `pg_dump` emits GUCs the pg16 server rejects on restore → image and tests pin `postgresql-client-16`; (2) `pg_restore --clean` invalidates pooled connections → `restore()` disposes the engine pool; (3) Alembic's `fileConfig` defaulted to `disable_existing_loggers=True`, silencing the app's loggers whenever migrations ran in-process → fixed to `False` in `migrations/env.py`.

## Step Status

| Step | Title | Status | PR/Commit |
|---|---|---|---|
| **Phase 0: Bootstrap** |
| 0.1 | Project scaffold + app factory + config validation | DONE | |
| 0.2 | Logging, error handlers, `/health` endpoints | DONE | |
| 0.3 | pytest harness + dev docker-compose (Timescale) | DONE | |
| **Phase 1: Data Layer** |
| 1.1 | SQLAlchemy models (Vehicle, Telemetry, DrivingEvent) | DONE | |
| 1.2 | Alembic + TimescaleDB hypertable + continuous aggregates | DONE | |
| 1.3 | Service layer scaffold + repositories | DONE | |
| **Phase 2: Ingest API** |
| 2.1 | API-key auth + zstd/gzip body decoding | DONE | |
| 2.2 | Telemetry Pydantic schemas + telemetry_service write path | DONE | |
| 2.3 | `POST /telemetry` (single/batch), `/telemetry/latest`, ingest contract tests | DONE | |
| **Phase 3: Management & Query REST API** |
| 3.1 | Vehicles CRUD (`/api/v1/vehicles`) | DONE | |
| 3.2 | Telemetry query + single read (`GET /api/v1/telemetry`) + pagination | DONE | |
| 3.3 | Telemetry edit/delete + events + stats endpoints | DONE | |
| **Phase 4: CSV + Manual Entry** |
| 4.1 | CSV import (upload, validate, partial-success report) | DONE | |
| 4.2 | CSV export (streamed) + manual single-row entry | DONE | |
| **Phase 5: Web UI (pure Flask)** |
| 5.1 | UI auth (API-key login + session) + base layout/CSS | DONE | |
| 5.2 | Dashboard (fleet stats) + vehicle detail pages | DONE | |
| 5.3 | Telemetry browser + manual-entry + edit/delete forms | DONE | |
| 5.4 | CSV upload page + admin page shell | DONE | |
| **Phase 6: Backup / Restore + Retention** |
| 6.1 | DB backup/restore endpoints (pg_dump/pg_restore) + admin UI | DONE | |
| 6.2 | Retention policy + data lifecycle | DONE | |
| **Phase 7: Containerization & Kubernetes** |
| 7.1 | Production multi-stage Dockerfile + docker-compose | DONE | |
| 7.2 | Kubernetes manifests (Deployment, StatefulSet, Service, Ingress, probes, migration Job) | DONE | |
| 7.3 | CI pipeline (lint + tests against Timescale + image build) | DONE | |
| **Phase 8: Observability (Alloy → Loki)** |
| 8.1 | Structured JSON logging + request instrumentation | DONE | |
| 8.2 | Alloy + Loki + Grafana for local (docker-compose) | DONE | |
| 8.3 | Alloy on Kubernetes + Grafana queries doc | DONE | |

## Notes

- **Phase 7/8 decisions:** (1) **Multi-stage image** — builder compiles a venv; slim runtime copies it, adds `postgresql-client-16`, runs as non-root (uid 10001) via gunicorn. `deploy/entrypoint.sh` runs migrations on boot (`deploy/run_migrations.py`, guarded by a **Postgres advisory lock** so concurrent replicas don't race), then execs gunicorn with **no `--access-logfile`** (the app emits its own structured `http_request` JSON line; gunicorn's text access log would pollute the JSON stream Alloy parses). (2) **K8s** (`deploy/k8s/`) — `kubectl apply -k` brings up Timescale StatefulSet + server Deployment with liveness `/healthz` (no DB) and readiness `/readyz` (DB-checked, 503 pulls the pod from the Service without restarting); migrations on boot or via the one-shot `migration-job.yaml` (`RUN_MIGRATIONS=0`). (3) **CI** — `server-test.yml` (ruff + pytest against a Timescale service container) and `server-build.yml` (build the image, smoke ingest + backup, push on `server-v*` tag). (4) **Observability** — the app only writes JSON to stdout; **Alloy** (compose `loki.source.docker`; k8s DaemonSet `loki.source.kubernetes`) parses it into the §10.4 schema — labels `app/component/level/env`, structured metadata `request_id/vin/device_id/endpoint/status/logger` — and `loki.write` with a **WAL** buffers across Loki outages. Use Alloy's `env("LOKI_URL")` (not `sys.env`). Canonical LogQL in `deploy/observability/grafana-queries.md`.
- **Phase 6 decisions:** (1) **TimescaleDB restore procedure** — a plain `pg_restore` corrupts hypertables/CAGGs; `backup_service.restore` wraps `pg_restore --clean --if-exists` in `timescaledb_pre_restore()`/`post_restore()` (run AUTOCOMMIT on the app engine; the flag is database-level so the separate pg_restore connection inherits it), then **`engine.dispose()`** to drop pooled connections holding cached plans bound to the now-recreated tables. (2) **Client version must match the server** — a pg18 `pg_dump` emits `transaction_timeout` (a pg17+ GUC) that a pg16 server rejects on restore; the dev `deploy/Dockerfile` installs `postgresql-client-16` from PGDG, and the backup tests prefer a Homebrew `postgresql@16` client. (3) **Security** — connection params are passed to the C tools as an argv list (never a shell string); the password goes via `PGPASSWORD`, never argv; binaries resolve from `PG_BIN_DIR`/PATH, never request input. (4) **Retention** is applied in the app factory on startup (not a migration) so it tracks the `TELEMETRY_RETENTION_DAYS` env without a schema change; non-fatal on error. (5) **Test isolation** — restore (`--clean`) and retention (drops chunks) are destructive at the DB level, so those tests run against **disposable databases** created/dropped in the same container; use `URL.render_as_string(hide_password=False)` (plain `str(URL)` masks the password as `***`).
- **Phase 4/5 decisions:** (1) **CSV columns are fixed** — `csv_service.CSV_COLUMNS` is the canonical header (matches the `telemetry` table); the export header and import validator share it, which is what makes round-trips lossless. Unknown header columns are rejected for the whole file (400, fail loud); bad *rows* are skipped and reported as `{row, reason}` with 1-based file line numbers (header = line 1). `extra` travels as a JSON string in one cell. Imported rows are always `source=csv` (provenance of *this copy*). (2) **One ON CONFLICT write path** — `telemetry_service.upsert_row` is shared by wire ingest, CSV import, and the UI manual-entry form. (3) **UI auth is OFF by default** (`UI_REQUIRE_AUTH=false`, decision 2026-06-11 — the operator does not want to log into the UI on their trusted network). The login form + `@ui_login_required` decorator remain in `app/web/auth.py` as an opt-in (`UI_REQUIRE_AUTH=true`) for exposed deployments; the nav hides Login/Logout when off. The REST API stays key-guarded regardless. CSRF is a session token validated by a blueprint-wide `before_request` on **every** web POST, unconditionally (no Flask-WTF dependency). (4) **`/admin/export`** duplicates the API export under session auth because a browser form can't send the key header. (5) **Error content negotiation**: `errors.py` renders `error.html` only when Accept explicitly prefers `text/html`; the app/curl keep JSON. (6) Dashboard sparklines are server-rendered SVG from the CAGG daily series (one query per listed vehicle — documented N+1, maintainability over performance). (7) The web UI is forms-only/no-JS-required; the only JS is the inline delete `confirm()` (progressive enhancement).
- **Phase 2/3 decisions:** (1) **Event dedupe** — events have no wire-level id, but offline-batch retries must not multiply rows, so `insert_events` skips exact `(vin, time, strategy, type)` matches; a re-POST is fully idempotent for snapshots *and* events. (2) **Error mapping** — Pydantic errors touching `vin` map to **400** (per §5.2 "malformed/blank VIN"); all other schema violations are **422** with Pydantic detail; body/header device-id disagreement is 400. (3) **`telemetry_daily` runs real-time** (`materialized_only=false`, set in migration 0003) so stats include rows newer than the last materialization. (4) **Timestamp format** — all responses serialize via `app/api/common.iso_z` (Z-suffix, milliseconds only when present) so `latest_timestamp` round-trips the app's `…30.123Z` exactly. (5) Shared ISO helpers live in `app/api/common.py`; `stats_service` duplicates `_iso` deliberately (services must not import from the API layer).
- **ORM choice (Phase 1):** uses **plain SQLAlchemy 2.x** (`DeclarativeBase` in `app/db/models.py`, engine + `sessionmaker` per-app on `app.extensions`, request-scoped session in `app/db/session.py`), not flask-sqlalchemy — matching the plan's `Base.metadata` / `sessionmaker` language. The Phase 0 flask-sqlalchemy placeholder was removed. **Services own all DB access** and accept an optional `session=` (defaulting to the request session) so they're testable without a Flask context; services **flush, not commit** — the caller (ingest orchestrator in Phase 2) owns the transaction boundary.
- **Alembic runs under AUTOCOMMIT** (`migrations/env.py`) because TimescaleDB continuous-aggregate creation/refresh cannot run in a transaction block. Timescale DDL lives in `app/db/timescale.py`. Migrations verified reversible (`downgrade base` → `upgrade head`).
- **Testing harness (built in Phase 0, see `TESTING.md`):** four layers — L1 unit (no DB), L2 integration (Flask test client + real Timescale), L3 e2e (real server over a socket + real Timescale), L4 prod-like (docker-compose'd image). `conftest.py` provisions the DB via **testcontainers** by default (falls back to `TEST_DATABASE_URL`, skips cleanly when Docker is down — never a false green). Mock payloads live in `tests/fixtures/` (canonical `single_payload()` == `sample_payload.json`, drift-guarded). Phase 0 proven end-to-end: `pytest -q` = 32 passed (incl. real Timescale + live socket); `scripts/e2e-test.sh` = containerized stack green. **Phase 2 adds the POST /telemetry contract matrix to `tests/e2e/`** (the table in `TESTING.md` §5).
- The wire contract in Phase 2 is **fixed by the already-shipped Android client** — do not change endpoint paths, header names, or the snake_case payload shape. Cross-check against `../pi-drive-android/implementation/phase-07-telemetry.md` before altering anything in `api/telemetry.py`.
- Integration tests must run against a **real TimescaleDB** (the `timescale/timescaledb` image), not SQLite — hypertables and continuous aggregates are Timescale-specific.
- **Phase 8 (observability):** the server only logs structured JSON to stdout; **Grafana Alloy** (not the server) ships logs to Loki, with a WAL for buffering when Loki is down. JSON logging is set up in Phase 0.2 and instrumented across Phases 2–6, then deployed in Phase 8. The Loki label/structured-metadata schema is **shared with the Android app** (`pi-drive-android` Phase 13) — keep them in sync so Grafana queries span both.
