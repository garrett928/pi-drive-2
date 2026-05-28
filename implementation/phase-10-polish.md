# Phase 10: Polish + Integration

**Goal:** Final integration pieces: AA layout settings editor, TCP transport for ELM327 emulator testing, end-to-end integration test suite, and CI pipeline. After this phase the project is feature-complete for v1.

**Depends on:** All prior phases.

---

## Step 10.1 -- Android Auto Layout Settings

**What to build in `mobile/src/main/java/.../ui/screens/settings/`:**

1. **`AALayoutScreen.kt`** (route: `settings/aa-layout`):
   - **Three tabs:** Dials (full screen), Graphs (full screen), Split (1/3 width)
   - **Each tab:**
     - Miniature 16:9 (or 1/3) preview canvas showing current widget layout
     - Clickable widget slots in the preview (selected = highlighted)
     - Below: selected widget panel with:
       - Widget type picker (Dial/Bar/Number/Graph/Text)
       - Data source picker (MetricId)
       - Label (auto or custom text)
     - "Reset screen" button (returns to defaults)
   - **Split tab sub-tabs:** Page 1 (hero + pills + graph) and Page 2 (2x3 grid)
   - **Metric picker grid:** 3-column grid of all MetricId options, grayed out if already used on same page

2. **`AALayoutConfig.kt`** (data model, in `shared/`):
   ```kotlin
   data class AALayoutConfig(
       val dialsSlots: List<AASlotConfig>, // 3 slots + stat strip
       val graphsSlots: List<AASlotConfig>, // 2 graphs + 2 stat boxes
       val splitPage1Slots: List<AASlotConfig>, // hero + 4 pills + graph
       val splitPage2Slots: List<AASlotConfig>, // 6 tiles
   )
   data class AASlotConfig(
       val metricId: MetricId,
       val widgetType: AAWidgetType,
       val label: String? = null, // null = auto from MetricId
   )
   ```
   Persisted to SharedPreferences as JSON.

3. **Wire to AA screens**: DialsScreen, GraphsScreen, SplitPanelScreen read from `AALayoutConfig` instead of hardcoded metrics.

**Unit tests:**
- Layout config: save custom layout -> read -> matches; reset to defaults -> all slots return to spec defaults

**Verify:**
- `/pd-run` -> Settings > Android Auto layout
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-aa-layout-editor.png` → read image: preview canvas + slot editor visible
- Change speed dial to RPM -> DHU shows RPM in first dial position
- Reset -> DHU returns to default layout

**Estimated size:** ~1.5k lines

---

## Step 10.2 -- TcpTransport + ELM327 Emulator Integration

The `TcpTransport` class was created in Step 4.1 alongside `BluetoothTransport`. This step wires it into the full app flow.

**What to build:**

1. **`mobile/.../di/TransportModule.kt`** (update Hilt module):
   - Read intent extras: `tcp_mode`, `tcp_host`, `tcp_port`
   - If `tcp_mode=true`: provide `TcpTransport(host, port)` as `OBDTransport`
   - If `demo_mode=true`: provide `MockTransport`
   - Else: provide `BluetoothTransport` (production)

2. **Developer settings** (optional, in Settings > About):
   - Hidden toggle (tap version 7 times to unlock)
   - TCP mode: host + port inputs
   - Demo mode toggle + scenario picker
   - Saves to SharedPreferences, overrides intent extras

3. **Full ELM327 emulator integration test:**
   - Launch emulator: `python3 -m elm -n 35000 -s car`
   - ADB: `adb reverse tcp:35000 tcp:35000`
   - App: launch with `--ez tcp_mode true --es tcp_host localhost --ei tcp_port 35000`
   - Verify: connect flow completes, dashboard shows live data from emulator
   - Inject values: change PID responses in emulator -> dashboard updates
   - Error handling: kill emulator -> app detects disconnect -> reconnect attempts

**Test criteria (manual, run via `/pd-obd`):**
- `/pd-obd` starts emulator + launches app in TCP mode
- Connect flow completes (all checklist steps green)
- Dashboard shows speed, RPM, coolant from emulator
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-tcp-dashboard.png` → read image: dashboard values match emulator's "car" scenario
- `/pd-logs` -> "TcpTransport connected", PID values logged

**Estimated size:** ~800 lines

---

## Step 10.3 -- End-to-End Integration Testing

**What to build:**

1. **Automated test runner script** (`scripts/e2e-test.sh`):
   ```bash
   #!/bin/bash
   # Runs the full E2E test suite
   # 1. Build
   # 2. Unit tests
   # 3. Install on emulator
   # 4. Launch in demo mode with each scenario
   # 5. Screenshot + logcat verification per scenario
   # 6. Report pass/fail
   ```

2. **Scenario test cases** (instrumented or script-driven):

   | Scenario | What to verify |
   |---|---|
   | CRUISE | Dashboard renders, speed ~60mph, RPM ~2500, all tiles populated |
   | HARD_BRAKE | Alert fires within 15s, logcat shows HARD_BRAKE event, alert overlay visible |
   | LOW_FUEL | Health alert fires when fuel < 10%, alert message correct |
   | COLD_START | Coolant starts low, climbs over time, no premature health alert |
   | DISCONNECT | Connection drops, banner shows reconnecting, trip pauses, connection restores |
   | OVERSPEED | Speed alert fires when > 75 mph (if enabled) |

3. **Instrumented UI tests** (`mobile/src/androidTest/`):
   - `DashboardE2ETest.kt`: Launch demo mode -> verify featured metric exists -> verify tile count = 6
   - `NavigationE2ETest.kt`: Launch -> tap Trips tab -> verify trip list screen -> tap Settings -> verify settings root
   - `ThresholdChangeE2ETest.kt`: Navigate to thresholds -> change brake threshold -> verify persisted

4. **Cross-module tests** (`shared/src/test/`):
   - `FullPipelineTest.kt`: MockTransport -> InitializationSequence -> OBDVehicleDataSource -> collect snapshots -> AccelerationDetector -> verify events
   - This tests the data pipeline end-to-end in JVM (no Android)

**Test criteria:**
- All unit tests pass: `./gradlew :shared:test :mobile:test`
- All instrumented tests pass: `./gradlew :mobile:connectedDebugAndroidTest`
- E2E script: all 6 scenarios produce screenshots + pass criteria
- `/pd-verify` succeeds

**Estimated size:** ~1.5k lines

---

## Step 10.4 -- CI Pipeline (GitHub Actions)

**What to build:**

1. **`.github/workflows/test.yml`**:
   ```yaml
   name: Tests
   on: [push, pull_request]
   jobs:
     unit-tests:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with: { java-version: '17', distribution: 'temurin' }
         - run: ./gradlew :shared:test :mobile:test
         - uses: actions/upload-artifact@v4
           if: failure()
           with:
             name: test-results
             path: '**/build/test-results/**/*.xml'

     integration-tests:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with: { java-version: '17', distribution: 'temurin' }
         - uses: actions/setup-python@v5
           with: { python-version: '3.11' }
         - run: pip install ELM327-emulator
         - run: python3 -m elm -n 35000 -s car &
         - run: sleep 2 && ./gradlew :shared:test -Pobd.test.port=35000

     build:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with: { java-version: '17', distribution: 'temurin' }
         - run: ./gradlew :mobile:assembleDebug :automotive:assembleDebug
         - uses: actions/upload-artifact@v4
           with:
             name: apk
             path: 'mobile/build/outputs/apk/debug/*.apk'
   ```

2. **`.github/workflows/pr.yml`** (optional):
   - Runs on pull_request only
   - Adds lint check: `./gradlew lint`
   - Adds build verification

3. **Update `shared/build.gradle.kts`**: Add test configuration for ELM327 integration tests (conditional on `-Pobd.test.port`).

**Test criteria:**
- Push to a branch -> GitHub Actions runs -> all jobs green
- PR created -> checks pass

**Estimated size:** ~300 lines (YAML + minor Gradle tweaks)
