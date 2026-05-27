# Phase 2: OBD Protocol

**Goal:** Build the OBD-II command/response layer: hex parsing, PID formulas, PID support detection, VIN decoding, and the polling loop that feeds VehicleSnapshot. After this phase, data from a real (or emulated) ELM327 adapter flows through the same StateFlow as demo data.

**Depends on:** Phase 1 (OBDTransport interface, VehicleSnapshot, VehicleDataSource interface).

---

## Step 2.1 -- Command Formatting + Response Parsing

**What to build in `shared/src/main/java/.../shared/obd/`:**

1. **`OBDCommand.kt`**: Sealed class for all commands the app sends:
   - AT commands: `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP(protocol)`, `ATRV`
   - PID requests: `PidRequest(service: Int, pid: Int)` -- formats as "01XX" with no spaces
   - Each has a `toRawString(): String` returning the wire format (e.g. "010D")

2. **`OBDResponse.kt`**: Sealed class for parsed responses:
   - `Success(serviceResponse: Int, pid: Int, dataBytes: ByteArray)`
   - `NoData` ("NO DATA" or "NO DATA>" response)
   - `Error(rawMessage: String)` (catch-all for "?", "UNABLE TO CONNECT", "BUS INIT", etc.)
   - `ATResponse(text: String)` (for AT command responses like "ELM327 v1.4b" or "14.2V")

3. **`ResponseParser.kt`**: Stateless parser:
   - `parse(rawResponse: String): OBDResponse` -- strips whitespace/CR/LF, handles all error conditions
   - Handles both spaced ("41 0D 50") and unspaced ("410D50") formats
   - Handles multi-line responses (multiple ECU responses)
   - Handles echo residue (if echo wasn't fully disabled)

4. **`PidDecoder.kt`**: Decodes raw data bytes into human-readable values. One function per PID:
   - `decodeSpeed(bytes): Int?` -- PID 0D: A km/h
   - `decodeRpm(bytes): Int?` -- PID 0C: ((A*256)+B)/4
   - `decodeCoolantTemp(bytes): Int?` -- PID 05: A-40 Celsius
   - `decodeIntakeTemp(bytes): Int?` -- PID 0F: A-40 Celsius
   - `decodeMaf(bytes): Float?` -- PID 10: ((A*256)+B)/100 g/s
   - `decodeThrottle(bytes): Float?` -- PID 11: (A*100)/255 %
   - `decodeFuelLevel(bytes): Float?` -- PID 2F: (A*100)/255 %
   - `decodeOilTemp(bytes): Int?` -- PID 5C: A-40 Celsius
   - `decodeFuelRate(bytes): Float?` -- PID 5E: ((A*256)+B)/20 L/h
   - `decodeBatteryVoltage(atResponse: String): Float?` -- parse "14.2V" from ATRV

5. **`FuelEconomy.kt`**: Calculation helpers:
   - `fromFuelRate(fuelRateLph: Float, speedKmh: Int): Float?` -- returns MPG
   - `fromMAF(mafGps: Float, speedKmh: Int): Float?` -- returns MPG (MAF / 12054 for gasoline)
   - Conversion: `kmPerLiterToMpg(kml: Float): Float`

**Unit tests -- these are critical, every formula must be verified:**
- `ResponseParserTest.kt`:
  - "410D50" -> Success(pid=0x0D, bytes=[0x50])
  - "41 0C 1A F8" -> Success(pid=0x0C, bytes=[0x1A, 0xF8])
  - "NO DATA" -> NoData
  - "?" -> Error
  - "SEARCHING..." followed by "410D50" -> Success (strips searching prefix)
  - Empty string -> Error
  - Multi-line with duplicate ECU responses -> first valid response used
- `PidDecoderTest.kt`:
  - Speed: [0x50] -> 80 km/h; [0x00] -> 0; [0xFF] -> 255
  - RPM: [0x1A, 0xF8] -> 1726; [0x00, 0x00] -> 0
  - Coolant: [0x82] -> 90C; [0x28] -> 0C (cold)
  - Throttle: [0x00] -> 0%; [0xFF] -> 100%; [0x80] -> ~50%
  - Empty/null bytes -> null
- `FuelEconomyTest.kt`:
  - MAF=8.4 g/s at 96.5 km/h -> ~90 MPG (highway cruise)
  - FuelRate=6.0 L/h at 96.5 km/h -> ~38 MPG
  - Speed=0 -> null (avoid division by zero)

**Estimated size:** ~1.5k lines

---

## Step 2.2 -- PID Support Bitmask + VIN Decoder

**What to build:**

1. **`shared/.../obd/PidSupport.kt`**:
   - `decode(response: OBDResponse.Success): Set<Int>` -- decodes 4-byte bitmask into set of supported PIDs
   - `queryRanges(): List<OBDCommand>` -- returns [0100, 0120, 0140, 0160] commands
   - `isSupported(pid: Int, supportedPids: Set<Int>): Boolean`
   - Handles the chaining: PID 0x20 in set means 0120 range exists, etc.

2. **`shared/.../obd/VinDecoder.kt`**:
   - `parseVinResponse(rawHex: String): String?` -- service 09, PID 02 returns multi-frame hex -> ASCII VIN
   - `decodeVin(vin: String): VehicleInfo` -- extract world manufacturer, year, make/model approximation
   - `VehicleInfo` data class: `vin`, `maskedVin` (e.g. "JF1VA **** 1G862045"), `year`, `make`, `model` (best-effort)

3. **`shared/.../obd/InitializationSequence.kt`**:
   - Orchestrates the full connect sequence via an `OBDTransport`:
     1. ATZ -> verify "ELM327" in response
     2. ATE0, ATL0, ATS0, ATH0
     3. ATSP 0
     4. Query supported PIDs (0100, then 0120 if indicated, etc.)
     5. Query VIN (0902)
     6. Return `InitResult(supportedPids: Set<Int>, vin: String?, vehicleInfo: VehicleInfo?, protocol: String?)`
   - Each step has a timeout; errors are captured per-step (not fatal)
   - Emits progress via `Flow<InitStep>` so UI can show checklist

**Unit tests:**
- `PidSupportTest.kt`:
  - "4100BE3EB813" -> set contains PIDs 01,03,04,05,06,07,0C,0D,0E,0F,10,11,13,15,1C,20
  - "4100000000001" -> only PID 20 supported (chaining indicator)
  - "41200000000" -> empty set for 21-40 range
- `VinDecoderTest.kt`:
  - Known VIN hex -> correct 17-char ASCII string
  - "JF1VA1E66G9362045" -> year=2016, make=Subaru (WMI "JF1")
  - Invalid/short hex -> null
- `InitializationSequenceTest.kt`:
  - Run against MockTransport -> returns expected InitResult
  - MockTransport that fails on ATSP -> InitResult has error for that step but continues

**Estimated size:** ~1.2k lines

---

## Step 2.3 -- OBD Polling Loop + OBDVehicleDataSource

**What to build:**

1. **`shared/.../data/OBDVehicleDataSource.kt`**:
   - Implements `VehicleDataSource`
   - Takes an `OBDTransport` + `Set<Int>` (supported PIDs) via constructor
   - Runs a coroutine loop:
     1. For each supported PID in priority order (speed, RPM first, then others)
     2. Send PID request via transport
     3. Parse response via `ResponseParser` + `PidDecoder`
     4. Build new `VehicleSnapshot` from accumulated values
     5. Emit to `_snapshot: MutableStateFlow<VehicleSnapshot>`
   - **Priority polling:** speed + RPM every cycle, other PIDs round-robin (1 per cycle)
   - Battery voltage via ATRV every 30 seconds (not a PID, separate AT command)
   - Handles transport errors: log + skip PID + continue loop (don't crash)
   - Tracks poll rate (cycles per second) and exposes via `connectionState`

2. **`shared/.../data/OBDPollScheduler.kt`**:
   - Determines PID polling order based on priority and support
   - High priority: 0x0D (speed), 0x0C (RPM) -- every cycle
   - Medium: 0x05 (coolant), 0x11 (throttle), 0x10 (MAF) -- every 2nd cycle
   - Low: 0x0F (intake), 0x2F (fuel), 0x5C (oil), 0x5E (fuel rate) -- every 4th cycle
   - Returns `List<OBDCommand>` for each cycle tick

3. **Update Hilt module**: Wire `OBDVehicleDataSource` when not in demo mode.

**Unit tests:**
- `OBDPollSchedulerTest.kt`: Cycle 1 includes speed+RPM+coolant; cycle 2 includes speed+RPM+throttle; etc.
- `OBDVehicleDataSourceTest.kt`: Feed MockTransport -> collect 10 snapshots from StateFlow -> speed/RPM populated every snapshot, coolant populated every other

**Integration test (runs against ELM327 emulator -- see TESTING.md Phase 2):**
- Start emulator: `python3 -m elm -n 35000 -s car`
- `TcpTransport` connects to localhost:35000
- `InitializationSequence` completes successfully
- `OBDVehicleDataSource` emits snapshots with non-null speed and RPM
- Run via: `/pd-obd` skill
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-obd-live.png` → read image: dashboard shows non-null speed and RPM values updating from ELM327 emulator

**Estimated size:** ~1.2k lines
