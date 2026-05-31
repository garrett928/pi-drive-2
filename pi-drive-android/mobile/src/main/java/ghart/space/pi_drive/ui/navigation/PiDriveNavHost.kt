package ghart.space.pi_drive.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import ghart.space.pi_drive.ui.screens.LiveDashboardScreen
import ghart.space.pi_drive.ui.screens.SettingsScreen
import ghart.space.pi_drive.ui.screens.TripHistoryScreen
import ghart.space.pi_drive.ui.screens.connect.ConnectDoneScreen
import ghart.space.pi_drive.ui.screens.connect.ConnectPairScreen
import ghart.space.pi_drive.ui.screens.connect.ConnectScanScreen
import ghart.space.pi_drive.ui.screens.settings.SettingsAALayoutScreen
import ghart.space.pi_drive.ui.screens.settings.SettingsHomeLayoutScreen
import ghart.space.pi_drive.ui.screens.settings.SettingsServerScreen
import ghart.space.pi_drive.ui.screens.settings.SettingsThresholdsScreen

/**
 * Root navigation host wiring all app routes to their composable screens.
 *
 * New screens must be added here as well as to [NavRoutes]. The start
 * destination is [NavRoutes.HOME] (live dashboard).
 *
 * @param navController The NavHostController supplied by [PiDriveScaffold].
 * @param modifier      Applied to the [NavHost] container.
 */
@Composable
fun PiDriveNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
        modifier = modifier,
    ) {
        // ── Bottom tab screens ────────────────────────────────────────────
        composable(NavRoutes.HOME)     { LiveDashboardScreen(navController) }
        composable(NavRoutes.TRIPS)    { TripHistoryScreen(navController) }
        composable(NavRoutes.SETTINGS) { SettingsScreen(navController) }

        // ── Connect flow (nested graph so all three screens share one ConnectViewModel) ──
        navigation(
            route = NavRoutes.CONNECT_GRAPH,
            startDestination = NavRoutes.CONNECT_SCAN,
        ) {
            composable(NavRoutes.CONNECT_SCAN) { ConnectScanScreen(navController) }
            composable(NavRoutes.CONNECT_PAIR) { ConnectPairScreen(navController) }
            composable(NavRoutes.CONNECT_DONE) { ConnectDoneScreen(navController) }
        }

        // ── Settings sub-screens ──────────────────────────────────────────
        composable(NavRoutes.SETTINGS_SERVER)      { SettingsServerScreen(navController) }
        composable(NavRoutes.SETTINGS_HOME_LAYOUT) { SettingsHomeLayoutScreen() }
        composable(NavRoutes.SETTINGS_AA_LAYOUT)   { SettingsAALayoutScreen(navController) }
        composable(NavRoutes.SETTINGS_THRESHOLDS)  { SettingsThresholdsScreen(navController) }
    }
}
