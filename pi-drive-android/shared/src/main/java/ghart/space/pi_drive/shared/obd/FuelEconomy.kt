package ghart.space.pi_drive.shared.obd

/**
 * Fuel economy calculation helpers.
 *
 * Provides two methods for estimating fuel economy (in US MPG) from OBD-II sensor readings,
 * plus a unit-conversion helper. Both calculation methods return null when speed is zero
 * to avoid division-by-zero.
 *
 * Gasoline constants used:
 * - Stoichiometric air/fuel ratio: 14.7 (by mass)
 * - Gasoline density: ~820 g/L
 * - Combined: STOICH_DENSITY = 14.7 × 820 = 12054 g/L
 */
object FuelEconomy {

    /** 14.7 stoich ratio × 820 g/L gasoline density, used in MAF-based fuel economy. */
    private const val STOICH_DENSITY = 12054f

    private const val KM_PER_MILE = 1.60934f
    private const val LITERS_PER_GALLON = 3.78541f

    /**
     * Estimates fuel economy from Mass Air Flow rate (PID 0x10).
     *
     * Derives instantaneous fuel consumption by dividing MAF by the stoichiometric
     * air/fuel density constant, then computes distance-per-volume and converts to MPG.
     *
     * Formula: km/L = speedKmh × STOICH_DENSITY / (mafGps × 3600)
     *
     * @param mafGps    MAF sensor reading in grams per second (from [PidDecoder.decodeMaf]).
     * @param speedKmh  Current vehicle speed in km/h (from [PidDecoder.decodeSpeed]).
     * @return Fuel economy in US MPG, or null if [speedKmh] is zero (stationary vehicle).
     */
    fun fromMAF(mafGps: Float, speedKmh: Int): Float? {
        if (speedKmh == 0) return null
        val kmPerLiter = speedKmh.toFloat() * STOICH_DENSITY / (mafGps * 3600f)
        return kmPerLiterToMpg(kmPerLiter)
    }

    /**
     * Estimates fuel economy from the engine fuel rate sensor (PID 0x5E).
     *
     * Divides current speed by the reported fuel consumption rate and converts to MPG.
     *
     * Formula: km/L = speedKmh / fuelRateLph
     *
     * @param fuelRateLph  Fuel consumption rate in litres per hour (from [PidDecoder.decodeFuelRate]).
     * @param speedKmh     Current vehicle speed in km/h (from [PidDecoder.decodeSpeed]).
     * @return Fuel economy in US MPG, or null if [speedKmh] is zero (stationary vehicle).
     */
    fun fromFuelRate(fuelRateLph: Float, speedKmh: Int): Float? {
        if (speedKmh == 0) return null
        val kmPerLiter = speedKmh.toFloat() / fuelRateLph
        return kmPerLiterToMpg(kmPerLiter)
    }

    /**
     * Converts km/L to US miles per gallon.
     *
     * @param kml Fuel economy in kilometres per litre.
     * @return Equivalent fuel economy in US MPG.
     */
    fun kmPerLiterToMpg(kml: Float): Float = kml * LITERS_PER_GALLON / KM_PER_MILE
}
