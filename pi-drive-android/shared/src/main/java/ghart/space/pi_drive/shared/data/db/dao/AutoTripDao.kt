package ghart.space.pi_drive.shared.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Room DAO for [AutoTripEntity] — automatically-detected driving trips.
 */
@Dao
interface AutoTripDao {

    /** Inserts a new trip and returns its auto-generated row ID. */
    @Insert
    suspend fun insert(trip: AutoTripEntity): Long

    /** Updates an existing trip row (matched by [AutoTripEntity.id]). */
    @Update
    suspend fun update(trip: AutoTripEntity)

    /**
     * Returns the currently-active trip (one whose [AutoTripEntity.endTime] is null),
     * or null if no trip is currently active.
     *
     * At most one trip should be active at any time; LIMIT 1 guards against data
     * inconsistency without hiding it silently.
     */
    @Query("SELECT * FROM auto_trips WHERE endTime IS NULL LIMIT 1")
    suspend fun getActive(): AutoTripEntity?

    /**
     * Returns all trips as a [Flow], newest first.
     *
     * Emits a new list whenever any trip row is inserted or updated, making it
     * suitable for driving the trip history screen via [androidx.lifecycle.ViewModel].
     */
    @Query("SELECT * FROM auto_trips ORDER BY startTime DESC")
    fun getAll(): Flow<List<AutoTripEntity>>

    /**
     * Returns trips whose [AutoTripEntity.startTime] falls within [[from], [to]],
     * inclusive, ordered newest-first.
     */
    @Query("SELECT * FROM auto_trips WHERE startTime >= :from AND startTime <= :to ORDER BY startTime DESC")
    suspend fun getByDateRange(from: Instant, to: Instant): List<AutoTripEntity>

    /** Deletes [trip] from the database. */
    @Delete
    suspend fun delete(trip: AutoTripEntity)
}
