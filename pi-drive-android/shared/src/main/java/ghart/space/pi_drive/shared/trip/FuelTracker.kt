package ghart.space.pi_drive.shared.trip

import ghart.space.pi_drive.shared.obd.FuelEconomy

/**
 * Integrates fuel consumption samples into a cumulative total and computes MPG.
 *
 * Supports two OBD data sources (in preference order):
 * 1. **Fuel rate (PID 0x5E):** `fuel_L += fuelRate_Lph × dt_hours`
 * 2. **MAF (PID 0x10):** `fuel_L += mafGps × dt_seconds / STOICH_DENSITY`
 *    where `STOICH_DENSITY = 14.7 (stoich AFR) × 820 (g/L) = 12054 g/L`.
 *
 * When neither source is available for a given sample, the sample is skipped (no fuel
 * is consumed, which is conservative but avoids inventing data).
 *
 * [currentMpg] delegates to [FuelEconomy] using the most recent sensor values.
 * [tripAverageMpg] divides cumulative distance by cumulative fuel consumed.
 */
class FuelTracker {

    companion object {
        /** 14.7 stoich AFR × 820 g/L gasoline density — matches [FuelEconomy]. */
        private const val STOICH_DENSITY = 12054f
        private const val LITERS_PER_GALLON = 3.78541f
    }

    /** Cumulative fuel consumed since last [reset], in litres. */
    var totalFuelLiters: Float = 0f
        private set

    // Most-recent sensor values, kept for [currentMpg].
    private var lastSpeedKmh: Int? = null
    private var lastMafGps: Float? = null
    private var lastFuelRateLph: Float? = null

    /**
     * Adds one fuel consumption sample.
     *
     * Prefers [fuelRateLph] (PID 0x5E) over [mafGps] (PID 0x10) when both are present.
     * Stores sensor values for [currentMpg] regardless of which path integrates fuel.
     *
     * @param mafGps      Mass air flow in g/s (PID 0x10), or null if unavailable.
     * @param fuelRateLph Fuel consumption rate in L/h (PID 0x5E), or null if unavailable.
     * @param speedKmh    Current speed in km/h, used for [currentMpg].
     * @param dtMs        Elapsed time since the previous sample, in milliseconds.
     */
    fun update(
        mafGps: Float? = null,
        fuelRateLph: Float? = null,
        speedKmh: Int? = null,
        dtMs: Long,
    ) {
        if (dtMs <= 0L) return

        lastSpeedKmh = speedKmh
        lastMafGps = mafGps
        lastFuelRateLph = fuelRateLph

        val consumed = when {
            fuelRateLph != null && fuelRateLph > 0f -> fuelRateLph * (dtMs / 3_600_000f)
            mafGps != null && mafGps > 0f -> mafGps * (dtMs / 1_000f) / STOICH_DENSITY
            else -> return
        }

        totalFuelLiters += consumed
    }

    /**
     * Instantaneous fuel economy based on the most recent sensor reading, in US MPG.
     * Null when the vehicle is stationary or no sensor data is available.
     */
    val currentMpg: Float?
        get() {
            val speedKmh = lastSpeedKmh?.takeIf { it > 0 } ?: return null
            return lastFuelRateLph?.let { FuelEconomy.fromFuelRate(it, speedKmh) }
                ?: lastMafGps?.let { FuelEconomy.fromMAF(it, speedKmh) }
        }

    /**
     * Trip-average fuel economy: [distanceMiles] ÷ total fuel consumed.
     * Null when no fuel has been tracked or [distanceMiles] is zero.
     *
     * @param distanceMiles Total trip distance from the paired [TripAccumulator].
     */
    fun tripAverageMpg(distanceMiles: Float): Float? {
        if (totalFuelLiters <= 0f || distanceMiles <= 0f) return null
        val gallons = totalFuelLiters / LITERS_PER_GALLON
        return distanceMiles / gallons
    }

    /** Resets all accumulated fuel data and clears cached sensor values. */
    fun reset() {
        totalFuelLiters = 0f
        lastSpeedKmh = null
        lastMafGps = null
        lastFuelRateLph = null
    }
}
