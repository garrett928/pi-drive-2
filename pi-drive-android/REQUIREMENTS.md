# Pi Drive -- Product Requirements

## 1. Overview

Pi Drive is a lightweight, modular Android application that connects to an OBDLink LX Bluetooth OBD-II adapter to read, display, and transmit live vehicle data. The app runs on an Android phone and projects a dashboard to Android Auto. It supports real-time and offline telemetry to a remote server, manual and automatic trip tracking, driving behavior alerts, and vehicle health warnings.

**UI reference:** The `ui-handoff/pi-drive/` directory contains HTML/CSS/JS design prototypes exported from Claude Designer. These prototypes define the pixel-level visual design. Developers should read the prototype source files (not render them) to extract exact dimensions, colors, spacing, and layout rules. The prototypes are the visual source of truth; this document is the behavioral and functional source of truth.

### 1.1 Design Language

- **Aesthetic:** Modern minimal, clean, similar to a fintech app
- **Theme:** Dark mode by default with optional light mode toggle
- **Color system:** oklch color space throughout. See `ui-handoff/pi-drive/project/pd-tokens.jsx` for the full token set.
- **Dark theme palette:**
  - Background: `oklch(0.155 0.005 60)` (near-black, slight warm)
  - Card surface: `oklch(0.195 0.005 60)`
  - Elevated surface: `oklch(0.215 0.005 60)`
  - Input/hover: `oklch(0.255 0.005 60)`
  - Borders: `oklch(0.30 0.005 60)` (standard), `oklch(0.24 0.005 60)` (subtle)
  - Text: `oklch(0.97 0.005 80)` (primary), `oklch(0.70 0.005 70)` (muted), `oklch(0.50 0.005 70)` (dim)
  - Semantic: danger `oklch(0.66 0.20 25)`, success `oklch(0.74 0.16 150)`, warn `oklch(0.80 0.15 80)`
- **Accent colors:** Four user-selectable options:
  1. Warm orange `oklch(0.72 0.17 55)` (default)
  2. Red `oklch(0.65 0.21 22)`
  3. Yellow `oklch(0.80 0.16 75)`
  4. Blue-teal `oklch(0.78 0.13 210)`
- Each accent has three variants: `base`, `soft` (16% opacity for backgrounds), `strong` (brighter for emphasis).
- **Typography:**
  - Primary: Geist (system-ui fallback)
  - Monospace: Geist Mono (ui-monospace, JetBrains Mono fallback)
  - Large metric values use Geist Mono with `tabular-nums` and tight letter-spacing
- **Branding:** "Pi Drive" — app icon is a `π` glyph in accent color on dark background, rounded square
- **Phone size target:** 360 × 800 logical pixels (Pixel-style Android)
- **Android Auto orientation:** Landscape, 880 × 500 outer frame (head-unit bezel)
- **Component library:** See `ui-handoff/pi-drive/project/pd-primitives.jsx` for the full set of reusable UI atoms (Icon, PDCard, PDRow, PDToggle, PDSlider, PDButton, PDPill, PDReadout, PDDial, PDBar, PDLine, etc.)

---

## 2. Core Features

| # | Feature | Priority |
|---|---|---|
| F1 | Bluetooth dongle connection (OBDLink LX) | Must have |
| F2 | Phone live dashboard (live data) | Must have |
| F3 | Android Auto dashboard (dials + graphs + split-screen) | Must have |
| F4 | Real-time server telemetry | Must have |
| F5 | Offline data recording + deferred upload | Must have |
| F6 | Acceleration & G-Force detection with alerts | Must have |
| F7 | Driving thresholds & alerts | Must have |
| F8 | Manual trip counter | Must have |
| F9 | Auto-detected trip tracking | Must have |
| F10 | Trip history + CSV export | Must have |
| F11 | Settings screens (server, layout, thresholds, general) | Must have |

---

## 3. Metric Inventory

The app defines a canonical set of 16 metrics used across all screens, settings, and telemetry. Each metric has a unique ID, display label, unit, icon, and data source identifier.

| ID | Label | Unit | Source | Notes |
|---|---|---|---|---|
| `speed` | Speed | mph | OBD PID `0D` | |
| `mpg` | MPG · instant | mpg | Calculated | From fuel rate or MAF |
| `mpgTrip` | MPG · trip avg | mpg | Calculated | Current auto-trip average |
| `mpgManual` | MPG · manual avg | mpg | Calculated | Manual trip average |
| `rpm` | RPM | rpm | OBD PID `0C` | |
| `throttle` | Throttle | % | OBD PID `11` | |
| `coolant` | Coolant temp | °F | OBD PID `05` | |
| `intake` | Intake temp | °F | OBD PID `0F` | |
| `oilTemp` | Oil temp | °F | OBD PID `5C` | |
| `battery` | Battery | V | AT command `ATRV` | |
| `fuel` | Fuel level | % | OBD PID `2F` | |
| `maf` | MAF air flow | g/s | OBD PID `10` | |
| `gforce` | G-force | g | Phone sensor | Accelerometer-based |
| `accel` | Linear accel | m/s² | Sensor fusion | OBD + GPS + accelerometer |
| `distance` | Trip distance | mi | Calculated | Current auto-trip |
| `manualTrip` | Manual trip | mi | Calculated | Manual trip accumulator |

All metrics use the same live data stream regardless of which screen (phone, AA, split) is displaying them. Metric selection and assignment are configured independently per screen in Settings.

---

## 4. Screens

### 4.1 Phone Screens

The phone app uses bottom tab navigation with three tabs: **Live**, **Trips**, **Settings**.

| Tab | Screen | Route | Purpose |
|---|---|---|---|
| Live | **Live dashboard** | `home` | Live data display with featured metric, MPG row, and configurable tile grid |
| Live | **Connect to dongle** | `connect/*` | 3-step Bluetooth pairing and initialization flow (full-screen, no bottom nav) |
| Trips | **Trip history** | `trips` | Weekly summary, day-grouped trip list with sync status |
| Settings | **Settings root** | `settings` | Vehicle card, section links to sub-screens |
| Settings | **Telemetry server** | `settings/server` | Server URL, API key, streaming config, signal selection |
| Settings | **Phone home layout** | `settings/home-layout` | Choose featured metric, configure tile grid |
| Settings | **Android Auto layout** | `settings/aa-layout` | Configure dials, graphs, and split-screen widget assignments |
| Settings | **Thresholds** | `settings/thresholds` | Acceleration, G-Force, speed, RPM thresholds and alert behavior |

The top bar shows the current screen title, a back button on sub-screens, and a contextual action on the right (LIVE pill on the dashboard, filter icon on trips).

### 4.2 Android Auto Screens

| Screen | Purpose |
|---|---|
| **Screen 1 -- Dials** | Three large arc gauges (speed, RPM, coolant) + bottom stat strip |
| **Screen 2 -- Graphs** | Two stacked line graphs (throttle, g-force) + stat boxes (MPG, manual trip) |
| **Split-screen panel** | ⅓-width panel alongside navigation app; two swipeable pages |

The user swipes between Screen 1 and Screen 2, or taps labeled "Dials" / "Graphs" buttons in the AA chrome bar. In split-screen mode, Android Auto displays a separate ⅓-width pi-drive panel alongside the navigation app.

---

## 5. Feature Specifications

### 5.1 Bluetooth Dongle Connection (F1)

**Scope:** Single dongle pairing only. The app supports one OBDLink LX adapter at a time.

#### 5.1.1 Connection Flow

The connect screen is a **3-step state machine** presented as a full-screen flow (no bottom navigation):

**Step 1 — Scan (route: `connect/scan`):**
1. A pulsing Bluetooth animation plays at the top center while scanning.
2. Discovered devices appear in a scrollable list. Each device row shows:
   - Device name (bold)
   - Signal strength as RSSI bars (1-4 bars icon)
   - Badge: "Paired" (if already OS-paired), "not OBD" (if identified as non-OBD device)
3. Tapping a device proceeds to Step 2.
4. A "Pair a new device" link at the bottom opens Android Bluetooth settings for devices not yet OS-paired.

**Step 2 — Pair (route: `connect/pair`):**
1. The screen shows a step-by-step initialization checklist. Each step shows a label, and a status icon (spinner while in progress, checkmark when complete):
   - RFCOMM socket (SPP UUID `00001101-0000-1000-8000-00805F9B34FB`)
   - ATZ (reset)
   - ATE0 / ATL0 / ATS0 / ATH0 (echo off, linefeeds off, spaces off, headers off)
   - ATSP 0 (auto-detect protocol)
   - PID support bitmask (query `0100`, `0120`, `0140` etc.)
   - VIN query (service 09, PID 02)
2. An overall progress bar or elapsed time indicator shows at the top.
3. On failure at any step, the step turns red with an error message and a "Retry" button.

**Step 3 — Done (route: `connect/done`):**
1. A success state showing:
   - Vehicle make, model, and year (decoded from VIN)
   - Masked VIN (e.g., "JF1VA ••••• 1G862045")
   - Count of supported signals (e.g., "13 of 16 signals supported")
   - Detected OBD protocol name (e.g., "ISO 15765-4 CAN")
2. A "Go to dashboard" primary button that navigates to the live dashboard.

#### 5.1.2 Connection States

| State | UI Indicator |
|---|---|
| **Disconnected** | Gray icon, "Not connected" label, tappable banner navigates to connect flow |
| **Connecting** | Animated spinner, "Connecting..." |
| **Connected** | Green dot, adapter name + short ID (last 4 of MAC) + protocol + poll rate shown in banner |
| **Error** | Red icon, error message, retry button |

#### 5.1.3 Connection Banner on Dashboard

When connected, the dashboard shows a tappable connection banner at the top:
- Left: Bluetooth icon + green dot
- Center: Adapter name (e.g., "OBDLink LX") + protocol (e.g., "CAN 500k") + poll rate (e.g., "13 × 2.4 Hz")
- Right: Chevron (tappable to navigate to connect/settings)

Tapping the banner navigates to the connection flow or adapter details.

#### 5.1.4 Auto-Reconnect

When the Bluetooth connection drops (e.g., adapter sleeps on ignition off, phone goes out of range):
- Attempt reconnection automatically every 10 seconds for 5 minutes.
- After 5 minutes, stop retrying and show a "Reconnect" button.
- When the adapter wakes (ignition on), the app should reconnect within one poll cycle.

#### 5.1.5 Permissions

The app requests at runtime:
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `ACCESS_FINE_LOCATION` (required for Bluetooth on Android)

---

### 5.2 Phone Live Dashboard (F2)

The live dashboard displays real-time vehicle data in a structured layout with three zones: a featured metric, an MPG row, and a tile grid.

#### 5.2.1 Featured Metric

The top section prominently displays one metric (default: speed) with:
- Value in large monospace font (76px, Geist Mono, tabular-nums)
- Unit label to the right
- A "LIVE" pill badge (green, with dot)
- A sparkline graph below the value showing recent history (30s rolling window)
- Metric label (e.g., "Speed") in small uppercase muted text above

The featured metric is user-configurable in Settings > Phone Home Layout.

#### 5.2.2 MPG Row

Below the featured metric, a 3-column row displays fuel economy data:

| Column | Label | Value | Notes |
|---|---|---|---|
| Left | MPG · instant | Current calculated MPG | Accent color, large monospace font |
| Center | MPG · trip avg | Auto-trip average MPG | Standard color |
| Right | MPG · manual | Manual trip average MPG | Includes inline "Reset" button |

Each column shows a small uppercase label and the value below in monospace. A calculation method indicator (e.g., "calc · MAF" or "calc · fuel rate") is shown in dim text.

#### 5.2.3 Tile Grid

Below the MPG row, a scrollable **2-column grid** of metric tiles. Each tile is a card displaying one metric with a visualization type appropriate to the data:

| Default Tile | Metric | Widget Type |
|---|---|---|
| 1 | RPM | **Dial** — 270° arc gauge with tick marks, value centered |
| 2 | Throttle | **Bar** — horizontal fill bar with percentage |
| 3 | Coolant temp | **Bar** — horizontal fill bar with temperature value |
| 4 | Battery voltage | **Number** — large monospace numeric readout |
| 5 | Fuel level | **Bar** — horizontal fill bar with percentage |
| 6 | G-force | **XY** — 2D crosshair plot showing lateral and longitudinal forces |

Widget types available: Dial, Bar, Number, XY (crosshair).

#### 5.2.4 Bottom Status Banner

A persistent banner at the bottom of the dashboard shows:
- Recording status: "RECORDING" pill (if a trip is active) with elapsed time
- Sync status: "LIVE" pill (streaming to server), "QUEUED" pill (buffered), or synced indicator

#### 5.2.5 Customization

- The user can change the **featured metric** from Settings > Phone Home Layout.
- The user can **add, remove, and reorder tiles** in the grid via long-press drag or the layout settings screen.
- Each tile's **widget type** (Dial, Bar, Number, XY) can be changed.
- The order and visibility are persisted locally.
- Metrics for unsupported PIDs (detected at connection time via PID support bitmask) are automatically hidden and grayed out in the selection screen.

---

### 5.3 Android Auto Dashboard (F3)

The Android Auto interface uses the Android for Cars App Library (`androidx.car.app`) and registers as an **IoT** category app.

#### 5.3.1 AA Chrome Bar

The top chrome bar (56px height) contains:
- **App brand:** π icon (28×28, accent color background) + "pi-drive" text
- **Screen selector:** Two pill buttons labeled "Dials" and "Graphs" with dots indicating the active screen, plus a "swipe" affordance label
- **Status indicators** (right side): Bluetooth icon + adapter name, cloud icon + "Streaming" label (green when connected), current time in monospace

#### 5.3.2 Screen 1 -- Dials

Three large arc gauges displayed in a 3-column grid:

| Position | Metric | Size | Notes |
|---|---|---|---|
| Left | **Speed** (primary) | 200px dial, 76px value font | Primary flag — largest value display |
| Center | **RPM** | 200px dial, 56px value font | Warning threshold at 6500 RPM (arc turns danger color) |
| Right | **Coolant** | 200px dial, 56px value font | Range 120-240°F |

Each dial is a 270° arc with:
- Track background in surface color, filled arc in accent (or danger when threshold exceeded)
- 11 tick marks around the arc
- Value centered inside in monospace with unit label below
- Metric label in small uppercase at top-left of the dial card

**Bottom stat strip:** A horizontal bar below the dials showing:

| Stat | Icon | Label | Value | Style |
|---|---|---|---|---|
| Trip distance | pin | Trip | 14.3 mi | standard |
| Manual trip | refresh | Manual | 248.6 mi | monospace |
| Trip MPG | fuel | MPG · trip | 26.4 | monospace |
| Instant MPG | fuel | MPG · now | live value | monospace, **accent color** |
| Battery | battery | Battery | 14.2 V | standard |
| — | — | — | STREAMING | green pill with dot |

Stats are separated by 1px vertical dividers.

#### 5.3.3 Screen 2 -- Graphs

Layout: 2/3 left column with two stacked graphs, 1/3 right column with two stat boxes.

**Left column — graphs:**

| Position | Metric | Notes |
|---|---|---|
| Top | **Throttle** (%) | Standard line graph, 0-100% range, gradient area fill |
| Bottom | **G-force (lateral)** (g) | Symmetric graph with zero midline, range auto-scales to peak |

Each graph card shows:
- Label in uppercase muted text (top-left)
- Current value in large monospace (top-right) with unit
- SVG line graph with accent-colored stroke, gradient area fill under the line
- Grid lines (3 horizontal dashed lines)
- Current-value dot at the end of the line with glow ring
- Responsive width (uses ResizeObserver)

**Right column — stat boxes:**

| Position | Content |
|---|---|
| Top | **MPG box:** Instant MPG in large accent monospace (48px), calculation method label ("calc · MAF"), trip average MPG below a divider |
| Bottom | **Manual trip box:** Distance in large monospace (44px) + unit, subtitle "since May 18 · 31.4 mpg avg", Reset button (pill, uppercase) |

#### 5.3.4 Threshold Lines on Graphs

When an acceleration or g-force graph is displayed, it renders horizontal threshold lines at the user-configured values:

- **Hard acceleration line:** Positive axis, accent color, labeled
- **Hard brake line:** Negative axis, danger color, labeled
- Lines are drawn as dashed/dotted horizontals spanning the full width
- If the plotted line crosses a threshold, the portion beyond renders in warning color
- Threshold lines update live if the user changes values in Settings > Thresholds

#### 5.3.5 Navigation Between Screens

The user swipes left/right or taps the "Dials" / "Graphs" pill buttons in the chrome bar. The transition is a horizontal slide animation (0.45s cubic-bezier easing). Implementation uses the Car App Library's `Screen` stack or `TabTemplate`.

#### 5.3.6 Split-Screen Mode

On displays 6" and larger, Android Auto supports split-screen. Pi-drive renders a dedicated **⅓-width side panel** alongside the navigation app (which occupies ⅔ width). Each app controls only its own column header.

**Side panel header (44px):**
- π icon (22×22) + "pi-drive" text
- Page indicator dots (two pages, animated width change on selection)
- Status icons: Bluetooth + cloud upload (green when streaming)

**Page 1 — Hero layout (fixed structure, configurable metrics):**

The layout has 6 configurable slots in a fixed arrangement:

| Slot | Position | Default Metric | Rendering |
|---|---|---|---|
| Hero | Top, full width | `mpg` (instant) | Large readout (44px monospace), accent color |
| Top-left pill | Below hero, left | `mpgTrip` | Compact: label + value (20px mono) |
| Top-right pill | Below hero, right | `mpgManual` | Compact: label + value |
| Bottom-left pill | Below pills, left | `distance` | Compact: label + value |
| Bottom-right pill | Below pills, right | `manualTrip` | Compact: label + value |
| Graph | Bottom, full width, flex height | `accel` | Rolling line graph with time axis (-30s to now) |

Above the hero slot: a fixed status strip showing dongle connection (short MAC ID, green dot) and server status (cloud icon, sample rate "30 Hz").

**Page 2 — Tile grid (configurable metrics):**

A 2-column × 3-row grid of compact metric tiles. Each tile shows an icon, label, and value.

| Default Position | Metric |
|---|---|
| 1 (top-left) | RPM |
| 2 (top-right) | Coolant temp |
| 3 (mid-left) | Throttle |
| 4 (mid-right) | Fuel level |
| 5 (bottom-left) | Battery |
| 6 (bottom-right) | Oil temp |

The user swipes between Page 1 and Page 2 within the ⅓ panel.

#### 5.3.7 Alerts on Android Auto

Driving behavior alerts (hard brake, hard acceleration, speed limit) and vehicle health alerts display as `CarToast` notifications on the Android Auto screen. These are non-blocking and dismiss automatically. This behavior is togglable in Settings > Thresholds > "CarToast on Android Auto."

---

### 5.4 Server Telemetry (F4, F5)

The app supports configurable streaming and buffering behavior via independent toggles (not a single mode picker).

#### 5.4.1 Server Configuration

The server config screen (`settings/server`) has four sections:

**Endpoint:**

| Field | Type | Notes |
|---|---|---|
| Server URL | Text input | Validated as HTTPS URL on save |
| Device ID | Text (read-only or editable) | Unique identifier for this device (e.g., "pd-rxv7a3-k9892") |
| API key | Text input (masked) | Shows last 4 characters visible; a "VERIFIED" pill badge when validated |

**Connection health card:**
- Shows connection status (healthy/unhealthy), round-trip latency (ms), time since last sync
- "Test" button to send a test POST and verify connectivity

**Streaming toggles:**

| Toggle | Default | Description |
|---|---|---|
| Stream live while driving | On | POST data to server in real time |
| Buffer when offline | On | Write to local storage when server unreachable; upload when reconnected |
| Upload on Wi-Fi only | Off | Skip cellular for queued uploads |
| Compress payloads | On | zstd compression (~3.4× smaller) |

**Sample rate:**
- Slider from 1 to 60 Hz with continuous adjustment
- Quick-select preset buttons: 1, 5, 10, 30, 60 Hz
- Default: 30 Hz

#### 5.4.2 Signal Selection

The server config screen shows all signals organized by category, each as a toggle chip:

**OBD · live PIDs:** Speed (0D), RPM (0C), Throttle (11), Coolant (05), Intake (0F), Oil temp (5C), MAF (10), Fuel rate (5E), Fuel level (2F), Battery (ATRV)

**Calculated · phone-side:** Fuel economy MPG, Fuel economy km/L, Accel (m/s²), Trip distance, Manual trip, Driving events

**Phone sensors:** GPS lat/lng + speed, Accelerometer XYZ

An info note indicates when a PID is unsupported by the vehicle (e.g., "Fuel rate PID (5E) isn't supported on this vehicle — MPG is calculated from MAF").

#### 5.4.3 Payload

Each POST sends a JSON object containing:
- Timestamp (ISO 8601)
- Device ID
- VIN (queried once at connection via OBD service 09, PID 02)
- GPS location (lat, lng, GPS speed)
- All enabled OBD readings
- Calculated values (fuel economy, acceleration rate, g-force)
- Any driving events (acceleration/g-force alerts) since the last upload
- Current manual trip summary (if active)

See `README.md` for the full payload schema.

#### 5.4.4 Offline Storage

- Data is written to a Room database.
- Each row contains one timestamped snapshot of all active metrics.
- The database respects the user's data retention setting (see 5.10.3).
- The user can trigger a manual "Upload All" from the settings screen.
- `WorkManager` handles background upload with retry and backoff.

---

### 5.5 Acceleration Detection (F6) -- "Acceleration" Strategy

Detects hard acceleration and hard braking based on the **rate of speed change** in **mph/s** (or km/h per second, matching the user's unit preference).

#### 5.5.1 Data Sources

- **Primary:** OBD-II vehicle speed (PID `0D`), converted to mph.
- **Fallback:** GPS speed (`Location.getSpeed()`), used if OBD data is stale (> 500 ms old).

Data source chips shown on the threshold settings card: "OBD speed (PID 0D)", "GPS speed".

#### 5.5.2 Thresholds

| Setting | Default | Range | Unit |
|---|---|---|---|
| Hard acceleration threshold | 9 | 3–20 | mph/s |
| Hard braking threshold | 6.5 | 3–20 | mph/s |
| Minimum event duration | 0.5 | 0.2–2.0 | seconds |

Thresholds are user-configurable via sliders in Settings > Thresholds. The hard braking slider shows a contextual count badge (e.g., "4× this week") when events have been recorded recently.

#### 5.5.3 Event Trigger

An event fires when the speed change rate exceeds the threshold for at least the minimum duration continuously. This prevents momentary bumps or sensor noise from firing false events.

---

### 5.6 G-Force Detection (F6) -- "G-Force" Strategy

Detects hard acceleration and hard braking based on **g-force** (multiples of 9.81 m/s²) using sensor fusion.

#### 5.6.1 Data Sources

- OBD-II speed deltas (PID `0D`)
- GPS speed deltas (`Location.getSpeed()`)
- Phone accelerometer (`TYPE_LINEAR_ACCELERATION`)

Data source chips shown on the threshold settings card: "OBD speed", "GPS speed", "TYPE_LINEAR_ACCELERATION".

#### 5.6.2 Cross-Validation

An event fires only when **at least 2 of 3 sources** agree that the threshold has been exceeded. This eliminates false positives from phone drops, GPS jumps, or OBD polling gaps.

#### 5.6.3 Accelerometer Calibration

On first enable, the app prompts the user to calibrate:
1. Mount the phone in the car.
2. Drive straight and accelerate briefly.
3. The app identifies the longitudinal axis and stores the mapping.

When G-Force is enabled but not calibrated, a warning banner appears on the strategy card: "Calibration needed" with a "Calibrate now" action button. Calibration can be re-run from Settings > Thresholds.

#### 5.6.4 Thresholds

| Setting | Default | Range | Unit |
|---|---|---|---|
| Hard acceleration threshold | 0.22 | 0.10–0.80 | g |
| Hard braking threshold | 0.265 | 0.010–0.080 | g |
| Severe braking threshold | 0.50 | 0.30–1.00 | g |
| Minimum event duration | 0.5 | 0.2–2.0 | seconds |

The severe braking threshold is a second-tier alert described as "Predictive of crash risk."

Thresholds are user-configurable via sliders in Settings > Thresholds.

---

### 5.7 Driving Thresholds & Alerts (F7)

The thresholds screen consolidates acceleration detection, speed/RPM alerts, and alert response behavior into a single settings screen.

#### 5.7.1 Strategy Cards

Acceleration and G-Force are presented as two independent "strategy cards." Each card has:
- Title, unit badge, subtitle description
- On/off toggle
- Source chips listing data inputs
- Optional warning banner (e.g., calibration needed)
- Slider controls for thresholds (shown only when the strategy is enabled)
- Visual treatment: bordered card with accent glow ring when enabled

#### 5.7.2 Speed & RPM Alerts

| Alert | Type | Default | Range |
|---|---|---|---|
| Speed limit alert | Toggle + slider | 75 mph | 25–130 mph |
| High RPM | Slider | 6500 RPM | 3000–9000 RPM |

The speed limit alert has a toggle to enable/disable. When enabled, a slider appears to set the threshold.

#### 5.7.3 Alert Response Options

When any threshold is triggered, the app can respond with one or more of these actions:

| Action | Default | Description |
|---|---|---|
| Sound alert | Off | Plays a short beep on the phone |
| Haptic feedback | On | Short vibration on the phone |
| CarToast on Android Auto | On | Banner alert on the head unit |
| Flag event in stream | Always on | Tags telemetry upload with event marker |

All response options (except event flagging, which is always on) are individually togglable in Settings > Thresholds > "When triggered."

#### 5.7.4 Vehicle Health Alerts

Configurable alerts for monitored values are integrated into the thresholds system. Alerts appear on both the phone and Android Auto (`CarToast`).

| Alert | Condition | Default Threshold | Enabled by Default |
|---|---|---|---|
| High coolant temp | Coolant temp > threshold | 230°F / 110°C | Yes |
| Low fuel | Fuel level < threshold | 10% | Yes |
| High RPM | RPM > threshold | 6500 RPM | Yes |
| Overspeed | Speed > threshold | 75 mph | No |
| Low battery voltage | Voltage < threshold | 11.5 V | No |

Each alert has: **enabled** (toggle), **threshold** (numeric), and **cooldown** (seconds before the same alert can fire again, default 60s). Alerts for PIDs unsupported by the connected vehicle are automatically disabled.

Driving events and health alerts are included in the server telemetry payload as events (with strategy type and severity level).

---

### 5.8 Manual Trip Counter (F8)

A persistent, user-controlled trip meter that tracks cumulative stats across driving sessions (including engine restarts and app restarts).

#### 5.8.1 Lifecycle

1. **Start/Reset:** User taps "Reset" from the MPG row on the dashboard, the manual trip card in settings, or the AA manual trip stat box.
2. **Recording:** While the OBD connection is active and speed > 0, the trip accumulates distance, duration, and speed samples.
3. **Auto-pause on ignition off:** When the OBD connection drops (adapter sleeps on ignition off, detected by loss of data or voltage drop below threshold), the trip **pauses**. Duration and distance stop accumulating.
4. **Auto-resume on ignition on:** When the OBD connection re-establishes, the trip resumes from where it paused.
5. **Persists across reboots:** Trip state is saved to the Room database. Killing and restarting the app does not lose the trip.
6. **Stop:** The trip continues indefinitely until the user explicitly resets it. There is no auto-stop.

#### 5.8.2 Tracked Metrics

| Metric | How calculated | Unit |
|---|---|---|
| Distance | Accumulated from speed × time deltas | mi or km |
| Duration | Wall clock time while recording (excludes paused time) | hh:mm:ss |
| Average speed | Distance / duration | mph or km/h |
| Max speed | Highest instantaneous speed observed | mph or km/h |
| Average fuel economy | Accumulated fuel consumption / distance (or MAF-derived) | MPG or km/L |

#### 5.8.3 Display

- **Dashboard MPG row:** Manual trip average MPG shown in the right column with an inline "Reset" button.
- **Settings root:** Manual trip row shows total distance + start date, with "Reset" button.
- **AA Screen 1:** Manual trip distance shown in the bottom stat strip (monospace).
- **AA Screen 2:** Manual trip box in the right column showing distance, start date, average MPG, and "Reset" button.
- **AA Split Page 1:** Manual trip as a pill slot (default bottom-right position).

---

### 5.9 Auto-Detected Trips (F9)

The app automatically detects individual driving sessions based on ignition state.

#### 5.9.1 Trip Boundaries

- A new auto trip **starts** when the OBD connection is established and the app receives valid data.
- The auto trip **ends** when the OBD connection is lost for longer than a configurable timeout (default: 5 minutes). Short connection drops (< timeout) are treated as pauses within the same trip.

#### 5.9.2 Coexistence with Manual Trip

Auto-detected trips and the manual trip run **independently and simultaneously**:

- The manual trip is a single long-running accumulator (e.g., "road trip from LA to Vegas over 3 days").
- Auto trips are discrete sessions (e.g., "Tuesday morning commute," "Tuesday evening commute").
- A manual trip may span many auto trips.
- Each auto trip tracks the same metrics as a manual trip (distance, duration, avg speed, max speed, fuel economy).

#### 5.9.3 Storage

Auto trips are saved to the Room database with start/end timestamps, a trip summary, and a reference to the raw telemetry data (if offline recording is enabled).

---

### 5.10 Trip History + Export (F10)

#### 5.10.1 Trip List Screen

The trip history screen (`trips` tab) shows:

**Weekly summary card (top):**
A card summarizing the current week's driving with four stats:
- Total distance (e.g., "143.6 mi")
- Drive time (e.g., "4h 12m")
- Average MPG (e.g., "28.3")
- Hard brakes count (e.g., "4")

**Day-grouped trip list:**
Trips are grouped by date with day headers (e.g., "Today — May 22", "Yesterday — May 21"). Each trip card shows:

| Element | Description |
|---|---|
| Route icon | Small SVG polyline showing a simplified route shape |
| Title | Auto-generated or user-editable (e.g., "Morning commute") |
| Time | Start time (e.g., "8:12 AM") |
| Duration | Trip length (e.g., "24 min") |
| Distance | Total distance (e.g., "14.3 mi") |
| Max speed | Peak speed during trip (e.g., "72 mph max") |
| Sync status | Pill badge: "LIVE" (green, streaming), "QUEUED" (amber, waiting to upload), or synced timestamp (dim) |
| Warning flags | Badge count of threshold violations during the trip (e.g., "2" in danger color) |

A filter icon in the top bar allows filtering trips.

#### 5.10.2 CSV Export

The user can export any individual trip as a CSV file. The CSV contains one row per data sample at the OBD poll rate, with columns for:

```
timestamp, lat, lng, speed_mph, rpm, coolant_temp_f, throttle_pct, fuel_level_pct, oil_temp_f, maf_gps, fuel_rate, battery_v, accel_mph_s, g_force
```

Export is triggered via a share button on the trip detail screen, using Android's `ShareSheet` (share to Files, email, Drive, etc.).

#### 5.10.3 Data Retention

The user configures retention in Settings (Privacy & retention):

| Option | Behavior |
|---|---|
| 30 days | Auto-delete trips older than 30 days |
| 60 days | Auto-delete trips older than 60 days |
| 90 days | Auto-delete trips older than 90 days |
| Unlimited | Never auto-delete |

Default: **90 days.** Deletion runs once per app launch. Manual trips that are still active are never auto-deleted.

---

### 5.11 Settings Screens (F11)

#### 5.11.1 Settings Root

The settings root screen shows a vehicle card at the top and grouped setting rows below.

**Vehicle card:**
- Vehicle avatar icon (car icon in rounded square)
- Vehicle name (e.g., "2019 Subaru WRX")
- Adapter name + short MAC ID + connection status (e.g., "OBDLink LX · 9F4C · connected")
- LIVE status pill (green)
- Divider, then "Pair a new dongle" link in accent color

**Section: Data & Display**
| Row | Subtitle | Navigates to |
|---|---|---|
| Phone home layout | "7 tiles · speed & MPG featured" | `settings/home-layout` |
| Android Auto layout | "Dials · Graphs · swipeable" | `settings/aa-layout` |
| Manual trip counter | "248.6 mi · since May 18" | — (inline Reset button) |
| Telemetry stream | "13 signals · 30 Hz" | `settings/server` |

**Section: Cloud & Server**
| Row | Subtitle | Navigates to |
|---|---|---|
| Telemetry server | "fleet.acme.io · authenticated" | `settings/server` |
| Network policy | "Stream on cellular + Wi-Fi" | network settings |
| Privacy & retention | "Keep trips for 90 days" | retention settings |

**Section: Driving alerts**
| Row | Subtitle | Navigates to |
|---|---|---|
| Thresholds | "Hard brake · acceleration · speed" | `settings/thresholds` |
| Diagnostic codes | "2 active · last read 8 min ago" | DTC screen (count pill badge shown) |

**Section: App**
| Row | Subtitle |
|---|---|
| About | "pi-drive · v 0.3.1 · build 412" |
| Reset all settings | Danger style (red text) |

#### 5.11.2 Settings -- Telemetry Server

See section 5.4.1 for full specification of endpoint, streaming toggles, sample rate, and signal selection.

#### 5.11.3 Settings -- Phone Home Layout

**Featured tile selector:**
- Horizontal scrollable row of metric buttons (icon + label)
- Currently featured metric is highlighted with accent border and soft background
- Selecting a metric changes the featured display on the dashboard

**Tile grid editor:**
- 2-column grid showing current tiles
- Each tile shows: metric icon, metric label, drag handle, widget type preview (miniature dial/bar/number/XY), widget type label
- Long-press and drag to reorder
- "Add tile" button (dashed border) to add a new metric tile
- Info note: "Tiles render with the same data stream as Android Auto. Configure independently below."

#### 5.11.4 Settings -- Android Auto Layout

The AA layout editor has three tabs for the three AA screen configurations:

**Tab: Dials (full screen)**
- Miniature 16:9 preview canvas showing current widget layout
- Clickable widget slots in the preview; selected slot is highlighted
- Below the preview: selected widget panel showing Widget type, Data source, Label (editable or "auto")
- "Add widget" and "Reset screen" buttons

**Tab: Graphs (full screen)**
- Same structure as Dials: preview canvas with clickable graph and text slots
- Widget configuration panel

**Tab: Split (⅓ width)**
- Sub-tabs: "Page 1" (Hero + pills + graph) and "Page 2" (2×3 tile grid)
- Miniature 1/3-panel preview with clickable slots
- Below: metric picker grid (3 columns) showing all PD_METRICS
  - Selected metric is highlighted
  - Metrics already used in other slots on the same page are grayed out / disabled
- Info notes: "Page 1 layout is fixed — hero on top, two pill rows, graph at the bottom. Each slot's metric is independently configurable." / "Page 2 is a free 2 × 3 tile grid — any metric in any cell."

For default slot assignments, see sections 5.3.2, 5.3.3, and 5.3.6.

#### 5.11.5 Settings -- Thresholds

See section 5.7 for full specification of acceleration, g-force, speed/RPM alerts, and alert response options.

#### 5.11.6 Settings -- General

| Field | Type | Default |
|---|---|---|
| Theme | Picker: Dark / Light | Dark |
| Accent color | 4-option color picker (see 1.1) | Warm orange |
| Speed units | Picker: mph / km/h | mph |
| Temperature units | Picker: °F / °C | °F |
| Data retention | Picker: 30 / 60 / 90 days / Unlimited | 90 days |
| Auto-trip end timeout | Numeric (minutes) | 5 |
| About / Version | Info | App version, build number, links |

---

### 5.12 Remote Logging & Diagnostics (F12)

The app ships its operational logs to a Grafana **Loki** instance so issues can be searched and diagnosed remotely — especially failures that happen in the car with no laptop attached. This complements (does not replace) the on-device `FileLogger` (logcat-to-file capture + crash handler; see `DEBUGGING.md`).

**Goal:** know the app is working — and especially when it is *not* — by searching logs in Grafana for errors, connection failures, and key lifecycle events, filterable by device and vehicle. Log generously; the bias is toward too much rather than too little. **Do not** ship a log line per telemetry record uploaded — log upload activity in aggregate (batch summaries) only.

#### 5.12.1 Transport

- The app pushes batched log entries in **Loki push API format** (`POST /loki/api/v1/push`, JSON, optional gzip) to a configurable endpoint. The endpoint is a Grafana **Alloy** `loki.source.api` gateway (recommended) or Loki directly — the wire format is identical, so it is a configuration choice.
- Android cannot run the Alloy agent itself, so the app is its own log shipper.
- Auth: optional bearer token header; optional `X-Scope-OrgID` tenant header. Stored alongside the endpoint in encrypted settings.

#### 5.12.2 Offline buffering

- Log entries are written to a Room `pending_logs` table (mirrors `pending_uploads`, see 6.x).
- A `WorkManager` worker ships batches with exponential backoff and reuploads when connectivity returns — identical strategy to telemetry offline upload (5.4.4). If Loki is unreachable, logs are retained (subject to the retention cap) and re-sent later.
- Respects the user's data-retention and Wi-Fi-only preferences.

#### 5.12.3 Label & metadata schema (shared with the server)

Per Loki best practices, **labels are static and low-cardinality**; identifiers go in **structured metadata** (queryable, no stream explosion).

- **Labels:** `app="pi-drive"`, `component` (`mobile` | `automotive`), `level` (`debug|info|warn|error`), `env` (`dev|prod`).
- **Structured metadata:** `tag` (log tag, e.g. `VehicleData`), `device_id`, `vin`, `session_id` (per app-run id), `event` (short key, e.g. `obd_init_complete`, `bt_connect`, `upload_batch`), `thread`.

#### 5.12.4 What to log

Generous on lifecycle and errors; sparse on high-frequency data:

- **Always:** app start/stop, config changes, uncaught exceptions/crashes (reuse the existing crash handler), all `WARN`/`ERROR`.
- **Connection:** BT scan/pair/connect/disconnect, adapter found/lost, every auto-reconnect attempt + outcome.
- **OBD init:** each init-sequence step, the **`supportedPids` count** (the empty-dials smoking gun), VIN read result.
- **Detection/trips/alerts:** hard accel/brake events, trip start/end, health alerts (all sparse — fine to log each).
- **Telemetry uploader:** batch summaries only (`uploaded batch=50, queue=0, latency=120ms`) — **never per record**.

#### 5.12.5 Settings

A "Diagnostics & Logging" section (under Developer/Cloud settings) exposes: remote-logging on/off, Loki endpoint URL, auth token (masked), minimum remote log level, a "Send test log" button (verifies connectivity, like the telemetry Test button), and current `pending_logs` queue depth.

---

## 6. Data Architecture

### 6.1 OBD Polling

The app polls OBD PIDs in a round-robin loop on a background thread:

1. Send PID request (e.g., `010D`).
2. Read response until `>` prompt.
3. Parse hex bytes, apply the PID formula.
4. Update the in-memory data model.
5. Notify UI observers (phone dashboard, Android Auto screen, telemetry service).
6. Repeat with the next PID.

**Target poll rate:** 2-5 Hz for the full PID set (depends on number of enabled PIDs and adapter response time). High-priority PIDs (speed, RPM) should be polled more frequently than low-priority PIDs (fuel level, oil temp).

### 6.2 Data Model

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

### 6.3 Local Database (Room)

| Table | Purpose |
|---|---|
| `snapshots` | Raw telemetry data (one row per poll cycle) |
| `driving_events` | Acceleration, g-force, and health alert events |
| `auto_trips` | Auto-detected trip summaries |
| `manual_trips` | Manual trip state and archived trip summaries |
| `pending_uploads` | Offline telemetry queued for server upload |
| `pending_logs` | Offline log entries queued for Loki push (see 5.12) |

### 6.4 Reactive Data Flow

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

## 7. Non-Functional Requirements

### 7.1 Performance

- OBD polling must not block the UI thread.
- The phone dashboard must update within 100 ms of new data arriving.
- Android Auto screen must update within 250 ms (constrained by Car App Library refresh rates).
- Server uploads must not affect OBD polling cadence.

### 7.2 Battery

- GPS polling at 1 Hz (adequate for speed and location, balanced with battery).
- Accelerometer at SENSOR_DELAY_GAME (~50 Hz) only when G-Force detection is enabled.
- OBD Bluetooth connection uses standard SPP -- no excessive battery drain beyond normal Bluetooth usage.
- The app should release sensors and stop polling when the OBD connection is lost and auto-reconnect attempts are exhausted.

### 7.3 Reliability

- The app must not crash on OBD communication errors (malformed responses, timeouts, adapter resets).
- All OBD communication must include timeout handling (default 2 seconds per command).
- The Bluetooth socket must be managed on a dedicated thread with proper cleanup on disconnect.
- Manual trip state must survive: process death, phone reboot, app update.

### 7.4 Security

- API keys are stored in Android `EncryptedSharedPreferences`.
- Server communication uses HTTPS only. HTTP URLs are rejected in settings validation.
- The OBDLink LX's 128-bit Bluetooth encryption is relied upon for the adapter link.

### 7.5 Compatibility

- **Min SDK:** 34 (Android 14)
- **Target SDK:** 36
- **Android Auto:** Requires Android Auto app on the phone + compatible head unit (or DHU for testing)
- **OBD adapter:** OBDLink LX (STN1155). Other ELM327-compatible adapters may work but are not officially supported.

---

## 8. Out of Scope (v1)

These features are explicitly deferred:

- Support for multiple Bluetooth dongles / multi-vehicle
- Full DTC reading, clearing, or diagnostic flow (the settings screen displays an active DTC count indicator, but a full DTC management UI is deferred)
- Enhanced/proprietary diagnostics (Mode 22)
- OBD data recording for playback/replay
- Social or leaderboard features
- Wear OS companion app
- iOS / cross-platform
- Map display of trip GPS tracks within the app (export to external tools via CSV)
- Widget / persistent notification with live data

---

## 9. Glossary

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
| **g** | Unit of acceleration equal to 9.81 m/s² (standard gravity). |
| **zstd** | Zstandard compression algorithm. Used for compressing telemetry payloads. |
| **oklch** | A perceptual color space (Lightness, Chroma, Hue) used in the design system for consistent color contrast. |
