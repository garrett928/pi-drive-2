package ghart.space.pi_drive.shared.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.model.EventType
import java.time.Instant

/**
 * Room DAO for [DrivingEventEntity] — hard acceleration and hard braking events.
 */
@Dao
interface DrivingEventDao {

    /** Inserts a single driving event and returns its auto-generated row ID. */
    @Insert
    suspend fun insert(event: DrivingEventEntity): Long

    /** Returns all events whose timestamp falls within [[from], [to]], inclusive. */
    @Query("SELECT * FROM driving_events WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    suspend fun getByTimeRange(from: Instant, to: Instant): List<DrivingEventEntity>

    /** Returns all events associated with [tripId], ordered by ascending timestamp. */
    @Query("SELECT * FROM driving_events WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getByTripId(tripId: Long): List<DrivingEventEntity>

    /**
     * Counts events of [type] whose timestamp falls within [[from], [to]], inclusive.
     *
     * Used by the trip accumulator to compute the per-trip event count in
     * [ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity.eventCount].
     */
    @Query("SELECT COUNT(*) FROM driving_events WHERE type = :type AND timestamp >= :from AND timestamp <= :to")
    suspend fun countByTypeAndTimeRange(type: EventType, from: Instant, to: Instant): Int
}
