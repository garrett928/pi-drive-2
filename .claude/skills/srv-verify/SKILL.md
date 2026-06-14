---
name: srv-verify
description: Verify that a Pi Drive server feature is fully working end-to-end. Use after implementing a server step, when asked to "verify the server", "check that the endpoint works", "confirm the Flask implementation", or "make sure the API works". Orchestrates tests → server start → curl exercises → log check → contract validation. Do NOT skip any step.
argument-hint: [step-name or endpoint to verify, e.g. "ingest" or "step 2.3"]
---

# srv-verify — End-to-end verification of a Pi Drive server feature

Feature/step: $ARGUMENTS

Do the following steps in order. Do not skip. Each has a required artifact.

---

## Step 1 — Run tests

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server
pytest tests/ -m "not integration" -q --tb=short 2>&1
```

**Required artifact:** pass/fail count. Stop if any test fails.

If integration tests exist for this feature:
```bash
pytest tests/ -m integration -q --tb=short 2>&1
```

Stop if contract tests fail — flag them: "⚠️ INGEST CONTRACT FAILURE — may break Android app."

---

## Step 2 — Ensure server is running

```bash
curl -s http://localhost:8080/health 2>/dev/null
```

If no response, start it:

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server/deploy
docker compose up -d timescaledb
sleep 3
cd ..
source deploy/.env 2>/dev/null || true
flask --app wsgi run --port 8080 --debug > /tmp/pidrive-server.log 2>&1 &
sleep 2
curl -s http://localhost:8080/health
```

**Required artifact:** `{"status":"ok"}`.

---

## Step 3 — Exercise the feature

Based on `$ARGUMENTS`, run the relevant curl assertions. Common patterns:

### Ingest (step 2.x)

```bash
API_KEY=$(grep API_KEY deploy/.env | cut -d= -f2)

# Single snapshot
curl -sv -X POST http://localhost:8080/telemetry \
  -H "Authorization: Bearer $API_KEY" \
  -H "X-Device-Id: pd-test-001" \
  -H "Content-Type: application/json" \
  -d '{
    "timestamp": "2026-06-08T12:00:00.000Z",
    "device_id": "pd-test-001",
    "vin": "TEST00000000000001",
    "obd": {"speed_kmh": 80, "rpm": 2000, "throttle_pct": 22.5},
    "calculated": {"fuel_economy_mpg": 28.5},
    "events": []
  }' 2>&1 | grep -E "< HTTP|{.*}"

# Expected: HTTP/1.1 200, {"ok":true,"accepted":1,"vehicles":["TEST00000000000001"]}

# Idempotency — second POST same payload:
# Expected: still 200, still accepted=1, no duplicate row

# Latest timestamp:
curl -s "http://localhost:8080/telemetry/latest?vin=TEST00000000000001" \
  -H "Authorization: Bearer $API_KEY"
# Expected: {"vin":"TEST00000000000001","latest_timestamp":"2026-06-08T12:00:00Z"}
```

### Vehicles (step 3.1)

```bash
curl -s http://localhost:8080/api/v1/vehicles -H "Authorization: Bearer $API_KEY"
curl -s http://localhost:8080/api/v1/vehicles/TEST00000000000001 -H "Authorization: Bearer $API_KEY"
```

### Telemetry query (step 3.2)

```bash
curl -s "http://localhost:8080/api/v1/telemetry?vin=TEST00000000000001&limit=5" \
  -H "Authorization: Bearer $API_KEY"
```

### CSV import (step 4.1)

```bash
echo "vin,time,speed_kmh,rpm
TEST00000000000001,2026-06-08T11:00:00Z,60,1800" > /tmp/test.csv
curl -s -X POST http://localhost:8080/api/v1/telemetry/import \
  -H "Authorization: Bearer $API_KEY" \
  -F file=@/tmp/test.csv
# Expected: {"imported":1,"skipped":0,"errors":[]}
```

### Health / k8s probes (step 7.x)

```bash
curl -s http://localhost:8080/healthz
curl -s http://localhost:8080/readyz
```

---

## Step 4 — Check logs for errors

```bash
grep -E "ERROR|WARNING|Exception|Traceback|500" /tmp/pidrive-server.log 2>/dev/null | tail -20
```

Also confirm the feature's log tag appeared:
```bash
grep -i "$ARGUMENTS" /tmp/pidrive-server.log 2>/dev/null | tail -10
```

**Required artifact:** no ERROR/Exception in logs; feature log tag visible if applicable.

---

## Step 5 — Check the web UI (for UI steps, phases 5+)

For steps that include a web UI page, use the browser MCP or computer use to open `http://localhost:8080` and navigate to the relevant page. Capture a screenshot.

For API-only steps, skip.

---

## Step 6 — Final report

```
FEATURE: <feature name / step>
STATUS: PASS / FAIL / PARTIAL

Tests:         X unit passed, Y integration passed, Z failed
Server health: OK / FAILED
Curl checks:   PASS / FAIL — [list any wrong responses]
Logs:          Clean / [ERROR details]
UI screenshot: [path] — [description] / N/A

Next: [fix or "ready to commit"]
```

Do not report PASS unless:
1. All tests passed.
2. Server health is 200.
3. All curl checks returned expected status codes and bodies.
4. No ERROR/Exception in logs.
