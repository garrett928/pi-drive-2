# Phase 4: Bluetooth Connection

**Goal:** Implement Bluetooth Classic RFCOMM communication with the OBDLink LX, including scan/pair UI, the 3-step connect flow, and automatic reconnection. After this phase the app can connect to a real adapter (or ESP32 emulating one).

**Depends on:** Phase 1 (OBDTransport interface), Phase 2 (InitializationSequence), Phase 3 (ConnectionBanner to display state).

**Reference:** README.md "Bluetooth Connection" section, REQUIREMENTS.md section 5.1.

---

## Step 4.1 -- BluetoothTransport

**What to build in `shared/src/main/java/.../shared/obd/`:**

1. **`BluetoothTransport.kt`**:
   - Implements `OBDTransport`
   - Takes a `BluetoothDevice` at construction
   - `connect()`: Opens RFCOMM socket with SPP UUID `00001101-0000-1000-8000-00805F9B34FB`, calls `socket.connect()` on IO dispatcher
   - `send(command)`: Writes `"$command\r"` to output stream, reads response bytes until `>` prompt character, returns trimmed string
   - `disconnect()`: Closes socket, streams
   - `isConnected`: StateFlow updated on connect/disconnect/error
   - **Timeout:** 2-second read timeout per command (`socket.soTimeout`)
   - **Thread safety:** All socket I/O on a single-threaded `Dispatchers.IO` context (no concurrent sends)
   - Error handling: catches `IOException`, updates `isConnected` to false, does not crash

2. **`shared/.../obd/TcpTransport.kt`**:
   - Implements `OBDTransport`
   - Connects to `host:port` via `java.net.Socket`
   - Same read-until-prompt logic as BluetoothTransport
   - Used for ELM327 emulator testing (Phase 2 in TESTING.md)

3. **Helper: `ResponseFramer.kt`**:
   - Shared logic for both transports: read bytes from `InputStream` until `>` prompt
   - Handles partial reads, strips `\r`, `\n`, whitespace
   - Timeout detection (throws `OBDTimeoutException` after configured duration)
   - Extracted so both transports use identical framing logic

**Unit tests:**
- `ResponseFramerTest.kt`:
  - Input stream containing "410D50\r\r>" -> returns "410D50"
  - Input stream containing "ELM327 v1.4b\r\r>" -> returns "ELM327 v1.4b"
  - Input stream with no `>` within timeout -> throws OBDTimeoutException
  - Multiple responses queued -> reads one at a time correctly

**Integration test (requires ELM327 emulator):**
- `/pd-obd` -> TcpTransport connects, sends ATZ, receives "ELM327" response

**Estimated size:** ~1k lines

---

## Step 4.2 -- Connect Screen UI (3-Step Flow)

**What to build in `mobile/src/main/java/.../ui/screens/connect/`:**

1. **`ConnectScanScreen.kt`** (route: `connect/scan`):
   - Pulsing Bluetooth animation at top (animated circle + BT icon)
   - Scrollable device list from `BluetoothAdapter.getBondedDevices()`
   - Each row: device name (bold), RSSI bars (1-4, mock for bonded devices), badge ("Paired" / "not OBD")
   - "Pair a new device" link at bottom -> opens Android BT settings intent
   - Tapping a device -> navigate to `connect/pair` with device address

2. **`ConnectPairScreen.kt`** (route: `connect/pair`):
   - Step-by-step checklist UI (vertical list of steps):
     - RFCOMM socket -> spinner/check/error
     - ATZ (reset) -> spinner/check/error
     - ATE0/ATL0/ATS0/ATH0 -> spinner/check/error
     - ATSP 0 -> spinner/check/error
     - PID support query -> spinner/check/error
     - VIN query -> spinner/check/error
   - Progress bar at top
   - Uses `InitializationSequence` from Phase 2
   - On failure: step turns red, "Retry" button
   - On success: auto-navigate to `connect/done`

3. **`ConnectDoneScreen.kt`** (route: `connect/done`):
   - Vehicle info: make, model, year (from VIN decode)
   - Masked VIN display
   - Supported signal count (e.g., "13 of 16 signals supported")
   - Detected OBD protocol name
   - "Go to dashboard" primary button -> navigate to `home`

4. **`ConnectViewModel.kt`** (`@HiltViewModel`):
   - Manages BT scanning, device selection, initialization flow
   - Exposes: `devices: StateFlow<List<DiscoveredDevice>>`, `initProgress: StateFlow<List<InitStepState>>`, `initResult: StateFlow<InitResult?>`

5. **Permission handling**: Runtime permission request for `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `ACCESS_FINE_LOCATION` before scanning.

6. **No bottom nav** on connect screens (full-screen flow).

**Unit tests:**
- `ConnectViewModelTest.kt`: Mock transport -> start init -> progress flow emits all 6 steps -> result has supported PIDs

**Verify (demo mode simulates the connect flow):**
- `/pd-run` -> navigate to connect screen
- `/pd-screenshot` at scan screen -> device list visible
- Navigate through pair and done screens -> `/pd-screenshot` each step
- "Go to dashboard" returns to live view

**Estimated size:** ~2k lines

---

## Step 4.3 -- Auto-Reconnect + Connection Manager

**What to build:**

1. **`shared/.../obd/ConnectionManager.kt`**:
   - Manages the full lifecycle: connect, monitor, reconnect
   - On disconnect: auto-reconnect attempt every 10 seconds for 5 minutes
   - After 5 min: stop retrying, expose "Reconnect" action
   - On adapter wake (reconnect succeeds): re-run initialization, resume polling
   - Lifecycle-aware: stop reconnect attempts when app is in background (save battery)
   - Exposes `connectionState: StateFlow<ConnectionState>` used by UI

2. **`shared/.../obd/AdapterWatcher.kt`**:
   - `BroadcastReceiver` for `BluetoothDevice.ACTION_ACL_DISCONNECTED`
   - Triggers reconnect flow when the adapter disconnects unexpectedly
   - Filters by the connected device's address (ignore other BT disconnects)

3. **Update `ConnectionBanner`**: When `ConnectionState.Disconnected` with `canRetry=true` -> show reconnect countdown and "Reconnect now" button.

4. **Update `LiveDashboardViewModel`**: Wire `ConnectionManager.connectionState` to the banner.

**Unit tests:**
- `ConnectionManagerTest.kt`:
  - Transport disconnect -> state changes to Disconnected -> reconnect attempts start
  - After 5 min of failures -> state shows canRetry=false
  - Reconnect succeeds -> state returns to Connected

**Verify:**
- `/pd-run` with DISCONNECT demo scenario -> after ~30s, banner shows "Reconnecting..." -> then reconnects and shows connected
- `/pd-screenshot` during disconnected state -> banner shows retry UI
- `/pd-logs` -> reconnect attempts visible in logcat

**Estimated size:** ~1k lines
