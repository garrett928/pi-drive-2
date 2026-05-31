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
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.launch

/**
 * Android Auto side-panel screen for split-screen mode.
 *
 * Renders a compact [GridTemplate] designed to fit in the 1/3-width side panel that
 * appears when a navigation app (e.g. Google Maps) is running in the foreground. The
 * Car App host handles scaling; this screen only provides concise grid items.
 *
 * Two pages are available, toggled via the [ActionStrip]:
 * - **Page 1 (Hero):** Instant MPG (hero value) + 4 pills: trip MPG, trip distance,
 *   manual trip distance, battery voltage.
 * - **Page 2 (Tiles):** 6 compact metric tiles: RPM, coolant, throttle, fuel level,
 *   battery, oil temp.
 *
 * Page indicator (1 or 2) appears in the template title.
 */
class SplitPanelScreen(carContext: CarContext) : Screen(carContext) {

    companion object {
        private const val TAG = "PiDrive"
    }

    private val pageManager = SplitPageManager()

    private var latestSnapshot: VehicleSnapshot = VehicleSnapshot.EMPTY
    private var latestManualTrip: ManualTripState = AADataBridge.manualTripState.value
    private var latestAutoTrip: AutoTripState? = null

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { AADataBridge.snapshot.collect { latestSnapshot = it; invalidate() } }
                launch { AADataBridge.manualTripState.collect { latestManualTrip = it; invalidate() } }
                launch { AADataBridge.autoTripState.collect { latestAutoTrip = it; invalidate() } }
            }
        }
        Log.d(TAG, "SplitPanelScreen: created")
    }

    override fun onGetTemplate(): Template {
        val snap = latestSnapshot
        val manual = latestManualTrip
        val auto = latestAutoTrip
        val page = pageManager.currentPage

        val itemList = if (page == SplitPageManager.Page.HERO) {
            buildPage1ItemList(snap, manual, auto)
        } else {
            buildPage2ItemList(snap)
        }

        val pageLabel = if (page == SplitPageManager.Page.HERO) "1 / 2" else "2 / 2"
        val otherPageTitle = if (page == SplitPageManager.Page.HERO) "Tiles →" else "← Hero"

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(otherPageTitle)
                    .setOnClickListener {
                        pageManager.togglePage()
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Dials")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()

        return GridTemplate.Builder()
            .setTitle("Pi Drive · $pageLabel")
            .setSingleList(itemList)
            .setActionStrip(actionStrip)
            .build()
    }

    // ── Page 1: Hero Layout ────────────────────────────────────────────────────

    private fun buildPage1ItemList(
        snap: VehicleSnapshot,
        manual: ManualTripState,
        auto: AutoTripState?,
    ): ItemList {
        val instantMpg = computeSplitInstantMpg(snap)
        val heroText = instantMpg?.let { "%.1f mpg".format(it) } ?: "— mpg"

        val tripMpg = auto?.avgMpg ?: manual.avgMpg
        val tripMpgText = tripMpg?.let { "%.1f mpg".format(it) } ?: "—"

        val tripDist = auto?.distanceMiles ?: manual.distanceMiles.takeIf { manual.isActive }
        val tripDistText = tripDist?.let { "%.1f mi".format(it) } ?: "—"

        val manualDistText = if (manual.isActive) "%.1f mi".format(manual.distanceMiles) else "—"

        val battText = snap.batteryVoltage?.let { "%.1f V".format(it) } ?: "—"

        return ItemList.Builder()
            .addItem(buildGridItem(heroText, "MPG · NOW"))
            .addItem(buildGridItem(tripMpgText, "TRIP MPG"))
            .addItem(buildGridItem(tripDistText, "AUTO TRIP"))
            .addItem(buildGridItem(manualDistText, "MANUAL"))
            .addItem(buildGridItem(battText, "BATTERY"))
            .build()
    }

    // ── Page 2: Metric Tiles ───────────────────────────────────────────────────

    private fun buildPage2ItemList(snap: VehicleSnapshot): ItemList {
        val rpmText = snap.rpm?.toString() ?: "—"
        val coolantF = snap.coolantTempC?.let { (it * 9f / 5f + 32f).toInt() }
        val coolantText = coolantF?.let { "${it}°F" } ?: "—"
        val throttleText = snap.throttlePct?.let { "%.0f%%".format(it) } ?: "—"
        val fuelText = snap.fuelLevelPct?.let { "%.0f%%".format(it) } ?: "—"
        val battText = snap.batteryVoltage?.let { "%.1f V".format(it) } ?: "—"
        val oilF = snap.oilTempC?.let { (it * 9f / 5f + 32f).toInt() }
        val oilText = oilF?.let { "${it}°F" } ?: "—"

        return ItemList.Builder()
            .addItem(buildGridItem(rpmText, "RPM"))
            .addItem(buildGridItem(coolantText, "COOLANT"))
            .addItem(buildGridItem(throttleText, "THROTTLE"))
            .addItem(buildGridItem(fuelText, "FUEL"))
            .addItem(buildGridItem(battText, "BATTERY"))
            .addItem(buildGridItem(oilText, "OIL TEMP"))
            .build()
    }

    private fun buildGridItem(title: String, label: String): GridItem =
        GridItem.Builder()
            .setTitle(title)
            .setText(label)
            .build()
}

/** Computes instant MPG for the side panel hero value. Mirrors logic in [buildGraphsTemplateData]. */
private fun computeSplitInstantMpg(snapshot: VehicleSnapshot): Float? {
    val speedKmh = snapshot.speedKmh?.toFloat() ?: return null
    if (speedKmh <= 0f) return null
    val fuelRateLph = snapshot.fuelRateLph
    if (fuelRateLph != null && fuelRateLph > 0f) {
        return (speedKmh / fuelRateLph) / 0.264172f * 0.621371f
    }
    val mafGps = snapshot.mafGps
    if (mafGps != null && mafGps > 0f) {
        val fuelRateGphCalc = (mafGps * 3600f) / 14.7f
        val fuelRateLphCalc = fuelRateGphCalc / 737.22f
        if (fuelRateLphCalc > 0f) {
            return (speedKmh / fuelRateLphCalc) / 0.264172f * 0.621371f
        }
    }
    return null
}
