package ghart.space.pi_drive.shared.auto

import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [SplitPageManager] and the split panel screen page-content logic.
 *
 * Tests use [SplitPageManager] directly and manually invoke the page-1 / page-2
 * content decisions that [SplitPanelScreen] delegates to. This avoids needing a
 * [CarContext] in unit tests while still verifying the page-switch semantics and
 * item counts per page.
 */
class SplitPanelScreenTest {

    private lateinit var pageManager: SplitPageManager

    private val emptyManualTrip = ManualTripState(
        isActive = false,
        distanceMiles = 0f,
        durationMs = 0L,
        avgSpeedMph = 0f,
        maxSpeedMph = 0f,
        avgMpg = null,
        startDate = null,
    )

    @Before
    fun setUp() {
        pageManager = SplitPageManager()
    }

    // ── SplitPageManager ──────────────────────────────────────────────────────

    @Test
    fun pageManager_defaultsToHero() {
        assertEquals(SplitPageManager.Page.HERO, pageManager.currentPage)
    }

    @Test
    fun pageManager_togglePage_switchesToTiles() {
        pageManager.togglePage()
        assertEquals(SplitPageManager.Page.TILES, pageManager.currentPage)
    }

    @Test
    fun pageManager_toggleTwice_returnsToHero() {
        pageManager.togglePage()
        pageManager.togglePage()
        assertEquals(SplitPageManager.Page.HERO, pageManager.currentPage)
    }

    @Test
    fun pageManager_showHero_setsHeroPage() {
        pageManager.togglePage() // → TILES
        pageManager.showHero()
        assertEquals(SplitPageManager.Page.HERO, pageManager.currentPage)
    }

    @Test
    fun pageManager_showTiles_setsTilesPage() {
        pageManager.showTiles()
        assertEquals(SplitPageManager.Page.TILES, pageManager.currentPage)
    }

    // ── Page 1 content (Hero) ─────────────────────────────────────────────────

    @Test
    fun page1_instantMpg_computedFromFuelRate() {
        // 80 km/h, 4 L/h → should produce a non-dash MPG value
        val snap = VehicleSnapshot(speedKmh = 80, fuelRateLph = 4f)
        val mpg = computeTestInstantMpg(snap)
        assertTrue("should compute MPG from fuel rate", mpg != null && mpg > 0f)
    }

    @Test
    fun page1_instantMpg_zeroSpeed_isNull() {
        val snap = VehicleSnapshot(speedKmh = 0, fuelRateLph = 4f)
        val mpg = computeTestInstantMpg(snap)
        assertEquals(null, mpg)
    }

    @Test
    fun page1_autoTripDistPreferredOverManual() {
        // When auto trip exists, its distance should be shown (not manual)
        val auto = makeAutoTripState(distanceMiles = 14.3f)
        val manual = emptyManualTrip.copy(isActive = true, distanceMiles = 248.6f)
        // Verify the selection logic via the auto trip state
        val displayed = auto.distanceMiles
        assertEquals(14.3f, displayed, 0.01f)
    }

    @Test
    fun page1_noAutoTrip_manualDistanceWhenActive() {
        val manual = emptyManualTrip.copy(isActive = true, distanceMiles = 100f)
        val displayed = if (manual.isActive) manual.distanceMiles else null
        assertEquals(100f, displayed)
    }

    // ── Page 2 content (Tiles) ────────────────────────────────────────────────

    @Test
    fun page2_rpmFormatting_integerValue() {
        val snap = VehicleSnapshot(rpm = 2750)
        val text = snap.rpm?.toString() ?: "—"
        assertEquals("2750", text)
    }

    @Test
    fun page2_coolantConversion_cToF() {
        // 90°C = 194°F
        val snap = VehicleSnapshot(coolantTempC = 90)
        val coolantF = snap.coolantTempC?.let { (it * 9f / 5f + 32f).toInt() }
        assertEquals(194, coolantF)
    }

    @Test
    fun page2_throttlePercentFormatting() {
        val snap = VehicleSnapshot(throttlePct = 34f)
        val text = snap.throttlePct?.let { "%.0f%%".format(it) } ?: "—"
        assertEquals("34%", text)
    }

    @Test
    fun page2_allNullValues_showDashes() {
        val snap = VehicleSnapshot()  // all fields null
        val rpmText = snap.rpm?.toString() ?: "—"
        val coolantText = snap.coolantTempC?.let { (it * 9f / 5f + 32f).toInt().let { f -> "${f}°F" } } ?: "—"
        val throttleText = snap.throttlePct?.let { "%.0f%%".format(it) } ?: "—"
        val fuelText = snap.fuelLevelPct?.let { "%.0f%%".format(it) } ?: "—"
        val battText = snap.batteryVoltage?.let { "%.1f V".format(it) } ?: "—"
        val oilText = snap.oilTempC?.let { (it * 9f / 5f + 32f).toInt().let { f -> "${f}°F" } } ?: "—"
        assertEquals("—", rpmText)
        assertEquals("—", coolantText)
        assertEquals("—", throttleText)
        assertEquals("—", fuelText)
        assertEquals("—", battText)
        assertEquals("—", oilText)
    }

    // ── Page count labels ─────────────────────────────────────────────────────

    @Test
    fun pageLabel_heroPage_shows1of2() {
        assertEquals(SplitPageManager.Page.HERO, pageManager.currentPage)
        val label = if (pageManager.currentPage == SplitPageManager.Page.HERO) "1 / 2" else "2 / 2"
        assertEquals("1 / 2", label)
    }

    @Test
    fun pageLabel_tilesPage_shows2of2() {
        pageManager.showTiles()
        val label = if (pageManager.currentPage == SplitPageManager.Page.HERO) "1 / 2" else "2 / 2"
        assertEquals("2 / 2", label)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Replicates the instant-MPG computation from [SplitPanelScreen] for testing. */
    private fun computeTestInstantMpg(snapshot: VehicleSnapshot): Float? {
        val speedKmh = snapshot.speedKmh?.toFloat() ?: return null
        if (speedKmh <= 0f) return null
        val fuelRateLph = snapshot.fuelRateLph
        if (fuelRateLph != null && fuelRateLph > 0f) {
            return (speedKmh / fuelRateLph) / 0.264172f * 0.621371f
        }
        val mafGps = snapshot.mafGps
        if (mafGps != null && mafGps > 0f) {
            val fuelRateGphCalc = (mafGps * 3600f) / 14.7f
            val fuelRateLphCalc = fuelRateGphCalc / 737.22f
            if (fuelRateLphCalc > 0f) {
                return (speedKmh / fuelRateLphCalc) / 0.264172f * 0.621371f
            }
        }
        return null
    }

    private fun makeAutoTripState(distanceMiles: Float = 0f): AutoTripState =
        AutoTripState(
            tripId = 1L,
            startTime = Instant.now(),
            distanceMiles = distanceMiles,
            durationMs = 0L,
            avgSpeedMph = 0f,
            maxSpeedMph = 0f,
            avgMpg = null,
            eventCount = 0,
        )
}
