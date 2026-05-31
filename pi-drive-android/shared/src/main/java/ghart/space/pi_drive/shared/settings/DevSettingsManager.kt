package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists developer settings that override the launch-intent [AppConfig] on app restart.
 *
 * These settings allow switching between TCP emulator mode, demo mode, and Bluetooth
 * without re-launching via adb commands. The developer settings screen is hidden behind
 * a 7-tap unlock sequence on the version row in Settings > App.
 *
 * Changes take effect on the **next** app launch because [AppConfig] is read once during
 * [MainActivity.onCreate] before Hilt injection. [MainActivity] reads these settings
 * from SharedPreferences directly (bypassing the Hilt singleton) so they can be applied
 * before the DI graph is constructed.
 *
 * @param prefs The [SharedPreferences] file used for persistence.
 */
class DevSettingsManager(private val prefs: SharedPreferences) {

    companion object {
        const val PREFS_NAME = "pi_drive_dev_settings"
        private const val KEY_DEV_UNLOCKED = "dev_unlocked"
        private const val KEY_DEMO_MODE = "demo_mode"
        private const val KEY_DEMO_SCENARIO = "demo_scenario"
        private const val KEY_TCP_MODE = "tcp_mode"
        private const val KEY_TCP_HOST = "tcp_host"
        private const val KEY_TCP_PORT = "tcp_port"

        /** Default ELM327 emulator host — 10.0.2.2 maps to localhost from an Android emulator. */
        const val DEFAULT_TCP_HOST = "10.0.2.2"

        /** Default ELM327 emulator port. */
        const val DEFAULT_TCP_PORT = 35000
    }

    /**
     * Snapshot of persisted developer preferences.
     *
     * @property isDevUnlocked   Whether the developer settings screen is accessible.
     * @property isDemoMode      Use [DemoVehicleDataSource] on next launch.
     * @property demoScenario    Name of the [DemoScenario] to run (matches enum constant name).
     * @property isTcpMode       Use [TcpTransport] on next launch (ELM327 emulator).
     * @property tcpHost         Host for [TcpTransport] (default = 10.0.2.2 = emulator localhost).
     * @property tcpPort         Port for [TcpTransport].
     */
    data class DevSettings(
        val isDevUnlocked: Boolean = false,
        val isDemoMode: Boolean = false,
        val demoScenario: String = "CRUISE",
        val isTcpMode: Boolean = false,
        val tcpHost: String = DEFAULT_TCP_HOST,
        val tcpPort: Int = DEFAULT_TCP_PORT,
    )

    private val _settings = MutableStateFlow(load())

    /** Current developer settings as a reactive [StateFlow]. */
    val settings: StateFlow<DevSettings> = _settings.asStateFlow()

    /** True if any override mode is active (demo or TCP), meaning AppConfig should be overridden. */
    val isAnyModeActive: Boolean
        get() = _settings.value.isDemoMode || _settings.value.isTcpMode

    private fun load(): DevSettings = DevSettings(
        isDevUnlocked = prefs.getBoolean(KEY_DEV_UNLOCKED, false),
        isDemoMode = prefs.getBoolean(KEY_DEMO_MODE, false),
        demoScenario = prefs.getString(KEY_DEMO_SCENARIO, "CRUISE") ?: "CRUISE",
        isTcpMode = prefs.getBoolean(KEY_TCP_MODE, false),
        tcpHost = prefs.getString(KEY_TCP_HOST, DEFAULT_TCP_HOST) ?: DEFAULT_TCP_HOST,
        tcpPort = prefs.getInt(KEY_TCP_PORT, DEFAULT_TCP_PORT),
    )

    /**
     * Persists [settings] and emits the new value on the [settings] [StateFlow].
     *
     * Changes to [DevSettings.isDemoMode] and [DevSettings.isTcpMode] take effect
     * on the next app launch.
     */
    fun update(settings: DevSettings) {
        prefs.edit()
            .putBoolean(KEY_DEV_UNLOCKED, settings.isDevUnlocked)
            .putBoolean(KEY_DEMO_MODE, settings.isDemoMode)
            .putString(KEY_DEMO_SCENARIO, settings.demoScenario)
            .putBoolean(KEY_TCP_MODE, settings.isTcpMode)
            .putString(KEY_TCP_HOST, settings.tcpHost)
            .putInt(KEY_TCP_PORT, settings.tcpPort)
            .apply()
        _settings.value = settings
    }

    /**
     * Unlocks the developer settings screen without changing any mode flags.
     *
     * Idempotent — calling unlock() when already unlocked has no effect.
     */
    fun unlock() {
        val current = _settings.value
        if (!current.isDevUnlocked) {
            update(current.copy(isDevUnlocked = true))
        }
    }

    /**
     * Resets all developer settings to defaults and locks the developer screen.
     *
     * This also disables any active TCP/demo overrides, so the next launch uses
     * Bluetooth transport as in production.
     */
    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = DevSettings()
    }
}
