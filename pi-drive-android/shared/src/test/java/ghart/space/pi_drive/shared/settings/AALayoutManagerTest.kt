package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import ghart.space.pi_drive.shared.data.model.MetricId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AALayoutManager].
 *
 * Uses an in-memory [FakeSharedPreferences] to avoid Robolectric. Covers:
 * - Default layout on first access.
 * - Saving a custom layout and reading it back.
 * - Resetting to defaults.
 * - Per-screen slot update helpers.
 * - Persistence across restarts (re-instantiate from same prefs).
 */
class AALayoutManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: AALayoutManager

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = AALayoutManager(prefs)
    }

    // ── Default layout ──────────────────────────────────────────────────────────

    @Test
    fun layout_defaultsMatchSpec() {
        val layout = manager.layout.value
        assertEquals(DEFAULT_DIALS_SLOTS, layout.dialsSlots)
        assertEquals(DEFAULT_GRAPHS_SLOTS, layout.graphsSlots)
        assertEquals(DEFAULT_SPLIT_PAGE1_SLOTS, layout.splitPage1Slots)
        assertEquals(DEFAULT_SPLIT_PAGE2_SLOTS, layout.splitPage2Slots)
    }

    @Test
    fun defaultDialsSlots_hasExactlySixEntries() {
        assertEquals(6, manager.layout.value.dialsSlots.size)
    }

    @Test
    fun defaultGraphsSlots_hasExactlyFourEntries() {
        assertEquals(4, manager.layout.value.graphsSlots.size)
    }

    @Test
    fun defaultSplitPage1Slots_hasExactlyFiveEntries() {
        assertEquals(5, manager.layout.value.splitPage1Slots.size)
    }

    @Test
    fun defaultSplitPage2Slots_hasExactlySixEntries() {
        assertEquals(6, manager.layout.value.splitPage2Slots.size)
    }

    // ── Save custom layout → read back ──────────────────────────────────────────

    @Test
    fun saveCustomLayout_readBackMatches() {
        val customDials = listOf(
            AASlotConfig(MetricId.RPM, AAWidgetType.DIAL),
            AASlotConfig(MetricId.SPEED, AAWidgetType.DIAL),
            AASlotConfig(MetricId.THROTTLE, AAWidgetType.DIAL),
            AASlotConfig(MetricId.COOLANT, AAWidgetType.STAT),
            AASlotConfig(MetricId.FUEL, AAWidgetType.STAT),
            AASlotConfig(MetricId.OIL_TEMP, AAWidgetType.STAT),
        )
        val config = AALayoutConfig(dialsSlots = customDials)
        manager.update(config)

        assertEquals(customDials, manager.layout.value.dialsSlots)
    }

    @Test
    fun saveCustomLayout_persistsAcrossRestart() {
        val customDials = listOf(
            AASlotConfig(MetricId.G_FORCE, AAWidgetType.TREND),
            AASlotConfig(MetricId.ACCEL, AAWidgetType.TREND),
            AASlotConfig(MetricId.MPG_INSTANT, AAWidgetType.TREND),
            AASlotConfig(MetricId.BATTERY, AAWidgetType.STAT),
            AASlotConfig(MetricId.FUEL, AAWidgetType.STAT),
            AASlotConfig(MetricId.RPM, AAWidgetType.STAT),
        )
        manager.update(AALayoutConfig(dialsSlots = customDials))

        // Re-instantiate with same prefs (simulates app restart)
        val reloaded = AALayoutManager(prefs)
        assertEquals(customDials, reloaded.layout.value.dialsSlots)
    }

    // ── Reset to defaults ────────────────────────────────────────────────────────

    @Test
    fun reset_returnsAllSlotsToDefaults() {
        val custom = AALayoutConfig(
            dialsSlots = listOf(AASlotConfig(MetricId.RPM, AAWidgetType.DIAL)),
            graphsSlots = listOf(AASlotConfig(MetricId.THROTTLE, AAWidgetType.TREND)),
            splitPage1Slots = listOf(AASlotConfig(MetricId.MPG_INSTANT, AAWidgetType.DIAL)),
            splitPage2Slots = listOf(AASlotConfig(MetricId.RPM, AAWidgetType.STAT)),
        )
        manager.update(custom)
        manager.reset()

        val layout = manager.layout.value
        assertEquals(DEFAULT_DIALS_SLOTS, layout.dialsSlots)
        assertEquals(DEFAULT_GRAPHS_SLOTS, layout.graphsSlots)
        assertEquals(DEFAULT_SPLIT_PAGE1_SLOTS, layout.splitPage1Slots)
        assertEquals(DEFAULT_SPLIT_PAGE2_SLOTS, layout.splitPage2Slots)
    }

    @Test
    fun reset_clearsPersistedPrefs() {
        manager.update(AALayoutConfig(dialsSlots = listOf(AASlotConfig(MetricId.RPM, AAWidgetType.DIAL))))
        manager.reset()

        // Re-instantiate — should return defaults, not the cleared custom value
        val reloaded = AALayoutManager(prefs)
        assertEquals(DEFAULT_DIALS_SLOTS, reloaded.layout.value.dialsSlots)
    }

    // ── Per-screen slot helpers ──────────────────────────────────────────────────

    @Test
    fun updateDialsSlots_onlyDialsSlotsChange() {
        val newDials = listOf(
            AASlotConfig(MetricId.FUEL, AAWidgetType.STAT),
            AASlotConfig(MetricId.OIL_TEMP, AAWidgetType.STAT),
            AASlotConfig(MetricId.INTAKE, AAWidgetType.STAT),
            AASlotConfig(MetricId.MAF, AAWidgetType.STAT),
            AASlotConfig(MetricId.ACCEL, AAWidgetType.STAT),
            AASlotConfig(MetricId.G_FORCE, AAWidgetType.STAT),
        )
        manager.updateDialsSlots(newDials)

        val layout = manager.layout.value
        assertEquals(newDials, layout.dialsSlots)
        assertEquals(DEFAULT_GRAPHS_SLOTS, layout.graphsSlots)
        assertEquals(DEFAULT_SPLIT_PAGE1_SLOTS, layout.splitPage1Slots)
        assertEquals(DEFAULT_SPLIT_PAGE2_SLOTS, layout.splitPage2Slots)
    }

    @Test
    fun updateGraphsSlots_onlyGraphsSlotsChange() {
        val newGraphs = listOf(
            AASlotConfig(MetricId.SPEED, AAWidgetType.TREND),
            AASlotConfig(MetricId.RPM, AAWidgetType.TREND),
            AASlotConfig(MetricId.THROTTLE, AAWidgetType.TREND),
            AASlotConfig(MetricId.BATTERY, AAWidgetType.STAT),
        )
        manager.updateGraphsSlots(newGraphs)

        val layout = manager.layout.value
        assertEquals(DEFAULT_DIALS_SLOTS, layout.dialsSlots)
        assertEquals(newGraphs, layout.graphsSlots)
    }

    @Test
    fun updateSplitPage1Slots_onlyPage1Changes() {
        val newPage1 = listOf(
            AASlotConfig(MetricId.SPEED, AAWidgetType.DIAL),
            AASlotConfig(MetricId.RPM, AAWidgetType.STAT),
            AASlotConfig(MetricId.COOLANT, AAWidgetType.STAT),
            AASlotConfig(MetricId.THROTTLE, AAWidgetType.STAT),
            AASlotConfig(MetricId.FUEL, AAWidgetType.STAT),
        )
        manager.updateSplitPage1Slots(newPage1)

        val layout = manager.layout.value
        assertEquals(newPage1, layout.splitPage1Slots)
        assertEquals(DEFAULT_SPLIT_PAGE2_SLOTS, layout.splitPage2Slots)
    }

    // ── Custom label ─────────────────────────────────────────────────────────────

    @Test
    fun customLabel_preservedAfterUpdate() {
        val slotWithLabel = AASlotConfig(MetricId.SPEED, AAWidgetType.DIAL, label = "VELOCITY")
        val slots = DEFAULT_DIALS_SLOTS.toMutableList()
        slots[0] = slotWithLabel
        manager.updateDialsSlots(slots)

        assertEquals("VELOCITY", manager.layout.value.dialsSlots[0].label)
        assertEquals("VELOCITY", manager.layout.value.dialsSlots[0].displayLabel)
    }

    @Test
    fun nullLabel_displayLabelUsesMetricDisplayLabel() {
        val slot = AASlotConfig(MetricId.SPEED, AAWidgetType.DIAL, label = null)
        assertNull(slot.label)
        assertEquals("SPEED", slot.displayLabel)
    }

    // ── StateFlow emissions ──────────────────────────────────────────────────────

    @Test
    fun update_emitsNewLayoutOnFlow() {
        val initial = manager.layout.value
        val newConfig = initial.copy(
            dialsSlots = listOf(
                AASlotConfig(MetricId.RPM, AAWidgetType.DIAL),
                AASlotConfig(MetricId.SPEED, AAWidgetType.DIAL),
                AASlotConfig(MetricId.THROTTLE, AAWidgetType.DIAL),
                AASlotConfig(MetricId.COOLANT, AAWidgetType.STAT),
                AASlotConfig(MetricId.FUEL, AAWidgetType.STAT),
                AASlotConfig(MetricId.BATTERY, AAWidgetType.STAT),
            )
        )
        manager.update(newConfig)

        assertEquals(newConfig, manager.layout.value)
    }
}

// ── Fake SharedPreferences ────────────────────────────────────────────────────

/**
 * In-memory [SharedPreferences] stub that avoids Robolectric for pure-JVM tests.
 */
private class FakeSharedPreferences : SharedPreferences {

    private val store = mutableMapOf<String, Any?>()
    private var pendingEdits = mutableMapOf<String, Any?>()
    private var pendingRemovals = mutableSetOf<String>()

    override fun getString(key: String, defValue: String?): String? =
        (store[key] as? String) ?: defValue

    override fun getAll(): Map<String, Any?> = store.toMap()
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = null
    override fun getInt(key: String, defValue: Int): Int = (store[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (store[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (store[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pendingEdits[key] = value; return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pendingEdits[key] = value; return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pendingEdits[key] = value; return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pendingEdits[key] = value; return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pendingEdits[key] = value; return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            pendingRemovals.add(key); return this
        }
        override fun clear(): SharedPreferences.Editor { store.clear(); return this }
        override fun commit(): Boolean { flush(); return true }
        override fun apply() { flush() }

        private fun flush() {
            pendingRemovals.forEach { store.remove(it) }
            pendingRemovals.clear()
            store.putAll(pendingEdits)
            pendingEdits.clear()
        }
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = Unit
}
