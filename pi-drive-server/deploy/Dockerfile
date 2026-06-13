# Pi Drive Server — production image (multi-stage).
#
# Build context: the pi-drive-server/ directory.
# Usage:  docker build -f deploy/Dockerfile -t pidrive-server .
#
# Stage 1 (builder) compiles dependencies into a self-contained virtualenv.
# Stage 2 (runtime) is a slim image carrying only that venv + the pg16 client,
# runs as a non-root user, and serves via gunicorn. The entrypoint applies
# Alembic migrations (under a Postgres advisory lock, so concurrent replicas
# don't race) before exec'ing gunicorn.

# ── Stage 1: builder ────────────────────────────────────────────────────────────
FROM python:3.12-slim AS builder

# Build deps for any wheels that need compiling (psycopg, zstandard ship wheels,
# but keep gcc available for safety on other arches).
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
    && rm -rf /var/lib/apt/lists/*

# Self-contained venv we can copy wholesale into the runtime stage.
ENV VIRTUAL_ENV=/opt/venv
RUN python -m venv "$VIRTUAL_ENV"
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

# ── Stage 2: runtime ────────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime

# postgresql-client-16 for pg_dump / pg_restore (backup feature, §8). The major
# version MUST match the server (timescale/timescaledb pg16): a newer pg_dump
# emits GUCs the pg16 server rejects on restore. Pull it from the PGDG repo.
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl gnupg \
    && install -d /usr/share/postgresql-common/pgdg \
    && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
        -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
    && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt $(. /etc/os-release && echo $VERSION_CODENAME)-pgdg main" \
        > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update && apt-get install -y --no-install-recommends \
        postgresql-client-16 \
    && apt-get purge -y curl gnupg \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

ENV VIRTUAL_ENV=/opt/venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"
COPY --from=builder "$VIRTUAL_ENV" "$VIRTUAL_ENV"

WORKDIR /app
COPY . .

# Run as a non-root user. gunicorn binds 8080 (>1024), so no privileges needed.
RUN useradd --create-home --uid 10001 pidrive \
    && chown -R pidrive:pidrive /app
USER pidrive

ENV FLASK_APP=wsgi.py \
    GUNICORN_WORKERS=2 \
    PYTHONUNBUFFERED=1

EXPOSE 8080

# Liveness from inside the container (no external curl dependency in the slim image:
# use python). /healthz is always-200 and DB-independent.
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD ["python", "-c", "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8080/healthz', timeout=3).status==200 else 1)"]

ENTRYPOINT ["deploy/entrypoint.sh"]
