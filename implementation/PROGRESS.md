# Pi Drive -- Implementation Progress

Last updated: 2026-05-31

## Current State

**Active phase:** Phase 10 -- Polish
**Active step:** 10.4 -- CI pipeline (GitHub Actions)
**Project state:** Step 10.3 complete. scripts/e2e-test.sh (6-scenario runner with build/unit-test/install/screenshot/logcat verification), FullPipelineTest (4 JVM tests: init sequence → data source → snapshots + AccelerationDetector event detection), DashboardE2ETest / NavigationE2ETest / ThresholdChangeE2ETest (instrumented Compose UI tests with SharedPrefs demo-mode setup). compose-ui-test-junit4 added. 517 tests green (4 new). Next: CI pipeline.

## Completed

| Step | Title | Summary |
|---|---|---|
| 0.1 | Build system + Kotlin + Compose + Hilt + Room | libs.versions.toml (AGP 9.1.1, Kotlin 2.2.21, KSP 2.3.8), all modules converted to Kotlin DSL; no kotlin.android plugin (AGP 9 builtInKotlin); JDK 25 required for jlink |
| 0.2 | Design system + theme tokens | PiDriveTheme with Material3 dark/light, token colors from pd-tokens.jsx |
| 0.3 | Navigation + app shell | PiDriveScaffold, NavHost with Live/Connect/Trips placeholder screens |
| 1.1 | Data models + interfaces | VehicleSnapshot, MetricId, MetricValue, ConnectionState, DrivingEvent, OBDTransport, VehicleDataSource, all unit-tested |
| 1.2 | MockTransport + DemoVehicleDataSource | MockTransport with canned AT/PID responses; DemoVehicleDataSource with 8 scenarios (CRUISE, CITY, HIGHWAY, HARD_BRAKE, COLD_START, LOW_FUEL, OVERSPEED, DISCONNECT); Hilt DataModule + AppConfig; 30 unit tests green; device: "Demo mode active, scenario: CRUISE" logged |
| 1.3 | Room database schema | 5 entities (snapshots, driving_events, auto_trips, manual_trips, pending_uploads), 5 DAOs, Converters (Instant/enum/Set<DataSource>), PiDriveDatabase v1, Hilt DatabaseModule; Robolectric added for JVM Room tests; 9 new tests, 79 total green |
| 2.1 | Command formatting + response parsing | OBDCommand sealed class, OBDResponse (ByteArray equality), ResponseParser (spaced/unspaced/multi-ECU/echo), PidDecoder (10 PIDs), FuelEconomy (MAF + fuel rate MPG); 131 tests green |
| 2.2 | PID support bitmask + VIN decoder | PidSupport (4-range decode + chaining), VinDecoder (multi-frame hex, year/WMI tables, VehicleInfo), InitializationSequence (Flow<InitStep>); 179 tests green |
| 2.3 | OBD polling loop + OBDVehicleDataSource | OBDPollScheduler (priority round-robin), OBDVehicleDataSource (polling loop, ATRV battery, Hz tracking), MockTransport yield() fix, DataModule wired; 210 tests green |
| 3.1 | Featured metric + sparkline | LiveDashboardViewModel (featuredValue/sparklineData/isLive, 30s rolling buffer), FeaturedMetric (76sp hero), SparklineGraph (Canvas, gradient fill, glow dot), LIVE pill, innerPadding fix; 219 tests green |
| 3.2 | MPG row + tile grid | MpgRow (instant/trip/manual, Reset pill), DialWidget (270° arc), BarWidget (directional warning), NumberWidget, XYWidget (crosshair), MetricTile/TileGrid (2-col 6-tile grid), scrollable layout, ViewModel.currentSnapshot; 223 tests green |
| 3.3 | Connection banner + status bar | ConnectionBanner (Connected/Disconnected/Connecting/Error variants, spinning BT icon animation), StatusBanner (LIVE/RECORDING/CONNECTING/ERROR/IDLE), connectionState StateFlow in ViewModel, banners wired in LiveDashboardScreen; 223 tests green |
| 4.1 | BluetoothTransport | ResponseFramer (read-until-prompt, SocketTimeoutException → OBDTimeoutException), BluetoothTransport (RFCOMM SPP, runInterruptible timeout), TcpTransport (soTimeout, ELM327 emulator), OBDTimeoutException domain type; 231 tests green |
| 4.2 | Connect Screen UI | ConnectScanScreen + ConnectPairScreen + ConnectDoneScreen; ConnectCoordinator (pure Kotlin, 6-step state machine); ConnectViewModel (HiltVM, scoped to nested CONNECT_GRAPH for shared state); 5 coordinator tests; screenshots in screenshots/ |
| 4.3 | Auto-reconnect + connection manager | ConnectionManager (5-min retry window, injectable clock, backgroundScope-safe); AdapterWatcher (BroadcastReceiver ACL_DISCONNECTED); ConnectionState.Disconnected(canRetry, retryIn); ConnectionBanner reconnecting row with countdown; 236 tests green |
| 5.1 | Acceleration detector (mph/s) | DetectionConfig; AccelerationDetector (OBD-primary/GPS-fallback, IDLE→DETECTING→COOLDOWN state machine, peak-rate tracking); AccelerationDetectorTest (9 tests); injected in MainActivity after AppConfig set; 251 tests green; verified: "Hard brake detected, rate=24.5 mph/s" in HARD_BRAKE demo |
| 5.2 | G-Force detector (sensor fusion) | GForceDetector (≥2/3 source cross-validation: OBD+GPS+accel), AccelerometerManager (TYPE_LINEAR_ACCELERATION, low-pass filter α=0.8, SharedPrefs calibration), CalibrationManager (axis/variance selection); GForceDetectorTest (7 tests), AccelerometerManagerTest (10 tests); DemoVehicleDataSource updated to emit gpsSpeedMps; 268 tests green; verified: "Hard brake detected via G-force, peak=1.13g [SEVERE], sources=[OBD, GPS]" |
| 5.3 | Alert system + health alerts | AlertManager (accepts Flow<DrivingEvent>+Flow<HealthAlert>, DB logging, per-type cooldown, isSevere), HealthMonitor (5 types, PID auto-disable, per-type cooldown), AlertAction sealed class, AlertOverlay+AlertBanner UI (slideInVertically, 3s auto-dismiss, haptic), LiveDashboardViewModel.currentAlert; AlertManagerTest (6 tests), HealthMonitorTest (8 tests); 284 tests green |
| 6.1 | Trip accumulator | TripAccumulator (speed→distance/duration integration, pause/resume, maxSpeed, avgSpeed), FuelTracker (fuel rate + MAF paths, currentMpg, tripAverageMpg), TripSummary; TripAccumulatorTest (13 tests), FuelTrackerTest (13 tests); 312 tests green |
| 6.2 | Manual trip manager | ManualTripManager (Room persistence, pause/resume on connect/disconnect, 10s periodic save, DB restore on restart), ManualTripState, wired into LiveDashboardViewModel + MPG row reset button; ManualTripManagerTest (6 tests, UnconfinedTestDispatcher); 318 tests green |
| 6.3 | Auto-detected trips | AutoTripDetector (connection-event trip boundaries, 5-min end timeout, TripAccumulator integration, Room persistence), AutoTripManager (thin coordinator + currentTrip/tripHistory flows), AutoTripState; AutoTripDetectorTest (5 tests); wired into LiveDashboardViewModel; 323 tests green |
| 7.1 | Telemetry payload + HTTP uploader | TelemetryPayload (@Serializable), TelemetryConfig + VinSource, TelemetryConfigRepository (SharedPreferences), PayloadBuilder (signal selection + VIN guard), TelemetryUploader (OkHttp, HTTPS-only), TelemetryUploadController (testable loop), TelemetryService (foreground service); Hilt added to :shared; 31 new tests, 354 total green |
| 7.2 | Offline buffer + WorkManager | OfflineBuffer (DAO wrapper, exponential back-off 30 s→24 h, max 10 retries), UploadWorker (custom Factory + processBatch), UploadScheduler (15-min periodic + one-shot); PiDriveApplication.Configuration.Provider + DelegatingWorkerFactory; WorkManagerInitializer removed; 15 new tests, 369 total green |
| 7.3 | Server settings screen | TelemetryServerViewModel (VinState, HealthState, saveVin/saveConfig/testConnection/fetchLastSyncTime), SettingsServerScreen (VIN warning banner, vehicle/endpoint/health/streaming/sample-rate/signal-selection sections), SettingsScreen stub updated; 14 new tests, 383 total green |
| 8.1 | Settings root + general settings | GeneralSettings + GeneralSettingsManager (SharedPrefs, StateFlow), AutoTripDao.deleteOlderThan, SettingsViewModel, SettingsRootScreen (vehicle card, Appearance/Data&Display/Cloud&Server/DrivingAlerts/App sections), theme+accent wired reactively in MainActivity, data retention job; 7 new tests, 390 total green |
| 8.2 | Phone home layout editor | DashboardLayout/DashboardLayoutManager (SharedPrefs JSON, StateFlow), SettingsHomeLayoutScreen (featured metric chips + tile grid editor with add/remove/reorder/change-widget-type), WidgetType moved to shared, LiveDashboardViewModel migrated to DashboardLayoutManager; 8 new tests, 398 total green |
| 8.3 | Thresholds screen | ThresholdsManager (SharedPrefs JSON, DetectionConfig+HealthMonitorConfig StateFlows), ThresholdsViewModel (event count badges, accel calibration), SettingsThresholdsScreen (Acceleration/G-Force/Speed&RPM/WhenTriggered/VehicleHealth sections), all detectors wired to ThresholdsManager via Hilt; detectors/tests updated to StateFlow<Config>; 10 new tests, 408 total green |
| 8.4 | Trip history screen + CSV export | TripHistoryScreen (weekly summary card, day-grouped sticky LazyColumn, LIVE/QUEUED/SYNCED pills, empty state), TripHistoryViewModel (7-day summary, groupByDay reactive), TripDetailScreen (stats, event list, Export CSV button), TripDetailViewModel (SavedStateHandle tripId; fixed LongType navArg crash), CsvExporter (pure toCsv() + FileProvider share intent), FileProvider + file_paths.xml; test date-grouping fixed to use Int daysAgo; 17 new tests, 425 total green |
| 9.1 | AA dials screen + Car App service | AADataBridge (singleton bridging Hilt→CarApp, bind() from MainActivity), DialsScreen (GridTemplate 6-item: speed/RPM/coolant dials + trip/MPG/battery stats, danger ⚠ prefix), PiDriveCarAppService/PiDriveCarAppSession (renamed from My*), buildDialsTemplateData pure function; 16 new tests, 441 total green |
| 9.2 | AA graphs screen | GraphsScreen (ListTemplate: throttle+g-force trend labels + instant MPG + manual trip stat), AAScreenManager (root screen factory, navigation graph documented), buildGraphsTemplateData pure function; 15 new tests, 456 total green |
| 9.3 | AA split-screen panel | SplitPanelScreen (GridTemplate, page 1: instant MPG hero + 4 pills, page 2: 6 metric tiles), SplitPageManager (togglePage/showHero/showTiles, in-place invalidate); 15 new tests, 471 total green |
| 9.4 | AA alerts + CarToast | AAAlertHandler (CarToast on DrivingEvent/HealthAlert, 10s per-type cooldown, aaToastEnabled gate), pure functions: buildAAToastMessage/shouldShowAAToast/alertKeyForAA, refactored for testability; 12 new tests, 483 total green |
| 10.1 | Android Auto layout settings | AALayoutConfig (AAWidgetType, AASlotConfig, 4 default slot lists), AALayoutManager (SharedPrefs JSON, StateFlow, per-screen update/reset), AAMetricFormatter (formatSlotValueForAA/isDangerConditionForAA for all 16 MetricIds), AADataBridge extended with aaLayout StateFlow, DialsScreen/GraphsScreen/SplitPanelScreen use configurable slots, AALayoutViewModel (HiltVM), SettingsAALayoutScreen (3 tabs, previews, slot editor bottom sheet, reorder); 15 new tests, 498 total green |
| 10.2 | TcpTransport + ELM327 emulator | DevSettingsManager (SharedPrefs, StateFlow, unlock/reset/isAnyModeActive), DevSettingsViewModel (HiltVM), SettingsDevScreen (demo mode + TCP mode host/port, restart banner, status card), 7-tap version unlock in SettingsScreen, MainActivity.applyDevSettingsToAppConfig() overrides AppConfig before Hilt injection; 15 new tests, 513 total green |
| 10.3 | End-to-end integration testing | scripts/e2e-test.sh (6-scenario runner), FullPipelineTest (4 JVM tests: init→dataSource→AccelerationDetector pipeline), DashboardE2ETest + NavigationE2ETest + ThresholdChangeE2ETest (Compose UI instrumented tests), compose-ui-test-junit4 added; 4 new tests, 517 total green |

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
| **Phase 2: OBD Protocol** |
| 2.1 | Command formatting + response parsing | DONE | |
| 2.2 | PID support bitmask + VIN decoder | DONE | |
| 2.3 | OBD polling loop + OBDVehicleDataSource | DONE | |
| **Phase 3: Phone Dashboard** |
| 3.1 | Featured metric + sparkline | DONE | |
| 3.2 | MPG row + tile grid | DONE | |
| 3.3 | Connection banner + status bar | DONE | |
| **Phase 4: Bluetooth** |
| 4.1 | BluetoothTransport | DONE | |
| 4.2 | Connect screen UI (3-step flow) | DONE | |
| 4.3 | Auto-reconnect + connection manager | DONE | |
| **Phase 5: Detection** |
| 5.1 | Acceleration detector (mph/s) | DONE | |
| 5.2 | G-Force detector (sensor fusion) | DONE | 35e5af9 |
| 5.3 | Alert system + health alerts | DONE | d1b3f65 |
| **Phase 6: Trips** |
| 6.1 | Trip accumulator | DONE | 3387926 |
| 6.2 | Manual trip manager | DONE | 59baf57 |
| 6.3 | Auto-detected trips | DONE | |
| **Phase 7: Telemetry** |
| 7.1 | Telemetry payload + HTTP uploader | DONE | 3957f78 |
| 7.2 | Offline buffer + WorkManager | DONE | dd3494d |
| 7.3 | Server settings screen | DONE | e4795b3 |
| **Phase 8: Settings** |
| 8.1 | Settings root + general settings | DONE | |
| 8.2 | Phone home layout editor | DONE | |
| 8.3 | Thresholds screen | DONE | fd6261b |
| 8.4 | Trip history screen + CSV export | DONE | 238034a |
| **Phase 9: Android Auto** |
| 9.1 | AA Screen 1 -- Dials | DONE | |
| 9.2 | AA Screen 2 -- Graphs | DONE | |
| 9.3 | AA split-screen panel | DONE | |
| 9.4 | AA alerts + CarToast | DONE | |
| **Phase 10: Polish** |
| 10.1 | Android Auto layout settings | DONE | 49bd9e3 |
| 10.2 | TcpTransport + ELM327 emulator | DONE | 9ad92a6 |
| 10.3 | End-to-end integration testing | DONE | |
| 10.4 | CI pipeline (GitHub Actions) | NOT STARTED | |

## Notes

### AGP 9.0+ / Kotlin 2.2.x build system
- Do NOT apply `org.jetbrains.kotlin.android` plugin to `:mobile` or `:automotive`; AGP 9.0+ handles Kotlin via `builtInKotlin=true` by default
- `kotlinOptions {}` block requires the kotlin.android plugin; use `compileOptions { sourceCompatibility/targetCompatibility }` instead
- KSP uses new versioning scheme starting ~2.3.x (no longer embeds Kotlin version string). Use `ksp = "2.3.8"`
- Android 36.1 compileSdk requires `jlink`, which only exists in full JDK installs. Set `org.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home` in gradle.properties
- Run `./gradlew --stop` after changing gradle.properties to kill cached daemons
