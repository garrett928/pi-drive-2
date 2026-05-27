package ghart.space.pi_drive.shared.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelEconomyTest {

    // ── fromMAF ──────────────────────────────────────────────────────────

    @Test
    fun `fromMAF highway cruise gives approximately 90 MPG`() {
        // MAF = 8.4 g/s, speed = 96.5 km/h
        // km/L = 96.5 × 12054 / (8.4 × 3600) ≈ 38.47
        // MPG = 38.47 × 3.78541 / 1.60934 ≈ 90.5
        val result = FuelEconomy.fromMAF(mafGps = 8.4f, speedKmh = 96)!!
        assertEquals(90f, result, 5f)
    }

    @Test
    fun `fromMAF returns null when speed is zero`() {
        assertNull(FuelEconomy.fromMAF(mafGps = 8.4f, speedKmh = 0))
    }

    @Test
    fun `fromMAF city driving gives lower MPG than highway`() {
        val highway = FuelEconomy.fromMAF(mafGps = 4.0f, speedKmh = 96)!!
        val city = FuelEconomy.fromMAF(mafGps = 4.0f, speedKmh = 32)!!
        assert(city < highway) { "City MPG ($city) should be less than highway MPG ($highway)" }
    }

    // ── fromFuelRate ──────────────────────────────────────────────────────

    @Test
    fun `fromFuelRate 6 lph at 96 kmh gives approximately 38 MPG`() {
        // km/L = 96.5 / 6.0 ≈ 16.08
        // MPG = 16.08 × 3.78541 / 1.60934 ≈ 37.85
        val result = FuelEconomy.fromFuelRate(fuelRateLph = 6.0f, speedKmh = 96)!!
        assertEquals(38f, result, 3f)
    }

    @Test
    fun `fromFuelRate returns null when speed is zero`() {
        assertNull(FuelEconomy.fromFuelRate(fuelRateLph = 6.0f, speedKmh = 0))
    }

    @Test
    fun `fromFuelRate lower fuel rate gives better MPG at same speed`() {
        val efficient = FuelEconomy.fromFuelRate(fuelRateLph = 4.0f, speedKmh = 80)!!
        val inefficient = FuelEconomy.fromFuelRate(fuelRateLph = 8.0f, speedKmh = 80)!!
        assert(efficient > inefficient) {
            "Lower fuel rate ($efficient MPG) should beat higher fuel rate ($inefficient MPG)"
        }
    }

    // ── kmPerLiterToMpg ───────────────────────────────────────────────────

    @Test
    fun `kmPerLiterToMpg 10 kml gives approximately 23_5 MPG`() {
        // 10 × 3.78541 / 1.60934 ≈ 23.52
        assertEquals(23.52f, FuelEconomy.kmPerLiterToMpg(10f), 0.1f)
    }

    @Test
    fun `kmPerLiterToMpg 0 returns 0`() {
        assertEquals(0f, FuelEconomy.kmPerLiterToMpg(0f), 0.001f)
    }
}
