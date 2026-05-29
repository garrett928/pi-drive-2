package ghart.space.pi_drive.shared.telemetry

import android.util.Log
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample

private const val TAG = "TelemetryUploader"

/**
 * Core upload-loop logic for server telemetry.
 *
 * Separated from [TelemetryService] so the business logic can be unit-tested without an
 * Android service lifecycle. [TelemetryService] constructs and delegates to this class.
 *
 * For each snapshot collected from [snapshots]:
 * 1. [PayloadBuilder.build] assembles a [TelemetryPayload] (returns failure if VIN is blank).
 * 2. [TelemetryUploader.upload] POSTs the payload to the server.
 * 3. On upload failure and [TelemetryConfig.bufferWhenOffline] true, [OfflineBuffer.enqueue]
 *    queues the payload for later retry by [UploadWorker] via WorkManager.
 *
 * @param snapshots      Live vehicle data flow from [VehicleDataSource.snapshot].
 * @param uploader       HTTP client for the telemetry server.
 * @param offlineBuffer  Offline upload queue; handles serialization and retry scheduling.
 */
class TelemetryUploadController(
    private val snapshots: Flow<VehicleSnapshot>,
    private val uploader: TelemetryUploader,
    private val offlineBuffer: OfflineBuffer,
) {

    /**
     * Runs the upload loop until the enclosing coroutine is cancelled.
     *
     * Decimates the [snapshots] flow to [TelemetryConfig.sampleRateHz] before processing
     * to avoid overwhelming the server when the OBD poller runs at a higher frequency.
     *
     * @param config       Active telemetry configuration; re-read on each call so changes
     *                     take effect after a service restart.
     * @param recentEvents Driving events to attach to each payload. Phase 7 passes an empty
     *                     list; Phase 8+ will supply events from the alert manager.
     */
    @OptIn(FlowPreview::class)
    suspend fun run(config: TelemetryConfig, recentEvents: List<DrivingEvent> = emptyList()) {
        val periodMs = 1000L / config.sampleRateHz.coerceAtLeast(1)
        snapshots
            .sample(periodMs)
            .collect { snapshot -> processSnapshot(snapshot, recentEvents, config) }
    }

    /**
     * Processes a single snapshot: builds the payload, then uploads or queues it.
     *
     * Exposed as `internal` so [TelemetryServiceTest] can drive individual snapshots
     * without needing a Flow or service lifecycle.
     */
    internal suspend fun processSnapshot(
        snapshot: VehicleSnapshot,
        events: List<DrivingEvent>,
        config: TelemetryConfig,
    ) {
        val payloadResult = PayloadBuilder.build(snapshot, events, config)

        if (payloadResult.isFailure) {
            // VIN blank — skip silently (PayloadBuilder already logged a warning).
            return
        }

        val payload = payloadResult.getOrThrow()

        val uploadResult = uploader.upload(payload)
        if (uploadResult.isSuccess) {
            Log.d(TAG, "Uploaded snapshot at ${snapshot.timestamp}")
            return
        }

        Log.w(TAG, "Upload failed: ${uploadResult.exceptionOrNull()?.message}")

        if (config.bufferWhenOffline) {
            offlineBuffer.enqueue(payload)
        }
    }
}
