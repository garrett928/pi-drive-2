"""
Admin/data-movement API (REQUIREMENTS.md §7, §8).

  POST /api/v1/telemetry/import   multipart upload of a telemetry CSV (§7)
  GET  /api/v1/telemetry/export   streamed CSV download for a VIN (+range) (§7)
  GET  /api/v1/admin/backup       streamed full-database pg_dump (§8)
  POST /api/v1/admin/restore      restore from a dump (destructive, confirm) (§8)

CSV import responses are always 200 with the partial-success report
`{imported, skipped, errors: [{row, reason}]}` — a file where some rows fail
is a *successful import of the good rows*, and the report says exactly what
was skipped and why. File-level problems (bad header, wrong type, too large)
are 400/413/415.

Backup/restore is full-database (distinct from telemetry CSV) and is an
operator tool: 503 when the pg client tools are unavailable, 400 when a
restore is attempted without `?confirm=true`.
"""

from __future__ import annotations

import logging

from flask import Blueprint, Response, abort, current_app, jsonify, request, stream_with_context

from app.api.common import parse_iso as _parse_iso
from app.auth import require_api_key
from app.db.session import get_session
from app.services import backup_service, csv_service, vehicle_service
from app.services.backup_service import BackupError, PgToolsUnavailable
from app.services.csv_service import CsvFormatError

logger = logging.getLogger("CsvService")

admin_bp = Blueprint("admin", __name__, url_prefix="/api/v1")

#: Content types browsers/tools commonly send for a CSV upload. Excel on
#: Windows infamously labels CSV as application/vnd.ms-excel.
_CSV_CONTENT_TYPES = ("text/csv", "application/csv", "application/vnd.ms-excel")


@admin_bp.post("/telemetry/import")
@require_api_key
def import_csv():
    """
    Import a telemetry CSV (multipart field `file=`).

    Returns 200 with `{imported, skipped, errors}` (partial success reported,
    never silently dropped). 400 missing file / unusable header · 413 file
    larger than MAX_BODY_BYTES · 415 not a CSV.
    """
    upload = request.files.get("file")
    if upload is None or not upload.filename:
        abort(400, description="Multipart field 'file' with a CSV file is required")

    is_csv_name = upload.filename.lower().endswith(".csv")
    is_csv_type = (upload.content_type or "").split(";")[0].strip() in _CSV_CONTENT_TYPES
    if not (is_csv_name or is_csv_type):
        abort(415, description="Upload must be a CSV file (.csv / text/csv)")

    max_bytes = current_app.config["MAX_BODY_BYTES"]
    if request.content_length is not None and request.content_length > max_bytes:
        abort(413)

    session = get_session()
    try:
        report = csv_service.parse_and_import(upload.stream, session=session)
    except CsvFormatError as exc:
        session.rollback()
        abort(400, description=str(exc))
    # One commit for the whole file: all good rows land atomically.
    session.commit()

    logger.info(
        "CSV import of '%s': imported=%d skipped=%d",
        upload.filename,
        report.imported,
        report.skipped,
    )
    return jsonify(report.to_dict()), 200


@admin_bp.get("/telemetry/export")
@require_api_key
def export_csv():
    """
    Download a VIN's telemetry as CSV, streamed (large ranges never buffer
    fully server-side). `start`/`end` optionally bound the range. 404 for an
    unknown VIN.
    """
    vin = (request.args.get("vin") or "").strip()
    if not vin:
        abort(400, description="vin query parameter is required")
    if vehicle_service.get(vin) is None:
        abort(404)

    start = request.args.get("start")
    end = request.args.get("end")
    start_dt = _parse_iso(start, param="start") if start else None
    end_dt = _parse_iso(end, param="end") if end else None

    # Filename like 1G1JC…_20260401T100000Z_now.csv — ISO basic format because
    # colons are not filename-safe.
    def _stamp(value, fallback: str) -> str:
        return value.strftime("%Y%m%dT%H%M%SZ") if value is not None else fallback

    filename = f"{vin}_{_stamp(start_dt, 'begin')}_{_stamp(end_dt, 'now')}.csv"

    # stream_with_context keeps the request context (and its DB session) alive
    # until the generator is exhausted — the session is torn down afterwards.
    generator = stream_with_context(
        csv_service.export_rows(vin, start=start_dt, end=end_dt)
    )
    return Response(
        generator,
        mimetype="text/csv",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


# ── Database backup / restore (§8) ─────────────────────────────────────────────


@admin_bp.get("/admin/backup")
@require_api_key
def backup_db():
    """
    Stream a full-database `pg_dump` (custom compressed format) as a download.
    503 if the pg client tools are not installed.
    """
    try:
        # Resolve the tool eagerly so a missing binary is a clean 503 rather
        # than an error mid-stream after headers are already sent.
        generator = stream_with_context(backup_service.dump())
        first = next(generator, b"")
    except PgToolsUnavailable as exc:
        abort(503, description=str(exc))

    def _body():
        yield first
        yield from generator

    filename = backup_service.backup_filename()
    return Response(
        _body(),
        mimetype="application/octet-stream",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@admin_bp.post("/admin/restore")
@require_api_key
def restore_db():
    """
    Restore the database from an uploaded dump (multipart `file=`). DESTRUCTIVE
    — replaces existing data; requires `?confirm=true`. 503 if pg tools are
    missing, 400 without confirm / no file, 500 on restore failure.
    """
    if request.args.get("confirm") != "true":
        abort(400, description="Restore is destructive — pass ?confirm=true to proceed")

    upload = request.files.get("file")
    if upload is None or not upload.filename:
        abort(400, description="Multipart field 'file' with a dump file is required")

    try:
        backup_service.restore(upload.stream)
    except PgToolsUnavailable as exc:
        abort(503, description=str(exc))
    except BackupError as exc:
        logger.error("Restore failed: %s", exc)
        abort(500, description="Restore failed — see server logs")

    return jsonify({"restored": True}), 200
