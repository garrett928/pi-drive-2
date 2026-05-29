package ghart.space.pi_drive.shared.telemetry

import ghart.space.pi_drive.shared.data.model.DataSource
import ghart.space.pi_drive.shared.data.model.DetectionStrategy
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.data.model.LatLng
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [PayloadBuilder].
 *
 * Verifies JSON output format, null-field omission, signal-selection filtering, and
 * VIN-blank guard behaviour.
 */
class PayloadBuilderTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val baseConfig = TelemetryConfig(
        deviceId = "device-001",
        vin = "1HGCM82633A123456",
        serverUrl = "https://example.com",
        sampleRateHz = 1,
    )

    private val fullSnapshot = VehicleSnapshot(
        timestamp = Instant.parse("2026-05-24T22:15:30.123Z"),
        speedKmh = 105,
        rpm = 2400,
        coolantTempC = 92,
        intakeAirTempC = 35,
        throttlePct = 22.5f,
        fuelLevelPct = 68.0f,
        oilTempC = 95,
        mafGps = 12.5f,
        fuelRateLph = null,
        batteryVoltage = 14.2f,
        gpsLat = 37.7749,
        gpsLng = -122.4194,
        gpsSpeedMps = 29.2f,
        gForce = 0.046f,
    )

    // ── VIN guard ─────────────────────────────────────────────────────────────

    @Test fun `blank VIN returns failure`() {
        val config = baseConfig.copy(vin = "")
        val result = PayloadBuilder.build(fullSnapshot, emptyList(), config)
        assertTrue("Expected failure for blank VIN", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test fun `whitespace-only VIN returns failure`() {
        val config = baseConfig.copy(vin = "   ")
        val result = PayloadBuilder.build(fullSnapshot, emptyList(), config)
        assertTrue(result.isFailure)
    }

    @Test fun `non-blank VIN propagates to payload`() {
        val result = PayloadBuilder.build(fullSnapshot, emptyList(), baseConfig)
        assertTrue(result.isSuccess)
        assertEquals("1HGCM82633A123456", result.getOrThrow().vin)
    }

    @Test fun `VIN comes from config not snapshot`() {
        val config = baseConfig.copy(vin = "CONFIG_VIN")
        val result = PayloadBuilder.build(fullSnapshot, emptyList(), config)
        assertEquals("CONFIG_VIN", result.getOrThrow().vin)
    }

    // ── JSON serialization ─────────────────────────────────────────────────────

    @Test fun `known snapshot serializes to expected JSON keys`() {
        val payload = PayloadBuilder.build(fullSnapshot, emptyList(), baseConfig).getOrThrow()
        val jsonStr = json.encodeToString(TelemetryPayload.serializer(), payload)
        val obj = json.parseToJsonElement(jsonStr).jsonObject

        assertEquals("2026-05-24T22:15:30.123Z", obj["timestamp"]?.jsonPrimitive?.content)
        assertEquals("device-001", obj["device_id"]?.jsonPrimitive?.content)
        assertEquals("1HGCM82633A123456", obj["vin"]?.jsonPrimitive?.content)
        assertNotNull("obd key missing", obj["obd"])
        assertNotNull("calculated key missing", obj["calculated"])
    }

    @Test fun `null OBD fields are omitted from JSON`() {
        val snapshot = fullSnapshot.copy(oilTempC = null, fuelRateLph = null)
        val payload = PayloadBuilder.build(snapshot, emptyList(), baseConfig).getOrThrow()
        val jsonStr = json.encodeToString(TelemetryPayload.serializer(), payload)
        val obdObj = json.parseToJsonElement(jsonStr).jsonObject["obd"]?.jsonObject!!

        assertFalse("oil_temp_c should be absent", obdObj.containsKey("oil_temp_c"))
        assertFalse("fuel_rate_lph should be absent", obdObj.containsKey("fuel_rate_lph"))
    }

    @Test fun `location absent when GPS coords null`() {
        val snapshot = fullSnapshot.copy(gpsLat = null, gpsLng = null)
        val payload = PayloadBuilder.build(snapshot, emptyList(), baseConfig).getOrThrow()
        assertNull(payload.location)
    }

    @Test fun `location present when GPS coords available`() {
        val payload = PayloadBuilder.build(fullSnapshot, emptyList(), baseConfig).getOrThrow()
        val loc = payload.location
        assertNotNull(loc)
        assertEquals(37.7749, loc!!.lat, 0.0001)
        assertEquals(-122.4194, loc.lng, 0.0001)
    }

    // ── Signal selection ───────────────────────────────────────────────────────

    @Test fun `disabled speed_kmh signal omits speed from OBD payload`() {
        val config = baseConfig.copy(enabledSignals = TelemetryConfig.ALL_SIGNALS - "speed_kmh")
        val payload = PayloadBuilder.build(fullSnapshot, emptyList(), config).getOrThrow()
        assertNull(payload.obd.speedKmh)
    }

    @Test fun `disabled location signal omits location`() {
        val config = baseConfig.copy(enabledSignals = TelemetryConfig.ALL_SIGNALS - "location")
        val payload = PayloadBuilder.build(fullSnapshot, emptyList(), config).getOrThrow()
        assertNull(payload.location)
    }

    @Test fun `disabled events signal returns empty event list`() {
        val config = baseConfig.copy(enabledSignals = TelemetryConfig.ALL_SIGNALS - "events")
        val event = makeDrivingEvent()
        val payload = PayloadBuilder.build(fullSnapshot, listOf(event), config).getOrThrow()
        assertTrue(payload.events.isEmpty())
    }

    @Test fun `disabled fuel_economy signals omit calculated fields`() {
        val config = baseConfig.copy(
            enabledSignals = TelemetryConfig.ALL_SIGNALS - setOf("fuel_economy_mpg", "fuel_economy_kml"),
        )
        val payload = PayloadBuilder.build(fullSnapshot, emptyList(), config).getOrThrow()
        assertNull(payload.calculated.fuelEconomyMpg)
        assertNull(payload.calculated.fuelEconomyKml)
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @Test fun `events are mapped to EventPayload correctly`() {
        val event = makeDrivingEvent()
        val payload = PayloadBuilder.build(fullSnapshot, listOf(event), baseConfig).getOrThrow()
        assertEquals(1, payload.events.size)
        val ep = payload.events[0]
        assertEquals("ACCELERATION", ep.strategy)
        assertEquals("HARD_BRAKE", ep.type)
        assertEquals(1200L, ep.durationMs)
        assertEquals(-11.2f, ep.rateMphS)
        assertTrue(ep.sources.contains("OBD"))
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun makeDrivingEvent() = DrivingEvent(
        strategy = DetectionStrategy.ACCELERATION,
        type = EventType.HARD_BRAKE,
        timestamp = Instant.parse("2026-05-24T22:15:28.800Z"),
        durationMs = 1200L,
        rateMphS = -11.2f,
        peakG = null,
        peakAccelMps2 = -5.0f,
        startSpeedMph = 59f,
        endSpeedMph = 38f,
        location = LatLng(37.7749, -122.4194),
        sources = setOf(DataSource.OBD, DataSource.GPS),
    )
}
