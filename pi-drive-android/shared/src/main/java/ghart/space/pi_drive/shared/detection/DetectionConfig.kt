package ghart.space.pi_drive.shared.detection

/**
 * Configuration knobs for both detection strategies (acceleration and G-force).
 *
 * Passed to [AccelerationDetector] and [GForceDetector] at construction time.
 * All thresholds have tuned defaults; individual fields can be overridden for
 * testing or user-configurable settings (Phase 8).
 *
 * @param accelEnabled              Whether the mph/s acceleration strategy is active.
 * @param accelHardAccelThreshold   Positive rate threshold for hard acceleration in mph/s.
 * @param accelHardBrakeThreshold   Absolute rate threshold for hard braking in mph/s
 *                                  (applied to the absolute value of a negative rate).
 * @param gForceEnabled             Whether the g-force sensor-fusion strategy is active.
 * @param gForceHardAccelThreshold  Hard-acceleration threshold in g.
 * @param gForceHardBrakeThreshold  Hard-braking threshold in g.
 * @param gForceSevereBrakeThreshold Severe-braking threshold in g (subset of hard-brake events).
 * @param minEventDurationMs        Minimum continuous duration the threshold must be exceeded
 *                                  before an event is emitted. Prevents transient bumps.
 * @param cooldownMs                Post-event cooldown window during which the same detector
 *                                  won't start tracking a new event. Prevents double-counting
 *                                  the tail of a single braking event.
 */
data class DetectionConfig(
    val accelEnabled: Boolean = true,
    val accelHardAccelThreshold: Float = 9f,
    val accelHardBrakeThreshold: Float = 6.5f,
    val gForceEnabled: Boolean = false,
    val gForceHardAccelThreshold: Float = 0.22f,
    val gForceHardBrakeThreshold: Float = 0.265f,
    val gForceSevereBrakeThreshold: Float = 0.50f,
    val minEventDurationMs: Long = 500L,
    val cooldownMs: Long = 3_000L,
)
