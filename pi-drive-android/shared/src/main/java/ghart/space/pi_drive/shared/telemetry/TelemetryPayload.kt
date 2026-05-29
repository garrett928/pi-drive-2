package ghart.space.pi_drive.shared.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The complete JSON body POSTed to `POST {serverUrl}/telemetry`.
 *
 * Null-valued fields are omitted from the serialized JSON output via
 * [kotlinx.serialization.json.Json] with `explicitNulls = false`.
 *
 * The server auto-registers a vehicle on the first upload for a new [vin]. Uploads
 * are idempotent by `(vin, timestamp)` — retrying the same snapshot is safe.
 */
@Serializable
data class TelemetryPayload(
    /** ISO 8601 UTC timestamp of the captured snapshot (e.g. `2026-05-24T22:15:30.123Z`). */
    val timestamp: String,
    /** Persistent per-device identifier generated at first launch. */
    @SerialName("device_id") val deviceId: String,
    /** Vehicle Identification Number — required; server rejects payloads with a blank VIN. */
    val vin: String,
    /** GPS-derived location at snapshot time; null if location signal is disabled or unavailable. */
    val location: LocationPayload?,
    /** Raw OBD-II sensor readings. Individual fields are null when disabled or unsupported. */
    val obd: OBDPayload,
    /** Values computed from OBD readings rather than read directly. */
    val calculated: CalculatedPayload,
    /** Instantaneous acceleration in m/s² (g-force or OBD speed delta); null when unavailable. */
    @SerialName("accel_mps2") val accelMps2: Float?,
    /** Hard-acceleration / hard-braking events that occurred at or before this snapshot. */
    val events: List<EventPayload>,
)

/** GPS-derived location at the time of the snapshot. */
@Serializable
data class LocationPayload(
    val lat: Double,
    val lng: Double,
    /** Speed derived from GPS in m/s; may differ from OBD speed due to filtering. */
    @SerialName("speed_gps") val speedGps: Float?,
)

/** Raw OBD-II sensor readings. Null means the signal was disabled or the vehicle doesn't support it. */
@Serializable
data class OBDPayload(
    @SerialName("speed_kmh") val speedKmh: Int?,
    val rpm: Int?,
    @SerialName("coolant_temp_c") val coolantTempC: Int?,
    @SerialName("intake_air_temp_c") val intakeAirTempC: Int?,
    @SerialName("throttle_pct") val throttlePct: Float?,
    @SerialName("fuel_level_pct") val fuelLevelPct: Float?,
    @SerialName("oil_temp_c") val oilTempC: Int?,
    @SerialName("maf_gps") val mafGps: Float?,
    @SerialName("fuel_rate_lph") val fuelRateLph: Float?,
    @SerialName("battery_voltage") val batteryVoltage: Float?,
)

/** Values derived from OBD readings; null when the required inputs are unavailable. */
@Serializable
data class CalculatedPayload(
    @SerialName("fuel_economy_mpg") val fuelEconomyMpg: Float?,
    @SerialName("fuel_economy_kml") val fuelEconomyKml: Float?,
)

/** One hard-acceleration or hard-braking event to include with the payload. */
@Serializable
data class EventPayload(
    /** Detection algorithm: `ACCELERATION` or `G_FORCE`. */
    val strategy: String,
    /** Event classification: `HARD_ACCEL` or `HARD_BRAKE`. */
    val type: String,
    /** ISO 8601 UTC timestamp of when the event started. */
    val timestamp: String,
    @SerialName("duration_ms") val durationMs: Long,
    /** Speed-change rate in mph/s; present only for `ACCELERATION` strategy events. */
    @SerialName("rate_mph_s") val rateMphS: Float?,
    /** Peak g-force magnitude; present only for `G_FORCE` strategy events. */
    @SerialName("peak_g") val peakG: Float?,
    @SerialName("peak_accel_mps2") val peakAccelMps2: Float,
    @SerialName("start_speed_mph") val startSpeedMph: Float,
    @SerialName("end_speed_mph") val endSpeedMph: Float,
    /** Sensor sources that contributed: `OBD`, `GPS`, `ACCELEROMETER`. */
    val sources: List<String>,
)
