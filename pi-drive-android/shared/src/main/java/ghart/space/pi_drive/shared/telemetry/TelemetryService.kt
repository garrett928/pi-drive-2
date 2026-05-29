package ghart.space.pi_drive.shared.telemetry

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import ghart.space.pi_drive.shared.data.VehicleDataSource
import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TelemetryUploader"

private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "telemetry_upload"

/**
 * Foreground service that streams vehicle telemetry to the configured server.
 *
 * Required for ongoing background data upload on Android 14+ (the OS will kill
 * a background coroutine without a foreground service notification).
 *
 * Lifecycle:
 * - Start: `startForegroundService(Intent(context, TelemetryService::class.java))`
 * - Stop: `stopService(Intent(context, TelemetryService::class.java))`
 *
 * The service starts [TelemetryUploadController.run] in its own [SupervisorJob]-scoped
 * coroutine. When the service is destroyed the scope is cancelled, stopping the upload loop.
 */
@AndroidEntryPoint
class TelemetryService : Service() {

    @Inject lateinit var dataSource: VehicleDataSource
    @Inject lateinit var pendingDao: PendingUploadDao
    @Inject lateinit var configRepository: TelemetryConfigRepository

    private val serviceScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val config = configRepository.load()
        if (config.serverUrl.isBlank()) {
            Log.d(TAG, "TelemetryService started but no server URL configured — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "TelemetryService started — uploading to ${config.serverUrl} at ${config.sampleRateHz} Hz")

        val uploader = TelemetryUploader(
            serverUrl = config.serverUrl,
            apiKey = config.apiKey,
            deviceId = config.deviceId,
        )

        val controller = TelemetryUploadController(
            snapshots = dataSource.snapshot,
            uploader = uploader,
            pendingDao = pendingDao,
        )

        serviceScope.launch {
            controller.run(config)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "TelemetryService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Telemetry Upload",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while Pi Drive is uploading telemetry data"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pi Drive Telemetry")
            .setContentText("Uploading vehicle data")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setSilent(true)
            .build()
}
