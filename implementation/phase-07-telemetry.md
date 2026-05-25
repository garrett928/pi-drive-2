# Phase 7: Server Telemetry

**Goal:** Implement real-time and offline data upload to a configurable server, plus the server settings screen. After this phase the app streams JSON payloads to an HTTP endpoint with retry and offline buffering.

**Depends on:** Phase 1 (VehicleDataSource, Room DB, PendingUploadDao), Phase 6 (trip data for payload).

**Reference:** REQUIREMENTS.md sections 5.4-5.4.4, README.md "Server Telemetry Upload" for payload schema.

---

## Step 7.1 -- Telemetry Payload + HTTP Uploader

**What to build in `shared/src/main/java/.../shared/telemetry/`:**

1. **`TelemetryPayload.kt`**: Data class matching the JSON schema in README.md:
   ```kotlin
   @Serializable
   data class TelemetryPayload(
       val timestamp: String, // ISO 8601
       val deviceId: String,
       val vin: String?,
       val location: LocationPayload?,
       val obd: OBDPayload,
       val calculated: CalculatedPayload,
       val accelMps2: Float?,
       val events: List<EventPayload>,
   )
   ```
   Plus nested data classes for `LocationPayload`, `OBDPayload`, `CalculatedPayload`, `EventPayload`.

2. **`PayloadBuilder.kt`**:
   - `build(snapshot: VehicleSnapshot, events: List<DrivingEvent>, config: TelemetryConfig): TelemetryPayload`
   - Applies signal selection (only includes enabled signals)
   - Converts units as needed for the wire format

3. **`TelemetryUploader.kt`**:
   - OkHttp-based HTTP client
   - `upload(payload: TelemetryPayload): Result<Unit>`
   - POST JSON to configured URL
   - Headers: `Content-Type: application/json`, `Authorization: Bearer $apiKey` (if configured), `X-Device-Id`
   - HTTPS validation (reject HTTP URLs)
   - Configurable timeout (10s connect, 30s read)
   - Returns success/failure with error message

4. **`TelemetryConfig.kt`**: Persisted in `EncryptedSharedPreferences`:
   ```kotlin
   data class TelemetryConfig(
       val serverUrl: String = "",
       val apiKey: String = "",
       val deviceId: String, // generated once on first launch
       val streamWhileDriving: Boolean = true,
       val bufferWhenOffline: Boolean = true,
       val uploadOnWifiOnly: Boolean = false,
       val compressPayloads: Boolean = true,
       val sampleRateHz: Int = 30,
       val enabledSignals: Set<String> = ALL_SIGNALS,
   )
   ```

5. **`TelemetryService.kt`**:
   - Foreground service (required for ongoing background work on Android 14+)
   - Subscribes to `VehicleDataSource.snapshot`
   - At configured sample rate: builds payload, attempts upload
   - On failure: queues to `PendingUploadDao` for later retry
   - Tracks: last successful upload time, queue depth, upload latency

**Unit tests:**
- `PayloadBuilderTest.kt`: snapshot with known values -> JSON string matches expected format; null fields omitted; signal selection filters correctly
- `TelemetryUploaderTest.kt`: Mock OkHttp -> verify request URL, headers, body format; verify HTTPS rejection for HTTP URL
- `TelemetryServiceTest.kt`: Feed 5 snapshots -> 5 upload attempts; upload failure -> queued in Room

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

**Estimated size:** ~1.2k lines

---

## Step 7.3 -- Server Settings Screen

**What to build in `mobile/src/main/java/.../ui/screens/settings/`:**

1. **`TelemetryServerScreen.kt`** (route: `settings/server`):
   - **Endpoint section:**
     - Server URL text input (validated as HTTPS on save)
     - Device ID (read-only, copyable)
     - API key input (masked, shows last 4 chars, "VERIFIED" pill when validated)
   - **Connection health card:**
     - Status: healthy/unhealthy
     - Round-trip latency (ms)
     - Time since last sync
     - "Test" button: sends a test POST, shows result
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
   - Test connection: POST to server URL with test payload, measure latency
   - Exposes: config state, connection health, signal support

**Unit tests:**
- ViewModel: save URL -> read back -> matches; invalid HTTP URL -> validation error; test connection with mock server -> latency measured

**Verify:**
- `/pd-run` -> navigate to Settings > Telemetry server
- `/pd-screenshot` -> all sections visible: endpoint, health, toggles, sample rate, signals
- Toggle a setting -> return -> setting persisted
- Tap "Test" -> health card updates

**Estimated size:** ~1.5k lines
