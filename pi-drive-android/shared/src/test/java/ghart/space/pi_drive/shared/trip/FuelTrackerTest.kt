package ghart.space.pi_drive.shared.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FuelTrackerTest {

    private lateinit var tracker: FuelTracker

    @Before
    fun setUp() {
        tracker = FuelTracker()
    }

    // ── Fuel rate (PID 5E) ────────────────────────────────────────────────────

    /**
     * 6 L/h for exactly 1 hour at 96.5 km/h ≈ 60 mph.
     *
     * fuel = 6 L/h × 1 h = 6 L = 6 / 3.78541 gal = 1.585 gal
     * distance = 60 mi
     * mpg = 60 / 1.585 ≈ 37.8 → rounds to ~38
     */
    @Test
    fun `fuel rate 6 Lph for 1 hour at 60 mph gives avg MPG around 38`() {
        val oneHourMs = 3_600_000L
        tracker.update(fuelRateLph = 6f, speedKmh = 97, dtMs = oneHourMs)

        val distanceMiles = 60f // driven in that hour at ~60 mph
        val mpg = tracker.tripAverageMpg(distanceMiles)

        assertNotNull(mpg)
        assertEquals(38f, mpg!!, 2f)
    }

    @Test
    fun `fuel rate integration accumulates total liters correctly`() {
        tracker.update(fuelRateLph = 6f, speedKmh = 97, dtMs = 3_600_000L) // 1 h → 6 L
        tracker.update(fuelRateLph = 3f, speedKmh = 97, dtMs = 3_600_000L) // 1 h → 3 L

        assertEquals(9f, tracker.totalFuelLiters, 0.01f)
    }

    // ── MAF (PID 10) ──────────────────────────────────────────────────────────

    /**
     * MAF 8.4 g/s at 96.5 km/h ≈ 60 mph.
     *
     * fuel_L = 8.4 g/s × 3600 s / 12054 g/L = 2.509 L → ~0.663 gal
     * distance = 60 mi
     * mpg = 60 / 0.663 = ~90.4 (high because 8.4 g/s is low MAF)
     *
     * Test only asserts a positive finite result ("reasonable MPG").
     */
    @Test
    fun `MAF 8_4 gps at 60 mph gives reasonable positive MPG`() {
        val oneHourMs = 3_600_000L
        tracker.update(mafGps = 8.4f, speedKmh = 97, dtMs = oneHourMs)

        val distanceMiles = 60f
        val mpg = tracker.tripAverageMpg(distanceMiles)

        assertNotNull("MAF-based MPG should not be null", mpg)
        assertTrue("MPG must be positive", mpg!! > 0f)
        assertTrue("MPG must be finite", mpg.isFinite())
    }

    @Test
    fun `MAF integration formula matches expected fuel consumption`() {
        // STOICH_DENSITY = 12054; fuel_L = maf_gps * dt_s / 12054
        // 12.054 g/s for 1000 ms → 12.054 * 1 / 12054 = 0.001 L
        tracker.update(mafGps = 12.054f, dtMs = 1_000L)

        assertEquals(0.001f, tracker.totalFuelLiters, 0.0001f)
    }

    // ── Source preference ─────────────────────────────────────────────────────

    @Test
    fun `fuel rate takes priority over MAF when both are provided`() {
        // If fuel rate is used: 6 L/h × 1 h = 6 L
        // If MAF is used: 10 g/s × 3600 s / 12054 ≈ 2.99 L
        tracker.update(mafGps = 10f, fuelRateLph = 6f, dtMs = 3_600_000L)

        assertEquals("Fuel rate should take priority", 6f, tracker.totalFuelLiters, 0.1f)
    }

    @Test
    fun `MAF is used as fallback when fuel rate is absent`() {
        tracker.update(mafGps = 12.054f, fuelRateLph = null, dtMs = 1_000L)

        assertTrue(tracker.totalFuelLiters > 0f)
    }

    @Test
    fun `no fuel consumed when neither source available`() {
        tracker.update(mafGps = null, fuelRateLph = null, dtMs = 5_000L)

        assertEquals(0f, tracker.totalFuelLiters, 0f)
    }

    // ── currentMpg ────────────────────────────────────────────────────────────

    @Test
    fun `currentMpg is null before any update`() {
        assertNull(tracker.currentMpg)
    }

    @Test
    fun `currentMpg is null when speed is zero`() {
        tracker.update(fuelRateLph = 3f, speedKmh = 0, dtMs = 1_000L)

        assertNull(tracker.currentMpg)
    }

    @Test
    fun `currentMpg is positive when moving with fuel rate`() {
        tracker.update(fuelRateLph = 6f, speedKmh = 97, dtMs = 1_000L)

        val mpg = tracker.currentMpg
        assertNotNull(mpg)
        assertTrue("currentMpg must be positive", mpg!! > 0f)
    }

    // ── tripAverageMpg edge cases ─────────────────────────────────────────────

    @Test
    fun `tripAverageMpg is null when no fuel consumed`() {
        assertNull(tracker.tripAverageMpg(100f))
    }

    @Test
    fun `tripAverageMpg is null when distance is zero`() {
        tracker.update(fuelRateLph = 6f, dtMs = 1_000L)

        assertNull(tracker.tripAverageMpg(0f))
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset zeros total fuel and clears cached values`() {
        tracker.update(fuelRateLph = 6f, speedKmh = 97, dtMs = 3_600_000L)
        tracker.reset()

        assertEquals(0f, tracker.totalFuelLiters, 0f)
        assertNull("currentMpg should be null after reset", tracker.currentMpg)
        assertNull("tripAverageMpg should be null after reset", tracker.tripAverageMpg(100f))
    }
}
