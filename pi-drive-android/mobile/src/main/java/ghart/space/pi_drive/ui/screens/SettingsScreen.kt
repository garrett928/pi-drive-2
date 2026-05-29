package ghart.space.pi_drive.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.ui.components.PDRow
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.navigation.NavRoutes

/**
 * Settings root screen — Phase 8.1 will replace this with the full vehicle card,
 * section rows, and all sub-screen navigation. This stub exposes the Telemetry
 * Server entry added in Phase 7.3 for end-to-end verification.
 */
@Composable
fun SettingsScreen(navController: NavController) {
    val colors = PiDriveTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        PDRow(
            title = "Telemetry Server",
            subtitle = "Server URL, VIN, streaming, signal selection",
            leadingIcon = Icons.Rounded.CloudUpload,
            onClick = { navController.navigate(NavRoutes.SETTINGS_SERVER) },
            trailing = {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.fgDim,
                )
            },
        )
        HorizontalDivider(color = colors.borderS)
    }
}
