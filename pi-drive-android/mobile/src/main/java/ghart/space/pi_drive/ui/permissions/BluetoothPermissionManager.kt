package ghart.space.pi_drive.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Manages Bluetooth runtime permission state for the connect flow.
 *
 * Checks [BLUETOOTH_PERMISSIONS] (BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION)
 * and determines the correct [PermissionState] to present:
 * - All granted → [PermissionState.Granted]
 * - Any denied but rationale appropriate → [PermissionState.ShowRationale]
 * - Any permanently denied → [PermissionState.PermanentlyDenied]
 *
 * Call [rememberBluetoothPermissionManager] to create an instance in a composable.
 * The [requestPermissions] function fires the system permission dialog and delivers
 * results to [onResult].
 *
 * @param activity     The current activity, used for [ActivityCompat.shouldShowRequestPermissionRationale].
 * @param launcher     The [ManagedActivityResultLauncher] for the multiple-permissions contract.
 * @param onResult     Callback called when the permission dialog completes. Receives the
 *                     combined [PermissionState] computed from all permission results.
 */
class BluetoothPermissionManager(
    private val activity: Activity,
    private val launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    private val onResult: (PermissionState) -> Unit,
) {

    /**
     * Returns the current [PermissionState] by checking all [BLUETOOTH_PERMISSIONS].
     * This is a synchronous check — it does not trigger any system UI.
     */
    fun currentState(): PermissionState {
        val allGranted = BLUETOOTH_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return PermissionState.Granted

        // Check if any denial is permanent (no rationale to show)
        val anyPermanentlyDenied = BLUETOOTH_PERMISSIONS.any { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        // Note: shouldShowRequestPermissionRationale returns false for permissions never requested
        // as well as for permanently denied ones. We use the heuristic that if any permission is
        // NOT granted and we should NOT show rationale, the app should direct the user to Settings.
        // (First-time requests are handled by the caller requesting permissions immediately.)
        if (anyPermanentlyDenied) return PermissionState.PermanentlyDenied

        return PermissionState.ShowRationale
    }

    /**
     * Launches the system permission request dialog.
     *
     * Results are delivered via [onResult] with the combined permission state.
     */
    fun requestPermissions() {
        launcher.launch(BLUETOOTH_PERMISSIONS.toTypedArray())
    }

    companion object {
        /** Permissions required for Bluetooth scanning and connecting on API 31+. */
        val BLUETOOTH_PERMISSIONS = listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}

/**
 * Remembers a [BluetoothPermissionManager] for the current composition.
 *
 * @param onResult Callback invoked when the system permission dialog completes.
 */
@Composable
fun rememberBluetoothPermissionManager(
    onResult: (PermissionState) -> Unit,
): BluetoothPermissionManager {
    val context = LocalContext.current
    val activity = context as Activity

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allGranted = results.values.all { it }
        val state = when {
            allGranted -> PermissionState.Granted
            results.any { (perm, granted) ->
                !granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            } -> PermissionState.PermanentlyDenied
            else -> PermissionState.ShowRationale
        }
        onResult(state)
    }

    return remember(activity) {
        BluetoothPermissionManager(
            activity = activity,
            launcher = launcher,
            onResult = onResult,
        )
    }
}
