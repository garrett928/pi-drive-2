package ghart.space.pi_drive.shared.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ghart.space.pi_drive.shared.data.db.entity.SnapshotEntity
import java.time.Instant

/**
 * Room DAO for [SnapshotEntity] — vehicle telemetry snapshots.
 *
 * All [Instant] query parameters are converted to epoch milliseconds by the
 * [ghart.space.pi_drive.shared.data.db.Converters] registered at the database level.
 */
@Dao
interface SnapshotDao {

    /** Inserts a single snapshot and returns its auto-generated row ID. */
    @Insert
    suspend fun insert(snapshot: SnapshotEntity): Long

    /** Bulk-inserts a list of snapshots. */
    @Insert
    suspend fun insertAll(snapshots: List<SnapshotEntity>)

    /** Returns all snapshots for [tripId], ordered by ascending timestamp. */
    @Query("SELECT * FROM snapshots WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getByTripId(tripId: Long): List<SnapshotEntity>

    /**
     * Returns snapshots whose timestamp falls within [[from], [to]], inclusive,
     * ordered by ascending timestamp.
     */
    @Query("SELECT * FROM snapshots WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    suspend fun getByTimeRange(from: Instant, to: Instant): List<SnapshotEntity>

    /**
     * Deletes snapshots with a timestamp strictly before [before].
     *
     * Returns the number of rows deleted. Used for routine pruning to cap storage usage.
     */
    @Query("DELETE FROM snapshots WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Instant): Int
}
