# Phase 9: Android Auto

**Goal:** Build all three AA screen modes (dials, graphs, split-screen) using the Car App Library, plus CarToast alerts. After this phase the app works on Android Auto via the Desktop Head Unit.

**Depends on:** Phase 0 (Car App Library scaffold), Phase 1 (VehicleDataSource), Phase 5 (alerts for CarToast), Phase 6 (trips for stat strip).

**Reference:** REQUIREMENTS.md sections 5.3.1-5.3.7, `ui-handoff/pi-drive/project/pd-screens-aa.jsx`, `pd-aa-split.jsx`.

**Important constraint:** The Car App Library uses `Template` objects, not Compose. Screens are built with `GridTemplate`, `ListTemplate`, `MessageTemplate`, etc. Custom rendering is limited. For gauges and graphs, use the `SurfaceContainer` API (if available) or draw via `Canvas` in a `SurfaceCallback`. If SurfaceContainer is not available for IoT apps, use `GridTemplate` with text-based gauge representations.

---

## Step 9.1 -- AA Screen 1: Dials

**What to build in `shared/src/main/java/.../shared/auto/`:**

1. **`DialsScreen.kt`** (extends `Screen`):
   - `onGetTemplate()` returns a `GridTemplate` with gauge data
   - **Three gauge items** (GridItem):
     - Speed: primary, value in large text, unit below
     - RPM: value + "RPM" text, danger color above 6500
     - Coolant: value + "F"/"C", danger color above threshold
   - **Bottom stat strip** via `Header` or additional grid row:
     - Trip distance, Manual trip, Trip MPG, Instant MPG, Battery, STREAMING status
   - Refreshes when `VehicleSnapshot` updates (call `invalidate()`)

2. **`PiDriveCarAppService.kt`** (rename from `MyCarAppService`):
   - Same structure but with proper naming
   - Creates `PiDriveCarAppSession`

3. **`PiDriveCarAppSession.kt`** (rename from `MyCarAppSession`):
   - Returns `DialsScreen` as initial screen
   - Holds reference to `VehicleDataSource` (obtained via service locator pattern since Hilt doesn't inject into Car App Library classes directly -- use a singleton or ServiceLocator)

4. **`AADataBridge.kt`**:
   - Singleton that bridges Hilt-managed `VehicleDataSource` to Car App Library screens
   - Set by the mobile app when the data source is ready
   - Car App screens read from it
   - Exposes: `snapshot: StateFlow<VehicleSnapshot>`, `connectionState`, `manualTripState`, `autoTripState`

5. **Update manifests**: Rename service references, ensure `CarAppService` is registered in both mobile and shared manifests.

**Unit tests:**
- `DialsScreenTest.kt`: Mock AADataBridge with known snapshot -> `onGetTemplate()` returns GridTemplate with correct item count and titles

**Verify (requires DHU):**
- Start emulator + DHU: `adb forward tcp:5277 tcp:5277` then `$ANDROID_HOME/extras/google/auto/desktop-head-unit`
- `/pd-run` in demo mode on phone
- DHU shows the dials screen with speed, RPM, coolant values updating
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-aa-dials.png` → read image: dials screen with speed, RPM, coolant visible on DHU
- `/pd-logs` -> no CarAppService errors

**Estimated size:** ~1.5k lines

---

## Step 9.2 -- AA Screen 2: Graphs

**What to build:**

1. **`GraphsScreen.kt`** (extends `Screen`):
   - Template showing graph data and stat boxes
   - **Left section (graphs):**
     - Throttle graph: current value + label ("THROTTLE · 34%")
     - G-force graph: current value + label ("G-FORCE · 0.12g")
     - Since Car App Library doesn't natively support line graphs in templates, represent as:
       - `ListTemplate` or `GridTemplate` items with current value, min/max, and trend indicator
       - OR use `NavigationTemplate` with `SurfaceCallback` for custom Canvas rendering (if available for IoT category)
   - **Right section (stat boxes):**
     - Instant MPG (large accent value, calc method)
     - Manual trip (distance, start date, avg MPG, Reset action)

2. **`AAScreenManager.kt`**:
   - Manages screen switching between DialsScreen and GraphsScreen
   - Uses `ScreenManager.push()` / `ScreenManager.pop()` or `TabTemplate` (Car App Library 1.7+)
   - If TabTemplate available: two tabs "Dials" and "Graphs"
   - If not: use `ActionStrip` with buttons to switch

3. **Update `PiDriveCarAppSession`**: Start with AAScreenManager, provide both screens.

**Unit tests:**
- `GraphsScreenTest.kt`: Mock data -> template has correct items and values

**Verify (DHU):**
- Swipe or tap to switch between Dials and Graphs screens
- Graphs screen shows throttle/g-force values and MPG stat box
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-aa-graphs.png` → read image: graphs screen with throttle/g-force values and MPG stat box

**Estimated size:** ~1.5k lines

---

## Step 9.3 -- AA Split-Screen Panel

**What to build:**

1. **`SplitPanelScreen.kt`** (extends `Screen`):
   - Renders the 1/3-width side panel for split-screen mode
   - **Page 1 -- Hero layout:**
     - Status strip: dongle connection + server status
     - Hero readout: instant MPG (large accent text)
     - 4 pill slots: mpgTrip, mpgManual, distance, manualTrip
     - Graph slot: rolling accel/g-force line (text representation if Canvas not available)
   - **Page 2 -- Tile grid:**
     - 2x3 grid of compact metric tiles: RPM, coolant, throttle, fuel, battery, oil temp
   - Page switching via `ActionStrip` buttons or by replacing the screen template

2. **`SplitPageManager.kt`**:
   - Tracks which page is shown (1 or 2)
   - `showPage1()` / `showPage2()`: calls `invalidate()` to re-render with different content
   - Uses page indicator in header (dots)

3. **Detection of split-screen mode:**
   - The Car App Library automatically renders your app in split mode when a nav app is also running
   - The template adapts to available space
   - Ensure grid items are concise enough for 1/3 width

**Unit tests:**
- `SplitPanelScreenTest.kt`: Page 1 template has hero item + 4 pills; Page 2 has 6 grid items

**Verify (DHU in wide mode):**
- `desktop-head-unit --screen-width 1920 --screen-height 720 --dpi 160`
- Start Google Maps in DHU, then Pi Drive appears in side panel
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-aa-split-p1.png` → read image: 1/3 panel with hero readout visible
- Switch to Page 2; Screenshot: `$ADB shell screencap -p /sdcard/screen2.png && $ADB pull /sdcard/screen2.png /tmp/pidrive-aa-split-p2.png` → read image: 6 metric tiles visible in 1/3 panel

**Estimated size:** ~1.5k lines

---

## Step 9.4 -- AA Alerts + CarToast

**What to build:**

1. **`AAAlertHandler.kt`**:
   - Subscribes to `AlertManager` event flow
   - On driving event or health alert:
     - If CarToast enabled in settings: show `CarToast.makeText(carContext, message, CarToast.LENGTH_SHORT)`
     - Message format: "Hard braking: 8.2 mph/s" or "High coolant: 235F"
   - Respects cooldown (doesn't spam toasts)

2. **Update `PiDriveCarAppSession`**: Initialize `AAAlertHandler` in `onCreateScreen`.

3. **Wire alert settings toggle** (`CarToast on Android Auto` from thresholds screen) to `AAAlertHandler.enabled`.

**Unit tests:**
- `AAAlertHandlerTest.kt`: DrivingEvent emitted -> CarToast created with correct message; CarToast disabled -> no toast; cooldown active -> no duplicate toast

**Verify (DHU):**
- `/pd-run` with HARD_BRAKE scenario
- Watch DHU -> CarToast appears on hard brake events
- Screenshot during CarToast: `ADB=~/Library/Android/sdk/platform-tools/adb; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-aa-toast.png` → read image: CarToast overlay visible on DHU during hard brake event
- `/pd-logs` -> "CarToast: Hard braking" in logcat
- Disable CarToast in settings -> no more toasts on DHU

**Estimated size:** ~600 lines
