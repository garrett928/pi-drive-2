"""
L2 integration tests for the Alembic migrations against a real TimescaleDB.

Proves the production DDL actually applies: the hypertable is created, the
continuous aggregate exists and rolls up correctly, and the readiness probe
reports the DB as up.
"""

from __future__ import annotations

import pytest
from sqlalchemy import text

pytestmark = pytest.mark.integration


def test_telemetry_is_a_hypertable(db_engine, migrated_db):
    """After migrations, `telemetry` appears in Timescale's hypertable catalog."""
    with db_engine.connect() as conn:
        name = conn.execute(
            text(
                "SELECT hypertable_name FROM timescaledb_information.hypertables "
                "WHERE hypertable_name = 'telemetry'"
            )
        ).scalar()
    assert name == "telemetry"


def test_continuous_aggregate_exists(db_engine, migrated_db):
    """The telemetry_daily continuous aggregate is registered."""
    with db_engine.connect() as conn:
        view = conn.execute(
            text(
                "SELECT view_name FROM timescaledb_information.continuous_aggregates "
                "WHERE view_name = 'telemetry_daily'"
            )
        ).scalar()
    assert view == "telemetry_daily"


def test_telemetry_daily_rollup_counts(db_engine, migrated_db):
    """
    Insert rows spanning two days → the daily aggregate reports correct per-day
    counts and max speed after a manual refresh.

    Uses an AUTOCOMMIT connection because continuous-aggregate refresh needs
    committed data and cannot run inside a transaction. Cleans up its own rows.
    """
    vin = "CAGGTEST00000001"
    conn = db_engine.connect().execution_options(isolation_level="AUTOCOMMIT")
    try:
        conn.execute(
            text(
                "INSERT INTO vehicles (vin, first_seen, last_seen) "
                "VALUES (:v, now(), now()) ON CONFLICT (vin) DO NOTHING"
            ),
            {"v": vin},
        )
        rows = [
            ("2026-01-01T10:00:00Z", 50.0),  # day 1
            ("2026-01-01T11:00:00Z", 70.0),  # day 1
            ("2026-01-02T10:00:00Z", 60.0),  # day 2
        ]
        for ts, spd in rows:
            conn.execute(
                text(
                    "INSERT INTO telemetry (vin, time, speed_kmh, source) "
                    "VALUES (:v, :t, :s, 'device') "
                    "ON CONFLICT (vin, time) DO UPDATE SET speed_kmh = EXCLUDED.speed_kmh"
                ),
                {"v": vin, "t": ts, "s": spd},
            )
        conn.execute(text("CALL refresh_continuous_aggregate('telemetry_daily', NULL, NULL);"))
        result = conn.execute(
            text(
                "SELECT bucket::date::text AS day, sample_count, max_speed_kmh "
                "FROM telemetry_daily WHERE vin = :v ORDER BY bucket"
            ),
            {"v": vin},
        ).all()
    finally:
        conn.execute(text("DELETE FROM telemetry WHERE vin = :v"), {"v": vin})
        conn.execute(text("DELETE FROM vehicles WHERE vin = :v"), {"v": vin})
        conn.execute(text("CALL refresh_continuous_aggregate('telemetry_daily', NULL, NULL);"))
        conn.close()

    by_day = {day: (count, max_speed) for day, count, max_speed in result}
    assert by_day["2026-01-01"][0] == 2
    assert by_day["2026-01-02"][0] == 1
    assert by_day["2026-01-01"][1] == 70.0


def test_readyz_reports_db_up(integration_client, migrated_db):
    """With the DB reachable, /readyz returns 200 and db: ok."""
    resp = integration_client.get("/readyz")
    assert resp.status_code == 200
    assert resp.get_json() == {"status": "ok", "db": "ok"}
