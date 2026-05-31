package ghart.space.pi_drive.shared.detection

import kotlinx.serialization.Serializable

/**
 * Configuration knobs for both detection strategies (acceleration and G-force).
 *
 * Passed to [AccelerationDetector] and [GForceDetector] via a [kotlinx.coroutines.flow.StateFlow]
 * so that user-configured thresholds take effect immediately without restarting the detectors.
 * All thresholds have tuned defaults.
 *
 * @param accelEnabled              Whether the mph/s acceleration strategy is active.
 * @param accelHardAccelThreshold   Positive rate threshold for hard acceleration in mph/s.
 * @param accelHardBrakeThreshold   Absolute rate threshold for hard braking in mph/s.
 * @param gForceEnabled             Whether the g-force sensor-fusion strategy is active.
 * @param gForceHardAccelThreshold  Hard-acceleration threshold in g.
 * @param gForceHardBrakeThreshold  Hard-braking threshold in g.
 * @param gForceSevereBrakeThreshold Severe-braking threshold in g.
 * @param minEventDurationMs        Minimum continuous duration before event emits.
 * @param cooldownMs                Post-event cooldown; prevents double-counting.
 * @param soundAlertEnabled         Play an audible alert when an event fires (default off).
 * @param hapticFeedbackEnabled     Vibrate when an event fires (default on).
 * @param aaToastEnabled            Show a CarToast on Android Auto when an event fires (default on).
 */
@Serializable
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
    val soundAlertEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val aaToastEnabled: Boolean = true,
)
