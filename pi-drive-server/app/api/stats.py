"""
Stats API (REQUIREMENTS.md §6.4) — /api/v1/stats.

  GET /api/v1/stats          fleet-wide rollup (the dashboard's JSON)
  GET /api/v1/stats/<vin>    per-vehicle stats over an optional range,
                             with the daily series from the continuous aggregate
"""

from __future__ import annotations

import logging

from flask import Blueprint, abort, jsonify, request

from app.api.common import parse_iso as _parse_iso
from app.auth import require_api_key
from app.services import stats_service

logger = logging.getLogger("PiDriveServer")

stats_bp = Blueprint("stats", __name__, url_prefix="/api/v1/stats")


@stats_bp.get("")
@require_api_key
def fleet():
    """Fleet-wide totals, recent-activity counts, span, per-vehicle summaries."""
    return jsonify(stats_service.fleet_stats()), 200


@stats_bp.get("/<vin>")
@require_api_key
def vehicle(vin: str):
    """Per-vehicle stats over an optional `start`/`end` range. 404 unknown VIN."""
    start = request.args.get("start")
    end = request.args.get("end")
    start_dt = _parse_iso(start, param="start") if start else None
    end_dt = _parse_iso(end, param="end") if end else None

    stats = stats_service.vehicle_stats(vin, start_dt, end_dt)
    if stats is None:
        abort(404)
    return jsonify(stats), 200
