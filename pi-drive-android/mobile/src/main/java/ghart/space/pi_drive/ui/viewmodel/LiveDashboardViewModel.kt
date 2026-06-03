package ghart.space.pi_drive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.data.VehicleDataSource
import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.detection.AlertManager
import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.obd.ConnectionManager
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.settings.DashboardLayout
import ghart.space.pi_drive.shared.settings.DashboardLayoutManager
import ghart.space.pi_drive.shared.trip.AutoTripManager
import ghart.space.pi_drive.shared.trip.ManualTripManager
import ghart.space.pi_drive.shared.data.model.MetricValue
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.data.model.extractMetricValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** Maximum samples retained for the sparkline rolling buffer (~30 s at 4 Hz). */
private const val SPARKLINE_BUFFER_SIZE = 120

/**
 * ViewModel for the Live Dashboard screen.
 *
 * Bridges [VehicleDataSource] to UI-ready state. The dashboard layout (featured metric
 * and tile list) is driven by [DashboardLayoutManager] so changes made in the layout
 * editor are reflected immediately without a screen restart.
 *
 * - [featuredMetricId]: the hero metric, persisted across app restarts
 * - [featuredValue]: formatted numeric string for the hero card (no unit)
 * - [sparklineData]: 30-second rolling float buffer, resets when featured metric changes
 * - [dashboardLayout]: full layout (tiles + featured metric) for the tile grid
 * - [isLive]: true when the adapter is in [ConnectionState.Connected] state
 */
@HiltViewModel
class LiveDashboardViewModel @Inject constructor(
    private val dataSource: VehicleDataSource,
    private val connectionManager: ConnectionManager,
    private val alertManager: AlertManager,
    private val manualTripManager: ManualTripManager,
    private val autoTripManager: AutoTripManager,
    private val dashboardLayoutManager: DashboardLayoutManager,
) : ViewModel() {

    private val initialMetric: MetricId = dashboardLayoutManager.layout.value.featuredMetricId

    /** Which metric is displayed in the hero card. Changes are persisted to [DashboardLayoutManager]. */
    val featuredMetricId: StateFlow<MetricId> = dashboardLayoutManager.layout
        .map { it.featuredMetricId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialMetric)

    /** Human-readable uppercase label for the featured metric, e.g. "SPEED". Reactive. */
    val featuredLabel: StateFlow<String> = featuredMetricId
        .map { it.displayLabel.uppercase() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialMetric.displayLabel.uppercase())

    /** Unit suffix for the featured metric, e.g. "mph". Reactive. */
    val featuredUnit: StateFlow<String> = featuredMetricId
        .map { it.unit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialMetric.unit)

    /** Formatted numeric-only value for the hero card, e.g. "49" or "2,500". */
    val featuredValue: StateFlow<String> = combine(
        dataSource.snapshot,
        featuredMetricId,
    ) { snap, metricId -> formatHeroValue(metricId, snap.extractMetricValue(metricId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "—")

    /**
     * Rolling 30-second buffer of raw float values for the sparkline.
     *
     * Resets to empty whenever [featuredMetricId] changes so the graph always
     * shows data for the currently selected metric.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sparklineData: StateFlow<List<Float>> = featuredMetricId
        .flatMapLatest { metricId ->
            dataSource.snapshot
                .map { snap -> snap.extractMetricValue(metricId).raw ?: 0f }
                .scan(emptyList<Float>()) { acc, value ->
                    val next = acc + value
                    if (next.size > SPARKLINE_BUFFER_SIZE) next.drop(1) else next
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * True when the adapter is in [ConnectionState.Connected] state.
     *
     * In demo mode, driven by [VehicleDataSource.connectionState] (simulated).
     * In production, driven by [ConnectionManager.connectionState] — becomes true only
     * after the user completes the Connect screen and the transport is handed off.
     */
    val isLive: StateFlow<Boolean> = (
        if (AppConfig.isDemoMode) dataSource.connectionState
        else connectionManager.connectionState
    )
        .map { it is ConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Full connection state — used by [ConnectionBanner] and [StatusBanner] for
     * adapter name, protocol, poll rate, and error messages.
     *
     * In demo mode, comes from [VehicleDataSource] (which simulates disconnect scenarios).
     * In production mode, comes from [ConnectionManager] (manages actual BT reconnects).
     */
    val connectionState: StateFlow<ConnectionState> = (
        if (AppConfig.isDemoMode) dataSource.connectionState
        else connectionManager.connectionState
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Disconnected())

    /**
     * Triggers an immediate reconnect attempt via [ConnectionManager].
     * No-op in demo mode (the demo data source manages its own state).
     */
    fun reconnectNow() {
        if (!AppConfig.isDemoMode) connectionManager.reconnectNow()
    }

    /**
     * Latest raw snapshot — used by the MPG row and tile grid to extract
     * individual metric values without needing separate StateFlows per metric.
     */
    val currentSnapshot: StateFlow<VehicleSnapshot> = dataSource.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleSnapshot.EMPTY)

    /**
     * Current state of the user-controlled manual trip segment.
     *
     * Delegates directly to [ManualTripManager.state]. [isActive] is true once the user
     * has tapped Reset at least once. [avgMpg] populates the manual column of the MPG row.
     */
    val manualTripState: StateFlow<ManualTripState> = manualTripManager.state

    /**
     * Resets the manual trip counter.
     *
     * Closes the current trip in Room and starts a fresh one. The MPG row manual column
     * will immediately show "—" until new fuel data arrives.
     */
    fun resetManualTrip() {
        manualTripManager.reset()
    }

    /**
     * Current state of the automatically-detected trip segment, or null if no trip is active.
     *
     * Null between engine-off and next engine-on, or when the OBD adapter has never connected.
     */
    val autoTripState: StateFlow<AutoTripState?> = autoTripManager.currentTrip

    /**
     * Full persisted dashboard layout. [TileGrid] observes this to rebuild the grid
     * when the user adds, removes, or reorders tiles in the layout editor.
     */
    val dashboardLayout: StateFlow<DashboardLayout> = dashboardLayoutManager.layout
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            dashboardLayoutManager.layout.value,
        )

    private val _currentAlert = MutableStateFlow<AlertAction?>(null)

    /**
     * The currently active alert banner, or null when no alert is showing.
     *
     * Set to non-null when [AlertManager] emits an [AlertAction]; reset to null
     * either by the auto-dismiss timer in [AlertOverlay] or by [dismissAlert].
     */
    val currentAlert: StateFlow<AlertAction?> = _currentAlert.asStateFlow()

    /** Clears the current alert, hiding the overlay banner. */
    fun dismissAlert() {
        _currentAlert.value = null
    }

    init {
        dataSource.startPolling()
        viewModelScope.launch {
            alertManager.alerts.collect { action ->
                _currentAlert.value = action
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataSource.stopPolling()
    }
}

/**
 * Returns the numeric-only portion of [mv] formatted for large hero display.
 * Strips the unit suffix so the composable can render value and unit separately.
 */
private fun formatHeroValue(id: MetricId, mv: MetricValue): String {
    val raw = mv.raw ?: return "—"
    return when (id) {
        MetricId.SPEED -> raw.roundToInt().toString()
        MetricId.RPM -> "%,d".format(raw.roundToInt())
        MetricId.THROTTLE -> raw.roundToInt().toString()
        MetricId.COOLANT,
        MetricId.INTAKE,
        MetricId.OIL_TEMP -> raw.roundToInt().toString()
        MetricId.BATTERY -> "%.1f".format(raw)
        MetricId.FUEL -> raw.roundToInt().toString()
        MetricId.MAF -> "%.1f".format(raw)
        MetricId.MPG_INSTANT,
        MetricId.MPG_TRIP,
        MetricId.MPG_MANUAL -> "%.1f".format(raw)
        MetricId.G_FORCE -> "%.2f".format(raw)
        MetricId.ACCEL -> "%.1f".format(raw)
        MetricId.DISTANCE,
        MetricId.MANUAL_TRIP -> "%.1f".format(raw)
    }
}
