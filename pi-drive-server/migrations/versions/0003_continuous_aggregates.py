"""continuous aggregate: telemetry_daily + refresh policy

Revision ID: 0003
Revises: 0002
Create Date: 2026-06-09

A per-vehicle, per-day rollup powering the stats UI (REQUIREMENTS.md §6.4, §9).
Querying a year of daily buckets is fast because Timescale materializes the
aggregate incrementally instead of scanning raw telemetry rows.

Runs under the AUTOCOMMIT connection configured in env.py — continuous-aggregate
creation and policy registration cannot run inside a transaction block.
"""

from typing import Sequence, Union

from alembic import op

revision: str = "0003"
down_revision: Union[str, None] = "0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Daily rollup per vehicle. WITH NO DATA → created empty; the policy below
    # (and manual refresh in tests) materializes it.
    op.execute(
        """
        CREATE MATERIALIZED VIEW telemetry_daily
        WITH (timescaledb.continuous) AS
        SELECT
            vin,
            time_bucket('1 day', time)     AS bucket,
            count(*)                       AS sample_count,
            avg(speed_kmh)                 AS avg_speed_kmh,
            max(speed_kmh)                 AS max_speed_kmh,
            avg(fuel_economy_mpg)          AS avg_fuel_economy_mpg,
            min(time)                      AS first_time,
            max(time)                      AS last_time
        FROM telemetry
        GROUP BY vin, bucket
        WITH NO DATA;
        """
    )

    # Real-time aggregation: queries against the view union the materialized
    # buckets with not-yet-materialized raw rows, so stats are always current.
    # (Recent TimescaleDB versions default to materialized_only = true, which
    # would hide rows ingested since the last refresh — wrong for our stats.)
    op.execute(
        "ALTER MATERIALIZED VIEW telemetry_daily "
        "SET (timescaledb.materialized_only = false);"
    )

    # Keep the aggregate fresh: refresh buckets from 30 days ago up to 1 hour ago,
    # hourly. (The most-recent hour stays live via real-time aggregation above.)
    op.execute(
        """
        SELECT add_continuous_aggregate_policy('telemetry_daily',
            start_offset      => INTERVAL '30 days',
            end_offset        => INTERVAL '1 hour',
            schedule_interval => INTERVAL '1 hour',
            if_not_exists     => TRUE);
        """
    )


def downgrade() -> None:
    op.execute("DROP MATERIALIZED VIEW IF EXISTS telemetry_daily;")
