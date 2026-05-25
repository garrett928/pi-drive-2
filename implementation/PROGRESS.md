# Pi Drive -- Implementation Progress

Last updated: 2026-05-25

## Current State

**Active phase:** Phase 0 -- Bootstrap
**Active step:** 0.1 -- Build system + Kotlin + Compose
**Project state:** Bare Android Studio scaffold. No Compose, no Hilt, no Room, no Kotlin plugin in shared module.

## Completed

(none yet)

## Step Status

| Step | Title | Status | PR/Commit |
|---|---|---|---|
| **Phase 0: Bootstrap** |
| 0.1 | Build system + Kotlin + Compose + Hilt + Room | NOT STARTED | |
| 0.2 | Design system + theme tokens | NOT STARTED | |
| 0.3 | Navigation + app shell | NOT STARTED | |
| **Phase 1: Data Layer** |
| 1.1 | Data models + interfaces | NOT STARTED | |
| 1.2 | MockTransport + DemoVehicleDataSource | NOT STARTED | |
| 1.3 | Room database schema | NOT STARTED | |
| **Phase 2: OBD Protocol** |
| 2.1 | Command formatting + response parsing | NOT STARTED | |
| 2.2 | PID support bitmask + VIN decoder | NOT STARTED | |
| 2.3 | OBD polling loop + OBDVehicleDataSource | NOT STARTED | |
| **Phase 3: Phone Dashboard** |
| 3.1 | Featured metric + sparkline | NOT STARTED | |
| 3.2 | MPG row + tile grid | NOT STARTED | |
| 3.3 | Connection banner + status bar | NOT STARTED | |
| **Phase 4: Bluetooth** |
| 4.1 | BluetoothTransport | NOT STARTED | |
| 4.2 | Connect screen UI (3-step flow) | NOT STARTED | |
| 4.3 | Auto-reconnect + connection manager | NOT STARTED | |
| **Phase 5: Detection** |
| 5.1 | Acceleration detector (mph/s) | NOT STARTED | |
| 5.2 | G-Force detector (sensor fusion) | NOT STARTED | |
| 5.3 | Alert system + health alerts | NOT STARTED | |
| **Phase 6: Trips** |
| 6.1 | Trip accumulator | NOT STARTED | |
| 6.2 | Manual trip manager | NOT STARTED | |
| 6.3 | Auto-detected trips | NOT STARTED | |
| **Phase 7: Telemetry** |
| 7.1 | Telemetry payload + HTTP uploader | NOT STARTED | |
| 7.2 | Offline buffer + WorkManager | NOT STARTED | |
| 7.3 | Server settings screen | NOT STARTED | |
| **Phase 8: Settings** |
| 8.1 | Settings root + general settings | NOT STARTED | |
| 8.2 | Phone home layout editor | NOT STARTED | |
| 8.3 | Thresholds screen | NOT STARTED | |
| 8.4 | Trip history screen + CSV export | NOT STARTED | |
| **Phase 9: Android Auto** |
| 9.1 | AA Screen 1 -- Dials | NOT STARTED | |
| 9.2 | AA Screen 2 -- Graphs | NOT STARTED | |
| 9.3 | AA split-screen panel | NOT STARTED | |
| 9.4 | AA alerts + CarToast | NOT STARTED | |
| **Phase 10: Polish** |
| 10.1 | Android Auto layout settings | NOT STARTED | |
| 10.2 | TcpTransport + ELM327 emulator | NOT STARTED | |
| 10.3 | End-to-end integration testing | NOT STARTED | |
| 10.4 | CI pipeline (GitHub Actions) | NOT STARTED | |

## Notes

- The scaffold currently uses View-based layout (activity_main.xml). Step 0.1 replaces this with Compose.
- shared/build.gradle is Groovy (.gradle not .kts). Step 0.1 may convert it to .kts for consistency.
- No Kotlin plugin is applied to the shared module. Step 0.1 fixes this.
