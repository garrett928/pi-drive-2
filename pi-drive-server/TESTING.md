# Pi Drive Server — Testing Strategy

> **Audience:** the developer (human or AI) verifying the Pi Drive telemetry server.
> **Companion docs:** `REQUIREMENTS.md` (§5 wire contract, §11 quality), the phase plans in `implementation/`, and the Android side's `../pi-drive-android/TESTING.md` (whose "skip without emulator" pattern this mirrors with "skip without database").

This document answers one question: **how do we know the server actually works** — not just that its functions return the right values in a mock, but that a real Flask process, talking to a real TimescaleDB, accepts the exact bytes the Android app sends and stores them correctly.

---

## 1. The problem this fixes

As of Phase 0, the server was validated **only** by in-process `pytest` using Flask's test client against a sqlite URL that was never connected to. That proves the factory wires up and `/health` returns 200 — it proves **nothing** about:

- whether a real WSGI server serves over a socket,
- whether SQLAlchemy models map onto a real Postgres/TimescaleDB schema,
- whether the hypertable, composite PK, and `ON CONFLICT` upsert behave,
- whether `Content-Encoding: zstd` bodies decode over the wire,
- whether the JSON the Android app sends round-trips with full type fidelity.

The plan files already *specify* the right tests (Phase 1 integration tests against real Timescale; Phase 2 Android-contract tests). What's missing is the **harness that lets them run** and the **discipline of running them**. This document builds that harness and defines the matrix.

---

## 2. The testing pyramid (tailored to this server)

Four layers, fast → slow, cheap → realistic. Every phase contributes to the layers it can.

| Layer | Process model | DB | Speed | What it proves | pytest marker |
|---|---|---|---|---|---|
| **L1 — Unit** | in-process, no Flask app | none (pure funcs / mocked session) | ms | config validation, Pydantic schema coercion, compression decode, auth-header parsing, model metadata | *(default, unmarked)* |
| **L2 — Integration** | Flask **test client** (in-process) | **real TimescaleDB** | ~100ms | migrations apply, hypertable exists, services upsert/query correctly, idempotency, type fidelity, `/readyz` DB check | `@pytest.mark.integration` |
| **L3 — Contract / E2E** | **real server process**, real socket | **real TimescaleDB** | ~1–2s | the full Android wire contract over HTTP: headers, status codes, zstd-on-the-wire, exact response shapes | `@pytest.mark.e2e` |
| **L4 — Prod-like smoke** | **docker-compose** (containerized gunicorn + Timescale) | container | ~30s startup | the shipped image boots, migrations run on startup, probes pass, app is reachable on `:8080` | shell script / CI only |

**Rule of thumb:** push every assertion to the lowest layer that can make it. Schema coercion → L1. "Does the upsert dedupe?" → L2. "Does `POST /telemetry` with `Content-Encoding: zstd` return `{"ok":true,"accepted":1}`?" → L3. "Does `docker compose up` serve a healthy app?" → L4.

L2 is the workhorse and satisfies most of the "real data, real DB, inspect the response" requirement. **L3 is the layer that literally runs the app as a server and calls it over a socket** — the thing explicitly demanded and currently absent.

---

## 3. Tooling — what must be installed, and why

| Tool | Status | Needed for | Action |
|---|---|---|---|
| **Docker Desktop** | installed (28.3.2), **daemon not running** | L2/L3/L4 database; L4 full stack | Start Docker Desktop; confirm `docker ps` works. |
| **Docker Compose v2** | installed (v2.39) | L4, `srv-run` | none |
| **`testcontainers[postgresql]`** (Python) | **not installed** | automatic per-session Timescale lifecycle in pytest (no manual `compose up`) | add to dev deps |
| **`pytest-flask`** *(optional)* | not installed | `live_server` fixture for L3 (alternative to our own threaded fixture) | evaluate; our own fixture avoids the dep |
| **`zstandard`** | installed (runtime dep) | producing zstd test bodies in L3 | none |
| **`requests`** | installed (dev dep) | L3 real-HTTP calls | none |
| **`psql` / `pg_dump` / `pg_restore`** (host CLI) | **not installed** | manual DB inspection; **Phase 6** backup/restore feature itself | `brew install libpq` and link, **or** always exec into the container (`docker compose exec timescaledb psql …`). Container path needs no host install. |

### 3.1 Decision: how to manage the test database

Three approaches were evaluated:

| Approach | Pros | Cons |
|---|---|---|
| **A. `testcontainers-python`** (recommended default) | pytest starts/stops a throwaway Timescale container itself; zero manual steps; clean DB per session; works identically locally and in CI; auto-cleanup | adds a dev dependency; ~5s container spin-up per session |
| **B. Manual `docker compose up` + `TEST_DATABASE_URL`** | no new deps; reuses the dev container; conftest already designed around `TEST_DATABASE_URL` | requires a separate *test* database so it doesn't clobber dev data; manual lifecycle; easy to forget and silently skip |
| **C. sqlite in-memory** | zero infra | **rejected** — no hypertables, no continuous aggregates, no `JSONB`/`ARRAY`/`ON CONFLICT` parity. Would give false confidence. The whole point is Timescale-specific behavior. |

**Recommendation: support A and B, default to A.** `conftest.py` resolves the DB URL in this order:
1. `TEST_DATABASE_URL` env var if set → use it (lets you point at an already-running container, or CI's service container).
2. else if `testcontainers` is importable and Docker is up → spin up a throwaway Timescale container for the session.
3. else → **skip** all `integration`/`e2e` tests with a clear reason (mirrors Android's "skip without emulator").

This means `pytest` always runs L1; it runs L2/L3 automatically when Docker is available, and degrades gracefully when it isn't — no test ever silently passes by hitting a fake DB.

### 3.2 Decision: how to run the real server for L3

| Approach | Pros | Cons |
|---|---|---|
| **A. Threaded `werkzeug` server fixture** (recommended) | real socket, real HTTP, real WSGI dispatch; starts in-process so the same Timescale fixture/transaction config is shared; fast; auto port; no subprocess flakiness | same Python process (not a separate OS process) — acceptable, the WSGI path is fully exercised |
| **B. `subprocess` `flask run` / `gunicorn` + poll `/health`** | truest separate-process model; exercises gunicorn | slower; log capture and teardown are fiddly; env plumbing |
| **C. compose-based (L4)** | containerized, closest to prod | 30s; too slow for the inner loop; reserved for CI smoke + `srv-verify` |

**Recommendation: A for the automated `tests/e2e/` suite** (a `live_server` fixture that boots `create_app()` on a background thread bound to an ephemeral port, yields the base URL). **B/C remain available** via the `srv-verify` skill and a `scripts/e2e-test.sh` for prod-like CI smoke.

---

## 4. Code & fixtures to build

### 4.1 `tests/conftest.py` — upgrade (the keystone)

Add DB-aware fixtures alongside the existing `app`/`client`:

- `db_url` (session) — resolves per §3.1 (env → testcontainers → skip).
- `_timescale_container` (session) — if using testcontainers, starts `timescale/timescaledb:latest-pg16`, yields its URL, stops on teardown.
- `migrated_db` (session) — runs `alembic upgrade head` against `db_url` once.
- `db_session` (function) — opens a transaction, yields a session, **rolls back** after each test so tests are isolated and order-independent.
- `integration_app` (session) — `create_app()` configured with the real `db_url`.
- `live_server` (session, L3) — boots `integration_app` on a `werkzeug` server in a daemon thread on an ephemeral port; yields `http://127.0.0.1:<port>`; shuts down on teardown.
- `api_headers` (function) — `{"Authorization": f"Bearer {API_KEY}"}` convenience.

Auto-skip wiring: a `pytest_collection_modifyitems` hook (or fixture-level `pytest.skip`) that skips `integration`/`e2e` items when `db_url` is `None`, with the reason printed once.

### 4.2 `tests/fixtures/` — mock data (mirrors `REQUIREMENTS.md` §5.5 exactly)

Create canonical payloads as files + a small builder so every phase reuses the same source of truth:

| File / factory | Purpose |
|---|---|
| `tests/fixtures/payloads.py` | Python factories: `single_payload()`, `batch_bare_array(n)`, `batch_wrapped(n)`, `payload_with_events()`, `payload_with_extra_fields()`, `payload_type_fidelity()` (floats that must stay floats, ints that must stay ints). Built from one canonical dict so drift is impossible. |
| `tests/fixtures/sample_payload.json` | The verbatim §5.5 example object — used by `curl`/`srv-verify` and as the contract anchor. |
| `tests/fixtures/sample_batch.json` | `{"snapshots":[…]}` and a bare-array variant. |
| `tests/fixtures/sample_telemetry.csv` | Round-trip CSV for Phase 4 import/export (header → §4.2 columns; `vin,time` required). |
| `tests/fixtures/zstd.py` | helper `zstd_compress(json_bytes)` to produce `Content-Encoding: zstd` bodies for L3. |

The canonical dict **must** match `../pi-drive-android/implementation/phase-07-telemetry.md`. A dedicated test asserts our `single_payload()` equals the documented example field-for-field, so if the doc and fixtures ever diverge, CI fails loudly.

### 4.3 Wire-contract fidelity assertions (the Android guarantee)

A focused `tests/e2e/test_ingest_contract.py` (Phase 2) that, against the **live server**, asserts each row of the contract table below. These are the tests that protect the already-shipped Android client.

---

## 5. Test-case matrix by phase

Tests are written **with** each phase (not deferred). "L#" = which pyramid layer.

### Phase 0 — Bootstrap *(retrofit now)*
- **L1** config validation (✅ done), error-handler JSON shapes (✅ done), health bodies (✅ done).
- **L3 (new):** boot the real server, `curl /health /healthz /readyz` over a socket → 200 + expected JSON. *Proves the L3 harness itself works before there's anything else to test.*
- **L4 (new):** `docker compose up` → `curl :8080/health` → 200. *Proves the dev image + compose.*

### Phase 1 — Data Layer
- **L1** model metadata: `Telemetry` PK is `(vin, time)`; `rpm` is Integer, others Float; `Source`/`Strategy` enum strings match the wire.
- **L2** migrations apply to real Timescale; `telemetry` appears in `timescaledb_information.hypertables`; daily continuous aggregate returns correct per-day counts after refresh; `/readyz` returns 200 with DB up and **503 with DB down**.
- **L2** `vehicle_service.upsert_vehicle` twice → one row, `last_seen` advances, `first_seen` fixed; `latest_timestamp` returns max / `None` for unknown VIN.

### Phase 2 — Ingest (the contract) — **the heart**
- **L1** Pydantic schema: coerces types, rejects blank VIN, preserves unknown keys into `extra`; `read_request_body` plain-JSON passthrough, **zstd round-trip**, oversized decompressed → 413.
- **L2** `ingest_batch` idempotency: same `(vin, time)` twice → one row, second is an update (not a duplicate); float/int stored with full fidelity (no `long` truncation — Java bug #2); auto-register vehicle on first VIN; events inserted.
- **L3 contract** (against live server, the table below):

  | # | Request | Expected response |
  |---|---|---|
  | 1 | `POST /telemetry` with §5.5 example, `Authorization: Bearer`, `X-Device-Id` | `200`, `{"ok":true,"accepted":1,"vehicles":["1G1JC…"]}`; vehicle + 1 telemetry + 2 events stored |
  | 2 | Same payload **zstd-compressed**, `Content-Encoding: zstd` | `200`, stored once |
  | 3 | Re-POST identical payload | `200`, still **one** telemetry row (idempotent) |
  | 4 | Bare-array batch and `{"snapshots":[…]}` batch | `200`, `accepted=n` |
  | 5 | Missing/blank `vin` | `400` |
  | 6 | Missing/wrong API key | `401` |
  | 7 | Body over `MAX_BODY_BYTES` (decompressed) | `413` |
  | 8 | Malformed JSON / schema violation | `422` with Pydantic detail |
  | 9 | `device_id` in body **and** `X-Device-Id` disagree | `400` |
  | 10 | `GET /telemetry/latest?vin=…` after ingest | `200` `{"vin":…,"latest_timestamp":…}`; unknown VIN → `404` |

### Phase 3 — Management API
- **L2/L3** vehicles list/get/patch/delete (cascade + `?confirm=true`); telemetry query filters (`vin` required, `start`/`end`/`fields`/`limit`/`order`) + pagination metadata; single get/patch/delete; bulk delete by range; events list/delete; `/stats` and `/stats/{vin}` shapes (served from the continuous aggregate).

### Phase 4 — CSV
- **L2/L3** import: valid rows upsert, bad rows reported in `errors[]` (partial success, not silent drop), auto-register, `source=csv`; export streams matching rows; **round-trip**: export → re-import → no loss / no duplicates.

### Phase 5 — Web UI
- **L3** auth: unauthenticated UI POST blocked; login with API key sets session; CSRF token required on form POSTs; key pages render 200 with seeded data (dashboard, vehicle detail, telemetry browser, manual-entry, edit, import, admin); empty-states render. Optional: Playwright/Chrome-MCP screenshot of dashboard for visual confirmation.

### Phase 6 — Backup / Retention
- **L4** `GET /api/v1/admin/backup` streams a `pg_dump`; `POST /api/v1/admin/restore` with `?confirm=true` round-trips into a clean DB and data matches; restore without confirm refused; retention policy drops chunks older than N days.

### Phase 7 — Container / k8s
- **L4** multi-stage image builds; non-root; `pg_dump` present; compose stack healthy; migrations run on startup; k8s manifests `kubectl apply --dry-run=server` clean; liveness `/healthz` & readiness `/readyz` wired.

### Phase 8 — Observability
- **L1/L3** `LOG_FORMAT=json` emits one valid JSON object per line with the §10.4 fields (`request_id`, `vin`, `endpoint`, `status`, `event`, `duration_ms`); a request carries one stable `request_id` across its lines; ingest logs a **batch summary** (`event=ingest_batch`) and **not** a line per record; Alloy config parses a sample line (golden-file test).

---

## 6. Skills to add / update

The interactive skills already exist and are good. Changes:

| Skill | Change |
|---|---|
| `srv-test` | After Phase 1 lands, ensure Step 4 sets `TEST_DATABASE_URL` (or relies on testcontainers) so `-m integration` actually runs; add an `-m e2e` invocation. |
| `srv-verify` | Already does L3 curl exercises — keep. Add a step to load `tests/fixtures/sample_payload.json` rather than inline JSON, so the skill and the test suite share one payload. |
| **`scripts/e2e-test.sh`** *(new)* | Prod-like L4: `docker compose up -d`, wait for `/health`, run the contract curls against `:8080`, assert responses with `jq`, tear down. Used by CI and as the manual "prove the shipped image" path. Mirrors the Android project's `scripts/e2e-test.sh`. |
| **`.github/workflows/ci.yml`** *(new, Phase 7.3)* | lint → L1 → (Timescale service container) L2+L3 → build image → L4 smoke. |

No *new* Claude skill is strictly required — `srv-run`/`srv-test`/`srv-verify`/`srv-logs` cover the interactive loop. The new artifacts are **code/scripts**, not skills.

---

## 7. How to run it (target developer experience)

```bash
# L1 only — fast, no Docker. Runs anywhere.
pytest -m "not integration and not e2e" -q

# L1 + L2 + L3 — auto-starts a throwaway Timescale via testcontainers (Docker must be up).
pytest -q

# Point at an already-running container instead of testcontainers:
TEST_DATABASE_URL=postgresql+psycopg://pidrive:pidrive@localhost:5432/pidrive_test pytest -q

# L4 prod-like smoke (containerized server + db):
scripts/e2e-test.sh
```

When Docker is down, `pytest -q` still passes L1 and prints `SKIPPED (no database: start Docker or set TEST_DATABASE_URL)` for L2/L3 — never a false green.

---

## 8. Sequencing — what to do, in order

1. **Foundation (retrofit into Phase 0) — ✅ DONE:**
   - ✅ Docker started; `deploy/.env` written from `.env.example` (git-ignored).
   - ✅ `testcontainers>=4.0` added to dev deps and installed.
   - ✅ `conftest.py` upgraded with the §4.1 fixtures (`db_url` via testcontainers→`TEST_DATABASE_URL`→skip, `db_engine`, `integration_app`, `live_server`, `api_headers`).
   - ✅ `tests/fixtures/` created (§4.2): `payloads.py`, `sample_payload.json`, `compression.py` (zstd/gzip) — drift-guarded by `tests/test_fixtures.py`.
   - ✅ `tests/integration/test_db_connection.py` (L2, real Timescale + extension check) and `tests/e2e/test_health_e2e.py` (L3, real server over a socket).
   - ✅ `scripts/e2e-test.sh` (L4 prod-like) added and run.
   - **Verified:** `pytest -q` = **32 passed** (incl. real Timescale via testcontainers + live socket); `DOCKER_HOST=<bad> pytest -m "integration or e2e"` = **8 skipped** (never a false green); `scripts/e2e-test.sh` = containerized stack green over `:8080`.
2. **Phase 1 — ✅ DONE:** L2 migrations + services tests landed; `/readyz` does a real `SELECT 1` (asserted at L3).
3. **Phase 2 — ✅ DONE:** the full contract matrix (§5) runs in `tests/e2e/test_ingest_contract.py` against the live server with the exact Android payload, zstd/gzip on the wire, and `(vin, time)` idempotency. **136 tests** at this point.
4. **Phase 3 — ✅ DONE:** management API integration suites (`test_vehicles_api.py`, `test_telemetry_api.py`, `test_events_stats_api.py`).
5. **Phase 4 — ✅ DONE:** `tests/integration/test_csv_api.py` (10 tests): import counts/partial-success/idempotency/header validation, streamed export, and the §7 export→wipe→re-import round-trip (values, ms precision, and `extra` JSONB all preserved). `scripts/e2e-test.sh` round-trips a real export through the import endpoint on the containerized stack.
6. **Phase 5 — ✅ DONE:** `tests/integration/test_web_ui.py` (15 tests): login/CSRF/logout, HTML-vs-JSON error negotiation, dashboard, metadata edit, browser pagination + filters, manual entry (incl. validation re-render), edit, delete, CSV upload page, admin + session-auth export download. `scripts/e2e-test.sh` additionally logs into the UI with curl (cookie jar + CSRF token) and asserts the dashboard renders. UI screenshots for the record live in `screenshots/`.
   - **Verified:** `pytest -q` = **161 passed** against real Timescale; `scripts/e2e-test.sh` = **15/15** on the containerized stack.
7. **Phase 6 — ✅ DONE:** `tests/integration/test_backup_api.py` (5 tests): backup streams a `pg_dump` archive (PGDMP magic), restore refuses without confirm, the **backup→wipe→restore round-trip** restores data intact, and a **retention** test applies the policy and runs the job to drop an ancient chunk while keeping a fresh row. Destructive tests run on **disposable databases** so the shared session DB is untouched; they skip cleanly when no pg16 client is present. `scripts/e2e-test.sh` adds a backup round-trip through the shipped image's pg16 client.
   - **Verified:** `pytest -q` = **167 passed** against real Timescale; `scripts/e2e-test.sh` = **17/17** on the containerized stack.
8. **Phase 7 — ✅ DONE:** the prod multi-stage image is built and run by `scripts/e2e-test.sh` (migrations-on-boot under an advisory lock, gunicorn, non-root, pg16 client); the L4 run asserts the schema is at head, ingests, CSV round-trips, and backup→restore round-trips through the image. K8s manifests render via `kubectl kustomize`. CI (`.github/workflows/server-test.yml`, `server-build.yml`) runs ruff + pytest against a Timescale service container and smoke-tests the built image.
9. **Phase 8 — ✅ DONE:** `tests/test_logging.py` (8 unit tests) covers the JSON formatter, the one-line-per-request access log with status/duration/component/request_id, 4xx→WARN / 5xx→ERROR-with-traceback, inbound `X-Request-Id`, and the ingest batch-summary contract. The pipeline was verified **live**: `docker compose --profile observability up` → traffic → Loki parsed the §10.4 schema (component label, request_id structured metadata, `ingest_batch` summary, 4xx by status), and **WAL buffering backfilled a line emitted during a Loki outage**.
   - **Verified:** `pytest -q` = **175 passed** against real Timescale, ruff clean; `scripts/e2e-test.sh` = **17/17** on the production image.

The server is **feature-complete**: all of REQUIREMENTS.md §12 acceptance criteria hold end-to-end.

---

## 9. Acceptance — "testing is real" when…

- `pytest -q` on a machine with Docker brings up a real TimescaleDB, applies migrations, and exercises services **and** a real Flask server over a socket — and the same command degrades to L1-only (clearly skipped, never faked) without Docker.
- The Phase 2 contract matrix (§5) passes against the **live server** with the **exact** Android payload, including zstd-on-the-wire and idempotency.
- `scripts/e2e-test.sh` boots the containerized stack and the shipped image answers `/health` and a real `POST /telemetry`.
- CI runs L1+L2+L3 against a Timescale service container on every push.
