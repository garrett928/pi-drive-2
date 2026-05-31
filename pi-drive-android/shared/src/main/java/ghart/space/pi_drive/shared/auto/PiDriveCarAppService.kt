package ghart.space.pi_drive.shared.auto

import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Car App Library service entry point for Pi Drive on Android Auto.
 *
 * Registered in the mobile AndroidManifest.xml with:
 * - `intent-filter` action: `androidx.car.app.CarAppService`
 * - `intent-filter` category: `androidx.car.app.category.IOT`
 *
 * The Android Auto host binds this service when the user launches Pi Drive from the
 * car head unit. It creates a [PiDriveCarAppSession] to own the screen stack and
 * alert handling for the duration of the session.
 */
class PiDriveCarAppService : CarAppService() {

    companion object {
        private const val TAG = "PiDrive"
    }

    override fun createHostValidator(): HostValidator {
        // ALLOW_ALL_HOSTS_VALIDATOR is acceptable for development and IOT apps.
        Log.d(TAG, "CarAppService: creating host validator")
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Log.d(TAG, "CarAppService: creating PiDriveCarAppSession")
        return PiDriveCarAppSession()
    }
}
