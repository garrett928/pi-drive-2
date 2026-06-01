package ghart.space.pi_drive.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.components.PDButtonPrimary
import ghart.space.pi_drive.shared.ui.components.PDButtonSecondary
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Bottom sheet explaining why Bluetooth permissions are required.
 *
 * Shown when [PermissionState.ShowRationale] is the current state — i.e. the user
 * previously denied a permission but hasn't selected "Don't ask again." The sheet
 * explains the purpose of each permission and provides an "Allow" button that
 * re-triggers the system permission dialog.
 *
 * @param onAllow    Called when the user taps "Allow" — caller should trigger the system dialog.
 * @param onDismiss  Called when the user taps "Not now" or swipes the sheet away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainPermissionsSheet(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElev,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = colors.accent.base,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Bluetooth Access Required",
                    style = type.titleMedium,
                    color = colors.fg,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pi Drive needs Bluetooth to connect to your OBD-II adapter and read live vehicle data.",
                style = type.bodyMedium,
                color = colors.fgMuted,
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = colors.borderS)
            Spacer(modifier = Modifier.height(20.dp))

            // Permission items
            PermissionItem(
                icon = Icons.Filled.Bluetooth,
                title = "Bluetooth Connect",
                description = "Communicate with your OBDLink LX adapter",
            )
            Spacer(modifier = Modifier.height(14.dp))
            PermissionItem(
                icon = Icons.Filled.Bluetooth,
                title = "Bluetooth Scan",
                description = "Discover nearby Bluetooth devices",
            )
            Spacer(modifier = Modifier.height(14.dp))
            PermissionItem(
                icon = Icons.Filled.Settings,
                title = "Precise Location",
                description = "Required by Android for Bluetooth scanning",
            )

            Spacer(modifier = Modifier.height(28.dp))

            PDButtonPrimary(
                text = "Allow Bluetooth Access",
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            PDButtonSecondary(
                text = "Not Now",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Bottom sheet shown when Bluetooth permissions are permanently denied.
 *
 * The system dialog will not appear; the user must grant permissions via Android Settings.
 *
 * @param onOpenSettings Called when the user taps "Open Settings".
 * @param onDismiss      Called when the user taps "Cancel".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermanentlyDeniedSheet(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElev,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = colors.warn,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Enable in Settings",
                    style = type.titleMedium,
                    color = colors.fg,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Bluetooth access was denied. To use Pi Drive, please open Android Settings and grant Bluetooth permissions to this app.",
                style = type.bodyMedium,
                color = colors.fgMuted,
            )

            Spacer(modifier = Modifier.height(28.dp))

            PDButtonPrimary(
                text = "Open Settings",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            PDButtonSecondary(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent.base,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = type.labelSmall,
                color = colors.fg,
            )
            Text(
                text = description,
                style = type.bodySmall,
                color = colors.fgMuted,
            )
        }
    }
}
