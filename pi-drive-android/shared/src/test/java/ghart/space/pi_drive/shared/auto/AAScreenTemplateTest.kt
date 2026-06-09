package ghart.space.pi_drive.shared.auto

import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.settings.AALayoutConfig
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Template-construction tests for the three Android Auto screens.
 *
 * Unlike [DialsScreenTest] / [GraphsScreenTest] / [SplitPanelScreenTest] — which exercise
 * only the pure `build*TemplateData` helpers — these tests drive the real Car App Library
 * template assembly path by calling [androidx.car.app.Screen.onGetTemplate] on a live
 * [TestCarContext]. This is the only layer that catches Car App *runtime* validation
 * failures, e.g.:
 *
 * - "When a grid item is loading, the image must not be set and vice versa"
 *   ([GridItem] built without an image), and
 * - "Action list exceeded max number of 1 actions with custom titles"
 *   ([ActionStrip] with two custom-title actions).
 *
 * Both of those throw at `.build()` time inside `onGetTemplate()`, so a passing assertion
 * here proves the template is constructible on a head unit. Plain JVM unit tests on the
 * data helpers never reach this code and previously let both crashes ship.
 *
 * Robolectric supplies the Android context; [TestCarContext] / [ScreenController] supply the
 * Car App host so the screen's lifecycle-bound flow collectors run and `onGetTemplate()` is
 * invoked exactly as it would be on a real head unit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AAScreenTemplateTest {

    private lateinit var carContext: TestCarContext

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        // Start each test from a known, fully-populated bridge state so every grid slot and
        // action is rendered (including the danger "⚠" prefix and battery "· LIVE" label paths).
        AADataBridge.setAALayout(AALayoutConfig())
        AADataBridge.setSnapshot(POPULATED_SNAPSHOT)
        AADataBridge.setManualTripState(ACTIVE_MANUAL_TRIP)
        AADataBridge.setAutoTripState(null)
        AADataBridge.setConnectionState(CONNECTED_STATE)
    }

    @After
    fun tearDown() {
        // Reset shared singleton state so ordering between tests stays deterministic.
        AADataBridge.setSnapshot(VehicleSnapshot.EMPTY)
        AADataBridge.setManualTripState(EMPTY_MANUAL_TRIP)
        AADataBridge.setAutoTripState(null)
        AADataBridge.setConnectionState(ConnectionState.Disconnected())
        AADataBridge.setAALayout(AALayoutConfig())
    }

    // ── DialsScreen ─────────────────────────────────────────────────────────────

    @Test
    fun dialsScreen_onGetTemplate_buildsValidGridTemplate() {
        val template = renderTemplate(DialsScreen(carContext))
        assertTrue("DialsScreen should return a GridTemplate", template is GridTemplate)
    }

    @Test
    fun dialsScreen_onGetTemplate_emptyData_doesNotThrow() {
        // Disconnected + empty snapshot: tiles show "—" and the battery "· LIVE" label is absent.
        AADataBridge.setSnapshot(VehicleSnapshot.EMPTY)
        AADataBridge.setManualTripState(EMPTY_MANUAL_TRIP)
        AADataBridge.setConnectionState(ConnectionState.Disconnected())
        val template = renderTemplate(DialsScreen(carContext))
        assertTrue(template is GridTemplate)
    }

    // ── GraphsScreen ────────────────────────────────────────────────────────────

    @Test
    fun graphsScreen_onGetTemplate_buildsValidListTemplate() {
        val template = renderTemplate(GraphsScreen(carContext))
        assertTrue("GraphsScreen should return a ListTemplate", template is ListTemplate)
    }

    // ── SplitPanelScreen ────────────────────────────────────────────────────────

    @Test
    fun splitPanelScreen_onGetTemplate_page1_buildsValidGridTemplate() {
        val template = renderTemplate(SplitPanelScreen(carContext))
        assertTrue("SplitPanelScreen page 1 should return a GridTemplate", template is GridTemplate)
    }

    @Test
    fun splitPanelScreen_onGetTemplate_page2_buildsValidGridTemplate() {
        // Page 2 uses a different item list (6 metric tiles with danger prefixes) and a
        // different ActionStrip title, so it must be exercised separately from page 1.
        val screen = SplitPanelScreen(carContext)
        ScreenController(screen).moveToState(Lifecycle.State.STARTED)
        // Render page 1 first, then toggle to page 2 and render again.
        assertNotNull(screen.onGetTemplate())
        screen.showTilesPageForTest()
        val template = screen.onGetTemplate()
        assertTrue("SplitPanelScreen page 2 should return a GridTemplate", template is GridTemplate)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Attaches [screen] to a [ScreenController], drives it to STARTED (so the screen's
     * flow-collecting coroutines run, exactly as on a head unit), and returns the template
     * produced by [androidx.car.app.Screen.onGetTemplate]. Throws if template assembly fails.
     */
    private fun renderTemplate(screen: androidx.car.app.Screen): Template {
        ScreenController(screen).moveToState(Lifecycle.State.STARTED)
        return screen.onGetTemplate()
    }

    private companion object {
        // Values chosen to trip the danger thresholds (RPM ≥ 6500, coolant ≥ 240°F) so the
        // "⚠"-prefix branch of grid-item assembly is covered.
        val POPULATED_SNAPSHOT = VehicleSnapshot(
            speedKmh = 97,
            rpm = 6800,
            coolantTempC = 120,
            batteryVoltage = 14.2f,
            throttlePct = 82f,
            gForce = 0.4f,
            fuelRateLph = 8.5f,
            mafGps = 12.0f,
            fuelLevelPct = 55f,
            oilTempC = 95,
        )

        val CONNECTED_STATE = ConnectionState.Connected(
            adapterName = "OBDLink LX",
            protocol = "ISO 15765-4",
            pollRateHz = 4f,
        )

        val ACTIVE_MANUAL_TRIP = ManualTripState(
            isActive = true,
            distanceMiles = 14.3f,
            durationMs = 1_800_000L,
            avgSpeedMph = 32f,
            maxSpeedMph = 71f,
            avgMpg = 26.4f,
            startDate = java.time.LocalDate.of(2026, 5, 1),
        )

        val EMPTY_MANUAL_TRIP = ManualTripState(
            isActive = false,
            distanceMiles = 0f,
            durationMs = 0L,
            avgSpeedMph = 0f,
            maxSpeedMph = 0f,
            avgMpg = null,
            startDate = null,
        )
    }
}
