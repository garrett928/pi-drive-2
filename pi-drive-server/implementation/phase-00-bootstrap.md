# Phase 0: Bootstrap

**Goal:** A runnable Flask app skeleton with the app-factory pattern, validated config, structured logging, error handlers, a health endpoint, and a pytest + dev-Docker harness. No business logic yet — but `docker compose up` serves a `/health` 200 and `pytest` is green.

**Depends on:** nothing.

**Reference:** `../REQUIREMENTS.md` §2 (stack), §3 (module structure), §10.2 (config), §11 (quality).

---

## Step 0.1 — Project scaffold + app factory + config validation

**What to build:**

1. **`pyproject.toml` / `requirements.txt`** — pin: `flask`, `gunicorn`, `sqlalchemy`, `alembic`, `flask-sqlalchemy` (or plain SQLAlchemy + a session helper — prefer plain for clarity), `psycopg[binary]`, `pydantic`, `pydantic-settings`, `zstandard`, `python-dotenv`; dev: `pytest`, `pytest-cov`, `requests`.
2. **`app/config.py`** — `Config` loaded via `pydantic-settings` from env. Fields per §10.2: `database_url`, `api_key` (resolve `API_KEY` or read `API_KEY_FILE`), `secret_key`, `ui_require_auth=True`, `max_body_bytes`, `telemetry_retention_days` (optional), `log_level="INFO"`. **Validate at construction**: missing `database_url`, `api_key`/`api_key_file`, or `secret_key` raises immediately with a clear message.
3. **`app/extensions.py`** — module-level `db` (SQLAlchemy engine/session factory) and a `migrate` hook, initialized lazily in the factory.
4. **`app/__init__.py`** — `create_app(config=None)` factory: load config (fail loud), init extensions, register blueprints (none yet beyond a placeholder), register error handlers (stub), return `app`.
5. **`wsgi.py`** — `app = create_app()` for gunicorn.

**Tests (`tests/test_config.py`, `tests/test_factory.py`):**
- Config with all required env present → constructs; `API_KEY_FILE` is read from disk.
- Missing `API_KEY` and `API_KEY_FILE` → raises at startup.
- `create_app()` with a test config returns a Flask app; `app.testing` works.

**Verify:**
- `FLASK_APP=wsgi.py flask run` boots with env set; refuses to boot with `API_KEY` unset (prints the reason).
- `pytest -q` green.

---

## Step 0.2 — Logging, error handlers, `/health`

**What to build:**

1. **`app/logging.py`** — configure structured logging (level from config). Define the log-tag convention (`PiDriveServer`, `Ingest`, etc. — §11). Attach a per-request id to the log context.
   - **`LOG_FORMAT` (`json`|`text`):** in `json` mode emit **one JSON object per line** (this is what Alloy/Loki ingest in Phase 8 — see `REQUIREMENTS.md` §10.4). Each record includes: `timestamp` (RFC3339), `level`, `logger` (tag), `message`, `request_id`, and any contextual fields bound for that request (`vin`, `device_id`, `endpoint`, `status`, `event`, `duration_ms`). `text` mode is for local dev readability.
   - Provide a small helper (e.g. `log.bind(**fields)` via a context var) so services attach `vin`/`endpoint`/`status` without threading args everywhere. Keep the label/metadata field names aligned with the **shared schema** in §10.4 so they map cleanly to Loki labels (`component`, `level`, `env`) and structured metadata (`request_id`, `vin`, `device_id`, `endpoint`, `status`, `logger`).
   - Set the `component` field per blueprint group (`api`/`web`/`ingest`) and `env` from the `ENV` config.
2. **`app/errors.py`** — JSON error handlers for `400/401/404/409/413/422/500`. Each returns `{ "error": "<message>", "request_id": "..." }` with the right status. `500` logs the traceback but returns a generic message. (These same handlers later serve HTML for `text/html` Accept — stub the content-negotiation now.)
3. **`app/api/health.py`** — blueprint with:
   - `GET /health` → `{"status":"ok"}` (200, no auth) — the app's "Test" button target.
   - `GET /healthz` → liveness, always 200.
   - `GET /readyz` → readiness; in Phase 1 it will check the DB. For now returns 200 with `{"status":"ok","db":"not_configured"}`.
   Register the blueprint in the factory.

**Tests:**
- `GET /health`, `/healthz`, `/readyz` → 200 with expected bodies.
- A route that raises → 500 handler returns JSON with `request_id`, not a stack trace.
- Forcing a 404 → JSON `{"error":...}`.

**Verify:** `curl localhost:5000/health` → `{"status":"ok"}`.

---

## Step 0.3 — pytest harness + dev docker-compose (Timescale)

**What to build:**

1. **`tests/conftest.py`** — fixtures: `app` (test config), `client` (Flask test client), and a `db_url` fixture that points at a disposable TimescaleDB (env `TEST_DATABASE_URL`, defaulting to the compose DB). Integration tests skip gracefully if no DB is reachable (mirrors the Android project's "skip without emulator" pattern).
2. **`deploy/docker-compose.yml`** — two services:
   - `timescaledb` (`timescale/timescaledb:latest-pg16`), named volume, healthcheck.
   - `pidrive-server` (built from `deploy/Dockerfile`, a minimal dev Dockerfile for now), env from `.env`, depends_on db healthy, port `8080:8080`.
3. **`deploy/.env.example`** — every variable from §10.2 with safe example values; documented.
4. **Minimal `deploy/Dockerfile`** (dev): `python:3.12-slim`, install deps, run `flask run`/`gunicorn`. (Hardened multi-stage version is Phase 7.)

**Tests:** `pytest` runs unit tests with no DB; integration markers (`@pytest.mark.integration`) run only when a DB is present.

**Verify:**
- `cd deploy && docker compose up --build` → `curl localhost:8080/health` → 200.
- `pytest -q` green locally (unit tests; integration skipped without DB).

**Estimated size:** ~600 lines across the phase.
