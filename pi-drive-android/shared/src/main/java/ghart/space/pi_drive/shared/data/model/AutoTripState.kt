package ghart.space.pi_drive.shared.data.model

import java.time.Instant

/**
 * Immutable snapshot of the currently-active auto-detected trip.
 *
 * Published by [ghart.space.pi_drive.shared.trip.AutoTripManager] as a
 * [kotlinx.coroutines.flow.StateFlow] while a trip is in progress. The value is `null`
 * when no trip is active (e.g., vehicle off or no OBD connection ever established).
 *
 * @param tripId       Room row ID of the [ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity].
 * @param startTime    When the trip started (engine-on / first connected).
 * @param distanceMiles Accumulated moving distance in US miles.
 * @param durationMs   Accumulated moving time in milliseconds (stopped time excluded).
 * @param avgSpeedMph  Average speed over the moving portion: distance ÷ moving hours.
 * @param maxSpeedMph  Peak instantaneous speed observed so far.
 * @param avgMpg       Trip-average fuel economy, or null if no fuel data is available.
 * @param eventCount   Number of hard-event alerts recorded against this trip.
 */
data class AutoTripState(
    val tripId: Long,
    val startTime: Instant,
    val distanceMiles: Float,
    val durationMs: Long,
    val avgSpeedMph: Float,
    val maxSpeedMph: Float,
    val avgMpg: Float?,
    val eventCount: Int,
)
