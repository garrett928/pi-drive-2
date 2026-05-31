package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Loads and persists [AALayoutConfig] to [SharedPreferences] as JSON.
 *
 * Exposes a hot [StateFlow] so the settings editor and the Android Auto screens
 * can both react to layout changes in real time without polling. The entire config
 * is stored under a single key; unknown JSON fields are silently ignored on read-back.
 *
 * Accepts [SharedPreferences] directly so unit tests can inject a fake implementation
 * without Robolectric.
 *
 * @param prefs The [SharedPreferences] instance backing the store.
 */
class AALayoutManager(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }
    private val _layout = MutableStateFlow(load())

    /** Current AA layout configuration. Emits a new value after every mutation or [reset]. */
    val layout: StateFlow<AALayoutConfig> = _layout.asStateFlow()

    /**
     * Replaces the Dials screen slot list and persists the change.
     *
     * @param slots List of exactly 6 [AASlotConfig] items in display order.
     */
    fun updateDialsSlots(slots: List<AASlotConfig>) =
        update(_layout.value.copy(dialsSlots = slots))

    /**
     * Replaces the Graphs screen slot list and persists the change.
     *
     * @param slots List of exactly 4 [AASlotConfig] items in list order.
     */
    fun updateGraphsSlots(slots: List<AASlotConfig>) =
        update(_layout.value.copy(graphsSlots = slots))

    /**
     * Replaces the Split Panel page-1 slot list and persists the change.
     *
     * @param slots List of exactly 5 [AASlotConfig] items: hero + 4 pills.
     */
    fun updateSplitPage1Slots(slots: List<AASlotConfig>) =
        update(_layout.value.copy(splitPage1Slots = slots))

    /**
     * Replaces the Split Panel page-2 slot list and persists the change.
     *
     * @param slots List of exactly 6 [AASlotConfig] items for the tile grid.
     */
    fun updateSplitPage2Slots(slots: List<AASlotConfig>) =
        update(_layout.value.copy(splitPage2Slots = slots))

    /**
     * Persists [config] to SharedPreferences and emits it on [layout].
     *
     * Write is asynchronous ([apply]). Use [SharedPreferences.Editor.commit] directly
     * if synchronous durability is required (e.g., before process termination).
     */
    fun update(config: AALayoutConfig) {
        prefs.edit { putString(KEY_LAYOUT, json.encodeToString(config)) }
        _layout.value = config
    }

    /**
     * Clears the persisted layout and resets [layout] to [AALayoutConfig] defaults.
     *
     * Called when the user selects "Reset all settings" from the settings root screen.
     */
    fun reset() {
        prefs.edit { remove(KEY_LAYOUT) }
        _layout.value = AALayoutConfig()
    }

    private fun load(): AALayoutConfig {
        val stored = prefs.getString(KEY_LAYOUT, null) ?: return AALayoutConfig()
        return try {
            json.decodeFromString<AALayoutConfig>(stored)
        } catch (_: Exception) {
            AALayoutConfig()
        }
    }

    companion object {
        /** SharedPreferences file name — used by the Hilt module to open the correct store. */
        const val PREFS_NAME = "pi_drive_aa_layout"
        private const val KEY_LAYOUT = "aa_layout"
    }
}
