# Pi Drive -- Master Implementation Plan

13 phases, 42 steps. Each step is one PR (~1k-5k lines, 30-60 min).
See `PROGRESS.md` for current state. Load individual `phase-NN-*.md` for step details.

## Dependency Graph

```
Phase 0: Bootstrap ──┐
                     v
Phase 1: Data Layer ──┬──> Phase 2: OBD Protocol
                      │         │
                      v         v
               Phase 3: Phone Dashboard
                      │
          ┌───────────┼───────────┐
          v           v           v
Phase 4: BT    Phase 5: Detection  Phase 6: Trips
          │           │           │
          v           v           v
     Phase 7: Server Telemetry
          │
          v
     Phase 8: Settings Screens
          │
          v
     Phase 9: Android Auto
          │
          v
     Phase 10: Polish + Integration
          │
          v
     Phase 11: Release Readiness
          │
          v
     Phase 12: Distribution & Monitoring
```

## Phase Overview

| Phase | File | Steps | What it delivers |
|---|---|---|---|
| **0** | `phase-00-bootstrap.md` | 0.1 - 0.3 | Kotlin + Compose + Hilt + Room + theme + nav shell |
| **1** | `phase-01-data-layer.md` | 1.1 - 1.3 | Data models, transport abstraction, demo mode, Room DB |
| **2** | `phase-02-obd-protocol.md` | 2.1 - 2.3 | Hex parsing, PID formulas, polling loop |
| **3** | `phase-03-phone-dashboard.md` | 3.1 - 3.3 | Featured metric, MPG row, tile grid, connection banner |
| **4** | `phase-04-bluetooth.md` | 4.1 - 4.3 | BT scan/pair, RFCOMM transport, auto-reconnect |
| **5** | `phase-05-detection.md` | 5.1 - 5.3 | Acceleration + G-Force detection, health alerts |
| **6** | `phase-06-trips.md` | 6.1 - 6.3 | Trip accumulator, manual trip, auto-trip detection |
| **7** | `phase-07-telemetry.md` | 7.1 - 7.3 | Server upload, offline buffer, server settings UI |
| **8** | `phase-08-settings.md` | 8.1 - 8.4 | All settings screens + trip history |
| **9** | `phase-09-android-auto.md` | 9.1 - 9.4 | AA dials, graphs, split-screen, CarToast alerts |
| **10** | `phase-10-polish.md` | 10.1 - 10.4 | TCP transport, E2E integration, CI pipeline |
| **11** | `phase-11-release-readiness.md` | 11.1 - 11.3 | App icon + splash, permissions onboarding, release build config |
| **12** | `phase-12-distribution.md` | 12.1 - 12.3 | Play Store listing, Crashlytics, beta testing |

## Conventions

- **Package root:** `ghart.space.pi_drive`
- **Shared module:** `ghart.space.pi_drive.shared` -- OBD logic, Car App Library, data models
- **Mobile module:** `ghart.space.pi_drive` -- phone UI, settings, MainActivity
- **Test tags:** `PiDrive`, `OBDTransport`, `VehicleData`, `TripAccumulator`, `AccelDetector`, `GForceDetector`, `TelemetryUploader`
- **Verify every step:** Run `/pd-test` then `/pd-verify` (or the subset specified in the step)

## How to Use This Plan

**AI agent:** Read `PROGRESS.md` -> load the phase file for the current step -> implement -> test -> update `PROGRESS.md`.

**Human developer:** Use `PROGRESS.md` as your roadmap. Each phase file has enough detail to implement without reading the full REQUIREMENTS.md. Check off steps as you go.
