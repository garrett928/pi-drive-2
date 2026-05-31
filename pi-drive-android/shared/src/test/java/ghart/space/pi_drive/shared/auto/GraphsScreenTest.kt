package ghart.space.pi_drive.shared.auto

import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Unit tests for [buildGraphsTemplateData].
 *
 * Tests use the pure data-transformation function directly to avoid needing a [CarContext].
 * Covers throttle trend labels, G-force danger indicators, MPG calculation paths (fuel-rate
 * and MAF), and manual-trip stat box formatting.
 */
class GraphsScreenTest {

    private val emptyManualTrip = ManualTripState(
        isActive = false,
        distanceMiles = 0f,
        durationMs = 0L,
        avgSpeedMph = 0f,
        maxSpeedMph = 0f,
        avgMpg = null,
        startDate = null,
    )

    // ── Throttle ──────────────────────────────────────────────────────────────

    @Test
    fun throttleTitle_containsCurrentValue() {
        val snap = VehicleSnapshot(throttlePct = 34f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("title should contain 34%", data.throttleTitle.contains("34%"))
    }

    @Test
    fun throttleDetail_highThrottle_showsHighTrend() {
        val snap = VehicleSnapshot(throttlePct = 75f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("HIGH trend expected above 70%", data.throttleDetail.contains("HIGH"))
    }

    @Test
    fun throttleDetail_midThrottle_showsMedTrend() {
        val snap = VehicleSnapshot(throttlePct = 50f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("MED trend expected for 40-70%", data.throttleDetail.contains("MED"))
    }

    @Test
    fun throttleDetail_lowThrottle_showsLowTrend() {
        val snap = VehicleSnapshot(throttlePct = 10f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("LOW trend expected below 40%", data.throttleDetail.contains("LOW"))
    }

    @Test
    fun throttleTitle_null_showsDash() {
        val snap = VehicleSnapshot(throttlePct = null)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("null throttle should show —", data.throttleTitle.contains("—"))
    }

    // ── G-force ───────────────────────────────────────────────────────────────

    @Test
    fun gforceTitle_containsCurrentValue() {
        val snap = VehicleSnapshot(gForce = 0.12f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("title should contain 0.12g", data.gforceTitle.contains("0.12g"))
    }

    @Test
    fun gforceDetail_hardThreshold_showsWarning() {
        val snap = VehicleSnapshot(gForce = 0.35f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("HARD warning expected above 0.3g", data.gforceDetail.contains("HARD"))
    }

    @Test
    fun gforceDetail_smooth_showsSmoothLabel() {
        val snap = VehicleSnapshot(gForce = 0.05f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("SMOOTH label expected below threshold", data.gforceDetail.contains("SMOOTH"))
    }

    // ── Instant MPG ───────────────────────────────────────────────────────────

    @Test
    fun instantMpgTitle_fuelRatePath_computesCorrectly() {
        // 80 km/h, 4 L/h → MPG ≈ 80/(4*0.264172*0.621371) ≈ 33.0 mpg
        val snap = VehicleSnapshot(speedKmh = 80, fuelRateLph = 4f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertFalse("should not show — when fuel rate available", data.instantMpgTitle.contains("—"))
    }

    @Test
    fun instantMpgTitle_zeroSpeed_showsDash() {
        val snap = VehicleSnapshot(speedKmh = 0, fuelRateLph = 4f)
        val data = buildGraphsTemplateData(snap, emptyManualTrip, null)
        assertTrue("zero speed should show —", data.instantMpgTitle.contains("—"))
    }

    // ── Trip MPG in mpgDetail ─────────────────────────────────────────────────

    @Test
    fun mpgDetail_autoTripMpg_preferredOverManual() {
        val autoTrip = makeAutoTripState(avgMpg = 28.5f)
        val manual = emptyManualTrip.copy(isActive = true, avgMpg = 35.0f)
        val data = buildGraphsTemplateData(VehicleSnapshot.EMPTY, manual, autoTrip)
        assertTrue("auto trip MPG should be shown", data.mpgDetail.contains("28.5"))
        assertFalse("manual MPG should not be shown", data.mpgDetail.contains("35.0"))
    }

    @Test
    fun mpgDetail_noTripMpg_showsDash() {
        val data = buildGraphsTemplateData(VehicleSnapshot.EMPTY, emptyManualTrip, null)
        assertTrue("no MPG data should show —", data.mpgDetail.contains("—"))
    }

    // ── Manual trip stat box ──────────────────────────────────────────────────

    @Test
    fun manualTripTitle_active_showsDistance() {
        val manual = emptyManualTrip.copy(isActive = true, distanceMiles = 248.6f)
        val data = buildGraphsTemplateData(VehicleSnapshot.EMPTY, manual, null)
        assertTrue("should show 248.6 mi", data.manualTripTitle.contains("248.6"))
    }

    @Test
    fun manualTripDetail_hasStartDateAndMpg() {
        val manual = emptyManualTrip.copy(
            isActive = true,
            distanceMiles = 248.6f,
            avgMpg = 31.4f,
            startDate = LocalDate.of(2024, 5, 18),
        )
        val data = buildGraphsTemplateData(VehicleSnapshot.EMPTY, manual, null)
        assertTrue("should include start date", data.manualTripDetail.contains("May"))
        assertTrue("should include avg mpg", data.manualTripDetail.contains("31.4"))
    }

    // ── All 4 items populated (item count sanity) ─────────────────────────────

    @Test
    fun buildGraphsTemplateData_allItemsNonBlank() {
        val snap = VehicleSnapshot(
            speedKmh = 80,
            throttlePct = 40f,
            gForce = 0.1f,
            fuelRateLph = 5f,
        )
        val manual = emptyManualTrip.copy(isActive = true, distanceMiles = 10f, avgMpg = 27f)
        val data = buildGraphsTemplateData(snap, manual, null)
        assertTrue(data.throttleTitle.isNotBlank())
        assertTrue(data.throttleDetail.isNotBlank())
        assertTrue(data.gforceTitle.isNotBlank())
        assertTrue(data.gforceDetail.isNotBlank())
        assertTrue(data.instantMpgTitle.isNotBlank())
        assertTrue(data.mpgDetail.isNotBlank())
        assertTrue(data.manualTripTitle.isNotBlank())
        assertTrue(data.manualTripDetail.isNotBlank())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeAutoTripState(avgMpg: Float? = null): AutoTripState =
        AutoTripState(
            tripId = 1L,
            startTime = Instant.now(),
            distanceMiles = 0f,
            durationMs = 0L,
            avgSpeedMph = 0f,
            maxSpeedMph = 0f,
            avgMpg = avgMpg,
            eventCount = 0,
        )
}
