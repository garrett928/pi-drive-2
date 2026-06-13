"""
TimescaleDB DDL helpers.

Small builders returning the Timescale-specific SQL used by migrations (and by
tests/maintenance), so the migration files stay readable. These emit trusted,
internal DDL only — never interpolate user input through them.

Why Timescale (REQUIREMENTS.md §2): telemetry is a hypertable partitioned on
`time`, and a daily continuous aggregate keeps year-long dashboard queries fast
without scanning raw rows.

Note: continuous-aggregate creation, policy registration, and manual refresh
**cannot run inside a transaction block** — the Alembic env runs migrations with
an AUTOCOMMIT connection so these succeed (see `migrations/env.py`).
"""

from __future__ import annotations

import logging

from sqlalchemy import text
from sqlalchemy.engine import Engine

logger = logging.getLogger("PiDriveServer")


def enable_extension() -> str:
    """Enable the TimescaleDB extension (idempotent)."""
    return "CREATE EXTENSION IF NOT EXISTS timescaledb;"


def create_hypertable(table: str, time_column: str, chunk_interval: str = "7 days") -> str:
    """
    Convert a regular table into a hypertable partitioned on `time_column`.

    The partition column must be part of the table's primary key (Timescale
    requirement); `telemetry`'s `(vin, time)` PK satisfies this.
    """
    return (
        f"SELECT create_hypertable('{table}', '{time_column}', "
        f"chunk_time_interval => INTERVAL '{chunk_interval}', if_not_exists => TRUE);"
    )


def add_retention_policy(table: str, days: int) -> str:
    """
    Drop chunks older than `days`. Used in Phase 6 (data lifecycle); kept here so
    all Timescale DDL lives in one place.
    """
    return (
        f"SELECT add_retention_policy('{table}', INTERVAL '{days} days', "
        f"if_not_exists => TRUE);"
    )


def refresh_continuous_aggregate(view: str, start: str = "NULL", end: str = "NULL") -> str:
    """
    Materialize a continuous aggregate over the given window (NULL = unbounded).

    Must run outside a transaction block (use an AUTOCOMMIT connection).
    """
    return f"CALL refresh_continuous_aggregate('{view}', {start}, {end});"


def ensure_retention_policy(engine: Engine, days: int, table: str = "telemetry") -> None:
    """
    Apply (idempotently) a TimescaleDB retention policy dropping chunks older
    than `days`. Called from the app factory on startup when
    `TELEMETRY_RETENTION_DAYS` is set (§6.2). `days` comes from validated config
    (an int), never request input.

    Policy registration runs a background job and cannot be in a transaction
    block — hence AUTOCOMMIT. Failures (e.g. the hypertable not yet migrated)
    are logged, not fatal: telemetry ingest must still start. Idempotent via
    `if_not_exists => TRUE`.
    """
    try:
        with engine.connect() as connection:
            connection.execution_options(isolation_level="AUTOCOMMIT")
            connection.execute(text(add_retention_policy(table, days)))
        logger.info("Applied retention policy: drop %s chunks older than %d days", table, days)
    except Exception as exc:  # noqa: BLE001 — startup must survive a DDL hiccup
        logger.warning(
            "Could not apply retention policy on '%s' (%d days): %s. "
            "Is the database migrated? Ingest will still start.",
            table, days, exc,
        )
