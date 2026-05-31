package ghart.space.pi_drive.shared.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
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
            }
        }
        Log.d(TAG, "DialsScreen: created")
    }

    override fun onGetTemplate(): Template {
        val data = buildDialsTemplateData(
            snapshot = latestSnapshot,
            manualTrip = latestManualTrip,
            autoTrip = latestAutoTrip,
            connectionState = latestConnectionState,
        )

        val itemList = ItemList.Builder()
            .addItem(buildGridItem(data.speedText, "MPH"))
            .addItem(buildGridItem(data.rpmText, "RPM"))
            .addItem(buildGridItem(data.coolantText, "COOLANT"))
            .addItem(buildGridItem(data.tripDistText, "TRIP DIST"))
            .addItem(buildGridItem(data.tripMpgText, "TRIP MPG"))
            .addItem(buildGridItem(data.batteryText, if (data.isStreaming) "BATT · LIVE" else "BATTERY"))
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Graphs")
                    .setOnClickListener { screenManager.push(GraphsScreen(carContext)) }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Panel")
                    .setOnClickListener { screenManager.push(SplitPanelScreen(carContext)) }
                    .build()
            )
            .build()

        return GridTemplate.Builder()
            .setTitle("Pi Drive")
            .setSingleList(itemList)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun buildGridItem(title: String, label: String): GridItem =
        GridItem.Builder()
            .setTitle(title)
            .setText(label)
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
