package ghart.space.pi_drive.shared.telemetry

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val TAG = "TelemetryUploader"

/** Unique name for the recurring 15-minute upload job. */
private const val PERIODIC_WORK_NAME = "pi_drive_telemetry_upload_periodic"

/** Unique name for the one-shot "catch up after going online" job. */
private const val ONE_SHOT_WORK_NAME = "pi_drive_telemetry_upload_oneshot"

/**
 * Schedules [UploadWorker] via [WorkManager] to drain the [OfflineBuffer].
 *
 * Two scheduling modes:
 * - **Periodic** ([schedulePeriodicUpload]): runs every 15 minutes (WorkManager minimum) to
 *   sweep the queue on a regular cadence. Persists across device reboots.
 * - **One-shot** ([triggerNow]): enqueued immediately when a network connection is restored
 *   after an offline period, so queued payloads are sent without waiting for the next
 *   periodic window.
 *
 * Both modes enforce a `CONNECTED` network constraint (upgraded to `UNMETERED` when
 * [TelemetryConfig.uploadOnWifiOnly] is true). WorkManager's built-in exponential back-off
 * (starting at 30 s) handles transient server errors independently of [OfflineBuffer]'s
 * own per-item retry logic.
 */
class UploadScheduler(private val context: Context) {

    /**
     * Enqueues (or keeps the existing) periodic background upload job.
     *
     * Uses [ExistingPeriodicWorkPolicy.KEEP] so that calling this multiple times on app
     * start doesn't reset the next-run timer — the existing schedule is preserved until
     * [cancelAll] is called or the constraints change.
     *
     * @param uploadOnWifiOnly When true, requires an unmetered (Wi-Fi) connection.
     */
    fun schedulePeriodicUpload(uploadOnWifiOnly: Boolean = false) {
        val constraints = buildConstraints(uploadOnWifiOnly)

        val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )

        Log.d(TAG, "UploadScheduler: periodic upload scheduled (wifiOnly=$uploadOnWifiOnly)")
    }

    /**
     * Immediately enqueues a one-time upload run.
     *
     * Call this when the device comes back online after an offline period so queued
     * payloads are sent without waiting for the next 15-minute window.
     *
     * Uses [ExistingWorkPolicy.REPLACE] so that rapid reconnect/disconnect cycles
     * don't accumulate multiple pending one-shot jobs.
     */
    fun triggerNow() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(buildConstraints(uploadOnWifiOnly = false))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )

        Log.d(TAG, "UploadScheduler: one-shot upload triggered")
    }

    /** Cancels all scheduled upload work. Call when the user disables server telemetry. */
    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC_WORK_NAME)
        wm.cancelUniqueWork(ONE_SHOT_WORK_NAME)
        Log.d(TAG, "UploadScheduler: all scheduled work cancelled")
    }

    private fun buildConstraints(uploadOnWifiOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                if (uploadOnWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
}
