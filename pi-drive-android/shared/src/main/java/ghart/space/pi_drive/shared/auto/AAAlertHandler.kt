package ghart.space.pi_drive.shared.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.EventType
import kotlinx.coroutines.launch

/**
 * Forwards driving events and health alerts to the Android Auto head unit as [CarToast]s.
 *
 * Subscribes to [AADataBridge.alerts] for the lifetime of [lifecycleOwner] (the
 * [PiDriveCarAppSession]). Each toast is gated by two conditions:
 * 1. [DetectionConfig.aaToastEnabled] must be true.
 * 2. A per-alert-type cooldown of [TOAST_COOLDOWN_MS] must have elapsed since the last
 *    toast of the same type — prevents spam during sustained hard-braking events.
 *
 * Message format:
 * - Driving event: "Hard brake: 8.2 mph/s" or "Hard accel: 1.1g"
 * - Health alert: forwarded verbatim from [AlertAction.HealthAlert.message]
 *
 * @param carContext      Car App [CarContext] for showing [CarToast]s.
 * @param lifecycleOwner  The [PiDriveCarAppSession]; toasts are suppressed after the session ends.
 * @param clock           Returns epoch-milliseconds; injectable for tests.
 */
class AAAlertHandler(
    private val carContext: CarContext,
    lifecycleOwner: LifecycleOwner,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    companion object {
        private const val TAG = "PiDrive"
        internal const val TOAST_COOLDOWN_MS = 10_000L
    }

    /** Epoch-ms of the last toast shown per [alertKeyForAA]. Prevents duplicate toasts. */
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
        val enabled = AADataBridge.detectionConfig.value.aaToastEnabled
        val key = alertKeyForAA(alert)
        val now = clock()

        if (!shouldShowAAToast(enabled, key, lastToastTime, now, TOAST_COOLDOWN_MS)) {
            Log.d(TAG, "AAAlertHandler: toast suppressed for $key (enabled=$enabled)")
            return
        }

        val message = buildAAToastMessage(alert)
        lastToastTime[key] = now
        Log.i(TAG, "CarToast: $message")
        CarToast.makeText(carContext, message, CarToast.LENGTH_SHORT).show()
    }
}

/**
 * Returns a stable string key identifying the alert type.
 *
 * Used by [AAAlertHandler] to track cooldown per alert category (e.g. "HARD_BRAKE",
 * "HIGH_COOLANT"). Package-internal so tests can use it without reflection.
 */
internal fun alertKeyForAA(alert: AlertAction): String = when (alert) {
    is AlertAction.DrivingEventAlert -> alert.event.type.name
    is AlertAction.HealthAlert -> alert.type.name
}

/**
 * Returns true when a new toast should be shown for the given alert.
 *
 * Pure function — testable without [CarContext] or [AADataBridge].
 *
 * @param enabled        Whether AA toasts are globally enabled (from [DetectionConfig.aaToastEnabled]).
 * @param key            Per-type alert key from [alertKeyForAA].
 * @param lastToastTime  Map of key → epoch-ms of the last toast shown for that key.
 * @param now            Current epoch-milliseconds.
 * @param cooldownMs     Minimum gap between toasts of the same type.
 */
internal fun shouldShowAAToast(
    enabled: Boolean,
    key: String,
    lastToastTime: Map<String, Long>,
    now: Long,
    cooldownMs: Long,
): Boolean {
    if (!enabled) return false
    val last = lastToastTime[key]
    return last == null || (now - last) >= cooldownMs
}

/**
 * Converts an [AlertAction] into a human-readable toast message string.
 *
 * Pure function — testable without [CarContext] or coroutines.
 *
 * Examples:
 * - `DrivingEventAlert(HARD_BRAKE, rateMphS=8.2)` → "Hard brake: 8.2 mph/s"
 * - `DrivingEventAlert(HARD_ACCEL, peakG=1.1)` → "Hard accel: 1.10g"
 * - `HealthAlert(HIGH_COOLANT, "Coolant 235°F")` → "Coolant 235°F"
 */
internal fun buildAAToastMessage(alert: AlertAction): String = when (alert) {
    is AlertAction.DrivingEventAlert -> {
        val typeLabel = if (alert.event.type == EventType.HARD_BRAKE) "Hard brake" else "Hard accel"
        val detail = alert.event.rateMphS?.let { "%.1f mph/s".format(it) }
            ?: alert.event.peakG?.let { "%.2fg".format(it) }
            ?: ""
        if (detail.isNotEmpty()) "$typeLabel: $detail" else typeLabel
    }
    is AlertAction.HealthAlert -> alert.message
}
