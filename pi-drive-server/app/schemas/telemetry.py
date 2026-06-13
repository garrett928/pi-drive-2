"""
Pydantic v2 schemas for the telemetry wire contract (REQUIREMENTS.md §5.5).

These decouple the wire format from storage: routes validate JSON into
`TelemetryIn`, then `to_orm_columns()` flattens it into a `telemetry` row dict.
All keys are snake_case, exactly as the Android app sends them.

Type fidelity (Java server Known Bug #2 fix): `rpm` is `int | None`; every other
numeric signal is `float | None`. Pydantic coerces per-field — floats are never
truncated to integers.

Forward-compatibility: unknown keys (top-level or inside location/obd/calculated)
are NOT rejected. `extra="allow"` captures them in `model_extra`, and
`to_orm_columns()` routes them into the `extra` JSONB column verbatim.
"""

from __future__ import annotations

import datetime as dt
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


class LocationIn(BaseModel):
    """`location` object: GPS fix. All optional; unknown keys preserved."""

    model_config = ConfigDict(extra="allow")

    lat: float | None = None
    lng: float | None = None
    speed_gps: float | None = None


class ObdIn(BaseModel):
    """`obd` object: signals read from the OBD-II adapter. All optional."""

    model_config = ConfigDict(extra="allow")

    speed_kmh: float | None = None
    rpm: int | None = None  # int — never truncated from / widened to float
    coolant_temp_c: float | None = None
    intake_air_temp_c: float | None = None
    throttle_pct: float | None = None
    fuel_level_pct: float | None = None
    oil_temp_c: float | None = None
    maf_gps: float | None = None
    fuel_rate_lph: float | None = None
    battery_voltage: float | None = None


class CalculatedIn(BaseModel):
    """`calculated` object: values the app derives (fuel economy)."""

    model_config = ConfigDict(extra="allow")

    fuel_economy_mpg: float | None = None
    fuel_economy_kml: float | None = None


class EventIn(BaseModel):
    """One entry of the `events[]` array: a detected hard accel/brake event."""

    strategy: str  # ACCELERATION | G_FORCE
    type: str  # e.g. HARD_BRAKE, HARD_ACCEL
    timestamp: dt.datetime
    duration_ms: int | None = None
    rate_mph_s: float | None = None  # ACCELERATION strategy
    peak_g: float | None = None  # G_FORCE strategy
    peak_accel_mps2: float | None = None
    start_speed_mph: float | None = None
    end_speed_mph: float | None = None
    sources: list[str] | None = None  # e.g. ["OBD", "GPS"]

    @field_validator("timestamp")
    @classmethod
    def _aware(cls, v: dt.datetime) -> dt.datetime:
        """Treat a naive timestamp as UTC so storage is always tz-aware."""
        return v if v.tzinfo is not None else v.replace(tzinfo=dt.UTC)


class TelemetryIn(BaseModel):
    """
    One full TelemetryPayload as the Android app sends it.

    `vin` and `timestamp` are mandatory; everything else may be absent when a
    signal is disabled or unsupported. Unknown top-level keys are preserved.
    """

    model_config = ConfigDict(extra="allow")

    timestamp: dt.datetime
    vin: str
    device_id: str | None = None
    location: LocationIn | None = None
    obd: ObdIn | None = None
    calculated: CalculatedIn | None = None
    accel_mps2: float | None = None
    events: list[EventIn] = Field(default_factory=list)

    @field_validator("vin")
    @classmethod
    def _vin_non_blank(cls, v: str) -> str:
        """The contract requires a VIN on every snapshot (else 400)."""
        v = v.strip()
        if not v:
            raise ValueError("vin must be non-blank")
        return v

    @field_validator("timestamp")
    @classmethod
    def _aware(cls, v: dt.datetime) -> dt.datetime:
        """Treat a naive timestamp as UTC so storage is always tz-aware."""
        return v if v.tzinfo is not None else v.replace(tzinfo=dt.UTC)


class TelemetryEdit(BaseModel):
    """
    PATCH body for editing one stored snapshot (management API, §6.2).

    Only signal values may change — `vin` and `time` are the identity and are
    rejected (`extra="forbid"`), as is anything not a known column. Provenance
    (`source`) is editable so an operator can re-tag a corrected row.
    """

    model_config = ConfigDict(extra="forbid")

    device_id: str | None = None
    lat: float | None = None
    lng: float | None = None
    speed_gps: float | None = None
    speed_kmh: float | None = None
    rpm: int | None = None
    coolant_temp_c: float | None = None
    intake_air_temp_c: float | None = None
    throttle_pct: float | None = None
    fuel_level_pct: float | None = None
    oil_temp_c: float | None = None
    maf_gps: float | None = None
    fuel_rate_lph: float | None = None
    battery_voltage: float | None = None
    fuel_economy_mpg: float | None = None
    fuel_economy_kml: float | None = None
    accel_mps2: float | None = None
    source: str | None = None


class TelemetryCsvRow(BaseModel):
    """
    One data row of a CSV import (REQUIREMENTS.md §7) — the flat-column
    equivalent of `TelemetryIn`, with the same per-field typing rules
    (`rpm` int, other signals float, `time` tz-aware).

    `extra="forbid"`: the importer validates the header against the known
    column set up front, so an unexpected key here is a programming error.
    `source` is accepted (exports include it) but ignored — imported rows are
    always stored with `source=csv`, recording how *this* copy arrived.
    """

    model_config = ConfigDict(extra="forbid")

    vin: str
    time: dt.datetime
    device_id: str | None = None
    lat: float | None = None
    lng: float | None = None
    speed_gps: float | None = None
    speed_kmh: float | None = None
    rpm: int | None = None
    coolant_temp_c: float | None = None
    intake_air_temp_c: float | None = None
    throttle_pct: float | None = None
    fuel_level_pct: float | None = None
    oil_temp_c: float | None = None
    maf_gps: float | None = None
    fuel_rate_lph: float | None = None
    battery_voltage: float | None = None
    fuel_economy_mpg: float | None = None
    fuel_economy_kml: float | None = None
    accel_mps2: float | None = None
    extra: dict | None = None
    source: str | None = None

    @field_validator("vin")
    @classmethod
    def _vin_non_blank(cls, v: str) -> str:
        """A blank VIN cannot identify a vehicle — reject the row."""
        v = v.strip()
        if not v:
            raise ValueError("vin must be non-blank")
        return v

    @field_validator("time")
    @classmethod
    def _aware(cls, v: dt.datetime) -> dt.datetime:
        """Treat a naive timestamp as UTC so storage is always tz-aware."""
        return v if v.tzinfo is not None else v.replace(tzinfo=dt.UTC)


class BatchShapeError(ValueError):
    """The request body is not one of the three accepted batch shapes."""


def parse_batch(data: Any) -> list[TelemetryIn]:
    """
    Normalize the three accepted POST /telemetry body shapes (§5.2) into a list:

      1. a single TelemetryPayload object,
      2. a bare JSON array of payloads,
      3. ``{"snapshots": [ ... ]}``.

    Raises `BatchShapeError` for anything else and lets Pydantic's
    `ValidationError` propagate for per-item schema violations.
    """
    if isinstance(data, list):
        items = data
    elif isinstance(data, dict) and isinstance(data.get("snapshots"), list):
        items = data["snapshots"]
    elif isinstance(data, dict):
        items = [data]
    else:
        raise BatchShapeError(
            "Body must be a telemetry object, an array of them, or {\"snapshots\": [...]}"
        )
    if not items:
        raise BatchShapeError("Batch contains no snapshots")
    return [TelemetryIn.model_validate(item) for item in items]


def to_orm_columns(
    snapshot: TelemetryIn,
    *,
    source: str = "device",
    device_id_fallback: str | None = None,
) -> dict[str, Any]:
    """
    Flatten a validated `TelemetryIn` into a `telemetry` table row dict.

    Nested wire objects map to flat columns; any unknown keys captured by
    `extra="allow"` are preserved in the `extra` JSONB column, namespaced under
    the object they arrived in (top-level extras sit at the root).
    """
    loc = snapshot.location
    obd = snapshot.obd
    calc = snapshot.calculated

    # Collect unknown keys for the `extra` column, preserving their position
    # in the original payload ({"obd": {...}} for unknown OBD keys, etc.).
    extra: dict[str, Any] = {}
    if snapshot.model_extra:
        extra.update(snapshot.model_extra)
    for name, obj in (("location", loc), ("obd", obd), ("calculated", calc)):
        if obj is not None and obj.model_extra:
            extra[name] = dict(obj.model_extra)

    return {
        "vin": snapshot.vin,
        "time": snapshot.timestamp,
        "device_id": snapshot.device_id or device_id_fallback,
        "lat": loc.lat if loc else None,
        "lng": loc.lng if loc else None,
        "speed_gps": loc.speed_gps if loc else None,
        "speed_kmh": obd.speed_kmh if obd else None,
        "rpm": obd.rpm if obd else None,
        "coolant_temp_c": obd.coolant_temp_c if obd else None,
        "intake_air_temp_c": obd.intake_air_temp_c if obd else None,
        "throttle_pct": obd.throttle_pct if obd else None,
        "fuel_level_pct": obd.fuel_level_pct if obd else None,
        "oil_temp_c": obd.oil_temp_c if obd else None,
        "maf_gps": obd.maf_gps if obd else None,
        "fuel_rate_lph": obd.fuel_rate_lph if obd else None,
        "battery_voltage": obd.battery_voltage if obd else None,
        "fuel_economy_mpg": calc.fuel_economy_mpg if calc else None,
        "fuel_economy_kml": calc.fuel_economy_kml if calc else None,
        "accel_mps2": snapshot.accel_mps2,
        "extra": extra or None,
        "source": source,
    }
