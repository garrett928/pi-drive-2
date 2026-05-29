package ghart.space.pi_drive.shared.telemetry

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

private const val TAG = "TelemetryUploader"

/**
 * WorkManager worker that drains the [OfflineBuffer] by uploading queued payloads.
 *
 * Each run uploads up to [BATCH_SIZE] items. On a fully-successful batch it returns
 * [Result.success]; if any upload fails it returns [Result.retry] so WorkManager schedules
 * a follow-up run using its exponential back-off policy.
 *
 * Dependencies are provided through [UploadWorker.Factory], which is registered as the
 * app's [androidx.work.WorkerFactory] in [ghart.space.pi_drive.PiDriveApplication].
 *
 * @param offlineBuffer     Offline upload queue.
 * @param configRepository  Supplies the server URL, API key, and device ID at runtime.
 */
class UploadWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val offlineBuffer: OfflineBuffer,
    private val configRepository: TelemetryConfigRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val config = configRepository.load()
        if (config.serverUrl.isBlank()) {
            Log.d(TAG, "UploadWorker: no server URL configured, nothing to do")
            return Result.success()
        }

        val uploader = TelemetryUploader(
            serverUrl = config.serverUrl,
            apiKey = config.apiKey,
            deviceId = config.deviceId,
        )

        val hasMore = processBatch(offlineBuffer, uploader)
        return if (hasMore) Result.retry() else Result.success()
    }

    /**
     * Custom [androidx.work.WorkerFactory] that injects [OfflineBuffer] and [TelemetryConfigRepository].
     *
     * Registered in [ghart.space.pi_drive.PiDriveApplication] so WorkManager uses it instead
     * of the default reflection-based factory.
     */
    class Factory(
        private val offlineBuffer: OfflineBuffer,
        private val configRepository: TelemetryConfigRepository,
    ) : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): CoroutineWorker? =
            if (workerClassName == UploadWorker::class.java.name)
                UploadWorker(appContext, workerParameters, offlineBuffer, configRepository)
            else null
    }

    companion object {
        private const val BATCH_SIZE = 50

        /**
         * Processes one batch of queued payloads from [offlineBuffer].
         *
         * Uploads items sequentially; stops immediately on the first failure to avoid
         * hammering an unreachable server. Successfully uploaded items are removed from the
         * queue; the failing item has its retry count incremented and its next-retry time
         * advanced with exponential back-off.
         *
         * Exposed as `internal` so [UploadWorkerTest] can drive it directly without needing
         * an Android context or WorkManager lifecycle.
         *
         * @return `true` when the queue still has items after this run (failure or more batches);
         *         `false` when the queue is fully drained.
         */
        internal suspend fun processBatch(
            offlineBuffer: OfflineBuffer,
            uploader: TelemetryUploader,
        ): Boolean {
            val batch = offlineBuffer.getNextBatch(BATCH_SIZE)
            if (batch.isEmpty()) {
                Log.d(TAG, "UploadWorker: offline queue is empty")
                return false
            }

            Log.d(TAG, "UploadWorker: processing ${batch.size} queued item(s)")

            for (entity in batch) {
                val result = uploader.uploadRaw(entity.payload)
                if (result.isSuccess) {
                    offlineBuffer.markUploaded(listOf(entity.id))
                    Log.d(TAG, "UploadWorker: uploaded id=${entity.id}")
                } else {
                    offlineBuffer.incrementRetry(entity.id)
                    Log.w(TAG, "UploadWorker: upload failed for id=${entity.id}, will retry")
                    return true
                }
            }

            val remaining = offlineBuffer.pendingCount()
            Log.d(TAG, "UploadWorker: batch done, $remaining item(s) remaining")
            return remaining > 0
        }
    }
}
