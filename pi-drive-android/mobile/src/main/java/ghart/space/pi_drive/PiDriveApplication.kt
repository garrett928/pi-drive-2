package ghart.space.pi_drive

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Annotated with [HiltAndroidApp] so Hilt generates
 * the component hierarchy used throughout the app.
 *
 * All Hilt modules are discovered automatically — no manual registration needed.
 *
 * Note: [AccelerationDetector] is injected in [MainActivity] (not here) so that
 * [AppConfig] is populated from intent extras before the Hilt singleton graph is
 * first accessed — [PiDriveApplication.onCreate] runs before any Activity, so
 * [AppConfig] flags would still be at default values here.
 */
@HiltAndroidApp
class PiDriveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("PiDrive", "PiDriveApplication.onCreate")
    }
}
