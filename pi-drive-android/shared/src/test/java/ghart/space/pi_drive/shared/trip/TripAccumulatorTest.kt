package ghart.space.pi_drive.shared.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TripAccumulatorTest {

    private lateinit var accumulator: TripAccumulator

    @Before
    fun setUp() {
        accumulator = TripAccumulator()
    }

    // ── Distance ──────────────────────────────────────────────────────────────

    @Test
    fun `60 mph for 1 second adds 0_01667 miles`() {
        accumulator.update(60f, 0L)
        accumulator.update(60f, 1_000L) // 1 second later

        assertEquals(0.01667f, accumulator.distanceMiles, 0.001f)
    }

    @Test
    fun `60 mph for 60 seconds adds 1 mile`() {
        var t = 0L
        accumulator.update(60f, t)
        repeat(60) {
            t += 1_000L
            accumulator.update(60f, t)
        }

        assertEquals(1.0f, accumulator.distanceMiles, 0.01f)
    }

    @Test
    fun `speed 0 does not add distance`() {
        accumulator.update(0f, 0L)
        accumulator.update(0f, 5_000L)

        assertEquals(0f, accumulator.distanceMiles, 0f)
    }

    @Test
    fun `speed 0 does not advance moving duration`() {
        accumulator.update(0f, 0L)
        accumulator.update(0f, 5_000L)

        assertEquals(0L, accumulator.movingDurationMs)
    }

    // ── Max speed ─────────────────────────────────────────────────────────────

    @Test
    fun `max speed is tracked across all samples`() {
        accumulator.update(30f, 0L)
        accumulator.update(75f, 1_000L)
        accumulator.update(55f, 2_000L)

        assertEquals(75f, accumulator.maxSpeedMph, 0f)
    }

    @Test
    fun `max speed stays zero when no moving samples`() {
        accumulator.update(0f, 0L)
        accumulator.update(0f, 1_000L)

        assertEquals(0f, accumulator.maxSpeedMph, 0f)
    }

    // ── Avg speed ─────────────────────────────────────────────────────────────

    @Test
    fun `avg speed is distance over moving hours`() {
        // 60 mph for 60 s = 1 mile in 1/60 hour → avgSpeed = 1 / (1/60) = 60 mph
        var t = 0L
        accumulator.update(60f, t)
        repeat(60) {
            t += 1_000L
            accumulator.update(60f, t)
        }

        assertEquals(60f, accumulator.avgSpeedMph, 1f)
    }

    @Test
    fun `avg speed is zero before any moving samples`() {
        assertEquals(0f, accumulator.avgSpeedMph, 0f)
    }

    // ── Pause / resume ────────────────────────────────────────────────────────

    @Test
    fun `pause stops distance and duration accumulation`() {
        // Accumulate 1 s at 60 mph
        accumulator.update(60f, 0L)
        accumulator.update(60f, 1_000L)

        val distBefore = accumulator.distanceMiles
        val durBefore = accumulator.movingDurationMs

        accumulator.pause()
        // These should be ignored
        accumulator.update(60f, 2_000L)
        accumulator.update(60f, 3_000L)

        assertEquals(distBefore, accumulator.distanceMiles, 0f)
        assertEquals(durBefore, accumulator.movingDurationMs)
    }

    @Test
    fun `resume after pause does not count paused interval as duration`() {
        // 1 s at 60 mph → 0.01667 miles
        accumulator.update(60f, 0L)
        accumulator.update(60f, 1_000L)

        accumulator.pause()
        accumulator.resume()

        // First update after resume establishes new origin (no Δt added)
        accumulator.update(60f, 100_000L) // 99 s gap — should not count

        // Second update: 1 s at 60 mph
        accumulator.update(60f, 101_000L)

        // Total: 2 × 0.01667 = 0.03333 miles; the 99-second gap is excluded
        assertEquals(2 * 0.01667f, accumulator.distanceMiles, 0.002f)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset zeros all accumulators`() {
        accumulator.update(60f, 0L)
        accumulator.update(60f, 1_000L)
        accumulator.fuelTracker.update(fuelRateLph = 5f, dtMs = 1_000L)

        accumulator.reset()

        assertEquals(0f, accumulator.distanceMiles, 0f)
        assertEquals(0L, accumulator.movingDurationMs)
        assertEquals(0f, accumulator.maxSpeedMph, 0f)
        assertEquals(0f, accumulator.avgSpeedMph, 0f)
        assertEquals(0f, accumulator.fuelTracker.totalFuelLiters, 0f)
    }

    @Test
    fun `reset after pause allows accumulation again`() {
        accumulator.update(60f, 0L)
        accumulator.pause()
        accumulator.reset()

        accumulator.update(60f, 0L)
        accumulator.update(60f, 1_000L)

        assertEquals(0.01667f, accumulator.distanceMiles, 0.001f)
    }

    // ── toSummary ─────────────────────────────────────────────────────────────

    @Test
    fun `toSummary returns correct distance and max speed`() {
        accumulator.update(0f, 0L)
        accumulator.update(80f, 1_000L)  // Δt = 1 s, speed = 80 mph → 0.02222 mi
        accumulator.update(60f, 2_000L)  // Δt = 1 s, speed = 60 mph → 0.01667 mi

        val summary = accumulator.toSummary()

        assertEquals(0.02222f + 0.01667f, summary.distanceMiles, 0.002f)
        assertEquals(80f, summary.maxSpeedMph, 0f)
    }

    @Test
    fun `toSummary avgMpg is null with no fuel data`() {
        accumulator.update(60f, 0L)
        accumulator.update(60f, 1_000L)

        assertNull(accumulator.toSummary().avgMpg)
    }

    @Test
    fun `toSummary avgMpg is populated when fuel tracker has data`() {
        // 60 mph for 1 hour = 1 mile per sample × 3600 samples
        var t = 0L
        accumulator.update(60f, t)
        repeat(3600) {
            val dt = 1_000L
            t += dt
            accumulator.update(60f, t)
            accumulator.fuelTracker.update(fuelRateLph = 6f, speedKmh = 97, dtMs = dt)
        }

        val summary = accumulator.toSummary()
        assertTrue("avgMpg should be positive", (summary.avgMpg ?: 0f) > 0f)
    }
}
