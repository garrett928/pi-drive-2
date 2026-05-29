package ghart.space.pi_drive.shared.telemetry

import kotlinx.serialization.Serializable

/** How the VIN was obtained. */
@Serializable
enum class VinSource { AUTO_OBD, MANUAL, NONE }

/**
 * Configuration for server telemetry uploads.
 *
 * [deviceId] is generated once at first launch by [TelemetryConfigRepository] and never
 * changes across app restarts or re-installs on the same device.
 *
 * [vin] is required for uploads — [PayloadBuilder] returns [Result.failure] when blank.
 * The VIN is sourced from OBD service 09 PID 02 ([VinSource.AUTO_OBD]) or entered manually
 * by the user ([VinSource.MANUAL]).
 *
 * [enabledSignals] controls which metric fields are included in each [TelemetryPayload].
 * The full set of valid signal names is in [ALL_SIGNALS].
 *
 * Production note: [apiKey] and [serverUrl] should be stored in EncryptedSharedPreferences.
 * The current [TelemetryConfigRepository] uses regular SharedPreferences; upgrade when
 * adding the `androidx.security:security-crypto` dependency.
 */
@Serializable
data class TelemetryConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
    val deviceId: String,
    val vin: String = "",
    val vinSource: VinSource = VinSource.NONE,
    val streamWhileDriving: Boolean = true,
    val bufferWhenOffline: Boolean = true,
    val uploadOnWifiOnly: Boolean = false,
    val compressPayloads: Boolean = true,
    val sampleRateHz: Int = 30,
    val enabledSignals: Set<String> = ALL_SIGNALS,
) {
    companion object {
        /** All valid signal names. Pass a subset to [enabledSignals] to filter uploads. */
        val ALL_SIGNALS: Set<String> = setOf(
            // OBD-II PIDs
            "speed_kmh", "rpm", "coolant_temp_c", "intake_air_temp_c",
            "throttle_pct", "fuel_level_pct", "oil_temp_c", "maf_gps",
            "fuel_rate_lph", "battery_voltage",
            // Calculated
            "fuel_economy_mpg", "fuel_economy_kml",
            // Phone sensors
            "location", "accel_mps2",
            // Events
            "events",
        )
    }
}
