"""
Web UI blueprint (REQUIREMENTS.md §9) — server-rendered Flask/Jinja pages.

No SPA framework, no build step: every page is plain HTML rendered from the
same service layer the REST API uses (routes never touch the ORM). Pages are
fully functional without JavaScript.

Pages:
  GET  /                                    dashboard (fleet stats + cards)
  GET/POST /login · GET /logout             API-key session auth (web.auth)
  GET/POST /vehicles/<vin>                  detail + metadata edit
  GET  /vehicles/<vin>/telemetry            paginated/filterable browser
  GET/POST /vehicles/<vin>/telemetry/new    manual snapshot entry
  GET  /telemetry/new                       manual entry (VIN typed in)
  GET/POST /telemetry/<vin>/<ts>/edit       edit one snapshot
  POST /telemetry/<vin>/<ts>/delete         delete one snapshot
  GET/POST /import                          CSV upload + result report
  GET  /admin                               export form + Phase 6 placeholders

All POSTs are CSRF-protected by the blueprint-wide before_request hook.
"""

from __future__ import annotations

import datetime as dt
import json
import logging

from flask import (
    Blueprint,
    abort,
    current_app,
    flash,
    redirect,
    render_template,
    request,
    url_for,
)
from pydantic import ValidationError

from app.api.common import iso_z, parse_iso
from app.db.session import get_session
from app.schemas.telemetry import TelemetryCsvRow, TelemetryEdit
from app.schemas.vehicle import VehicleUpdate
from app.services import (
    backup_service,
    csv_service,
    event_service,
    stats_service,
    telemetry_service,
    vehicle_service,
)
from app.services.csv_service import CsvFormatError
from app.web import auth

logger = logging.getLogger("PiDriveServer")

web_bp = Blueprint("web", __name__)

#: Manual-entry / edit form fields, in display order: (column, label, input kind).
#: `kind` picks the HTML input: "text" free text, "int"/"float" numeric.
FORM_FIELDS: tuple[tuple[str, str, str], ...] = (
    ("device_id", "Device ID", "text"),
    ("lat", "Latitude", "float"),
    ("lng", "Longitude", "float"),
    ("speed_gps", "GPS speed (mph)", "float"),
    ("speed_kmh", "Speed (km/h)", "float"),
    ("rpm", "RPM", "int"),
    ("coolant_temp_c", "Coolant temp (°C)", "float"),
    ("intake_air_temp_c", "Intake air temp (°C)", "float"),
    ("throttle_pct", "Throttle (%)", "float"),
    ("fuel_level_pct", "Fuel level (%)", "float"),
    ("oil_temp_c", "Oil temp (°C)", "float"),
    ("maf_gps", "MAF (g/s)", "float"),
    ("fuel_rate_lph", "Fuel rate (L/h)", "float"),
    ("battery_voltage", "Battery (V)", "float"),
    ("fuel_economy_mpg", "Fuel economy (MPG)", "float"),
    ("fuel_economy_kml", "Fuel economy (km/L)", "float"),
    ("accel_mps2", "Acceleration (m/s²)", "float"),
)


# ── Blueprint-wide hooks ───────────────────────────────────────────────────────


@web_bp.before_request
def _csrf_protect():
    """Every UI form POST must carry the session's CSRF token (§9.2)."""
    if request.method == "POST":
        auth.validate_csrf()


@web_bp.app_context_processor
def _template_globals():
    """Expose helpers every template needs (CSRF token, auth state, iso)."""
    return {
        "csrf_token": auth.csrf_token,
        "logged_in": auth.is_logged_in,
        # When auth is off (the default), the nav hides Login/Logout entirely.
        "auth_enabled": current_app.config["UI_REQUIRE_AUTH"],
        "iso_z": iso_z,
    }


# ── Auth pages (Step 5.1) ──────────────────────────────────────────────────────


def _safe_next(target: str | None) -> str:
    """Only follow same-site relative redirect targets (open-redirect guard)."""
    if target and target.startswith("/") and not target.startswith("//"):
        return target
    return url_for("web.dashboard")


@web_bp.route("/login", methods=["GET", "POST"])
def login():
    """API-key login form. On success, mark the signed session authenticated."""
    if request.method == "POST":
        if auth.try_login(request.form.get("api_key", "")):
            flash("Logged in.", "success")
            return redirect(_safe_next(request.args.get("next")))
        flash("Invalid API key.", "error")
        return render_template("login.html"), 401
    if auth.is_logged_in():
        return redirect(url_for("web.dashboard"))
    return render_template("login.html")


@web_bp.get("/logout")
def logout():
    """Clear the session and return to the login page."""
    auth.logout()
    flash("Logged out.", "success")
    return redirect(url_for("web.login"))


# ── Dashboard (Step 5.2) ───────────────────────────────────────────────────────


def _sparkline_points(
    values: list[float | None], width: int = 120, height: int = 28
) -> str | None:
    """
    Map a small numeric series onto SVG polyline points (server-rendered
    sparkline — the page needs no JS). Returns None when there is nothing
    meaningful to draw (fewer than two real values).
    """
    present = [v for v in values if v is not None]
    if len(present) < 2:
        return None
    lo, hi = min(present), max(present)
    span = (hi - lo) or 1.0  # flat series draws a midline
    points: list[str] = []
    step = width / (len(values) - 1)
    for i, value in enumerate(values):
        if value is None:
            continue
        x = i * step
        y = height - ((value - lo) / span) * (height - 4) - 2
        points.append(f"{x:.1f},{y:.1f}")
    return " ".join(points)


@web_bp.get("/")
@auth.ui_login_required
def dashboard():
    """Fleet dashboard: totals, span, and a summary card per vehicle."""
    stats = stats_service.fleet_stats()

    # One CAGG query per listed vehicle for the card sparkline (last 30 days
    # of avg speed). N+1 by design: trivially correct, and the dashboard lists
    # at most 100 vehicles (maintainability over performance).
    month_ago = dt.datetime.now(dt.UTC) - dt.timedelta(days=30)
    for vehicle in stats["vehicles"]:
        detail = stats_service.vehicle_stats(vehicle["vin"], start=month_ago)
        series = [d["avg_speed_kmh"] for d in (detail or {}).get("daily", [])]
        vehicle["sparkline"] = _sparkline_points(series)

    return render_template("dashboard.html", stats=stats)


# ── Vehicle detail (Step 5.2) ──────────────────────────────────────────────────

_RANGE_CHOICES = {"7": 7, "30": 30, "365": 365, "all": None}


@web_bp.route("/vehicles/<vin>", methods=["GET", "POST"])
@auth.ui_login_required
def vehicle_detail(vin: str):
    """Vehicle metadata (editable), stat rollups, recent telemetry and events."""
    session = get_session()
    vehicle = vehicle_service.get(vin, session=session)
    if vehicle is None:
        abort(404)

    if request.method == "POST":
        # Metadata edit: all four mutable fields are present in the form;
        # blank means "clear". VehicleUpdate enforces types (year is an int).
        fields = {
            name: (request.form.get(name, "").strip() or None)
            for name in ("make", "model", "year", "nickname")
        }
        try:
            update = VehicleUpdate.model_validate(fields)
        except ValidationError:
            flash("Invalid metadata — year must be a number.", "error")
            return redirect(url_for("web.vehicle_detail", vin=vin))
        vehicle_service.update_metadata(vin, update.model_dump(), session=session)
        session.commit()
        flash("Vehicle metadata saved.", "success")
        return redirect(url_for("web.vehicle_detail", vin=vin))

    range_key = request.args.get("days", "30")
    days = _RANGE_CHOICES.get(range_key, 30)
    start = (
        dt.datetime.now(dt.UTC) - dt.timedelta(days=days) if days else None
    )
    stats = stats_service.vehicle_stats(vin, start=start, session=session)
    recent, _ = telemetry_service.query(vin, limit=10, session=session)
    events, event_total = event_service.list_events(vin=vin, limit=10, session=session)

    return render_template(
        "vehicle_detail.html",
        vehicle=vehicle,
        stats=stats,
        recent=recent,
        events=events,
        event_total=event_total,
        range_key=range_key,
        range_choices=_RANGE_CHOICES,
    )


# ── Telemetry browser (Step 5.3) ───────────────────────────────────────────────


def _parse_filter(value: str | None, name: str) -> dt.datetime | None:
    """Parse an optional UI date filter; bad input flashes and is ignored."""
    if not value:
        return None
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        flash(f"Ignored invalid {name} filter (use ISO 8601).", "error")
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=dt.UTC)


@web_bp.get("/vehicles/<vin>/telemetry")
@auth.ui_login_required
def telemetry_browser(vin: str):
    """Paginated, filterable table of raw snapshots with edit/delete actions."""
    if vehicle_service.get(vin) is None:
        abort(404)

    start = _parse_filter(request.args.get("start"), "start")
    end = _parse_filter(request.args.get("end"), "end")
    order = "asc" if request.args.get("order") == "asc" else "desc"
    try:
        limit = max(1, min(int(request.args.get("limit", 50)), 500))
        offset = max(0, int(request.args.get("offset", 0)))
    except ValueError:
        limit, offset = 50, 0

    rows, has_more = telemetry_service.query(
        vin, start=start, end=end, order=order, limit=limit, offset=offset
    )
    return render_template(
        "telemetry_browser.html",
        vin=vin,
        rows=rows,
        has_more=has_more,
        limit=limit,
        offset=offset,
        order=order,
        start=request.args.get("start", ""),
        end=request.args.get("end", ""),
    )


# ── Manual entry (Step 5.3) ────────────────────────────────────────────────────


@web_bp.route("/telemetry/new", methods=["GET", "POST"])
@web_bp.route("/vehicles/<vin>/telemetry/new", methods=["GET", "POST"])
@auth.ui_login_required
def telemetry_new(vin: str | None = None):
    """Add one snapshot by hand. Stored with `source=manual` (§9.1)."""
    if request.method == "POST":
        values = {
            key: value.strip()
            for key, value in request.form.items()
            if key not in ("csrf_token", "extra") and value.strip()
        }
        extra_text = request.form.get("extra", "").strip()
        errors: list[str] = []
        if extra_text:
            try:
                values["extra"] = json.loads(extra_text)
            except ValueError:
                errors.append("extra: not valid JSON")

        row = None
        if not errors:
            try:
                row = TelemetryCsvRow.model_validate(values)
            except ValidationError as exc:
                errors = [
                    f"{'.'.join(str(p) for p in e.get('loc', ()))}: {e.get('msg', '')}"
                    for e in exc.errors()
                ]
        if errors or row is None:
            for message in errors:
                flash(message, "error")
            return (
                render_template(
                    "telemetry_form.html",
                    mode="new",
                    vin=vin,
                    form_fields=FORM_FIELDS,
                    values=request.form,
                ),
                400,
            )

        session = get_session()
        columns = row.model_dump()
        columns["source"] = "manual"
        vehicle_service.upsert_vehicle(
            row.vin, row.device_id, seen_at=row.time, session=session
        )
        telemetry_service.upsert_row(columns, session=session)
        session.commit()
        flash(f"Snapshot added for {row.vin} at {iso_z(row.time)}.", "success")
        return redirect(url_for("web.telemetry_browser", vin=row.vin))

    return render_template(
        "telemetry_form.html", mode="new", vin=vin, form_fields=FORM_FIELDS, values={}
    )


# ── Edit / delete one snapshot (Step 5.3) ──────────────────────────────────────


@web_bp.route("/telemetry/<vin>/<timestamp>/edit", methods=["GET", "POST"])
@auth.ui_login_required
def telemetry_edit(vin: str, timestamp: str):
    """Edit one snapshot's signal values. Blank input clears the value."""
    time = parse_iso(timestamp, param="timestamp")
    session = get_session()
    row = telemetry_service.get_one(vin, time, session=session)
    if row is None:
        abort(404)

    if request.method == "POST":
        # The form posts every editable field; blank → None (explicit clear).
        fields = {
            name: (request.form.get(name, "").strip() or None)
            for name, _label, _kind in FORM_FIELDS
        }
        try:
            edit = TelemetryEdit.model_validate(fields)
        except ValidationError as exc:
            for err in exc.errors():
                loc = ".".join(str(p) for p in err.get("loc", ()))
                flash(f"{loc}: {err.get('msg', '')}", "error")
            return (
                render_template(
                    "telemetry_form.html",
                    mode="edit",
                    vin=vin,
                    timestamp=timestamp,
                    form_fields=FORM_FIELDS,
                    values=request.form,
                ),
                400,
            )
        # exclude_unset: only the fields the form actually posted — never e.g.
        # source=None (not on the form; NOT NULL in the table).
        telemetry_service.update_one(
            vin, time, edit.model_dump(exclude_unset=True), session=session
        )
        session.commit()
        flash(f"Snapshot {iso_z(time)} updated.", "success")
        return redirect(url_for("web.telemetry_browser", vin=vin))

    values = {name: getattr(row, name) for name, _label, _kind in FORM_FIELDS}
    # Blank out Nones so the form shows empty inputs rather than "None".
    values = {k: ("" if v is None else v) for k, v in values.items()}
    return render_template(
        "telemetry_form.html",
        mode="edit",
        vin=vin,
        timestamp=timestamp,
        form_fields=FORM_FIELDS,
        values=values,
    )


@web_bp.post("/telemetry/<vin>/<timestamp>/delete")
@auth.ui_login_required
def telemetry_delete(vin: str, timestamp: str):
    """Delete one snapshot (CSRF-protected POST; the row's Delete button)."""
    time = parse_iso(timestamp, param="timestamp")
    session = get_session()
    if telemetry_service.delete_one(vin, time, session=session):
        session.commit()
        flash(f"Deleted snapshot {iso_z(time)}.", "success")
    else:
        flash("Snapshot was already gone.", "error")
    return redirect(url_for("web.telemetry_browser", vin=vin))


# ── CSV upload (Step 5.4) ──────────────────────────────────────────────────────


@web_bp.route("/import", methods=["GET", "POST"])
@auth.ui_login_required
def import_page():
    """CSV upload form → import report (imported/skipped + per-row errors)."""
    if request.method == "POST":
        upload = request.files.get("file")
        if upload is None or not upload.filename:
            flash("Choose a CSV file to upload.", "error")
            return render_template("import.html", report=None), 400

        session = get_session()
        try:
            report = csv_service.parse_and_import(upload.stream, session=session)
        except CsvFormatError as exc:
            session.rollback()
            flash(str(exc), "error")
            return render_template("import.html", report=None), 400
        session.commit()
        flash(
            f"Imported {report.imported} row(s), skipped {report.skipped}.",
            "success" if report.imported else "error",
        )
        return render_template("import.html", report=report)

    return render_template("import.html", report=None)


# ── Admin shell (Step 5.4) ─────────────────────────────────────────────────────


@web_bp.get("/admin")
@auth.ui_login_required
def admin():
    """Export, DB backup/restore, and retention/lifecycle info (§8, §6.2)."""
    vehicles, _total = vehicle_service.list_vehicles(limit=500)
    stats = stats_service.fleet_stats()
    return render_template(
        "admin.html",
        vehicles=[v for v, _samples, _events in vehicles],
        retention_days=current_app.config["TELEMETRY_RETENTION_DAYS"],
        oldest=stats["oldest"],
        newest=stats["newest"],
        api_key_configured=bool(current_app.config["API_KEY"]),
        pg_tools=backup_service.pg_tools_available(),
    )


@web_bp.get("/admin/export")
@auth.ui_login_required
def admin_export():
    """
    Session-authenticated CSV download for the admin form. A browser form
    cannot send the API-key header the `/api/v1/telemetry/export` endpoint
    requires, so the UI streams the same `csv_service.export_rows` under the
    login session instead.
    """
    from flask import Response, stream_with_context

    vin = (request.args.get("vin") or "").strip()
    if not vin or vehicle_service.get(vin) is None:
        flash("Pick a vehicle to export.", "error")
        return redirect(url_for("web.admin"))
    start = _parse_filter(request.args.get("start"), "start")
    end = _parse_filter(request.args.get("end"), "end")

    def _stamp(value: dt.datetime | None, fallback: str) -> str:
        return value.strftime("%Y%m%dT%H%M%SZ") if value is not None else fallback

    filename = f"{vin}_{_stamp(start, 'begin')}_{_stamp(end, 'now')}.csv"
    generator = stream_with_context(csv_service.export_rows(vin, start=start, end=end))
    return Response(
        generator,
        mimetype="text/csv",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@web_bp.get("/admin/backup")
@auth.ui_login_required
def admin_backup():
    """Session-authenticated full-database backup download (§8)."""
    from flask import Response, stream_with_context

    try:
        generator = stream_with_context(backup_service.dump())
        first = next(generator, b"")
    except backup_service.PgToolsUnavailable as exc:
        flash(str(exc), "error")
        return redirect(url_for("web.admin"))

    def _body():
        yield first
        yield from generator

    filename = backup_service.backup_filename()
    return Response(
        _body(),
        mimetype="application/octet-stream",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@web_bp.post("/admin/restore")
@auth.ui_login_required
def admin_restore():
    """
    Guarded restore-from-backup (§8). DESTRUCTIVE — the form has an explicit
    confirm checkbox; without it we refuse and flash.
    """
    if request.form.get("confirm") != "yes":
        flash("Restore not confirmed — tick the confirmation box to proceed.", "error")
        return redirect(url_for("web.admin"))
    upload = request.files.get("file")
    if upload is None or not upload.filename:
        flash("Choose a backup file to restore.", "error")
        return redirect(url_for("web.admin"))

    try:
        backup_service.restore(upload.stream)
    except backup_service.PgToolsUnavailable as exc:
        flash(str(exc), "error")
        return redirect(url_for("web.admin"))
    except backup_service.BackupError:
        flash("Restore failed — see server logs.", "error")
        return redirect(url_for("web.admin"))

    flash("Database restored from backup.", "success")
    return redirect(url_for("web.dashboard"))
