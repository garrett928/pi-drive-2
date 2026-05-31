package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import ghart.space.pi_drive.shared.data.model.MetricId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Loads and persists [DashboardLayout] to [SharedPreferences] as JSON.
 *
 * Exposes a hot [StateFlow] so the live dashboard and layout editor screen can both
 * react to changes in real time without polling. The entire layout is stored under a
 * single key; unrecognised JSON fields are silently dropped on read-back.
 *
 * Designed to accept [SharedPreferences] directly (rather than [android.content.Context])
 * so that unit tests can inject a fake implementation without Robolectric.
 *
 * @param prefs The [SharedPreferences] instance to read from and write to.
 */
class DashboardLayoutManager(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }
    private val _layout = MutableStateFlow(load())

    /** Current layout snapshot. Emits a new value after every mutation or [reset]. */
    val layout: StateFlow<DashboardLayout> = _layout.asStateFlow()

    /**
     * Persists [metricId] as the new featured metric and emits the updated layout.
     *
     * All other layout fields are preserved.
     */
    fun updateFeaturedMetric(metricId: MetricId) {
        update(_layout.value.copy(featuredMetricId = metricId))
    }

    /**
     * Replaces the tile list with [tiles] and emits the updated layout.
     *
     * Callers are responsible for maintaining a sane list length (at least one tile).
     */
    fun updateTiles(tiles: List<DashboardTileConfig>) {
        update(_layout.value.copy(tiles = tiles))
    }

    /**
     * Persists the entire [layout] to SharedPreferences and emits it on [layout].
     *
     * The write is asynchronous ([apply]). Call [SharedPreferences.Editor.commit] directly
     * if synchronous durability is required (e.g., before process termination).
     */
    fun update(layout: DashboardLayout) {
        prefs.edit { putString(KEY_LAYOUT, json.encodeToString(layout)) }
        _layout.value = layout
    }

    /**
     * Clears the stored layout and resets [layout] to the [DashboardLayout] defaults.
     *
     * Called when the user selects "Reset all settings" from the settings root screen.
     */
    fun reset() {
        prefs.edit { remove(KEY_LAYOUT) }
        _layout.value = DashboardLayout()
    }

    private fun load(): DashboardLayout {
        val stored = prefs.getString(KEY_LAYOUT, null) ?: return DashboardLayout()
        return try {
            json.decodeFromString<DashboardLayout>(stored)
        } catch (_: Exception) {
            DashboardLayout()
        }
    }

    companion object {
        /** SharedPreferences file name — used by the Hilt module to open the correct store. */
        const val PREFS_NAME = "pi_drive_dashboard_layout"
        private const val KEY_LAYOUT = "layout"
    }
}
