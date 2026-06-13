"""
Telemetry endpoints.

Two blueprints, both backed by `telemetry_service` (routes never touch the ORM):

`ingest_bp` — the **Android wire contract** (REQUIREMENTS.md §5; fixed by the
shipped client — do not change paths, headers, or payload shape):
  POST /telemetry                 ingest one snapshot or a batch (zstd/gzip ok)
  GET  /telemetry/latest?vin=     most recent stored timestamp for a VIN

`telemetry_api_bp` — the management/query surface (§6.2) under /api/v1/telemetry:
  GET    /api/v1/telemetry?vin=&start=&end=&fields=&order=&limit=&offset=
  GET    /api/v1/telemetry/<vin>/<timestamp>
  POST   /api/v1/telemetry                       manual single-row entry
  PATCH  /api/v1/telemetry/<vin>/<timestamp>
  DELETE /api/v1/telemetry/<vin>/<timestamp>
  DELETE /api/v1/telemetry?vin=&start=&end=&confirm=true

Error mapping (§5.2): 400 blank/missing VIN or device-id mismatch · 401 bad key
· 413 body too large · 422 schema/JSON validation (Pydantic detail included).
"""

from __future__ import annotations

import json
import logging

from flask import Blueprint, abort, jsonify, request
from pydantic import ValidationError

from app.auth import require_api_key
from app.compression import read_request_body
from app.db.models import Telemetry
from app.db.session import get_session
from app.logging_config import bind
from app.schemas.telemetry import (
    BatchShapeError,
    TelemetryEdit,
    TelemetryIn,
    parse_batch,
)
from app.services import telemetry_service
from app.services.telemetry_service import SIGNAL_COLUMNS, DeviceIdMismatchError

logger = logging.getLogger("Ingest")

ingest_bp = Blueprint("ingest", __name__)
telemetry_api_bp = Blueprint("telemetry_api", __name__, url_prefix="/api/v1/telemetry")


# ── Serialization helpers ──────────────────────────────────────────────────────

from app.api.common import iso_z as _iso  # noqa: E402
from app.api.common import parse_iso as _parse_iso  # noqa: E402


def _row_to_dict(row: Telemetry, fields: list[str] | None = None) -> dict:
    """
    Serialize one telemetry row. `vin` and `time` are always included; `fields`
    (when given) selects a subset of the signal columns.

    Subsetting happens at serialization rather than in the SELECT — simpler code
    for an identical wire result, and row width is trivial at this scale
    (maintainability over performance, per the project philosophy).
    """
    names = fields if fields is not None else list(SIGNAL_COLUMNS)
    out: dict = {"vin": row.vin, "time": _iso(row.time)}
    for name in names:
        out[name] = getattr(row, name)
    return out


def _validation_response(exc: ValidationError) -> None:
    """
    Map a Pydantic error per the contract: VIN problems are the client's data
    being unusable (400); anything else is a schema violation (422, with detail).
    """
    errors = exc.errors()
    if any("vin" in (err.get("loc") or ()) for err in errors):
        abort(400, description="vin is required and must be non-blank")
    detail = json.dumps(
        [
            {"loc": list(err.get("loc", ())), "msg": err.get("msg", "")}
            for err in errors
        ],
        default=str,
    )
    abort(422, description=detail)


# ── Ingest contract (§5) ───────────────────────────────────────────────────────


@ingest_bp.post("/telemetry")
@require_api_key
def ingest():
    """
    Ingest one snapshot or a batch (single object, bare array, or
    `{"snapshots": [...]}`), optionally zstd/gzip-compressed.

    Auto-registers vehicles, upserts snapshots by `(vin, time)`, inserts events —
    all in one transaction. Returns a real write confirmation.
    """
    bind(endpoint="/telemetry")
    body = read_request_body(request)  # 413 / 400 handled inside

    try:
        data = json.loads(body)
    except (ValueError, UnicodeDecodeError):
        abort(422, description="Body is not valid JSON")

    try:
        items = parse_batch(data)
    except BatchShapeError as exc:
        abort(422, description=str(exc))
    except ValidationError as exc:
        _validation_response(exc)

    session = get_session()
    try:
        accepted, vins = telemetry_service.ingest_batch(
            items,
            device_id_header=request.headers.get("X-Device-Id"),
            session=session,
        )
        session.commit()
    except DeviceIdMismatchError as exc:
        session.rollback()
        abort(400, description=str(exc))

    bind(vin=",".join(sorted(vins)), status=200)
    return jsonify({"ok": True, "accepted": accepted, "vehicles": sorted(vins)}), 200


@ingest_bp.get("/telemetry/latest")
@require_api_key
def latest():
    """Most recent stored timestamp for a VIN — the app's sync offset."""
    vin = (request.args.get("vin") or "").strip()
    if not vin:
        abort(400, description="vin query parameter is required")

    ts = telemetry_service.latest_timestamp(vin)
    if ts is None:
        abort(404)
    return jsonify({"vin": vin, "latest_timestamp": _iso(ts)}), 200


# ── Management query/read (§6.2) ───────────────────────────────────────────────

_MAX_LIMIT = 1000
_DEFAULT_LIMIT = 100


@telemetry_api_bp.get("")
@require_api_key
def query_telemetry():
    """Filtered, paginated telemetry query. `vin` is required."""
    vin = (request.args.get("vin") or "").strip()
    if not vin:
        abort(400, description="vin query parameter is required")

    start = request.args.get("start")
    end = request.args.get("end")
    start_dt = _parse_iso(start, param="start") if start else None
    end_dt = _parse_iso(end, param="end") if end else None

    order = request.args.get("order", "desc").lower()
    if order not in ("asc", "desc"):
        abort(400, description="order must be 'asc' or 'desc'")

    try:
        limit = min(int(request.args.get("limit", _DEFAULT_LIMIT)), _MAX_LIMIT)
        offset = int(request.args.get("offset", 0))
    except ValueError:
        abort(400, description="limit and offset must be integers")
    if limit < 1 or offset < 0:
        abort(400, description="limit must be >= 1 and offset >= 0")

    fields: list[str] | None = None
    if request.args.get("fields"):
        fields = [f.strip() for f in request.args["fields"].split(",") if f.strip()]
        unknown = [f for f in fields if f not in SIGNAL_COLUMNS]
        if unknown:
            abort(400, description=f"unknown fields: {', '.join(unknown)}")

    rows, has_more = telemetry_service.query(
        vin, start=start_dt, end=end_dt, order=order, limit=limit, offset=offset
    )
    return jsonify(
        {
            "rows": [_row_to_dict(r, fields) for r in rows],
            "limit": limit,
            "offset": offset,
            "has_more": has_more,
        }
    ), 200


@telemetry_api_bp.get("/<vin>/<timestamp>")
@require_api_key
def get_snapshot(vin: str, timestamp: str):
    """One snapshot by exact `(vin, time)`. 404 if absent."""
    time = _parse_iso(timestamp, param="timestamp")
    row = telemetry_service.get_one(vin, time)
    if row is None:
        abort(404)
    return jsonify(_row_to_dict(row)), 200


# ── Management mutation (§6.2) ─────────────────────────────────────────────────


@telemetry_api_bp.post("")
@require_api_key
def create_snapshot():
    """
    Manual single-row entry (UI form / API). Same validation as ingest, stored
    with `source=manual`; auto-registers the vehicle like the ingest path.
    """
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        abort(422, description="Body must be a single telemetry JSON object")

    try:
        item = TelemetryIn.model_validate(data)
    except ValidationError as exc:
        _validation_response(exc)

    session = get_session()
    telemetry_service.ingest_batch([item], source="manual", session=session)
    session.commit()

    row = telemetry_service.get_one(item.vin, item.timestamp, session=session)
    return jsonify(_row_to_dict(row)), 201


@telemetry_api_bp.patch("/<vin>/<timestamp>")
@require_api_key
def edit_snapshot(vin: str, timestamp: str):
    """Partial update of one snapshot's signal values. 404 if absent."""
    time = _parse_iso(timestamp, param="timestamp")
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        abort(422, description="Body must be a JSON object of fields to update")

    try:
        edit = TelemetryEdit.model_validate(data)
    except ValidationError as exc:
        abort(400, description=json.dumps(
            [{"loc": list(e.get("loc", ())), "msg": e.get("msg", "")} for e in exc.errors()],
            default=str,
        ))

    fields = edit.model_dump(exclude_unset=True)
    if not fields:
        abort(400, description="No editable fields provided")

    session = get_session()
    row = telemetry_service.update_one(vin, time, fields, session=session)
    if row is None:
        abort(404)
    session.commit()
    return jsonify(_row_to_dict(row)), 200


@telemetry_api_bp.delete("/<vin>/<timestamp>")
@require_api_key
def delete_snapshot(vin: str, timestamp: str):
    """Delete one snapshot. 204 on success, 404 if absent."""
    time = _parse_iso(timestamp, param="timestamp")
    session = get_session()
    if not telemetry_service.delete_one(vin, time, session=session):
        abort(404)
    session.commit()
    return "", 204


@telemetry_api_bp.delete("")
@require_api_key
def delete_range():
    """
    Bulk delete a VIN's snapshots in a time range. Destructive — requires
    `?confirm=true`. Returns the deleted-row count.
    """
    vin = (request.args.get("vin") or "").strip()
    if not vin:
        abort(400, description="vin query parameter is required")
    if request.args.get("confirm") != "true":
        abort(400, description="Bulk delete requires ?confirm=true")

    start = request.args.get("start")
    end = request.args.get("end")
    start_dt = _parse_iso(start, param="start") if start else None
    end_dt = _parse_iso(end, param="end") if end else None

    session = get_session()
    deleted = telemetry_service.delete_range(
        vin, start=start_dt, end=end_dt, session=session
    )
    session.commit()
    logger.info("Bulk-deleted %d telemetry rows for vin=%s", deleted, vin)
    return jsonify({"deleted": deleted}), 200
