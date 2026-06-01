package ghart.space.pi_drive.ui.permissions

/**
 * Represents the current state of a runtime permission request.
 *
 * Used by [BluetoothPermissionManager] to communicate what UI the caller should
 * show to the user, and by [PermissionGate] to decide whether to show content,
 * a rationale explanation, or a "go to settings" prompt.
 */
sealed class PermissionState {
    /** All required permissions have been granted. Show the protected content. */
    object Granted : PermissionState()

    /**
     * The user denied at least one permission but did not select "Don't ask again".
     * Show a rationale explaining why the permission is needed, then re-request.
     */
    object ShowRationale : PermissionState()

    /**
     * The user denied and selected "Don't ask again" (or the permission is restricted by policy).
     * The system dialog will no longer appear; the user must grant permission via system Settings.
     */
    object PermanentlyDenied : PermissionState()

    /** Permission status has not been checked yet. Show a loading indicator or request immediately. */
    object NotRequested : PermissionState()
}
