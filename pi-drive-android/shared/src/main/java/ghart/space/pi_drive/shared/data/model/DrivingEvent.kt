package ghart.space.pi_drive.shared.data.model

import java.time.Instant

/**
 * Which detection algorithm produced the event.
 *
 * - [ACCELERATION]: OBD speed deltas converted to mph/s. Works without accelerometer.
 * - [G_FORCE]: Accelerometer + GPS fusion. More accurate but requires calibration.
 */
enum class DetectionStrategy { ACCELERATION, G_FORCE }

/**
 * Classification of the driving event.
 *
 * - [HARD_ACCEL]: Rapid positive acceleration above the configured threshold.
 * - [HARD_BRAKE]: Rapid negative acceleration (deceleration) above the threshold.
 */
enum class EventType { HARD_ACCEL, HARD_BRAKE }

/**
 * Which sensor(s) contributed data to this event's detection.
 */
enum class DataSource { OBD, GPS, ACCELEROMETER }

/**
 * A simple latitude/longitude pair, independent of any mapping SDK.
 *
 * Used to record the vehicle's position at the time of a [DrivingEvent]
 * without introducing a Maps SDK dependency in the data model.
 *
 * @param lat Latitude in decimal degrees (positive = north).
 * @param lng Longitude in decimal degrees (positive = east).
 */
data class LatLng(val lat: Double, val lng: Double)

/**
 * A single hard-acceleration or hard-braking event detected during a trip.
 *
 * Logged to the Room database, included in telemetry uploads, and surfaced
 * as a toast alert on the phone and Android Auto screens.
 *
 * @param strategy        Detection algorithm that produced this event.
 * @param type            Whether this was a hard acceleration or hard brake.
 * @param timestamp       When the event began.
 * @param durationMs      How long the threshold was exceeded, in milliseconds.
 * @param rateMphS        Speed-change rate in mph/s (ACCELERATION strategy only; null for G_FORCE).
 * @param peakG           Peak g-force magnitude (G_FORCE strategy only; null for ACCELERATION).
 * @param peakAccelMps2   Peak acceleration in m/s² (populated by both strategies).
 * @param startSpeedMph   Vehicle speed at the start of the event, in mph.
 * @param endSpeedMph     Vehicle speed at the end of the event, in mph.
 * @param location        GPS position at event start, or null if GPS is unavailable.
 * @param sources         Set of data sources that contributed to detection.
 */
data class DrivingEvent(
    val strategy: DetectionStrategy,
    val type: EventType,
    val timestamp: Instant,
    val durationMs: Long,
    val rateMphS: Float?,
    val peakG: Float?,
    val peakAccelMps2: Float,
    val startSpeedMph: Float,
    val endSpeedMph: Float,
    val location: LatLng?,
    val sources: Set<DataSource>,
)
