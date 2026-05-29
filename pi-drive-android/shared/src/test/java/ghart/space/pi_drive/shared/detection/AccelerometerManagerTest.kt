package ghart.space.pi_drive.shared.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for [AccelerometerManager.lowPass] and [CalibrationManager.identify].
 *
 * These test only the pure-Kotlin computation logic — no Android sensor APIs are
 * exercised, so no Robolectric or instrumentation is needed.
 */
class AccelerometerManagerTest {

    // ── Low-pass filter ───────────────────────────────────────────────────────

    /**
     * A sudden spike (prev=0, raw=100) should be heavily attenuated by the filter.
     * With alpha=0.8: output = 0.8*0 + 0.2*100 = 20.
     */
    @Test
    fun `low-pass filter attenuates sudden spike`() {
        val result = AccelerometerManager.lowPass(prev = 0f, raw = 100f)
        assertEquals("Spike should be attenuated to 20", 20f, result, 0.001f)
    }

    /**
     * A steady signal (prev == raw) should pass through unchanged.
     * With alpha=0.8: output = 0.8*50 + 0.2*50 = 50.
     */
    @Test
    fun `low-pass filter passes steady signal unchanged`() {
        val result = AccelerometerManager.lowPass(prev = 50f, raw = 50f)
        assertEquals("Steady signal should pass through", 50f, result, 0.001f)
    }

    /**
     * After several iterations of a step input (0 → 10), the output converges
     * toward the input value. After 10 samples the filter output should be > 8.
     */
    @Test
    fun `low-pass filter converges toward step input`() {
        var filtered = 0f
        repeat(10) { filtered = AccelerometerManager.lowPass(prev = filtered, raw = 10f) }
        assertTrue("Filter should converge: filtered=$filtered", filtered > 8f)
    }

    /**
     * Custom alpha = 0.0 means no memory: output equals raw.
     */
    @Test
    fun `low-pass filter with alpha 0 returns raw value`() {
        val result = AccelerometerManager.lowPass(prev = 99f, raw = 42f, alpha = 0f)
        assertEquals(42f, result, 0.001f)
    }

    /**
     * Custom alpha = 1.0 means no update: output equals prev.
     */
    @Test
    fun `low-pass filter with alpha 1 returns prev value`() {
        val result = AccelerometerManager.lowPass(prev = 99f, raw = 42f, alpha = 1f)
        assertEquals(99f, result, 0.001f)
    }

    // ── Calibration axis identification ───────────────────────────────────────

    /**
     * If Y-axis has the highest variance (typical for portrait-held phone driving
     * straight), [CalibrationManager.identify] should return axis=1.
     */
    @Test
    fun `calibration selects Y axis when Y has highest variance`() {
        // X: constant 0, Y: varies +/-5, Z: small noise
        val samples = (0..49).map { i ->
            floatArrayOf(
                0f,
                if (i % 2 == 0) 5f else -5f,   // Y: high variance
                (i % 3 - 1) * 0.1f,              // Z: low variance
            )
        }
        val (axis, _) = CalibrationManager.identify(samples)
        assertEquals("Y-axis (1) should be selected", 1, axis)
    }

    /**
     * If Z-axis has the highest variance, [CalibrationManager.identify] returns axis=2.
     */
    @Test
    fun `calibration selects Z axis when Z has highest variance`() {
        val samples = (0..49).map { i ->
            floatArrayOf(
                (i % 2) * 0.1f,                  // X: low variance
                (i % 3 - 1) * 0.1f,              // Y: low variance
                if (i % 2 == 0) 8f else -8f,     // Z: high variance
            )
        }
        val (axis, _) = CalibrationManager.identify(samples)
        assertEquals("Z-axis (2) should be selected", 2, axis)
    }

    /**
     * Sign detection: if the mean of the selected axis is positive, sign = +1.
     * Y-axis alternates 2/4 (mean=3, variance=1) — highest variance, positive mean.
     */
    @Test
    fun `calibration returns positive sign when mean is positive`() {
        val samples = (0..9).map { i -> floatArrayOf(0f, if (i % 2 == 0) 2f else 4f, 0f) }
        val (axis, sign) = CalibrationManager.identify(samples)
        assertEquals(1, axis)
        assertEquals(1, sign)
    }

    /**
     * Sign detection: if the mean of the selected axis is negative, sign = -1.
     * Y-axis alternates -2/-4 (mean=-3, variance=1) — highest variance, negative mean.
     */
    @Test
    fun `calibration returns negative sign when mean is negative`() {
        val samples = (0..9).map { i -> floatArrayOf(0f, if (i % 2 == 0) -2f else -4f, 0f) }
        val (axis, sign) = CalibrationManager.identify(samples)
        assertEquals(1, axis)
        assertEquals(-1, sign)
    }

    /** Empty samples list returns default (1, 1). */
    @Test
    fun `calibration returns default for empty samples`() {
        val (axis, sign) = CalibrationManager.identify(emptyList())
        assertEquals(1, axis)
        assertEquals(1, sign)
    }
}
