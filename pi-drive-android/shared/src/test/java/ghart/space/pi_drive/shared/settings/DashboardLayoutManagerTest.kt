package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import ghart.space.pi_drive.shared.data.model.MetricId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [DashboardLayoutManager].
 *
 * Uses a [FakeSharedPreferences] to avoid any Android/Robolectric dependency, verifying
 * JSON persistence, read-back consistency, and fallback-to-defaults on corrupt JSON.
 */
class DashboardLayoutManagerTest {

    // ── Fake SharedPreferences ────────────────────────────────────────────────

    private class FakeSharedPreferences : SharedPreferences {
        private val store = mutableMapOf<String, Any?>()

        inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearPending = false

            override fun putString(k: String, v: String?) = apply { pending[k] = v }
            override fun putStringSet(k: String, v: Set<String>?) = apply { pending[k] = v }
            override fun putInt(k: String, v: Int) = apply { pending[k] = v }
            override fun putLong(k: String, v: Long) = apply { pending[k] = v }
            override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
            override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
            override fun remove(k: String) = apply { pending[k] = null }
            override fun clear() = apply { clearPending = true }

            override fun commit(): Boolean { apply(); return true }

            override fun apply() {
                if (clearPending) store.clear()
                pending.forEach { (k, v) -> if (v == null) store.remove(k) else store[k] = v }
            }
        }

        override fun getAll(): Map<String, *> = store.toMap()
        override fun getString(k: String, def: String?): String? = (store[k] as? String) ?: def
        override fun getStringSet(k: String, def: Set<String>?): Set<String>? =
            (store[k] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: def
        override fun getInt(k: String, def: Int): Int = (store[k] as? Int) ?: def
        override fun getLong(k: String, def: Long): Long = (store[k] as? Long) ?: def
        override fun getFloat(k: String, def: Float): Float = (store[k] as? Float) ?: def
        override fun getBoolean(k: String, def: Boolean): Boolean = (store[k] as? Boolean) ?: def
        override fun contains(k: String): Boolean = store.containsKey(k)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeManager(prefs: SharedPreferences = FakeSharedPreferences()) =
        DashboardLayoutManager(prefs)

    // ── Tests ─────────────────────────────────────────────────────────────────

    /** Fresh manager with empty prefs should report the [DashboardLayout] defaults. */
    @Test
    fun `default layout uses SPEED as featured metric`() {
        val manager = makeManager()
        assertEquals(MetricId.SPEED, manager.layout.value.featuredMetricId)
    }

    /** Default tiles list should match [DEFAULT_DASHBOARD_TILES]. */
    @Test
    fun `default layout tiles match DEFAULT_DASHBOARD_TILES`() {
        val manager = makeManager()
        assertEquals(DEFAULT_DASHBOARD_TILES, manager.layout.value.tiles)
    }

    /** Saving a layout with 4 custom tiles reads back with the same 4 tiles. */
    @Test
    fun `save 4 custom tiles - read back has same 4 tiles`() {
        val manager = makeManager()
        val tiles = listOf(
            DashboardTileConfig(MetricId.RPM, WidgetType.DIAL),
            DashboardTileConfig(MetricId.THROTTLE, WidgetType.BAR),
            DashboardTileConfig(MetricId.FUEL, WidgetType.NUMBER),
            DashboardTileConfig(MetricId.BATTERY, WidgetType.NUMBER),
        )
        manager.updateTiles(tiles)
        assertEquals(tiles, manager.layout.value.tiles)
    }

    /** Updating the featured metric persists and reads back correctly. */
    @Test
    fun `update featured metric to RPM - read back matches`() {
        val manager = makeManager()
        manager.updateFeaturedMetric(MetricId.RPM)
        assertEquals(MetricId.RPM, manager.layout.value.featuredMetricId)
    }

    /** Persisted values survive manager recreation (simulates app restart). */
    @Test
    fun `persisted layout survives manager recreation`() {
        val prefs = FakeSharedPreferences()
        val tiles = listOf(
            DashboardTileConfig(MetricId.SPEED, WidgetType.BAR),
            DashboardTileConfig(MetricId.RPM, WidgetType.DIAL),
        )
        makeManager(prefs).update(DashboardLayout(featuredMetricId = MetricId.BATTERY, tiles = tiles))

        val reloaded = makeManager(prefs)
        assertEquals(MetricId.BATTERY, reloaded.layout.value.featuredMetricId)
        assertEquals(tiles, reloaded.layout.value.tiles)
    }

    /** Invalid/corrupt JSON in prefs falls back to default layout without crashing. */
    @Test
    fun `corrupt JSON in prefs falls back to defaults`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("layout", "{this is not valid json}").apply()

        val manager = makeManager(prefs)
        assertEquals(MetricId.SPEED, manager.layout.value.featuredMetricId)
        assertEquals(DEFAULT_DASHBOARD_TILES, manager.layout.value.tiles)
    }

    /** [reset] clears persisted values and restores defaults. */
    @Test
    fun `reset after update returns defaults`() {
        val manager = makeManager()
        manager.updateFeaturedMetric(MetricId.COOLANT)
        manager.updateTiles(listOf(DashboardTileConfig(MetricId.THROTTLE, WidgetType.BAR)))

        manager.reset()

        assertEquals(MetricId.SPEED, manager.layout.value.featuredMetricId)
        assertEquals(DEFAULT_DASHBOARD_TILES, manager.layout.value.tiles)
    }

    /** StateFlow emits the new layout after [update]. */
    @Test
    fun `update emits on layout StateFlow`() {
        val manager = makeManager()
        val newLayout = DashboardLayout(featuredMetricId = MetricId.MAF)
        manager.update(newLayout)
        assertEquals(MetricId.MAF, manager.layout.value.featuredMetricId)
    }
}
