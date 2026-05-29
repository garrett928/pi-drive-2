package ghart.space.pi_drive

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import ghart.space.pi_drive.shared.telemetry.UploadWorker
import javax.inject.Inject

/**
 * Application entry point. Annotated with [HiltAndroidApp] so Hilt generates the component
 * hierarchy used throughout the app.
 *
 * Implements [Configuration.Provider] to register a custom [DelegatingWorkerFactory] that
 * lets WorkManager construct [UploadWorker] with its Hilt-provided dependencies
 * ([OfflineBuffer] and [TelemetryConfigRepository]). This replaces WorkManager's default
 * reflection-based factory, which cannot supply constructor arguments.
 *
 * Note: [AccelerationDetector] is injected in [MainActivity] (not here) so that [AppConfig]
 * is populated from intent extras before the Hilt singleton graph is first accessed.
 */
@HiltAndroidApp
class PiDriveApplication : Application(), Configuration.Provider {

    @Inject lateinit var uploadWorkerFactory: UploadWorker.Factory

    override val workManagerConfiguration: Configuration
        get() {
            val factory = DelegatingWorkerFactory()
            factory.addFactory(uploadWorkerFactory)
            return Configuration.Builder()
                .setWorkerFactory(factory)
                .build()
        }

    override fun onCreate() {
        super.onCreate()
        Log.d("PiDrive", "PiDriveApplication.onCreate")
    }
}
