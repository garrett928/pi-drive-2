# Phase 7: Containerization & Kubernetes

**Goal:** Production-grade container image, a compose stack for prod-like local runs, a complete applyable Kubernetes example with working liveness/readiness probes and a migration Job, and CI.

**Depends on:** all prior phases (ships the finished app).

**Reference:** `../REQUIREMENTS.md` §10. Health contract from §5.4 / §9: `/healthz` liveness, `/readyz` readiness (DB-checked).

---

## Step 7.1 — Production Dockerfile + docker-compose

**What to build in `deploy/`:**

1. **`Dockerfile`** (multi-stage, replaces the dev one):
   - Builder stage: install build deps, create a wheel/venv.
   - Runtime stage: `python:3.12-slim`, copy venv, install `postgresql-client` (for `pg_dump`/`pg_restore` — Phase 6), create + run as a **non-root** user, `EXPOSE 8080`, `ENTRYPOINT` runs gunicorn (`wsgi:app`, workers from `GUNICORN_WORKERS`). Healthcheck hitting `/healthz`.
2. **Migration on boot:** an entrypoint wrapper (or separate command) that runs `alembic upgrade head` before gunicorn — guarded so concurrent replicas don't race (advisory lock or a dedicated migration step). Document both options.
3. **`docker-compose.yml`** (prod-like): `pidrive-server` + `timescaledb` with named volume, env from `.env`, healthchecks, `depends_on` db healthy. Document `docker compose up` as the one-command local deploy.
4. **`.env.example`** kept current with every §10.2 variable.

**Tests/verify:**
- `docker build` succeeds; image runs as non-root.
- `docker compose up` → migrations apply → `curl /health` 200 → POST a sample payload → row stored.

---

## Step 7.2 — Kubernetes manifests

**What to build in `deploy/k8s/`** (complete, applyable example):

1. `namespace.yaml`.
2. `configmap.yaml` — non-secret config (`UI_REQUIRE_AUTH`, `LOG_LEVEL`, `MAX_BODY_BYTES`, `TELEMETRY_RETENTION_DAYS`, `GUNICORN_WORKERS`).
3. `secret.example.yaml` — `API_KEY`, `SECRET_KEY`, DB credentials / `DATABASE_URL` (documented placeholders; user supplies real values; note Sealed Secrets / external-secrets as production options).
4. `postgres-statefulset.yaml` + `postgres-service.yaml` — `timescale/timescaledb` with a `PersistentVolumeClaim` (durability + fast year-long queries). Document the alternative: point `DATABASE_URL` at a managed Postgres and skip these.
5. `server-deployment.yaml` — the app; env from ConfigMap + Secret; **`livenessProbe: /healthz`**, **`readinessProbe: /readyz`** (initialDelay/period/timeout set sensibly); CPU/mem requests+limits; `replicas` (default 1; note migration-race guard before scaling up).
6. `server-service.yaml` — ClusterIP on 8080.
7. `ingress.yaml` — example TLS ingress (host + cert annotations as placeholders).
8. `migration-job.yaml` — one-shot Job (or initContainer) running `alembic upgrade head`; document running it before/with rollout.
9. `kustomization.yaml` — ties resources together; a short `deploy/k8s/README.md` with `kubectl apply -k` instructions and the probe/secret/PVC notes.

**Verify:**
- `kubectl apply -k deploy/k8s/` on a local cluster (kind/minikube) brings up DB + server; probes pass; POST telemetry through the Service works.
- Killing the DB flips `/readyz` to 503 and the pod out of the ready set (liveness stays up).

---

## Step 7.3 — CI pipeline

**What to build (`.github/workflows/`):**

1. **`test.yml`** — on push/PR: lint (`ruff`), type-check (optional `mypy`), run `pytest` with a **TimescaleDB service container** so integration + ingest-contract tests run for real; upload coverage.
2. **`build.yml`** — build the production image (and optionally push on tag). Smoke-test: run the image + a Timescale service, `curl /health`, POST a sample payload, assert `accepted=1`.
3. Document the badge/status in `pi-drive-server/README.md` (a short ops README: how to run locally, env vars, deploy).

**Verify:** CI green on a clean branch; the ingest-contract job proves the Android wire format still works end-to-end.

**Estimated size:** ~700 lines across the phase (mostly YAML/Dockerfile).

---

## Done definition

When Phase 7 is complete, re-check `../REQUIREMENTS.md` §12 Acceptance Criteria end-to-end: app streams (single/batch/zstd, idempotent), health/probes work, type fidelity holds, the UI supports stats/manual-entry/edit/delete/CSV/backup, every UI action has an API, compose + K8s deploy cleanly, and tests pass against real Timescale.
