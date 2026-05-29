package ghart.space.pi_drive.shared.detection

/**
 * Pure-Kotlin utility for identifying the phone's longitudinal accelerometer axis.
 *
 * During a calibration drive (straight-line acceleration or braking), the longitudinal
 * axis of the phone's accelerometer will show significantly more variance than the lateral
 * or vertical axes. [identify] selects that axis and determines its sign.
 *
 * This class is stateless and requires no Android APIs, making it unit-testable without
 * instrumentation. Persistence is handled by [AccelerometerManager].
 */
object CalibrationManager {

    /**
     * Duration of the calibration sample window (used by callers to know how long to
     * collect before calling [identify]).
     */
    const val SAMPLE_WINDOW_MS = 5_000L

    /**
     * Given a list of raw 3-axis accelerometer samples, identifies the longitudinal axis.
     *
     * ## Algorithm
     * 1. Compute the variance of each axis across all samples.
     * 2. The axis with the highest variance is the longitudinal axis (most movement during
     *    straight-line acceleration or braking).
     * 3. Sign is determined from the mean: a positive mean means forward acceleration is
     *    positive on that axis (sign = +1); negative mean means sign = -1.
     *
     * @param samples List of `FloatArray(3)` where index 0=X, 1=Y, 2=Z.
     * @return Pair(axisIndex, sign): axisIndex in 0..2, sign in {-1, +1}.
     *         Returns (1, 1) (Y-axis, positive) if samples is empty — a sensible default
     *         for portrait-held phones where the phone Y-axis is longitudinal.
     */
    fun identify(samples: List<FloatArray>): Pair<Int, Int> {
        if (samples.isEmpty()) return Pair(1, 1)

        val means = FloatArray(3)
        for (axis in 0..2) {
            means[axis] = samples.sumOf { it[axis].toDouble() }.toFloat() / samples.size
        }

        val variances = FloatArray(3)
        for (axis in 0..2) {
            variances[axis] = samples.sumOf { s ->
                val d = s[axis] - means[axis]
                (d * d).toDouble()
            }.toFloat() / samples.size
        }

        val axis = variances.indices.maxByOrNull { variances[it] } ?: 1
        val sign = if (means[axis] >= 0f) 1 else -1

        return Pair(axis, sign)
    }
}
