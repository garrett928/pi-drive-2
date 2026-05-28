# Phase 8: Settings Screens

**Goal:** Build all remaining settings screens (root, general, layout editors, thresholds) and the trip history screen. After this phase, all phone screens exist and are functional.

**Depends on:** Phase 3 (dashboard), Phase 5 (detection config), Phase 6 (trips), Phase 7 (server settings).

**Reference:** REQUIREMENTS.md sections 5.10-5.11, `ui-handoff/pi-drive/project/pd-screens-settings.jsx`.

---

## Step 8.1 -- Settings Root + General Settings

**What to build in `mobile/src/main/java/.../ui/screens/settings/`:**

1. **`SettingsRootScreen.kt`** (route: `settings`, replace placeholder):
   - **Vehicle card** (top):
     - Car icon in rounded square
     - Vehicle name (from VIN decode, or "Not connected")
     - Adapter name + short MAC + connection status
     - LIVE pill (when connected)
     - Divider, "Pair a new dongle" link -> navigates to `connect/scan`
   - **Section: Data & Display** (PDRow list):
     - Phone home layout -> `settings/home-layout`
     - Android Auto layout -> `settings/aa-layout`
     - Manual trip counter (inline: distance + date, Reset button)
     - Telemetry stream (subtitle: signal count + Hz)
   - **Section: Cloud & Server**:
     - Telemetry server -> `settings/server`
     - Network policy (inline toggle or sub-screen)
     - Privacy & retention (inline picker)
   - **Section: Driving alerts**:
     - Thresholds -> `settings/thresholds`
     - Diagnostic codes (count badge, deferred UI)
   - **Section: App**:
     - About (version, build number)
     - Reset all settings (danger style, confirmation dialog)

2. **`GeneralSettingsManager.kt`** (in `shared/`):
   - Wraps SharedPreferences for general settings:
     - Theme: dark/light (default dark)
     - Accent color: 1-4 (default 1, warm orange)
     - Speed units: mph/kmh (default mph)
     - Temperature units: F/C (default F)
     - Data retention: 30/60/90/unlimited days (default 90)
     - Auto-trip end timeout: minutes (default 5)
   - Exposes `StateFlow<GeneralSettings>` for reactive updates

3. **Theme integration**: Wire `GeneralSettingsManager.accentColor` to `PiDriveTheme` via `CompositionLocal`. Changing accent color immediately updates the entire UI.

4. **Data retention job**: On app launch, delete auto trips older than retention period via `AutoTripDao.deleteOlderThan()`.

**Unit tests:**
- `GeneralSettingsManagerTest.kt`: Set accent color -> read back -> matches; default values are correct
- Data retention: insert trips at various ages -> run retention -> only old ones deleted

**Verify:**
- `/pd-run` -> navigate to Settings tab
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-settings-root.png` → read image: vehicle card + all section rows visible
- Tap theme -> switch to light; Screenshot: `$ADB shell screencap -p /sdcard/screen2.png && $ADB pull /sdcard/screen2.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-settings-light-theme.png` → read image: light theme applied
- Tap accent color -> switch to red; Screenshot: `$ADB shell screencap -p /sdcard/screen3.png && $ADB pull /sdcard/screen3.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-settings-red-accent.png` → read image: red accent throughout

**Estimated size:** ~1.5k lines

---

## Step 8.2 -- Phone Home Layout Editor

**What to build:**

1. **`PhoneLayoutScreen.kt`** (route: `settings/home-layout`):
   - **Featured tile selector:**
     - Horizontal scrollable row of all 16 MetricId options
     - Each is a pill/chip with icon + short label
     - Current featured metric highlighted with accent border + soft background
     - Tapping selects -> updates featured metric on dashboard
   - **Tile grid editor:**
     - 2-column grid showing current tiles
     - Each tile shows: metric icon, metric label, widget type mini-preview, widget type name
     - Drag handle for reorder (use `LazyVerticalGrid` with reorderable library or custom drag)
     - Tap tile -> bottom sheet to change widget type (Dial/Bar/Number/XY) or metric
     - "Add tile" card (dashed border) at the end
     - Swipe to remove
   - **Info note:** "Unsupported PIDs are grayed out based on your vehicle."

2. **`DashboardLayout.kt`** (data model, in `shared/`):
   ```kotlin
   data class DashboardLayout(
       val featuredMetricId: MetricId = MetricId.SPEED,
       val tiles: List<TileConfig> = DEFAULT_TILES,
   )
   data class TileConfig(
       val metricId: MetricId,
       val widgetType: WidgetType,
   )
   ```
   Persisted to SharedPreferences as JSON.

3. **`DashboardLayoutManager.kt`**: Loads/saves layout, provides defaults, exposes `StateFlow<DashboardLayout>`.

4. **Update `LiveDashboardScreen`**: Read from `DashboardLayoutManager` for featured metric and tile list instead of hardcoded values.

**Unit tests:**
- `DashboardLayoutManagerTest.kt`: Save layout with 4 tiles -> read back -> same 4 tiles; invalid JSON -> fallback to defaults

**Verify:**
- `/pd-run` -> Settings > Phone home layout
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-layout-editor.png` → read image: featured selector + tile grid visible
- Change featured metric to RPM -> back to dashboard; Screenshot: `$ADB shell screencap -p /sdcard/screen2.png && $ADB pull /sdcard/screen2.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-layout-rpm-featured.png` → read image: RPM is now the featured metric
- Remove a tile, add a different one -> back to dashboard -> reflects changes

**Estimated size:** ~1.5k lines

---

## Step 8.3 -- Thresholds Screen

**What to build:**

1. **`ThresholdsScreen.kt`** (route: `settings/thresholds`):
   - **Acceleration strategy card:**
     - Title: "Acceleration", unit badge "mph/s", subtitle
     - Toggle on/off (accent glow ring when enabled)
     - Source chips: "OBD speed (PID 0D)", "GPS speed"
     - Sliders (shown when enabled):
       - Hard acceleration: 3-20 mph/s, default 9
       - Hard braking: 3-20 mph/s, default 6.5
       - Min event duration: 0.2-2.0s, default 0.5
     - Contextual badge: "4x this week" on hard brake slider (from DrivingEventDao count)
   - **G-Force strategy card:**
     - Title: "G-Force", unit badge "g", subtitle
     - Toggle on/off
     - Source chips: "OBD speed", "GPS speed", "TYPE_LINEAR_ACCELERATION"
     - Warning banner when enabled + not calibrated: "Calibration needed" + "Calibrate now" button
     - Sliders (shown when enabled):
       - Hard acceleration: 0.10-0.80g, default 0.22
       - Hard braking: 0.10-0.80g, default 0.265
       - Severe braking: 0.30-1.00g, default 0.50
       - Min event duration: 0.2-2.0s, default 0.5
   - **Speed & RPM section:**
     - Speed limit: toggle + slider 25-130 mph, default 75
     - High RPM: slider 3000-9000, default 6500
   - **When triggered section:**
     - Sound alert: toggle (default off)
     - Haptic feedback: toggle (default on)
     - CarToast on Android Auto: toggle (default on)
     - Flag event in stream: always on (non-toggleable, dimmed)
   - **Vehicle health alerts section:**
     - High coolant temp: toggle + threshold (default 230F, on)
     - Low fuel: toggle + threshold (default 10%, on)
     - Low battery: toggle + threshold (default 11.5V, off)

2. **`ThresholdsViewModel.kt`**: Loads/saves `DetectionConfig` + health alert config, queries recent event counts.

3. **Wire config changes** to `AccelerationDetector`, `GForceDetector`, and `HealthMonitor` via reactive flows.

**Unit tests:**
- ViewModel: change brake threshold -> detector config updates -> next HARD_BRAKE scenario uses new threshold

**Verify:**
- `/pd-run` -> Settings > Thresholds
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-thresholds.png` → read image: both strategy cards visible, sliders functional
- Enable G-Force without calibration -> warning banner shows
- Change hard brake to 4 mph/s -> run HARD_BRAKE scenario -> events fire more frequently

**Estimated size:** ~2k lines

---

## Step 8.4 -- Trip History Screen + CSV Export

**What to build:**

1. **`TripHistoryScreen.kt`** (route: `trips`, replace placeholder):
   - **Weekly summary card** (top):
     - 4 stats in a row: total distance, drive time, avg MPG, hard brakes count
     - Calculated from `AutoTripDao.getByDateRange(startOfWeek, now)`
   - **Day-grouped trip list:**
     - Day headers: "Today -- May 25", "Yesterday -- May 24", etc.
     - Trip cards (PDCard):
       - Route icon (simplified SVG polyline -- can be a static icon initially)
       - Title (auto-generated: "Morning commute" based on time of day, or "Trip" + #)
       - Time: start time (e.g., "8:12 AM")
       - Duration, Distance, Max speed
       - Sync status pill: "LIVE" (green), "QUEUED" (amber), or synced timestamp (dim)
       - Warning badge: count of threshold events during trip
     - `LazyColumn` with sticky headers per day

2. **`TripHistoryViewModel.kt`**:
   - Queries `AutoTripDao.getAll()` (paged via Paging3 or simple Flow)
   - Groups by date
   - Calculates weekly summary
   - Exposes filter state

3. **`TripDetailScreen.kt`** (optional sub-route `trips/{tripId}`):
   - Full trip summary with all stats
   - Event list (hard brakes, etc.)
   - Share/export button

4. **CSV Export:**
   - `CsvExporter.kt`: Queries `SnapshotDao.getByTripId` -> writes CSV with columns: `timestamp, lat, lng, speed_mph, rpm, coolant_temp_f, throttle_pct, fuel_level_pct, oil_temp_f, maf_gps, fuel_rate, battery_v, accel_mph_s, g_force`
   - Uses Android `ShareSheet` (ACTION_SEND with text/csv MIME type)
   - Writes to app's cache directory, provides via `FileProvider`

**Unit tests:**
- `TripHistoryViewModelTest.kt`: 3 trips across 2 days -> grouped correctly, weekly summary computed
- `CsvExporterTest.kt`: 5 snapshots -> CSV has 6 lines (header + 5 rows), columns match spec

**Verify:**
- `/pd-run` CITY scenario (generates multiple short trips via auto-detection)
- Navigate to Trips tab; Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-trip-history.png` → read image: weekly summary + trip list visible
- Trip cards show time, distance, duration
- Tap a trip -> detail view -> share button visible

**Estimated size:** ~2k lines
