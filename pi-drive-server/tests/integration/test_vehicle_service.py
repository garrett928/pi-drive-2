"""
L2 integration tests for the vehicle + telemetry services against a real DB.

Exercises the two methods implemented in Phase 1 — `upsert_vehicle` (idempotent
auto-registration) and `latest_timestamp` — using the transaction-isolated
`db_session` fixture (writes roll back after each test).
"""

from __future__ import annotations

import datetime as dt

import pytest

from app.db.models import Telemetry, Vehicle
from app.services import telemetry_service, vehicle_service

pytestmark = pytest.mark.integration

UTC = dt.UTC


def test_upsert_vehicle_inserts_then_updates(db_session):
    """First upsert inserts; a later upsert updates the same row in place."""
    vin = "TESTVIN0000000001"
    t1 = dt.datetime(2026, 1, 1, 12, 0, tzinfo=UTC)
    t2 = dt.datetime(2026, 1, 2, 12, 0, tzinfo=UTC)

    v1 = vehicle_service.upsert_vehicle(vin, "dev-a", seen_at=t1, session=db_session)
    assert v1.vin == vin
    assert v1.first_seen == t1
    assert v1.last_seen == t1
    assert v1.device_id == "dev-a"

    v2 = vehicle_service.upsert_vehicle(vin, "dev-b", seen_at=t2, session=db_session)

    # Still exactly one row (upsert, not duplicate).
    assert db_session.query(Vehicle).filter_by(vin=vin).count() == 1
    assert v2.last_seen == t2  # advanced
    assert v2.first_seen == t1  # unchanged
    assert v2.device_id == "dev-b"  # most recent reporting device


def test_upsert_vehicle_keeps_earliest_first_seen(db_session):
    """An out-of-order (earlier) sighting lowers first_seen but not last_seen."""
    vin = "TESTVIN0000000002"
    later = dt.datetime(2026, 3, 1, tzinfo=UTC)
    earlier = dt.datetime(2026, 2, 1, tzinfo=UTC)

    vehicle_service.upsert_vehicle(vin, "d", seen_at=later, session=db_session)
    v = vehicle_service.upsert_vehicle(vin, "d", seen_at=earlier, session=db_session)

    assert v.first_seen == earlier  # least(stored, incoming)
    assert v.last_seen == later  # greatest(stored, incoming)


def test_upsert_vehicle_keeps_device_id_when_new_is_null(db_session):
    """A sighting with no device_id does not wipe the stored device_id."""
    vin = "TESTVIN0000000004"
    t1 = dt.datetime(2026, 1, 1, tzinfo=UTC)
    t2 = dt.datetime(2026, 1, 2, tzinfo=UTC)

    vehicle_service.upsert_vehicle(vin, "dev-a", seen_at=t1, session=db_session)
    v = vehicle_service.upsert_vehicle(vin, None, seen_at=t2, session=db_session)

    assert v.device_id == "dev-a"


def test_latest_timestamp(db_session):
    """latest_timestamp returns the max time for a VIN, None for an unknown VIN."""
    vin = "TESTVIN0000000003"
    t1 = dt.datetime(2026, 1, 1, 12, 0, tzinfo=UTC)
    t2 = dt.datetime(2026, 1, 1, 12, 5, tzinfo=UTC)

    # Vehicle must exist first (telemetry FK → vehicles).
    vehicle_service.upsert_vehicle(vin, "d", seen_at=t1, session=db_session)
    db_session.add(Telemetry(vin=vin, time=t1, speed_kmh=10.0, source="device"))
    db_session.add(Telemetry(vin=vin, time=t2, speed_kmh=20.0, source="device"))
    db_session.flush()

    assert telemetry_service.latest_timestamp(vin, session=db_session) == t2
    assert telemetry_service.latest_timestamp("NO_SUCH_VIN", session=db_session) is None
