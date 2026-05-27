package ghart.space.pi_drive.ui.screens.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/** Step 2 of the connect flow — Phase 4.2 implements the init checklist. */
@Composable
fun ConnectPairScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Connect — Pair", color = PiDriveTheme.colors.fg)
    }
}
