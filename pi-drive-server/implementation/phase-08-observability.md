# Phase 8: Observability — Structured Logs + Grafana Alloy / Loki

**Goal:** Ship the server's logs to a Grafana **Loki** instance via **Grafana Alloy** so failures are searchable and centralized. The server writes structured JSON to stdout; Alloy collects, parses, and forwards to Loki with on-disk buffering (WAL) that survives Loki outages. Provide compose + Kubernetes deployment and canonical Grafana queries.

**Depends on:** Phase 0 (`logging.py` — JSON structured logging is established there), Phase 7 (container image + compose + K8s to attach Alloy to). Instrumentation touches the services built in Phases 2–6.

**Reference:** `REQUIREMENTS.md` §10.4. Alloy components: `loki.source.file` (tail container/pod stdout), `loki.process` (parse JSON → labels + structured metadata), `loki.write` (forward to Loki; `wal { enabled = true }` for buffering + backoff retries). Loki push best practice: static low-cardinality **labels**; identifiers in **structured metadata**.

**Architecture:** the server never talks to Loki directly — it only logs JSON to stdout. This keeps the app decoupled and makes "cache and reupload if Loki is unavailable" Alloy's job (its WAL), not the server's.

**Shared label / metadata schema (identical to the Android app — keep in sync):**
- **Labels:** `app="pi-drive-server"`, `component` (`api`|`web`|`ingest`), `level`, `env`.
- **Structured metadata:** `request_id`, `vin`, `device_id`, `endpoint`, `status`, `logger`.

---

## Step 8.1 — Structured JSON logging + request instrumentation

**What to build (refining Phase 0.2):**

1. **Confirm/extend `app/logging.py`:** JSON formatter emits one object per line with the §10.4 fields. A context-var-bound logger lets request scope carry `request_id` (+ `vin`/`endpoint`/`status`). `component`/`env` set from config.
2. **Request middleware (`app/__init__.py` or a small `app/observability.py`):** Flask `before_request`/`after_request` hooks that:
   - Generate/propagate `request_id` (honor inbound `X-Request-Id` if present; echo it back in the response header — already referenced by the error handlers).
   - Bind `endpoint`, method, and, after the view runs, `status` and `duration_ms`; emit one access log line per request at INFO (`event="http_request"`), WARN/ERROR for 4xx/5xx.
3. **Instrument the services** (generous on lifecycle/errors, sparse on data — `REQUIREMENTS.md` §10.4):
   - **Ingest (`telemetry_service.ingest_batch`):** one **batch summary** line (`event="ingest_batch"`, `accepted`, `vehicles`, `duration_ms`) — **never per snapshot**. WARN on partial validation failures; ERROR on DB failure.
   - **Auto-register:** INFO when a new VIN is registered (`event="vehicle_registered"`, `vin`).
   - **Auth:** WARN on rejected key (`logger="Auth"`, `event="auth_failed"`) — no secrets in the line.
   - **CSV / Backup:** INFO start/finish with counts; WARN per-row import errors (aggregated count, not one line each); ERROR on `pg_dump`/`pg_restore` failure.
   - **Startup/shutdown:** INFO with config summary (no secrets); ERROR + exit on missing required config.

**Tests:**
- JSON formatter: a log call produces valid one-line JSON with the expected keys; `request_id` is present within request scope.
- Middleware: a request emits exactly one `http_request` line with `status` + `duration_ms`; a 500 logs at ERROR with the traceback and a `request_id`.
- Ingest emits a single `ingest_batch` summary for an N-snapshot batch (assert not N lines).

**Verify:** `LOG_FORMAT=json flask --app wsgi run`; `curl` an endpoint; confirm stdout shows one structured JSON line per request with `request_id`, `endpoint`, `status`.

**Estimated size:** ~0.6k lines

---

## Step 8.2 — Alloy + Loki + Grafana for local (docker-compose)

**What to build in `deploy/observability/` and `deploy/docker-compose.yml`:**

1. **`alloy-config.alloy`:**
   - `loki.source.file` (or `loki.source.docker`) tailing the `pidrive-server` container's stdout.
   - `loki.process` — `stage.json` to lift fields from the JSON line; `stage.labels` for `component`/`level`/`env` (+ static `app`); `stage.structured_metadata` for `request_id`/`vin`/`device_id`/`endpoint`/`status`/`logger`; `stage.timestamp` from the `timestamp` field.
   - `loki.write` → Loki `/loki/api/v1/push`, with `wal { enabled = true }` (set `stability.level = "experimental"` as required), backoff retries, and `tenant_id` if used.
2. **compose services:** add `loki` (`grafana/loki`), `alloy` (`grafana/alloy`, mounts the config + the server log source), and an optional `grafana` (provisioned with a Loki datasource). Document `docker compose --profile observability up`.
3. **`.env.example`:** add `LOKI_URL`, `LOKI_TENANT` (optional), `LOG_FORMAT=json`, `ENV`.

**Verify:**
- `docker compose --profile observability up`; hit a few endpoints (and one that 500s).
- In Grafana (or `logcli`): `{app="pi-drive-server"}` shows the lines; `{app="pi-drive-server", level="error"}` shows the 500 with its `request_id`; filter by structured metadata `| endpoint="/telemetry"`.
- Stop Loki, generate logs, restart Loki → buffered logs arrive (WAL reupload).

**Estimated size:** ~0.5k lines (mostly config)

---

## Step 8.3 — Kubernetes Alloy deployment + Grafana queries doc

**What to build in `deploy/k8s/` and `deploy/observability/`:**

1. **Alloy on K8s** — choose and document one (default: sidecar for simplicity at this scale):
   - `alloy-config.yaml` (ConfigMap with the `.alloy` config), and either an Alloy **sidecar** container in `server-deployment.yaml` reading the pod's logs, or an `alloy-daemonset.yaml` collecting node pod logs. A PVC/emptyDir for the WAL directory.
   - Point `loki.write` at the cluster Loki Service, or document pointing at an existing Grafana Cloud / Loki endpoint via Secret (`LOKI_URL`, token).
2. **Optional Loki + Grafana manifests** (or a `README` note to use an existing stack): minimal `loki.yaml` + `grafana.yaml`, clearly marked optional.
3. **`deploy/observability/grafana-queries.md`** — canonical LogQL for the team:
   - All errors: `{app="pi-drive-server", level="error"}`
   - 5xx by endpoint: `{app="pi-drive-server"} | status=~"5.." | endpoint!=""`
   - Trace one request end-to-end: `{app="pi-drive-server"} | request_id="..."`
   - Ingest health: `{app="pi-drive-server", component="ingest"} |= "ingest_batch"`
   - Per-vehicle errors: `{app="pi-drive-server", level="error"} | vin="..."`
   - Auth failures: `{app="pi-drive-server"} | logger="Auth" |= "auth_failed"`
4. **CI/docs:** note in the ops `README` how to enable observability and where the WAL lives.

**Verify:**
- `kubectl apply` the Alloy config + (sidecar or daemonset) on a local cluster; generate server traffic; confirm logs land in Loki and the queries in the doc return results.
- Kill the Loki pod briefly; confirm Alloy's WAL buffers and backfills on recovery.

**Estimated size:** ~0.7k lines (YAML + config + docs)

---

## Done definition

The server emits structured JSON logs with per-request `request_id` and contextual metadata; Alloy collects and forwards them to Loki with WAL buffering that survives Loki outages; and the logs are searchable in Grafana by `request_id`/`vin`/`endpoint`/`status`/`level` using the shared schema — with ingest logged as batch summaries, never per record. The label/metadata schema matches the Android app (`pi-drive-android` Phase 13) so both data sources query uniformly in one Grafana.
