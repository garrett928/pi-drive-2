package ghart.space.pi_drive.shared.data.model

import java.time.Instant

/**
 * Immutable snapshot of all vehicle telemetry values at a single point in time.
 *
 * All numeric fields are nullable — a null value means the metric is not supported
 * by the connected adapter or has not yet been polled. Consumers must handle nulls
 * gracefully (e.g., show "—" in the UI, skip accumulation).
 *
 * Fields follow SI / OBD-native units throughout — unit conversion for display
 * happens in the UI layer via [MetricValue].
 *
 * @param timestamp       When this snapshot was captured (defaults to now).
 * @param speedKmh        Vehicle speed in km/h (OBD PID 0x0D).
 * @param rpm             Engine RPM (OBD PID 0x0C).
 * @param coolantTempC    Engine coolant temperature in °C (OBD PID 0x05).
 * @param intakeAirTempC  Intake air temperature in °C (OBD PID 0x0F).
 * @param throttlePct     Absolute throttle position 0–100% (OBD PID 0x11).
 * @param fuelLevelPct    Fuel tank level 0–100% (OBD PID 0x2F).
 * @param oilTempC        Engine oil temperature in °C (OBD PID 0x5C).
 * @param mafGps          Mass air flow rate in grams/second (OBD PID 0x10).
 * @param fuelRateLph     Fuel consumption rate in litres/hour (OBD PID 0x5E).
 * @param batteryVoltage  Control module supply voltage in V (OBD PID 0x42).
 * @param gpsLat          GPS latitude in decimal degrees (from device GPS).
 * @param gpsLng          GPS longitude in decimal degrees (from device GPS).
 * @param gpsSpeedMps     GPS-derived speed in m/s (from device GPS).
 * @param accelRateMphS   Computed acceleration/deceleration rate in mph/s (detector output).
 * @param gForce          Computed lateral/longitudinal g-force magnitude (detector output).
 */
data class VehicleSnapshot(
    val timestamp: Instant = Instant.now(),
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val intakeAirTempC: Int? = null,
    val throttlePct: Float? = null,
    val fuelLevelPct: Float? = null,
    val oilTempC: Int? = null,
    val mafGps: Float? = null,
    val fuelRateLph: Float? = null,
    val batteryVoltage: Float? = null,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsSpeedMps: Float? = null,
    val accelRateMphS: Float? = null,
    val gForce: Float? = null,
) {
    companion object {
        /** An empty snapshot with all fields null — useful as an initial StateFlow value. */
        val EMPTY = VehicleSnapshot()
    }
}
