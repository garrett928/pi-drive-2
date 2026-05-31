package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DevSettingsManager].
 *
 * Uses an in-memory [FakeSharedPreferences] to avoid Robolectric. Covers:
 * - Default settings on first access.
 * - Unlock sets [DevSettingsManager.DevSettings.isDevUnlocked] and is idempotent.
 * - Update persists all fields and emits on the [StateFlow].
 * - Persistence across restart (re-instantiate from same prefs).
 * - Reset returns all fields to defaults.
 * - [DevSettingsManager.isAnyModeActive] reflects active modes.
 */
class DevSettingsManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: DevSettingsManager

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
        override fun registerOnSharedPreferenceChangeListener(
            l: SharedPreferences.OnSharedPreferenceChangeListener
        ) {}
        override fun unregisterOnSharedPreferenceChangeListener(
            l: SharedPreferences.OnSharedPreferenceChangeListener
        ) {}
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = DevSettingsManager(prefs)
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    fun defaults_allFalseAndDefaultValues() {
        val s = manager.settings.value
        assertFalse(s.isDevUnlocked)
        assertFalse(s.isDemoMode)
        assertFalse(s.isTcpMode)
        assertEquals("CRUISE", s.demoScenario)
        assertEquals(DevSettingsManager.DEFAULT_TCP_HOST, s.tcpHost)
        assertEquals(DevSettingsManager.DEFAULT_TCP_PORT, s.tcpPort)
    }

    @Test
    fun isAnyModeActive_falseByDefault() {
        assertFalse(manager.isAnyModeActive)
    }

    // ── Unlock ────────────────────────────────────────────────────────────────

    @Test
    fun unlock_setsIsDevUnlocked() {
        manager.unlock()
        assertTrue(manager.settings.value.isDevUnlocked)
    }

    @Test
    fun unlock_isIdempotent() {
        manager.unlock()
        manager.unlock()
        assertTrue(manager.settings.value.isDevUnlocked)
    }

    @Test
    fun unlock_doesNotChangeModeFlags() {
        manager.unlock()
        val s = manager.settings.value
        assertFalse(s.isDemoMode)
        assertFalse(s.isTcpMode)
    }

    @Test
    fun unlock_persistsAcrossRestart() {
        manager.unlock()
        val reloaded = DevSettingsManager(prefs)
        assertTrue(reloaded.settings.value.isDevUnlocked)
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    fun update_persistsDemoModeAndScenario() {
        manager.update(
            DevSettingsManager.DevSettings(isDemoMode = true, demoScenario = "HARD_BRAKE")
        )
        val s = manager.settings.value
        assertTrue(s.isDemoMode)
        assertEquals("HARD_BRAKE", s.demoScenario)
    }

    @Test
    fun update_persistsTcpModeHostAndPort() {
        manager.update(
            DevSettingsManager.DevSettings(
                isTcpMode = true,
                tcpHost = "192.168.1.100",
                tcpPort = 38000,
            )
        )
        val s = manager.settings.value
        assertTrue(s.isTcpMode)
        assertEquals("192.168.1.100", s.tcpHost)
        assertEquals(38000, s.tcpPort)
    }

    @Test
    fun update_emitsNewValueOnFlow() {
        val newSettings = DevSettingsManager.DevSettings(isDemoMode = true, demoScenario = "CITY")
        manager.update(newSettings)
        assertEquals(newSettings, manager.settings.value)
    }

    @Test
    fun update_persistsAcrossRestart() {
        manager.update(
            DevSettingsManager.DevSettings(
                isDevUnlocked = true,
                isTcpMode = true,
                tcpHost = "localhost",
                tcpPort = 40000,
            )
        )
        val reloaded = DevSettingsManager(prefs)
        val s = reloaded.settings.value
        assertTrue(s.isDevUnlocked)
        assertTrue(s.isTcpMode)
        assertEquals("localhost", s.tcpHost)
        assertEquals(40000, s.tcpPort)
    }

    // ── isAnyModeActive ───────────────────────────────────────────────────────

    @Test
    fun isAnyModeActive_trueWhenDemoModeEnabled() {
        manager.update(DevSettingsManager.DevSettings(isDemoMode = true))
        assertTrue(manager.isAnyModeActive)
    }

    @Test
    fun isAnyModeActive_trueWhenTcpModeEnabled() {
        manager.update(DevSettingsManager.DevSettings(isTcpMode = true))
        assertTrue(manager.isAnyModeActive)
    }

    @Test
    fun isAnyModeActive_falseWhenBothDisabled() {
        manager.update(DevSettingsManager.DevSettings(isDevUnlocked = true))
        assertFalse(manager.isAnyModeActive)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun reset_restoresAllDefaults() {
        manager.update(
            DevSettingsManager.DevSettings(
                isDevUnlocked = true,
                isDemoMode = true,
                demoScenario = "OVERSPEED",
                isTcpMode = false,
                tcpHost = "192.168.0.1",
                tcpPort = 9999,
            )
        )
        manager.reset()

        val s = manager.settings.value
        assertFalse(s.isDevUnlocked)
        assertFalse(s.isDemoMode)
        assertFalse(s.isTcpMode)
        assertEquals("CRUISE", s.demoScenario)
        assertEquals(DevSettingsManager.DEFAULT_TCP_HOST, s.tcpHost)
        assertEquals(DevSettingsManager.DEFAULT_TCP_PORT, s.tcpPort)
    }

    @Test
    fun reset_clearsPersistedPrefs() {
        manager.update(DevSettingsManager.DevSettings(isDevUnlocked = true, isDemoMode = true))
        manager.reset()

        val reloaded = DevSettingsManager(prefs)
        assertFalse(reloaded.settings.value.isDevUnlocked)
        assertFalse(reloaded.settings.value.isDemoMode)
    }
}
