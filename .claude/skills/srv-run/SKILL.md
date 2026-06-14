---
name: srv-run
description: Start the Pi Drive Flask server in development mode with its Postgres+TimescaleDB container. Use when starting server development, testing an endpoint, running the server for the first time in a session, or when asked to "start the server", "run the Flask app", "launch the server", or "bring up the backend". After launch, confirm /health returns 200.
---

# srv-run — Start the Pi Drive Flask server

Bring up the TimescaleDB container and start the Flask development server.

## Step 1 — Ensure a .env file exists

```bash
ls /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server/deploy/.env 2>/dev/null || \
  echo "MISSING — copy deploy/.env.example to deploy/.env and fill in values"
```

If `.env` is missing, read `deploy/.env.example`, fill in sensible dev defaults, and write `deploy/.env`. Required vars at minimum: `DATABASE_URL`, `API_KEY`, `SECRET_KEY`.

## Step 2 — Start TimescaleDB (if not already running)

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server/deploy
docker compose up -d timescaledb
```

Wait for it to be healthy:
```bash
docker compose ps timescaledb
```

If unhealthy after 15 seconds, check logs:
```bash
docker compose logs timescaledb --tail 20
```

## Step 3 — Run Alembic migrations

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server
source deploy/.env 2>/dev/null || true
flask --app wsgi db upgrade 2>&1 || alembic upgrade head 2>&1
```

Skip if migrations haven't been written yet (Phase 1 not started).

## Step 4 — Start the dev server

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server
source deploy/.env 2>/dev/null || true
flask --app wsgi run --port 8080 --debug 2>&1 &
SRV_PID=$!
echo "Server PID: $SRV_PID"
sleep 2
```

## Step 5 — Confirm it's up

```bash
curl -s http://localhost:8080/health
```

Expected: `{"status":"ok"}` with HTTP 200.

If the response is not 200, check the server output:
```bash
curl -sv http://localhost:8080/health 2>&1 | head -30
```

## Step 6 — Report

Tell the user:
- Server URL: `http://localhost:8080`
- Health check result
- DB container status
- Any startup errors

Note: to stop the server later, run `kill $SRV_PID` or `pkill -f "flask.*wsgi"`. To stop the DB: `cd deploy && docker compose stop timescaledb`.
