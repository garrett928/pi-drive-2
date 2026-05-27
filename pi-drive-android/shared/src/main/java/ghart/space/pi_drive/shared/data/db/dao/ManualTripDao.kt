package ghart.space.pi_drive.shared.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ghart.space.pi_drive.shared.data.db.entity.ManualTripEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [ManualTripEntity] — user-controlled trip segments.
 */
@Dao
interface ManualTripDao {

    /** Inserts a new manual trip and returns its auto-generated row ID. */
    @Insert
    suspend fun insert(trip: ManualTripEntity): Long

    /** Updates an existing manual trip row (matched by [ManualTripEntity.id]). */
    @Update
    suspend fun update(trip: ManualTripEntity)

    /**
     * Returns the currently-active manual trip ([ManualTripEntity.isActive] = true),
     * or null if none is active.
     */
    @Query("SELECT * FROM manual_trips WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ManualTripEntity?

    /**
     * Returns all manual trips as a [Flow], newest first.
     *
     * Emits a new list whenever any row is inserted or updated.
     */
    @Query("SELECT * FROM manual_trips ORDER BY startTime DESC")
    fun getAll(): Flow<List<ManualTripEntity>>
}
