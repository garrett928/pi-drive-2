package ghart.space.pi_drive.shared.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricValueTest {

    // ── SPEED ────────────────────────────────────────────────────────────────

    @Test
    fun `extract speed from snapshot returns correct mph value`() {
        val snapshot = VehicleSnapshot(speedKmh = 96) // 96 km/h ≈ 59.6 mph
        val value = snapshot.extractMetricValue(MetricId.SPEED)

        assertNotNull(value.raw)
        val mph = value.raw!!
        assertTrue("Expected ~59.6 mph, got $mph", mph in 59f..61f)
        assertTrue("Display should contain 'mph'", value.display.contains("mph"))
    }

    @Test
    fun `extract speed from empty snapshot returns UNAVAILABLE`() {
        val snapshot = VehicleSnapshot()
        val value = snapshot.extractMetricValue(MetricId.SPEED)

        assertNull(value.raw)
        assertEquals("—", value.display)
    }

    // ── RPM ──────────────────────────────────────────────────────────────────

    @Test
    fun `extract rpm returns correct value`() {
        val snapshot = VehicleSnapshot(rpm = 3200)
        val value = snapshot.extractMetricValue(MetricId.RPM)

        assertEquals(3200f, value.raw)
        assertTrue(value.display.contains("3") && value.display.contains("rpm"))
    }

    @Test
    fun `extract rpm from empty snapshot returns UNAVAILABLE`() {
        val value = VehicleSnapshot().extractMetricValue(MetricId.RPM)
        assertNull(value.raw)
    }

    // ── THROTTLE ─────────────────────────────────────────────────────────────

    @Test
    fun `extract throttle returns percent value and display`() {
        val snapshot = VehicleSnapshot(throttlePct = 42.5f)
        val value = snapshot.extractMetricValue(MetricId.THROTTLE)

        assertEquals(42.5f, value.raw)
        assertTrue(value.display.contains("%"))
    }

    // ── TEMPERATURE METRICS ───────────────────────────────────────────────────

    @Test
    fun `extract coolant temperature returns celsius value`() {
        val snapshot = VehicleSnapshot(coolantTempC = 90)
        val value = snapshot.extractMetricValue(MetricId.COOLANT)

        assertEquals(90f, value.raw)
        assertTrue(value.display.contains("90"))
    }

    @Test
    fun `extract oil temp returns celsius value`() {
        val snapshot = VehicleSnapshot(oilTempC = 105)
        val value = snapshot.extractMetricValue(MetricId.OIL_TEMP)

        assertEquals(105f, value.raw)
        assertTrue(value.display.contains("105"))
    }

    // ── BATTERY ───────────────────────────────────────────────────────────────

    @Test
    fun `extract battery voltage returns formatted float`() {
        val snapshot = VehicleSnapshot(batteryVoltage = 14.1f)
        val value = snapshot.extractMetricValue(MetricId.BATTERY)

        assertEquals(14.1f, value.raw!!, 0.01f)
        assertTrue(value.display.contains("V"))
    }

    // ── MPG_INSTANT ────────────────────────────────────────────────────────────

    @Test
    fun `instant mpg computed from fuelRateLph and speed`() {
        // 60 km/h ≈ 37.3 mph, fuel rate 4 L/h ≈ 1.057 gph → 37.3 / 1.057 ≈ 35.3 mpg
        val snapshot = VehicleSnapshot(speedKmh = 60, fuelRateLph = 4f)
        val value = snapshot.extractMetricValue(MetricId.MPG_INSTANT)

        assertNotNull(value.raw)
        val mpg = value.raw!!
        assertTrue("Expected ~35 mpg, got $mpg", mpg in 32f..38f)
        assertTrue(value.display.contains("mpg"))
    }

    @Test
    fun `instant mpg from maf when fuelRateLph is null`() {
        // 80 km/h ≈ 49.7 mph, MAF = 6 g/s — should yield a reasonable mpg
        val snapshot = VehicleSnapshot(speedKmh = 80, mafGps = 6f)
        val value = snapshot.extractMetricValue(MetricId.MPG_INSTANT)

        assertNotNull(value.raw)
        val mpg = value.raw!!
        assertTrue("Expected a positive mpg value, got $mpg", mpg > 0f)
    }

    @Test
    fun `instant mpg is UNAVAILABLE when speed is null`() {
        val snapshot = VehicleSnapshot(fuelRateLph = 4f) // no speed
        val value = snapshot.extractMetricValue(MetricId.MPG_INSTANT)
        assertNull(value.raw)
    }

    @Test
    fun `instant mpg is 0 when vehicle is stationary`() {
        val snapshot = VehicleSnapshot(speedKmh = 0, fuelRateLph = 1f)
        val value = snapshot.extractMetricValue(MetricId.MPG_INSTANT)
        assertEquals(0f, value.raw!!, 0.01f)
    }

    // ── ACCUMULATOR METRICS (must return UNAVAILABLE) ────────────────────────

    @Test
    fun `MPG_TRIP returns UNAVAILABLE from single snapshot`() {
        val value = VehicleSnapshot(speedKmh = 80).extractMetricValue(MetricId.MPG_TRIP)
        assertNull(value.raw)
        assertEquals("—", value.display)
    }

    @Test
    fun `DISTANCE returns UNAVAILABLE from single snapshot`() {
        val value = VehicleSnapshot().extractMetricValue(MetricId.DISTANCE)
        assertNull(value.raw)
    }

    @Test
    fun `MANUAL_TRIP returns UNAVAILABLE from single snapshot`() {
        val value = VehicleSnapshot().extractMetricValue(MetricId.MANUAL_TRIP)
        assertNull(value.raw)
    }

    // ── UNAVAILABLE SENTINEL ────────────────────────────────────────────────

    @Test
    fun `UNAVAILABLE sentinel has null raw and dash display`() {
        assertNull(MetricValue.UNAVAILABLE.raw)
        assertEquals("—", MetricValue.UNAVAILABLE.display)
    }
}
