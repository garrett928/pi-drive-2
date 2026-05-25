# Pi Drive -- Product Requirements

## 1. Overview

Pi Drive is a lightweight, modular Android application that connects to an OBDLink LX Bluetooth OBD-II adapter to read, display, and transmit live vehicle data. The app runs on an Android phone and projects a dashboard to Android Auto. It supports real-time and offline telemetry to a remote server, manual and automatic trip tracking, driving behavior alerts, and vehicle health warnings.

### 1.1 Design Language

- **Aesthetic:** Modern minimal, clean, similar to a fintech app
- **Theme:** Dark mode by default with optional light mode toggle
- **Accent color:** Single warm accent (orange/red), user-selectable
- **Typography:** Large, legible text optimized for in-car glancing
- **Branding:** "Pi Drive"
- **Phone size target:** Pixel-style Android
- **Android Auto orientation:** Landscape (standard head-unit)

---

## 2. Core Features

| # | Feature | Priority |
|---|---|---|
| F1 | Bluetooth dongle connection (OBDLink LX) | Must have |
| F2 | Phone home dashboard (live data) | Must have |
| F3 | Android Auto dashboard (dials + graphs, two screens) | Must have |
| F4 | Real-time server telemetry | Must have |
| F5 | Offline data recording + deferred upload | Must have |
| F6 | Acceleration & G-Force detection with alerts | Must have |
| F7 | Vehicle health alerts | Must have |
| F8 | Manual trip counter | Must have |
| F9 | Auto-detected trip tracking | Must have |
| F10 | Trip history + CSV export | Must have |
| F11 | Settings screens | Must have |

---

## 3. Screens

### 3.1 Phone Screens

| Screen | Purpose |
|---|---|
| **Home Dashboard** | Live data display with configurable, reorderable widgets |
| **Connect to Dongle** | Bluetooth pairing and connection flow |
| **Trip History** | List of past trips (manual + auto-detected), detail view, export |
| **Settings -- Server Config** | Telemetry server URL, API key, upload mode |
| **Settings -- Data Selection** | Choose and reorder which metrics appear on the phone home |
| **Settings -- Android Auto Layout** | (Future) Configure AA screen contents |
| **Settings -- Thresholds** | Hard brake, fast acceleration, and vehicle health alert thresholds |
| **Settings -- General** | Theme (dark/light), accent color, units (mph/km/h), data retention |

### 3.2 Android Auto Screens

| Screen | Purpose |
|---|---|
| **Screen 1 -- Dials** | Gauge-style display of key metrics (speed, RPM, coolant temp, etc.) |
| **Screen 2 -- Graphs** | Line graphs of selected metrics over a configurable time window |

The user swipes between the two screens. Layout is **fixed** (Screen 1 = dials, Screen 2 = graphs). The data items shown on each screen are determined by the app with sensible defaults.

---

## 4. Feature Specifications

### 4.1 Bluetooth Dongle Connection (F1)

**Scope:** Single dongle pairing only. The app supports one OBDLink LX adapter at a time.

#### 4.1.1 Connection Flow

1. User opens the "Connect" screen (or taps a connection status indicator on the home dashboard).
2. If the adapter is already paired at the OS level, the app shows it with a "Connect" button.
3. If not paired, the app prompts the user to pair via Android Bluetooth settings (with instructions to press the LX's Pair button first).
4. On tap "Connect," the app opens an RFCOMM socket using SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.
5. The app sends the initialization sequence (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP 0`) and verifies with `0100`.
6. On success, the home dashboard goes live. On failure, show an error with retry option.

#### 4.1.2 Connection States

| State | UI Indicator |
|---|---|
| **Disconnected** | Gray icon, "Not connected" label, tap to connect |
| **Connecting** | Animated spinner, "Connecting..." |
| **Connected** | Green icon, adapter name shown |
| **Error** | Red icon, error message, retry button |

#### 4.1.3 Auto-Reconnect

When the Bluetooth connection drops (e.g., adapter sleeps on ignition off, phone goes out of range):
- Attempt reconnection automatically every 10 seconds for 5 minutes.
- After 5 minutes, stop retrying and show a "Reconnect" button.
- When the adapter wakes (ignition on), the app should reconnect within one poll cycle.

#### 4.1.4 Permissions

The app requests at runtime:
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `ACCESS_FINE_LOCATION` (required for Bluetooth on Android)

---

### 4.2 Phone Home Dashboard (F2)

The home dashboard displays live vehicle data in a scrollable list of cards/widgets.

#### 4.2.1 Available Data Items

| Data Item | Source | Unit |
|---|---|---|
| Vehicle speed | OBD PID `0D` | mph or km/h (user pref) |
| Engine RPM | OBD PID `0C` | RPM |
| Coolant temperature | OBD PID `05` | F or C (user pref) |
| Intake air temperature | OBD PID `0F` | F or C |
| Throttle position | OBD PID `11` | % |
| Fuel tank level | OBD PID `2F` | % |
| Engine oil temperature | OBD PID `5C` | F or C |
| MAF air flow rate | OBD PID `10` | g/s |
| Fuel consumption rate | OBD PID `5E` | L/h or gal/h |
| Fuel economy (calculated) | Derived | MPG or km/L |
| Battery voltage | AT command `ATRV` | V |
| GPS speed | Phone GPS | mph or km/h |
| Current acceleration rate | Derived | mph/s |
| Current g-force | Derived | g |
| Trip distance (manual) | Accumulated | mi or km |
| Trip duration (manual) | Accumulated | hh:mm:ss |
| Trip average speed (manual) | Derived | mph or km/h |

#### 4.2.2 Customization

- The user can **show or hide** any data item from the Settings -- Data Selection screen.
- The user can **reorder** visible items via drag-and-drop in that same screen.
- The order and visibility are persisted in `SharedPreferences`.
- Items for unsupported PIDs (detected at connection time via PID support bitmask) are automatically hidden and grayed out in the selection screen.

#### 4.2.3 Layout

- One column of cards, each showing a data label, current value, and unit.
- Cards update in real time as new OBD data arrives.
- Connection status indicator at the top of the screen (persistent).
- Manual trip counter summary bar (distance, duration, avg speed) pinned below the connection status when a manual trip is active.

---

### 4.3 Android Auto Dashboard (F3)

The Android Auto interface uses the Android for Cars App Library (`androidx.car.app`) and registers as an **IoT** category app.

#### 4.3.1 Screen 1 -- Dials

Displayed using `GridTemplate`. Each grid item represents a gauge/dial showing:
- The metric name (e.g., "Speed")
- The current value with unit (e.g., "65 mph")
- An icon representing the metric

Default items (6 max for readability in split-screen):
1. Speed
2. RPM
3. Coolant Temp
4. Throttle %
5. Fuel Level
6. Battery Voltage

#### 4.3.2 Screen 2 -- Graphs

Displayed using `GridTemplate` or a custom `NavigationTemplate` surface (if line graph rendering requires a `Surface`). Shows line graphs of selected metrics over time.

- **Time window:** User-configurable in settings: 30s, 60s, 2 min, 5 min.
- **Default window:** 60 seconds.
- Graphs auto-scroll left as new data arrives.
- Default graph items:
  - Speed over time
  - RPM over time
  - Coolant temp over time

#### 4.3.2.1 Threshold Lines on Acceleration Graph

When the acceleration rate graph is displayed, it must render horizontal threshold lines at the user-configured hard acceleration and hard braking values:

- **Hard acceleration line** at +9 mph/s (positive axis, accent color, labeled "Accel Limit")
- **Hard brake line** at −6.5 mph/s (negative axis, accent color, labeled "Brake Limit")
- Lines are drawn as dashed/dotted horizontals spanning the full width of the graph
- If the plotted line crosses either threshold, the portion of the line beyond the threshold renders in a warning color (red)
- Threshold lines update live if the user changes the values in Settings -- Thresholds

#### 4.3.3 Navigation Between Screens

The user swipes left/right (or taps navigation affordances) to switch between Screen 1 and Screen 2. Implementation uses the Car App Library's `Screen` stack or `TabTemplate` if available at the target API level.

#### 4.3.4 Split-Screen / Half-Screen

Android Auto natively supports split-screen on displays 6" and larger. The app's templates automatically adapt to the reduced screen space. No special code is required, but:
- Grid items must use concise labels.
- Dial values must be large and legible.
- Limit to 6 items per screen max.

#### 4.3.5 Alerts on Android Auto

Driving behavior alerts (hard brake, hard acceleration) and vehicle health alerts display as `CarToast` notifications on the Android Auto screen. These are non-blocking and dismiss automatically.

---

### 4.4 Server Telemetry (F4, F5)

The app supports three user-selectable telemetry modes:

| Mode | Behavior |
|---|---|
| **Live** | POST data to the server in real time. If the server is unreachable, data is dropped (not queued). |
| **Offline** | Write all data to local storage (Room DB). Upload manually or on a schedule when the user chooses. |
| **Hybrid** (default) | POST in real time. On failure, buffer to local storage and retry with exponential backoff. |

#### 4.4.1 Server Configuration

| Setting | Type | Required | Notes |
|---|---|---|---|
| Server URL | text (URL) | Yes | Base URL for the telemetry API endpoint |
| API key | text | No | Sent as `X-API-Key` header if provided |
| Upload mode | picker | Yes | Live / Offline / Hybrid |
| Upload interval | picker | Yes (Live/Hybrid) | How often to POST: 1s, 5s, 10s, 30s, 60s |
| Data items to upload | multi-select | Yes | Choose which metrics are included in the payload |

#### 4.4.2 Payload

Each POST sends a JSON object containing:
- Timestamp (ISO 8601)
- VIN (queried once at connection via OBD service `09`, PID `02`)
- GPS location (lat, lng, GPS speed)
- All enabled OBD readings
- Calculated values (fuel economy, acceleration rate, g-force)
- Any driving events (acceleration/g-force alerts) since the last upload
- Current manual trip summary (if active)

See `README.md` for the full payload schema.

#### 4.4.3 Offline Storage

- Data is written to a Room database.
- Each row contains one timestamped snapshot of all active metrics.
- The database respects the user's data retention setting (see 4.10.3).
- The user can trigger a manual "Upload All" from the settings screen.
- `WorkManager` handles background upload with retry and backoff.

#### 4.4.4 Data Selection for Upload

The user can select which data items are included in server uploads from the Settings -- Server Config screen. This is independent of which items are shown on the phone dashboard. For example, a user might display speed and RPM on the phone but upload all available PIDs to the server.

---

### 4.5 Acceleration Detection (F6) -- "Acceleration" Strategy

Detects hard acceleration and hard braking based on the **rate of speed change** in **mph/s** (or km/h per second, matching the user's unit preference).

#### 4.5.1 Data Sources

- **Primary:** OBD-II vehicle speed (PID `0D`), converted to mph.
- **Fallback:** GPS speed (`Location.getSpeed()`), used if OBD data is stale (> 500 ms old).

#### 4.5.2 Thresholds

| Setting | Default | Unit |
|---|---|---|
| Hard acceleration threshold | 9 | mph/s |
| Hard braking threshold | 6.5 | mph/s |
| Minimum event duration | 0.5 | seconds |

Thresholds are user-configurable in Settings -- Thresholds.

#### 4.5.3 Event Trigger

An event fires when the speed change rate exceeds the threshold for at least the minimum duration continuously. This prevents momentary bumps or sensor noise from firing false events.

---

### 4.6 G-Force Detection (F6) -- "G-Force" Strategy

Detects hard acceleration and hard braking based on **g-force** (multiples of 9.81 m/s^2) using sensor fusion.

#### 4.6.1 Data Sources

- OBD-II speed deltas (PID `0D`)
- GPS speed deltas (`Location.getSpeed()`)
- Phone accelerometer (`TYPE_LINEAR_ACCELERATION`)

#### 4.6.2 Cross-Validation

An event fires only when **at least 2 of 3 sources** agree that the threshold has been exceeded. This eliminates false positives from phone drops, GPS jumps, or OBD polling gaps.

#### 4.6.3 Accelerometer Calibration

On first enable, the app prompts the user to calibrate:
1. Mount the phone in the car.
2. Drive straight and accelerate briefly.
3. The app identifies the longitudinal axis and stores the mapping.

Calibration can be re-run from Settings -- Thresholds.

#### 4.6.4 Thresholds

| Setting | Default | Unit |
|---|---|---|
| Hard acceleration threshold | 0.22 | g |
| Hard braking threshold | 0.265 | g |
| Severe braking threshold | 0.50 | g |
| Minimum event duration | 0.5 | seconds |

Thresholds are user-configurable in Settings -- Thresholds.

---

### 4.7 Vehicle Health Alerts (F7)

Configurable alerts that fire when a monitored value crosses a user-defined threshold. Alerts appear on **both the phone and Android Auto** (`CarToast`).

#### 4.7.1 Default Alert Rules

| Alert | Condition | Default Threshold | Enabled by Default |
|---|---|---|---|
| High coolant temp | Coolant temp > threshold | 230 F / 110 C | Yes |
| Low fuel | Fuel level < threshold | 10% | Yes |
| High RPM | RPM > threshold | 6000 RPM | Yes |
| Overspeed | Speed > threshold | 80 mph / 129 km/h | No |
| Low battery voltage | Voltage < threshold | 11.5 V | No |

#### 4.7.2 Alert Behavior

- Each alert has: **enabled** (toggle), **threshold** (numeric), and **cooldown** (seconds before the same alert can fire again, default 60s).
- When triggered: show a toast/notification on the phone, show a `CarToast` on Android Auto, log the event.
- Alerts are included in the server telemetry payload as events (same format as driving events, with an `ALERT` strategy type).

#### 4.7.3 User Settings

All thresholds, enabled states, and cooldowns are configurable from Settings -- Thresholds. Alerts for PIDs unsupported by the connected vehicle are automatically disabled.

---

### 4.8 Manual Trip Counter (F8)

A persistent, user-controlled trip meter that tracks cumulative stats across driving sessions (including engine restarts and app restarts).

#### 4.8.1 Lifecycle

1. **Start/Reset:** User taps "Start New Trip" (or "Reset Trip") from the Trip History screen or a home dashboard action.
2. **Recording:** While the OBD connection is active and speed > 0, the trip accumulates distance, duration, and speed samples.
3. **Auto-pause on ignition off:** When the OBD connection drops (adapter sleeps on ignition off, detected by loss of data or voltage drop below threshold), the trip **pauses**. Duration and distance stop accumulating.
4. **Auto-resume on ignition on:** When the OBD connection re-establishes, the trip resumes from where it paused.
5. **Persists across reboots:** Trip state is saved to the Room database. Killing and restarting the app does not lose the trip.
6. **Stop:** The trip continues indefinitely until the user explicitly resets it. There is no auto-stop.

#### 4.8.2 Tracked Metrics

| Metric | How calculated | Unit |
|---|---|---|
| Distance | Accumulated from speed * time deltas | mi or km |
| Duration | Wall clock time while recording (excludes paused time) | hh:mm:ss |
| Average speed | Distance / duration | mph or km/h |
| Max speed | Highest instantaneous speed observed | mph or km/h |
| Average fuel economy | Accumulated fuel consumption / distance (or MAF-derived) | MPG or km/L |

#### 4.8.3 Display

- A summary bar on the phone home dashboard (when a manual trip is active) showing distance, duration, and avg speed.
- The full trip detail (including max speed and fuel economy) is available on the Trip History screen.

---

### 4.9 Auto-Detected Trips (F9)

The app automatically detects individual driving sessions based on ignition state.

#### 4.9.1 Trip Boundaries

- A new auto trip **starts** when the OBD connection is established and the app receives valid data.
- The auto trip **ends** when the OBD connection is lost for longer than a configurable timeout (default: 5 minutes). Short connection drops (< timeout) are treated as pauses within the same trip.

#### 4.9.2 Coexistence with Manual Trip

Auto-detected trips and the manual trip run **independently and simultaneously**:

- The manual trip is a single long-running accumulator (e.g., "road trip from LA to Vegas over 3 days").
- Auto trips are discrete sessions (e.g., "Tuesday morning commute," "Tuesday evening commute").
- A manual trip may span many auto trips.
- Each auto trip tracks the same metrics as a manual trip (distance, duration, avg speed, max speed, fuel economy).

#### 4.9.3 Storage

Auto trips are saved to the Room database with start/end timestamps, a trip summary, and a reference to the raw telemetry data (if offline recording is enabled).

---

### 4.10 Trip History + Export (F10)

#### 4.10.1 Trip List

The Trip History screen shows two tabs or sections:
- **Auto Trips:** Chronological list of auto-detected trips with date, duration, distance, and avg speed.
- **Manual Trip:** Current manual trip (if active) with full stats, plus a history of past manual trips (each time the user resets, the previous trip is archived).

Tapping a trip shows a detail view with all tracked metrics and (if recorded) a map of the GPS track.

#### 4.10.2 CSV Export

The user can export any individual trip as a CSV file. The CSV contains one row per data sample at the OBD poll rate, with columns for:

```
timestamp, lat, lng, speed_mph, rpm, coolant_temp_f, throttle_pct, fuel_level_pct, oil_temp_f, maf_gps, fuel_rate, battery_v, accel_mph_s, g_force
```

Export is triggered via a share button on the trip detail screen, using Android's `ShareSheet` (share to Files, email, Drive, etc.).

#### 4.10.3 Data Retention

The user configures retention in Settings -- General:

| Option | Behavior |
|---|---|
| 30 days | Auto-delete trips older than 30 days |
| 60 days | Auto-delete trips older than 60 days |
| 90 days | Auto-delete trips older than 90 days |
| Unlimited | Never auto-delete |

Default: **90 days.** Deletion runs once per app launch. Manual trips that are still active are never auto-deleted.

---

### 4.11 Settings Screens (F11)

#### 4.11.1 Settings -- Server Config

| Field | Type | Notes |
|---|---|---|
| Server URL | Text input | Validated as a URL on save |
| API key | Text input (masked) | Optional, sent as `X-API-Key` |
| Upload mode | Picker: Live / Offline / Hybrid | Default: Hybrid |
| Upload interval | Picker: 1s / 5s / 10s / 30s / 60s | Default: 10s. Only shown for Live and Hybrid modes. |
| Data items to upload | Multi-select checklist | All items enabled by default |
| Upload All (offline data) | Button | Only shown when offline data exists. Triggers immediate upload. |
| Test Connection | Button | Sends a test POST to the server and shows success/failure |

#### 4.11.2 Settings -- Data Selection (Phone Home)

- Checklist of all available data items.
- Each item has a toggle (show/hide).
- Drag handles for reordering.
- Items for unsupported PIDs are shown but grayed out with "(unsupported)" label.
- Changes take effect immediately on the home dashboard.

#### 4.11.3 Settings -- Thresholds

**Acceleration strategy:**

| Field | Type | Default |
|---|---|---|
| Enabled | Toggle | On |
| Hard accel threshold | Numeric (mph/s) | 9 |
| Hard brake threshold | Numeric (mph/s) | 6.5 |

**G-Force strategy:**

| Field | Type | Default |
|---|---|---|
| Enabled | Toggle | Off |
| Hard accel threshold | Numeric (g) | 0.22 |
| Hard brake threshold | Numeric (g) | 0.265 |
| Severe brake threshold | Numeric (g) | 0.50 |
| Calibrate accelerometer | Button | Opens calibration flow |

**Shared:**

| Field | Type | Default |
|---|---|---|
| Minimum event duration | Numeric (seconds) | 0.5 |
| Alert sound | Toggle | On |

**Vehicle health alerts:**

Each alert (coolant, fuel, RPM, speed, voltage) has:

| Field | Type |
|---|---|
| Enabled | Toggle |
| Threshold | Numeric (in the relevant unit) |
| Cooldown | Numeric (seconds) |

#### 4.11.4 Settings -- General

| Field | Type | Default |
|---|---|---|
| Theme | Picker: Dark / Light | Dark |
| Accent color | Color picker (orange/red presets) | Orange |
| Speed units | Picker: mph / km/h | mph |
| Temperature units | Picker: F / C | F |
| Data retention | Picker: 30 / 60 / 90 days / Unlimited | 90 days |
| Auto-trip end timeout | Numeric (minutes) | 5 |
| About / Version | Info | App version, build number, links |

---

## 5. Data Architecture

### 5.1 OBD Polling

The app polls OBD PIDs in a round-robin loop on a background thread:

1. Send PID request (e.g., `010D`).
2. Read response until `>` prompt.
3. Parse hex bytes, apply the PID formula.
4. Update the in-memory data model.
5. Notify UI observers (phone dashboard, Android Auto screen, telemetry service).
6. Repeat with the next PID.

**Target poll rate:** 2-5 Hz for the full PID set (depends on number of enabled PIDs and adapter response time). High-priority PIDs (speed, RPM) should be polled more frequently than low-priority PIDs (fuel level, oil temp).

### 5.2 Data Model

```kotlin
data class VehicleSnapshot(
    val timestamp: Instant,
    val speedKmh: Int?,
    val rpm: Int?,
    val coolantTempC: Int?,
    val intakeAirTempC: Int?,
    val throttlePct: Float?,
    val fuelLevelPct: Float?,
    val oilTempC: Int?,
    val mafGps: Float?,
    val fuelRateLph: Float?,
    val batteryVoltage: Float?,
    val gpsLat: Double?,
    val gpsLng: Double?,
    val gpsSpeedMps: Float?,
    val accelRateMphS: Float?,
    val gForce: Float?
)
```

### 5.3 Local Database (Room)

| Table | Purpose |
|---|---|
| `snapshots` | Raw telemetry data (one row per poll cycle) |
| `driving_events` | Acceleration, g-force, and health alert events |
| `auto_trips` | Auto-detected trip summaries |
| `manual_trips` | Manual trip state and archived trip summaries |
| `pending_uploads` | Offline telemetry queued for server upload |

### 5.4 Reactive Data Flow

Use Kotlin `StateFlow` / `SharedFlow` to propagate data from the OBD polling thread to all consumers:

```
OBD Poll Loop  -->  StateFlow<VehicleSnapshot>  -->  Phone Dashboard UI
                                                 -->  Android Auto Screen
                                                 -->  Acceleration Detector
                                                 -->  G-Force Detector
                                                 -->  Health Alert Monitor
                                                 -->  Trip Accumulator(s)
                                                 -->  Telemetry Uploader
```

---

## 6. Non-Functional Requirements

### 6.1 Performance

- OBD polling must not block the UI thread.
- The phone dashboard must update within 100 ms of new data arriving.
- Android Auto screen must update within 250 ms (constrained by Car App Library refresh rates).
- Server uploads must not affect OBD polling cadence.

### 6.2 Battery

- GPS polling at 1 Hz (adequate for speed and location, balanced with battery).
- Accelerometer at SENSOR_DELAY_GAME (~50 Hz) only when G-Force detection is enabled.
- OBD Bluetooth connection uses standard SPP -- no excessive battery drain beyond normal Bluetooth usage.
- The app should release sensors and stop polling when the OBD connection is lost and auto-reconnect attempts are exhausted.

### 6.3 Reliability

- The app must not crash on OBD communication errors (malformed responses, timeouts, adapter resets).
- All OBD communication must include timeout handling (default 2 seconds per command).
- The Bluetooth socket must be managed on a dedicated thread with proper cleanup on disconnect.
- Manual trip state must survive: process death, phone reboot, app update.

### 6.4 Security

- API keys are stored in Android `EncryptedSharedPreferences`.
- Server communication uses HTTPS only. HTTP URLs are rejected in settings validation.
- The OBDLink LX's 128-bit Bluetooth encryption is relied upon for the adapter link.

### 6.5 Compatibility

- **Min SDK:** 34 (Android 14)
- **Target SDK:** 36
- **Android Auto:** Requires Android Auto app on the phone + compatible head unit (or DHU for testing)
- **OBD adapter:** OBDLink LX (STN1155). Other ELM327-compatible adapters may work but are not officially supported.

---

## 7. Out of Scope (v1)

These features are explicitly deferred:

- Support for multiple Bluetooth dongles / multi-vehicle
- Android Auto screen layout customization (fixed in v1)
- DTC reading, clearing, or display
- Enhanced/proprietary diagnostics (Mode 22)
- OBD data recording for playback/replay
- Social or leaderboard features
- Wear OS companion app
- iOS / cross-platform
- Map display of trip GPS tracks within the app (export to external tools via CSV)
- Widget / persistent notification with live data

---

## 8. Glossary

| Term | Definition |
|---|---|
| **OBD-II** | On-Board Diagnostics, Second Generation. Standardized vehicle diagnostic system, mandatory on all US vehicles since 1996. |
| **PID** | Parameter ID. A code used to request a specific data value from the vehicle's ECU. |
| **ECU** | Electronic Control Unit. The vehicle's onboard computer. |
| **ELM327** | A de facto standard command set for OBD-II interpreters. The OBDLink LX is ELM327-compatible. |
| **STN1155** | The OBD Solutions chip inside the OBDLink LX. Supports the ELM327 AT command set plus extended ST commands. |
| **SPP** | Serial Port Profile. The Bluetooth profile used by the OBDLink LX for serial communication. |
| **RFCOMM** | The Bluetooth protocol layer that emulates serial ports. Used by Android's `BluetoothSocket`. |
| **CAN** | Controller Area Network. The dominant OBD-II signaling protocol on modern vehicles. |
| **MAF** | Mass Air Flow sensor. Used to calculate fuel economy when direct fuel consumption rate is unavailable. |
| **DHU** | Desktop Head Unit. Android's emulator for testing Android Auto apps without a real car. |
| **CarToast** | A short notification message displayed on the Android Auto screen. |
| **g** | Unit of acceleration equal to 9.81 m/s^2 (standard gravity). |
