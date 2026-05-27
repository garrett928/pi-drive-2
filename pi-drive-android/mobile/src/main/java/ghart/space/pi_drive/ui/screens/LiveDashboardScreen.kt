package ghart.space.pi_drive.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Live dashboard screen — Phase 3 will replace this placeholder with the
 * featured metric, MPG row, sparkline, and tile grid.
 */
@Composable
fun LiveDashboardScreen(navController: NavController) {
    val colors = PiDriveTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Live Dashboard",
            style = PiDriveTheme.typography.titleLarge,
            color = colors.fg,
        )
    }
}
