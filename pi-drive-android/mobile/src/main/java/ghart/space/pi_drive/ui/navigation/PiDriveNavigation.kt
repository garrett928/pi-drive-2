package ghart.space.pi_drive.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

// ---------------------------------------------------------------------------
// PiDriveNavigation — bottom tab bar with 3 tabs.
// ---------------------------------------------------------------------------

/** Data describing one bottom navigation tab. */
private data class NavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    NavTab(NavRoutes.HOME,     "Live",     Icons.Rounded.DirectionsCar),
    NavTab(NavRoutes.TRIPS,    "Trips",    Icons.Rounded.Route),
    NavTab(NavRoutes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

/**
 * Bottom navigation bar for the three main tabs.
 *
 * Observes the current back-stack entry to highlight the correct tab.
 * Navigating between tabs clears the back stack above the start destination
 * and restores state, which is standard Android tab navigation behaviour.
 *
 * @param navController The NavController driving navigation state.
 */
@Composable
fun PiDriveBottomNav(navController: NavController) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = colors.bgElev,
        contentColor = colors.fgMuted,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        // Pop up to HOME so the back button doesn't stack tabs
                        popUpTo(NavRoutes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = type.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent.base,
                    selectedTextColor = colors.accent.base,
                    indicatorColor = colors.accent.soft,
                    unselectedIconColor = colors.fgMuted,
                    unselectedTextColor = colors.fgMuted,
                ),
            )
        }
    }
}
