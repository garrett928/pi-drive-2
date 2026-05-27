package ghart.space.pi_drive.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.navigation.NavRoutes
import ghart.space.pi_drive.ui.navigation.PiDriveBottomNav
import ghart.space.pi_drive.ui.navigation.PiDriveNavHost

/**
 * Root scaffold that composes the top bar, bottom navigation, and nav host together.
 *
 * The bottom navigation bar is automatically hidden during the connect flow
 * (the 3-step Bluetooth pairing) since those screens are full-screen flows.
 *
 * This composable owns the [NavHostController] and passes it to both the
 * navigation bar (for tab-switching) and the nav host (for screen routing).
 */
@Composable
fun PiDriveScaffold() {
    val navController = rememberNavController()
    val colors = PiDriveTheme.colors

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: NavRoutes.HOME

    // Derive the title for the current route
    val screenTitle = when {
        currentRoute == NavRoutes.HOME                    -> "Pi Drive"
        currentRoute == NavRoutes.TRIPS                   -> "Trips"
        currentRoute == NavRoutes.SETTINGS                -> "Settings"
        currentRoute == NavRoutes.CONNECT_SCAN            -> "Connect"
        currentRoute == NavRoutes.CONNECT_PAIR            -> "Connecting"
        currentRoute == NavRoutes.CONNECT_DONE            -> "Connected"
        currentRoute == NavRoutes.SETTINGS_SERVER         -> "Telemetry Server"
        currentRoute == NavRoutes.SETTINGS_HOME_LAYOUT    -> "Phone Layout"
        currentRoute == NavRoutes.SETTINGS_AA_LAYOUT      -> "Android Auto Layout"
        currentRoute == NavRoutes.SETTINGS_THRESHOLDS     -> "Thresholds"
        else                                              -> "Pi Drive"
    }

    // Back navigation: show back button for all non-tab routes
    val showBack = currentRoute !in NavRoutes.bottomNavRoutes
    val onBack: (() -> Unit)? = if (showBack) ({ navController.navigateUp() }) else null

    // Hide bottom nav on the connect flow (full-screen steps)
    val showBottomNav = !currentRoute.startsWith("connect/")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            PiDriveTopBar(
                title = screenTitle,
                onBack = onBack,
            )
        },
        bottomBar = {
            if (showBottomNav) {
                PiDriveBottomNav(navController)
            }
        },
    ) { _ ->
        PiDriveNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
