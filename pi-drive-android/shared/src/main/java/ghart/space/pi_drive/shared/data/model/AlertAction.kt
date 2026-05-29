package ghart.space.pi_drive.shared.data.model

/**
 * Sealed hierarchy of actions the alert system can request from the UI layer.
 *
 * Emitted by [ghart.space.pi_drive.shared.detection.AlertManager] and consumed by
 * [LiveDashboardViewModel] to trigger overlays, haptic feedback, and sounds.
 */
sealed class AlertAction {

    /**
     * A hard-acceleration or hard-braking event was confirmed by the detection pipeline
     * and should be displayed as an alert overlay.
     *
     * @param event    The full driving event including type, peak rate/g, sources, and location.
     * @param isSevere True when the event exceeded the severe threshold (e.g., > 0.50g).
     */
    data class DrivingEventAlert(
        val event: DrivingEvent,
        val isSevere: Boolean = false,
    ) : AlertAction()

    /**
     * A vehicle health threshold was exceeded (high coolant, low fuel, etc.).
     *
     * @param type    Which health parameter triggered the alert.
     * @param message Short human-readable description, e.g. "Coolant 115 °C".
     * @param value   The current value that exceeded the threshold.
     */
    data class HealthAlert(
        val type: HealthAlertType,
        val message: String,
        val value: Float,
    ) : AlertAction()
}

/**
 * Categories of vehicle health alerts monitored by
 * [ghart.space.pi_drive.shared.detection.HealthMonitor].
 */
enum class HealthAlertType(val displayName: String) {
    HIGH_COOLANT("High Coolant"),
    LOW_FUEL("Low Fuel"),
    HIGH_RPM("High RPM"),
    OVERSPEED("Overspeed"),
    LOW_BATTERY("Low Battery"),
}
