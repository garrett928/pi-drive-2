"""
Driving-event service — list and delete stored events (REQUIREMENTS.md §6.3).

Insertion lives in `telemetry_service.insert_events` (events arrive only inside
telemetry payloads); this service owns the read/delete side for the management
API and UI.
"""

from __future__ import annotations

import datetime as dt
import logging

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.models import DrivingEvent
from app.db.session import get_session

logger = logging.getLogger("TelemetryService")


def _session(session: Session | None) -> Session:
    return session if session is not None else get_session()


def list_events(
    *,
    vin: str | None = None,
    start: dt.datetime | None = None,
    end: dt.datetime | None = None,
    limit: int = 100,
    offset: int = 0,
    session: Session | None = None,
) -> tuple[list[DrivingEvent], int]:
    """
    Paginated event listing, newest first. All filters optional — no `vin`
    means fleet-wide. Returns `(rows, total_matching)`.
    """
    sess = _session(session)

    conditions = []
    if vin:
        conditions.append(DrivingEvent.vin == vin)
    if start is not None:
        conditions.append(DrivingEvent.time >= start)
    if end is not None:
        conditions.append(DrivingEvent.time <= end)

    stmt = (
        select(DrivingEvent)
        .where(*conditions)
        .order_by(DrivingEvent.time.desc())
        .limit(limit)
        .offset(offset)
    )
    rows = list(sess.execute(stmt).scalars())
    total = sess.execute(
        select(func.count()).select_from(DrivingEvent).where(*conditions)
    ).scalar_one()
    return rows, int(total)


def delete_event(event_id: int, *, session: Session | None = None) -> bool:
    """Delete one event by id. Returns True if it existed."""
    sess = _session(session)
    event = sess.get(DrivingEvent, event_id)
    if event is None:
        return False
    sess.delete(event)
    sess.flush()
    logger.info("Deleted driving event id=%d (vin=%s)", event_id, event.vin)
    return True
