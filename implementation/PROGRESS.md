# Pi Drive -- Implementation Progress

Last updated: 2026-05-26

## Current State

**Active phase:** Phase 2 -- OBD Protocol
**Active step:** 2.2 -- PID support bitmask + VIN decoder
**Project state:** OBD command/response layer complete (Step 2.1 done). OBDCommand sealed class (ATZ, ATE0, ATL0, ATS0, ATH0, ATSP, ATRV, PidRequest), OBDResponse sealed class (Success with ByteArray equality, NoData, Error, ATResponse), ResponseParser (handles spaced/unspaced hex, SEARCHING prefix, echo residue, multi-ECU), PidDecoder (10 decode functions), FuelEconomy (MAF-based and fuel-rate-based MPG). 131 unit tests green. Next: PID support bitmask, VIN decoder, InitializationSequence.

## Completed

| Step | Title | Summary |
|---|---|---|
| 0.1 | Build system + Kotlin + Compose + Hilt + Room | libs.versions.toml (AGP 9.1.1, Kotlin 2.2.21, KSP 2.3.8), all modules converted to Kotlin DSL; no kotlin.android plugin (AGP 9 builtInKotlin); JDK 25 required for jlink |
| 0.2 | Design system + theme tokens | PiDriveTheme with Material3 dark/light, token colors from pd-tokens.jsx |
| 0.3 | Navigation + app shell | PiDriveScaffold, NavHost with Live/Connect/Trips placeholder screens |
| 1.1 | Data models + interfaces | VehicleSnapshot, MetricId, MetricValue, ConnectionState, DrivingEvent, OBDTransport, VehicleDataSource, all unit-tested |
| 1.2 | MockTransport + DemoVehicleDataSource | MockTransport with canned AT/PID responses; DemoVehicleDataSource with 8 scenarios (CRUISE, CITY, HIGHWAY, HARD_BRAKE, COLD_START, LOW_FUEL, OVERSPEED, DISCONNECT); Hilt DataModule + AppConfig; 30 unit tests green; device: "Demo mode active, scenario: CRUISE" logged |
| 1.3 | Room database schema | 5 entities (snapshots, driving_events, auto_trips, manual_trips, pending_uploads), 5 DAOs, Converters (Instant/enum/Set<DataSource>), PiDriveDatabase v1, Hilt DatabaseModule; Robolectric added for JVM Room tests; 9 new tests, 79 total green |

## Step Status

| Step | Title | Status | PR/Commit |
|---|---|---|---|
| **Phase 0: Bootstrap** |
| 0.1 | Build system + Kotlin + Compose + Hilt + Room | DONE | |
| 0.2 | Design system + theme tokens | DONE | |
| 0.3 | Navigation + app shell | DONE | |
| **Phase 1: Data Layer** |
| 1.1 | Data models + interfaces | DONE | |
| 1.2 | MockTransport + DemoVehicleDataSource | DONE | |
| 1.3 | Room database schema | DONE | |
| 2.1 | Command formatting + response parsing | DONE | |
| **Phase 2: OBD Protocol** |
| 2.1 | Command formatting + response parsing | DONE | |
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

### AGP 9.0+ / Kotlin 2.2.x build system
- Do NOT apply `org.jetbrains.kotlin.android` plugin to `:mobile` or `:automotive`; AGP 9.0+ handles Kotlin via `builtInKotlin=true` by default
- `kotlinOptions {}` block requires the kotlin.android plugin; use `compileOptions { sourceCompatibility/targetCompatibility }` instead
- KSP uses new versioning scheme starting ~2.3.x (no longer embeds Kotlin version string). Use `ksp = "2.3.8"`
- Android 36.1 compileSdk requires `jlink`, which only exists in full JDK installs. Set `org.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home` in gradle.properties
- Run `./gradlew --stop` after changing gradle.properties to kill cached daemons
