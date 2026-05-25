# Phase 3: Phone Live Dashboard

**Goal:** Build the main phone screen: featured metric with sparkline, MPG row, and scrollable tile grid. After this phase, the app shows live-updating vehicle data in demo mode.

**Depends on:** Phase 0 (theme + nav), Phase 1 (VehicleDataSource + DemoVehicleDataSource).

**Design reference:** `ui-handoff/pi-drive/project/pd-screens-phone.jsx` -- Live Dashboard section.

---

## Step 3.1 -- Featured Metric + Sparkline

**What to build in `mobile/src/main/java/.../ui/`:**

1. **`components/MetricDisplay.kt`**:
   - `FeaturedMetric` composable: large monospace value (76px, tabular-nums), unit label right-aligned, metric label in uppercase muted text above
   - Parameters: `value: String`, `unit: String`, `label: String`, `isLive: Boolean`

2. **`components/SparklineGraph.kt`**:
   - Canvas-based composable drawing a rolling 30-second line graph
   - Input: `List<Float>` (recent values with timestamps)
   - Draws: accent-colored stroke, gradient fill below, current-value dot at end with glow
   - Handles empty/partial data gracefully

3. **`components/LivePill.kt`**:
   - "LIVE" pill badge: green dot + "LIVE" text, accent background

4. **`screens/LiveDashboardScreen.kt`** (replace placeholder):
   - Top section: FeaturedMetric (default: speed) with SparklineGraph below
   - LivePill in top-right corner
   - Wire to `VehicleDataSource.snapshot` via `collectAsStateWithLifecycle()`
   - ViewModel: `LiveDashboardViewModel` -- collects snapshot, maintains 30s rolling buffer for sparkline, extracts featured metric value

5. **`viewmodel/LiveDashboardViewModel.kt`**:
   - `@HiltViewModel`
   - Injects `VehicleDataSource`
   - Exposes: `featuredValue: StateFlow<String>`, `featuredUnit: StateFlow<String>`, `sparklineData: StateFlow<List<Float>>`, `isLive: StateFlow<Boolean>`
   - Configurable featured metric ID (default SPEED, stored in SavedStateHandle)

**Unit tests:**
- `LiveDashboardViewModelTest.kt`: Inject DemoVehicleDataSource with CRUISE scenario -> collect featuredValue for 2 seconds -> values are non-empty and change over time
- `SparklineGraphTest.kt`: Snapshot test with known data points (if using Compose preview testing)

**Verify:**
- `/pd-run` with CRUISE scenario
- `/pd-screenshot` -> large speed value visible, sparkline animating, LIVE pill shown
- `/pd-logs` -> no errors, "Demo mode active" in logcat

**Estimated size:** ~1.5k lines

---

## Step 3.2 -- MPG Row + Tile Grid

**What to build:**

1. **`components/MpgRow.kt`**:
   - 3-column Row: instant MPG (accent), trip avg MPG, manual avg MPG
   - Each column: small uppercase label, large monospace value, calculation method in dim text
   - Manual column has inline "Reset" button (pill style)

2. **`components/widgets/DialWidget.kt`**:
   - 270-degree arc gauge with tick marks (11 ticks)
   - Track background in surface color, filled arc in accent (danger when threshold exceeded)
   - Value centered inside (monospace), unit below, label top-left
   - Parameters: `value: Float`, `min: Float`, `max: Float`, `label: String`, `unit: String`, `warningThreshold: Float?`

3. **`components/widgets/BarWidget.kt`**:
   - Horizontal fill bar
   - Background in surface, fill in accent
   - Value + unit right-aligned, label left
   - Parameters: `value: Float`, `max: Float`, `label: String`, `unit: String`

4. **`components/widgets/NumberWidget.kt`**:
   - Large monospace readout (centered in card)
   - Label above, unit next to value

5. **`components/widgets/XYWidget.kt`**:
   - 2D crosshair plot (lateral vs longitudinal g-force)
   - Crosshair at center, dot at current position
   - Grid lines at 0.25g intervals

6. **`components/MetricTile.kt`**:
   - PDCard wrapping one of the widget types
   - Parameters: `metricId: MetricId`, `widgetType: WidgetType`, `value: Float?`
   - `WidgetType` enum: `DIAL, BAR, NUMBER, XY`

7. **`components/TileGrid.kt`**:
   - 2-column `LazyVerticalGrid` of `MetricTile`
   - Default 6 tiles: RPM(DIAL), Throttle(BAR), Coolant(BAR), Battery(NUMBER), Fuel(BAR), GForce(XY)

8. **Update `LiveDashboardScreen.kt`**: Add MpgRow below featured metric, TileGrid below that. Scrollable column layout.

9. **Update `LiveDashboardViewModel.kt`**: Expose all metric values needed by MPG row and tiles.

**Unit tests:**
- `MpgRowTest.kt`: Verify MPG formatting (1 decimal place, "---" when null)
- Widget snapshot tests if Compose preview testing is available

**Verify:**
- `/pd-run` with CRUISE scenario
- `/pd-screenshot` -> featured metric + MPG row + 6 tiles all visible and updating
- Scroll down to see all tiles
- `/pd-screenshot` after scrolling -> bottom tiles visible

**Estimated size:** ~2k lines

---

## Step 3.3 -- Connection Banner + Status Bar

**What to build:**

1. **`components/ConnectionBanner.kt`**:
   - Tappable banner at top of dashboard (above featured metric)
   - Connected state: BT icon + green dot, adapter name, protocol, poll rate, chevron
   - Disconnected state: gray icon, "Not connected", tappable to navigate to connect flow
   - Connecting state: animated spinner, "Connecting..."
   - Error state: red icon, error message, retry button
   - Tapping navigates to connect flow (route: `connect/scan`)

2. **`components/StatusBanner.kt`**:
   - Persistent bar at bottom of dashboard (above bottom nav)
   - Recording: "RECORDING" pill with elapsed time
   - Sync: "LIVE" pill (streaming), "QUEUED" pill (buffered), or synced time
   - Compact, single-line

3. **Update `LiveDashboardScreen.kt`**: Insert ConnectionBanner at top, StatusBanner at bottom.

4. **Update `LiveDashboardViewModel.kt`**: Expose `connectionState`, recording status, sync status.

**Unit tests:**
- ViewModel: disconnected state -> banner shows "Not connected"; connected state -> shows adapter info

**Verify:**
- `/pd-run` -> demo mode shows Connected banner (since DemoVehicleDataSource reports connected)
- `/pd-screenshot` -> banner visible at top with green dot, status bar at bottom
- Full dashboard layout matches design reference

**Estimated size:** ~800 lines
