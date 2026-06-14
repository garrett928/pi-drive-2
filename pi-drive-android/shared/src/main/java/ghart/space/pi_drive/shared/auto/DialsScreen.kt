package ghart.space.pi_drive.shared.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import ghart.space.pi_drive.shared.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.settings.AALayoutConfig
import kotlinx.coroutines.launch

/**
 * Android Auto screen that displays the three primary vehicle gauges plus a stat strip.
 *
 * Uses a [GridTemplate] (6 items) since the Car App Library's IOT category does not support
 * custom canvas rendering. Values are text-based; threshold violations are prefixed with "⚠".
 *
 * Layout (two rows of 3 items each):
 * ```
 * [ Speed mph ]  [ RPM ]        [ Coolant °F ]
 * [ Trip dist ]  [ Trip MPG ]   [ Battery V  ]
 * ```
 *
 * The [ActionStrip] provides navigation to [GraphsScreen] and [SplitPanelScreen].
 * Invalidate is called on every [VehicleSnapshot] change.
 */
class DialsScreen(carContext: CarContext) : Screen(carContext) {

    companion object {
        private const val TAG = "PiDrive"
        private const val RPM_DANGER_THRESHOLD = 6500
        private const val COOLANT_DANGER_F = 240
        private const val KMH_TO_MPH = 0.621371f
    }

    private var latestSnapshot: VehicleSnapshot = VehicleSnapshot.EMPTY
    private var latestManualTrip: ManualTripState = AADataBridge.manualTripState.value
    private var latestAutoTrip: AutoTripState? = null
    private var latestConnectionState: ConnectionState = ConnectionState.Disconnected()
    private var latestLayout: AALayoutConfig = AADataBridge.aaLayout.value

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AADataBridge.snapshot.collect { snap ->
                        latestSnapshot = snap
                        invalidate()
                    }
                }
                launch {
                    AADataBridge.manualTripState.collect { trip ->
                        latestManualTrip = trip
                        invalidate()
                    }
                }
                launch {
                    AADataBridge.autoTripState.collect { trip ->
                        latestAutoTrip = trip
                        invalidate()
                    }
                }
                launch {
                    AADataBridge.connectionState.collect { state ->
                        latestConnectionState = state
                        invalidate()
                    }
                }
                launch {
                    AADataBridge.aaLayout.collect { layout ->
                        latestLayout = layout
                        invalidate()
                    }
                }
            }
        }
        Log.d(TAG, "DialsScreen: created")
    }

    override fun onGetTemplate(): Template = safeAATemplate("DialsScreen") { buildTemplate() }

    private fun buildTemplate(): Template {
        val slots = latestLayout.dialsSlots
        val snap = latestSnapshot
        val manual = latestManualTrip
        val auto = latestAutoTrip
        val isStreaming = latestConnectionState is ConnectionState.Connected

        // Logged every render so the captured log shows the exact inputs that produced a crash:
        // an empty slot list, a blank value string, etc. all violate GridTemplate constraints.
        Log.d(TAG, "DialsScreen.onGetTemplate: slots=${slots.size} streaming=$isStreaming " +
            "snapshot=[speed=${snap.speedKmh} rpm=${snap.rpm} battery=${snap.batteryVoltage}]")

        val itemListBuilder = ItemList.Builder()
        slots.take(6).forEach { slot ->
            val valueText = formatSlotValueForAA(slot, snap, manual, auto)
            val dangered = if (isDangerConditionForAA(slot.metricId, snap)) "⚠ $valueText" else valueText
            // Battery slot gets a "LIVE" suffix while the OBD connection is active
            val labelText = if (isStreaming && slot.metricId == MetricId.BATTERY)
                "${slot.displayLabel} · LIVE" else slot.displayLabel
            itemListBuilder.addItem(buildGridItem(dangered, labelText))
        }

        // Nav actions use icons, not titles: a Car App ActionStrip allows at most
        // one action with a custom title, so multi-action strips must be icon-based.
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(actionIcon(R.drawable.ic_aa_graphs))
                    .setOnClickListener { screenManager.push(GraphsScreen(carContext)) }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(actionIcon(R.drawable.ic_aa_panel))
                    .setOnClickListener { screenManager.push(SplitPanelScreen(carContext)) }
                    .build()
            )
            .build()

        return GridTemplate.Builder()
            .setTitle("Pi Drive")
            .setSingleList(itemListBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }

    /**
     * Builds a single grid tile.
     *
     * The Car App Library requires every [GridItem] in a [GridTemplate] to have an
     * image (or be flagged as loading); a title/text-only item throws
     * [IllegalStateException] at build time. We attach a neutral gauge glyph tinted
     * to the head-unit theme so the numeric [title] (the metric value) stays the focus.
     */
    private fun buildGridItem(title: String, label: String): GridItem =
        GridItem.Builder()
            .setTitle(title)
            .setText(label)
            .setImage(tileIcon, GridItem.IMAGE_TYPE_ICON)
            .build()

    private val tileIcon: CarIcon by lazy {
        CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_metric_tile)
        )
            .setTint(CarColor.DEFAULT)
            .build()
    }

    /** Builds a theme-tinted [CarIcon] for an ActionStrip action from a drawable resource. */
    private fun actionIcon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId))
            .setTint(CarColor.DEFAULT)
            .build()
}

/**
 * Display data for [DialsScreen.onGetTemplate].
 *
 * Extracted from the live flows so the rendering logic can be tested without a [CarContext].
 *
 * @param speedText   Current speed, e.g. "58" or "—".
 * @param rpmText     Current RPM, prefixed with "⚠ " when above [DialsScreen.RPM_DANGER_THRESHOLD].
 * @param coolantText Coolant temperature in °F, prefixed with "⚠ " when above 240°F.
 * @param tripDistText Trip distance in miles, e.g. "14.3 mi".
 * @param tripMpgText  Trip-average MPG, e.g. "26.4 mpg".
 * @param batteryText  Battery voltage, e.g. "14.2 V".
 * @param isStreaming  True when the OBD connection is established (used to label the battery tile).
 */
data class DialsTemplateData(
    val speedText: String,
    val rpmText: String,
    val coolantText: String,
    val tripDistText: String,
    val tripMpgText: String,
    val batteryText: String,
    val isStreaming: Boolean,
)

/**
 * Converts live data into [DialsTemplateData] for use in [DialsScreen.onGetTemplate].
 *
 * Pure function with no side effects — suitable for direct unit testing.
 *
 * @param snapshot        Most-recent vehicle telemetry snapshot.
 * @param manualTrip      Current manual-trip state from [ManualTripManager].
 * @param autoTrip        Current auto-detected trip, or null if none is active.
 * @param connectionState Current OBD connection state.
 */
fun buildDialsTemplateData(
    snapshot: VehicleSnapshot,
    manualTrip: ManualTripState,
    autoTrip: AutoTripState?,
    connectionState: ConnectionState,
): DialsTemplateData {
    // Speed: convert km/h → mph
    val speedMph = snapshot.speedKmh?.let { (it * 0.621371f).toInt() }
    val speedText = speedMph?.toString() ?: "—"

    // RPM: prefix with warning symbol above danger threshold
    val rpm = snapshot.rpm
    val rpmText = when {
        rpm == null -> "—"
        rpm >= 6500 -> "⚠ $rpm"
        else -> rpm.toString()
    }

    // Coolant: convert °C → °F, warn above 240°F
    val coolantF = snapshot.coolantTempC?.let { (it * 9f / 5f + 32f).toInt() }
    val coolantText = when {
        coolantF == null -> "—"
        coolantF >= 240 -> "⚠ ${coolantF}°F"
        else -> "${coolantF}°F"
    }

    // Trip distance: prefer auto trip, fall back to manual trip
    val tripDistMi = autoTrip?.distanceMiles ?: if (manualTrip.isActive) manualTrip.distanceMiles else null
    val tripDistText = tripDistMi?.let { "%.1f mi".format(it) } ?: "—"

    // Trip MPG: prefer auto trip, fall back to manual trip
    val tripMpg = autoTrip?.avgMpg ?: manualTrip.avgMpg
    val tripMpgText = tripMpg?.let { "%.1f mpg".format(it) } ?: "—"

    // Battery voltage
    val batteryText = snapshot.batteryVoltage?.let { "%.1f V".format(it) } ?: "—"

    // Streaming flag drives the battery tile label
    val isStreaming = connectionState is ConnectionState.Connected

    return DialsTemplateData(
        speedText = speedText,
        rpmText = rpmText,
        coolantText = coolantText,
        tripDistText = tripDistText,
        tripMpgText = tripMpgText,
        batteryText = batteryText,
        isStreaming = isStreaming,
    )
}
