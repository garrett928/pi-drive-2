package ghart.space.pi_drive.shared.settings

import ghart.space.pi_drive.shared.data.model.MetricId
import kotlinx.serialization.Serializable

/**
 * Identifies the widget type used to render a dashboard metric tile.
 *
 * Defined here in the shared module so [DashboardTileConfig] (persisted layout) and
 * the mobile MetricTile composable both reference the same type.
 */
@Serializable
enum class WidgetType { DIAL, BAR, NUMBER, XY }

/**
 * Persisted configuration for a single tile in the dashboard grid.
 *
 * Only [metricId] and [widgetType] are stored here. Rendering parameters (scale,
 * thresholds, labels) are derived from per-[MetricId] defaults at render time.
 *
 * @param metricId   Which metric this tile displays.
 * @param widgetType The visual widget variant to use.
 */
@Serializable
data class DashboardTileConfig(
    val metricId: MetricId,
    val widgetType: WidgetType,
)

/** Default tile set used when no layout has been saved yet. */
val DEFAULT_DASHBOARD_TILES: List<DashboardTileConfig> = listOf(
    DashboardTileConfig(MetricId.RPM, WidgetType.DIAL),
    DashboardTileConfig(MetricId.THROTTLE, WidgetType.BAR),
    DashboardTileConfig(MetricId.COOLANT, WidgetType.BAR),
    DashboardTileConfig(MetricId.BATTERY, WidgetType.NUMBER),
    DashboardTileConfig(MetricId.FUEL, WidgetType.BAR),
    DashboardTileConfig(MetricId.G_FORCE, WidgetType.XY),
)

/**
 * Full persisted layout for the phone dashboard.
 *
 * [featuredMetricId] drives the hero card at the top. [tiles] drives the 2-column
 * metric grid below it. Serialized as JSON and stored via [DashboardLayoutManager].
 *
 * @param featuredMetricId Metric shown in the large hero card. Default: [MetricId.SPEED].
 * @param tiles            Ordered tile list for the grid. Default: [DEFAULT_DASHBOARD_TILES].
 */
@Serializable
data class DashboardLayout(
    val featuredMetricId: MetricId = MetricId.SPEED,
    val tiles: List<DashboardTileConfig> = DEFAULT_DASHBOARD_TILES,
)
