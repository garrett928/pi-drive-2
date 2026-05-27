package ghart.space.pi_drive.shared.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ghart.space.pi_drive.shared.data.model.DataSource
import ghart.space.pi_drive.shared.data.model.DetectionStrategy
import ghart.space.pi_drive.shared.data.model.EventType
import java.time.Instant

/**
 * Room entity storing a hard-acceleration or hard-braking driving event.
 *
 * Mirrors [ghart.space.pi_drive.shared.data.model.DrivingEvent] with two differences:
 * - The [ghart.space.pi_drive.shared.data.model.LatLng] location is flattened into
 *   [locationLat] and [locationLng] to avoid a nested object.
 * - [sources] is stored as a comma-separated string via
 *   [ghart.space.pi_drive.shared.data.db.Converters].
 *
 * Indexed on [timestamp] for time-range queries and on [tripId] for trip association.
 *
 * @param id     Auto-generated primary key.
 * @param tripId FK to [AutoTripEntity.id]; null if the event occurred outside a detected trip.
 */
@Entity(
    tableName = "driving_events",
    indices = [
        Index("timestamp"),
        Index("tripId"),
    ],
)
data class DrivingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val strategy: DetectionStrategy,
    val type: EventType,
    val timestamp: Instant,
    val durationMs: Long,
    val rateMphS: Float? = null,
    val peakG: Float? = null,
    val peakAccelMps2: Float,
    val startSpeedMph: Float,
    val endSpeedMph: Float,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val sources: Set<DataSource>,
)
