---
name: implement-feature
description: Implement the next feature in the Pi Drive implementation plan. Works for both the Android app (Kotlin/Compose) and the Flask server (Python/Flask). Reads progress, loads the right phase file, implements the step, runs tests, and updates progress.
---

# Implement Feature

Implement a feature step from the Pi Drive implementation plan — for either the Android app or the Flask server.

## Step 1 — Determine project and step

Read the PROGRESS file for both projects to find what's active:
- `pi-drive-android/implementation/PROGRESS.md`
- `pi-drive-server/implementation/PROGRESS.md`

If the user specifies a step (e.g. "implement server step 2.1" or "implement Android step 3.2"), use that. If not, use the project whose active step is earlier / more urgent, or ask if both are in progress simultaneously.

---

## Route A — Android (Kotlin / Compose / Gradle)

### A.2 — Load phase plan

Read `pi-drive-android/implementation/phase-NN-*.md` for the current step only.

### A.3 — Check prerequisites

Verify prerequisite phases are DONE in `pi-drive-android/implementation/PROGRESS.md`. If not, implement prerequisites first.

### A.4 — Implement

Follow conventions from `CLAUDE.md`:
- Package root: `ghart.space.pi_drive` (mobile), `ghart.space.pi_drive.shared` (shared)
- Gradle project root: `pi-drive-android/`
- KDoc every public class and interface
- Named parameters, sealed classes, exhaustive `when`
- Log tags: `PiDrive`, `OBDTransport`, `VehicleData`, `TripAccumulator`, `AccelDetector`, `GForceDetector`, `TelemetryUploader`

### A.5 — Write tests

Test locations:
- `shared/src/test/java/ghart/space/pi_drive/shared/` — JVM unit tests
- `mobile/src/test/java/ghart/space/pi_drive/` — JVM unit tests
- `mobile/src/androidTest/` — instrumented tests (need device)

### A.6 — Build and test

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android
./gradlew :shared:test :mobile:test 2>&1 | tail -30
./gradlew :mobile:assembleDebug 2>&1 | tail -20
```

Fix any failures before continuing.

### A.7 — Verify on device (UI steps)

For steps with UI output, use `/pd-verify` or manually install and screenshot:

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android
./gradlew :mobile:installDebug
$ADB shell am start -n ghart.space.pi_drive/.MainActivity --ez demo_mode true --es demo_scenario "CRUISE"
sleep 4
$ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-verify.png
```

Read the screenshot. Check logcat for the expected log tags.

### A.8 — Update Android progress

Edit `pi-drive-android/implementation/PROGRESS.md`:
- Step status → `DONE`
- PR/Commit column
- Advance "Active step"
- Update "Current State"
- Add to Completed summary

---

## Route B — Server (Python / Flask / Postgres+Timescale)

### B.2 — Load phase plan

Read `pi-drive-server/implementation/phase-NN-*.md` for the current step only.

### B.3 — Check prerequisites

Verify prerequisite phases are DONE in `pi-drive-server/implementation/PROGRESS.md`.

### B.4 — Implement

Follow conventions from `pi-drive-server/REQUIREMENTS.md` §11:
- **Strict layering:** `api/` and `web/` call `services/`; only `services/` touch the ORM. Never put DB access in route handlers.
- Docstring every module, public function, and service method (what + why).
- Pydantic schemas decouple wire format from ORM models — define schemas in `app/schemas/`.
- Typed SQLAlchemy `Mapped[]` columns; never use `var: Column(...)` untyped.
- Enums (`StrEnum`) for string-valued constants (`Source`, `Strategy`).
- No string interpolation into SQL or shell commands (parameterized queries / subprocess list args).
- Log tags in every module: `PiDriveServer`, `Ingest`, `TelemetryService`, `VehicleService`, `CsvService`, `BackupService`, `Auth`.
- Fail loud: validate env and inputs; missing required config → refuse to boot.

### B.5 — Write tests

Test locations:
- `pi-drive-server/tests/` — pytest
- Unit tests (no DB): `test_*.py` with no `@pytest.mark.integration`
- Integration tests (real Timescale): mark with `@pytest.mark.integration`

Every ingest-contract test must assert the exact Android wire format per `pi-drive-server/REQUIREMENTS.md` §5.

### B.6 — Run tests

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server

# Unit tests (no DB required)
pytest tests/ -m "not integration" -q

# Integration tests (requires docker compose up)
pytest tests/ -m integration -q
```

Fix any failures before continuing.

### B.7 — Verify server is running (API/UI steps)

For steps with HTTP endpoints, run the dev server and verify with curl:

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-server
# ensure the compose DB is up:
cd deploy && docker compose up -d timescaledb && cd ..
# run dev server:
flask --app wsgi run --port 8080 &
sleep 2
curl -s localhost:8080/health
```

For UI steps, open `http://localhost:8080` and capture a screenshot with `/pd-screenshot` or the `mcp__computer-use__screenshot` tool.

Stop the dev server after verifying:
```bash
kill %1 2>/dev/null || pkill -f "flask.*wsgi" 2>/dev/null
```

### B.8 — Update server progress

Edit `pi-drive-server/implementation/PROGRESS.md`:
- Step status → `DONE`
- PR/Commit column
- Advance "Active step"
- Update "Current State"
- Add to Completed summary

---

## Step 9 — Report (both projects)

Tell the user:
- Project (Android / Server) and step implemented
- Files created/modified
- Test results (pass count)
- Verification result (screenshot / curl output)
- What the next step is
