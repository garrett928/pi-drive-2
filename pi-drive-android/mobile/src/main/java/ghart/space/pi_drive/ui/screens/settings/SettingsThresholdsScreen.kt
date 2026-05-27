package ghart.space.pi_drive.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/** Thresholds + alerts editor — Phase 8.3 implements the full screen. */
@Composable
fun SettingsThresholdsScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings — Thresholds", color = PiDriveTheme.colors.fg)
    }
}
