"""
Telemetry service — the only place telemetry/event rows are read/written.

Write path (Phase 2): `upsert_snapshot` (idempotent by `(vin, time)`),
`insert_events` (deduped), and `ingest_batch` — the one-transaction orchestrator
behind `POST /telemetry`. Read/edit/delete path (Phase 3): `query`, `get_one`,
`update_one`, `delete_one`, `delete_range`.

All statements are parameterized ORM/Core — never string concatenation (Java
server Known Bug #6). Functions take an optional `session` (defaulting to the
request-scoped session) and flush rather than commit; the route owns the
transaction boundary so a whole batch commits or rolls back together.
"""

from __future__ import annotations

import datetime as dt
import logging
import time as time_mod

from sqlalchemy import delete as sa_delete
from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.db.models import DrivingEvent, Telemetry
from app.db.session import get_session
from app.schemas.telemetry import EventIn, TelemetryIn, to_orm_columns
from app.services import vehicle_service

logger = logging.getLogger("TelemetryService")
ingest_logger = logging.getLogger("Ingest")


class DeviceIdMismatchError(ValueError):
    """Body `device_id` and the `X-Device-Id` header are both present but disagree."""


def _session(session: Session | None) -> Session:
    return session if session is not None else get_session()


def latest_timestamp(vin: str, *, session: Session | None = None) -> dt.datetime | None:
    """
    Return the most recent telemetry `time` stored for `vin`, or None if the VIN
    has no telemetry yet.

    Backs `GET /telemetry/latest` (the app's "last synced" display / sync offset).
    """
    stmt = select(func.max(Telemetry.time)).where(Telemetry.vin == vin)
    return _session(session).execute(stmt).scalar_one_or_none()


# ── Write path (Phase 2) ───────────────────────────────────────────────────────


def upsert_row(row: dict, *, session: Session | None = None) -> None:
    """
    Upsert one flat telemetry row dict by `(vin, time)` — the idempotency
    contract shared by the wire ingest path and the CSV importer.

    `INSERT ... ON CONFLICT (vin, time) DO UPDATE` with last-write-wins
    semantics: a re-upload replaces all signal columns with the new values,
    so retried batches and re-imported files can never duplicate rows.
    The owning vehicle row must already exist (callers upsert it first).
    """
    stmt = pg_insert(Telemetry).values(**row)
    update_cols = {
        name: stmt.excluded[name] for name in row if name not in ("vin", "time")
    }
    stmt = stmt.on_conflict_do_update(index_elements=["vin", "time"], set_=update_cols)
    sess = _session(session)
    sess.execute(stmt)
    sess.flush()


def upsert_snapshot(
    snapshot: TelemetryIn,
    *,
    source: str = "device",
    device_id_fallback: str | None = None,
    session: Session | None = None,
) -> None:
    """
    Upsert one wire-format snapshot by `(vin, time)`.

    Flattens the nested payload into a row dict and delegates to `upsert_row`
    (the single ON CONFLICT write path).
    """
    row = to_orm_columns(
        snapshot, source=source, device_id_fallback=device_id_fallback
    )
    upsert_row(row, session=session)


def insert_events(
    vin: str,
    events: list[EventIn],
    *,
    source: str = "device",
    session: Session | None = None,
) -> int:
    """
    Insert driving events for a payload, skipping exact duplicates.

    Events have no natural primary key on the wire, but the app retries offline
    batches — so a re-POST must not multiply event rows (same spirit as the
    `(vin, time)` snapshot upsert). An event is considered a duplicate when a
    row with the same `(vin, time, strategy, type)` already exists. Returns the
    number of rows actually inserted.
    """
    sess = _session(session)
    inserted = 0
    for event in events:
        exists = sess.execute(
            select(DrivingEvent.id).where(
                DrivingEvent.vin == vin,
                DrivingEvent.time == event.timestamp,
                DrivingEvent.strategy == event.strategy,
                DrivingEvent.type == event.type,
            )
        ).first()
        if exists:
            continue
        sess.add(
            DrivingEvent(
                vin=vin,
                time=event.timestamp,
                strategy=event.strategy,
                type=event.type,
                duration_ms=event.duration_ms,
                rate_mph_s=event.rate_mph_s,
                peak_g=event.peak_g,
                peak_accel_mps2=event.peak_accel_mps2,
                start_speed_mph=event.start_speed_mph,
                end_speed_mph=event.end_speed_mph,
                sources=event.sources,
                source=source,
            )
        )
        inserted += 1
    sess.flush()
    return inserted


def ingest_batch(
    items: list[TelemetryIn],
    *,
    device_id_header: str | None = None,
    source: str = "device",
    session: Session | None = None,
) -> tuple[int, set[str]]:
    """
    Ingest a validated batch: the write path behind `POST /telemetry`.

    For each snapshot, in one shared transaction (the caller commits):
      1. enforce body/header `device_id` agreement (§5.5 — 400 on mismatch),
      2. auto-register / refresh the vehicle (`vehicle_service.upsert_vehicle`),
      3. upsert the snapshot by `(vin, time)`,
      4. insert its events (deduped).

    Returns `(accepted_count, vins_seen)` so the response can confirm exactly
    what was written (fixes Java server Known Bug #7 — no write confirmation).
    """
    sess = _session(session)
    started = time_mod.monotonic()
    vins: set[str] = set()

    for item in items:
        if (
            device_id_header
            and item.device_id
            and device_id_header != item.device_id
        ):
            raise DeviceIdMismatchError(
                "device_id in body does not match X-Device-Id header"
            )
        effective_device = item.device_id or device_id_header
        vehicle_service.upsert_vehicle(
            item.vin, effective_device, seen_at=item.timestamp, session=sess
        )
        upsert_snapshot(
            item, source=source, device_id_fallback=device_id_header, session=sess
        )
        if item.events:
            insert_events(item.vin, item.events, source=source, session=sess)
        vins.add(item.vin)

    duration_ms = int((time_mod.monotonic() - started) * 1000)
    # Batch-level summary only — never a log line per record (§10.4).
    ingest_logger.info(
        "event=ingest_batch accepted=%d vehicles=%s duration_ms=%d",
        len(items),
        sorted(vins),
        duration_ms,
    )
    return len(items), vins


# ── Query / edit / delete (Phase 3) ─────────────────────────────────────────────

#: Columns a client may request via `fields=` or edit via PATCH. Everything in
#: the table except the identity pair (vin/time) and provenance (source), which
#: have dedicated handling.
SIGNAL_COLUMNS: tuple[str, ...] = (
    "device_id",
    "lat",
    "lng",
    "speed_gps",
    "speed_kmh",
    "rpm",
    "coolant_temp_c",
    "intake_air_temp_c",
    "throttle_pct",
    "fuel_level_pct",
    "oil_temp_c",
    "maf_gps",
    "fuel_rate_lph",
    "battery_voltage",
    "fuel_economy_mpg",
    "fuel_economy_kml",
    "accel_mps2",
    "extra",
    "source",
)


def query(
    vin: str,
    *,
    start: dt.datetime | None = None,
    end: dt.datetime | None = None,
    order: str = "desc",
    limit: int = 100,
    offset: int = 0,
    session: Session | None = None,
) -> tuple[list[Telemetry], bool]:
    """
    Time-bounded, paginated telemetry query for one VIN.

    Always parameterized and bounded by the caller's range (no hardcoded
    1-year window — Java server Known Bug #3). Leverages the `(vin, time desc)`
    index. Returns `(rows, has_more)`; `has_more` is computed by fetching one
    row past `limit`, avoiding a separate COUNT over the hypertable.
    """
    stmt = select(Telemetry).where(Telemetry.vin == vin)
    if start is not None:
        stmt = stmt.where(Telemetry.time >= start)
    if end is not None:
        stmt = stmt.where(Telemetry.time <= end)
    order_by = Telemetry.time.asc() if order == "asc" else Telemetry.time.desc()
    stmt = stmt.order_by(order_by).limit(limit + 1).offset(offset)

    rows = list(_session(session).execute(stmt).scalars())
    has_more = len(rows) > limit
    return rows[:limit], has_more


def get_one(
    vin: str, time: dt.datetime, *, session: Session | None = None
) -> Telemetry | None:
    """Return the single snapshot at exactly `(vin, time)`, or None."""
    return _session(session).get(Telemetry, (vin, time))


def update_one(
    vin: str,
    time: dt.datetime,
    fields: dict,
    *,
    session: Session | None = None,
) -> Telemetry | None:
    """
    Partially update one snapshot's signal values. Returns the updated row, or
    None if `(vin, time)` does not exist. Callers validate `fields` keys against
    `SIGNAL_COLUMNS` before calling (the API layer uses a Pydantic schema).
    """
    sess = _session(session)
    row = sess.get(Telemetry, (vin, time))
    if row is None:
        return None
    for name, value in fields.items():
        setattr(row, name, value)
    sess.flush()
    return row


def delete_one(vin: str, time: dt.datetime, *, session: Session | None = None) -> bool:
    """Delete one snapshot. Returns True if a row was removed."""
    sess = _session(session)
    row = sess.get(Telemetry, (vin, time))
    if row is None:
        return False
    sess.delete(row)
    sess.flush()
    return True


def delete_range(
    vin: str,
    *,
    start: dt.datetime | None = None,
    end: dt.datetime | None = None,
    session: Session | None = None,
) -> int:
    """
    Bulk-delete a VIN's snapshots within an optional time range (unbounded side
    allowed). Returns the number of rows deleted. The API layer requires
    `?confirm=true` before calling.
    """
    stmt = sa_delete(Telemetry).where(Telemetry.vin == vin)
    if start is not None:
        stmt = stmt.where(Telemetry.time >= start)
    if end is not None:
        stmt = stmt.where(Telemetry.time <= end)
    sess = _session(session)
    result = sess.execute(stmt)
    sess.flush()
    return result.rowcount or 0
