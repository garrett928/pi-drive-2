package ghart.space.pi_drive.shared.data.model

import java.time.LocalDate

/**
 * UI state snapshot for the user-controlled manual trip segment.
 *
 * Produced by [ghart.space.pi_drive.shared.trip.ManualTripManager] and consumed by the
 * dashboard MPG row. All numeric fields represent totals accumulated since the last
 * [ghart.space.pi_drive.shared.trip.ManualTripManager.reset] call, combining persisted base
 * values loaded from Room with live in-memory increments from the current session.
 *
 * @param isActive       True when a manual trip is in progress (a [reset] has been called
 *                       at least once and the trip has not been replaced by a subsequent reset).
 * @param distanceMiles  Total distance driven while this trip is active, in US miles.
 *                       Counts only movement intervals (speed > 0).
 * @param durationMs     Wall-clock time while the vehicle was moving (speed > 0), in milliseconds.
 * @param avgSpeedMph    Average moving speed: distanceMiles ÷ durationHours. Zero when no movement.
 * @param maxSpeedMph    Peak instantaneous speed observed during this trip, in mph.
 * @param avgMpg         Trip-average fuel economy in US MPG. Null when no fuel sensor data
 *                       has been received or when [distanceMiles] is zero.
 * @param startDate      Calendar date (UTC) when the trip was last reset. Null if no trip yet.
 */
data class ManualTripState(
    val isActive: Boolean,
    val distanceMiles: Float,
    val durationMs: Long,
    val avgSpeedMph: Float,
    val maxSpeedMph: Float,
    val avgMpg: Float?,
    val startDate: LocalDate?,
)
