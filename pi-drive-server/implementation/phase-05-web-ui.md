# Phase 5: Web UI (pure Flask / Jinja)

**Goal:** A small, server-rendered web UI — no SPA framework, no build step — for high-level stats, browsing/editing/deleting telemetry, manual entry, and CSV upload. Every action maps to an endpoint built in Phases 2–4.

**Depends on:** Phases 2–4 (the UI calls the same services).

**Reference:** `../REQUIREMENTS.md` §9. Styling reuses Pi Drive's warm-dark palette (`#D88A30` accent on `#201D19`; see `../pi-drive-android/ui-handoff/pi-drive/project/pd-tokens.jsx`).

---

## Step 5.1 — UI auth + base layout/CSS

> **Decision change (2026-06-11):** the operator does not want to log into the web UI — **`UI_REQUIRE_AUTH` now defaults to `false`** and the UI opens with no login. The login form, session marker, and `@ui_login_required` decorator remain in `app/web/auth.py` as an opt-in for exposed deployments (`UI_REQUIRE_AUTH=true`). CSRF protection on form POSTs stays on unconditionally. The REST API is unaffected (still key-guarded).

**What to build in `app/web/`:**

1. **`app/web/routes.py`** — Jinja blueprint. Config flag `UI_REQUIRE_AUTH` (default **false** — no login; opt-in for exposed deployments).
2. **Auth (opt-in):** `GET /login` form accepts the API key; on match, store an authenticated marker in a signed session cookie (`SECRET_KEY`); `GET /logout` clears it. A `@ui_login_required` decorator guards pages only when `UI_REQUIRE_AUTH=true`. (No user accounts — one key.)
3. **CSRF:** protect all UI form POSTs (Flask-WTF `CSRFProtect`, or a manual signed token) — always on, independent of the auth flag. Token-auth API endpoints remain CSRF-exempt.
4. **`templates/base.html`** — layout: header (nav: Dashboard, Vehicles, Import, Admin, Logout), flash-message area, footer. **`static/app.css`** — small stylesheet, dark theme tokens, readable tables, cards. Pages must be usable without JS.
5. **HTML error handlers:** extend `app/errors.py` content-negotiation so UI routes render friendly 401/404/500 pages.

**Tests:**
- Default config: pages open with no login (no redirect).
- With `UI_REQUIRE_AUTH=true`: unauthed access → redirect to `/login`; wrong key → re-prompt; correct key → session set, access granted.
- Form POST without CSRF token → rejected (regardless of the auth flag).

---

## Step 5.2 — Dashboard + vehicle detail

**What to build:**

1. **`GET /`** — Dashboard. Calls `stats_service.fleet_stats()`. Renders: totals (vehicles, samples, samples 24h/7d, events, storage span) and a per-vehicle summary card grid (nickname/vin, last seen, sample count, recent events, a mini sparkline). Sparkline: server-rendered inline SVG **or** a single Chart.js CDN include reading a small embedded JSON — page works without it.
2. **`GET /vehicles/{vin}`** — Vehicle detail: editable metadata form (PATCH via `vehicle_service`), stat rollups over a selectable range (query param), recent telemetry table (paginated), recent events, export links.

**Tests:**
- Dashboard renders with seeded data (counts visible in HTML); empty-state renders with zero vehicles.
- Vehicle detail shows metadata; editing nickname via the form persists.

---

## Step 5.3 — Telemetry browser + manual-entry + edit/delete forms

**What to build:**

1. **`GET /vehicles/{vin}/telemetry`** — paginated, sortable, filterable (date range) table of snapshots; each row has **Edit** and **Delete** actions.
2. **`GET /telemetry/new` / `GET /vehicles/{vin}/telemetry/new`** + POST handler — manual-entry form for all known fields → `telemetry_service.upsert_snapshot(source=manual)`. Validation errors re-render the form with messages.
3. **`GET /telemetry/{vin}/{timestamp}/edit`** + POST — edit-one form (PATCH path). 
4. **Delete** — POST (CSRF-protected) → `delete_one`; confirm via a small interstitial or inline confirm; flash result.

**Tests:**
- Browser paginates and filters by date.
- Manual entry form creates a row (`source=manual`), visible afterward.
- Edit changes a value; delete removes the row; both flash confirmation.

---

## Step 5.4 — CSV upload page + admin shell

**What to build:**

1. **`GET /import`** + POST (multipart) — CSV upload form → `csv_service.parse_and_import` → results page showing imported/skipped and a table of per-row errors.
2. **`GET /admin`** — admin shell page: telemetry CSV export (download link/form with vin+range), and placeholders for DB backup/restore (wired in Phase 6) and retention info. Shows that an API key is configured (never displays it).

**Tests:**
- Upload a good CSV via the form → results page shows `imported=N`.
- Upload a CSV with bad rows → error table lists them; good rows imported.

**Verify (manual):** log in, walk Dashboard → Vehicle → Telemetry (add/edit/delete) → Import → Admin; confirm each action and its flash message. Capture screenshots into `pi-drive-server/screenshots/` for the record.

**Estimated size:** ~1.6k lines across the phase.
