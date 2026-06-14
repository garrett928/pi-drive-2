# Pi Drive Server

[![Server Tests](https://github.com/garrett928/pi-drive-2/actions/workflows/server-test.yml/badge.svg)](https://github.com/garrett928/pi-drive-2/actions/workflows/server-test.yml)
[![Server Image](https://github.com/garrett928/pi-drive-2/actions/workflows/server-build.yml/badge.svg)](https://github.com/garrett928/pi-drive-2/actions/workflows/server-build.yml)

Flask + TimescaleDB telemetry server for the [Pi Drive](../CLAUDE.md) Android
app: ingests live OBD-II telemetry, stores it in a TimescaleDB hypertable,
serves a management REST API and a server-rendered web UI, and supports CSV and
full-database backup/restore.

## Run locally (the fast way)

One command — builds the image, starts TimescaleDB, migrates on boot,
serves via gunicorn:

```bash
cd pi-drive-server/deploy
cp .env.example .env          # set API_KEY + SECRET_KEY
docker compose up --build
# → http://localhost:8080  (UI)   POST http://localhost:8080/telemetry  (ingest)
```

## Run for development

```bash
cd pi-drive-server
pip install -e ".[dev]"
# Start a Timescale container however you like, then:
export DATABASE_URL=postgresql+psycopg://pidrive:pidrive@localhost:5432/pidrive
export API_KEY=dev-key SECRET_KEY=dev-secret ENV=dev
alembic upgrade head
flask --app wsgi run --port 8080
```

## Tests

```bash
pytest -q          # L1 unit always run; L2/L3 use a real Timescale via
                   # testcontainers (Docker) or $TEST_DATABASE_URL, else skip.
bash scripts/e2e-test.sh   # L4: build the image + compose stack, exercise it end-to-end
```

The backup/restore tests need a **pg16** client (`pg_dump`/`pg_restore`); they
skip cleanly if it's absent. See [TESTING.md](TESTING.md) for the four-layer
strategy.

## Configuration

All via environment (validated at startup — missing required vars refuse to
start). Full reference: [`deploy/.env.example`](deploy/.env.example).

| Variable | Required | Notes |
|---|---|---|
| `DATABASE_URL` | yes | `postgresql+psycopg://user:pass@host:5432/pidrive` |
| `API_KEY` / `API_KEY_FILE` | yes | Static key for ingest + management (the app sends it) |
| `SECRET_KEY` | yes | Flask session signing |
| `ENV` | no (`prod`) | `dev`/`prod`; sets log-format default |
| `LOG_LEVEL` / `LOG_FORMAT` | no | `json` (Alloy/Loki) or `text` |
| `UI_REQUIRE_AUTH` | no (`false`) | **No UI login by default**; `true` adds an API-key login form |
| `MAX_BODY_BYTES` | no (10 MiB) | Decompressed request-body cap |
| `TELEMETRY_RETENTION_DAYS` | no | Timescale retention; unset = keep forever |
| `GUNICORN_WORKERS` | no (`2`) | Worker processes |
| `PG_BIN_DIR` | no | Where `pg_dump`/`pg_restore` live, if not on PATH |
| `RUN_MIGRATIONS` | no (`1`) | `0` = skip boot migrations (use the K8s Job) |

## Deploy

- **Docker / compose:** [`deploy/Dockerfile`](deploy/Dockerfile) (multi-stage,
  non-root, gunicorn, pg16 client) + [`deploy/docker-compose.yml`](deploy/docker-compose.yml).
- **Kubernetes:** [`deploy/k8s/`](deploy/k8s/README.md) — `kubectl apply -k deploy/k8s/`;
  liveness `/healthz`, readiness `/readyz`, migrations on boot or via a Job.
- **Observability:** structured JSON logs → Grafana Alloy → Loki; see
  [`deploy/observability/`](deploy/observability/) and the
  `--profile observability` compose stack.

## Health endpoints

- `GET /healthz` — always 200 liveness (no DB).
- `GET /readyz` — 200 when the DB is reachable, else 503 (readiness).
- `GET /health` — `{"status":"ok"}` convenience check.

## Architecture

See [`../CLAUDE.md`](../CLAUDE.md) (layering rules), [`REQUIREMENTS.md`](REQUIREMENTS.md)
(full spec), and [`implementation/PROGRESS.md`](implementation/PROGRESS.md)
(what's built).
