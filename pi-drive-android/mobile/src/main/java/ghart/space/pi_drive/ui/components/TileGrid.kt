package ghart.space.pi_drive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.data.model.extractMetricValue
import ghart.space.pi_drive.shared.settings.DashboardTileConfig
import ghart.space.pi_drive.shared.settings.DEFAULT_DASHBOARD_TILES
import ghart.space.pi_drive.shared.settings.WidgetType

/**
 * Maps a [MetricId] + [WidgetType] to a rendering [TileConfig] with appropriate scale,
 * label, unit, and warning thresholds for each metric.
 *
 * This is the single source of truth for per-metric rendering parameters so the tile
 * grid and layout editor both produce consistent results.
 */
internal fun DashboardTileConfig.toRenderConfig(): TileConfig = when (metricId) {
    MetricId.RPM -> TileConfig(
        label = "RPM", unit = "rpm", widgetType = widgetType,
        min = 0f, max = 6_000f, warningThreshold = 5_000f, warningAbove = true,
    )
    MetricId.THROTTLE -> TileConfig(
        label = "Throttle", unit = "%", widgetType = widgetType,
        min = 0f, max = 100f,
    )
    MetricId.COOLANT -> TileConfig(
        label = "Coolant", unit = "°C", widgetType = widgetType,
        min = 60f, max = 120f, warningThreshold = 108f, warningAbove = true,
    )
    MetricId.BATTERY -> TileConfig(
        label = "Battery", unit = "V", widgetType = widgetType,
        min = 10f, max = 16f, warningThreshold = 11.5f,
    )
    MetricId.FUEL -> TileConfig(
        label = "Fuel", unit = "%", widgetType = widgetType,
        min = 0f, max = 100f, warningThreshold = 15f,
    )
    MetricId.G_FORCE -> TileConfig(
        label = "G-Force", unit = "g", widgetType = widgetType,
        min = -1f, max = 1f,
    )
    MetricId.SPEED -> TileConfig(
        label = "Speed", unit = "mph", widgetType = widgetType,
        min = 0f, max = 120f,
    )
    MetricId.INTAKE -> TileConfig(
        label = "Intake Air", unit = "°C", widgetType = widgetType,
        min = -20f, max = 60f,
    )
    MetricId.OIL_TEMP -> TileConfig(
        label = "Oil Temp", unit = "°C", widgetType = widgetType,
        min = 60f, max = 140f, warningThreshold = 130f, warningAbove = true,
    )
    MetricId.MAF -> TileConfig(
        label = "MAF", unit = "g/s", widgetType = widgetType,
        min = 0f, max = 30f,
    )
    MetricId.ACCEL -> TileConfig(
        label = "Accel Rate", unit = "mph/s", widgetType = widgetType,
        min = -15f, max = 15f,
    )
    MetricId.DISTANCE -> TileConfig(
        label = "Distance", unit = "mi", widgetType = widgetType,
        min = 0f, max = 100f,
    )
    MetricId.MANUAL_TRIP -> TileConfig(
        label = "Trip Dist", unit = "mi", widgetType = widgetType,
        min = 0f, max = 50f,
    )
    MetricId.MPG_INSTANT -> TileConfig(
        label = "MPG", unit = "mpg", widgetType = widgetType,
        min = 0f, max = 60f,
    )
    MetricId.MPG_TRIP -> TileConfig(
        label = "Trip MPG", unit = "mpg", widgetType = widgetType,
        min = 0f, max = 60f,
    )
    MetricId.MPG_MANUAL -> TileConfig(
        label = "Avg MPG", unit = "mpg", widgetType = widgetType,
        min = 0f, max = 60f,
    )
}

/**
 * 2-column grid of [MetricTile]s driven by a [VehicleSnapshot].
 *
 * Renders [tiles] in pairs, using a non-lazy [Column] of [Row]s so it can be
 * embedded inside a vertically-scrollable [Column] without nesting issues.
 * Defaults to [DEFAULT_DASHBOARD_TILES] if no layout tiles are provided.
 *
 * @param snapshot Current vehicle snapshot; tiles read their values from it.
 * @param tiles    Ordered list of persisted tile configs from [DashboardLayout].
 * @param modifier Applied to the outer column.
 */
@Composable
fun TileGrid(
    snapshot: VehicleSnapshot,
    tiles: List<DashboardTileConfig> = DEFAULT_DASHBOARD_TILES,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowTiles.forEach { dashConfig ->
                    val raw = snapshot.extractMetricValue(dashConfig.metricId).raw
                    MetricTile(
                        config = dashConfig.toRenderConfig(),
                        value = raw,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill the last row gap if the tile count is odd
                if (rowTiles.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
