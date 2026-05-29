package ghart.space.pi_drive.shared.detection

import android.util.Log
import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.HealthAlertType
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * Monitors live vehicle snapshots and emits [AlertAction.HealthAlert] when configurable
 * health thresholds are exceeded.
 *
 * ## Supported alerts
 * | Alert          | PID   | Default threshold | Default enabled |
 * |----------------|-------|-------------------|-----------------|
 * | High coolant   | 0x05  | 110 °C            | yes             |
 * | Low fuel       | 0x2F  | 10 %              | yes             |
 * | High RPM       | 0x0C  | 6 500 rpm         | yes             |
 * | Overspeed      | 0x0D  | 75 mph            | no              |
 * | Low battery    | 0x42  | 11.5 V            | no              |
 *
 * ## Cooldown
 * Each alert type has an independent cooldown window. An alert that fires will not
 * re-fire for the same type until [cooldownMs] has elapsed.
 *
 * ## PID auto-disable
 * If the vehicle's supported-PID set does not include the PID for a metric, that
 * alert is silently skipped.
 *
 * @param snapshots     Live vehicle data stream.
 * @param supportedPids Current vehicle's supported PIDs (from OBD initialization).
 * @param config        Threshold and enable settings.
 * @param clock         Returns the current epoch milliseconds. Overridable for testing.
 */
class HealthMonitor(
    private val snapshots: StateFlow<VehicleSnapshot>,
    private val supportedPids: StateFlow<Set<Int>>,
    private val config: HealthMonitorConfig = HealthMonitorConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    companion object {
        private const val TAG = "PiDrive"

        // OBD PID codes for each monitored metric
        private const val PID_COOLANT  = 0x05
        private const val PID_FUEL     = 0x2F
        private const val PID_RPM      = 0x0C
        private const val PID_SPEED    = 0x0D
        private const val PID_BATTERY  = 0x42
    }

    /**
     * Returns a cold [Flow] that emits a [AlertAction.HealthAlert] each time a health
     * threshold is crossed and the per-type cooldown has expired.
     *
     * The flow runs until its collection scope is cancelled.
     */
    fun alerts(): Flow<AlertAction.HealthAlert> = flow {
        val lastFire = mutableMapOf<HealthAlertType, Long>()

        snapshots.collect { snap ->
            val pids = supportedPids.value
            val now = clock()

            fun shouldFire(type: HealthAlertType, cooldown: Long): Boolean {
                val last = lastFire[type] ?: return true  // never fired → always allow
                return now - last >= cooldown
            }

            fun tryEmit(type: HealthAlertType, cooldown: Long, value: Float, msg: String) {
                if (shouldFire(type, cooldown)) {
                    lastFire[type] = now
                    Log.i(TAG, "Health alert: $msg")
                }
            }

            // High coolant
            if (config.highCoolantEnabled && pids.contains(PID_COOLANT)) {
                val temp = snap.coolantTempC?.toFloat()
                if (temp != null && temp >= config.highCoolantThresholdC) {
                    if (shouldFire(HealthAlertType.HIGH_COOLANT, config.highCoolantCooldownMs)) {
                        lastFire[HealthAlertType.HIGH_COOLANT] = now
                        val msg = "Coolant %.0f °C".format(temp)
                        Log.i(TAG, "Health alert: $msg")
                        emit(AlertAction.HealthAlert(HealthAlertType.HIGH_COOLANT, msg, temp))
                    }
                }
            }

            // Low fuel
            if (config.lowFuelEnabled && pids.contains(PID_FUEL)) {
                val fuel = snap.fuelLevelPct
                if (fuel != null && fuel <= config.lowFuelThresholdPct) {
                    if (shouldFire(HealthAlertType.LOW_FUEL, config.lowFuelCooldownMs)) {
                        lastFire[HealthAlertType.LOW_FUEL] = now
                        val msg = "Fuel %.0f%%".format(fuel)
                        Log.i(TAG, "Health alert: $msg")
                        emit(AlertAction.HealthAlert(HealthAlertType.LOW_FUEL, msg, fuel))
                    }
                }
            }

            // High RPM
            if (config.highRpmEnabled && pids.contains(PID_RPM)) {
                val rpm = snap.rpm?.toFloat()
                if (rpm != null && rpm >= config.highRpmThreshold) {
                    if (shouldFire(HealthAlertType.HIGH_RPM, config.highRpmCooldownMs)) {
                        lastFire[HealthAlertType.HIGH_RPM] = now
                        val msg = "RPM %.0f".format(rpm)
                        Log.i(TAG, "Health alert: $msg")
                        emit(AlertAction.HealthAlert(HealthAlertType.HIGH_RPM, msg, rpm))
                    }
                }
            }

            // Overspeed (disabled by default)
            if (config.overspeedEnabled && pids.contains(PID_SPEED)) {
                val speedMph = snap.speedKmh?.times(0.621371f)
                if (speedMph != null && speedMph >= config.overspeedThresholdMph) {
                    if (shouldFire(HealthAlertType.OVERSPEED, config.overspeedCooldownMs)) {
                        lastFire[HealthAlertType.OVERSPEED] = now
                        val msg = "Speed %.0f mph".format(speedMph)
                        Log.i(TAG, "Health alert: $msg")
                        emit(AlertAction.HealthAlert(HealthAlertType.OVERSPEED, msg, speedMph))
                    }
                }
            }

            // Low battery (disabled by default)
            if (config.lowBatteryEnabled && pids.contains(PID_BATTERY)) {
                val volts = snap.batteryVoltage
                if (volts != null && volts <= config.lowBatteryThresholdV) {
                    if (shouldFire(HealthAlertType.LOW_BATTERY, config.lowBatteryCooldownMs)) {
                        lastFire[HealthAlertType.LOW_BATTERY] = now
                        val msg = "Battery %.1fV".format(volts)
                        Log.i(TAG, "Health alert: $msg")
                        emit(AlertAction.HealthAlert(HealthAlertType.LOW_BATTERY, msg, volts))
                    }
                }
            }
        }
    }
}

/**
 * Configuration for [HealthMonitor] alert thresholds and enable flags.
 *
 * Defaults reflect common safe thresholds. Overspeed and low-battery alerts are disabled
 * by default as they depend on local preferences and vehicle type.
 */
data class HealthMonitorConfig(
    val highCoolantEnabled: Boolean = true,
    val highCoolantThresholdC: Float = 110f,
    val highCoolantCooldownMs: Long = 60_000L,

    val lowFuelEnabled: Boolean = true,
    val lowFuelThresholdPct: Float = 10f,
    val lowFuelCooldownMs: Long = 60_000L,

    val highRpmEnabled: Boolean = true,
    val highRpmThreshold: Float = 6500f,
    val highRpmCooldownMs: Long = 30_000L,

    val overspeedEnabled: Boolean = false,
    val overspeedThresholdMph: Float = 75f,
    val overspeedCooldownMs: Long = 30_000L,

    val lowBatteryEnabled: Boolean = false,
    val lowBatteryThresholdV: Float = 11.5f,
    val lowBatteryCooldownMs: Long = 300_000L,
)
