package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loads and persists [GeneralSettings] to [SharedPreferences].
 *
 * Exposes a hot [StateFlow] so theme, unit labels, and trip-end timeout
 * can react to changes without polling. Each field is stored individually —
 * updating one preference does not overwrite unrelated settings.
 *
 * Designed to accept [SharedPreferences] directly (rather than [android.content.Context])
 * so that unit tests can inject a fake implementation.
 *
 * @param prefs The [SharedPreferences] instance to read from and write to.
 */
class GeneralSettingsManager(private val prefs: SharedPreferences) {

    private val _settings = MutableStateFlow(load())

    /** Current settings snapshot. Emits a new value after every [update] or [reset]. */
    val settings: StateFlow<GeneralSettings> = _settings.asStateFlow()

    /**
     * Persists [settings] to SharedPreferences and emits the new value on [settings].
     *
     * The write is asynchronous ([apply]) — call [SharedPreferences.Editor.commit] directly
     * if synchronous durability is required (e.g., before process termination).
     */
    fun update(settings: GeneralSettings) {
        prefs.edit {
            putBoolean(KEY_DARK_THEME, settings.isDarkTheme)
            putInt(KEY_ACCENT_INDEX, settings.accentIndex.coerceIn(0, 3))
            putInt(KEY_SPEED_UNIT, settings.speedUnit.ordinal)
            putInt(KEY_TEMP_UNIT, settings.temperatureUnit.ordinal)
            putInt(KEY_DATA_RETENTION_DAYS, settings.dataRetentionDays)
            putInt(KEY_AUTO_TRIP_TIMEOUT, settings.autoTripEndTimeoutMinutes)
        }
        _settings.value = settings
    }

    /**
     * Clears all stored preferences and resets [settings] to the defaults defined
     * by [GeneralSettings].
     *
     * Called when the user selects "Reset all settings" from the settings root screen.
     */
    fun reset() {
        prefs.edit { clear() }
        _settings.value = GeneralSettings()
    }

    private fun load(): GeneralSettings = GeneralSettings(
        isDarkTheme = prefs.getBoolean(KEY_DARK_THEME, true),
        accentIndex = prefs.getInt(KEY_ACCENT_INDEX, 0).coerceIn(0, 3),
        speedUnit = SpeedUnit.entries.getOrElse(prefs.getInt(KEY_SPEED_UNIT, 0)) { SpeedUnit.MPH },
        temperatureUnit = TemperatureUnit.entries.getOrElse(
            prefs.getInt(KEY_TEMP_UNIT, 0)
        ) { TemperatureUnit.FAHRENHEIT },
        dataRetentionDays = prefs.getInt(KEY_DATA_RETENTION_DAYS, 90),
        autoTripEndTimeoutMinutes = prefs.getInt(KEY_AUTO_TRIP_TIMEOUT, 5),
    )

    companion object {
        /** SharedPreferences file name — used by the Hilt module to open the correct store. */
        const val PREFS_NAME = "pi_drive_general_settings"

        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_ACCENT_INDEX = "accent_index"
        private const val KEY_SPEED_UNIT = "speed_unit"
        private const val KEY_TEMP_UNIT = "temp_unit"
        private const val KEY_DATA_RETENTION_DAYS = "data_retention_days"
        private const val KEY_AUTO_TRIP_TIMEOUT = "auto_trip_timeout"
    }
}
