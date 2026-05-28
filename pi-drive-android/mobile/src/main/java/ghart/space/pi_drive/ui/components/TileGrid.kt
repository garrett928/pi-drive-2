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

/** Default tile configurations shown on the Live Dashboard. */
private val DEFAULT_TILES = listOf(
    TileConfig(label = "RPM",     unit = "rpm", widgetType = WidgetType.DIAL,   min = 0f, max = 6_000f, warningThreshold = 5_000f),
    TileConfig(label = "Throttle",unit = "%",   widgetType = WidgetType.BAR,    min = 0f, max = 100f),
    TileConfig(label = "Coolant", unit = "°C",  widgetType = WidgetType.BAR,    min = 60f, max = 120f, warningThreshold = 108f, warningAbove = true),
    TileConfig(label = "Battery", unit = "V",   widgetType = WidgetType.NUMBER),
    TileConfig(label = "Fuel",    unit = "%",   widgetType = WidgetType.BAR,    min = 0f, max = 100f, warningThreshold = 15f),
    TileConfig(label = "G-Force", unit = "g",   widgetType = WidgetType.XY),
)

/** Maps a [TileConfig.label] to the [MetricId] it should read from the snapshot. */
private val LABEL_TO_METRIC = mapOf(
    "RPM"      to MetricId.RPM,
    "Throttle" to MetricId.THROTTLE,
    "Coolant"  to MetricId.COOLANT,
    "Battery"  to MetricId.BATTERY,
    "Fuel"     to MetricId.FUEL,
    "G-Force"  to MetricId.G_FORCE,
)

/**
 * 2-column grid of [MetricTile]s driven by a [VehicleSnapshot].
 *
 * Renders [tiles] in pairs, using a non-lazy [Column] of [Row]s so it can be
 * embedded inside a vertically-scrollable [Column] without nesting issues.
 * Defaults to [DEFAULT_TILES] (RPM, Throttle, Coolant, Battery, Fuel, G-Force).
 *
 * @param snapshot Current vehicle snapshot; tiles read their values from it.
 * @param tiles    Ordered list of tile configs. Defaults to the 6 standard tiles.
 * @param modifier Applied to the outer column.
 */
@Composable
fun TileGrid(
    snapshot: VehicleSnapshot,
    tiles: List<TileConfig> = DEFAULT_TILES,
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
                rowTiles.forEach { config ->
                    val metricId = LABEL_TO_METRIC[config.label]
                    val raw = metricId?.let { snapshot.extractMetricValue(it).raw }
                    MetricTile(
                        config = config,
                        value = raw,
                        modifier = Modifier.weight(1f),
                    )
                }
                // If odd number of tiles, fill the gap
                if (rowTiles.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
