# Pi Drive — Project Context

## Role

You are a senior developer with deep experience in both Android (Kotlin, Jetpack Compose, Android Auto / Car App Library, Bluetooth Classic, OBD-II) and Python backend development (Flask, SQLAlchemy, Pydantic, Postgres/TimescaleDB, Docker, Kubernetes). You are building Pi Drive — the Android app and its Flask telemetry server — from a greenfield scaffold into a production-quality system.

## Code philosophy

- **Maintainability over performance.** Prefer readable, well-structured code over clever optimizations. If a performance trade-off must be made, document it.
- **Document everything.** Every public class, interface, and function gets a KDoc comment explaining what it does and why, not just what parameters it takes. Non-obvious logic gets inline comments.
- **Explicit over implicit.** Favor named parameters, sealed classes, and exhaustive `when` expressions over flags, magic numbers, and implicit behavior.
- **Fail loudly in development, gracefully in production.** Use `check()` / `require()` assertions in non-UI code. Handle all OBD communication errors without crashing.
- **Small, focused classes.** Single responsibility. If a class is hard to name, it's doing too much.

## Project overview

Pi Drive connects to an OBDLink LX Bluetooth OBD-II adapter, reads live vehicle data, and displays it on a phone and Android Auto head unit. It streams telemetry to a configurable server, detects hard acceleration / braking events, and tracks trips. See `pi-drive-android/REQUIREMENTS.md` for the full product spec and `pi-drive-android/ui-handoff/pi-drive/project/` for the visual design.

## Repository structure

```
pi-drive-2/
├── CLAUDE.md                   ← this file
├── pi-drive-android/           ← Android project root (run Gradle from here)
│   ├── README.md               ← OBD-II protocol, AT commands, Bluetooth, Android Auto reference
│   ├── REQUIREMENTS.md         ← full product requirements, screen specs, feature details
│   ├── TESTING.md              ← testing strategy: mock data, TCP emulator, DHU, CI
│   ├── SIGNING.md              ← keystore and release signing instructions
│   ├── implementation/         ← phased implementation plan (load individual phase files as needed)
│   │   ├── IMPLEMENTATION.md   ← master plan overview + dependency graph
│   │   ├── PROGRESS.md         ← current state tracker (read this first)
│   │   └── phase-NN-*.md      ← detailed plans per phase (11 files)
│   ├── scripts/                ← e2e-test.sh and other dev scripts
│   ├── screenshots/            ← app screenshots captured during development/verification
│   ├── mobile/                 ← :mobile — phone UI, settings, main activity
│   ├── automotive/             ← :automotive — Android Automotive OS target
│   ├── shared/                 ← :shared — OBD logic, Car App Library, data models
│   └── ui-handoff/pi-drive/project/
│       ├── pd-tokens.jsx       ← color tokens, typography, accent palette
│       ├── pd-primitives.jsx   ← reusable UI components and icon set
│       ├── pd-screens-phone.jsx  ← Live, Connect, Trips screens
│       ├── pd-screens-aa.jsx   ← Android Auto dials and graphs screens
│       └── pd-screens-settings.jsx ← all settings screens
└── pi-drive-server/            ← Flask telemetry server (Python)
    ├── REQUIREMENTS.md         ← full server spec (storage, API contract, UI, k8s)
    ├── SERVER_DEVELOPER_DOCS.md ← lessons from the original Java server
    ├── implementation/         ← phased implementation plan (load individual phase files as needed)
    │   ├── IMPLEMENTATION.md   ← master plan overview + dependency graph
    │   ├── PROGRESS.md         ← current state tracker (read this first)
    │   └── phase-NN-*.md      ← detailed plans per phase (8 files)
    ├── app/                    ← Flask application (created in Phase 0)
    ├── migrations/             ← Alembic migrations
    ├── tests/                  ← pytest unit + integration tests
    └── deploy/                 ← Dockerfile, docker-compose.yml, k8s/
```

## Key identifiers

### Android app

| Name | Value |
|---|---|
| Application ID | `ghart.space.pi_drive` |
| Main activity | `ghart.space.pi_drive.MainActivity` |
| Shared module namespace | `ghart.space.pi_drive.shared` |
| Gradle modules | `:mobile`, `:automotive`, `:shared` |
| Min SDK | 34 (Android 14) · Target SDK 36 · Language: Kotlin |

### Flask server

| Name | Value |
|---|---|
| Language | Python 3.12 |
| Framework | Flask 3.x, SQLAlchemy 2.x, Alembic, Pydantic v2 |
| Database | Postgres 16 + TimescaleDB |
| Dev server | `flask --app wsgi run --port 8080` |
| Prod server | Gunicorn via `wsgi.py` |
| Test runner | pytest (`tests/`) |
| Ingest endpoint | `POST /telemetry` (matches Android `TelemetryUploader`) |
| Idempotency key | `(vin, time)` |
| Auth | `Authorization: Bearer <API_KEY>` or `X-API-Key` |

## Core architecture rules

**Transport abstraction** — OBD communication uses an `OBDTransport` interface. Never reference `BluetoothSocket` directly in business logic. Implementations: `BluetoothTransport` (production), `TcpTransport` (ELM327 emulator), `MockTransport` (tests).

**Data source abstraction** — UI binds to a `VehicleDataSource` interface, never to OBD directly. Implementations: `OBDVehicleDataSource` (production), `DemoVehicleDataSource` (test/demo mode).

**Reactive flow** — `StateFlow<VehicleSnapshot>` carries data from the OBD polling coroutine to all consumers (UI, Android Auto, trip accumulators, detectors, uploader). No shared mutable state outside the flow.

**Test mode activation** — `MainActivity` reads intent extras to swap implementations at launch: `demo_mode` (Boolean), `tcp_mode` (Boolean), `tcp_host` (String), `tcp_port` (Int), `demo_scenario` (String). Production builds ignore these extras.

**Log tags** — Use consistent tags so logcat filtering works: `PiDrive`, `OBDTransport`, `VehicleData`, `TripAccumulator`, `AccelDetector`, `GForceDetector`, `TelemetryUploader`.

## Server architecture rules

**Strict layering** — `api/` and `web/` blueprints call `services/`; only `services/` touch SQLAlchemy. Never put DB queries in route handlers.

**Wire contract is fixed** — `POST /telemetry`, `GET /telemetry/latest?vin=`, `GET /health` must match what the Android app sends exactly. Check `pi-drive-android/implementation/phase-07-telemetry.md` before changing any ingest path, header name, or payload field.

**Pydantic decouples wire from storage** — Define all request/response shapes in `app/schemas/`. ORM models live in `app/db/models.py`. Never expose ORM objects directly in responses.

**Idempotent ingest** — `POST /telemetry` upserts by `(vin, time)`. Re-uploading the same snapshot must never duplicate rows.

**Server log tags** — `PiDriveServer`, `Ingest`, `TelemetryService`, `VehicleService`, `CsvService`, `BackupService`, `Auth`.

**Fail loud at startup** — Missing `DATABASE_URL`, `API_KEY`/`API_KEY_FILE`, or `SECRET_KEY` → refuse to start with a clear message.

## Reference documents

### Android app

| Document | What's in it |
|---|---|
| `pi-drive-android/README.md` | OBD-II protocol primer, AT/ST command reference, target PIDs with formulas, Bluetooth RFCOMM setup, Android Auto Car App Library guide, server payload schema |
| `pi-drive-android/REQUIREMENTS.md` | All 16 metrics, 8 phone screens, 3 AA screens, feature specs (trips, alerts, settings), design token values |
| `pi-drive-android/TESTING.md` | How to test without a dongle: demo mode, ELM327 TCP emulator, Bluetooth (ESP32), DHU for Android Auto, CI pipeline, agent verify loop |
| `pi-drive-android/ui-handoff/pi-drive/project/pd-tokens.jsx` | Exact oklch color values for dark/light themes, 4 accent options, font families |
| `pi-drive-android/ui-handoff/pi-drive/project/pd-primitives.jsx` | Icon paths, component dimensions, spacing, interaction patterns |

### Flask server

| Document | What's in it |
|---|---|
| `pi-drive-server/REQUIREMENTS.md` | Full server spec: storage, data model, Android wire contract, REST API, CSV, backup, UI, k8s |
| `pi-drive-server/TESTING.md` | Testing strategy: 4-layer pyramid (unit / integration / e2e-over-socket / prod-like compose), testcontainers Timescale, `tests/fixtures/` mock payloads, per-phase test matrix, contract tests |
| `pi-drive-server/SERVER_DEVELOPER_DOCS.md` | Lessons from the Java server rewrite — what was broken and why |
| `pi-drive-android/implementation/phase-07-telemetry.md` | **The Android client contract** — exact endpoints, headers, payload the app sends |

## Implementation plans

### Android app (`pi-drive-android/implementation/`)

| File | Purpose |
|---|---|
| `IMPLEMENTATION.md` | Master plan: 13 phases, 42 steps, dependency graph |
| `PROGRESS.md` | Current state: which step is active, what's done |
| `phase-NN-*.md` | Detailed plan for each phase (load only what you need) |

### Flask server (`pi-drive-server/implementation/`)

| File | Purpose |
|---|---|
| `IMPLEMENTATION.md` | Master plan: 8 phases, 24 steps, dependency graph |
| `PROGRESS.md` | Current state: which step is active, what's done (start here: Phase 0, Step 0.1) |
| `phase-NN-*.md` | Detailed plan for each phase (load only what you need) |

**To implement a feature (either project):** Use `/implement-feature` — it routes to the right project automatically.

**To update a plan (either project):** Use `/update-plan` — it routes by context.

## Project slash commands

### Android app commands (wrap Android SDK / ADB / Gradle)

| Command | What it does |
|---|---|
| `/pd-run` | Boot emulator if needed, build, install, launch in demo mode |
| `/pd-test` | Run Gradle unit tests and report results |
| `/pd-obd` | Start ELM327 emulator + ADB port bridge, launch app in TCP mode |
| `/pd-screenshot` | Capture screen, pull to local disk, read it for visual verification |
| `/pd-logs` | Dump and filter logcat for Pi Drive tags |
| `/pd-verify` | Full end-to-end verify: tests → build → launch → navigate → screenshot → logcat → spec compare |

### Server commands (wrap Flask / pytest / Docker)

| Command | What it does |
|---|---|
| `/srv-run` | Start TimescaleDB container + Flask dev server on `:8080`, confirm `/health` 200 |
| `/srv-test` | Run pytest (unit + integration against Timescale), report pass/fail |
| `/srv-logs` | Dump and filter Flask server logs by tag or keyword |
| `/srv-verify` | Full end-to-end verify: pytest → server up → curl endpoint → log check |

### Cross-project commands

| Command | What it does |
|---|---|
| `/implement-feature` | Routes to Android or server — reads PROGRESS.md, loads phase file, implements step, tests, updates progress |
| `/update-plan` | Routes to Android or server — surgically edits only affected plan files |
