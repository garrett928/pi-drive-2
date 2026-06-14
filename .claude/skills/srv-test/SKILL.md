---
name: srv-test
description: Run the Pi Drive server test suite (pytest). Use after implementing or modifying any server feature, when asked to "run server tests", "check flask tests", "verify server tests pass", or before marking a server step complete. Runs unit tests (no DB) first, then integration tests if a Timescale container is available. Reports pass/fail counts and full failure details.
---

# srv-test — Run Pi Drive server tests

Arguments (optional): `unit` to run only unit tests, `integration` to run only integration tests, `all` or blank to run both.

Module scope: $ARGUMENTS

## Step 1 — Move to server root

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server
```

## Step 2 — Run unit tests (no DB required)

Always run these first — they're fast and need no container.

```bash
pytest tests/ -m "not integration" -q --tb=short 2>&1
```

If `$ARGUMENTS` is `integration`, skip this step.

## Step 3 — Check DB availability

```bash
docker ps --filter "name=timescaledb" --filter "status=running" --format "{{.Names}}" 2>/dev/null
```

If the TimescaleDB container is not running and integration tests are needed, offer to start it:
```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server/deploy
docker compose up -d timescaledb
sleep 5
cd ..
```

If no container and `$ARGUMENTS` is `unit`, skip integration tests entirely and say so.

## Step 4 — Run integration tests (requires DB)

If `$ARGUMENTS` is not `unit` and the DB is available:

```bash
pytest tests/ -m integration -q --tb=short 2>&1
```

If DB is unavailable, note: "Integration tests skipped — TimescaleDB not running. Start with `/srv-run`."

## Step 5 — Parse results

For any failures, look for:
```
FAILED tests/path/test_file.py::TestClass::test_name
```

Show the full error body (the `--tb=short` flag keeps it readable).

## Step 6 — Report

Print a summary:

```
Unit tests:        X passed, Y failed, Z skipped
Integration tests: X passed, Y failed, Z skipped  (or: skipped — no DB)

FAILURES:
  tests/test_ingest_api.py::test_post_telemetry_single
    AssertionError: expected 200, got 401
    at tests/test_ingest_api.py:42
```

If all tests pass, say so clearly and give totals.

Flag any failure in an ingest-contract test separately — those guard the Android wire format:
> ⚠️  INGEST CONTRACT FAILURE: This may break the Android app's telemetry upload.
