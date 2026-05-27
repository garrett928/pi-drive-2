package ghart.space.pi_drive.ui.screens.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/** Step 1 of the connect flow — Phase 4.2 implements the full scan UI. */
@Composable
fun ConnectScanScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Connect — Scan", color = PiDriveTheme.colors.fg)
    }
}
