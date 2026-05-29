package ghart.space.pi_drive.shared.trip

import android.util.Log
import ghart.space.pi_drive.shared.data.db.dao.ManualTripDao
import ghart.space.pi_drive.shared.data.db.entity.ManualTripEntity
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val TAG = "TripAccumulator"
private const val KMH_TO_MPH = 0.621371f
private const val LITERS_PER_GALLON = 3.78541f
private const val SAVE_INTERVAL_MS = 10_000L

/**
 * User-controlled trip tracker with Room persistence.
 *
 * Wraps a [TripAccumulator] to provide a manual trip that survives app restarts,
 * pauses when OBD disconnects, resumes on reconnect, and persists accumulated
 * state to Room every 10 seconds. The user resets the trip from the dashboard,
 * which zeroes all accumulators and starts a new Room record.
 *
 * ## Lifecycle
 * - **Init:** Restores the active trip from Room (if any) before observing snapshots.
 * - **Accumulating:** Updates on each [VehicleSnapshot] when connected and trip is active.
 * - **Pause:** Accumulation stops when [ConnectionState] is not [ConnectionState.Connected].
 * - **Resume:** Accumulation restarts when [ConnectionState.Connected] is observed.
 * - **Reset:** Closes the current trip in Room, zeroes all accumulators, opens a new record.
 * - **Save:** Persists state to Room every [SAVE_INTERVAL_MS] ms while a trip is active.
 *
 * @param snapshots       Vehicle snapshot stream from [VehicleDataSource].
 * @param connectionState Connection state stream from [VehicleDataSource].
 * @param dao             Room DAO for reading and writing [ManualTripEntity] records.
 * @param scope           Application-lifetime coroutine scope.
 * @param clock           Wall-clock source; injectable for testing.
 */
class ManualTripManager(
    private val snapshots: StateFlow<VehicleSnapshot>,
    private val connectionState: StateFlow<ConnectionState>,
    private val dao: ManualTripDao,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** The [TripAccumulator] for this manual trip. Starts paused; resumes on Connected. */
    private val accumulator = TripAccumulator().apply { pause() }

    /**
     * Persisted base values loaded from Room on restore. These represent what was
     * accumulated in previous sessions. In-session increments come from [accumulator].
     */
    private var baseDistanceMiles = 0f
    private var baseDurationMs = 0L
    private var baseMaxSpeedMph = 0f
    private var baseFuelLiters = 0f

    /** Instant the current trip was started (or restored). Null if no trip has been created. */
    @Volatile private var startInstant: Instant? = null

    /** Room row ID of the currently active trip. Null when no trip is active. */
    @Volatile private var activeTripId: Long? = null

    /** Timestamp of the most recent snapshot processed by the fuel tracker. */
    @Volatile private var prevSnapshotMs = 0L

    private val _state = MutableStateFlow(
        ManualTripState(
            isActive = false,
            distanceMiles = 0f,
            durationMs = 0L,
            avgSpeedMph = 0f,
            maxSpeedMph = 0f,
            avgMpg = null,
            startDate = null,
        )
    )

    /** Current manual trip state — updated on each snapshot and after each [reset]. */
    val state: StateFlow<ManualTripState> = _state.asStateFlow()

    init {
        scope.launch {
            // Restore persisted active trip before starting observation (sequential).
            val activeTrip = dao.getActive()
            if (activeTrip != null) {
                restoreFrom(activeTrip)
            }

            // Watch connection state: pause accumulation when OBD disconnects.
            launch {
                connectionState.collect { cs ->
                    if (cs is ConnectionState.Connected) {
                        accumulator.resume()
                    } else {
                        accumulator.pause()
                    }
                }
            }

            // Accumulate speed and fuel from each snapshot while a trip is active.
            launch {
                snapshots.collect { snap ->
                    if (activeTripId == null) return@collect
                    val ts = snap.timestamp.toEpochMilli()
                    val speedMph = snap.speedKmh?.times(KMH_TO_MPH) ?: 0f
                    accumulator.update(speedMph, ts)
                    if (prevSnapshotMs != 0L) {
                        val dtMs = ts - prevSnapshotMs
                        if (dtMs > 0) {
                            accumulator.fuelTracker.update(
                                mafGps = snap.mafGps,
                                fuelRateLph = snap.fuelRateLph,
                                speedKmh = snap.speedKmh,
                                dtMs = dtMs,
                            )
                        }
                    }
                    prevSnapshotMs = ts
                    _state.value = buildState()
                }
            }

            // Persist state to Room every SAVE_INTERVAL_MS while a trip is active.
            launch {
                while (true) {
                    delay(SAVE_INTERVAL_MS)
                    saveToDb()
                }
            }
        }
    }

    /**
     * Resets the manual trip counter.
     *
     * Closes the current trip in Room (marks it inactive), zeroes all in-memory and
     * base accumulators, then creates a new active trip record. The [state] updates
     * immediately to reflect the fresh trip.
     *
     * This is safe to call from any thread — it dispatches to [scope].
     */
    fun reset() {
        scope.launch {
            val oldId = activeTripId
            activeTripId = null // Prevent snapshot accumulation during the reset window.

            if (oldId != null) {
                val start = startInstant ?: Instant.now(clock)
                dao.update(buildEntity(id = oldId, start = start, isActive = false))
            }

            accumulator.reset()
            // Restore pause state if currently disconnected so we don't accumulate stale Δt.
            if (connectionState.value !is ConnectionState.Connected) accumulator.pause()

            baseDistanceMiles = 0f
            baseDurationMs = 0L
            baseMaxSpeedMph = 0f
            baseFuelLiters = 0f
            prevSnapshotMs = 0L

            startInstant = Instant.now(clock)
            val newId = dao.insert(ManualTripEntity(startTime = startInstant!!, isActive = true))
            activeTripId = newId

            Log.d(TAG, "ManualTripManager reset, new trip id=$newId")
            _state.value = buildState()
        }
    }

    /**
     * Restores in-memory state from a persisted [ManualTripEntity].
     *
     * Reconstructs [baseFuelLiters] from the saved average MPG and distance, so that
     * the running fuel average continues smoothly across app restarts.
     */
    private fun restoreFrom(entity: ManualTripEntity) {
        activeTripId = entity.id
        startInstant = entity.startTime
        baseDistanceMiles = entity.distanceMi
        baseDurationMs = entity.durationMs
        baseMaxSpeedMph = entity.maxSpeedMph
        baseFuelLiters = if (entity.avgMpg != null && entity.avgMpg > 0f && entity.distanceMi > 0f) {
            (entity.distanceMi / entity.avgMpg) * LITERS_PER_GALLON
        } else 0f
        Log.d(TAG, "ManualTripManager restored trip id=${entity.id}, dist=${entity.distanceMi} mi")
        _state.value = buildState()
    }

    private fun totalDistanceMiles() = baseDistanceMiles + accumulator.distanceMiles

    private fun totalDurationMs() = baseDurationMs + accumulator.movingDurationMs

    private fun totalMaxSpeedMph() = maxOf(baseMaxSpeedMph, accumulator.maxSpeedMph)

    private fun totalAvgSpeedMph(): Float {
        val dur = totalDurationMs()
        return if (dur == 0L) 0f else totalDistanceMiles() / (dur / 3_600_000f)
    }

    private fun computeAvgMpg(): Float? {
        val totalFuelLiters = baseFuelLiters + accumulator.fuelTracker.totalFuelLiters
        val totalDist = totalDistanceMiles()
        return if (totalFuelLiters <= 0f || totalDist <= 0f) null
        else totalDist / (totalFuelLiters / LITERS_PER_GALLON)
    }

    private fun buildState() = ManualTripState(
        isActive = activeTripId != null,
        distanceMiles = totalDistanceMiles(),
        durationMs = totalDurationMs(),
        avgSpeedMph = totalAvgSpeedMph(),
        maxSpeedMph = totalMaxSpeedMph(),
        avgMpg = computeAvgMpg(),
        startDate = startInstant?.atOffset(ZoneOffset.UTC)?.toLocalDate(),
    )

    private fun buildEntity(id: Long, start: Instant, isActive: Boolean) = ManualTripEntity(
        id = id,
        startTime = start,
        distanceMi = totalDistanceMiles(),
        durationMs = totalDurationMs(),
        avgSpeedMph = totalAvgSpeedMph(),
        maxSpeedMph = totalMaxSpeedMph(),
        avgMpg = computeAvgMpg(),
        isActive = isActive,
    )

    private suspend fun saveToDb() {
        val id = activeTripId ?: return
        val start = startInstant ?: return
        dao.update(buildEntity(id = id, start = start, isActive = true))
        Log.v(TAG, "ManualTripManager periodic save, dist=${totalDistanceMiles()} mi")
    }
}
