# Phase 4: CSV Import / Export + Manual Entry

**Goal:** Move telemetry in and out as CSV via API (and, in Phase 5, the UI). Round-trip safe: a file exported here re-imports without loss. Partial failures are reported, never silently dropped.

**Depends on:** Phase 2 (write path), Phase 3 (query path).

**Reference:** `../REQUIREMENTS.md` §7. CSV is **telemetry data**, distinct from DB backups (Phase 6).

---

## Step 4.1 — CSV import

**What to build in `app/services/csv_service.py` + `app/api/admin.py`:**

1. **`csv_service.parse_and_import(file_stream) -> ImportReport`:**
   - Header row maps to `telemetry` columns (§4.2). `vin` and `time` columns **required**; missing → row error.
   - Per row: coerce types via the same Pydantic typing rules as ingest (reuse `TelemetryIn` flattening where practical), upsert by `(vin, time)`, auto-register vehicle, `source=csv`.
   - Collect per-row errors `{row_number, reason}` and continue (partial success). Wrap the successful rows in a transaction (or batch-commit with a documented policy).
   - `ImportReport(imported, skipped, errors[])`.
2. **`POST /api/v1/telemetry/import`** (multipart `file=`, `@require_api_key`): stream the upload to `csv_service`; return `200 {imported, skipped, errors}` (use `207`/`200` consistently — document). Reject non-CSV / oversized files (`413`/`415`).

**Tests (integration):**
- Valid CSV of N rows → `imported=N`, vehicles registered, floats preserved.
- File with 2 bad rows (missing vin, bad timestamp) → those in `errors`, the rest imported.
- Re-importing the same file → idempotent (upsert), no duplicates.

---

## Step 4.2 — CSV export + manual entry confirmation

**What to build:**

1. **`csv_service.export(vin, start, end) -> iterator`:** stream rows as CSV (generator → Flask streaming response) so large ranges don't buffer in memory. Column order is stable and matches the import header (round-trip).
2. **`GET /api/v1/telemetry/export?vin=&start=&end=`** (`@require_api_key`): streamed `text/csv` download with a sensible `Content-Disposition` filename (`{vin}_{start}_{end}.csv`).
3. **Round-trip test:** export a VIN's data → re-import into a fresh DB → row counts and values match.
4. Confirm the manual single-row entry endpoint from Step 3.3 (`POST /api/v1/telemetry`, `source=manual`) is covered; add a test asserting a hand-entered row appears in a subsequent export.

**Tests (integration):**
- Export streams valid CSV; header matches importer.
- Export → import round-trip preserves rows and types.

**Verify (manual):**
```bash
curl -s "localhost:8080/api/v1/telemetry/export?vin=$VIN" -H "Authorization: Bearer $API_KEY" -o out.csv
curl -sX POST localhost:8080/api/v1/telemetry/import -H "Authorization: Bearer $API_KEY" -F file=@out.csv
```

**Estimated size:** ~700 lines across the phase.
