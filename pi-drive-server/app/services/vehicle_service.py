"""
Vehicle service — the only place vehicle rows are read/written (REQUIREMENTS §3).

Functions take an optional `session` (defaulting to the request-scoped session)
so they are usable inside a request and testable without a Flask context.
Services flush but do not commit; the caller owns the transaction boundary (the
ingest orchestrator commits a whole batch at once).
"""

from __future__ import annotations

import datetime as dt
import logging

from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.db.models import DrivingEvent, Telemetry, Vehicle
from app.db.session import get_session

logger = logging.getLogger("VehicleService")


def _session(session: Session | None) -> Session:
    return session if session is not None else get_session()


def upsert_vehicle(
    vin: str,
    device_id: str | None = None,
    *,
    seen_at: dt.datetime,
    session: Session | None = None,
) -> Vehicle:
    """
    Insert a vehicle on first sight of a VIN, or update it on subsequent sightings.

    On insert: `first_seen` and `last_seen` are set to `seen_at`.
    On conflict (existing VIN): `last_seen` advances to the later of the stored
    and incoming time, `first_seen` keeps the earlier, and `device_id` updates to
    the most recent reporting device (kept if the new one is null).

    This is idempotent and safe to call for every ingested snapshot.
    """
    sess = _session(session)

    stmt = pg_insert(Vehicle).values(
        vin=vin,
        device_id=device_id,
        first_seen=seen_at,
        last_seen=seen_at,
    )
    stmt = stmt.on_conflict_do_update(
        index_elements=["vin"],
        set_={
            "device_id": func.coalesce(stmt.excluded.device_id, Vehicle.device_id),
            "last_seen": func.greatest(Vehicle.last_seen, stmt.excluded.last_seen),
            "first_seen": func.least(Vehicle.first_seen, stmt.excluded.first_seen),
        },
    )
    sess.execute(stmt)
    sess.flush()

    vehicle = sess.get(Vehicle, vin)
    # The upsert was a Core statement; refresh so the returned ORM object carries
    # the current DB values (important when the same session called us before).
    sess.refresh(vehicle)
    return vehicle


def get(vin: str, *, session: Session | None = None) -> Vehicle | None:
    """Return the vehicle for `vin`, or None if unknown."""
    return _session(session).get(Vehicle, vin)


def list_vehicles(
    *, limit: int = 50, offset: int = 0, session: Session | None = None
) -> tuple[list[tuple[Vehicle, int, int]], int]:
    """
    Return vehicles ordered by most-recently-seen, each with its summary counts.

    Result is `([(vehicle, sample_count, event_count), ...], total_vehicles)`.
    Counts come from grouped subqueries outer-joined to the vehicles table —
    one round trip, correct zeros for vehicles with no data yet.
    """
    sess = _session(session)

    sample_counts = (
        select(Telemetry.vin, func.count().label("n"))
        .group_by(Telemetry.vin)
        .subquery()
    )
    event_counts = (
        select(DrivingEvent.vin, func.count().label("n"))
        .group_by(DrivingEvent.vin)
        .subquery()
    )
    stmt = (
        select(
            Vehicle,
            func.coalesce(sample_counts.c.n, 0),
            func.coalesce(event_counts.c.n, 0),
        )
        .outerjoin(sample_counts, Vehicle.vin == sample_counts.c.vin)
        .outerjoin(event_counts, Vehicle.vin == event_counts.c.vin)
        .order_by(Vehicle.last_seen.desc().nullslast())
        .limit(limit)
        .offset(offset)
    )
    rows = [(v, int(s), int(e)) for v, s, e in sess.execute(stmt)]
    total = sess.execute(select(func.count()).select_from(Vehicle)).scalar_one()
    return rows, int(total)


def summary_counts(vin: str, *, session: Session | None = None) -> tuple[int, int]:
    """Return `(sample_count, event_count)` for one vehicle."""
    sess = _session(session)
    samples = sess.execute(
        select(func.count()).select_from(Telemetry).where(Telemetry.vin == vin)
    ).scalar_one()
    events = sess.execute(
        select(func.count()).select_from(DrivingEvent).where(DrivingEvent.vin == vin)
    ).scalar_one()
    return int(samples), int(events)


def update_metadata(
    vin: str, fields: dict, *, session: Session | None = None
) -> Vehicle | None:
    """
    Edit mutable display metadata (make/model/year/nickname).

    Returns the updated vehicle, or None if the VIN is unknown. Callers
    validate `fields` via `VehicleUpdate` first, so only the four mutable
    columns can arrive here.
    """
    sess = _session(session)
    vehicle = sess.get(Vehicle, vin)
    if vehicle is None:
        return None
    for name, value in fields.items():
        setattr(vehicle, name, value)
    sess.flush()
    logger.info("Updated metadata for vin=%s: %s", vin, sorted(fields))
    return vehicle


def delete(vin: str, *, session: Session | None = None) -> bool:
    """
    Delete a vehicle and — via the DB's ON DELETE CASCADE — all of its
    telemetry and events. Returns True if the vehicle existed.

    The API layer requires `?confirm=true` before calling (destructive).
    """
    sess = _session(session)
    vehicle = sess.get(Vehicle, vin)
    if vehicle is None:
        return False
    sess.delete(vehicle)
    sess.flush()
    logger.info("Deleted vehicle vin=%s (telemetry + events cascaded)", vin)
    return True
