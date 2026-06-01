package ghart.space.pi_drive

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end instrumented test for the live dashboard.
 *
 * Launches [MainActivity] in demo/CRUISE mode (via developer SharedPreferences) and
 * verifies that the dashboard renders its core components:
 * - Featured metric section (shows the "LIVE" pill)
 * - Tile grid with the default 6 metric tiles
 *
 * The test configures demo mode by writing to the developer-settings SharedPreferences
 * file **before** the activity launches — matching exactly how [MainActivity.applyDevSettingsToAppConfig]
 * reads them during `onCreate`.
 */
@RunWith(AndroidJUnit4::class)
class DashboardE2ETest {

    /**
     * Order 0: Set SharedPreferences so MainActivity reads demo_mode=true.
     * Must run before the compose rule (order 1) launches the activity.
     */
    @get:Rule(order = 0)
    val demoModeSetup = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences(DEV_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("demo_mode", true)
                .putString("demo_scenario", "CRUISE")
                .commit()
        }

        override fun after() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences(DEV_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    /** Order 1: Launch MainActivity after demo mode is configured. */
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ── Tests ──────────────────────────────────────────────────────────────────

    /**
     * Verifies that the "LIVE" pill appears on the dashboard, which confirms the
     * featured metric section rendered and the data source is streaming snapshots.
     */
    @Test
    fun dashboard_showsLivePill() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("LIVE").assertIsDisplayed()
    }

    /**
     * Verifies that all 6 default tile metric labels are present in the grid.
     *
     * Default tiles from [DEFAULT_DASHBOARD_TILES]:
     *   RPM, Throttle, Coolant, Battery, Fuel, G-Force
     */
    @Test
    fun dashboard_showsDefaultSixTiles() {
        composeTestRule.waitForIdle()
        val expectedLabels = listOf("RPM", "Throttle", "Coolant", "Battery", "Fuel", "G-Force")
        for (label in expectedLabels) {
            composeTestRule.onNodeWithText(label, useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    /**
     * Verifies the featured metric section displays a label — in CRUISE demo mode
     * the default featured metric is Speed, shown as "SPEED" (uppercased).
     */
    @Test
    fun dashboard_featuredMetric_isVisible() {
        composeTestRule.waitForIdle()
        // FeaturedMetric renders the label uppercased; default featured is Speed
        composeTestRule.onNodeWithText("SPEED", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private companion object {
        /** Must match [DevSettingsManager.PREFS_NAME]. */
        const val DEV_PREFS_NAME = "pi_drive_dev_settings"
    }
}
