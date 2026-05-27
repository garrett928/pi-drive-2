package ghart.space.pi_drive

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Annotated with [HiltAndroidApp] so Hilt generates
 * the component hierarchy used throughout the app.
 *
 * All Hilt modules are discovered automatically — no manual registration needed.
 */
@HiltAndroidApp
class PiDriveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("PiDrive", "PiDriveApplication.onCreate")
    }
}
