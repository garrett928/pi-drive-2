#!/usr/bin/env bash
#
# L4 prod-like smoke test: bring up the containerized stack (gunicorn/flask +
# TimescaleDB) via docker-compose, wait for health, exercise the live endpoints
# over :8080 with curl, assert responses with jq, then tear down.
#
# This is the "prove the shipped image works" path — closest to production.
# For the fast inner loop use `pytest` (L1/L2/L3) instead.
#
# Usage:  scripts/e2e-test.sh            # build, test, tear down
#         scripts/e2e-test.sh --keep     # leave the stack running afterwards
#
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_DIR="$SERVER_DIR/deploy"
BASE_URL="http://localhost:8080"
KEEP=0
[[ "${1:-}" == "--keep" ]] && KEEP=1

PASS=0
FAIL=0

log()  { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }
ok()   { printf '  \033[1;32m✓ %s\033[0m\n' "$*"; PASS=$((PASS+1)); }
bad()  { printf '  \033[1;31m✗ %s\033[0m\n' "$*"; FAIL=$((FAIL+1)); }

cleanup() {
  if [[ "$KEEP" -eq 0 ]]; then
    log "Tearing down stack"
    (cd "$COMPOSE_DIR" && docker compose down -v) || true
  else
    log "Leaving stack running (--keep). Stop it with: (cd $COMPOSE_DIR && docker compose down -v)"
  fi
}
trap cleanup EXIT

# ── Preconditions ──────────────────────────────────────────────────────────────
command -v docker >/dev/null || { echo "docker not found"; exit 2; }
docker info >/dev/null 2>&1 || { echo "Docker daemon not running. Start Docker Desktop."; exit 2; }
[[ -f "$COMPOSE_DIR/.env" ]] || { echo "Missing $COMPOSE_DIR/.env (copy from .env.example)"; exit 2; }

# ── Bring up the stack ─────────────────────────────────────────────────────────
log "Building and starting stack (docker compose up --build -d)"
(cd "$COMPOSE_DIR" && docker compose up --build -d)

log "Waiting for $BASE_URL/health"
for i in $(seq 1 60); do
  if curl -fsS "$BASE_URL/health" >/dev/null 2>&1; then break; fi
  sleep 1
  if [[ "$i" -eq 60 ]]; then
    bad "server did not become healthy in 60s"
    (cd "$COMPOSE_DIR" && docker compose logs pidrive-server --tail 40) || true
    exit 1
  fi
done

# ── Exercise endpoints ─────────────────────────────────────────────────────────
log "GET /health"
body="$(curl -fsS "$BASE_URL/health")"
[[ "$(jq -r .status <<<"$body")" == "ok" ]] && ok "/health → {\"status\":\"ok\"}" || bad "/health body: $body"

log "GET /healthz"
code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/healthz")"
[[ "$code" == "200" ]] && ok "/healthz → 200" || bad "/healthz → $code"

log "GET /readyz"
code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/readyz")"
[[ "$code" == "200" ]] && ok "/readyz → 200" || bad "/readyz → $code"

log "GET /no-such-endpoint (expect JSON 404)"
code="$(curl -s -o /tmp/pd_404.json -w '%{http_code}' "$BASE_URL/no-such-endpoint")"
if [[ "$code" == "404" ]] && jq -e .error /tmp/pd_404.json >/dev/null 2>&1; then
  ok "/no-such-endpoint → JSON 404"
else
  bad "/no-such-endpoint → $code, body: $(cat /tmp/pd_404.json)"
fi

# ── Ingest contract (Phase 2) ──────────────────────────────────────────────────
# Pull the API key from deploy/.env so the curls authenticate like the app does.
API_KEY="$(grep -E '^API_KEY=' "$COMPOSE_DIR/.env" | head -1 | cut -d= -f2-)"
PAYLOAD="$SERVER_DIR/tests/fixtures/sample_payload.json"
VIN="1G1JC524417100001"

# The production image migrates on boot (entrypoint.sh, advisory-locked); the
# server only becomes ready after that. Confirm the schema is at head rather
# than re-running it.
log "Verify migrations applied on boot (alembic current == head)"
current="$(cd "$COMPOSE_DIR" && docker compose exec -T pidrive-server alembic current 2>/dev/null)"
if grep -q "(head)" <<<"$current"; then
  ok "schema at head: $(tr -d '\n' <<<"$current" | sed 's/.*-> //')"
else
  bad "schema not at head: $current"
fi

log "POST /telemetry (canonical Android payload)"
body="$(curl -fsS -X POST "$BASE_URL/telemetry" \
  -H "Authorization: Bearer $API_KEY" -H "X-Device-Id: pd-rxv7a3-k9892" \
  -H 'Content-Type: application/json' --data @"$PAYLOAD")"
if [[ "$(jq -r .ok <<<"$body")" == "true" && "$(jq -r .accepted <<<"$body")" == "1" ]]; then
  ok "ingest → accepted=1"
else
  bad "ingest response: $body"
fi

log "Re-POST same payload (idempotency)"
curl -fsS -X POST "$BASE_URL/telemetry" \
  -H "Authorization: Bearer $API_KEY" -H 'Content-Type: application/json' \
  --data @"$PAYLOAD" >/dev/null
rows="$(curl -fsS "$BASE_URL/api/v1/telemetry?vin=$VIN" -H "X-API-Key: $API_KEY" | jq '.rows | length')"
[[ "$rows" == "1" ]] && ok "still one telemetry row after re-POST" || bad "expected 1 row, got $rows"

log "GET /telemetry/latest"
latest="$(curl -fsS "$BASE_URL/telemetry/latest?vin=$VIN" -H "Authorization: Bearer $API_KEY")"
[[ "$(jq -r .vin <<<"$latest")" == "$VIN" ]] && ok "latest → $(jq -r .latest_timestamp <<<"$latest")" || bad "latest: $latest"

log "GET /api/v1/stats (management surface)"
stats="$(curl -fsS "$BASE_URL/api/v1/stats" -H "X-API-Key: $API_KEY")"
[[ "$(jq -r .total_samples <<<"$stats")" -ge 1 ]] && ok "stats → total_samples=$(jq -r .total_samples <<<"$stats")" || bad "stats: $stats"

log "POST /telemetry without key (expect 401)"
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/telemetry" \
  -H 'Content-Type: application/json' --data @"$PAYLOAD")"
[[ "$code" == "401" ]] && ok "unauthenticated ingest → 401" || bad "expected 401, got $code"

# ── CSV import/export (Phase 4) ────────────────────────────────────────────────
log "GET /api/v1/telemetry/export (streamed CSV)"
curl -fsS "$BASE_URL/api/v1/telemetry/export?vin=$VIN" -H "X-API-Key: $API_KEY" -o /tmp/pd_export.csv
if head -1 /tmp/pd_export.csv | grep -q '^vin,time,' && [[ "$(wc -l < /tmp/pd_export.csv | tr -d ' ')" -ge 2 ]]; then
  ok "export → canonical header + $(( $(wc -l < /tmp/pd_export.csv | tr -d ' ') - 1 )) row(s)"
else
  bad "export CSV malformed: $(head -2 /tmp/pd_export.csv)"
fi

log "POST /api/v1/telemetry/import (round-trip the export)"
body="$(curl -fsS -X POST "$BASE_URL/api/v1/telemetry/import" \
  -H "X-API-Key: $API_KEY" -F "file=@/tmp/pd_export.csv;type=text/csv")"
if [[ "$(jq -r .imported <<<"$body")" -ge 1 && "$(jq -r '.errors | length' <<<"$body")" == "0" ]]; then
  ok "import → imported=$(jq -r .imported <<<"$body"), no errors (idempotent upsert)"
else
  bad "import response: $body"
fi

# ── Database backup / restore (Phase 6) ────────────────────────────────────────
# Exercises the shipped image's pg16 client end-to-end: dump → wipe → restore →
# verify the row came back.
log "GET /api/v1/admin/backup (full pg_dump)"
curl -fsS "$BASE_URL/api/v1/admin/backup" -H "X-API-Key: $API_KEY" -o /tmp/pd_backup.dump
if [[ "$(head -c 5 /tmp/pd_backup.dump)" == "PGDMP" ]]; then
  ok "backup → pg_dump custom archive ($(wc -c < /tmp/pd_backup.dump | tr -d ' ') bytes)"
else
  bad "backup not a pg_dump archive: $(head -c 16 /tmp/pd_backup.dump | xxd | head -1)"
fi

log "DELETE the vehicle, then restore from backup"
curl -s -o /dev/null -X DELETE "$BASE_URL/api/v1/vehicles/$VIN?confirm=true" -H "X-API-Key: $API_KEY"
restore="$(curl -fsS -X POST "$BASE_URL/api/v1/admin/restore?confirm=true" \
  -H "X-API-Key: $API_KEY" -F "file=@/tmp/pd_backup.dump")"
if [[ "$(jq -r .restored <<<"$restore")" == "true" ]]; then
  rows="$(curl -fsS "$BASE_URL/api/v1/telemetry?vin=$VIN" -H "X-API-Key: $API_KEY" | jq '.rows | length')"
  [[ "$rows" -ge 1 ]] && ok "restore brought data back ($rows row(s))" || bad "restore left 0 rows"
else
  bad "restore response: $restore"
fi

log "POST /api/v1/admin/restore without confirm (expect 400)"
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/v1/admin/restore" \
  -H "X-API-Key: $API_KEY" -F "file=@/tmp/pd_backup.dump")"
[[ "$code" == "400" ]] && ok "unconfirmed restore → 400" || bad "expected 400, got $code"

# ── Web UI smoke (Phase 5) ─────────────────────────────────────────────────────
# UI auth is off by default (decision 2026-06-11) — the dashboard is reachable
# with no login.
log "GET / (no login required by default)"
page="$(curl -fsS "$BASE_URL/")"
grep -q "Fleet dashboard" <<<"$page" && ok "dashboard renders without login" || bad "dashboard not reachable: $(head -c 200 <<<"$page")"

log "Cleanup: DELETE the test vehicle (cascade)"
code="$(curl -s -o /dev/null -w '%{http_code}' -X DELETE \
  "$BASE_URL/api/v1/vehicles/$VIN?confirm=true" -H "X-API-Key: $API_KEY")"
[[ "$code" == "204" ]] && ok "vehicle deleted (telemetry + events cascaded)" || bad "delete → $code"

# ── Report ─────────────────────────────────────────────────────────────────────
log "Result: $PASS passed, $FAIL failed"
[[ "$FAIL" -eq 0 ]]
