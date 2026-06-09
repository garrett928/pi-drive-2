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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ghart.space.pi_drive.shared.R
import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.settings.AALayoutConfig
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
        Log.d(TAG, "SplitPanelScreen: created")
    }

    override fun onGetTemplate(): Template {
        val snap = latestSnapshot
        val manual = latestManualTrip
        val auto = latestAutoTrip
        val page = pageManager.currentPage
        val layout = latestLayout

        val itemList = if (page == SplitPageManager.Page.HERO) {
            buildPage1ItemList(snap, manual, auto, layout)
        } else {
            buildPage2ItemList(snap, manual, auto, layout)
        }

        val pageLabel = if (page == SplitPageManager.Page.HERO) "1 / 2" else "2 / 2"
        val otherPageTitle = if (page == SplitPageManager.Page.HERO) "Tiles →" else "← Hero"

        // A Car App ActionStrip allows at most one action with a custom title. The
        // page-toggle keeps its (dynamic) label; the "back to Dials" action uses an icon.
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
                    .setIcon(actionIcon(R.drawable.ic_aa_dials))
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
        layout: AALayoutConfig,
    ): ItemList {
        val slots = layout.splitPage1Slots
        val builder = ItemList.Builder()
        slots.take(5).forEach { slot ->
            val valueText = formatSlotValueForAA(slot, snap, manual, auto)
            builder.addItem(buildGridItem(valueText, slot.displayLabel))
        }
        return builder.build()
    }

    // ── Page 2: Metric Tiles ───────────────────────────────────────────────────

    private fun buildPage2ItemList(
        snap: VehicleSnapshot,
        manual: ManualTripState,
        auto: AutoTripState?,
        layout: AALayoutConfig,
    ): ItemList {
        val slots = layout.splitPage2Slots
        val builder = ItemList.Builder()
        slots.take(6).forEach { slot ->
            val valueText = formatSlotValueForAA(slot, snap, manual, auto)
            val dangered = if (isDangerConditionForAA(slot.metricId, snap)) "⚠ $valueText" else valueText
            builder.addItem(buildGridItem(dangered, slot.displayLabel))
        }
        return builder.build()
    }

    /**
     * Builds a single grid tile.
     *
     * The Car App Library requires every [GridItem] in a [GridTemplate] to carry an
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

    /**
     * Test seam: switches to the [SplitPageManager.Page.TILES] page so a test can render and
     * validate the page-2 template. Mirrors what the ActionStrip toggle does at runtime
     * (toggle page + [invalidate]) without invoking a host-backed click listener.
     */
    @androidx.annotation.VisibleForTesting
    internal fun showTilesPageForTest() {
        pageManager.showTiles()
        invalidate()
    }
}

