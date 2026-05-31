package ghart.space.pi_drive.shared.auto

import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.settings.AASlotConfig
import ghart.space.pi_drive.shared.settings.AAWidgetType

/**
 * Formats the value for a configured Android Auto slot.
 *
 * Resolves the raw metric from [snapshot] / trip state and applies the [AASlotConfig.widgetType]
 * format style (plain number, trend arrow, stat with unit). Returns "—" when the metric
 * is unavailable. Unit conversions (km/h → mph, °C → °F) are applied here for display.
 *
 * @param slot       Slot configuration specifying which metric and format style to use.
 * @param snapshot   Most-recent vehicle telemetry snapshot.
 * @param manualTrip Current manual-trip state.
 * @param autoTrip   Current auto-detected trip, or null if none is active.
 * @return           Formatted string for the Car App Library item title field.
 */
internal fun formatSlotValueForAA(
    slot: AASlotConfig,
    snapshot: VehicleSnapshot,
    manualTrip: ManualTripState,
    autoTrip: AutoTripState?,
): String {
    val raw = formatRawMetric(slot.metricId, snapshot, manualTrip, autoTrip)
    if (raw == "—") return raw

    return when (slot.widgetType) {
        AAWidgetType.DIAL  -> raw
        AAWidgetType.STAT  -> raw
        AAWidgetType.TEXT  -> slot.metricId.displayLabel
        AAWidgetType.TREND -> {
            val arrow = trendArrowFor(slot.metricId, snapshot)
            if (arrow.isNotEmpty()) "$raw  $arrow" else raw
        }
    }
}

/**
 * Returns true when [metricId]'s current value crosses a visual danger threshold,
 * indicating that a "⚠ " prefix should be prepended to the display string.
 *
 * Thresholds are intentionally conservative (RPM ≥ 6500, coolant ≥ 240°F) and
 * separate from the configurable [DetectionConfig] alert thresholds.
 */
internal fun isDangerConditionForAA(
    metricId: MetricId,
    snapshot: VehicleSnapshot,
): Boolean = when (metricId) {
    MetricId.RPM     -> (snapshot.rpm ?: 0) >= 6500
    MetricId.COOLANT -> (snapshot.coolantTempC?.let { (it * 9f / 5f + 32f).toInt() } ?: 0) >= 240
    else             -> false
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Converts [metricId] to a raw display string using SI → display unit conversions. */
private fun formatRawMetric(
    metricId: MetricId,
    snapshot: VehicleSnapshot,
    manualTrip: ManualTripState,
    autoTrip: AutoTripState?,
): String = when (metricId) {
    MetricId.SPEED       ->
        snapshot.speedKmh?.let { (it * 0.621371f).toInt().toString() } ?: "—"
    MetricId.RPM         ->
        snapshot.rpm?.toString() ?: "—"
    MetricId.COOLANT     ->
        snapshot.coolantTempC?.let { "${(it * 9f / 5f + 32f).toInt()}°F" } ?: "—"
    MetricId.INTAKE      ->
        snapshot.intakeAirTempC?.let { "${(it * 9f / 5f + 32f).toInt()}°F" } ?: "—"
    MetricId.OIL_TEMP    ->
        snapshot.oilTempC?.let { "${(it * 9f / 5f + 32f).toInt()}°F" } ?: "—"
    MetricId.THROTTLE    ->
        snapshot.throttlePct?.let { "%.0f%%".format(it) } ?: "—"
    MetricId.FUEL        ->
        snapshot.fuelLevelPct?.let { "%.0f%%".format(it) } ?: "—"
    MetricId.BATTERY     ->
        snapshot.batteryVoltage?.let { "%.1f V".format(it) } ?: "—"
    MetricId.MAF         ->
        snapshot.mafGps?.let { "%.1f g/s".format(it) } ?: "—"
    MetricId.G_FORCE     ->
        snapshot.gForce?.let { "%.2fg".format(it) } ?: "—"
    MetricId.ACCEL       ->
        snapshot.accelRateMphS?.let { "%.1f mph/s".format(it) } ?: "—"
    MetricId.MPG_INSTANT ->
        computeInstantMpgString(snapshot)
    MetricId.MPG_TRIP    ->
        (autoTrip?.avgMpg ?: manualTrip.avgMpg)?.let { "%.1f mpg".format(it) } ?: "—"
    MetricId.MPG_MANUAL  ->
        manualTrip.avgMpg?.let { "%.1f mpg".format(it) } ?: "—"
    MetricId.DISTANCE    ->
        (autoTrip?.distanceMiles ?: manualTrip.distanceMiles.takeIf { manualTrip.isActive })
            ?.let { "%.1f mi".format(it) } ?: "—"
    MetricId.MANUAL_TRIP ->
        if (manualTrip.isActive) "%.1f mi".format(manualTrip.distanceMiles) else "—"
}

/** Computes instant MPG from MAF or fuel-rate path. Returns "—" if unavailable. */
private fun computeInstantMpgString(snapshot: VehicleSnapshot): String {
    val speedKmh = snapshot.speedKmh?.toFloat() ?: return "—"
    if (speedKmh <= 0f) return "—"

    val fuelRateLph = snapshot.fuelRateLph
    if (fuelRateLph != null && fuelRateLph > 0f) {
        return "%.1f mpg".format((speedKmh / fuelRateLph) / 0.264172f * 0.621371f)
    }

    val mafGps = snapshot.mafGps
    if (mafGps != null && mafGps > 0f) {
        val fuelRateLphCalc = (mafGps * 3600f / 14.7f) / 737.22f
        if (fuelRateLphCalc > 0f) {
            return "%.1f mpg".format((speedKmh / fuelRateLphCalc) / 0.264172f * 0.621371f)
        }
    }

    return "—"
}

/**
 * Returns a trend arrow suffix for TREND-type slots.
 *
 * Only a few metrics have meaningful trend indicators; for all others an empty string
 * is returned and the [AAWidgetType.TREND] format degrades to plain value display.
 */
private fun trendArrowFor(metricId: MetricId, snapshot: VehicleSnapshot): String = when (metricId) {
    MetricId.THROTTLE -> when {
        (snapshot.throttlePct ?: 0f) >= 70f -> "▲ HIGH"
        (snapshot.throttlePct ?: 0f) >= 40f -> "▶ MED"
        else                                 -> "▼ LOW"
    }
    MetricId.G_FORCE  -> when {
        (snapshot.gForce ?: 0f) >= 0.3f  -> "⚠ HARD"
        (snapshot.gForce ?: 0f) >= 0.15f -> "▶ MOD"
        else                              -> "▼ SMOOTH"
    }
    MetricId.ACCEL    -> when {
        (snapshot.accelRateMphS ?: 0f) >= 8f -> "▲ HARD"
        (snapshot.accelRateMphS ?: 0f) >= 4f -> "▶ MOD"
        else                                  -> "▼ NORMAL"
    }
    else              -> ""
}
