package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GeneralSettingsManager].
 *
 * Uses a [FakeSharedPreferences] to avoid any Android/Robolectric dependency, verifying
 * read-back consistency after [GeneralSettingsManager.update] and correct reset behaviour.
 */
class GeneralSettingsManagerTest {

    // ── Fake SharedPreferences ────────────────────────────────────────────────

    /**
     * In-memory [SharedPreferences] backed by a [HashMap].
     *
     * Implements only the methods used by [GeneralSettingsManager]; enough to exercise
     * all preference read/write paths without involving the Android SDK at runtime.
     */
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
        GeneralSettingsManager(prefs)

    // ── Tests ─────────────────────────────────────────────────────────────────

    /** Fresh manager with empty prefs should report all defaults from [GeneralSettings]. */
    @Test
    fun `default values match GeneralSettings defaults`() {
        val manager = makeManager()
        val defaults = GeneralSettings()
        val actual = manager.settings.value

        assertEquals("isDarkTheme default", defaults.isDarkTheme, actual.isDarkTheme)
        assertEquals("accentIndex default", defaults.accentIndex, actual.accentIndex)
        assertEquals("speedUnit default", defaults.speedUnit, actual.speedUnit)
        assertEquals("temperatureUnit default", defaults.temperatureUnit, actual.temperatureUnit)
        assertEquals("dataRetentionDays default", defaults.dataRetentionDays, actual.dataRetentionDays)
        assertEquals("autoTripEndTimeoutMinutes default", defaults.autoTripEndTimeoutMinutes, actual.autoTripEndTimeoutMinutes)
    }

    /** Updating the accent index persists the value and emits it on [settings]. */
    @Test
    fun `update accent index - read back matches`() {
        val manager = makeManager()

        manager.update(manager.settings.value.copy(accentIndex = 2))

        assertEquals(2, manager.settings.value.accentIndex)
    }

    /** Updating the theme flag persists the value. */
    @Test
    fun `update dark theme to false - read back is false`() {
        val manager = makeManager()

        manager.update(manager.settings.value.copy(isDarkTheme = false))

        assertFalse(manager.settings.value.isDarkTheme)
    }

    /** Updating speed unit to KMH persists the value. */
    @Test
    fun `update speed unit - read back matches`() {
        val manager = makeManager()

        manager.update(manager.settings.value.copy(speedUnit = SpeedUnit.KMH))

        assertEquals(SpeedUnit.KMH, manager.settings.value.speedUnit)
    }

    /** Changes written to prefs are re-loaded correctly by a new manager sharing the same prefs. */
    @Test
    fun `persisted values survive manager recreation`() {
        val prefs = FakeSharedPreferences()
        val manager1 = makeManager(prefs)
        manager1.update(
            GeneralSettings(
                isDarkTheme = false,
                accentIndex = 3,
                speedUnit = SpeedUnit.KMH,
                temperatureUnit = TemperatureUnit.CELSIUS,
                dataRetentionDays = 30,
                autoTripEndTimeoutMinutes = 10,
            )
        )

        // Recreate from same prefs — simulates app restart reading existing prefs.
        val manager2 = makeManager(prefs)
        val loaded = manager2.settings.value

        assertFalse(loaded.isDarkTheme)
        assertEquals(3, loaded.accentIndex)
        assertEquals(SpeedUnit.KMH, loaded.speedUnit)
        assertEquals(TemperatureUnit.CELSIUS, loaded.temperatureUnit)
        assertEquals(30, loaded.dataRetentionDays)
        assertEquals(10, loaded.autoTripEndTimeoutMinutes)
    }

    /** [reset] clears persisted values and emits [GeneralSettings] defaults. */
    @Test
    fun `reset after update returns all defaults`() {
        val manager = makeManager()
        manager.update(
            GeneralSettings(isDarkTheme = false, accentIndex = 2, speedUnit = SpeedUnit.KMH)
        )

        manager.reset()
        val after = manager.settings.value

        assertTrue("isDarkTheme should be restored to default", after.isDarkTheme)
        assertEquals("accentIndex should be restored to default", 0, after.accentIndex)
        assertEquals("speedUnit should be restored to default", SpeedUnit.MPH, after.speedUnit)
    }

    /** Out-of-range accent index is clamped to [0, 3] on load. */
    @Test
    fun `accent index out of range is clamped`() {
        val prefs = FakeSharedPreferences()
        // Write a value beyond the valid range directly to prefs.
        prefs.edit().putInt("accent_index", 99).apply()

        val manager = makeManager(prefs)

        assertEquals(3, manager.settings.value.accentIndex)
    }
}
