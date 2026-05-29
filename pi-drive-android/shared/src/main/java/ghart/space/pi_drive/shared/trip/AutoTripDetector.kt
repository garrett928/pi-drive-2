package ghart.space.pi_drive.shared.trip

import android.util.Log
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

private const val TAG = "TripAccumulator"
private const val KMH_TO_MPH = 0.621371f

/** Default time without OBD connection before the active trip is closed (5 minutes). */
const val AUTO_TRIP_END_TIMEOUT_MS = 5L * 60L * 1_000L

/**
 * Detects trip boundaries from OBD connection events and accumulates driving statistics.
 *
 * ## Trip lifecycle
 * - **Start:** First [ConnectionState.Connected] event when no trip is active.
 * - **Pause:** Any non-Connected state. Distance and duration stop accumulating.
 *   A countdown timer starts; if [endTimeoutMs] elapses without reconnection the trip ends.
 * - **Resume:** [ConnectionState.Connected] arrives before [endTimeoutMs] expires.
 *   The countdown is cancelled and accumulation restarts.
 * - **End:** [endTimeoutMs] expires without reconnection. The finalized summary is written
 *   to Room and [currentTrip] becomes `null`.
 *
 * Both "short drops" (Bluetooth blip, signal loss < timeout) and intentional disconnects
 * (engine off, ignition key) are handled correctly: short drops become pauses; long drops
 * close the trip.
 *
 * ## Accumulation
 * Speed samples are integrated using [TripAccumulator] (same algorithm as the manual trip).
 * Fuel data from [VehicleSnapshot.fuelRateLph] or [VehicleSnapshot.mafGps] feeds [FuelTracker]
 * for average MPG.
 *
 * @param snapshots      Vehicle snapshot stream from [VehicleDataSource].
 * @param connectionState Connection state stream from [VehicleDataSource].
 * @param dao            Room DAO for reading and writing [AutoTripEntity] records.
 * @param scope          Application-lifetime coroutine scope.
 * @param clock          Wall-clock source; injectable for testing.
 * @param endTimeoutMs   Milliseconds to wait after disconnection before ending the trip.
 */
class AutoTripDetector(
    private val snapshots: StateFlow<VehicleSnapshot>,
    private val connectionState: StateFlow<ConnectionState>,
    private val dao: AutoTripDao,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    val endTimeoutMs: Long = AUTO_TRIP_END_TIMEOUT_MS,
) {

    /** Accumulates speed samples into distance, duration, and fuel for the active trip. */
    private val accumulator = TripAccumulator().apply { pause() }

    /** Room row ID of the currently-active trip; null when no trip is open. */
    @Volatile private var activeTripId: Long? = null

    /** Wall-clock start time of the currently-active trip. */
    @Volatile private var tripStart: Instant? = null

    /**
     * The pending "end trip" job launched when the OBD connection drops.
     * Cancelled if the connection is restored before [endTimeoutMs] elapses.
     */
    @Volatile private var endJob: Job? = null

    /** Epoch-ms timestamp of the most recent snapshot fed to the fuel tracker. */
    @Volatile private var prevSnapshotMs = 0L

    private val _currentTrip = MutableStateFlow<AutoTripState?>(null)

    /**
     * Current auto-trip state, updated on every snapshot while a trip is active.
     * Emits `null` when no trip is in progress.
     */
    val currentTrip: StateFlow<AutoTripState?> = _currentTrip.asStateFlow()

    init {
        scope.launch { watchConnectionState() }
        scope.launch { watchSnapshots() }
    }

    // ── Connection state handling ─────────────────────────────────────────────

    private suspend fun watchConnectionState() {
        connectionState.collect { cs ->
            when (cs) {
                is ConnectionState.Connected -> handleConnected()
                else -> handleDisconnected()
            }
        }
    }

    /**
     * Called when the OBD adapter connects.
     *
     * - If an end-timeout is pending (short reconnect within the window): cancel the timer,
     *   resume the existing trip.
     * - If no trip is active at all: start a new trip.
     */
    private suspend fun handleConnected() {
        val job = endJob
        if (job != null) {
            // Reconnected before the end timeout — cancel the timer and resume.
            job.cancel()
            endJob = null
            accumulator.resume()
            Log.d(TAG, "Auto trip resumed after short disconnect")
        } else if (activeTripId == null) {
            startNewTrip()
        }
        // If endJob is null but activeTripId != null: was already connected (duplicate event).
    }

    /**
     * Called when the OBD adapter disconnects (any non-Connected state).
     *
     * Pauses accumulation and starts the end-timeout countdown. Idempotent: if a timeout
     * is already running (e.g., [ConnectionState.Connecting] fires after [ConnectionState.Disconnected])
     * no second timer is started.
     */
    private fun handleDisconnected() {
        if (activeTripId == null || endJob != null) return

        accumulator.pause()
        endJob = scope.launch {
            delay(endTimeoutMs)
            finalizeTrip()
            endJob = null
        }
        Log.d(TAG, "Auto trip paused, end timer started (${endTimeoutMs}ms)")
    }

    // ── Trip lifecycle ────────────────────────────────────────────────────────

    private suspend fun startNewTrip() {
        val now = Instant.now(clock)
        tripStart = now
        prevSnapshotMs = 0L
        accumulator.reset() // TripAccumulator.reset() unpauses the accumulator
        val id = dao.insert(AutoTripEntity(startTime = now))
        activeTripId = id
        _currentTrip.value = buildState()
        Log.d(TAG, "Auto trip started: id=$id")
    }

    /**
     * Writes the finalized trip summary to Room and clears in-memory state.
     *
     * Idempotent: a second call while no trip is active is silently ignored.
     */
    private suspend fun finalizeTrip() {
        val id = activeTripId ?: return
        val start = tripStart ?: Instant.now(clock)
        val summary = accumulator.toSummary()
        dao.update(
            AutoTripEntity(
                id = id,
                startTime = start,
                endTime = Instant.now(clock),
                distanceMi = summary.distanceMiles,
                durationMs = summary.durationMs,
                avgSpeedMph = summary.avgSpeedMph,
                maxSpeedMph = summary.maxSpeedMph,
                avgMpg = summary.avgMpg,
                eventCount = 0,
            )
        )
        activeTripId = null
        tripStart = null
        _currentTrip.value = null
        Log.d(
            TAG,
            "Auto trip ended: id=$id, dist=${summary.distanceMiles} mi, dur=${summary.durationMs}ms",
        )
    }

    // ── Snapshot accumulation ─────────────────────────────────────────────────

    private suspend fun watchSnapshots() {
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
            _currentTrip.value = buildState()
        }
    }

    // ── State builder ─────────────────────────────────────────────────────────

    private fun buildState(): AutoTripState? {
        val id = activeTripId ?: return null
        val start = tripStart ?: return null
        return AutoTripState(
            tripId = id,
            startTime = start,
            distanceMiles = accumulator.distanceMiles,
            durationMs = accumulator.movingDurationMs,
            avgSpeedMph = accumulator.avgSpeedMph,
            maxSpeedMph = accumulator.maxSpeedMph,
            avgMpg = accumulator.fuelTracker.tripAverageMpg(accumulator.distanceMiles),
            eventCount = 0,
        )
    }
}
