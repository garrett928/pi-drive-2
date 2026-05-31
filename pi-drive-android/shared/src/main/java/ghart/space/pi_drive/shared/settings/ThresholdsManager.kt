package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import ghart.space.pi_drive.shared.detection.DetectionConfig
import ghart.space.pi_drive.shared.detection.HealthMonitorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Loads and persists [DetectionConfig] and [HealthMonitorConfig] to [SharedPreferences] as JSON.
 *
 * Exposes hot [StateFlow]s so detectors ([ghart.space.pi_drive.shared.detection.AccelerationDetector],
 * [ghart.space.pi_drive.shared.detection.GForceDetector],
 * [ghart.space.pi_drive.shared.detection.HealthMonitor]) and the settings UI can both react to
 * threshold changes in real time without polling or restarting the detectors.
 *
 * Designed to accept [SharedPreferences] directly so unit tests can inject a fake
 * implementation without Robolectric.
 *
 * @param prefs The [SharedPreferences] instance to read from and write to.
 */
class ThresholdsManager(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _detectionConfig = MutableStateFlow(loadDetectionConfig())
    private val _healthMonitorConfig = MutableStateFlow(loadHealthMonitorConfig())

    /** Current detection/event thresholds. Emits after every [updateDetectionConfig] or [reset]. */
    val detectionConfig: StateFlow<DetectionConfig> = _detectionConfig.asStateFlow()

    /** Current health alert thresholds. Emits after every [updateHealthMonitorConfig] or [reset]. */
    val healthMonitorConfig: StateFlow<HealthMonitorConfig> = _healthMonitorConfig.asStateFlow()

    /**
     * Persists [config] to SharedPreferences and emits the updated value on [detectionConfig].
     *
     * Because [AccelerationDetector] and [GForceDetector] read `configFlow.value` on every
     * snapshot tick, the change takes effect immediately without restarting the detectors.
     */
    fun updateDetectionConfig(config: DetectionConfig) {
        prefs.edit { putString(KEY_DETECTION, json.encodeToString(config)) }
        _detectionConfig.value = config
    }

    /**
     * Persists [config] to SharedPreferences and emits the updated value on [healthMonitorConfig].
     *
     * [HealthMonitor] reads `configFlow.value` on every snapshot tick, so the change takes
     * effect immediately without restarting the monitor.
     */
    fun updateHealthMonitorConfig(config: HealthMonitorConfig) {
        prefs.edit { putString(KEY_HEALTH, json.encodeToString(config)) }
        _healthMonitorConfig.value = config
    }

    /**
     * Clears all stored thresholds and resets both flows to their class-default values.
     *
     * Called when the user selects "Reset all settings" from the settings root screen.
     */
    fun reset() {
        prefs.edit {
            remove(KEY_DETECTION)
            remove(KEY_HEALTH)
        }
        _detectionConfig.value = DetectionConfig()
        _healthMonitorConfig.value = HealthMonitorConfig()
    }

    private fun loadDetectionConfig(): DetectionConfig {
        val stored = prefs.getString(KEY_DETECTION, null) ?: return DetectionConfig()
        return try {
            json.decodeFromString<DetectionConfig>(stored)
        } catch (_: Exception) {
            DetectionConfig()
        }
    }

    private fun loadHealthMonitorConfig(): HealthMonitorConfig {
        val stored = prefs.getString(KEY_HEALTH, null) ?: return HealthMonitorConfig()
        return try {
            json.decodeFromString<HealthMonitorConfig>(stored)
        } catch (_: Exception) {
            HealthMonitorConfig()
        }
    }

    companion object {
        /** SharedPreferences file name — used by the Hilt module to open the correct store. */
        const val PREFS_NAME = "pi_drive_thresholds"
        private const val KEY_DETECTION = "detection_config"
        private const val KEY_HEALTH = "health_monitor_config"
    }
}
