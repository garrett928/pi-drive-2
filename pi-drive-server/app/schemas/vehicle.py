"""
Pydantic schemas for the vehicles management API (REQUIREMENTS.md §6.1).

`VehicleUpdate` validates PATCH bodies: only the four mutable metadata fields
are accepted; `extra="forbid"` rejects attempts to change `vin` (identity) or
any server-maintained column (`first_seen`, `last_seen`, `device_id`).
"""

from __future__ import annotations

import datetime as dt

from pydantic import BaseModel, ConfigDict


class VehicleUpdate(BaseModel):
    """PATCH body — user-editable display metadata only."""

    model_config = ConfigDict(extra="forbid")

    make: str | None = None
    model: str | None = None
    year: int | None = None
    nickname: str | None = None


class VehicleOut(BaseModel):
    """Response shape for a vehicle, built from the ORM row."""

    model_config = ConfigDict(from_attributes=True)

    vin: str
    device_id: str | None = None
    make: str | None = None
    model: str | None = None
    year: int | None = None
    nickname: str | None = None
    first_seen: dt.datetime | None = None
    last_seen: dt.datetime | None = None
    created_at: dt.datetime | None = None
