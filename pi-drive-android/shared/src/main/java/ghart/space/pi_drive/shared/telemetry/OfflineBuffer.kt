package ghart.space.pi_drive.shared.telemetry

import android.util.Log
import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import ghart.space.pi_drive.shared.data.db.entity.PendingUploadEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

private const val TAG = "TelemetryUploader"

/** Maximum retry attempts before a queued payload is discarded. */
private const val MAX_RETRIES = 10

/** Base exponential backoff delay in seconds (doubles per retry, capped at 24 h). */
private const val BASE_BACKOFF_SECONDS = 30L

/** Maximum backoff ceiling: 24 hours in seconds. */
private const val MAX_BACKOFF_SECONDS = 86_400L

/**
 * Manages the local queue of telemetry payloads that could not be uploaded immediately.
 *
 * Wraps [PendingUploadDao] and handles JSON serialization, batch retrieval, and
 * exponential back-off retry scheduling. Items that exceed [MAX_RETRIES] are silently
 * discarded with a warning log rather than retried forever.
 *
 * Back-off formula: `min(30 * 2^retryCount, 86400)` seconds added to the current time.
 * This gives delays of: 30 s, 60 s, 120 s, 240 s, … up to 24 h.
 *
 * Thread safety: all methods are `suspend` functions and delegate to Room, which serializes
 * database access on its own dispatcher.
 */
class OfflineBuffer(private val dao: PendingUploadDao) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    /**
     * Serializes [payload] to JSON and inserts it into the upload queue.
     *
     * [PendingUploadEntity.nextRetryTime] is set to [Instant.EPOCH] so the item is eligible
     * for the very next upload batch (no initial delay).
     */
    suspend fun enqueue(payload: TelemetryPayload) {
        val serialized = json.encodeToString(payload)
        dao.insert(
            PendingUploadEntity(
                timestamp = Instant.now(),
                payload = serialized,
                retryCount = 0,
                nextRetryTime = Instant.EPOCH,
            )
        )
        Log.d(TAG, "Queued payload for retry (queue depth: ${dao.countPending()})")
    }

    /**
     * Returns the next [limit] items whose [PendingUploadEntity.nextRetryTime] is in the past,
     * ordered oldest-first.
     */
    suspend fun getNextBatch(limit: Int): List<PendingUploadEntity> =
        dao.getNextBatch(limit = limit, now = Instant.now())

    /**
     * Deletes all items with IDs in [ids] from the queue.
     *
     * Call this after a successful upload batch to prevent re-sending items.
     */
    suspend fun markUploaded(ids: List<Long>) {
        dao.deleteUploaded(ids)
    }

    /**
     * Increments [PendingUploadEntity.retryCount] for the item with [id] and advances its
     * [PendingUploadEntity.nextRetryTime] using exponential back-off.
     *
     * If the item has already reached [MAX_RETRIES], it is deleted and a warning is logged.
     */
    suspend fun incrementRetry(id: Long) {
        val entity = dao.getById(id) ?: return
        if (entity.retryCount >= MAX_RETRIES) {
            Log.w(TAG, "Discarding queued payload after $MAX_RETRIES retries (id=$id)")
            dao.deleteUploaded(listOf(id))
            return
        }
        val backoffSeconds = minOf(BASE_BACKOFF_SECONDS * (1L shl entity.retryCount), MAX_BACKOFF_SECONDS)
        dao.update(
            entity.copy(
                retryCount = entity.retryCount + 1,
                nextRetryTime = Instant.now().plusSeconds(backoffSeconds),
            )
        )
        Log.d(TAG, "Retry ${entity.retryCount + 1}/$MAX_RETRIES for id=$id (next in ${backoffSeconds}s)")
    }

    /** Returns the total number of items currently in the upload queue. */
    suspend fun pendingCount(): Int = dao.countPending()
}
