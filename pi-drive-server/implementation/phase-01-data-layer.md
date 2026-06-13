# Phase 1: Data Layer

**Goal:** The persistence foundation — typed SQLAlchemy models, Alembic migrations, the TimescaleDB hypertable + continuous aggregates that keep a year of telemetry fast, and a thin service/repository scaffold so later phases never touch the ORM from routes.

**Depends on:** Phase 0.

**Reference:** `../REQUIREMENTS.md` §4 (data model), §2 (why Timescale), §3 (layering). Fixes Java-server bugs #2 (long truncation), #3 (1-year registry), #6 (string concat). See `../SERVER_DEVELOPER_DOCS.md`.

---

## Step 1.1 — SQLAlchemy models

**What to build in `app/db/models.py`** (SQLAlchemy 2.x typed `Mapped[]`):

1. **`Vehicle`** — table `vehicles`, per §4.1. PK `vin: str`. Columns: `device_id`, `make`, `model`, `year`, `nickname`, `first_seen`, `last_seen`, `created_at`. Relationship to telemetry/events (no ORM-level cascade needed if DB FKs cascade; document the choice).
2. **`Telemetry`** — table `telemetry`, per §4.2. Composite PK `(vin, time)`. All known signal columns with correct types (`Integer` for `rpm`, `Float`/`double precision` for the rest), `extra: JSONB`, `source: str` (enum-like: `device|manual|csv`). FK `vin → vehicles.vin` `ON DELETE CASCADE`.
3. **`DrivingEvent`** — table `driving_events`, per §4.3. PK `id` bigint identity, FK `vin`, `time`, `strategy`, `type`, the optional metric columns, `sources: ARRAY(Text)`, `source`.
4. **Enums** — Python `StrEnum` for `Source` (`DEVICE/MANUAL/CSV`) and `Strategy` (`ACCELERATION/G_FORCE`); store as text.

Every model and column gets a docstring/comment tying it to the wire field it came from.

**Tests (`tests/test_models.py`, unit — no DB needed for these):**
- Model metadata: `Telemetry` PK is `(vin, time)`; types are int/float as specified (assert column types).
- `Source`/`Strategy` enum values match the wire strings.

**Verify:** models import cleanly; `Base.metadata` lists the three tables.

---

## Step 1.2 — Alembic + TimescaleDB hypertable + continuous aggregates

**What to build:**

1. **`migrations/`** — initialize Alembic against `Base.metadata`; `env.py` reads `DATABASE_URL` from config.
2. **Migration 0001 — base schema:** create `vehicles`, `telemetry`, `driving_events` with FKs, indexes (`telemetry(vin, time desc)`, `driving_events(vin, time)`).
3. **Migration 0002 — Timescale:** `CREATE EXTENSION IF NOT EXISTS timescaledb;` then `SELECT create_hypertable('telemetry', 'time', ...)` (chunk interval e.g. 7 days). Document that the hypertable partition column must be part of the PK (`(vin, time)` satisfies this).
4. **Migration 0003 — continuous aggregates:** a daily rollup materialized view per vehicle powering the stats UI — e.g. `telemetry_daily` with `time_bucket('1 day', time)`, `vin`, `count(*)`, `avg/max(speed_kmh)`, `avg(fuel_economy_mpg)`, min/max time. Add a refresh policy. Document why (fast year-long dashboard queries without scanning raw rows).
5. **`app/db/timescale.py`** — small helpers wrapping the Timescale-specific DDL so migrations and tests stay readable; a `add_retention_policy(days)` helper (used in Phase 6).
6. Wire `/readyz` (from 0.2) to actually `SELECT 1` against the DB and return `503` on failure.

**Tests (`tests/test_migrations.py`, `@pytest.mark.integration`, real Timescale):**
- Run all migrations up → tables + hypertable exist (`timescaledb_information.hypertables` shows `telemetry`).
- Insert rows spanning days → `telemetry_daily` returns correct per-day counts after refresh.
- `/readyz` returns 200 with DB up.

**Verify:** `alembic upgrade head` against the compose DB succeeds; `\d telemetry` shows the hypertable.

---

## Step 1.3 — Service layer scaffold + repositories

**What to build in `app/services/`** (the layer that owns all DB access — §3 rule):

1. **`app/db/session.py`** — request-scoped session management (SQLAlchemy `sessionmaker`, Flask teardown closes it). A `@with_session` helper or a `g`-bound session.
2. **`vehicle_service.py`** — stubs with real signatures + docstrings: `upsert_vehicle(vin, device_id, *, seen_at)`, `get(vin)`, `list(limit, offset)`, `update_metadata(vin, **fields)`, `delete(vin)`. Implement `upsert_vehicle` (insert-or-update `last_seen`/`device_id`, set `first_seen` on insert) now — it's needed by ingest.
3. **`telemetry_service.py`** — stubs: `upsert_snapshot(...)`, `insert_events(...)`, `latest_timestamp(vin)`, `query(...)`, `get_one(...)`, `update_one(...)`, `delete_one(...)`, `delete_range(...)`. Implement `latest_timestamp` now (simple `MAX(time)` query) since the ingest phase needs it.
4. **`stats_service.py`** — stub `fleet_stats()`, `vehicle_stats(vin, start, end)` reading from the continuous aggregate.

Keep each service free of HTTP concerns (no `request`/`jsonify`); they take/return plain Python and ORM/dataclass objects.

**Tests (`tests/test_vehicle_service.py`, integration):**
- `upsert_vehicle` twice for same VIN → one row; `last_seen` advances; `first_seen` unchanged.
- `latest_timestamp` returns the max time, `None` for unknown VIN.

**Verify:** services import; the two implemented methods work against the compose DB.

**Estimated size:** ~900 lines across the phase.
