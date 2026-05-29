package ghart.space.pi_drive.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.data.VehicleDataSource
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.obd.ConnectionManager
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.data.model.MetricValue
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.data.model.extractMetricValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

/** Maximum samples retained for the sparkline rolling buffer (~30 s at 4 Hz). */
private const val SPARKLINE_BUFFER_SIZE = 120

/**
 * ViewModel for the Live Dashboard screen.
 *
 * Bridges [VehicleDataSource] to UI-ready state:
 * - [featuredValue]: formatted numeric string for the hero card (no unit)
 * - [sparklineData]: 30-second rolling float buffer for the sparkline
 * - [isLive]: true when the data source reports [ConnectionState.Connected]
 *
 * The featured metric defaults to [MetricId.SPEED] and is persisted across
 * configuration changes via [SavedStateHandle].
 */
@HiltViewModel
class LiveDashboardViewModel @Inject constructor(
    private val dataSource: VehicleDataSource,
    private val connectionManager: ConnectionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Which metric is displayed in the hero card. Survives rotation. */
    val featuredMetricId: MetricId =
        savedStateHandle.get<String>("featured_metric")
            ?.let { runCatching { MetricId.valueOf(it) }.getOrNull() }
            ?: MetricId.SPEED

    /** Human-readable uppercase label for the featured metric, e.g. "SPEED". */
    val featuredLabel: String = featuredMetricId.displayLabel.uppercase()

    /** Unit suffix for the featured metric, e.g. "mph". */
    val featuredUnit: String = featuredMetricId.unit

    /** Formatted numeric-only value for the hero card, e.g. "49" or "2,500". */
    val featuredValue: StateFlow<String> = dataSource.snapshot
        .map { snap -> formatHeroValue(featuredMetricId, snap.extractMetricValue(featuredMetricId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "—")

    /**
     * Rolling 30-second buffer of raw float values for the sparkline.
     * Oldest values are dropped once [SPARKLINE_BUFFER_SIZE] is reached.
     */
    val sparklineData: StateFlow<List<Float>> = dataSource.snapshot
        .map { snap -> snap.extractMetricValue(featuredMetricId).raw ?: 0f }
        .scan(emptyList<Float>()) { acc, value ->
            val next = acc + value
            if (next.size > SPARKLINE_BUFFER_SIZE) next.drop(1) else next
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when the adapter is in [ConnectionState.Connected] state. */
    val isLive: StateFlow<Boolean> = dataSource.connectionState
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

    init {
        dataSource.startPolling()
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
