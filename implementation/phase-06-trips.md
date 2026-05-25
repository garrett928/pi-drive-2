# Phase 6: Trip Tracking

**Goal:** Implement trip accumulation (distance, duration, fuel economy), manual trip counter, and auto-detected trip boundaries. After this phase the dashboard shows trip data and the trip infrastructure is ready for the history screen.

**Depends on:** Phase 1 (VehicleDataSource, Room DB), Phase 3 (MPG row on dashboard).

**Reference:** REQUIREMENTS.md sections 5.8, 5.9, 5.10.

---

## Step 6.1 -- Trip Accumulator

**What to build in `shared/src/main/java/.../shared/trip/`:**

1. **`TripAccumulator.kt`**:
   - Stateful class that integrates speed samples into distance + duration
   - `update(speedMph: Float, timestampMs: Long)`: adds sample
   - **Distance:** `distance += speed_mph * (dt_seconds / 3600)` miles
   - **Duration:** Wall-clock time while speed > 0 (excludes stopped time)
   - **Average speed:** `distance / (duration_hours)`
   - **Max speed:** Highest instantaneous speed observed
   - **Fuel economy:** Tracks cumulative fuel consumed (from fuel rate or MAF) + distance for average MPG
   - `reset()`: Zeros all accumulators
   - `pause()` / `resume()`: Suspends accumulation (ignition off)
   - `toSummary(): TripSummary` data class snapshot

2. **`TripSummary.kt`**:
   ```kotlin
   data class TripSummary(
       val distanceMiles: Float,
       val durationMs: Long,
       val avgSpeedMph: Float,
       val maxSpeedMph: Float,
       val avgMpg: Float?,
       val eventCount: Int = 0,
   )
   ```

3. **`FuelTracker.kt`**:
   - Tracks cumulative fuel consumed
   - Two calculation paths (same as FuelEconomy from Phase 2):
     - From PID 5E (fuel rate): integrate `fuelRate_Lph * dt_hours`
     - From PID 10 (MAF): integrate `maf / 12054 * dt_hours`
   - Exposes: `totalFuelLiters`, `currentMpg`, `tripAverageMpg`

**Unit tests:**
- `TripAccumulatorTest.kt`:
  - 60 mph for 1 second -> distance = 0.01667 mi (within 0.001)
  - 60 mph for 60 seconds -> distance ~= 1.0 mi
  - Speed = 0 -> duration does not advance
  - Pause/resume: duration stops during pause, resumes correctly
  - Max speed tracked across all samples
  - Reset: all values return to zero
- `FuelTrackerTest.kt`:
  - Fuel rate 6 L/h for 1 hour at 96.5 km/h -> avg MPG ~38
  - MAF 8.4 g/s at 96.5 km/h -> reasonable MPG

**Estimated size:** ~1k lines

---

## Step 6.2 -- Manual Trip Manager

**What to build:**

1. **`shared/.../trip/ManualTripManager.kt`**:
   - Wraps a `TripAccumulator` with persistence
   - **Lifecycle:**
     - User taps "Reset" -> creates new manual trip in Room, starts accumulating
     - OBD connected + speed > 0 -> accumulates
     - OBD disconnects (ignition off) -> pauses (duration/distance stop)
     - OBD reconnects -> resumes from where it left off
     - App killed/restarted -> reads last active trip from Room, continues
     - Runs indefinitely until user resets
   - Saves state to `ManualTripDao` every 10 seconds (batched, not per-sample)
   - Exposes: `StateFlow<ManualTripState>` with current distance, duration, avgMpg, startDate

2. **`data/model/ManualTripState.kt`**:
   ```kotlin
   data class ManualTripState(
       val isActive: Boolean,
       val distanceMiles: Float,
       val durationMs: Long,
       val avgSpeedMph: Float,
       val maxSpeedMph: Float,
       val avgMpg: Float?,
       val startDate: LocalDate?,
   )
   ```

3. **Wire to dashboard:**
   - `MpgRow` reads `manualTripState.avgMpg` for the manual column
   - "Reset" button calls `ManualTripManager.reset()`
   - Update `LiveDashboardViewModel` to expose `manualTripState`

**Unit tests:**
- `ManualTripManagerTest.kt`:
  - Start trip -> feed speed samples -> distance accumulates
  - Pause (simulate OBD disconnect) -> feed more time -> distance doesn't change
  - Resume -> distance accumulates again
  - Reset -> distance = 0, new startDate
  - Restore from Room: insert trip into DB -> create manager -> reads existing trip

**Verify:**
- `/pd-run` CRUISE scenario -> MPG row manual column shows accumulating MPG
- Tap Reset -> value resets to "---"
- `/pd-screenshot` -> MPG row visible with all 3 columns

**Estimated size:** ~1.2k lines

---

## Step 6.3 -- Auto-Detected Trips

**What to build:**

1. **`shared/.../trip/AutoTripDetector.kt`**:
   - Watches `ConnectionState` from VehicleDataSource
   - **Trip start:** Connection established + first valid data received
   - **Trip end:** Connection lost for > `endTimeoutMinutes` (default 5 min). Short drops (< timeout) are pauses within the same trip.
   - Creates a new `AutoTripEntity` in Room on trip start
   - Updates entity with summary on trip end
   - Uses its own `TripAccumulator` (independent from manual trip)

2. **`shared/.../trip/AutoTripManager.kt`**:
   - Owns the `AutoTripDetector` + accumulator
   - On trip end: finalize summary, save to Room, link driving events to trip
   - Exposes: `StateFlow<AutoTripState?>` for the current active trip (null if no active trip)
   - Exposes: `Flow<List<AutoTripEntity>>` for trip history (from DAO)

3. **`data/model/AutoTripState.kt`**:
   ```kotlin
   data class AutoTripState(
       val tripId: Long,
       val startTime: Instant,
       val distanceMiles: Float,
       val durationMs: Long,
       val avgSpeedMph: Float,
       val maxSpeedMph: Float,
       val avgMpg: Float?,
       val eventCount: Int,
   )
   ```

4. **Coexistence:** Both ManualTripManager and AutoTripManager run simultaneously, each with their own TripAccumulator, both fed from the same VehicleSnapshot flow.

**Unit tests:**
- `AutoTripDetectorTest.kt`:
  - Connection established -> trip starts
  - Connection lost < 5 min -> trip pauses, does not end
  - Connection lost > 5 min -> trip ends, summary written to Room
  - Connection lost + reconnected within timeout -> same trip continues
  - Two separate sessions > 5 min apart -> two separate trips

**Verify:**
- `/pd-run` DISCONNECT scenario -> logcat shows trip start, pause, resume, end
- `/pd-logs` -> "Auto trip started", "Auto trip paused", "Auto trip ended"
- Query Room (via app inspection or logcat) -> trip entity exists with correct times

**Estimated size:** ~1.2k lines
