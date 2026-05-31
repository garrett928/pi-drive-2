package ghart.space.pi_drive.shared.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen

/**
 * Coordinator for Android Auto screen navigation.
 *
 * Creates the root [DialsScreen] and provides factory methods for the other screens
 * reachable from within the AA session. The [PiDriveCarAppSession] uses this class
 * to get the initial screen; navigation between screens is handled by each screen's
 * own [androidx.car.app.model.ActionStrip] using [androidx.car.app.ScreenManager].
 *
 * Navigation graph:
 * ```
 *   DialsScreen (root)
 *     ├─ ActionStrip "Graphs" → GraphsScreen (push)
 *     └─ ActionStrip "Panel"  → SplitPanelScreen (push)
 *   GraphsScreen
 *     └─ ActionStrip "Dials"  → pop (back to DialsScreen)
 *   SplitPanelScreen
 *     ├─ ActionStrip "← Hero" / "Tiles →" → in-place page toggle
 *     └─ ActionStrip "Dials"  → pop (back to DialsScreen)
 * ```
 *
 * Note: [TabTemplate] (which would enable tab-bar navigation) requires Car App API
 * level 6 (library 1.6+). The current target is API level 5 (library 1.4.0), so
 * [androidx.car.app.model.ActionStrip]-based navigation is used instead.
 *
 * @param carContext The [CarContext] provided by [PiDriveCarAppSession].
 */
class AAScreenManager(private val carContext: CarContext) {

    /**
     * Returns the root screen for the AA session.
     *
     * Called once from [PiDriveCarAppSession.onCreateScreen]; the [DialsScreen] takes
     * ownership of its own [androidx.car.app.ScreenManager] for onward navigation.
     */
    fun createRootScreen(): Screen = DialsScreen(carContext)
}
