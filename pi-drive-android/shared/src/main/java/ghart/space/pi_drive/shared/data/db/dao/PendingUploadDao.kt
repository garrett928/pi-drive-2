package ghart.space.pi_drive.shared.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import ghart.space.pi_drive.shared.data.db.entity.PendingUploadEntity
import java.time.Instant

/**
 * Room DAO for [PendingUploadEntity] — telemetry payloads awaiting upload.
 */
@Dao
interface PendingUploadDao {

    /** Enqueues a new payload for upload and returns its auto-generated row ID. */
    @Insert
    suspend fun insert(upload: PendingUploadEntity): Long

    /**
     * Returns up to [limit] upload items whose [PendingUploadEntity.nextRetryTime] is at or
     * before [now], ordered oldest-first so earlier failures are retried first.
     */
    @Query("SELECT * FROM pending_uploads WHERE nextRetryTime <= :now ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getNextBatch(limit: Int, now: Instant): List<PendingUploadEntity>

    /**
     * Removes a successfully-uploaded item from the queue.
     *
     * "Mark uploaded" means deletion — there is no intermediate state. Callers that
     * need to batch-delete multiple items can use [deleteUploaded].
     */
    @Delete
    suspend fun markUploaded(upload: PendingUploadEntity)

    /**
     * Batch-deletes all items whose [PendingUploadEntity.id] is in [ids].
     *
     * Returns the number of rows deleted. More efficient than calling [markUploaded]
     * in a loop when an entire batch succeeds at once.
     */
    @Query("DELETE FROM pending_uploads WHERE id IN (:ids)")
    suspend fun deleteUploaded(ids: List<Long>): Int

    /** Returns the total count of items currently in the upload queue. */
    @Query("SELECT COUNT(*) FROM pending_uploads")
    suspend fun countPending(): Int
}
