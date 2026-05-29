package ghart.space.pi_drive.shared.telemetry

import android.util.Log
import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import kotlinx.coroutines.FlowPreview
import ghart.space.pi_drive.shared.data.db.entity.PendingUploadEntity
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

private const val TAG = "TelemetryUploader"

/**
 * Core upload-loop logic for server telemetry.
 *
 * This class is separated from [TelemetryService] so the business logic can be
 * unit-tested without an Android service lifecycle. [TelemetryService] constructs and
 * delegates to this class.
 *
 * For each snapshot collected from [snapshots]:
 * 1. [PayloadBuilder.build] assembles a [TelemetryPayload] (returns failure if VIN is blank).
 * 2. [TelemetryUploader.upload] POSTs the payload to the server.
 * 3. On upload failure and when [TelemetryConfig.bufferWhenOffline] is true, the serialized
 *    payload is enqueued in [PendingUploadDao] for later retry by WorkManager (Phase 7.2).
 *
 * @param snapshots     Live vehicle data flow from [VehicleDataSource.snapshot].
 * @param uploader      HTTP client for the telemetry server.
 * @param pendingDao    Room DAO for the offline upload queue.
 */
class TelemetryUploadController(
    private val snapshots: Flow<VehicleSnapshot>,
    private val uploader: TelemetryUploader,
    private val pendingDao: PendingUploadDao,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    /**
     * Runs the upload loop until the enclosing coroutine is cancelled.
     *
     * Decimates the [snapshots] flow to [TelemetryConfig.sampleRateHz] before processing
     * to avoid overwhelming the server when the OBD poller runs at a higher frequency.
     *
     * @param config  Active telemetry configuration; re-read on each call so config changes
     *                take effect after a service restart.
     * @param recentEvents  Driving events to attach to each payload. In Phase 7, callers
     *                      pass an empty list; Phase 8+ will supply events from [AlertManager].
     */
    @OptIn(FlowPreview::class)
    suspend fun run(config: TelemetryConfig, recentEvents: List<DrivingEvent> = emptyList()) {
        val periodMs = (1000L / config.sampleRateHz.coerceAtLeast(1))
        snapshots
            .sample(periodMs)
            .collect { snapshot -> processSnapshot(snapshot, recentEvents, config) }
    }

    /**
     * Processes a single snapshot: builds the payload and uploads or queues it.
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
            // VIN missing — skip silently (PayloadBuilder already logged a warning).
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
            val serialized = json.encodeToString(payload)
            pendingDao.insert(
                PendingUploadEntity(
                    timestamp = snapshot.timestamp,
                    payload = serialized,
                    nextRetryTime = Instant.EPOCH,
                )
            )
            Log.d(TAG, "Queued snapshot for retry (queue depth: ${pendingDao.countPending()})")
        }
    }
}
