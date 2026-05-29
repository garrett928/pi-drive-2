package ghart.space.pi_drive.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.obd.InitResult
import ghart.space.pi_drive.shared.ui.components.PDButtonPrimary
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.navigation.NavRoutes
import ghart.space.pi_drive.ui.viewmodel.ConnectViewModel


/** OBD PIDs that map to distinct vehicle metrics (excludes support range queries). */
private val HARDWARE_PIDS = setOf(0x05, 0x0C, 0x0D, 0x0F, 0x10, 0x11, 0x2F, 0x42, 0x5C)

/** Total metrics Pi Drive tracks (9 hardware + 7 software/derived). */
private const val TOTAL_SIGNALS = 16

/**
 * Step 3 of the connect flow — shows the adapter's vehicle info, supported signal
 * count, and a "Go to dashboard" button.
 *
 * @param navController Used to navigate back to [NavRoutes.HOME].
 * @param viewModel     Hilt-injected; shared across the connect flow.
 */
@Composable
fun ConnectDoneScreen(
    navController: NavController,
    viewModel: ConnectViewModel = hiltViewModel(
        remember(navController) { navController.getBackStackEntry(NavRoutes.CONNECT_GRAPH) }
    ),
) {
    val initResult by viewModel.initResult.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Success icon
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = PiDriveTheme.colors.success,
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Adapter ready",
            style = PiDriveTheme.typography.titleLarge,
            color = PiDriveTheme.colors.fg,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Vehicle info card
        PDCard(
            contentPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            initResult?.let { result ->
                VehicleInfoSection(result = result)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        PDButtonPrimary(
            text = "Go to dashboard",
            onClick = {
                navController.navigate(NavRoutes.HOME) {
                    popUpTo(NavRoutes.HOME) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Private composables ──────────────────────────────────────────────────────

@Composable
private fun VehicleInfoSection(result: InitResult) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val vehicleInfo = result.vehicleInfo
    val supportedHardware = result.supportedPids.intersect(HARDWARE_PIDS).size
    val softwareMetrics = TOTAL_SIGNALS - HARDWARE_PIDS.size  // 7 derived signals
    val supportedSignals = supportedHardware + softwareMetrics

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Vehicle header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = colors.accent.base,
                modifier = Modifier.size(24.dp),
            )
            Column {
                val vehicleName = buildVehicleLabel(vehicleInfo?.year, vehicleInfo?.make)
                Text(
                    text = vehicleName,
                    style = type.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.fg,
                )
            }
        }

        // Masked VIN
        if (vehicleInfo?.maskedVin != null) {
            InfoRow(label = "VIN", value = vehicleInfo.maskedVin)
        } else if (vehicleInfo?.vin != null) {
            InfoRow(label = "VIN", value = vehicleInfo.vin)
        }

        // Supported signals
        InfoRow(
            label = "Signals",
            value = "$supportedSignals of $TOTAL_SIGNALS supported",
        )

        // Protocol
        val protocol = result.protocol
        if (protocol != null) {
            InfoRow(label = "Protocol", value = protocol)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = type.bodySmall,
            color = colors.fgDim,
        )
        Text(
            text = value,
            style = type.bodySmall,
            color = colors.fg,
        )
    }
}

private fun buildVehicleLabel(year: Int?, make: String?): String = when {
    year != null && make != null -> "$year $make"
    make != null                 -> make
    year != null                 -> "Model Year $year"
    else                         -> "Unknown vehicle"
}
