package ghart.space.pi_drive.shared.detection

import android.util.Log
import ghart.space.pi_drive.shared.data.model.DataSource
import ghart.space.pi_drive.shared.data.model.DetectionStrategy
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.data.model.LatLng
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Detects hard acceleration and hard braking events from OBD/GPS speed deltas.
 *
 * Consumes a [StateFlow<VehicleSnapshot>] and produces a [Flow<DrivingEvent>] using
 * the [DetectionStrategy.ACCELERATION] strategy.
 *
 * ## Algorithm
 * Each snapshot is compared to the previous one to compute:
 * `rate_mph_s = (speed_now_mph - speed_prev_mph) / delta_time_s`
 *
 * The detector runs a four-state machine:
 * - **IDLE** → watching for threshold crossing.
 * - **DETECTING** → threshold exceeded; accumulating duration and tracking peak rate.
 *   Reverts to IDLE if the rate drops below threshold before [DetectionConfig.minEventDurationMs].
 * - **EVENT** → duration met; a [DrivingEvent] is emitted and the machine moves to COOLDOWN.
 * - **COOLDOWN** → waits [DetectionConfig.cooldownMs] before returning to IDLE, preventing
 *   double-detection of the same braking event.
 *
 * ## Speed sources
 * - **Primary:** OBD speed (`VehicleSnapshot.speedKmh`, converted km/h → mph × 0.621371).
 * - **Fallback:** GPS speed (`VehicleSnapshot.gpsSpeedMps`, m/s → mph × 2.23694),
 *   used when no OBD speed update has been received for > [OBD_STALE_THRESHOLD_MS].
 *
 * @param snapshots Upstream vehicle data stream (must be a hot StateFlow).
 * @param config    Detection thresholds and timing parameters.
 */
class AccelerationDetector(
    private val snapshots: StateFlow<VehicleSnapshot>,
    private val configFlow: StateFlow<DetectionConfig> = MutableStateFlow(DetectionConfig()),
) {

    companion object {
        /** OBD speed older than this is considered stale; GPS fallback kicks in. */
        const val OBD_STALE_THRESHOLD_MS = 500L

        private const val TAG = "AccelDetector"

        /** mph/s → m/s² conversion factor. */
        private const val MPH_S_TO_MPS2 = 0.44704f
    }

    private enum class State { IDLE, DETECTING, COOLDOWN }

    /**
     * Returns a cold [Flow] that collects [snapshots] and emits a [DrivingEvent]
     * each time a hard-acceleration or hard-braking event is confirmed.
     *
     * The flow runs until its collection scope is cancelled.
     */
    fun events(): Flow<DrivingEvent> = flow {
        var prevSpeedMph: Float? = null
        var prevTimestamp: Instant? = null
        var lastOBDTimestamp: Instant? = null

        var state = State.IDLE
        var detectStart = Instant.EPOCH
        var startSpeedMph = 0f
        var peakRateMphS = 0f
        var detectedType = EventType.HARD_BRAKE
        var cooldownStart = Instant.EPOCH

        snapshots.collect { snap ->
            val config = configFlow.value   // re-read on each tick so user changes apply immediately
            if (!config.accelEnabled) return@collect

            val now = snap.timestamp

            // ── Determine speed in mph (OBD primary, GPS fallback) ────────────
            val obdMph = snap.speedKmh?.times(0.621371f)
            if (obdMph != null) lastOBDTimestamp = now

            val obdStale = lastOBDTimestamp == null ||
                Duration.between(lastOBDTimestamp, now).toMillis() > OBD_STALE_THRESHOLD_MS

            val speedMph: Float?
            val sources: Set<DataSource>
            when {
                obdMph != null -> {
                    speedMph = obdMph
                    sources = setOf(DataSource.OBD)
                }
                snap.gpsSpeedMps != null && obdStale -> {
                    speedMph = snap.gpsSpeedMps * 2.23694f
                    sources = setOf(DataSource.GPS)
                }
                else -> {
                    speedMph = null
                    sources = emptySet()
                }
            }

            // Need two valid speed samples to compute a rate
            if (speedMph == null) {
                return@collect
            }
            val prev = prevSpeedMph
            val prevTs = prevTimestamp
            prevSpeedMph = speedMph
            prevTimestamp = now

            if (prev == null || prevTs == null) return@collect

            val dtMs = Duration.between(prevTs, now).toMillis()
            if (dtMs <= 0L) return@collect

            val rate = (speedMph - prev) / (dtMs / 1000f)

            // ── State machine ─────────────────────────────────────────────────
            val exceedsAccel = rate >= config.accelHardAccelThreshold
            val exceedsBrake = rate <= -config.accelHardBrakeThreshold
            val exceeded = exceedsAccel || exceedsBrake
            val currentType = if (exceedsAccel) EventType.HARD_ACCEL else EventType.HARD_BRAKE

            when (state) {
                State.IDLE -> {
                    if (exceeded) {
                        state = State.DETECTING
                        detectStart = now
                        startSpeedMph = prev
                        peakRateMphS = rate
                        detectedType = currentType
                        Log.d(TAG, "Detecting ${currentType.name}, rate=${rate} mph/s")
                    }
                }

                State.DETECTING -> {
                    val stillDetecting = exceeded && currentType == detectedType
                    if (!stillDetecting) {
                        Log.d(TAG, "Detection cancelled (rate=${rate} mph/s)")
                        state = State.IDLE
                    } else {
                        peakRateMphS = if (detectedType == EventType.HARD_ACCEL)
                            maxOf(peakRateMphS, rate) else minOf(peakRateMphS, rate)

                        val durationMs = Duration.between(detectStart, now).toMillis()
                        if (durationMs >= config.minEventDurationMs) {
                            val absRate = abs(peakRateMphS)
                            Log.i(TAG, "${if (detectedType == EventType.HARD_BRAKE) "Hard brake" else "Hard accel"} " +
                                "detected, rate=${absRate} mph/s, duration=${durationMs}ms")
                            emit(
                                DrivingEvent(
                                    strategy = DetectionStrategy.ACCELERATION,
                                    type = detectedType,
                                    timestamp = detectStart,
                                    durationMs = durationMs,
                                    rateMphS = absRate,
                                    peakG = null,
                                    peakAccelMps2 = absRate * MPH_S_TO_MPS2,
                                    startSpeedMph = startSpeedMph,
                                    endSpeedMph = speedMph,
                                    location = snap.gpsLat?.let { lat ->
                                        snap.gpsLng?.let { lng -> LatLng(lat, lng) }
                                    },
                                    sources = sources,
                                )
                            )
                            state = State.COOLDOWN
                            cooldownStart = now
                        }
                    }
                }

                State.COOLDOWN -> {
                    if (Duration.between(cooldownStart, now).toMillis() >= config.cooldownMs) {
                        Log.d(TAG, "Cooldown over, resuming detection")
                        state = State.IDLE
                    }
                }
            }
        }
    }
}
