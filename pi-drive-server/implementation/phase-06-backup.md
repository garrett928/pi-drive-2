# Phase 6: Backup / Restore + Retention

**Goal:** Full-database backup/restore (distinct from telemetry CSV) via API and the admin UI, plus a configurable telemetry retention policy.

**Depends on:** Phase 3 (admin blueprint), Phase 5 (admin page). Uses `pg_dump`/`pg_restore` (installed in the image — Phase 7 / dev Dockerfile).

**Reference:** `../REQUIREMENTS.md` §8, §10.2 (`TELEMETRY_RETENTION_DAYS`).

---

## Step 6.1 — DB backup/restore endpoints + admin UI

**What to build in `app/services/backup_service.py` + `app/api/admin.py` + admin template:**

1. **`backup_service.dump() -> stream`:** shell out to `pg_dump` (custom/compressed format `-Fc`) against `DATABASE_URL`; stream stdout to the response. Never interpolate the URL into a shell string unsafely — pass args as a list; pass the password via `PGPASSWORD`/`.pgpass` env, not argv. Log tag `BackupService`.
2. **`GET /api/v1/admin/backup`** (`@require_api_key`): streamed download, `Content-Disposition: pidrive-backup-{timestamp}.dump`.
3. **`backup_service.restore(file_stream)`:** run `pg_restore --clean --if-exists` into the DB. **Destructive** — only proceed when explicitly confirmed.
4. **`POST /api/v1/admin/restore`** (multipart `file=`, `?confirm=true`, `@require_api_key`): refuse without `confirm` (`400`); on success `200 {"restored": true}`. Document clearly that this replaces existing data.
5. **Admin UI:** wire the Phase 5 admin page — "Download backup" button → backup endpoint; "Restore from backup" file form with an explicit confirm checkbox → restore endpoint; flash result.

**Tests (integration):**
- Backup endpoint returns a non-empty `pg_dump` stream.
- Seed data → backup → wipe → restore → data matches (round-trip). (Use a disposable test DB.)
- Restore without `confirm` → 400.

---

## Step 6.2 — Retention policy + data lifecycle

**What to build:**

1. **Timescale retention:** if `TELEMETRY_RETENTION_DAYS` is set, apply a Timescale `add_retention_policy('telemetry', INTERVAL 'N days')` (via the `app/db/timescale.py` helper) on startup/migration; unset → keep forever. Document that retention drops old chunks automatically.
2. **Admin page:** display current retention setting and oldest stored timestamp.
3. **Optional manual purge:** the bulk range-delete from Step 3.3 already covers ad-hoc cleanup; reference it from the admin page.

**Tests (integration):**
- With a short retention configured, inserting an old-dated row and triggering the policy drops it (or assert the policy exists via `timescaledb_information.jobs`).

**Verify (manual):**
```bash
curl -s localhost:8080/api/v1/admin/backup -H "Authorization: Bearer $API_KEY" -o backup.dump
curl -sX POST localhost:8080/api/v1/admin/restore -H "Authorization: Bearer $API_KEY" -F file=@backup.dump "?confirm=true"
```

**Estimated size:** ~600 lines across the phase.
