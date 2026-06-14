---
name: srv-logs
description: Dump and filter Pi Drive server (Flask) logs. Use when debugging unexpected server behavior, a request is returning the wrong status, an endpoint isn't working, or to confirm a feature is emitting the expected log lines. Arguments: optional filter keyword or log tag (e.g. "Ingest", "Auth", "TelemetryService", "500").
---

# srv-logs — Pi Drive server logs

Filter and display Flask server log output.

Filter/tag: $ARGUMENTS

## Step 1 — Check where logs are going

The server logs to stdout when run with Flask dev server, or to a file if configured. Check:

```bash
# If running via docker compose:
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server/deploy
docker compose logs pidrive-server --tail 100 2>&1

# If running as a background process (flask run):
# Logs went to the terminal where it started. Check for a log file:
ls /tmp/pidrive-server*.log 2>/dev/null || echo "no log file found"
```

## Step 2 — Filter by tag or keyword

If `$ARGUMENTS` is provided, filter:

```bash
# From docker compose:
docker compose logs pidrive-server 2>&1 | grep -i "$ARGUMENTS" | tail -60

# From a log file:
grep -i "$ARGUMENTS" /tmp/pidrive-server.log 2>/dev/null | tail -60
```

If no argument, show the last 80 lines unfiltered.

## Step 3 — Look for key patterns

Always scan for these regardless of filter:
```bash
# Errors and warnings:
docker compose logs pidrive-server 2>&1 | grep -E "ERROR|WARNING|500|Exception|Traceback" | tail -20
```

## Step 4 — Report

Summarize:
- Total log lines visible
- Any `ERROR` / `WARNING` / `Exception` / `500` occurrences
- Lines matching the requested filter tag
- The full traceback of any exception found

**Log tags to look for by feature:**

| Tag | Feature |
|---|---|
| `PiDriveServer` | Server startup, config, general |
| `Ingest` | `POST /telemetry` — payload parsing, upserts, batch |
| `Auth` | API key validation (401 cases) |
| `TelemetryService` | DB writes, upsert, latest-timestamp |
| `VehicleService` | Vehicle auto-register, CRUD |
| `CsvService` | CSV import/export, row errors |
| `BackupService` | pg_dump/pg_restore |
| `Compression` | zstd/gzip decoding, 413 |
