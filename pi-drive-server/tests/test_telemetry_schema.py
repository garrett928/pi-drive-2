"""
L1 unit tests for the telemetry wire schemas (app/schemas/telemetry.py).

The key guarantees: type fidelity (floats stay floats, rpm stays int — Java
server bug #2 regression guard), unknown keys preserved into `extra`, blank VIN
rejected, the three batch shapes normalize, and the flattening to ORM columns.
"""

from __future__ import annotations

import datetime as dt

import pytest
from pydantic import ValidationError

from app.schemas.telemetry import (
    BatchShapeError,
    TelemetryIn,
    parse_batch,
    to_orm_columns,
)
from tests.fixtures import payloads


class TestTypeFidelity:
    def test_floats_survive_as_floats(self):
        t = TelemetryIn.model_validate(payloads.payload_type_fidelity())
        assert t.obd.throttle_pct == 33.7  # not truncated to 33
        assert isinstance(t.obd.throttle_pct, float)
        assert t.obd.battery_voltage == 14.27

    def test_rpm_is_int(self):
        t = TelemetryIn.model_validate(payloads.single_payload())
        assert t.obd.rpm == 2400
        assert isinstance(t.obd.rpm, int)

    def test_orm_row_preserves_types(self):
        row = to_orm_columns(TelemetryIn.model_validate(payloads.payload_type_fidelity()))
        assert row["rpm"] == 3175 and isinstance(row["rpm"], int)
        assert row["throttle_pct"] == 33.7


class TestExtraPreservation:
    def test_unknown_keys_land_in_extra(self):
        t = TelemetryIn.model_validate(payloads.payload_with_extra_fields())
        row = to_orm_columns(t)
        assert row["extra"]["some_future_top_level"] == "preserve-me"
        assert row["extra"]["obd"]["future_pid_x"] == 999

    def test_no_unknown_keys_means_null_extra(self):
        row = to_orm_columns(TelemetryIn.model_validate(payloads.single_payload()))
        assert row["extra"] is None


class TestValidation:
    def test_blank_vin_rejected(self):
        p = payloads.single_payload()
        p["vin"] = "   "
        with pytest.raises(ValidationError, match="vin"):
            TelemetryIn.model_validate(p)

    def test_missing_vin_rejected(self):
        with pytest.raises(ValidationError):
            TelemetryIn.model_validate(payloads.payload_missing_vin())

    def test_timestamp_parses_aware(self):
        t = TelemetryIn.model_validate(payloads.single_payload())
        assert t.timestamp.tzinfo is not None
        assert t.timestamp == dt.datetime(
            2026, 5, 24, 22, 15, 30, 123000, tzinfo=dt.UTC
        )

    def test_naive_timestamp_assumed_utc(self):
        p = payloads.single_payload()
        p["timestamp"] = "2026-05-24T22:15:30"
        t = TelemetryIn.model_validate(p)
        assert t.timestamp.tzinfo == dt.UTC

    def test_events_parse(self):
        t = TelemetryIn.model_validate(payloads.single_payload())
        assert len(t.events) == 2
        assert t.events[0].strategy == "ACCELERATION"
        assert t.events[1].peak_g == 0.51
        assert t.events[1].sources == ["OBD", "GPS", "ACCELEROMETER"]


class TestBatchShapes:
    def test_single_object(self):
        items = parse_batch(payloads.single_payload())
        assert len(items) == 1

    def test_bare_array(self):
        items = parse_batch(payloads.batch_bare_array(3))
        assert len(items) == 3
        assert len({i.timestamp for i in items}) == 3

    def test_wrapped(self):
        items = parse_batch(payloads.batch_wrapped(2))
        assert len(items) == 2

    def test_garbage_shape_rejected(self):
        with pytest.raises(BatchShapeError):
            parse_batch("just a string")

    def test_empty_batch_rejected(self):
        with pytest.raises(BatchShapeError):
            parse_batch([])


class TestOrmFlattening:
    def test_full_payload_flattens(self):
        row = to_orm_columns(TelemetryIn.model_validate(payloads.single_payload()))
        assert row["vin"] == payloads.CANONICAL_VIN
        assert row["lat"] == 37.7749
        assert row["speed_kmh"] == 105
        assert row["fuel_economy_mpg"] == 28.5
        assert row["accel_mps2"] == 0.45
        assert row["fuel_rate_lph"] is None  # explicit null on the wire
        assert row["source"] == "device"

    def test_device_id_fallback_from_header(self):
        p = payloads.single_payload()
        del p["device_id"]
        row = to_orm_columns(
            TelemetryIn.model_validate(p), device_id_fallback="pd-header"
        )
        assert row["device_id"] == "pd-header"

    def test_absent_nested_objects_yield_nulls(self):
        p = {"vin": "V123", "timestamp": "2026-01-01T00:00:00Z"}
        row = to_orm_columns(TelemetryIn.model_validate(p))
        assert row["lat"] is None and row["rpm"] is None
