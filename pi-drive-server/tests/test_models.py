"""
L1 unit tests for the ORM models (no database required).

Asserts the schema-level guarantees that protect the wire contract and the
Timescale requirements: the telemetry composite PK, int-vs-float type fidelity,
and the enum string values.
"""

from __future__ import annotations

import sqlalchemy as sa

from app.db.models import Base, DrivingEvent, Source, Strategy, Telemetry, Vehicle


class TestMetadata:
    def test_three_tables_registered(self):
        """Base.metadata lists exactly the three core tables."""
        assert set(Base.metadata.tables) == {"vehicles", "telemetry", "driving_events"}


class TestTelemetry:
    def test_primary_key_is_vin_time(self):
        """The idempotency key is the composite PK (vin, time)."""
        pk_cols = {c.name for c in Telemetry.__table__.primary_key.columns}
        assert pk_cols == {"vin", "time"}

    def test_rpm_is_integer(self):
        """rpm stays an Integer (no blanket-float; Java bug #2 fix)."""
        assert isinstance(Telemetry.__table__.c.rpm.type, sa.Integer)

    def test_float_signals_are_double(self):
        """Continuous signals are DOUBLE PRECISION, not truncated to int."""
        for col in ("speed_kmh", "throttle_pct", "battery_voltage", "fuel_economy_mpg", "accel_mps2"):
            assert isinstance(Telemetry.__table__.c[col].type, sa.Double), col

    def test_extra_is_jsonb(self):
        from sqlalchemy.dialects.postgresql import JSONB

        assert isinstance(Telemetry.__table__.c.extra.type, JSONB)

    def test_vin_foreign_key_cascades(self):
        """Deleting a vehicle cascades to its telemetry at the DB level."""
        fks = list(Telemetry.__table__.c.vin.foreign_keys)
        assert len(fks) == 1
        assert fks[0].ondelete == "CASCADE"
        assert fks[0].column.table.name == "vehicles"


class TestDrivingEvent:
    def test_id_is_bigint(self):
        assert isinstance(DrivingEvent.__table__.c.id.type, sa.BigInteger)

    def test_sources_is_text_array(self):
        from sqlalchemy.dialects.postgresql import ARRAY

        assert isinstance(DrivingEvent.__table__.c.sources.type, ARRAY)


class TestVehicle:
    def test_pk_is_vin(self):
        pk_cols = {c.name for c in Vehicle.__table__.primary_key.columns}
        assert pk_cols == {"vin"}


class TestEnums:
    def test_source_values_match_wire(self):
        assert Source.DEVICE.value == "device"
        assert Source.MANUAL.value == "manual"
        assert Source.CSV.value == "csv"

    def test_strategy_values_match_wire(self):
        assert Strategy.ACCELERATION.value == "ACCELERATION"
        assert Strategy.G_FORCE.value == "G_FORCE"
