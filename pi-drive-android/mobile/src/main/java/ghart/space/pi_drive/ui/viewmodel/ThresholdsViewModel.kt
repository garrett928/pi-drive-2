package ghart.space.pi_drive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.detection.AccelerometerManager
import ghart.space.pi_drive.shared.detection.DetectionConfig
import ghart.space.pi_drive.shared.detection.HealthMonitorConfig
import ghart.space.pi_drive.shared.settings.ThresholdsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * ViewModel for [ghart.space.pi_drive.ui.screens.settings.SettingsThresholdsScreen].
 *
 * Exposes reactive [DetectionConfig] and [HealthMonitorConfig] flows from [ThresholdsManager]
 * and routes all user changes back for persistence. Also loads the weekly hard-brake event
 * count for the contextual badge on the Hard Brake slider.
 *
 * @param thresholdsManager Persists detection and health alert thresholds.
 * @param eventDao          Queries recent driving events for contextual badges.
 * @param accelManager      Provides accelerometer calibration state.
 */
@HiltViewModel
class ThresholdsViewModel @Inject constructor(
    private val thresholdsManager: ThresholdsManager,
    private val eventDao: DrivingEventDao,
    private val accelManager: AccelerometerManager,
) : ViewModel() {

    /** Current detection/event thresholds — reflects changes immediately. */
    val detectionConfig: StateFlow<DetectionConfig> = thresholdsManager.detectionConfig

    /** Current health alert thresholds — reflects changes immediately. */
    val healthMonitorConfig: StateFlow<HealthMonitorConfig> = thresholdsManager.healthMonitorConfig

    private val _hardBrakeCountWeek = MutableStateFlow(0)
    /** Count of HARD_BRAKE events in the last 7 days — used as a contextual badge. */
    val hardBrakeCountWeek: StateFlow<Int> = _hardBrakeCountWeek.asStateFlow()

    private val _hardAccelCountWeek = MutableStateFlow(0)
    /** Count of HARD_ACCEL events in the last 7 days — used as a contextual badge. */
    val hardAccelCountWeek: StateFlow<Int> = _hardAccelCountWeek.asStateFlow()

    /** True when the accelerometer has been calibrated for longitudinal g-force. */
    val isAccelerometerCalibrated: Boolean = accelManager.isCalibrated

    init {
        viewModelScope.launch {
            val now = Instant.now()
            val weekAgo = now.minus(7, ChronoUnit.DAYS)
            _hardBrakeCountWeek.value =
                eventDao.countByTypeAndTimeRange(EventType.HARD_BRAKE, weekAgo, now)
            _hardAccelCountWeek.value =
                eventDao.countByTypeAndTimeRange(EventType.HARD_ACCEL, weekAgo, now)
        }
    }

    /** Persists the updated [DetectionConfig] and emits the change to all collectors. */
    fun updateDetectionConfig(config: DetectionConfig) = thresholdsManager.updateDetectionConfig(config)

    /** Persists the updated [HealthMonitorConfig] and emits the change to all collectors. */
    fun updateHealthMonitorConfig(config: HealthMonitorConfig) =
        thresholdsManager.updateHealthMonitorConfig(config)
}
