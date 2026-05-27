package ghart.space.pi_drive.shared.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for a telemetry payload queued for upload to the remote server.
 *
 * Failed uploads are retried with exponential back-off: the uploader increments
 * [retryCount] and sets [nextRetryTime] to a future instant. Items with
 * [nextRetryTime] in the past (≤ now) are eligible for the next upload batch.
 *
 * Successfully uploaded items are deleted — there is no "uploaded" state in the table.
 * [Instant.EPOCH] (epoch = 0) as [nextRetryTime] means "retry immediately."
 *
 * @param id            Auto-generated primary key.
 * @param payload       Serialized JSON body for the telemetry endpoint.
 * @param nextRetryTime Earliest instant at which this item should be retried.
 */
@Entity(tableName = "pending_uploads")
data class PendingUploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    val payload: String,
    val retryCount: Int = 0,
    val nextRetryTime: Instant = Instant.EPOCH,
)
