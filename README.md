# Pi Drive

Pi Drive is a personal OBD-II dashboard for Android and Android Auto. It connects to an OBDLink LX Bluetooth adapter, reads live vehicle data from the car's ECU, displays it on your phone and head unit, detects hard acceleration and braking events, and streams everything to a self-hosted backend for long-term storage and analysis.

I built this for two reasons, I wanted something to show me fast acceleration and braking events and I wanted to view my cars history in Grafana. I looked at some other open source solutions but nothing quite fit what I was looking for. My car doesn't give me a lot of info the instrument cluster, so doing this will allow me to see things like battery voltage and coolant temp over time. I can also start to see some intersting stats like avg speed and heat maps of my driving patterns.

---

## What it does

**On the phone and head unit**

The app pairs with the OBDLink LX over Bluetooth Classic, opens an RFCOMM connection, and starts polling the ECU for speed, RPM, coolant temperature, oil temperature, intake air temperature, throttle position, fuel level, and MAF airflow. It calculates fuel economy from MAF when a direct fuel rate PID isn't available.

All of that shows up on your phone *and* on Android Auto — in half-screen / split-screen mode so Google Maps can stay up at the same time. When you hit the brakes hard or punch the throttle, the app notices and fires an alert. This feature is to check a saving driving dongle from progessive that I have (and regret getting...) The progressive dongle doesn't notify you when you accelerate "too fast", it just dings your driving score. Now, I can see when I am close to its acceleration limit in real time. "Too fast" is in quotes because I drive a honda fit with barely 100HP, I couldn't accelerate fast if I wanted to.

**Hard event detection**

There are two independent detection strategies running in parallel. One measures rate-of-speed-change in mph/s — simple, intuitive, no calibration needed. The other does proper g-force estimation using sensor fusion across OBD speed, GPS speed, and the phone accelerometer, cross-validating across sources to eliminate false positives from phone drops and GPS jumps in tunnels. You can run both at once. Both log their events independently so you can review them separately.

**Telemetry server**

The app batches telemetry snapshots and POSTs them to a self-hosted Flask server backed by Postgres + TimescaleDB. The server stores everything in a hypertable for fast time-range queries, auto-registers new vehicles on first contact, handles offline batch uploads idempotently, and serves a management REST API plus a server-rendered web UI for browsing your data, editing records, importing/exporting CSV, and doing full database backups. It runs on Docker Compose or Kubernetes.

---

## Repository layout

```
pi-drive-2/
├── pi-drive-android/     ← Android app (Kotlin, Jetpack Compose, Car App Library)
└── pi-drive-server/      ← Flask telemetry server (Python, TimescaleDB)
```

---

## Architecture

```
 OBD-II Bus
     │
     ▼
 OBDLink LX  ──── Bluetooth Classic (SPP/RFCOMM) ────▶  Pi Drive Android App
 (STN1155)                                               │
                                                         ├── Phone screen (Jetpack Compose)
                                                         │
                                                         ├── Android Auto head unit
                                                         │   (Car App Library, GridTemplate,
                                                         │    half-screen alongside Google Maps)
                                                         │
                                                         ├── Sensor fusion
                                                         │   (OBD speed + GPS + accelerometer
                                                         │    → hard accel / hard brake detection)
                                                         │
                                                         └── HTTP POST (JSON, batched, zstd)
                                                                     │
                                                                     ▼
                                                             Pi Drive Server
                                                             (Flask + TimescaleDB)
                                                             ├── POST /telemetry (ingest)
                                                             ├── REST API (/api/v1/...)
                                                             ├── Web UI (Jinja2, no SPA)
                                                             └── docker-compose / k8s
```

**Android app data flow:** A `StateFlow<VehicleSnapshot>` carries data from the OBD polling coroutine to every consumer — the phone UI, the Android Auto screen, the trip accumulator, the event detectors, and the telemetry uploader. No shared mutable state, no callbacks threading through everything.

**Server layering:** Routes call services; only services touch the database. Pydantic schemas decouple the wire format from the ORM so the two can evolve independently. The `(vin, timestamp)` pair is the idempotency key — the app retries offline batches aggressively and the server handles it without duplicating rows.

---

## Android app

**Language / SDK:** Kotlin, min SDK 34 (Android 14), target SDK 36

**Modules:**

| Module | What it contains |
|---|---|
| `:mobile` | Phone UI, main activity, Bluetooth connection management, settings |
| `:shared` | OBD communication layer, Car App Library service/session/screens, data models |
| `:automotive` | Android Automotive OS target (for embedded head units running AAOS natively) |

**Key design decisions:**

- OBD communication goes through an `OBDTransport` interface. The production implementation uses a real Bluetooth socket; tests and emulator runs use a TCP socket pointed at an ELM327 emulator. Business logic never sees `BluetoothSocket` directly.
- The UI binds to a `VehicleDataSource` interface. `DemoVehicleDataSource` makes it possible to demo and develop the full UI without touching a car.
- Test mode is activated by intent extras at launch (`demo_mode`, `tcp_mode`, `tcp_host`, etc.). Production builds ignore those extras entirely.

**Relevant docs:**

- [`pi-drive-android/README.md`](pi-drive-android/README.md) — OBD-II protocol primer, AT/ST command reference, Bluetooth RFCOMM setup, Android Auto integration guide, server payload schema
- [`pi-drive-android/REQUIREMENTS.md`](pi-drive-android/REQUIREMENTS.md) — full product spec: all 16 metrics, 8 phone screens, 3 AA screens, detection strategies, settings
- [`pi-drive-android/TESTING.md`](pi-drive-android/TESTING.md) — how to test without a car: demo mode, ELM327 TCP emulator, DHU for Android Auto, CI
- [`pi-drive-android/ui-handoff/pi-drive/project/`](pi-drive-android/ui-handoff/pi-drive/project/) — design tokens, component specs, screen designs

**Build:**

```bash
cd pi-drive-android
./gradlew :mobile:installDebug
```

See [`pi-drive-android/SIGNING.md`](pi-drive-android/SIGNING.md) for release signing.

---

## Telemetry server

**Stack:** Python 3.12, Flask 3.x, SQLAlchemy 2.x, Pydantic v2, Postgres 16 + TimescaleDB, Gunicorn, Docker / Kubernetes

**Quick start:**

```bash
cd pi-drive-server/deploy
cp .env.example .env          # set API_KEY and SECRET_KEY
docker compose up --build
# UI:    http://localhost:8080
# Ingest: POST http://localhost:8080/telemetry
```

**Tests:**

```bash
cd pi-drive-server
pytest -q        # unit tests always run; integration tests spin up Timescale via testcontainers
```

**Relevant docs:**

- [`pi-drive-server/README.md`](pi-drive-server/README.md) — dev setup, env vars, deploy reference
- [`pi-drive-server/REQUIREMENTS.md`](pi-drive-server/REQUIREMENTS.md) — full spec: data model, wire contract, REST API, web UI, k8s, observability
- [`pi-drive-server/TESTING.md`](pi-drive-server/TESTING.md) — four-layer test strategy (unit → integration → e2e-over-socket → prod-like compose)

**Why TimescaleDB and not InfluxDB?** This data is high-frequency time-series but the management requirements — manual row edit, CSV round-trip, relational queries across vehicles, proper backup/restore — are all awkward in InfluxDB. TimescaleDB is a Postgres extension: one datastore, one backup target, full SQL, but with hypertable partitioning and continuous aggregates for fast year-long scans. One container, not two.

---

## Development without a car

You don't need a car or an OBD adapter to work on most of this.

- **Demo mode** — launch the Android app with `demo_mode=true` and `DemoVehicleDataSource` feeds realistic synthetic data through the whole stack: UI, Android Auto, event detection, telemetry upload.
- **ELM327 TCP emulator** — the [ELM327-emulator](https://github.com/Ircama/ELM327-emulator) project emulates the adapter over TCP. The app's `TcpTransport` connects to it directly. Use this when you need real OBD protocol behavior, not synthetic data.
- **Android Auto DHU** — the Desktop Head Unit emulator lets you see and interact with the Android Auto interface on your development machine. No head unit required.

---

## Observability

The server emits structured JSON logs (one object per line) and ships them to Grafana Loki via Grafana Alloy. Alloy buffers to a WAL when Loki is unavailable, so no logs are lost during outages. Compose and K8s configs for the full stack are in [`pi-drive-server/deploy/observability/`](pi-drive-server/deploy/observability/).

The Android app has a corresponding on-device file logger and a planned Loki push path (Phase 13 of the Android plan) using the same label schema so phone and server logs are searchable together.

---

## Implementation plans

Both the Android app and the server follow a phased implementation plan. If you want to know what's built, what's in progress, and what's coming:

- Android: [`pi-drive-android/implementation/PROGRESS.md`](pi-drive-android/implementation/PROGRESS.md)
- Server: [`pi-drive-server/implementation/PROGRESS.md`](pi-drive-server/implementation/PROGRESS.md)

The master plans are [`pi-drive-android/implementation/IMPLEMENTATION.md`](pi-drive-android/implementation/IMPLEMENTATION.md) and [`pi-drive-server/implementation/IMPLEMENTATION.md`](pi-drive-server/implementation/IMPLEMENTATION.md).

---

## Hardware

- **OBD adapter:** [OBDLink LX](https://www.obdlink.com/products/obdlink-lx/) — Bluetooth Classic, STN1155 chip, ELM327-compatible + ST extended command set. Not a clone. Worth the price.
- **Vehicle compatibility:** any car with an OBD-II port — US vehicles 1996+, EU petrol 2001+, EU diesel 2004+.

---

## License

[MIT](LICENSE) — © 2026 Garrett Hart
