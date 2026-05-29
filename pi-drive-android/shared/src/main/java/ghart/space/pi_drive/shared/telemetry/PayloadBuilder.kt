package ghart.space.pi_drive.shared.telemetry

import android.util.Log
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.obd.FuelEconomy
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val TAG = "TelemetryUploader"

/** 1 mph/s → 0.44704 m/s². Used when gForce is unavailable but accelRateMphS is present. */
private const val MPH_S_TO_MPS2 = 0.44704f

/** 1 g → 9.80665 m/s². Converts [VehicleSnapshot.gForce] to m/s² for the payload. */
private const val G_TO_MPS2 = 9.80665f

/** Converts MPG to km/L: 1 MPG = 1.60934 km/mi ÷ 3.78541 L/gal ≈ 0.425144 km/L. */
private const val MPG_TO_KML = 0.425144f

/**
 * Constructs a [TelemetryPayload] ready for upload from a [VehicleSnapshot], the current
 * list of recent [DrivingEvent]s, and the active [TelemetryConfig].
 *
 * Signal selection is governed by [TelemetryConfig.enabledSignals] — fields whose signal
 * key is not in that set are set to null and therefore omitted from the JSON wire format.
 */
object PayloadBuilder {

    private val isoFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /**
     * Builds a [TelemetryPayload] from the given inputs.
     *
     * Returns [Result.failure] if [TelemetryConfig.vin] is blank. Uploads are skipped
     * (not queued to the offline buffer) until a VIN is provided, because a VIN-less
     * record cannot be matched to a vehicle on the server.
     */
    fun build(
        snapshot: VehicleSnapshot,
        events: List<DrivingEvent>,
        config: TelemetryConfig,
    ): Result<TelemetryPayload> {
        if (config.vin.isBlank()) {
            Log.w(TAG, "Skipping telemetry upload: VIN not configured")
            return Result.failure(IllegalStateException("VIN is required for telemetry upload"))
        }

        val signals = config.enabledSignals

        return Result.success(
            TelemetryPayload(
                timestamp = isoFormatter.format(snapshot.timestamp),
                deviceId = config.deviceId,
                vin = config.vin,
                location = buildLocation(snapshot, signals),
                obd = buildObd(snapshot, signals),
                calculated = buildCalculated(snapshot, signals),
                accelMps2 = buildAccelMps2(snapshot, signals),
                events = buildEvents(events, signals),
            )
        )
    }

    private fun buildLocation(snapshot: VehicleSnapshot, signals: Set<String>): LocationPayload? {
        if ("location" !in signals) return null
        val lat = snapshot.gpsLat ?: return null
        val lng = snapshot.gpsLng ?: return null
        return LocationPayload(lat = lat, lng = lng, speedGps = snapshot.gpsSpeedMps)
    }

    private fun buildObd(snapshot: VehicleSnapshot, signals: Set<String>): OBDPayload =
        OBDPayload(
            speedKmh = snapshot.speedKmh.takeIf { "speed_kmh" in signals },
            rpm = snapshot.rpm.takeIf { "rpm" in signals },
            coolantTempC = snapshot.coolantTempC.takeIf { "coolant_temp_c" in signals },
            intakeAirTempC = snapshot.intakeAirTempC.takeIf { "intake_air_temp_c" in signals },
            throttlePct = snapshot.throttlePct.takeIf { "throttle_pct" in signals },
            fuelLevelPct = snapshot.fuelLevelPct.takeIf { "fuel_level_pct" in signals },
            oilTempC = snapshot.oilTempC.takeIf { "oil_temp_c" in signals },
            mafGps = snapshot.mafGps.takeIf { "maf_gps" in signals },
            fuelRateLph = snapshot.fuelRateLph.takeIf { "fuel_rate_lph" in signals },
            batteryVoltage = snapshot.batteryVoltage.takeIf { "battery_voltage" in signals },
        )

    private fun buildCalculated(snapshot: VehicleSnapshot, signals: Set<String>): CalculatedPayload {
        val wantMpg = "fuel_economy_mpg" in signals
        val wantKml = "fuel_economy_kml" in signals

        val fuelMpg: Float? = when {
            !wantMpg && !wantKml -> null
            snapshot.mafGps != null && snapshot.speedKmh != null ->
                FuelEconomy.fromMAF(snapshot.mafGps, snapshot.speedKmh)
            snapshot.fuelRateLph != null && snapshot.speedKmh != null ->
                FuelEconomy.fromFuelRate(snapshot.fuelRateLph, snapshot.speedKmh)
            else -> null
        }

        return CalculatedPayload(
            fuelEconomyMpg = fuelMpg.takeIf { wantMpg },
            fuelEconomyKml = fuelMpg?.times(MPG_TO_KML).takeIf { wantKml },
        )
    }

    private fun buildAccelMps2(snapshot: VehicleSnapshot, signals: Set<String>): Float? {
        if ("accel_mps2" !in signals) return null
        // Prefer g-force (sensor fusion) over OBD speed-delta when both are present.
        return snapshot.gForce?.times(G_TO_MPS2)
            ?: snapshot.accelRateMphS?.times(MPH_S_TO_MPS2)
    }

    private fun buildEvents(events: List<DrivingEvent>, signals: Set<String>): List<EventPayload> {
        if ("events" !in signals) return emptyList()
        return events.map { event ->
            EventPayload(
                strategy = event.strategy.name,
                type = event.type.name,
                timestamp = isoFormatter.format(event.timestamp),
                durationMs = event.durationMs,
                rateMphS = event.rateMphS,
                peakG = event.peakG,
                peakAccelMps2 = event.peakAccelMps2,
                startSpeedMph = event.startSpeedMph,
                endSpeedMph = event.endSpeedMph,
                sources = event.sources.map { it.name },
            )
        }
    }
}
