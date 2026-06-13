"""
Driving-events management API (REQUIREMENTS.md §6.3) — /api/v1/events.

  GET    /api/v1/events?vin=&start=&end=   paginated listing, newest first
  DELETE /api/v1/events/<id>               remove one event
"""

from __future__ import annotations

import logging

from flask import Blueprint, abort, jsonify, request

from app.api.common import iso_z as _iso
from app.api.common import parse_iso as _parse_iso
from app.auth import require_api_key
from app.db.models import DrivingEvent
from app.db.session import get_session
from app.services import event_service

logger = logging.getLogger("TelemetryService")

events_bp = Blueprint("events", __name__, url_prefix="/api/v1/events")


def _event_dict(event: DrivingEvent) -> dict:
    return {
        "id": event.id,
        "vin": event.vin,
        "time": _iso(event.time),
        "strategy": event.strategy,
        "type": event.type,
        "duration_ms": event.duration_ms,
        "rate_mph_s": event.rate_mph_s,
        "peak_g": event.peak_g,
        "peak_accel_mps2": event.peak_accel_mps2,
        "start_speed_mph": event.start_speed_mph,
        "end_speed_mph": event.end_speed_mph,
        "sources": event.sources,
        "source": event.source,
    }


@events_bp.get("")
@require_api_key
def list_events():
    """Paginated event listing; all filters optional (no vin = fleet-wide)."""
    vin = (request.args.get("vin") or "").strip() or None
    start = request.args.get("start")
    end = request.args.get("end")
    start_dt = _parse_iso(start, param="start") if start else None
    end_dt = _parse_iso(end, param="end") if end else None

    try:
        limit = min(int(request.args.get("limit", 100)), 1000)
        offset = int(request.args.get("offset", 0))
    except ValueError:
        abort(400, description="limit and offset must be integers")
    if limit < 1 or offset < 0:
        abort(400, description="limit must be >= 1 and offset >= 0")

    rows, total = event_service.list_events(
        vin=vin, start=start_dt, end=end_dt, limit=limit, offset=offset
    )
    return jsonify(
        {
            "events": [_event_dict(e) for e in rows],
            "total": total,
            "limit": limit,
            "offset": offset,
        }
    ), 200


@events_bp.delete("/<int:event_id>")
@require_api_key
def delete_event(event_id: int):
    """Delete one event by id. 204 on success, 404 if unknown."""
    session = get_session()
    if not event_service.delete_event(event_id, session=session):
        abort(404)
    session.commit()
    return "", 204
