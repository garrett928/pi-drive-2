# Pi Drive -- Testing Strategy

How to develop and test Pi Drive without a physical OBD-II dongle or vehicle. Covers three phases of increasing fidelity, Android Auto testing via the Desktop Head Unit, and automated test infrastructure.

## TL;DR

| Phase | What it tests | Connection needed | Setup time |
|---|---|---|---|
| **1 — Demo mode** | UI, trips, alerts, server upload, AA screens | None | Minutes |
| **2 — ELM327 emulator** | OBD parsing, AT commands, PID formulas, protocol handling | TCP via ADB | ~15 min |
| **3 — Bluetooth** | Full BT pairing, RFCOMM, reconnection, real-world flow | Bluetooth | Varies |

You do **not** need to be at your car for phases 1 or 2. Phase 3 can be done with a cheap ESP32 board on your desk, or occasionally with the real dongle in the car.

---

## Architecture for Testability

The single most important design decision for testability is **abstracting the transport layer**. The OBD communication logic should never talk directly to a `BluetoothSocket`. Instead, define a transport interface:

```kotlin
interface OBDTransport {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(command: String): String
    val isConnected: StateFlow<Boolean>
}
```

Then provide three implementations:

| Implementation | Use case |
|---|---|
| `BluetoothTransport` | Production — real OBDLink LX over RFCOMM |
| `TcpTransport` | Dev — connects to ELM327 emulator over TCP |
| `MockTransport` | Tests/demo — returns canned or generated responses in-memory |

Inject the transport via Hilt/Dagger. A build flavor or developer setting selects which implementation to use. This lets you test everything from OBD hex parsing to UI rendering without touching Bluetooth.

Similarly, abstract the data source layer above OBD:

```kotlin
interface VehicleDataSource {
    val snapshot: StateFlow<VehicleSnapshot>
    val connectionState: StateFlow<ConnectionState>
    fun startPolling()
    fun stopPolling()
}
```

| Implementation | Use case |
|---|---|
| `OBDVehicleDataSource` | Production — drives a real `OBDTransport` |
| `DemoVehicleDataSource` | Demo mode — generates realistic time-varying data with no transport at all |

This two-layer abstraction gives you flexibility: `MockTransport` tests the OBD parsing pipeline end-to-end, while `DemoVehicleDataSource` skips OBD entirely for fast UI iteration.

---

## Phase 1: Demo Mode (No Connection)

Demo mode generates realistic vehicle data entirely in-memory. No dongle, no emulator, no network. Works on both physical phones and Android emulators.

### What it tests

- All phone screens (dashboard, trips, settings)
- Android Auto screens (dials, graphs, split-screen)
- Acceleration and G-Force detection logic
- Trip accumulation (manual + auto)
- Server telemetry upload (payload format, retry, buffering)
- Threshold alerts and CarToast behavior
- Layout customization (tile grid, featured metric, AA widget assignment)

### What it does NOT test

- Bluetooth pairing and RFCOMM connection
- AT/ST command initialization sequence
- OBD hex response parsing
- PID support bitmask detection
- Real adapter behavior (timeouts, sleep/wake, protocol detection)

### Implementation: `DemoVehicleDataSource`

Generate time-varying mock data that simulates a realistic drive:

```kotlin
class DemoVehicleDataSource : VehicleDataSource {
    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot
    override val connectionState = MutableStateFlow(ConnectionState.CONNECTED)

    private var tick = 0

    fun startPolling() {
        // Emit a new snapshot every 250ms
        coroutineScope.launch {
            while (isActive) {
                delay(250)
                tick++
                _snapshot.value = generateSnapshot(tick)
            }
        }
    }

    private fun generateSnapshot(tick: Int): VehicleSnapshot {
        // Sine-wave oscillations to simulate driving
        val speed = 58 + (sin(tick * 0.07) * 15).toInt()
        val rpm = 2750 + (sin(tick * 0.09) * 400).toInt()
        val throttle = 30f + (sin(tick * 0.06) * 20).toFloat()
        val coolant = 192 + (sin(tick * 0.02) * 3).toInt()
        // ... etc for all metrics
        return VehicleSnapshot(
            timestamp = Instant.now(),
            speedKmh = (speed / 0.621371).toInt(),
            rpm = rpm,
            coolantTempC = (coolant - 32) * 5 / 9,
            throttlePct = throttle,
            // ...
        )
    }
}
```

### Driving scenarios

Define named scenarios that exercise specific app behavior:

| Scenario | Behavior | Tests |
|---|---|---|
| `CRUISE` | Steady ~60 mph, stable RPM, normal temps | Baseline UI rendering, sparklines |
| `CITY` | Speed oscillates 0-45 mph, frequent stops | Auto-trip detection, MPG calculation |
| `HIGHWAY` | 65-80 mph, low RPM, high fuel efficiency | Speed alert threshold, manual trip accumulation |
| `HARD_BRAKE` | Normal driving → sudden deceleration events | Acceleration detection, G-Force cross-validation, alert firing |
| `COLD_START` | Coolant starts at 70°F, climbs to 195°F over 5 min | Health alert (high coolant), temp gauge rendering |
| `LOW_FUEL` | Fuel level drains from 15% to 5% over time | Low fuel health alert |
| `OVERSPEED` | Brief bursts above speed limit threshold | Speed limit alert, event logging |
| `DISCONNECT` | Data stops mid-drive, resumes after 30s | Auto-pause/resume, reconnection banner, trip continuity |

### Activating demo mode

Add a developer option (not exposed to end users in production):

**Option A — Build flavor:**
```kotlin
// In your DI module
@Provides fun provideDataSource(): VehicleDataSource {
    return if (BuildConfig.DEMO_MODE) DemoVehicleDataSource()
    else OBDVehicleDataSource(transport)
}
```

**Option B — Developer settings toggle:**
Add a hidden "Demo mode" toggle in Settings > About (tap version number 7 times to unlock, Android-style). This lets you switch between demo and real mode without rebuilding.

**Option C — ADB flag (recommended for dev):**
```bash
adb shell am start -n ghart.space.pi_drive/.MainActivity --ez demo_mode true
```

This is the most flexible for development — no code changes needed to switch modes.

---

## Phase 2: ELM327 Emulator over TCP

The ELM327 emulator simulates a real OBD-II adapter, responding to AT commands and PID requests with configurable data. This tests the actual OBD protocol layer: command formatting, hex parsing, PID formulas, error handling, and timeout behavior.

### What it tests (beyond Phase 1)

- AT/ST initialization sequence (ATZ, ATE0, ATL0, ATS0, ATH0, ATSP 0)
- PID support bitmask parsing (0100, 0120, 0140 responses)
- VIN decoding (service 09, PID 02)
- Hex response parsing and PID formulas
- Unsupported PID handling ("NO DATA" responses)
- Protocol auto-detection
- Timeout and error recovery

### Setup

**1. Install the emulator on your Mac:**

```bash
pip3 install ELM327-emulator
```

Requires Python 3.6+.

**2. Start the emulator on a TCP port:**

```bash
# Basic — default PID set
python3 -m elm -n 35000

# With the "car" scenario (Toyota Auris Hybrid, realistic PID values)
python3 -m elm -n 35000 -s car
```

The emulator is now listening on `localhost:35000`.

**3. Connect your phone via ADB port forwarding:**

ADB reverse port forwarding makes the Mac's TCP port accessible from the phone as `localhost`:

```bash
# Phone can now reach the emulator at localhost:35000
adb reverse tcp:35000 tcp:35000
```

**4. Tell the app to use TCP transport:**

```bash
adb shell am start -n ghart.space.pi_drive/.MainActivity \
    --ez tcp_mode true \
    --es tcp_host "localhost" \
    --ei tcp_port 35000
```

Or use the developer settings toggle to switch transport to TCP and enter the host/port.

### `TcpTransport` implementation

```kotlin
class TcpTransport(
    private val host: String,
    private val port: Int,
) : OBDTransport {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        socket = Socket(host, port).apply {
            soTimeout = 5000
        }
        reader = socket!!.getInputStream().bufferedReader()
        writer = socket!!.getOutputStream().bufferedWriter()
        // Read the initial prompt
        readUntilPrompt()
    }

    override suspend fun send(command: String): String = withContext(Dispatchers.IO) {
        writer?.apply {
            write("$command\r")
            flush()
        }
        readUntilPrompt()
    }

    private fun readUntilPrompt(): String {
        val sb = StringBuilder()
        while (true) {
            val c = reader?.read() ?: break
            if (c.toChar() == '>') break
            sb.append(c.toChar())
        }
        return sb.toString().trim()
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        socket?.close()
    }
}
```

### Emulator features useful for testing

**Interactive commands (type into the emulator console):**

| Command | Effect |
|---|---|
| `scenario car` | Switch to the Toyota Auris scenario |
| `edit 010D=41 0D 50` | Set speed PID response to 80 km/h |
| `edit 010C=41 0C 1A F8` | Set RPM to 1726 |
| `counters` | Show request counts per PID |

**Simulating unsupported PIDs:**
The emulator returns "NO DATA" for PIDs not in its scenario. This is how a real vehicle responds to unsupported PIDs — test that your app handles this gracefully.

**Simulating errors:**
```bash
# In the emulator console:
edit 010D=?          # Returns "?" (invalid response)
edit 010D=NO DATA    # Returns NO DATA (unsupported PID)
```

### Android emulator support

ADB port forwarding also works with the Android emulator. The emulator's `10.0.2.2` address points to the host machine, so you can alternatively connect directly:

```bash
# From inside the Android emulator, connect to:
# host: 10.0.2.2
# port: 35000
```

No `adb reverse` needed for the Android emulator — just use `10.0.2.2` as the host.

---

## Phase 3: Bluetooth Testing

For testing the full Bluetooth stack (pairing, RFCOMM socket, reconnection, adapter sleep/wake), you need a device that speaks Bluetooth Classic SPP.

### Option A: ESP32 as a desk dongle (recommended)

An ESP32 development board (~$5-10) can emulate an ELM327 adapter over Bluetooth Classic SPP. This gives you a real Bluetooth connection to test against without needing a car.

**Setup:**
1. Buy an ESP32-DevKitC or similar (any ESP32 with Classic BT — not ESP32-C3/S3 which are BLE-only).
2. Flash an ELM327 emulator firmware. Options:
   - [esp32-obd2-emulator](https://github.com/meatpiHQ/esp32-obd2-emulator) — purpose-built for this
   - Write a minimal one: accept SPP connections, respond to AT commands and Mode 01 PIDs with hardcoded values
3. Power it via USB from your Mac.
4. Pair the phone with the ESP32 over Bluetooth, then run Pi Drive as if connecting to a real dongle.

This tests the full production Bluetooth path: scanning, pairing, RFCOMM socket, AT initialization, data polling, and reconnection (unplug the ESP32 to simulate adapter sleep).

### Option B: Mac as Bluetooth SPP server

macOS supports Bluetooth Classic but creating an SPP server is non-trivial. The ELM327 emulator project has some Bluetooth support on UNIX systems, but macOS Bluetooth serial bridging requires additional tools and is fragile. The ESP32 approach is more reliable.

If you want to try it:
```bash
# The emulator claims Bluetooth support — try:
python3 -m elm -b
# This may require pybluez, which has limited macOS support.
# Falls back to serial if BT fails.
```

### Option C: Real dongle at the car

For final validation, test with the actual OBDLink LX plugged into a vehicle. This is the only way to test:
- Real protocol auto-detection (CAN/KWP/ISO)
- Real PID support bitmask from a specific vehicle
- Real VIN decoding
- Adapter sleep/wake on ignition off/on
- Real-world data rates and timing

Do this periodically (weekly, before releases), not for daily development.

### Bluetooth-specific test cases

| Test | How to simulate | What to verify |
|---|---|---|
| First-time pairing | Factory reset ESP32 or use new BT name | Scan screen shows device, pairing completes |
| Reconnect to known device | Power-cycle ESP32 | App detects disconnect, auto-reconnects |
| Adapter sleep/wake | Unplug ESP32 USB for 30s, replug | Trip auto-pauses, resumes on reconnect |
| Connection timeout | Start app with ESP32 off | Error state shown, retry button works |
| Out of range | Walk phone away from ESP32 | Disconnect detected, auto-reconnect starts |
| Multiple devices visible | Run 2 ESP32s or have other BT devices nearby | Correct device identified, "not OBD" badge on others |

---

## Android Auto Testing with DHU

The Desktop Head Unit (DHU) emulates an Android Auto head unit on your Mac. Combined with Phase 1 (demo mode) or Phase 2 (ELM327 emulator), this lets you test all AA screens without a car.

### Setup

**1. Install DHU via Android Studio:**
- Open SDK Manager → SDK Tools tab
- Check "Android Auto Desktop Head Unit Emulator"
- Install

**2. Enable developer mode on the phone:**
- Install the "Android Auto" app on your phone
- Open Android Auto settings → tap version number 10 times → "OK" to enable developer mode
- In developer settings, check "Unknown sources" (allows sideloaded AA apps)

**3. Start the head unit server on the phone:**
```bash
adb forward tcp:5277 tcp:5277
```

**4. Launch DHU on the Mac:**
```bash
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

The DHU window opens and mirrors the Android Auto output from your phone.

### Testing screen modes

**Full-screen (dials and graphs):**
```bash
# Default DHU size — tests full-screen AA layout
desktop-head-unit
```

The DHU shows your app's dials/graphs screens. Swipe left/right or click the Dials/Graphs buttons to switch.

**Split-screen (⅓ panel alongside maps):**
```bash
# Launch DHU with a wider display to trigger split-screen
desktop-head-unit --screen-width 1920 --screen-height 720 --dpi 160
```

Or use DHU's built-in split-screen mode:
- Launch a navigation app (e.g., Google Maps) in the DHU
- Your app should appear in the ⅓ side panel
- Test both Page 1 (hero + pills + graph) and Page 2 (tile grid) by swiping

**Configurable display sizes:**
```bash
# Small head unit (tests compact layout)
desktop-head-unit --screen-width 800 --screen-height 480 --dpi 160

# Large head unit (tests spacious layout)
desktop-head-unit --screen-width 1920 --screen-height 1080 --dpi 160

# Minimum for split-screen (6" display equivalent)
desktop-head-unit --screen-width 1280 --screen-height 720 --dpi 160
```

### DHU test cases

| Test | How | What to verify |
|---|---|---|
| Dials render correctly | Open Screen 1 in DHU | 3 dials (speed/RPM/coolant), bottom stat strip, values update live |
| Graphs render correctly | Swipe to Screen 2 | Throttle and G-force graphs animate, MPG and trip boxes show data |
| Screen switching | Tap Dials/Graphs buttons | Smooth slide animation, correct screen content |
| Split-screen panel | Start Maps + Pi Drive in DHU | ⅓ panel with header, page dots, swipeable pages |
| CarToast alerts | Trigger a hard brake scenario | Toast notification appears and auto-dismisses |
| Split-screen Page 1 | View ⅓ panel Page 1 | Hero readout, 4 pill slots, graph with time axis |
| Split-screen Page 2 | Swipe to Page 2 in ⅓ panel | 2×3 tile grid, all values updating |

### Combining DHU with data sources

The DHU is purely a display — it renders whatever your app sends. Combine it with any phase:

```bash
# Terminal 1: Start ELM327 emulator
python3 -m elm -n 35000 -s car

# Terminal 2: Forward ports
adb reverse tcp:35000 tcp:35000
adb forward tcp:5277 tcp:5277

# Terminal 3: Launch DHU
$ANDROID_HOME/extras/google/auto/desktop-head-unit

# Terminal 4: Launch app in TCP mode
adb shell am start -n ghart.space.pi_drive/.MainActivity \
    --ez tcp_mode true --es tcp_host localhost --ei tcp_port 35000
```

Now the phone shows live dashboard, DHU shows AA screens, and both are fed by the emulator.

---

## Automated Testing

Structure the codebase so that OBD parsing, detection algorithms, and data accumulation can run in unit tests and CI without a device.

### Unit tests (no device needed)

**OBD hex parsing:**
```kotlin
class PidParserTest {
    @Test fun `parse speed response`() {
        // PID 0D: A = speed in km/h
        val raw = "410D50"  // 0x50 = 80 km/h
        assertEquals(80, PidParser.parseSpeed(raw))
    }

    @Test fun `parse RPM response`() {
        // PID 0C: ((A*256)+B)/4
        val raw = "410C1AF8"  // (0x1A*256 + 0xF8)/4 = 1726
        assertEquals(1726, PidParser.parseRpm(raw))
    }

    @Test fun `parse NO DATA response`() {
        val raw = "NO DATA"
        assertNull(PidParser.parseSpeed(raw))
    }

    @Test fun `parse malformed response`() {
        val raw = "41GARBAGE"
        assertNull(PidParser.parseSpeed(raw))
    }
}
```

**PID support bitmask:**
```kotlin
class PidSupportTest {
    @Test fun `decode supported PIDs from 0100 response`() {
        // "4100BE3EB813" -> bitmask for PIDs 01-20
        val supported = PidSupport.decode("4100BE3EB813")
        assertTrue(supported.contains(0x05))  // coolant
        assertTrue(supported.contains(0x0C))  // RPM
        assertTrue(supported.contains(0x0D))  // speed
    }
}
```

**Acceleration detection:**
```kotlin
class AccelerationDetectorTest {
    @Test fun `hard brake event fires at threshold`() {
        val detector = AccelerationDetector(
            hardAccelThreshold = 9f,
            hardBrakeThreshold = 6.5f,
            minDurationMs = 500
        )

        val events = mutableListOf<DrivingEvent>()
        detector.onEvent = { events.add(it) }

        // Simulate going from 60 to 50 mph in 1 second (10 mph/s decel)
        detector.update(speedMph = 60f, timestampMs = 0)
        detector.update(speedMph = 55f, timestampMs = 250)
        detector.update(speedMph = 52f, timestampMs = 500)
        detector.update(speedMph = 50f, timestampMs = 750)

        assertEquals(1, events.size)
        assertEquals(EventType.HARD_BRAKE, events[0].type)
    }

    @Test fun `transient bump does not fire`() {
        val detector = AccelerationDetector(minDurationMs = 500)
        val events = mutableListOf<DrivingEvent>()
        detector.onEvent = { events.add(it) }

        // Single spike under min duration
        detector.update(speedMph = 60f, timestampMs = 0)
        detector.update(speedMph = 45f, timestampMs = 200) // spike
        detector.update(speedMph = 59f, timestampMs = 400) // recovers

        assertEquals(0, events.size)
    }
}
```

**Fuel economy calculation:**
```kotlin
class FuelEconomyTest {
    @Test fun `MPG from MAF sensor`() {
        // MAF = 8.4 g/s, speed = 96.5 km/h (60 mph)
        val mpg = FuelEconomy.fromMAF(mafGps = 8.4f, speedKmh = 96.5f)
        // fuel rate = 8.4 / 12054 = 0.000697 L/s = 2.51 L/h
        // km/L = 96.5 / 2.51 = 38.45
        // MPG = 38.45 * 2.352 = 90.4 (highway cruising)
        assertTrue(mpg in 85f..95f)
    }

    @Test fun `MPG from fuel rate PID`() {
        val mpg = FuelEconomy.fromFuelRate(fuelRateLph = 6.0f, speedKmh = 96.5f)
        // km/L = 96.5 / 6.0 = 16.08
        // MPG = 16.08 * 2.352 = 37.8
        assertTrue(mpg in 36f..40f)
    }
}
```

**Trip accumulation:**
```kotlin
class TripAccumulatorTest {
    @Test fun `distance accumulates from speed samples`() {
        val trip = TripAccumulator()
        // 60 mph for 1 second = 0.01667 miles
        trip.update(speedMph = 60f, timestampMs = 0)
        trip.update(speedMph = 60f, timestampMs = 1000)
        assertEquals(0.01667f, trip.distanceMiles, 0.001f)
    }

    @Test fun `trip pauses when speed is zero`() {
        val trip = TripAccumulator()
        trip.update(speedMph = 60f, timestampMs = 0)
        trip.update(speedMph = 0f, timestampMs = 1000)
        trip.update(speedMph = 0f, timestampMs = 5000) // 4 seconds stopped
        val durationBefore = trip.durationMs
        trip.update(speedMph = 0f, timestampMs = 10000) // still stopped
        assertEquals(durationBefore, trip.durationMs)
    }
}
```

### Integration tests (ELM327 emulator in CI)

Run the ELM327 emulator as a subprocess in integration tests:

```kotlin
class OBDIntegrationTest {
    companion object {
        private lateinit var emulatorProcess: Process

        @BeforeClass @JvmStatic fun startEmulator() {
            emulatorProcess = ProcessBuilder(
                "python3", "-m", "elm", "-n", "35000", "-s", "car"
            ).start()
            Thread.sleep(2000) // wait for emulator to start
        }

        @AfterClass @JvmStatic fun stopEmulator() {
            emulatorProcess.destroy()
        }
    }

    @Test fun `full initialization sequence`() = runBlocking {
        val transport = TcpTransport("localhost", 35000)
        transport.connect()

        val atz = transport.send("ATZ")
        assertTrue(atz.contains("ELM327"))

        transport.send("ATE0")
        transport.send("ATL0")
        transport.send("ATS0")
        transport.send("ATH0")
        transport.send("ATSP 0")

        val pids = transport.send("0100")
        assertTrue(pids.startsWith("41 00") || pids.startsWith("4100"))

        transport.disconnect()
    }
}
```

**CI pipeline (GitHub Actions):**

```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]
jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: ./gradlew :shared:test

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - run: pip install ELM327-emulator
      - run: python3 -m elm -n 35000 -s car &
      - run: sleep 2 && ./gradlew :shared:connectedTest
        # Or run JVM integration tests that connect to localhost:35000
```

### Instrumented UI tests

Use Compose UI testing or Espresso with `DemoVehicleDataSource`:

```kotlin
@HiltAndroidTest
class DashboardScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @BindValue val dataSource: VehicleDataSource = DemoVehicleDataSource(scenario = "CRUISE")

    @Test fun `speed value displays on dashboard`() {
        composeRule.onNodeWithTag("featured_value").assertExists()
        composeRule.onNodeWithTag("featured_unit").assertTextEquals("mph")
    }

    @Test fun `tile grid shows 6 tiles`() {
        composeRule.onAllNodesWithTag("metric_tile").assertCountEquals(6)
    }
}
```

---

## Test Scenario Matrix

A checklist of scenarios to validate across all three phases. Mark which phases cover each scenario.

| Scenario | P1 Demo | P2 TCP | P3 BT | AA DHU |
|---|---|---|---|---|
| Dashboard renders all metrics | ✓ | ✓ | ✓ | ✓ |
| Featured metric updates live | ✓ | ✓ | ✓ | ✓ |
| MPG row shows instant/trip/manual | ✓ | ✓ | ✓ | — |
| Tile grid shows correct widget types | ✓ | ✓ | ✓ | — |
| Sparkline animates | ✓ | ✓ | ✓ | — |
| AT initialization sequence | — | ✓ | ✓ | — |
| PID support bitmask parsing | — | ✓ | ✓ | — |
| VIN decode + vehicle info display | — | ✓ | ✓ | — |
| Unsupported PID graceful handling | — | ✓ | ✓ | — |
| BT scan + device list + RSSI | — | — | ✓ | — |
| BT pairing flow (3-step) | — | — | ✓ | — |
| Disconnect → auto-reconnect | ✓* | — | ✓ | — |
| Trip auto-pause on ignition off | ✓* | — | ✓ | — |
| Manual trip accumulates + resets | ✓ | ✓ | ✓ | ✓ |
| Auto-trip start/stop/boundary | ✓ | ✓ | ✓ | — |
| Weekly trip summary card | ✓ | ✓ | ✓ | — |
| Trip sync status pills (LIVE/QUEUED) | ✓ | ✓ | ✓ | — |
| Hard brake alert fires | ✓ | ✓ | ✓ | ✓ |
| G-Force cross-validation | ✓ | — | — | — |
| Speed limit alert | ✓ | ✓ | ✓ | ✓ |
| CarToast on AA | — | — | — | ✓ |
| Haptic feedback on alert | ✓ | ✓ | ✓ | — |
| Server upload (live stream) | ✓ | ✓ | ✓ | — |
| Server upload (offline buffer) | ✓ | ✓ | ✓ | — |
| zstd payload compression | ✓ | ✓ | ✓ | — |
| AA dials screen | — | — | — | ✓ |
| AA graphs screen | — | — | — | ✓ |
| AA split-screen ⅓ panel (Page 1) | — | — | — | ✓ |
| AA split-screen ⅓ panel (Page 2) | — | — | — | ✓ |
| Settings > phone home layout editor | ✓ | ✓ | ✓ | — |
| Settings > AA layout editor | ✓ | ✓ | ✓ | ✓ |
| Settings > thresholds sliders | ✓ | ✓ | ✓ | — |
| Settings > server config + test | ✓ | ✓ | ✓ | — |
| Theme switching (dark ↔ light) | ✓ | ✓ | ✓ | ✓ |
| Accent color switching | ✓ | ✓ | ✓ | ✓ |

*✓\* = simulated via demo scenario (DISCONNECT scenario)*

---

## Quick Start

The fastest path from zero to testing:

```bash
# 1. Build and install the app
cd pi-drive-android
./gradlew :mobile:installDebug

# 2. Launch in demo mode
adb shell am start -n ghart.space.pi_drive/.MainActivity --ez demo_mode true

# 3. (Optional) Add Android Auto
adb forward tcp:5277 tcp:5277
$ANDROID_HOME/extras/google/auto/desktop-head-unit

# 4. (Optional) Add ELM327 emulator for OBD protocol testing
pip3 install ELM327-emulator
python3 -m elm -n 35000 -s car &
adb reverse tcp:35000 tcp:35000
adb shell am start -n ghart.space.pi_drive/.MainActivity --ez tcp_mode true
```

You're now testing the full app — phone dashboard, Android Auto, and live OBD data — all from your desk.
