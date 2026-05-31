package ghart.space.pi_drive.shared.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.settings.AALayoutConfig
import ghart.space.pi_drive.shared.settings.AAWidgetType
import kotlinx.coroutines.launch

/**
 * Android Auto screen showing live graph data: throttle, G-force, and MPG stats.
 *
 * Since the Car App Library's IOT category does not support custom canvas rendering,
 * graphs are represented as a [ListTemplate] with current value, trend indicator,
 * and a stat section for instant MPG and manual-trip MPG.
 *
 * Screen stack:
 * - [DialsScreen] → push → [GraphsScreen]
 * - Back button / [ActionStrip] "Dials" → pop → [DialsScreen]
 */
class GraphsScreen(carContext: CarContext) : Screen(carContext) {

    companion object {
        private const val TAG = "PiDrive"

        // Trend thresholds
        private const val THROTTLE_HIGH_PCT = 70f
        private const val GFORCE_HIGH_G = 0.3f
    }

    private var latestSnapshot: VehicleSnapshot = VehicleSnapshot.EMPTY
    private var latestManualTrip: ManualTripState = AADataBridge.manualTripState.value
    private var latestAutoTrip: AutoTripState? = null
    private var latestLayout: AALayoutConfig = AADataBridge.aaLayout.value

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { AADataBridge.snapshot.collect { latestSnapshot = it; invalidate() } }
                launch { AADataBridge.manualTripState.collect { latestManualTrip = it; invalidate() } }
                launch { AADataBridge.autoTripState.collect { latestAutoTrip = it; invalidate() } }
                launch { AADataBridge.aaLayout.collect { latestLayout = it; invalidate() } }
            }
        }
        Log.d(TAG, "GraphsScreen: created")
    }

    override fun onGetTemplate(): Template {
        val slots = latestLayout.graphsSlots
        val snap = latestSnapshot
        val manual = latestManualTrip
        val auto = latestAutoTrip

        val itemListBuilder = ItemList.Builder()
        slots.take(4).forEach { slot ->
            val valueText = formatSlotValueForAA(slot, snap, manual, auto)
            val title = "${slot.displayLabel} · $valueText"
            val detail = if (slot.widgetType == AAWidgetType.TREND)
                slot.metricId.displayLabel
            else
                slot.displayLabel
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(title)
                    .addText(detail)
                    .build()
            )
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Dials")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Pi Drive · Graphs")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemListBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }
}

/**
 * Display data for [GraphsScreen.onGetTemplate].
 *
 * Extracted from live flows so the rendering logic can be tested without a [CarContext].
 */
data class GraphsTemplateData(
    val throttleTitle: String,
    val throttleDetail: String,
    val gforceTitle: String,
    val gforceDetail: String,
    val instantMpgTitle: String,
    val mpgDetail: String,
    val manualTripTitle: String,
    val manualTripDetail: String,
)

/**
 * Converts live data into [GraphsTemplateData] for [GraphsScreen.onGetTemplate].
 *
 * Pure function — suitable for direct unit testing.
 *
 * @param snapshot        Most-recent vehicle telemetry snapshot.
 * @param manualTrip      Current manual-trip state from [ManualTripManager].
 * @param autoTrip        Current auto-detected trip, or null.
 */
fun buildGraphsTemplateData(
    snapshot: VehicleSnapshot,
    manualTrip: ManualTripState,
    autoTrip: AutoTripState?,
): GraphsTemplateData {
    // Throttle
    val throttlePct = snapshot.throttlePct
    val throttleLabel = throttlePct?.let { "%.0f%%".format(it) } ?: "—"
    val throttleTrend = when {
        throttlePct == null -> ""
        throttlePct >= 70f -> "  ▲ HIGH"
        throttlePct >= 40f -> "  ▶ MED"
        else -> "  ▼ LOW"
    }
    val throttleTitle = "THROTTLE · $throttleLabel"
    val throttleDetail = "Throttle position$throttleTrend"

    // G-force
    val gForce = snapshot.gForce
    val gforceLabel = gForce?.let { "%.2fg".format(it) } ?: "—"
    val gforceTrend = when {
        gForce == null -> ""
        gForce >= 0.3f -> "  ⚠ HARD"
        gForce >= 0.15f -> "  ▶ MOD"
        else -> "  ▼ SMOOTH"
    }
    val gforceTitle = "G-FORCE · $gforceLabel"
    val gforceDetail = "Lateral / longitudinal$gforceTrend"

    // Instant MPG (computed from MAF or fuel rate)
    val instantMpg = computeInstantMpg(snapshot)
    val instantMpgTitle = "MPG · NOW  ${instantMpg?.let { "%.1f".format(it) } ?: "—"}"
    val tripAvgMpg = autoTrip?.avgMpg ?: manualTrip.avgMpg
    val mpgDetail = tripAvgMpg?.let { "Trip avg: %.1f mpg".format(it) } ?: "Trip avg: —"

    // Manual trip stat box
    val manualTripDist = manualTrip.distanceMiles.takeIf { manualTrip.isActive }
    val manualTripTitle = "MANUAL TRIP  ${manualTripDist?.let { "%.1f mi".format(it) } ?: "—"}"
    val manualTripAvgMpg = manualTrip.avgMpg
    val startLabel = manualTrip.startDate?.let { "since ${it.month.name.lowercase().replaceFirstChar(Char::uppercase)} ${it.dayOfMonth}" } ?: ""
    val manualTripDetail = buildString {
        if (startLabel.isNotEmpty()) append(startLabel)
        if (manualTripAvgMpg != null) {
            if (isNotEmpty()) append("  ·  ")
            append("%.1f mpg avg".format(manualTripAvgMpg))
        }
        if (isEmpty()) append("—")
    }

    return GraphsTemplateData(
        throttleTitle = throttleTitle,
        throttleDetail = throttleDetail,
        gforceTitle = gforceTitle,
        gforceDetail = gforceDetail,
        instantMpgTitle = instantMpgTitle,
        mpgDetail = mpgDetail,
        manualTripTitle = manualTripTitle,
        manualTripDetail = manualTripDetail,
    )
}

/**
 * Computes instant MPG from the current snapshot using MAF or fuel-rate path.
 *
 * Returns null if neither MAF nor fuel rate is available or if speed is zero.
 */
private fun computeInstantMpg(snapshot: VehicleSnapshot): Float? {
    val speedKmh = snapshot.speedKmh?.toFloat() ?: return null
    if (speedKmh <= 0f) return null

    // Fuel-rate path (preferred when available)
    val fuelRateLph = snapshot.fuelRateLph
    if (fuelRateLph != null && fuelRateLph > 0f) {
        val speedKmhF = speedKmh
        // km/h ÷ L/h × (1 L / 0.264172 gal) × (1 km / 0.621371 mi)
        return (speedKmhF / fuelRateLph) / 0.264172f * 0.621371f
    }

    // MAF path
    val mafGps = snapshot.mafGps
    if (mafGps != null && mafGps > 0f) {
        // MAF (g/s) → fuel_rate_gph = maf × 3600 / 14.7 (stoich AFR for gasoline)
        val fuelRateGph = (mafGps * 3600f) / 14.7f
        val fuelRateLph = fuelRateGph / 737.22f  // g/h → L/h (density ~737 g/L)
        if (fuelRateLph > 0f) {
            return (speedKmh / fuelRateLph) / 0.264172f * 0.621371f
        }
    }

    return null
}
