# Phase 13: Remote Logging (Grafana Loki)

**Goal:** Ship the app's operational logs to a Grafana Loki instance so failures can be searched and diagnosed remotely — without a laptop in the car. The app batches structured log entries, buffers them offline in Room, and pushes them in Loki push-API format to a Grafana **Alloy** gateway (or Loki directly), reuploading when connectivity returns. This complements the existing on-device `FileLogger` (logcat-to-file + crash capture; see `DEBUGGING.md`).

**Depends on:** Phase 1 (Room DB), Phase 7 (reuses the offline-buffer + `WorkManager` upload pattern — `OfflineBuffer`, `UploadWorker`, `UploadScheduler`), Phase 8 (settings infra).

**Reference:** `REQUIREMENTS.md` §5.12, `DEBUGGING.md` (FileLogger). Loki push API: `POST /loki/api/v1/push`, JSON body `{"streams":[{"stream":{<labels>},"values":[["<ns_ts_string>","<line>",{<structured_metadata>}]]}]}` — **timestamp is a string of epoch nanoseconds**; supports `Content-Encoding: gzip`; auth via bearer header + optional `X-Scope-OrgID`. Label/metadata schema is shared with the server (`pi-drive-server` Phase 8) so Grafana queries are uniform.

---

## Architecture note

Android cannot run the Alloy agent, so the app is its own log shipper. The push wire format is identical whether the target is an Alloy `loki.source.api` gateway (recommended — Alloy then forwards to Loki with WAL buffering) or Loki directly, so the endpoint is a configuration value, not a code change.

**Shared label / metadata schema (do not deviate — the server uses the same):**
- **Labels (static, low cardinality):** `app="pi-drive"`, `component` (`mobile`|`automotive`), `level` (`debug|info|warn|error`), `env` (`dev|prod`).
- **Structured metadata (per-line, queryable):** `tag` (log tag e.g. `VehicleData`), `device_id`, `vin`, `session_id`, `event` (short key), `thread`. Never put these in labels — Loki best practice keeps identifiers out of the stream index.

---

## Step 13.1 — Structured log layer + Room `pending_logs` buffer

**What to build in `shared/src/main/java/.../shared/diag/`:**

1. **`LogEvent.kt`** — immutable structured event:
   ```kotlin
   data class LogEvent(
       val timestampNanos: Long,   // System epoch ns
       val level: LogLevel,        // DEBUG/INFO/WARN/ERROR
       val component: String,      // "mobile" | "automotive"
       val tag: String,            // e.g. "VehicleData"
       val message: String,
       val event: String? = null,  // short key, e.g. "obd_init_complete"
       val deviceId: String? = null,
       val vin: String? = null,
       val sessionId: String,      // per app-run UUID
       val thread: String,
   )
   ```
2. **`RemoteLogger.kt`** — the capture entry point. A thin facade (or a Timber `Tree`) with `d/i/w/e(tag, message, event=…, throwable=…)`. It:
   - Builds a `LogEvent` (filling `sessionId`, `thread`, configured `deviceId`/`vin`).
   - Filters by the configured minimum remote level.
   - Hands the event to `LogBuffer.enqueue` (non-blocking).
   - Exceptions: serialize the stack trace into `message`; level `ERROR`.
   - Wire the **existing uncaught-exception handler** (FileLogger's) to also emit a final `ERROR` `LogEvent` with `event="crash"` and flush synchronously.
3. **Room:** `PendingLogEntity` (table `pending_logs`: `id`, `timestampNanos`, `level`, `component`, `tag`, `message`, `event`, `deviceId`, `vin`, `sessionId`, `thread`, `retryCount`, `nextRetryAt`) + `PendingLogDao`. Add to `PiDriveDatabase` (schema bump + migration).
4. **`LogBuffer.kt`** — wraps `PendingLogDao`: `enqueue(event)`, `getNextBatch(limit)`, `delete(ids)`, `incrementRetry(ids)`, `trimToRetention(maxRows / maxAge)` so a long offline period can't fill storage.

**Unit tests:**
- `RemoteLogger` filters below-threshold levels; fills `sessionId`/`thread`; throwable → ERROR with stack trace in message.
- `LogBuffer`: enqueue/getNextBatch/delete round-trip; `trimToRetention` drops oldest beyond cap.

**Verify:** `/pd-test`.

**Estimated size:** ~0.9k lines

---

## Step 13.2 — Loki push client + upload worker + settings

**What to build in `shared/.../diag/`:**

1. **`LokiPayload.kt`** — builds the Loki push JSON from a `List<LogEvent>`:
   - Group events into **streams keyed by the label set** (`app/component/level/env`) — events with the same labels share a stream.
   - Each value = `[ "<timestampNanos as string>", "<message>", { tag, device_id, vin, session_id, event, thread } ]` (omit null metadata keys).
   - `@Serializable`; assert timestamp is serialized as a **string** (Loki rejects numeric ts with 400).
2. **`LokiLogShipper.kt`** — OkHttp client:
   - `ship(events): Result<Unit>` → `POST {lokiUrl}/loki/api/v1/push`, `Content-Type: application/json`, optional gzip (`Content-Encoding: gzip` when `compress` enabled), `Authorization: Bearer {token}` and `X-Scope-OrgID: {tenant}` if configured.
   - HTTPS validation; connect/read timeouts; returns success/failure with status + body for diagnostics.
   - `sendTestLog()` for the settings Test button.
3. **`LogUploadWorker.kt`** (`CoroutineWorker`) + scheduling — mirror Phase 7.2:
   - Pull `LogBuffer.getNextBatch(200)`, ship, on success delete, on failure `incrementRetry` with exponential backoff (cap retries; then drop oldest with a local warning).
   - Periodic (15-min) + one-shot trigger when connectivity restored. Honor Wi-Fi-only and data-retention prefs (reuse the telemetry constraints).
   - Register with the existing `DelegatingWorkerFactory`.
4. **`RemoteLogConfig.kt`** + repository (EncryptedSharedPreferences, like `TelemetryConfig`): `enabled`, `lokiUrl`, `authToken`, `tenant`, `minLevel`, `compress`, reuse `deviceId`.

**Unit tests:**
- `LokiPayload`: events with identical labels collapse into one stream; timestamp serialized as string; null metadata omitted; multi-stream grouping correct.
- `LokiLogShipper` (MockWebServer): correct URL/headers/body; HTTP URL rejected; gzip path; `sendTestLog` round-trip.
- `LogUploadWorker`: success deletes batch; failure increments retry; backoff schedule.

**Verify:**
- `/pd-run`, then point `lokiUrl` at a MockWebServer or a local Loki (`docker run grafana/loki`).
- `/pd-logs` → `RemoteLog` tag shows "shipped batch=N" and queue draining.

**Estimated size:** ~1.3k lines

---

## Step 13.3 — Instrumentation + Diagnostics settings screen + Grafana queries

**What to build:**

1. **Instrument the key points** with `RemoteLogger` (per `REQUIREMENTS.md` §5.12.4) — generous on lifecycle/errors, sparse on data:
   - App start/stop, config changes (`event="app_start"` etc.).
   - Connection: scan/pair/connect/disconnect, adapter found/lost, each auto-reconnect attempt + result (`ConnectionManager`, `AdapterWatcher`).
   - OBD init: each init step and the **`supportedPids` count** (`InitializationSequence`/`OBDVehicleDataSource`), VIN read result.
   - Detection/trips/alerts: hard accel/brake, trip start/end, health alerts.
   - Telemetry uploader: **batch summaries only** (`event="upload_batch"`, fields: count, queue depth, latency) — never per record.
2. **`SettingsDiagnosticsScreen.kt`** (under Developer/Cloud settings): remote-logging toggle, Loki URL, auth token (masked), tenant, min-level picker, compress toggle, **"Send test log"** button (calls `sendTestLog`, shows result inline), and live `pending_logs` queue depth.
3. **`pi-drive-android/observability/grafana-queries.md`** — a short doc of canonical LogQL queries for the team, e.g.:
   - All errors for a device: `{app="pi-drive", level="error"} | device_id="pd-..."`
   - Empty-dials triage: `{app="pi-drive"} |= "supportedPids=0"`
   - Connection failures: `{app="pi-drive"} | event=~"bt_.*|reconnect.*" |= "fail"`
   - Crashes: `{app="pi-drive", level="error"} | event="crash"`

**Unit tests:**
- Diagnostics ViewModel: save/read config; test-log invokes shipper; queue-depth flow reflects buffer.
- Spot-check a couple of instrumentation points emit the expected `event`/metadata (e.g. init complete logs `supportedPids`).

**Verify:**
- `/pd-verify` (or manual): navigate to Diagnostics settings, screenshot the screen, tap "Send test log" → success; with a local Loki, confirm the test line and an `obd_init_complete` line appear in Grafana/LogCLI.
- Screenshot → `pi-drive-android/screenshots/pidrive-diagnostics-settings.png`.

**Estimated size:** ~1.2k lines

---

## Done definition

App emits structured logs for all lifecycle and error paths (not per telemetry record), buffers them in `pending_logs` when offline, and reuploads to the Alloy/Loki endpoint on reconnect. Logs are searchable in Grafana by `device_id`, `vin`, `tag`, `event`, and `level` using the shared schema. The Diagnostics settings screen configures the endpoint and verifies connectivity.
