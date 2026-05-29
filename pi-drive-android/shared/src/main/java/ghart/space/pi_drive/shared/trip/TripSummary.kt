package ghart.space.pi_drive.shared.trip

/**
 * Immutable snapshot of accumulated trip statistics.
 *
 * Produced by [TripAccumulator.toSummary] and consumed by the dashboard and history screens.
 *
 * @param distanceMiles  Total distance travelled (speed > 0 only), in US miles.
 * @param durationMs     Wall-clock time while the vehicle was moving (speed > 0), in milliseconds.
 * @param avgSpeedMph    Average speed: [distanceMiles] ÷ ([durationMs] converted to hours).
 *                       Zero when [durationMs] is zero.
 * @param maxSpeedMph    Peak instantaneous speed observed during the trip, in mph.
 * @param avgMpg         Average fuel economy for the trip, in US MPG. Null when no fuel data
 *                       has been received or when [distanceMiles] is zero.
 * @param eventCount     Number of hard-brake or hard-accel driving events logged during the trip.
 */
data class TripSummary(
    val distanceMiles: Float,
    val durationMs: Long,
    val avgSpeedMph: Float,
    val maxSpeedMph: Float,
    val avgMpg: Float?,
    val eventCount: Int = 0,
)
