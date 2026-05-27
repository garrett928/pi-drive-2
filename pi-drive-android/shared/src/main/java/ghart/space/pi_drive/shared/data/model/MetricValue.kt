package ghart.space.pi_drive.shared.data.model

import kotlin.math.roundToInt

/**
 * A resolved value for a single metric extracted from a [VehicleSnapshot].
 *
 * The [raw] field holds the numeric value in the metric's base unit (as declared in
 * [MetricId.unit]). The [display] field is a pre-formatted string ready for rendering
 * in dashboard tiles — it includes the numeric value and unit suffix.
 *
 * A null [raw] means the metric is not available (unsupported PID, missing sensor,
 * or a trip-accumulator metric that isn't tracked in a single snapshot).
 *
 * @param raw     Numeric value in [MetricId.unit] units, or null if unavailable.
 * @param display Human-readable formatted string, e.g. "37 mph", "2,500 rpm", "—".
 */
data class MetricValue(
    val raw: Float?,
    val display: String,
) {
    companion object {
        /** Sentinel value returned when a metric cannot be read from the snapshot. */
        val UNAVAILABLE = MetricValue(raw = null, display = "—")
    }
}

// ── Conversion constants ───────────────────────────────────────────────────

private const val KMH_TO_MPH = 0.621371f
private const val MPS_TO_MPH = 2.23694f

/** Stoichiometric air-fuel ratio for gasoline. */
private const val STOICH_AFR = 14.7f

/** Gasoline density in kg/L. */
private const val GASOLINE_DENSITY_KG_PER_L = 0.7489f

/** Litres per US gallon. */
private const val L_PER_GALLON = 3.78541f

/** km per mile. */
private const val KM_PER_MILE = 1.60934f

// ── Extension function ─────────────────────────────────────────────────────

/**
 * Extracts and formats the value for [metricId] from this snapshot.
 *
 * Speed is returned in mph (the app's primary display unit — UI adds a toggle later).
 * Temperatures are in °C. MPG_INSTANT is derived from [fuelRateLph] if available,
 * falling back to a MAF-based estimate using stoichiometric combustion math.
 *
 * Metrics that require accumulator state across multiple snapshots
 * (MPG_TRIP, MPG_MANUAL, DISTANCE, MANUAL_TRIP) always return [MetricValue.UNAVAILABLE]
 * from a single snapshot — they are computed by the trip accumulator in Phase 6.
 *
 * @param metricId Which metric to extract.
 * @return A [MetricValue] with the raw value and a formatted display string.
 */
fun VehicleSnapshot.extractMetricValue(metricId: MetricId): MetricValue = when (metricId) {

    MetricId.SPEED -> speedKmh
        ?.let { kmh ->
            val mph = kmh * KMH_TO_MPH
            MetricValue(raw = mph, display = "${mph.roundToInt()} mph")
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.RPM -> rpm
        ?.let { r ->
            MetricValue(raw = r.toFloat(), display = "%,d rpm".format(r))
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.THROTTLE -> throttlePct
        ?.let { t ->
            MetricValue(raw = t, display = "${t.roundToInt()}%")
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.COOLANT -> coolantTempC
        ?.let { c ->
            MetricValue(raw = c.toFloat(), display = "$c °C")
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.INTAKE -> intakeAirTempC
        ?.let { c ->
            MetricValue(raw = c.toFloat(), display = "$c °C")
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.OIL_TEMP -> oilTempC
        ?.let { c ->
            MetricValue(raw = c.toFloat(), display = "$c °C")
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.BATTERY -> batteryVoltage
        ?.let { v ->
            MetricValue(raw = v, display = "%.1f V".format(v))
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.FUEL -> fuelLevelPct
        ?.let { f ->
            MetricValue(raw = f, display = "${f.roundToInt()}%")
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.MAF -> mafGps
        ?.let { m ->
            MetricValue(raw = m, display = "%.1f g/s".format(m))
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.G_FORCE -> gForce
        ?.let { g ->
            MetricValue(raw = g, display = "%.2f g".format(g))
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.ACCEL -> accelRateMphS
        ?.let { a ->
            MetricValue(raw = a, display = "%.1f mph/s".format(a))
        }
        ?: MetricValue.UNAVAILABLE

    MetricId.MPG_INSTANT -> computeInstantMpg()

    // Accumulator-based metrics — not computable from a single snapshot.
    MetricId.MPG_TRIP,
    MetricId.MPG_MANUAL,
    MetricId.DISTANCE,
    MetricId.MANUAL_TRIP,
    -> MetricValue.UNAVAILABLE
}

/**
 * Computes instantaneous MPG from the snapshot.
 *
 * Priority:
 * 1. OBD PID 0x5E (fuelRateLph) + OBD speed — most accurate.
 * 2. OBD PID 0x10 (mafGps) + OBD speed — stoichiometric estimate.
 * 3. Otherwise — null (no fuel data available).
 *
 * Returns [MetricValue.UNAVAILABLE] if speed is zero (avoids division by zero)
 * or if no fuel consumption data is present.
 */
private fun VehicleSnapshot.computeInstantMpg(): MetricValue {
    val speedMph = (speedKmh ?: return MetricValue.UNAVAILABLE) * KMH_TO_MPH
    if (speedMph < 0.5f) return MetricValue(raw = 0f, display = "0 mpg")

    val mpg: Float? = when {
        fuelRateLph != null && fuelRateLph > 0f -> {
            // Direct: speed_mph / fuel_consumption_gph
            val fuelGph = fuelRateLph * (1f / L_PER_GALLON)
            speedMph / fuelGph
        }
        mafGps != null && mafGps > 0f -> {
            // Stoichiometric estimate:
            // fuel_gps = maf / AFR (grams of fuel per second)
            // fuel_L_per_s = fuel_gps / (density_kg_per_L * 1000)
            // fuel_L_per_h = fuel_L_per_s * 3600
            // mpg = speed_mph / (fuel_L_per_h / L_per_gallon)
            val fuelGps = mafGps / STOICH_AFR
            val fuelLph = (fuelGps / (GASOLINE_DENSITY_KG_PER_L * 1000f)) * 3600f
            if (fuelLph > 0f) speedMph / (fuelLph / L_PER_GALLON) else null
        }
        else -> null
    }

    return mpg
        ?.coerceIn(0f, 99.9f)
        ?.let { MetricValue(raw = it, display = "%.1f mpg".format(it)) }
        ?: MetricValue.UNAVAILABLE
}
