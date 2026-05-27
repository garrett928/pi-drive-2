package ghart.space.pi_drive.shared.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for a user-controlled manual trip segment.
 *
 * The user explicitly starts and stops a manual trip from the dashboard. At most one
 * manual trip can be active ([isActive] = true) at any time — enforced by the
 * [ghart.space.pi_drive.shared.data.db.dao.ManualTripDao] before inserting a new trip.
 *
 * Statistics are accumulated in real time by the trip accumulator and are not
 * recomputed on read.
 *
 * @param id       Auto-generated primary key.
 * @param isActive True while the user has not yet ended this trip.
 */
@Entity(tableName = "manual_trips")
data class ManualTripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Instant,
    val distanceMi: Float = 0f,
    val durationMs: Long = 0L,
    val avgSpeedMph: Float = 0f,
    val maxSpeedMph: Float = 0f,
    val avgMpg: Float? = null,
    val isActive: Boolean = false,
)
