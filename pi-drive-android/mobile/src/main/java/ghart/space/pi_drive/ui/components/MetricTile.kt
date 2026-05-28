package ghart.space.pi_drive.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.ui.components.widgets.BarWidget
import ghart.space.pi_drive.ui.components.widgets.DialWidget
import ghart.space.pi_drive.ui.components.widgets.NumberWidget
import ghart.space.pi_drive.ui.components.widgets.XYWidget

/** Widget type for a metric tile. Determines which widget composable is rendered. */
enum class WidgetType { DIAL, BAR, NUMBER, XY }

/**
 * Dashboard metric tile — a [PDCard] wrapping one of the four widget types.
 *
 * The [TileConfig] carries all rendering parameters so callers don't need to
 * pass them separately. [value] is kept outside [TileConfig] because it changes
 * on every snapshot while the config is static for the session.
 *
 * @param config  Widget type, metric label/unit, and scale/threshold params.
 * @param value   Current raw float value from the vehicle snapshot, or null.
 * @param modifier Applied to the [PDCard] container.
 */
@Composable
fun MetricTile(
    config: TileConfig,
    value: Float?,
    modifier: Modifier = Modifier,
) {
    PDCard(
        modifier = modifier,
        contentPadding = 12.dp,
    ) {
        when (config.widgetType) {
            WidgetType.DIAL -> DialWidget(
                value = value ?: 0f,
                min = config.min,
                max = config.max,
                label = config.label,
                unit = config.unit,
                warningThreshold = config.warningThreshold,
            )
            WidgetType.BAR -> BarWidget(
                value = value ?: 0f,
                max = config.max,
                label = config.label,
                unit = config.unit,
                warningThreshold = config.warningThreshold,
                warningAbove = config.warningAbove,
            )
            WidgetType.NUMBER -> NumberWidget(
                value = value,
                label = config.label,
                unit = config.unit,
            )
            WidgetType.XY -> XYWidget(
                lateral = 0f,
                longitudinal = value ?: 0f,
                label = config.label,
            )
        }
    }
}

/**
 * Immutable display configuration for a dashboard metric tile.
 *
 * @param label             Display label shown on the tile (not necessarily uppercased here).
 * @param unit              Unit suffix string.
 * @param widgetType        Which widget variant to render.
 * @param min               Scale minimum (used by DIAL; BAR always starts at 0).
 * @param max               Scale maximum for DIAL and BAR widgets.
 * @param warningThreshold  Optional threshold for danger coloring.
 * @param warningAbove      If true, danger when value >= threshold (coolant, RPM).
 *                          If false (default), danger when value <= threshold (fuel).
 */
data class TileConfig(
    val label: String,
    val unit: String,
    val widgetType: WidgetType,
    val min: Float = 0f,
    val max: Float = 100f,
    val warningThreshold: Float? = null,
    val warningAbove: Boolean = false,
)
