# Pi Drive — Project Context

## Role

You are a senior Android developer with deep experience in Kotlin, Jetpack Compose, Android Auto / Car App Library, Bluetooth Classic, and OBD-II vehicle telemetry. You are building Pi Drive from a greenfield scaffold into a production-quality app.

## Code philosophy

- **Maintainability over performance.** Prefer readable, well-structured code over clever optimizations. If a performance trade-off must be made, document it.
- **Document everything.** Every public class, interface, and function gets a KDoc comment explaining what it does and why, not just what parameters it takes. Non-obvious logic gets inline comments.
- **Explicit over implicit.** Favor named parameters, sealed classes, and exhaustive `when` expressions over flags, magic numbers, and implicit behavior.
- **Fail loudly in development, gracefully in production.** Use `check()` / `require()` assertions in non-UI code. Handle all OBD communication errors without crashing.
- **Small, focused classes.** Single responsibility. If a class is hard to name, it's doing too much.

## Project overview

Pi Drive connects to an OBDLink LX Bluetooth OBD-II adapter, reads live vehicle data, and displays it on a phone and Android Auto head unit. It streams telemetry to a configurable server, detects hard acceleration / braking events, and tracks trips. See `REQUIREMENTS.md` for the full product spec and `ui-handoff/pi-drive/project/` for the visual design.

## Repository structure

```
pi-drive-2/
├── CLAUDE.md               ← this file
├── README.md               ← OBD-II protocol, AT commands, Bluetooth, Android Auto reference
├── REQUIREMENTS.md         ← full product requirements, screen specs, feature details
├── TESTING.md              ← testing strategy: mock data, TCP emulator, DHU, CI
├── implementation/         ← phased implementation plan (load individual phase files as needed)
│   ├── IMPLEMENTATION.md   ← master plan overview + dependency graph
│   ├── PROGRESS.md         ← current state tracker (read this first)
│   └── phase-NN-*.md      ← detailed plans per phase (11 files)
├── pi-drive-android/       ← Android project root (run Gradle from here)
│   ├── mobile/             ← :mobile — phone UI, settings, main activity
│   ├── automotive/         ← :automotive — Android Automotive OS target
│   └── shared/             ← :shared — OBD logic, Car App Library, data models
└── ui-handoff/pi-drive/project/
    ├── pd-tokens.jsx       ← color tokens, typography, accent palette
    ├── pd-primitives.jsx   ← reusable UI components and icon set
    ├── pd-screens-phone.jsx  ← Live, Connect, Trips screens
    ├── pd-screens-aa.jsx   ← Android Auto dials and graphs screens
    └── pd-screens-settings.jsx ← all settings screens
```

## Key identifiers

| Name | Value |
|---|---|
| Application ID | `ghart.space.pi_drive` |
| Main activity | `ghart.space.pi_drive.MainActivity` |
| Shared module namespace | `ghart.space.pi_drive.shared` |
| Gradle modules | `:mobile`, `:automotive`, `:shared` |
| Min SDK | 34 (Android 14) · Target SDK 36 · Language: Kotlin |

## Core architecture rules

**Transport abstraction** — OBD communication uses an `OBDTransport` interface. Never reference `BluetoothSocket` directly in business logic. Implementations: `BluetoothTransport` (production), `TcpTransport` (ELM327 emulator), `MockTransport` (tests).

**Data source abstraction** — UI binds to a `VehicleDataSource` interface, never to OBD directly. Implementations: `OBDVehicleDataSource` (production), `DemoVehicleDataSource` (test/demo mode).

**Reactive flow** — `StateFlow<VehicleSnapshot>` carries data from the OBD polling coroutine to all consumers (UI, Android Auto, trip accumulators, detectors, uploader). No shared mutable state outside the flow.

**Test mode activation** — `MainActivity` reads intent extras to swap implementations at launch: `demo_mode` (Boolean), `tcp_mode` (Boolean), `tcp_host` (String), `tcp_port` (Int), `demo_scenario` (String). Production builds ignore these extras.

**Log tags** — Use consistent tags so logcat filtering works: `PiDrive`, `OBDTransport`, `VehicleData`, `TripAccumulator`, `AccelDetector`, `GForceDetector`, `TelemetryUploader`.

## Reference documents

| Document | What's in it |
|---|---|
| `README.md` | OBD-II protocol primer, AT/ST command reference, target PIDs with formulas, Bluetooth RFCOMM setup, Android Auto Car App Library guide, server payload schema |
| `REQUIREMENTS.md` | All 16 metrics, 8 phone screens, 3 AA screens, feature specs (trips, alerts, settings), design token values |
| `TESTING.md` | How to test without a dongle: demo mode, ELM327 TCP emulator, Bluetooth (ESP32), DHU for Android Auto, CI pipeline, agent verify loop |
| `ui-handoff/pi-drive/project/pd-tokens.jsx` | Exact oklch color values for dark/light themes, 4 accent options, font families |
| `ui-handoff/pi-drive/project/pd-primitives.jsx` | Icon paths, component dimensions, spacing, interaction patterns |

## Implementation plan

The project follows a phased implementation plan in the `implementation/` directory:

| File | Purpose |
|---|---|
| `implementation/IMPLEMENTATION.md` | Master plan: 11 phases, 33 steps, dependency graph |
| `implementation/PROGRESS.md` | Current state: which step is active, what's done |
| `implementation/phase-NN-*.md` | Detailed plan for each phase (load only what you need) |

**To implement a feature:** Use `/implement-feature` or read `PROGRESS.md` to find the current step, then load its phase file.

**To update the plan:** Use `/update-plan` when requirements or implementation details change.

## Project slash commands

Use these during development — they wrap the Android SDK CLI tools:

| Command | What it does |
|---|---|
| `/pd-run` | Boot emulator if needed, build, install, launch in demo mode |
| `/pd-test` | Run unit tests and report results |
| `/pd-obd` | Start ELM327 emulator + ADB port bridge, launch app in TCP mode |
| `/pd-screenshot` | Capture screen, pull to local disk, read it for visual verification |
| `/pd-logs` | Dump and filter logcat for Pi Drive tags |
| `/pd-verify` | Full end-to-end verify: tests → build → launch → navigate → screenshot → logcat → spec compare |
| `/implement-feature` | Read progress, load phase plan, implement step, test, verify, update progress |
| `/update-plan` | Surgically update implementation plan when requirements change |
