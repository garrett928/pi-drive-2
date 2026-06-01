package ghart.space.pi_drive

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end navigation tests for the three-tab app structure.
 *
 * Launches [MainActivity] in demo mode and verifies that tapping the bottom navigation
 * tabs correctly navigates between the Live, Trips, and Settings screens.
 *
 * These tests confirm that the NavHost routes are correctly wired and that each root
 * screen renders its title or a recognisable landmark node.
 */
@RunWith(AndroidJUnit4::class)
class NavigationE2ETest {

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

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ── Tests ──────────────────────────────────────────────────────────────────

    /**
     * Taps the "Trips" tab and verifies the trips screen renders.
     *
     * The trips screen shows "Trips" in the top app bar and a "This Week" or empty-state
     * message in the content area.
     */
    @Test
    fun navigation_tripsTab_showsTripScreen() {
        composeTestRule.waitForIdle()

        // Tap the Trips navigation tab (content description = "Trips")
        composeTestRule.onNodeWithText("Trips").performClick()
        composeTestRule.waitForIdle()

        // Trips screen shows its title in the top bar
        composeTestRule.onNodeWithText("Trip History").assertIsDisplayed()
    }

    /**
     * Taps the "Settings" tab and verifies the settings root screen renders.
     *
     * The settings screen shows a section header such as "Data & Display".
     */
    @Test
    fun navigation_settingsTab_showsSettingsScreen() {
        composeTestRule.waitForIdle()

        // Tap the Settings navigation tab
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        // Settings root shows the "Data & Display" section header
        composeTestRule.onNodeWithText("Data & Display").assertIsDisplayed()
    }

    /**
     * Verifies that navigating from Settings back to the Live tab restores the dashboard.
     */
    @Test
    fun navigation_backToLive_restoresDashboard() {
        composeTestRule.waitForIdle()

        // Go to Settings
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        // Return to Live tab
        composeTestRule.onNodeWithText("Live").performClick()
        composeTestRule.waitForIdle()

        // Dashboard is visible again (LIVE pill is present)
        composeTestRule.onNodeWithText("LIVE").assertIsDisplayed()
    }

    private companion object {
        const val DEV_PREFS_NAME = "pi_drive_dev_settings"
    }
}
