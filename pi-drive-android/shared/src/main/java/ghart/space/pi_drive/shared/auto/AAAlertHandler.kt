package ghart.space.pi_drive.shared.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.EventType
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle

/**
 * Forwards driving events and health alerts to the Android Auto head unit as [CarToast]s.
 *
 * Subscribes to [AADataBridge.alerts] for the lifetime of the [lifecycleOwner] (the
 * [PiDriveCarAppSession]). Each toast is shown only when [DetectionConfig.aaToastEnabled]
 * is true and the AA toast cooldown has not yet elapsed, preventing spam during a sustained
 * hard-braking event.
 *
 * Message format:
 * - Driving event: "Hard brake: 8.2 mph/s" or "Hard accel: 1.1g"
 * - Health alert: forwarded verbatim from [AlertAction.HealthAlert.message]
 *
 * @param carContext    Car App [CarContext] used to create and show toasts.
 * @param lifecycleOwner  The [PiDriveCarAppSession] — toasts are suppressed when the session ends.
 * @param clock         Returns epoch-milliseconds; overridable in tests.
 */
class AAAlertHandler(
    private val carContext: CarContext,
    lifecycleOwner: LifecycleOwner,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    companion object {
        private const val TAG = "PiDrive"
        private const val TOAST_COOLDOWN_MS = 10_000L
    }

    /** Epoch-ms of the last toast shown per alert key. Prevents duplicate toasts. */
    private val lastToastTime = mutableMapOf<String, Long>()

    init {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AADataBridge.alerts.collect { alert ->
                    maybeShowToast(alert)
                }
            }
        }
    }

    private fun maybeShowToast(alert: AlertAction) {
        if (!AADataBridge.detectionConfig.value.aaToastEnabled) {
            Log.d(TAG, "AAAlertHandler: toast suppressed (aaToastEnabled=false)")
            return
        }

        val key = alertKey(alert)
        val now = clock()
        val last = lastToastTime[key]
        if (last != null && now - last < TOAST_COOLDOWN_MS) {
            Log.d(TAG, "AAAlertHandler: toast suppressed (within cooldown): $key")
            return
        }

        val message = buildToastMessage(alert)
        lastToastTime[key] = now
        Log.i(TAG, "CarToast: $message")
        CarToast.makeText(carContext, message, CarToast.LENGTH_SHORT).show()
    }

    private fun alertKey(alert: AlertAction): String = when (alert) {
        is AlertAction.DrivingEventAlert -> alert.event.type.name
        is AlertAction.HealthAlert -> alert.type.name
    }

    private fun buildToastMessage(alert: AlertAction): String = when (alert) {
        is AlertAction.DrivingEventAlert -> {
            val typeLabel = if (alert.event.type == EventType.HARD_BRAKE) "Hard brake" else "Hard accel"
            val detail = alert.event.rateMphS?.let { "%.1f mph/s".format(it) }
                ?: alert.event.peakG?.let { "%.2fg".format(it) }
                ?: ""
            if (detail.isNotEmpty()) "$typeLabel: $detail" else typeLabel
        }
        is AlertAction.HealthAlert -> alert.message
    }
}
