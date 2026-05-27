# Phase 5: Acceleration & G-Force Detection

**Goal:** Implement both detection strategies (Acceleration in mph/s and G-Force with sensor fusion), plus health alerts. After this phase, the app detects and logs hard acceleration/braking events and vehicle health warnings.

**Depends on:** Phase 1 (VehicleDataSource, DrivingEvent model, Room DB).

**Reference:** REQUIREMENTS.md sections 5.5-5.7, README.md "Acceleration and Braking Detection".

---

## Step 5.1 -- Acceleration Detector (mph/s Strategy)

**What to build in `shared/src/main/java/.../shared/detection/`:**

1. **`AccelerationDetector.kt`**:
   - Consumes `StateFlow<VehicleSnapshot>` and emits `Flow<DrivingEvent>`
   - **Data sources:** OBD speed (primary), GPS speed (fallback if OBD stale > 500ms)
   - **Calculation:** `rate_mph_s = (speed_now - speed_prev) / delta_time_s`
   - Conversion: OBD km/h -> mph (* 0.621371), GPS m/s -> mph (* 2.23694)
   - **Thresholds** (configurable):
     - Hard acceleration: default 9 mph/s
     - Hard braking: default 6.5 mph/s
   - **Minimum duration filter:** Event only fires when threshold exceeded continuously for >= `minDurationMs` (default 500ms). Prevents transient bumps.
   - **Event emission:** Creates `DrivingEvent` with `strategy=ACCELERATION`, `type=HARD_ACCEL|HARD_BRAKE`, timestamps, speeds, rate, sources used
   - **State machine:** IDLE -> DETECTING (threshold crossed) -> EVENT (duration met) -> COOLDOWN -> IDLE

2. **`DetectionConfig.kt`**: Data class holding all threshold settings for both strategies:
   ```kotlin
   data class DetectionConfig(
       val accelEnabled: Boolean = true,
       val accelHardAccelThreshold: Float = 9f,
       val accelHardBrakeThreshold: Float = 6.5f,
       val gForceEnabled: Boolean = false,
       val gForceHardAccelThreshold: Float = 0.22f,
       val gForceHardBrakeThreshold: Float = 0.265f,
       val gForceSevereBrakeThreshold: Float = 0.50f,
       val minEventDurationMs: Long = 500,
   )
   ```

**Unit tests -- comprehensive, these are safety-critical:**
- `AccelerationDetectorTest.kt`:
  - Hard brake: feed 60->50 mph over 1s (10 mph/s) with brake threshold 6.5 -> event fires
  - Hard accel: feed 30->42 mph over 1s (12 mph/s) with accel threshold 9 -> event fires
  - Below threshold: feed 60->55 mph over 1s (5 mph/s) with threshold 6.5 -> no event
  - Transient spike: speed drops and recovers within 200ms -> no event (min duration filter)
  - Sustained event: speed drops steadily for 2s -> event fires after minDuration, reports correct peak rate
  - Zero speed: stopped car -> no spurious events
  - GPS fallback: OBD speed goes stale (no update 600ms), GPS speed available -> uses GPS
  - Multiple events: two hard brakes 5s apart -> two separate events

**Verify:**
- `/pd-run` HARD_BRAKE scenario
- `/pd-logs` -> `AccelDetector` tag: "Hard brake detected, rate=X mph/s" logged within 15s
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-accel-detector.png` → read image: dashboard showing live speed data confirms AccelerationDetector receiving snapshots

**Estimated size:** ~1k lines

---

## Step 5.2 -- G-Force Detector (Sensor Fusion)

**What to build:**

1. **`GForceDetector.kt`**:
   - Consumes `StateFlow<VehicleSnapshot>` + accelerometer data channel
   - **Three data sources:**
     - OBD speed delta: `(speed_current - speed_previous) / dt` converted to m/s^2
     - GPS speed delta: same formula on `Location.getSpeed()` values
     - Phone accelerometer: `TYPE_LINEAR_ACCELERATION` longitudinal axis
   - **Cross-validation:** Event fires only when **>= 2 of 3 sources** agree threshold exceeded
   - **Thresholds** (configurable): 0.22g accel, 0.265g brake, 0.50g severe
   - **Conversion:** `g = m_s2 / 9.81`, `m_s2 = g * 9.81`
   - Same duration filter and state machine as AccelerationDetector

2. **`AccelerometerManager.kt`**:
   - Registers `TYPE_LINEAR_ACCELERATION` sensor at `SENSOR_DELAY_GAME` (~50 Hz)
   - Calibration: identifies longitudinal axis and sign during calibration drive
   - Low-pass filter (alpha ~0.8) on raw readings
   - Stores calibration (axis index, sign) in SharedPreferences
   - Exposes `StateFlow<Float>` for longitudinal acceleration in m/s^2
   - Lifecycle-aware: register on start, unregister on stop

3. **`CalibrationManager.kt`**:
   - Calibration flow: collect 5s of accelerometer data during straight-line driving
   - Identify axis with highest variance -> that's longitudinal
   - Determine sign (positive = forward acceleration)
   - Persist result, expose `isCalibrated: Boolean`

**Unit tests:**
- `GForceDetectorTest.kt`:
  - All 3 sources above 0.265g -> event fires (HARD_BRAKE)
  - 2 of 3 above threshold -> event fires
  - Only 1 of 3 above threshold -> no event (cross-validation failure)
  - OBD above, GPS above, accelerometer below -> fires (2/3)
  - Accelerometer spike but OBD and GPS steady -> no event (phone drop scenario)
  - Severe brake: 2 sources above 0.50g -> severity=SEVERE in event
- `AccelerometerManagerTest.kt`:
  - Low-pass filter: sudden spike smoothed; steady signal passes through
  - Calibration: axis with highest variance selected

**Verify:**
- `/pd-run` HARD_BRAKE scenario
- `/pd-logs` -> `GForceDetector` tag: cross-validation logic running; note: physical accelerometer not available on emulator, so OBD+GPS sources are used
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-gforce-detector.png` → read image: G-Force tile visible on dashboard, confirms sensor pipeline wired correctly

**Estimated size:** ~1.5k lines

---

## Step 5.3 -- Alert System + Health Alerts

**What to build:**

1. **`shared/.../detection/AlertManager.kt`**:
   - Central coordinator for all alerts
   - Subscribes to AccelerationDetector + GForceDetector event flows
   - Subscribes to VehicleSnapshot for health monitoring
   - On event/threshold:
     - Log to Room (`DrivingEventDao.insert`)
     - Emit `AlertAction` to UI layer
   - **Alert cooldown:** Configurable per alert type (default 60s). Same alert won't fire twice within cooldown.

2. **`shared/.../detection/HealthMonitor.kt`**:
   - Watches VehicleSnapshot for health thresholds:
     - High coolant: > 230F / 110C (default, configurable)
     - Low fuel: < 10% (default, configurable)
     - High RPM: > 6500 (default, configurable)
     - Overspeed: > 75 mph (default, disabled by default)
     - Low battery: < 11.5V (default, disabled by default)
   - Each alert: `enabled` toggle, `threshold` value, `cooldownSeconds`
   - Auto-disables alerts for PIDs unsupported by the vehicle
   - Emits `HealthAlert` events

3. **`data/model/AlertAction.kt`**: Sealed class:
   - `SoundAlert(type)`, `HapticAlert(type)`, `CarToastAlert(message)`, `EventFlag(event)`
   - Configurable: sound on/off, haptic on/off, CarToast on/off, event flag always on

4. **`mobile/.../ui/components/AlertOverlay.kt`**:
   - Composable overlay shown when an alert fires
   - Brief banner at top of screen, auto-dismisses after 3s
   - Shows event type, value, severity
   - Haptic feedback via `HapticFeedbackType.LongPress`

5. **Update `LiveDashboardViewModel`**: Subscribe to `AlertManager`, trigger overlay and haptic.

**Unit tests:**
- `AlertManagerTest.kt`: AccelerationDetector fires event -> AlertManager logs to DB + emits AlertAction
- `HealthMonitorTest.kt`: Coolant at 115C -> fires alert; at 90C -> no alert; fire alert -> cooldown -> same condition -> no second alert within cooldown
- `AlertManagerTest.kt` cooldown: two events 30s apart with 60s cooldown -> only first fires alert

**Verify:**
- `/pd-run` with HARD_BRAKE scenario
- `/pd-logs` -> "HARD_BRAKE event" logged with rate/g values
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-alert-overlay.png` → read image: alert overlay visible when event fires
- `/pd-run` with LOW_FUEL scenario -> health alert fires when fuel drops below 10%

**Estimated size:** ~1.5k lines
