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
 * Detects hard acceleration and braking events using a multi-source g-force strategy.
 *
 * Consumes a [StateFlow<VehicleSnapshot>] (for OBD and GPS speed) and an optional
 * accelerometer channel, then produces a [Flow<DrivingEvent>] using the
 * [DetectionStrategy.G_FORCE] strategy.
 *
 * ## Cross-validation rule
 * An event fires only when **≥ 2 of 3 sources** independently agree that the g-force
 * threshold is exceeded. This prevents false positives from:
 * - OBD speed noise (single-source spikes)
 * - Phone drops (accelerometer spike with no OBD/GPS change)
 * - GPS multipath errors (GPS spike without OBD confirmation)
 *
 * ## Sources
 * - **OBD:** `(speedKmh_now - speedKmh_prev) / dt` → m/s² → g
 * - **GPS:** `(gpsSpeedMps_now - gpsSpeedMps_prev) / dt` → m/s² → g
 * - **Accelerometer:** [accelMps2Flow] (longitudinal axis, already low-pass filtered
 *   and calibrated by [AccelerometerManager])
 *
 * ## Severity
 * If ≥ 2 sources exceed [DetectionConfig.gForceSevereBrakeThreshold], the
 * [DrivingEvent.peakG] is tagged at the severe level.
 *
 * @param snapshots    Live vehicle snapshot stream.
 * @param accelMps2Flow Longitudinal accelerometer reading in m/s². Null means the
 *                      accelerometer source is unavailable (e.g. emulator). Detection
 *                      then requires only 1 of the remaining 2 sources to agree — but
 *                      only if the other source is present and confirms.
 * @param config       Detection thresholds and timing parameters.
 */
class GForceDetector(
    private val snapshots: StateFlow<VehicleSnapshot>,
    private val accelMps2Flow: StateFlow<Float?>? = null,
    private val configFlow: StateFlow<DetectionConfig> = MutableStateFlow(DetectionConfig()),
) {

    companion object {
        private const val TAG = "GForceDetector"
        private const val G = 9.81f
        private const val MS_TO_G = 1f / G
    }

    private enum class State { IDLE, DETECTING, COOLDOWN }

    /**
     * Returns a cold [Flow] that emits a [DrivingEvent] each time the cross-validation
     * rule confirms a hard-acceleration or hard-braking event.
     */
    fun events(): Flow<DrivingEvent> = flow {
        if (!configFlow.value.gForceEnabled) return@flow

        var prevSnapshot: VehicleSnapshot? = null

        var state = State.IDLE
        var detectStart = Instant.EPOCH
        var startSpeedMph = 0f
        var peakG = 0f
        var detectedType = EventType.HARD_BRAKE
        var cooldownStart = Instant.EPOCH

        snapshots.collect { snap ->
            val config = configFlow.value   // re-read on each tick so user changes apply immediately
            val now = snap.timestamp
            val prev = prevSnapshot
            prevSnapshot = snap

            if (prev == null) return@collect

            val dtMs = Duration.between(prev.timestamp, now).toMillis()
            if (dtMs <= 0L) return@collect
            val dtS = dtMs / 1000f

            // ── Compute g-force magnitude per source ──────────────────────────
            val obdAccelMps2 = if (prev.speedKmh != null && snap.speedKmh != null) {
                val dVKmh = (snap.speedKmh - prev.speedKmh).toFloat()
                dVKmh * (1000f / 3600f) / dtS  // km/h → m/s, then /dt
            } else null

            val gpsAccelMps2 = if (prev.gpsSpeedMps != null && snap.gpsSpeedMps != null) {
                (snap.gpsSpeedMps - prev.gpsSpeedMps) / dtS
            } else null

            val accelSensorMps2 = accelMps2Flow?.value

            // ── Cross-validate: count sources exceeding threshold ─────────────
            val brakeG = config.gForceHardBrakeThreshold
            val accelG = config.gForceHardAccelThreshold
            val severeG = config.gForceSevereBrakeThreshold

            data class SourceVote(val gForce: Float, val source: DataSource)

            val votes = buildList {
                if (obdAccelMps2 != null) add(SourceVote(abs(obdAccelMps2) * MS_TO_G, DataSource.OBD))
                if (gpsAccelMps2 != null) add(SourceVote(abs(gpsAccelMps2) * MS_TO_G, DataSource.GPS))
                if (accelSensorMps2 != null) add(SourceVote(abs(accelSensorMps2) * MS_TO_G, DataSource.ACCELEROMETER))
            }

            // Signed g-force from OBD to determine direction (brake vs accel)
            val signedGFromObd = obdAccelMps2?.let { it * MS_TO_G }
            val signedGFromGps = gpsAccelMps2?.let { it * MS_TO_G }
            // Negative g = braking, positive g = acceleration
            val isNegative = (signedGFromObd ?: signedGFromGps ?: 0f) < 0f

            val exceedsHardBrake = votes.count { it.gForce >= brakeG }
            val exceedsHardAccel = votes.count { it.gForce >= accelG }
            val exceedsSeVere = votes.count { it.gForce >= severeG }

            val brakeTriggered = isNegative && exceedsHardBrake >= 2
            val accelTriggered = !isNegative && exceedsHardAccel >= 2
            val exceeded = brakeTriggered || accelTriggered
            val currentType = if (brakeTriggered) EventType.HARD_BRAKE else EventType.HARD_ACCEL

            val usedSources = votes.map { it.source }.toSet()
            val peakGNow = votes.maxOfOrNull { it.gForce } ?: 0f

            Log.d(TAG, "G-force sources: OBD=${obdAccelMps2?.let { abs(it) * MS_TO_G }?.let { "%.3f".format(it) } ?: "n/a"}g " +
                "GPS=${gpsAccelMps2?.let { abs(it) * MS_TO_G }?.let { "%.3f".format(it) } ?: "n/a"}g " +
                "Accel=${accelSensorMps2?.let { abs(it) * MS_TO_G }?.let { "%.3f".format(it) } ?: "n/a"}g")

            when (state) {
                State.IDLE -> {
                    if (exceeded) {
                        state = State.DETECTING
                        detectStart = now
                        startSpeedMph = prev.speedKmh?.times(0.621371f) ?: 0f
                        peakG = peakGNow
                        detectedType = currentType
                        Log.d(TAG, "Detecting ${currentType.name}, g=$peakGNow")
                    }
                }

                State.DETECTING -> {
                    val stillDetecting = exceeded && currentType == detectedType
                    if (!stillDetecting) {
                        Log.d(TAG, "G-force detection cancelled")
                        state = State.IDLE
                    } else {
                        peakG = maxOf(peakG, peakGNow)

                        val durationMs = Duration.between(detectStart, now).toMillis()
                        if (durationMs >= config.minEventDurationMs) {
                            val isSevere = exceedsSeVere >= 2
                            Log.i(TAG, "${if (detectedType == EventType.HARD_BRAKE) "Hard brake" else "Hard accel"} " +
                                "detected via G-force, peak=${peakG}g${if (isSevere) " [SEVERE]" else ""}, " +
                                "sources=$usedSources, duration=${durationMs}ms")
                            emit(
                                DrivingEvent(
                                    strategy = DetectionStrategy.G_FORCE,
                                    type = detectedType,
                                    timestamp = detectStart,
                                    durationMs = durationMs,
                                    rateMphS = null,
                                    peakG = peakG,
                                    peakAccelMps2 = peakG * G,
                                    startSpeedMph = startSpeedMph,
                                    endSpeedMph = snap.speedKmh?.times(0.621371f) ?: 0f,
                                    location = snap.gpsLat?.let { lat ->
                                        snap.gpsLng?.let { lng -> LatLng(lat, lng) }
                                    },
                                    sources = usedSources,
                                )
                            )
                            state = State.COOLDOWN
                            cooldownStart = now
                        }
                    }
                }

                State.COOLDOWN -> {
                    if (Duration.between(cooldownStart, now).toMillis() >= config.cooldownMs) {
                        Log.d(TAG, "G-force cooldown over")
                        state = State.IDLE
                    }
                }
            }
        }
    }
}
