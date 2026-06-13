"""
SQLAlchemy ORM models for Pi Drive Server.

Typed SQLAlchemy 2.x `Mapped[]` models, one per table in REQUIREMENTS.md §4:
  - Vehicle        (vehicles)        §4.1 — VIN-first identity, mutable metadata
  - Telemetry      (telemetry)       §4.2 — one row per snapshot; TimescaleDB hypertable
  - DrivingEvent   (driving_events)  §4.3 — hard accel/brake events

Type fidelity (fixes Java server Known Bug #2): ints are `Integer`, everything
else numeric is `Double` (PostgreSQL double precision) — no blanket truncation.

Each column documents the wire field (REQUIREMENTS.md §5.5) it maps from.
Alembic targets `Base.metadata`; the hypertable + continuous aggregate are added
by migrations (the ORM only knows the logical schema).
"""

from __future__ import annotations

import datetime as dt
from enum import StrEnum

from sqlalchemy import (
    BigInteger,
    DateTime,
    Double,
    ForeignKey,
    Integer,
    Text,
    text,
)
from sqlalchemy.dialects.postgresql import ARRAY, JSONB
from sqlalchemy.orm import (
    DeclarativeBase,
    Mapped,
    mapped_column,
    relationship,
)


class Base(DeclarativeBase):
    """Declarative base for all ORM models. Alembic migrations target Base.metadata."""


class Source(StrEnum):
    """Provenance of a row, stored as text. Mirrors the wire/CSV/manual origins."""

    DEVICE = "device"
    MANUAL = "manual"
    CSV = "csv"


class Strategy(StrEnum):
    """Driving-event detection strategy, stored as text (matches the wire strings)."""

    ACCELERATION = "ACCELERATION"
    G_FORCE = "G_FORCE"


class Vehicle(Base):
    """
    A vehicle, identified by VIN (REQUIREMENTS.md §4.1).

    Identity is VIN-first; vehicles are auto-registered on first telemetry.
    Metadata (make/model/year/nickname) is user-editable and never baked into
    historical telemetry (telemetry references the vehicle by `vin` FK), so
    editing display metadata cannot corrupt stored data.
    """

    __tablename__ = "vehicles"

    vin: Mapped[str] = mapped_column(Text, primary_key=True, doc="Vehicle Identification Number (wire: vin).")
    device_id: Mapped[str | None] = mapped_column(Text, nullable=True, doc="Last device that reported for this VIN.")
    make: Mapped[str | None] = mapped_column(Text, nullable=True, doc="Optional, user-editable.")
    model: Mapped[str | None] = mapped_column(Text, nullable=True, doc="Optional, user-editable.")
    year: Mapped[int | None] = mapped_column(Integer, nullable=True, doc="Optional, user-editable.")
    nickname: Mapped[str | None] = mapped_column(Text, nullable=True, doc="User-editable display name.")
    first_seen: Mapped[dt.datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, doc="First telemetry timestamp received."
    )
    last_seen: Mapped[dt.datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, doc="Most recent telemetry timestamp received."
    )
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=text("now()"), nullable=False, doc="Row creation time."
    )

    # Cascade is enforced at the DB level (ON DELETE CASCADE on the FKs below);
    # passive_deletes=True tells the ORM not to emit its own DELETEs and to let
    # Postgres handle the cascade — one authoritative cascade path.
    telemetry: Mapped[list[Telemetry]] = relationship(
        back_populates="vehicle", passive_deletes=True
    )
    events: Mapped[list[DrivingEvent]] = relationship(
        back_populates="vehicle", passive_deletes=True
    )


class Telemetry(Base):
    """
    One telemetry snapshot (REQUIREMENTS.md §4.2). The TimescaleDB hypertable.

    Composite primary key `(vin, time)` is the idempotency key: re-uploading the
    same snapshot upserts rather than duplicates. `time` is the hypertable
    partition column and must be part of the PK (Timescale requirement) — which
    `(vin, time)` satisfies. Known signals are typed columns; anything
    unrecognized is preserved verbatim in `extra` (JSONB) for forward-compat.
    """

    __tablename__ = "telemetry"

    # ── Identity / partition ──────────────────────────────────────────────────
    vin: Mapped[str] = mapped_column(
        Text,
        ForeignKey("vehicles.vin", ondelete="CASCADE"),
        primary_key=True,
        doc="Owning vehicle (wire: vin).",
    )
    time: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), primary_key=True, doc="Snapshot time / partition key (wire: timestamp)."
    )

    device_id: Mapped[str | None] = mapped_column(Text, nullable=True, doc="wire: device_id / X-Device-Id")

    # ── Location ──────────────────────────────────────────────────────────────
    lat: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: location.lat")
    lng: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: location.lng")
    speed_gps: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: location.speed_gps")

    # ── OBD signals ───────────────────────────────────────────────────────────
    speed_kmh: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.speed_kmh")
    rpm: Mapped[int | None] = mapped_column(Integer, nullable=True, doc="wire: obd.rpm (int — no truncation)")
    coolant_temp_c: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.coolant_temp_c")
    intake_air_temp_c: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.intake_air_temp_c")
    throttle_pct: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.throttle_pct")
    fuel_level_pct: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.fuel_level_pct")
    oil_temp_c: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.oil_temp_c")
    maf_gps: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.maf_gps")
    fuel_rate_lph: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.fuel_rate_lph")
    battery_voltage: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: obd.battery_voltage")

    # ── Calculated ────────────────────────────────────────────────────────────
    fuel_economy_mpg: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: calculated.fuel_economy_mpg")
    fuel_economy_kml: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: calculated.fuel_economy_kml")

    # ── Motion ────────────────────────────────────────────────────────────────
    accel_mps2: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: accel_mps2")

    # ── Forward-compat + provenance ───────────────────────────────────────────
    extra: Mapped[dict | None] = mapped_column(JSONB, nullable=True, doc="Unrecognized fields, preserved verbatim.")
    source: Mapped[str] = mapped_column(
        Text, nullable=False, server_default=Source.DEVICE.value, doc="device | manual | csv."
    )

    vehicle: Mapped[Vehicle] = relationship(back_populates="telemetry")


class DrivingEvent(Base):
    """
    A hard-acceleration / hard-braking event (REQUIREMENTS.md §4.3).

    From the payload's `events[]` array. Linked to its vehicle by `vin` and
    timestamped at the event time. Metric columns are strategy-dependent
    (`rate_mph_s` for ACCELERATION, `peak_g` for G_FORCE).
    """

    __tablename__ = "driving_events"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True, doc="Generated.")
    vin: Mapped[str] = mapped_column(
        Text, ForeignKey("vehicles.vin", ondelete="CASCADE"), nullable=False, doc="Parent payload vin."
    )
    time: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), nullable=False, doc="wire: events[].timestamp")
    strategy: Mapped[str] = mapped_column(Text, nullable=False, doc="ACCELERATION | G_FORCE")
    type: Mapped[str] = mapped_column(Text, nullable=False, doc="e.g. HARD_BRAKE, HARD_ACCEL")
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True, doc="wire: duration_ms")
    rate_mph_s: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: rate_mph_s (ACCELERATION)")
    peak_g: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: peak_g (G_FORCE)")
    peak_accel_mps2: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: peak_accel_mps2")
    start_speed_mph: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: start_speed_mph")
    end_speed_mph: Mapped[float | None] = mapped_column(Double, nullable=True, doc="wire: end_speed_mph")
    sources: Mapped[list[str] | None] = mapped_column(ARRAY(Text), nullable=True, doc="wire: sources, e.g. [OBD, GPS]")
    source: Mapped[str] = mapped_column(
        Text, nullable=False, server_default=Source.DEVICE.value, doc="device | manual | csv."
    )

    vehicle: Mapped[Vehicle] = relationship(back_populates="events")
