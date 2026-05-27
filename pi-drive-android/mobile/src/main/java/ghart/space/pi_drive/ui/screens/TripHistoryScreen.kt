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
 * Trip history list — Phase 8.4 will replace this with weekly summary,
 * day-grouped trip cards, and CSV export.
 */
@Composable
fun TripHistoryScreen(navController: NavController) {
    val colors = PiDriveTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Trip History",
            style = PiDriveTheme.typography.titleLarge,
            color = colors.fg,
        )
    }
}
