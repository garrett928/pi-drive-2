package ghart.space.pi_drive.shared.settings

import ghart.space.pi_drive.shared.data.model.MetricId
import kotlinx.serialization.Serializable

/**
 * Identifies how a metric value is visually formatted in an Android Auto text item.
 *
 * The Car App Library only renders text; these variants control the string format style
 * used when the slot value is built.
 */
@Serializable
enum class AAWidgetType {
    /** Simple numeric value — formatted as a plain number (e.g. "58" for speed). */
    DIAL,

    /** Trend-annotated value — appends ▲/▶/▼ based on magnitude (e.g. throttle, G-force). */
    TREND,

    /** Stat with unit suffix — e.g. "14.3 mi", "26.4 mpg", "14.2 V". */
    STAT,

    /** Plain display label only — shows the metric name rather than a numeric value. */
    TEXT,
}

/**
 * Persisted configuration for a single slot in an Android Auto screen.
 *
 * @param metricId   Which vehicle metric this slot displays.
 * @param widgetType How the value is formatted in the Car App Library item title.
 * @param label      Optional custom label; null uses [MetricId.displayLabel] in uppercase.
 */
@Serializable
data class AASlotConfig(
    val metricId: MetricId,
    val widgetType: AAWidgetType,
    val label: String? = null,
) {
    /** Resolved label for the Car App Library item text field. */
    val displayLabel: String get() = label?.uppercase() ?: metricId.displayLabel.uppercase()
}

// ── Default slot configurations ───────────────────────────────────────────────

/** Default 6-slot layout for the Dials full-screen grid (2 rows × 3 columns). */
val DEFAULT_DIALS_SLOTS: List<AASlotConfig> = listOf(
    AASlotConfig(MetricId.SPEED,    AAWidgetType.DIAL),
    AASlotConfig(MetricId.RPM,      AAWidgetType.DIAL),
    AASlotConfig(MetricId.COOLANT,  AAWidgetType.DIAL),
    AASlotConfig(MetricId.DISTANCE, AAWidgetType.STAT),
    AASlotConfig(MetricId.MPG_TRIP, AAWidgetType.STAT),
    AASlotConfig(MetricId.BATTERY,  AAWidgetType.STAT),
)

/** Default 4-slot layout for the Graphs screen (ListTemplate rows). */
val DEFAULT_GRAPHS_SLOTS: List<AASlotConfig> = listOf(
    AASlotConfig(MetricId.THROTTLE,    AAWidgetType.TREND),
    AASlotConfig(MetricId.G_FORCE,     AAWidgetType.TREND),
    AASlotConfig(MetricId.MPG_INSTANT, AAWidgetType.TREND),
    AASlotConfig(MetricId.MANUAL_TRIP, AAWidgetType.STAT),
)

/** Default 5-slot layout for the Split Panel hero page (hero + 4 pills). */
val DEFAULT_SPLIT_PAGE1_SLOTS: List<AASlotConfig> = listOf(
    AASlotConfig(MetricId.MPG_INSTANT, AAWidgetType.DIAL),   // hero value
    AASlotConfig(MetricId.MPG_TRIP,    AAWidgetType.STAT),   // pill 1
    AASlotConfig(MetricId.DISTANCE,    AAWidgetType.STAT),   // pill 2
    AASlotConfig(MetricId.MANUAL_TRIP, AAWidgetType.STAT),   // pill 3
    AASlotConfig(MetricId.BATTERY,     AAWidgetType.STAT),   // pill 4
)

/** Default 6-slot layout for the Split Panel tiles page (2 rows × 3 columns). */
val DEFAULT_SPLIT_PAGE2_SLOTS: List<AASlotConfig> = listOf(
    AASlotConfig(MetricId.RPM,      AAWidgetType.STAT),
    AASlotConfig(MetricId.COOLANT,  AAWidgetType.STAT),
    AASlotConfig(MetricId.THROTTLE, AAWidgetType.STAT),
    AASlotConfig(MetricId.FUEL,     AAWidgetType.STAT),
    AASlotConfig(MetricId.BATTERY,  AAWidgetType.STAT),
    AASlotConfig(MetricId.OIL_TEMP, AAWidgetType.STAT),
)

/**
 * Full persisted layout for all three Android Auto screens.
 *
 * Each screen's slots are independently configurable. Defaults match the original
 * Phase 9 hardcoded metric assignments.
 *
 * Serialized as JSON and stored via [AALayoutManager] in SharedPreferences.
 *
 * @param dialsSlots      Six slots for the Dials full-screen grid (2×3).
 * @param graphsSlots     Four slots for the Graphs screen list.
 * @param splitPage1Slots Five slots for the Split Panel hero page (hero + 4 pills).
 * @param splitPage2Slots Six slots for the Split Panel tiles page.
 */
@Serializable
data class AALayoutConfig(
    val dialsSlots: List<AASlotConfig> = DEFAULT_DIALS_SLOTS,
    val graphsSlots: List<AASlotConfig> = DEFAULT_GRAPHS_SLOTS,
    val splitPage1Slots: List<AASlotConfig> = DEFAULT_SPLIT_PAGE1_SLOTS,
    val splitPage2Slots: List<AASlotConfig> = DEFAULT_SPLIT_PAGE2_SLOTS,
)
