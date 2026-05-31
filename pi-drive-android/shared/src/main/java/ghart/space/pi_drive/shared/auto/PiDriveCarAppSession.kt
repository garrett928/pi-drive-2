package ghart.space.pi_drive.shared.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Car App Library session for Pi Drive.
 *
 * Created by [PiDriveCarAppService] when the Android Auto host binds the service.
 * Responsibilities:
 * 1. Returns [DialsScreen] as the root screen (the initial view on the head unit).
 * 2. Initializes [AAAlertHandler] so driving events trigger [androidx.car.app.model.CarToast]s.
 *
 * [Session] implements [LifecycleOwner], so coroutines in [AAAlertHandler] are automatically
 * cancelled when the session ends (user exits Pi Drive or disconnects the phone).
 */
class PiDriveCarAppSession : Session() {

    companion object {
        private const val TAG = "PiDrive"
    }

    private var alertHandler: AAAlertHandler? = null

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "CarAppSession: onCreateScreen — launching DialsScreen via AAScreenManager")
        alertHandler = AAAlertHandler(carContext = carContext, lifecycleOwner = this)
        return AAScreenManager(carContext).createRootScreen()
    }
}
