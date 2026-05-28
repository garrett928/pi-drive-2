# Phase 7: Server Telemetry

**Goal:** Implement real-time and offline data upload to a configurable server, plus the server settings screen. After this phase the app streams JSON payloads to an HTTP endpoint with retry and offline buffering.

**Depends on:** Phase 1 (VehicleDataSource, Room DB, PendingUploadDao), Phase 6 (trip data for payload).

**Reference:** REQUIREMENTS.md sections 5.4-5.4.4, README.md "Server Telemetry Upload" for payload schema.

---

## Server API Contract

The Android client communicates with three endpoints. All responses are **plain JSON — no HAL/HATEOAS `_links`**. If `apiKey` is configured, every request includes `Authorization: Bearer {apiKey}`.

### `POST {serverUrl}/telemetry`

Upload one telemetry snapshot.

- **Auth:** `Authorization: Bearer {apiKey}` (if set), `X-Device-Id: {deviceId}`
- **Body:** `TelemetryPayload` JSON (see Step 7.1). `vin` is required.
- **Server behavior:** Auto-registers the vehicle on the first upload for a new VIN. Idempotent by `(vin, timestamp)` — re-uploading the same snapshot is safe.
- **Response `200 OK`:** `{ "ok": true }`
- **Response `400`:** Missing or blank `vin`, malformed body.
- **Response `401`:** Bad or missing API key (if server has auth enabled).

### `GET {serverUrl}/telemetry/latest?vin={vin}`

Get the server's most recently received timestamp for this vehicle.

- **Auth:** same as above.
- **Response `200 OK`:** `{ "vin": "1HGCM82633A123456", "latestTimestamp": "2026-05-25T14:30:00Z" }`
- **Response `404`:** No telemetry exists for this VIN yet.
- **Use:** Settings screen "last synced" display; future cross-device recovery (e.g., fresh install on a new phone can query this to know the server's sync state).

### `GET {serverUrl}/health`

Liveness / connection test. Used by the "Test" button in the server settings screen.

- **Auth:** same as above.
- **Response `200 OK`:** `{ "status": "ok" }`
- **Response `401`:** Bad API key.

---

## Step 7.1 -- Telemetry Payload + HTTP Uploader

**What to build in `shared/src/main/java/.../shared/telemetry/`:**

1. **`TelemetryPayload.kt`**: Data class matching the JSON schema in README.md:
   ```kotlin
   @Serializable
   data class TelemetryPayload(
       val timestamp: String, // ISO 8601
       val deviceId: String,
       val vin: String,       // required — sourced from TelemetryConfig, never null
       val location: LocationPayload?,
       val obd: OBDPayload,
       val calculated: CalculatedPayload,
       val accelMps2: Float?,
       val events: List<EventPayload>,
   )
   ```
   Plus nested data classes for `LocationPayload`, `OBDPayload`, `CalculatedPayload`, `EventPayload`.

2. **`PayloadBuilder.kt`**:
   - `build(snapshot: VehicleSnapshot, events: List<DrivingEvent>, config: TelemetryConfig): Result<TelemetryPayload>`
   - Returns `Result.failure` with a descriptive error if `config.vin` is blank — upload is skipped, not queued, until VIN is provided
   - Sources `vin` from `config.vin` (never from `VehicleSnapshot` — VIN is a static vehicle property, not a live signal)
   - Applies signal selection (only includes enabled signals)
   - Converts units as needed for the wire format

3. **`TelemetryUploader.kt`**:
   - OkHttp-based HTTP client
   - `upload(payload: TelemetryPayload): Result<Unit>` → `POST {serverUrl}/telemetry`
   - `checkHealth(): Result<Unit>` → `GET {serverUrl}/health` (used by settings Test button)
   - `getLatestTimestamp(vin: String): Result<String?>` → `GET {serverUrl}/telemetry/latest?vin={vin}` (returns ISO 8601 string or null if 404)
   - Headers on all requests: `Content-Type: application/json`, `Authorization: Bearer $apiKey` (if configured), `X-Device-Id: {deviceId}`
   - HTTPS validation (reject HTTP URLs)
   - Configurable timeout (10s connect, 30s read)
   - Returns success/failure with error message

4. **`TelemetryConfig.kt`**: Persisted in `EncryptedSharedPreferences`:
   ```kotlin
   /** How the VIN was obtained. */
   enum class VinSource { AUTO_OBD, MANUAL, NONE }

   data class TelemetryConfig(
       val serverUrl: String = "",
       val apiKey: String = "",
       val deviceId: String,          // generated once on first launch
       val vin: String = "",          // VIN of the vehicle; blank until detected or entered
       val vinSource: VinSource = VinSource.NONE,
       val streamWhileDriving: Boolean = true,
       val bufferWhenOffline: Boolean = true,
       val uploadOnWifiOnly: Boolean = false,
       val compressPayloads: Boolean = true,
       val sampleRateHz: Int = 30,
       val enabledSignals: Set<String> = ALL_SIGNALS,
   )
   ```
   **Note:** VIN reading from OBD (service 09 PID 02) is Phase 2's responsibility. Phase 7 owns storing the result and handling the manual-entry fallback. When Phase 2 successfully reads a VIN it should call a `VinRepository` use case (stubbed here, wired in Phase 2) that writes `vin` + `vinSource=AUTO_OBD` into `TelemetryConfig`.

5. **`TelemetryService.kt`**:
   - Foreground service (required for ongoing background work on Android 14+)
   - Subscribes to `VehicleDataSource.snapshot`
   - At configured sample rate: builds payload, attempts upload
   - On failure: queues to `PendingUploadDao` for later retry
   - Tracks: last successful upload time, queue depth, upload latency

**Unit tests:**
- `PayloadBuilderTest.kt`: snapshot with known values → JSON string matches expected format; null fields omitted; signal selection filters correctly
- `PayloadBuilderTest.kt` VIN: config with non-blank VIN → `vin` present in payload; config with blank VIN → `Result.failure` returned, no payload built
- `TelemetryUploaderTest.kt`: Mock OkHttp → verify `POST /telemetry` URL, headers, body format; verify HTTPS rejection for HTTP URL; `GET /health` returns ok; `GET /telemetry/latest?vin=` returns timestamp string
- `TelemetryServiceTest.kt`: Feed 5 snapshots → 5 upload attempts; upload failure → queued in Room; blank VIN in config → uploads skipped (not queued)

**Verify:**
- `/pd-run` CRUISE scenario with a valid server URL configured in TelemetryConfig
- `/pd-logs` -> `TelemetryUploader` tag: "POST /telemetry" requests logged at the configured sample rate
- Screenshot (dashboard): `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-telemetry-active.png` → read image: dashboard live and streaming data
- Navigate to Settings > Telemetry Server; Screenshot: `$ADB shell screencap -p /sdcard/screen2.png && $ADB pull /sdcard/screen2.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-telemetry-settings.png` → read image: "Last upload" timestamp is recent

**Estimated size:** ~1.5k lines

---

## Step 7.2 -- Offline Buffer + WorkManager

**What to build:**

1. **`shared/.../telemetry/OfflineBuffer.kt`**:
   - Wraps `PendingUploadDao`
   - `enqueue(payload: TelemetryPayload)`: Serialize to JSON, insert as `PendingUploadEntity`
   - `getNextBatch(limit: Int): List<PendingUploadEntity>`
   - `markUploaded(ids: List<Long>)`: Delete uploaded entries
   - `incrementRetry(id: Long)`: Increase retry count, set next retry time with exponential backoff
   - Max retries: 10 (then discard with warning log)

2. **`shared/.../telemetry/UploadWorker.kt`**:
   - `CoroutineWorker` for WorkManager
   - Queries `OfflineBuffer.getNextBatch(50)`
   - Uploads each payload via `TelemetryUploader`
   - On success: mark uploaded. On failure: increment retry
   - Returns `Result.success()` when queue empty, `Result.retry()` when items remain

3. **`shared/.../telemetry/UploadScheduler.kt`**:
   - Schedules `UploadWorker` via WorkManager
   - Constraints: network available, optionally Wi-Fi only
   - Periodic: every 15 minutes (WorkManager minimum)
   - One-shot: triggered when connection restored after offline period
   - Backoff: exponential, starting at 30 seconds

4. **Update `TelemetryService`**: On upload failure, delegate to `OfflineBuffer.enqueue`. On network restore, trigger `UploadScheduler.triggerNow()`.

**Unit tests:**
- `OfflineBufferTest.kt`: Enqueue 5 -> getNextBatch(3) returns 3 -> markUploaded(3) -> getNextBatch(3) returns remaining 2
- `OfflineBufferTest.kt` backoff: incrementRetry -> next retry time increases exponentially
- `UploadWorkerTest.kt`: 3 queued, uploader succeeds -> all marked uploaded; uploader fails on 2nd -> 1st marked uploaded, 2nd retried, 3rd still queued

**Verify:**
- `/pd-run` CRUISE scenario with server URL set to a non-existent host
- `/pd-logs` -> "Upload failed, queuing for retry" messages
- Set server URL to a mock endpoint (httpbin or local) -> queued data uploads
- Screenshot (Settings > Telemetry Server): `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-offline-buffer.png` → read image: health section shows error state while server unreachable, then recovery after pointing to mock endpoint

**Estimated size:** ~1.2k lines

---

## Step 7.3 -- Server Settings Screen

**What to build in `mobile/src/main/java/.../ui/screens/settings/`:**

1. **`TelemetryServerScreen.kt`** (route: `settings/server`):
   - **Vehicle section:**
     - VIN display: if `vinSource == AUTO_OBD` show a read-only chip labelled "Detected from OBD" with the VIN value; if `vinSource == MANUAL` show an editable text field pre-filled with the saved VIN; if `vinSource == NONE` show an editable text field with hint text "Enter VIN manually"
     - "Re-detect from OBD" button — only enabled when OBD is connected; triggers `TelemetryServerViewModel.retriggerVinDetection()`
     - If VIN is blank on first entry to this screen, show an inline warning banner: "Uploads paused — VIN required. Connect your OBD adapter or enter the VIN manually."
   - **Endpoint section:**
     - Server URL text input (validated as HTTPS on save)
     - Device ID (read-only, copyable)
     - API key input (masked, shows last 4 chars, "VERIFIED" pill when validated)
   - **Connection health card:**
     - Status: healthy/unhealthy
     - Round-trip latency (ms)
     - Time since last sync (sourced from `GET /telemetry/latest?vin={vin}`)
     - "Test" button: calls `GET {serverUrl}/health`, shows result inline
   - **Streaming toggles:**
     - Stream live while driving (on/off)
     - Buffer when offline (on/off)
     - Upload on Wi-Fi only (on/off)
     - Compress payloads (on/off)
   - **Sample rate:**
     - Slider: 1-60 Hz
     - Preset buttons: 1, 5, 10, 30, 60 Hz
   - **Signal selection:**
     - Toggle chips grouped by category (OBD PIDs, Calculated, Phone sensors)
     - Disabled chips for unsupported PIDs with info note

2. **`TelemetryServerViewModel.kt`** (`@HiltViewModel`):
   - Loads/saves `TelemetryConfig`
   - Exposes: config state, connection health, signal support, `vinState: StateFlow<VinState>`
   - `VinState`: data class with `vin: String`, `source: VinSource`, `isBlank: Boolean`
   - `saveVin(vin: String)`: saves to `TelemetryConfig` with `vinSource = MANUAL`
   - `retriggerVinDetection()`: calls stubbed `VinRepository.readVinFromObd()` use case — logs a warning in Phase 7 since Phase 2 hasn't wired this yet; full implementation wired in Phase 2
   - Test connection: `GET {serverUrl}/health` via `TelemetryUploader.checkHealth()`, measures round-trip latency
   - Fetch last synced time: `GET {serverUrl}/telemetry/latest?vin={vin}` on screen entry

**Unit tests:**
- ViewModel: save URL → read back → matches; invalid HTTP URL → validation error; test connection with mock uploader → latency measured
- ViewModel VIN: blank VIN on load → `vinState.isBlank = true`; `saveVin("1HGCM...")` → `vinSource == MANUAL`, stored in config; `retriggerVinDetection()` when disconnected → no-op + warning log

**Verify:**
- `/pd-run` -> navigate to Settings > Telemetry server
- Screenshot: `ADB=~/Library/Android/sdk/platform-tools/adb; mkdir -p /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots; $ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /Users/ghart/Documents/garrett-files/projects/pi-drive-2/screenshots/pidrive-server-settings.png` → read image: all sections visible — endpoint, health, toggles, sample rate, signals
- Toggle a setting -> return -> setting persisted
- Tap "Test" -> health card updates

**Estimated size:** ~1.5k lines
