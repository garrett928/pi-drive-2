package ghart.space.pi_drive.shared.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for an automatically-detected driving trip.
 *
 * A trip becomes active the moment the engine-on / motion heuristic fires and
 * [endTime] is null while it is active. The trip is closed by setting [endTime]
 * when the engine-off / idle heuristic fires.
 *
 * Aggregate statistics ([distanceMi], [avgSpeedMph], etc.) are updated incrementally
 * by the trip accumulator as new snapshots arrive; they are not computed on read.
 *
 * @param id          Auto-generated primary key.
 * @param endTime     Null while the trip is active; set to the end timestamp on close.
 * @param syncStatus  Whether trip data has been uploaded to the telemetry server.
 */
@Entity(tableName = "auto_trips")
data class AutoTripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val distanceMi: Float = 0f,
    val durationMs: Long = 0L,
    val avgSpeedMph: Float = 0f,
    val maxSpeedMph: Float = 0f,
    val avgMpg: Float? = null,
    val eventCount: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
