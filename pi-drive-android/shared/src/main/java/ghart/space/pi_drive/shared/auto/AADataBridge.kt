package ghart.space.pi_drive.shared.auto

import ghart.space.pi_drive.shared.data.VehicleDataSource
import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.detection.AlertManager
import ghart.space.pi_drive.shared.detection.DetectionConfig
import ghart.space.pi_drive.shared.settings.ThresholdsManager
import ghart.space.pi_drive.shared.trip.AutoTripManager
import ghart.space.pi_drive.shared.trip.ManualTripManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton data bridge between Hilt-managed singletons and Car App Library screens.
 *
 * The Car App Library creates its own service that is separate from the phone Activity
 * and cannot use Hilt injection directly. This object acts as a shared memory bus:
 * [MainActivity] calls [bind] once after Hilt injection to forward live flows from
 * Hilt-managed singletons into this bridge, and Car App screens read from the public
 * flow properties.
 *
 * All mutable state is backed by thread-safe [MutableStateFlow] or [MutableSharedFlow].
 * Car App screens that collect these flows will always get the most-recent value even
 * after the phone Activity is backgrounded.
 */
object AADataBridge {

    private val _snapshot = MutableStateFlow(VehicleSnapshot.EMPTY)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    private val _manualTripState = MutableStateFlow(
        ManualTripState(
            isActive = false,
            distanceMiles = 0f,
            durationMs = 0L,
            avgSpeedMph = 0f,
            maxSpeedMph = 0f,
            avgMpg = null,
            startDate = null,
        )
    )
    private val _autoTripState = MutableStateFlow<AutoTripState?>(null)
    private val _alerts = MutableSharedFlow<AlertAction>(extraBufferCapacity = 16)
    private val _detectionConfig = MutableStateFlow(DetectionConfig())

    /** Live vehicle telemetry. Initial value is [VehicleSnapshot.EMPTY]; updates after [bind]. */
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    /** OBD adapter connection state. Drives the status indicator in the AA header. */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** User-controlled manual trip: distance, MPG, start date. */
    val manualTripState: StateFlow<ManualTripState> = _manualTripState.asStateFlow()

    /** Auto-detected trip in progress, or null when no trip is active. */
    val autoTripState: StateFlow<AutoTripState?> = _autoTripState.asStateFlow()

    /**
     * Driving events and health alerts from [AlertManager].
     * [AAAlertHandler] collects this to show [androidx.car.app.model.CarToast]s.
     */
    val alerts: SharedFlow<AlertAction> = _alerts.asSharedFlow()

    /** Current detection thresholds — used by [AAAlertHandler] to check [DetectionConfig.aaToastEnabled]. */
    val detectionConfig: StateFlow<DetectionConfig> = _detectionConfig.asStateFlow()

    /**
     * Connects this bridge to the Hilt-managed data layer.
     *
     * Should be called once from [MainActivity.onCreate] after Hilt injection. The [scope]
     * should outlive the [CarAppService] session — the application-scoped [CoroutineScope]
     * provided by [DataModule] is appropriate.
     *
     * Calling [bind] more than once launches additional collection coroutines; avoid calling
     * it from a lifecycle callback that fires on rotation.
     *
     * @param vehicleDataSource  Source of live OBD / demo telemetry.
     * @param manualTripManager  Tracks the user-reset manual trip.
     * @param autoTripManager    Tracks connection-boundary auto trips.
     * @param alertManager       Emits driving events and health alerts.
     * @param thresholdsManager  Provides reactive detection config for AA toast enable flag.
     * @param scope              Coroutine scope that outlives the CarAppService session.
     */
    fun bind(
        vehicleDataSource: VehicleDataSource,
        manualTripManager: ManualTripManager,
        autoTripManager: AutoTripManager,
        alertManager: AlertManager,
        thresholdsManager: ThresholdsManager,
        scope: CoroutineScope,
    ) {
        scope.launch { vehicleDataSource.snapshot.collect { _snapshot.value = it } }
        scope.launch { vehicleDataSource.connectionState.collect { _connectionState.value = it } }
        scope.launch { manualTripManager.state.collect { _manualTripState.value = it } }
        scope.launch { autoTripManager.currentTrip.collect { _autoTripState.value = it } }
        scope.launch { alertManager.alerts.collect { _alerts.emit(it) } }
        scope.launch { thresholdsManager.detectionConfig.collect { _detectionConfig.value = it } }
    }

    /** For tests: directly override the snapshot without going through [bind]. */
    internal fun setSnapshot(s: VehicleSnapshot) { _snapshot.value = s }

    /** For tests: directly override the manual trip state. */
    internal fun setManualTripState(s: ManualTripState) { _manualTripState.value = s }

    /** For tests: directly override the auto trip state. */
    internal fun setAutoTripState(s: AutoTripState?) { _autoTripState.value = s }

    /** For tests: directly override the connection state. */
    internal fun setConnectionState(s: ConnectionState) { _connectionState.value = s }

    /** For tests: directly override the detection config. */
    internal fun setDetectionConfig(c: DetectionConfig) { _detectionConfig.value = c }

    /** For tests: emit an alert directly into the alerts flow. */
    internal suspend fun emitAlert(alert: AlertAction) { _alerts.emit(alert) }
}
