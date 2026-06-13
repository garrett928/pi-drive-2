"""
CSV import/export service (REQUIREMENTS.md §7).

CSV moves **telemetry data** in and out of the store — it is not a database
backup (that is Phase 6). The format is one header row of telemetry column
names followed by one line per snapshot, and it is round-trip safe: a file
produced by `export_rows` re-imports through `parse_and_import` without loss.

Import policy (documented behavior):
  - The header is validated up front: `vin` and `time` are required, unknown
    column names are rejected for the whole file (fail loud — a typo'd header
    would otherwise silently drop a column from every row).
  - Each data row is validated independently (`TelemetryCsvRow`, the same
    typing rules as wire ingest). A bad row is recorded in `errors` with its
    file line number and skipped; good rows continue — partial success is
    reported, never silently dropped.
  - Successful rows are upserted by `(vin, time)` (re-importing a file is
    idempotent), vehicles are auto-registered, and rows are stored with
    `source=csv` regardless of any `source` column in the file (provenance
    records how *this* copy arrived).
  - The service flushes but does not commit; the route commits once after the
    whole file is processed, so all imported rows land atomically.
"""

from __future__ import annotations

import csv
import datetime as dt
import io
import json
import logging
from collections.abc import Iterator
from dataclasses import dataclass, field
from typing import IO, Any

from pydantic import ValidationError
from sqlalchemy.orm import Session

from app.schemas.telemetry import TelemetryCsvRow
from app.services import telemetry_service, vehicle_service

logger = logging.getLogger("CsvService")

#: Canonical CSV column order. Matches the `telemetry` table (§4.2); the
#: export header and the import validator both use it, which is what makes
#: export → import round-trip safe.
CSV_COLUMNS: tuple[str, ...] = (
    "vin",
    "time",
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


class CsvFormatError(ValueError):
    """The file as a whole is unusable (bad/missing header) — maps to 400."""


@dataclass
class ImportReport:
    """Outcome of one CSV import: the §7 partial-success contract."""

    imported: int = 0
    skipped: int = 0
    errors: list[dict[str, Any]] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        """The JSON body of the import response."""
        return {"imported": self.imported, "skipped": self.skipped, "errors": self.errors}


def _iso(value: dt.datetime) -> str:
    """Same Z-suffixed format as `app.api.common.iso_z` (duplicated rather than
    imported — services must not depend on the API layer)."""
    spec = "milliseconds" if value.microsecond else "seconds"
    return value.isoformat(timespec=spec).replace("+00:00", "Z")


def _row_error(report: ImportReport, line_number: int, reason: str) -> None:
    """Record one skipped row. `row` is the 1-based file line (header = line 1)."""
    report.skipped += 1
    report.errors.append({"row": line_number, "reason": reason})


def _validation_reason(exc: ValidationError) -> str:
    """Flatten a Pydantic error into one human-readable line for the report."""
    return "; ".join(
        f"{'.'.join(str(p) for p in err.get('loc', ())) or 'row'}: {err.get('msg', '')}"
        for err in exc.errors()
    )


def parse_and_import(
    stream: IO[bytes], *, session: Session | None = None
) -> ImportReport:
    """
    Import a CSV file of telemetry rows (§7).

    Validates the header, then per row: coerce types via `TelemetryCsvRow`,
    auto-register the vehicle, upsert by `(vin, time)` with `source=csv`.
    Returns an `ImportReport`; raises `CsvFormatError` if the header is
    unusable (missing vin/time, unknown columns, empty file).
    """
    # utf-8-sig transparently strips a BOM, which Excel prepends to CSV exports.
    text = io.TextIOWrapper(stream, encoding="utf-8-sig", newline="")
    reader = csv.DictReader(text)

    if reader.fieldnames is None:
        raise CsvFormatError("CSV file is empty — expected a header row")
    header = [name.strip() for name in reader.fieldnames]
    missing = {"vin", "time"} - set(header)
    if missing:
        raise CsvFormatError(
            f"CSV header is missing required column(s): {', '.join(sorted(missing))}"
        )
    unknown = [name for name in header if name not in CSV_COLUMNS]
    if unknown:
        raise CsvFormatError(
            f"CSV header has unknown column(s): {', '.join(unknown)} "
            f"(expected a subset of: {', '.join(CSV_COLUMNS)})"
        )

    report = ImportReport()
    for line_number, raw in enumerate(reader, start=2):  # header is line 1
        # DictReader yields None for short rows / "" for empty cells; both mean
        # "no value". Strip whitespace so " 42 " coerces cleanly.
        values: dict[str, Any] = {
            key.strip(): value.strip()
            for key, value in raw.items()
            if key is not None and value is not None and value.strip() != ""
        }

        # `extra` travels as a JSON string inside one CSV cell.
        if "extra" in values:
            try:
                values["extra"] = json.loads(values["extra"])
            except ValueError:
                _row_error(report, line_number, "extra: not valid JSON")
                continue

        try:
            row = TelemetryCsvRow.model_validate(values)
        except ValidationError as exc:
            _row_error(report, line_number, _validation_reason(exc))
            continue

        columns = row.model_dump()
        columns["source"] = "csv"  # provenance of this copy, not the original's
        vehicle_service.upsert_vehicle(
            row.vin, row.device_id, seen_at=row.time, session=session
        )
        telemetry_service.upsert_row(columns, session=session)
        report.imported += 1

    logger.info(
        "event=csv_import imported=%d skipped=%d",
        report.imported,
        report.skipped,
    )
    return report


# Page size for the export query loop. Bounds memory per chunk; the response
# streams, so the total range can be arbitrarily large.
_EXPORT_PAGE = 1000


def export_rows(
    vin: str,
    *,
    start: dt.datetime | None = None,
    end: dt.datetime | None = None,
    session: Session | None = None,
) -> Iterator[str]:
    """
    Stream a VIN's telemetry as CSV text chunks (header first, then one line
    per snapshot, oldest first).

    A generator so Flask can stream the response — large ranges never buffer
    fully in memory. Column order is `CSV_COLUMNS`, which the importer accepts
    verbatim (round-trip safe).
    """
    buffer = io.StringIO()
    writer = csv.writer(buffer)

    def take() -> str:
        """Return what the csv writer produced since the last call."""
        chunk = buffer.getvalue()
        buffer.seek(0)
        buffer.truncate(0)
        return chunk

    writer.writerow(CSV_COLUMNS)
    yield take()

    offset = 0
    while True:
        rows, has_more = telemetry_service.query(
            vin,
            start=start,
            end=end,
            order="asc",
            limit=_EXPORT_PAGE,
            offset=offset,
            session=session,
        )
        for row in rows:
            writer.writerow(_csv_cells(row))
        if rows:
            yield take()
        if not has_more:
            return
        offset += _EXPORT_PAGE


def _csv_cells(row: Any) -> list[str]:
    """Serialize one Telemetry ORM row into CSV cells in `CSV_COLUMNS` order."""
    cells: list[str] = []
    for name in CSV_COLUMNS:
        value = getattr(row, name)
        if value is None:
            cells.append("")
        elif name == "time":
            cells.append(_iso(value))
        elif name == "extra":
            cells.append(json.dumps(value))
        else:
            cells.append(str(value))
    return cells
