package ghart.space.pi_drive.ui.permissions

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Composable permission gate for Bluetooth access.
 *
 * Renders one of three states based on the current [PermissionState]:
 * - **Granted:** shows [content] directly.
 * - **ShowRationale:** shows [ExplainPermissionsSheet] explaining why Bluetooth is needed;
 *   tapping "Allow" re-triggers the system permission dialog.
 * - **PermanentlyDenied:** shows [PermanentlyDeniedSheet] directing the user to Android Settings.
 * - **NotRequested:** requests permissions immediately on first composition.
 *
 * Typical usage inside the Bluetooth connect flow:
 * ```kotlin
 * PermissionGate {
 *     ConnectScanScreen(navController)
 * }
 * ```
 *
 * @param content Composable to display when all Bluetooth permissions are granted.
 */
@Composable
fun PermissionGate(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf<PermissionState>(PermissionState.NotRequested) }

    val permManager = rememberBluetoothPermissionManager { newState ->
        permissionState = newState
    }

    // Initialise from current permission state
    if (permissionState == PermissionState.NotRequested) {
        permissionState = permManager.currentState()
        if (permissionState == PermissionState.NotRequested) {
            // First-time request — fire system dialog immediately
            permissionState = PermissionState.ShowRationale
        }
    }

    when (permissionState) {
        PermissionState.Granted -> {
            content()
        }

        PermissionState.ShowRationale -> {
            // Show content behind the sheet so the UI doesn't appear blank
            content()
            ExplainPermissionsSheet(
                onAllow = { permManager.requestPermissions() },
                onDismiss = {
                    // Re-check state; user may have navigated away
                    permissionState = permManager.currentState()
                },
            )
        }

        PermissionState.PermanentlyDenied -> {
            content()
            PermanentlyDeniedSheet(
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    )
                },
                onDismiss = {
                    permissionState = permManager.currentState()
                },
            )
        }

        PermissionState.NotRequested -> {
            // Handled above by the initialisation block
            content()
        }
    }
}
