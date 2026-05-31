package ghart.space.pi_drive.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ghart.space.pi_drive.shared.settings.AALayoutConfig
import ghart.space.pi_drive.shared.settings.AALayoutManager
import ghart.space.pi_drive.shared.settings.AASlotConfig
import ghart.space.pi_drive.shared.settings.DEFAULT_DIALS_SLOTS
import ghart.space.pi_drive.shared.settings.DEFAULT_GRAPHS_SLOTS
import ghart.space.pi_drive.shared.settings.DEFAULT_SPLIT_PAGE1_SLOTS
import ghart.space.pi_drive.shared.settings.DEFAULT_SPLIT_PAGE2_SLOTS
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for the Android Auto layout settings screen.
 *
 * Exposes the current [AALayoutConfig] as a [StateFlow] and provides mutation methods
 * for each screen's slot list. All changes are immediately persisted via [AALayoutManager].
 *
 * @param aaLayoutManager Persists the AA screen layout configuration to SharedPreferences.
 */
@HiltViewModel
class AALayoutViewModel @Inject constructor(
    private val aaLayoutManager: AALayoutManager,
) : ViewModel() {

    /** Current AA layout configuration. Emits a new value after every mutation. */
    val layout: StateFlow<AALayoutConfig> = aaLayoutManager.layout

    /**
     * Replaces the Dials screen slot list.
     *
     * @param slots Ordered list of exactly 6 [AASlotConfig] items.
     */
    fun updateDialsSlots(slots: List<AASlotConfig>) = aaLayoutManager.updateDialsSlots(slots)

    /**
     * Replaces the Graphs screen slot list.
     *
     * @param slots Ordered list of exactly 4 [AASlotConfig] items.
     */
    fun updateGraphsSlots(slots: List<AASlotConfig>) = aaLayoutManager.updateGraphsSlots(slots)

    /**
     * Replaces the Split Panel page-1 slot list (hero + 4 pills).
     *
     * @param slots Ordered list of exactly 5 [AASlotConfig] items.
     */
    fun updateSplitPage1Slots(slots: List<AASlotConfig>) = aaLayoutManager.updateSplitPage1Slots(slots)

    /**
     * Replaces the Split Panel page-2 slot list (6 tile grid).
     *
     * @param slots Ordered list of exactly 6 [AASlotConfig] items.
     */
    fun updateSplitPage2Slots(slots: List<AASlotConfig>) = aaLayoutManager.updateSplitPage2Slots(slots)

    /** Resets the Dials screen layout to its factory defaults. */
    fun resetDials() =
        aaLayoutManager.update(aaLayoutManager.layout.value.copy(dialsSlots = DEFAULT_DIALS_SLOTS))

    /** Resets the Graphs screen layout to its factory defaults. */
    fun resetGraphs() =
        aaLayoutManager.update(aaLayoutManager.layout.value.copy(graphsSlots = DEFAULT_GRAPHS_SLOTS))

    /** Resets the Split Panel page-1 layout to its factory defaults. */
    fun resetSplitPage1() =
        aaLayoutManager.update(aaLayoutManager.layout.value.copy(splitPage1Slots = DEFAULT_SPLIT_PAGE1_SLOTS))

    /** Resets the Split Panel page-2 layout to its factory defaults. */
    fun resetSplitPage2() =
        aaLayoutManager.update(aaLayoutManager.layout.value.copy(splitPage2Slots = DEFAULT_SPLIT_PAGE2_SLOTS))

    /** Resets all AA screen layouts to factory defaults. */
    fun resetAll() = aaLayoutManager.reset()
}
