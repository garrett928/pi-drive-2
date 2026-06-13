"""
Vehicles management API (REQUIREMENTS.md §6.1) — /api/v1/vehicles.

  GET    /api/v1/vehicles            list with summary counts, paginated
  GET    /api/v1/vehicles/<vin>      one vehicle + summary; 404 unknown
  PATCH  /api/v1/vehicles/<vin>      edit mutable metadata
  DELETE /api/v1/vehicles/<vin>      cascade delete; requires ?confirm=true

All routes require the API key and delegate to `vehicle_service` (no ORM here).
"""

from __future__ import annotations

import json
import logging

from flask import Blueprint, abort, jsonify, request
from pydantic import ValidationError

from app.api.common import iso_z as _iso
from app.auth import require_api_key
from app.db.models import Vehicle
from app.db.session import get_session
from app.schemas.vehicle import VehicleUpdate
from app.services import vehicle_service

logger = logging.getLogger("VehicleService")

vehicles_bp = Blueprint("vehicles", __name__, url_prefix="/api/v1/vehicles")


def _vehicle_dict(vehicle: Vehicle, sample_count: int, event_count: int) -> dict:
    """Serialize one vehicle with its summary counts."""
    return {
        "vin": vehicle.vin,
        "device_id": vehicle.device_id,
        "make": vehicle.make,
        "model": vehicle.model,
        "year": vehicle.year,
        "nickname": vehicle.nickname,
        "first_seen": _iso(vehicle.first_seen),
        "last_seen": _iso(vehicle.last_seen),
        "created_at": _iso(vehicle.created_at),
        "sample_count": sample_count,
        "event_count": event_count,
    }


@vehicles_bp.get("")
@require_api_key
def list_vehicles():
    """List vehicles (most recently seen first) with summary counts."""
    try:
        limit = min(int(request.args.get("limit", 50)), 500)
        offset = int(request.args.get("offset", 0))
    except ValueError:
        abort(400, description="limit and offset must be integers")
    if limit < 1 or offset < 0:
        abort(400, description="limit must be >= 1 and offset >= 0")

    rows, total = vehicle_service.list_vehicles(limit=limit, offset=offset)
    return jsonify(
        {
            "vehicles": [_vehicle_dict(v, s, e) for v, s, e in rows],
            "total": total,
            "limit": limit,
            "offset": offset,
        }
    ), 200


@vehicles_bp.get("/<vin>")
@require_api_key
def get_vehicle(vin: str):
    """One vehicle with its summary counts. 404 if unknown."""
    vehicle = vehicle_service.get(vin)
    if vehicle is None:
        abort(404)
    samples, events = vehicle_service.summary_counts(vin)
    return jsonify(_vehicle_dict(vehicle, samples, events)), 200


@vehicles_bp.patch("/<vin>")
@require_api_key
def patch_vehicle(vin: str):
    """
    Update mutable metadata (make/model/year/nickname). Attempts to change
    `vin` or any server-maintained field are rejected with 400.
    """
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        abort(400, description="Body must be a JSON object")

    try:
        update = VehicleUpdate.model_validate(data)
    except ValidationError as exc:
        abort(400, description=json.dumps(
            [{"loc": list(e.get("loc", ())), "msg": e.get("msg", "")} for e in exc.errors()],
            default=str,
        ))

    fields = update.model_dump(exclude_unset=True)
    if not fields:
        abort(400, description="No editable fields provided")

    session = get_session()
    vehicle = vehicle_service.update_metadata(vin, fields, session=session)
    if vehicle is None:
        abort(404)
    session.commit()

    samples, events = vehicle_service.summary_counts(vin, session=session)
    return jsonify(_vehicle_dict(vehicle, samples, events)), 200


@vehicles_bp.delete("/<vin>")
@require_api_key
def delete_vehicle(vin: str):
    """
    Delete a vehicle AND all its telemetry + events (DB cascade). Destructive —
    requires `?confirm=true`; 400 explains the guard otherwise.
    """
    if request.args.get("confirm") != "true":
        abort(
            400,
            description=(
                "Deleting a vehicle removes all of its telemetry and events. "
                "Re-send with ?confirm=true to proceed."
            ),
        )
    session = get_session()
    if not vehicle_service.delete(vin, session=session):
        abort(404)
    session.commit()
    return "", 204
