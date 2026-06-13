"""
Stats service — fleet/vehicle rollups for the dashboard and stats API (§6.4).

Two data paths, chosen deliberately:

- **Exact scalars** (counts, avg/max over a requested range) run as
  parameterized aggregates against the raw hypertable — always fresh, exact,
  and fast for bounded ranges thanks to the `(vin, time desc)` index.
- **The daily time series** reads the `telemetry_daily` continuous aggregate,
  which is what makes year-long dashboard charts cheap (no raw-row scan).
  Real-time aggregation is enabled (migration 0003), so the series includes
  rows ingested since the last materialization.
"""

from __future__ import annotations

import datetime as dt
import logging
from typing import Any

from sqlalchemy import func, select, text
from sqlalchemy.orm import Session

from app.db.models import DrivingEvent, Telemetry, Vehicle
from app.db.session import get_session
from app.services import vehicle_service

logger = logging.getLogger("PiDriveServer")


def _session(session: Session | None) -> Session:
    return session if session is not None else get_session()


def _iso(value: dt.datetime | None) -> str | None:
    """Same Z-suffixed format as `app.api.common.iso_z` (duplicated rather than
    imported — services must not depend on the API layer)."""
    if value is None:
        return None
    spec = "milliseconds" if value.microsecond else "seconds"
    return value.isoformat(timespec=spec).replace("+00:00", "Z")


def fleet_stats(*, session: Session | None = None) -> dict[str, Any]:
    """
    Fleet-wide high-level stats: the JSON behind the UI dashboard.

    Totals, recent-activity counts (last 24h / 7d wall clock), the storage span,
    and a per-vehicle summary list.
    """
    sess = _session(session)
    now = dt.datetime.now(dt.UTC)

    total_vehicles = sess.execute(
        select(func.count()).select_from(Vehicle)
    ).scalar_one()
    total_samples = sess.execute(
        select(func.count()).select_from(Telemetry)
    ).scalar_one()
    total_events = sess.execute(
        select(func.count()).select_from(DrivingEvent)
    ).scalar_one()
    samples_24h = sess.execute(
        select(func.count())
        .select_from(Telemetry)
        .where(Telemetry.time >= now - dt.timedelta(hours=24))
    ).scalar_one()
    samples_7d = sess.execute(
        select(func.count())
        .select_from(Telemetry)
        .where(Telemetry.time >= now - dt.timedelta(days=7))
    ).scalar_one()
    oldest, newest = sess.execute(
        select(func.min(Telemetry.time), func.max(Telemetry.time))
    ).one()

    vehicles, _total = vehicle_service.list_vehicles(
        limit=100, offset=0, session=sess
    )
    per_vehicle = [
        {
            "vin": v.vin,
            "nickname": v.nickname,
            "last_seen": _iso(v.last_seen),
            "sample_count": samples,
            "event_count": events,
        }
        for v, samples, events in vehicles
    ]

    return {
        "total_vehicles": int(total_vehicles),
        "total_samples": int(total_samples),
        "total_events": int(total_events),
        "samples_24h": int(samples_24h),
        "samples_7d": int(samples_7d),
        "oldest": _iso(oldest),
        "newest": _iso(newest),
        "vehicles": per_vehicle,
    }


def vehicle_stats(
    vin: str,
    start: dt.datetime | None = None,
    end: dt.datetime | None = None,
    *,
    session: Session | None = None,
) -> dict[str, Any] | None:
    """
    Per-vehicle stats over an optional time range, plus the daily series from
    the continuous aggregate. Returns None for an unknown VIN.
    """
    sess = _session(session)
    if sess.get(Vehicle, vin) is None:
        return None

    conditions = [Telemetry.time >= start] if start is not None else []
    if end is not None:
        conditions.append(Telemetry.time <= end)

    sample_count, avg_speed, max_speed, avg_mpg, first_time, last_time = sess.execute(
        select(
            func.count(),
            func.avg(Telemetry.speed_kmh),
            func.max(Telemetry.speed_kmh),
            func.avg(Telemetry.fuel_economy_mpg),
            func.min(Telemetry.time),
            func.max(Telemetry.time),
        ).where(Telemetry.vin == vin, *conditions)
    ).one()

    event_conditions = [DrivingEvent.time >= start] if start is not None else []
    if end is not None:
        event_conditions.append(DrivingEvent.time <= end)
    events_by_type = {
        type_: int(n)
        for type_, n in sess.execute(
            select(DrivingEvent.type, func.count())
            .where(DrivingEvent.vin == vin, *event_conditions)
            .group_by(DrivingEvent.type)
        )
    }

    # Daily series from the continuous aggregate (parameterized text SQL — the
    # CAGG is not ORM-mapped). Real-time aggregation keeps it current.
    daily_sql = (
        "SELECT bucket, sample_count, avg_speed_kmh, max_speed_kmh, avg_fuel_economy_mpg "
        "FROM telemetry_daily WHERE vin = :vin"
    )
    params: dict[str, Any] = {"vin": vin}
    if start is not None:
        daily_sql += " AND bucket >= :start"
        params["start"] = start
    if end is not None:
        daily_sql += " AND bucket <= :end"
        params["end"] = end
    daily_sql += " ORDER BY bucket"

    daily = [
        {
            "bucket": _iso(bucket),
            "sample_count": int(n),
            "avg_speed_kmh": float(avg_s) if avg_s is not None else None,
            "max_speed_kmh": float(max_s) if max_s is not None else None,
            "avg_fuel_economy_mpg": float(mpg) if mpg is not None else None,
        }
        for bucket, n, avg_s, max_s, mpg in sess.execute(text(daily_sql), params)
    ]

    return {
        "vin": vin,
        "sample_count": int(sample_count),
        "avg_speed_kmh": float(avg_speed) if avg_speed is not None else None,
        "max_speed_kmh": float(max_speed) if max_speed is not None else None,
        "avg_fuel_economy_mpg": float(avg_mpg) if avg_mpg is not None else None,
        "first_time": _iso(first_time),
        "last_time": _iso(last_time),
        "events_by_type": events_by_type,
        "daily": daily,
    }
