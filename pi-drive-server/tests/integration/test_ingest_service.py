"""
L2 integration tests for the ingest write path (telemetry_service) against a
real TimescaleDB, using the transaction-isolated db_session fixture.
"""

from __future__ import annotations

import pytest
from sqlalchemy import select

from app.db.models import DrivingEvent, Telemetry, Vehicle
from app.schemas.telemetry import TelemetryIn, parse_batch
from app.services import telemetry_service
from app.services.telemetry_service import DeviceIdMismatchError
from tests.fixtures import payloads

pytestmark = pytest.mark.integration

VIN = payloads.CANONICAL_VIN


def _ingest(db_session, data, **kwargs):
    items = parse_batch(data)
    return telemetry_service.ingest_batch(items, session=db_session, **kwargs)


def test_single_payload_stores_vehicle_snapshot_events(db_session):
    accepted, vins = _ingest(db_session, payloads.single_payload())

    assert accepted == 1
    assert vins == {VIN}
    vehicle = db_session.get(Vehicle, VIN)
    assert vehicle is not None
    assert vehicle.first_seen is not None
    rows = db_session.execute(select(Telemetry).where(Telemetry.vin == VIN)).scalars().all()
    assert len(rows) == 1
    assert rows[0].rpm == 2400  # int fidelity
    assert rows[0].throttle_pct == 22.5  # float fidelity
    events = db_session.execute(
        select(DrivingEvent).where(DrivingEvent.vin == VIN)
    ).scalars().all()
    assert len(events) == 2


def test_reingest_is_idempotent(db_session):
    """Same (vin, time) twice → one telemetry row, no duplicated events."""
    _ingest(db_session, payloads.single_payload())
    _ingest(db_session, payloads.single_payload())

    rows = db_session.execute(select(Telemetry).where(Telemetry.vin == VIN)).scalars().all()
    assert len(rows) == 1
    events = db_session.execute(
        select(DrivingEvent).where(DrivingEvent.vin == VIN)
    ).scalars().all()
    assert len(events) == 2  # deduped on (vin, time, strategy, type)


def test_reingest_updates_values(db_session):
    """A re-upload with changed values wins (last-write-wins upsert)."""
    _ingest(db_session, payloads.single_payload())
    changed = payloads.single_payload()
    changed["obd"]["rpm"] = 3000
    _ingest(db_session, changed)

    row = db_session.execute(
        select(Telemetry).where(Telemetry.vin == VIN)
    ).scalar_one()
    assert row.rpm == 3000


def test_batch_accepts_three(db_session):
    accepted, vins = _ingest(db_session, payloads.batch_bare_array(3))
    assert accepted == 3
    rows = db_session.execute(select(Telemetry).where(Telemetry.vin == VIN)).scalars().all()
    assert len(rows) == 3  # distinct timestamps → distinct rows


def test_new_vin_autoregisters_with_seen_window(db_session):
    batch = payloads.batch_bare_array(3)
    _ingest(db_session, batch)
    vehicle = db_session.get(Vehicle, VIN)
    times = sorted(TelemetryIn.model_validate(b).timestamp for b in batch)
    assert vehicle.first_seen == times[0]
    assert vehicle.last_seen == times[-1]


def test_device_id_mismatch_raises(db_session):
    with pytest.raises(DeviceIdMismatchError):
        _ingest(
            db_session,
            payloads.single_payload(),  # body says pd-rxv7a3-k9892
            device_id_header="pd-DIFFERENT",
        )


def test_device_id_header_agreeing_is_fine(db_session):
    accepted, _ = _ingest(
        db_session,
        payloads.single_payload(),
        device_id_header=payloads.CANONICAL_DEVICE_ID,
    )
    assert accepted == 1


def test_extra_fields_persist_to_jsonb(db_session):
    _ingest(db_session, payloads.payload_with_extra_fields())
    row = db_session.execute(select(Telemetry).where(Telemetry.vin == VIN)).scalar_one()
    assert row.extra["some_future_top_level"] == "preserve-me"
    assert row.extra["obd"]["future_pid_x"] == 999


def test_manual_source_recorded(db_session):
    _ingest(db_session, payloads.single_payload(), source="manual")
    row = db_session.execute(select(Telemetry).where(Telemetry.vin == VIN)).scalar_one()
    assert row.source == "manual"
