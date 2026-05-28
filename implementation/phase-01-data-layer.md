# Phase 1: Data Layer

**Goal:** Create the data model, transport abstraction, demo data source, and Room database. After this phase, the app has data flowing through StateFlow and persisting to a local DB -- all future UI and feature phases bind to these interfaces.

**Depends on:** Phase 0 (Compose + Hilt + Room in build system).

---

## Step 1.1 -- Data Models + Interfaces

**What to build in `shared/src/main/java/.../shared/`:**

1. **`data/model/VehicleSnapshot.kt`**:
   ```kotlin
   data class VehicleSnapshot(
       val timestamp: Instant = Instant.now(),
       val speedKmh: Int? = null,
       val rpm: Int? = null,
       val coolantTempC: Int? = null,
       val intakeAirTempC: Int? = null,
       val throttlePct: Float? = null,
       val fuelLevelPct: Float? = null,
       val oilTempC: Int? = null,
       val mafGps: Float? = null,
       val fuelRateLph: Float? = null,
       val batteryVoltage: Float? = null,
       val gpsLat: Double? = null,
       val gpsLng: Double? = null,
       val gpsSpeedMps: Float? = null,
       val accelRateMphS: Float? = null,
       val gForce: Float? = null,
   )
   ```

2. **`data/model/MetricId.kt`**: Enum of all 16 metrics with display label, unit string, and icon resource ID. Values: `SPEED, MPG_INSTANT, MPG_TRIP, MPG_MANUAL, RPM, THROTTLE, COOLANT, INTAKE, OIL_TEMP, BATTERY, FUEL, MAF, G_FORCE, ACCEL, DISTANCE, MANUAL_TRIP`.

3. **`data/model/ConnectionState.kt`**: Sealed class: `Disconnected`, `Connecting`, `Connected(adapterName, protocol, pollRateHz)`, `Error(message)`.

4. **`data/model/DrivingEvent.kt`**: Data class matching the event model from README section "Event model" -- `DetectionStrategy`, `EventType`, `DataSource` enums, and `DrivingEvent` data class.

5. **`obd/OBDTransport.kt`**: Interface:
   ```kotlin
   interface OBDTransport {
       suspend fun connect()
       suspend fun disconnect()
       suspend fun send(command: String): String
       val isConnected: StateFlow<Boolean>
   }
   ```

6. **`data/VehicleDataSource.kt`**: Interface:
   ```kotlin
   interface VehicleDataSource {
       val snapshot: StateFlow<VehicleSnapshot>
       val connectionState: StateFlow<ConnectionState>
       val supportedPids: StateFlow<Set<Int>>
       fun startPolling()
       fun stopPolling()
   }
   ```

7. **`data/model/MetricValue.kt`**: Helper to extract a metric's current value from a VehicleSnapshot given a MetricId. Returns `Float?` plus formatted display string.

**Unit tests (`shared/src/test/`):**
- `VehicleSnapshotTest.kt`: Default snapshot has all nulls; snapshot with values round-trips correctly
- `MetricValueTest.kt`: Extract speed from snapshot -> correct value; extract from empty snapshot -> null
- `MetricIdTest.kt`: All 16 enum values exist; each has non-blank label and unit

**Estimated size:** ~1k lines

---

## Step 1.2 -- MockTransport + DemoVehicleDataSource

**What to build:**

1. **`shared/src/main/java/.../shared/obd/MockTransport.kt`**:
   - Implements `OBDTransport`
   - `send()` returns canned hex responses based on command (AT commands return standard strings, PID requests return configurable hex)
   - Has a `setPidResponse(pid: Int, hexBytes: String)` for test injection
   - Default responses for all target PIDs (speed=80 km/h, RPM=2500, coolant=90C, etc.)

2. **`shared/src/main/java/.../shared/data/DemoVehicleDataSource.kt`**:
   - Implements `VehicleDataSource`
   - Emits `VehicleSnapshot` every 250ms via coroutine
   - Uses sine-wave oscillations to simulate realistic driving
   - Supports named scenarios via `DemoScenario` enum:
     - `CRUISE`: steady 60 mph, 2500 RPM, stable temps
     - `CITY`: 0-45 mph oscillation, frequent stops
     - `HIGHWAY`: 65-80 mph, low RPM
     - `HARD_BRAKE`: normal -> sudden deceleration events every ~15s
     - `COLD_START`: coolant climbs from 70F to 195F over 5 min
     - `LOW_FUEL`: fuel drains 15% -> 5%
     - `OVERSPEED`: bursts above 75 mph
     - `DISCONNECT`: data stops for 30s then resumes

3. **`mobile/src/main/java/.../di/DataModule.kt`**:
   - Hilt module providing `VehicleDataSource`
   - Reads intent extras from `MainActivity` to decide: if `demo_mode=true` -> `DemoVehicleDataSource`, else -> (placeholder for production, will be OBDVehicleDataSource later)
   - Scenario selection via `demo_scenario` extra

4. **Update `MainActivity.kt`**: Parse intent extras for demo_mode/demo_scenario, pass to DI.

**Unit tests:**
- `MockTransportTest.kt`: send ATZ -> contains "ELM327"; send "010D" -> returns valid hex; send unsupported -> "NO DATA"
- `DemoVehicleDataSourceTest.kt`: start CRUISE -> snapshot.speedKmh is non-null within 500ms; HARD_BRAKE -> speed drops sharply at expected intervals

**Verify:**
- `/pd-run` (launches in demo mode by default) -> logcat shows "Demo mode active, scenario: CRUISE"
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-demo-placeholder.png` → read image: app still shows placeholder text (UI isn't wired yet, but no crash)

**Estimated size:** ~1.5k lines

---

## Step 1.3 -- Room Database Schema

**What to build in `shared/`:**

1. **`shared/src/main/java/.../shared/data/db/PiDriveDatabase.kt`**:
   - Room database class with all tables
   - Version 1

2. **Entities** (`shared/src/main/java/.../shared/data/db/entity/`):
   - `SnapshotEntity.kt`: Mirrors VehicleSnapshot + auto-generated primary key + tripId FK
   - `DrivingEventEntity.kt`: Mirrors DrivingEvent + primary key
   - `AutoTripEntity.kt`: id, startTime, endTime, distanceMi, durationMs, avgSpeedMph, maxSpeedMph, avgMpg, eventCount, syncStatus
   - `ManualTripEntity.kt`: id, startTime, distanceMi, durationMs, avgSpeedMph, maxSpeedMph, avgMpg, isActive
   - `PendingUploadEntity.kt`: id, timestamp, payload (JSON string), retryCount, nextRetryTime

3. **DAOs** (`shared/src/main/java/.../shared/data/db/dao/`):
   - `SnapshotDao.kt`: insert, getByTripId, getByTimeRange, deleteOlderThan
   - `DrivingEventDao.kt`: insert, getByTimeRange, getByTripId, countByTypeAndTimeRange
   - `AutoTripDao.kt`: insert, update, getActive, getAll (paged), getByDateRange, delete
   - `ManualTripDao.kt`: insert, update, getActive, getAll
   - `PendingUploadDao.kt`: insert, getNextBatch(limit), markUploaded, deleteUploaded, countPending

4. **Type converters** (`shared/src/main/java/.../shared/data/db/Converters.kt`):
   - `Instant` <-> `Long` (epochMilli)
   - `DetectionStrategy` / `EventType` / `DataSource` <-> `String`
   - `Set<DataSource>` <-> `String` (comma-separated)

5. **Hilt module**: Provide database and DAOs.

**Unit tests (use Room's in-memory database):**
- `SnapshotDaoTest.kt`: Insert 10 snapshots -> query by time range returns correct subset -> deleteOlderThan removes expected rows
- `AutoTripDaoTest.kt`: Insert trip -> getActive returns it -> update with endTime -> getActive returns null
- `PendingUploadDaoTest.kt`: Insert 3 -> getNextBatch(2) returns 2 -> markUploaded -> getNextBatch returns remaining 1

**Estimated size:** ~1.5k lines
