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
 * Settings root screen — Phase 8.1 will replace this with the vehicle card,
 * section rows, and all sub-screen navigation.
 */
@Composable
fun SettingsScreen(navController: NavController) {
    val colors = PiDriveTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Settings",
            style = PiDriveTheme.typography.titleLarge,
            color = colors.fg,
        )
    }
}
