# Pi Drive Server — Kubernetes deployment

A complete, applyable example. It brings up TimescaleDB (StatefulSet + PVC) and
the Flask/gunicorn server (Deployment + Service + Ingress), with working
liveness/readiness probes and migrations handled on pod boot.

## Quick start

```bash
# 1. Provide secrets (never commit real values).
cp deploy/k8s/secret.example.yaml deploy/k8s/secret.yaml
#   edit secret.yaml: set API_KEY, SECRET_KEY, DATABASE_URL, POSTGRES_PASSWORD
#   (DATABASE_URL password must match POSTGRES_PASSWORD when using the bundled DB)
kubectl apply -f deploy/k8s/secret.yaml

# 2. Apply everything else.
kubectl apply -k deploy/k8s/

# 3. Watch it come up.
kubectl -n pi-drive get pods -w
```

The server pod becomes **Ready** only after migrations apply and `/readyz`
returns 200 (DB reachable).

## Image

The manifests reference `pidrive-server:latest`. Point them at your registry:

```bash
cd deploy/k8s
kustomize edit set image pidrive-server=ghcr.io/you/pidrive-server:v1.2.3
# or pass --set on apply with a kustomize-capable kubectl
```

For a local cluster (kind/minikube), load the locally-built image:

```bash
docker build -f deploy/Dockerfile -t pidrive-server:latest .
kind load docker-image pidrive-server:latest        # kind
minikube image load pidrive-server:latest           # minikube
```

## Health probes (§5.4 / §9)

- **livenessProbe → `/healthz`** — always 200, no DB dependency. Keeps a wedged
  process from lingering without coupling restarts to the database.
- **readinessProbe → `/readyz`** — runs `SELECT 1`. Returns **503 when the DB is
  down**, so Kubernetes pulls the pod from the Service (no traffic) but does
  **not** restart it; it rejoins automatically when the DB recovers.

## Migrations

Two safe options:

1. **On boot (default).** `RUN_MIGRATIONS=1` (ConfigMap) → the entrypoint runs
   `alembic upgrade head` under a Postgres advisory lock, so concurrent
   replicas don't race. No extra step.
2. **One-shot Job.** Set `RUN_MIGRATIONS=0`, then before each rollout:
   ```bash
   kubectl apply -f deploy/k8s/migration-job.yaml
   kubectl -n pi-drive wait --for=condition=complete job/pidrive-migrate --timeout=120s
   ```

## Database options

- **Bundled (default):** `postgres-statefulset.yaml` runs `timescale/timescaledb`
  with a 10Gi PVC. Durable and fast for year-long queries.
- **Managed:** delete `postgres-statefulset.yaml` + `postgres-service.yaml` from
  `kustomization.yaml` and point `DATABASE_URL` at your managed Postgres/Timescale.

## Secrets in production

The plain Secret is for the example only. In production use **Sealed Secrets**,
the **External Secrets Operator**, or your cloud's secret manager. `secret.yaml`
is gitignored.

## Scaling

`server-deployment.yaml` defaults to `replicas: 1`. Scaling up is safe — boot
migrations are advisory-locked — but the bundled Postgres StatefulSet is a
single instance; for HA, use a managed/replicated database.

## Observability (Phase 8)

Log shipping (Alloy → Loki) is layered on separately; see
`deploy/observability/` and `deploy/k8s/alloy-*.yaml`.
