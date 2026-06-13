#!/usr/bin/env sh
#
# Container entrypoint: apply database migrations, then serve via gunicorn.
#
# Migrations run under a Postgres advisory lock (see deploy/run_migrations.py)
# so that when multiple replicas start at once, exactly one applies the schema
# and the rest wait, then no-op. For Kubernetes you can instead disable this
# (RUN_MIGRATIONS=0) and run migrations as a one-shot Job (deploy/k8s/
# migration-job.yaml) — both paths are safe.
set -eu

if [ "${RUN_MIGRATIONS:-1}" = "1" ]; then
    echo "entrypoint: applying database migrations (advisory-locked)…"
    python deploy/run_migrations.py
else
    echo "entrypoint: RUN_MIGRATIONS=0 — skipping in-container migrations."
fi

WORKERS="${GUNICORN_WORKERS:-2}"
echo "entrypoint: starting gunicorn (${WORKERS} workers) on :8080"
# No --access-logfile: the app emits its own structured `http_request` JSON line
# per request (logging_config.py). gunicorn's plain-text access log would only
# pollute the JSON stream that Alloy parses. Keep gunicorn's error log (startup
# / worker lifecycle) on stderr.
exec gunicorn wsgi:app \
    --bind 0.0.0.0:8080 \
    --workers "${WORKERS}" \
    --error-logfile - \
    --forwarded-allow-ips '*'
