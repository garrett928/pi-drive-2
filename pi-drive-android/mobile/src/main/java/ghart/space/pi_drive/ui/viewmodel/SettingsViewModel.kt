package ghart.space.pi_drive.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ghart.space.pi_drive.shared.data.VehicleDataSource
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.settings.DashboardLayout
import ghart.space.pi_drive.shared.settings.DashboardLayoutManager
import ghart.space.pi_drive.shared.settings.DashboardTileConfig
import ghart.space.pi_drive.shared.settings.GeneralSettings
import ghart.space.pi_drive.shared.settings.GeneralSettingsManager
import ghart.space.pi_drive.shared.telemetry.TelemetryConfig
import ghart.space.pi_drive.shared.telemetry.TelemetryConfigRepository
import ghart.space.pi_drive.shared.trip.ManualTripManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for the Settings root screen and its sub-screens (layout editor, thresholds).
 *
 * Aggregates state from multiple sources so the composable has a single, stable
 * dependency to observe. All mutations are routed back through the appropriate
 * repository or manager to ensure persistence.
 *
 * @param dataSource              The live vehicle data source — provides [connectionState].
 * @param generalSettingsManager  Persists user appearance and behaviour preferences.
 * @param dashboardLayoutManager  Persists the phone dashboard tile layout and featured metric.
 * @param telemetryConfigRepository Persists server / signal configuration.
 * @param manualTripManager       Provides and resets the manual trip accumulator.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    dataSource: VehicleDataSource,
    private val generalSettingsManager: GeneralSettingsManager,
    private val dashboardLayoutManager: DashboardLayoutManager,
    private val telemetryConfigRepository: TelemetryConfigRepository,
    private val manualTripManager: ManualTripManager,
) : ViewModel() {

    /** Current OBD adapter connection state — drives the vehicle card. */
    val connectionState: StateFlow<ConnectionState> = dataSource.connectionState

    /** Live manual trip state — drives the inline trip counter row. */
    val manualTripState: StateFlow<ManualTripState> = manualTripManager.state

    /** User-controlled appearance and behaviour preferences. */
    val generalSettings: StateFlow<GeneralSettings> = generalSettingsManager.settings

    /** Current phone dashboard layout — drives the layout editor and live dashboard tile grid. */
    val dashboardLayout: StateFlow<DashboardLayout> = dashboardLayoutManager.layout

    private val _telemetryConfig = MutableStateFlow(telemetryConfigRepository.load())

    /** Current telemetry configuration — used for the stream subtitle and Wi-Fi policy toggle. */
    val telemetryConfig: StateFlow<TelemetryConfig> = _telemetryConfig.asStateFlow()

    /** Persists [settings] and emits the update on [generalSettings]. */
    fun updateGeneralSettings(settings: GeneralSettings) =
        generalSettingsManager.update(settings)

    /**
     * Toggles the Wi-Fi-only upload policy.
     *
     * The change is written to [TelemetryConfigRepository] and emitted on [telemetryConfig].
     */
    fun setWifiOnly(enabled: Boolean) {
        val updated = _telemetryConfig.value.copy(uploadOnWifiOnly = enabled)
        telemetryConfigRepository.save(updated)
        _telemetryConfig.value = updated
    }

    /** Resets the manual trip accumulator to zero and starts a new Room record. */
    fun resetManualTrip() = manualTripManager.reset()

    /**
     * Clears all general settings preferences and resets them to [GeneralSettings] defaults.
     *
     * Called when the user confirms "Reset all settings" from the danger row.
     */
    fun resetAllSettings() = generalSettingsManager.reset()

    /** Persists [metricId] as the new featured metric on the phone dashboard. */
    fun setFeaturedMetric(metricId: MetricId) = dashboardLayoutManager.updateFeaturedMetric(metricId)

    /** Replaces the dashboard tile list with [tiles]. */
    fun updateDashboardTiles(tiles: List<DashboardTileConfig>) = dashboardLayoutManager.updateTiles(tiles)

    /** Adds [tile] at the end of the dashboard tile list. */
    fun addDashboardTile(tile: DashboardTileConfig) {
        dashboardLayoutManager.updateTiles(dashboardLayoutManager.layout.value.tiles + tile)
    }

    /** Removes the tile at [index] from the dashboard tile list. */
    fun removeDashboardTile(index: Int) {
        val tiles = dashboardLayoutManager.layout.value.tiles.toMutableList()
        if (index in tiles.indices) {
            tiles.removeAt(index)
            dashboardLayoutManager.updateTiles(tiles)
        }
    }

    /** Moves the tile at [fromIndex] to [toIndex] (for up/down reordering). */
    fun moveDashboardTile(fromIndex: Int, toIndex: Int) {
        val tiles = dashboardLayoutManager.layout.value.tiles.toMutableList()
        if (fromIndex in tiles.indices && toIndex in tiles.indices) {
            val tile = tiles.removeAt(fromIndex)
            tiles.add(toIndex, tile)
            dashboardLayoutManager.updateTiles(tiles)
        }
    }
}
