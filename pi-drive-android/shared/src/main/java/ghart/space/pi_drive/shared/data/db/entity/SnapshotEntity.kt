package ghart.space.pi_drive.shared.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity storing a single vehicle telemetry snapshot.
 *
 * Mirrors [ghart.space.pi_drive.shared.data.model.VehicleSnapshot] with the addition of
 * a database primary key and an optional [tripId] foreign key that links this snapshot
 * to an [AutoTripEntity] once the trip-detection algorithm assigns it — either in
 * real-time or retroactively.
 *
 * Indexed on [timestamp] for time-range queries and on [tripId] for trip playback.
 * FK cascades delete so that removing a trip also removes its snapshots.
 *
 * @param id        Auto-generated primary key.
 * @param tripId    FK to [AutoTripEntity.id]; null for unassigned or manual-trip snapshots.
 * @param timestamp When this snapshot was captured (stored as epoch milliseconds via
 *                  [ghart.space.pi_drive.shared.data.db.Converters]).
 */
@Entity(
    tableName = "snapshots",
    foreignKeys = [
        ForeignKey(
            entity = AutoTripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("tripId"),
        Index("timestamp"),
    ],
)
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val timestamp: Instant,
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val intakeAirTempC: Int? = null,
    val throttlePct: Float? = null,
    val fuelLevelPct: Float? = null,
    val oilTempC: Int? = null,
    val mafGps: Float? = null,
    val fuelRateLph: Float? = null,
    val batteryVoltage: Float? = null,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsSpeedMps: Float? = null,
    val accelRateMphS: Float? = null,
    val gForce: Float? = null,
)
