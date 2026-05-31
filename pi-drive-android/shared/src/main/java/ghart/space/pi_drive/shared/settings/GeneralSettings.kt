package ghart.space.pi_drive.shared.settings

/**
 * Snapshot of all user-configurable general preferences.
 *
 * This is a value type — mutations produce a new instance, which is then
 * persisted by [GeneralSettingsManager.update]. All fields have safe defaults
 * matching the product spec.
 *
 * @param isDarkTheme              True for the dark color scheme (default), false for light.
 * @param accentIndex              Index into `AccentOptions.all` — 0 = WarmOrange (default),
 *                                 1 = Red, 2 = Yellow, 3 = BlueTeal.
 * @param speedUnit                Display unit for speed readings (default: MPH).
 * @param temperatureUnit          Display unit for temperature readings (default: Fahrenheit).
 * @param dataRetentionDays        Days of auto-trip history to keep. -1 = unlimited. Default: 90.
 * @param autoTripEndTimeoutMinutes Minutes of inactivity before an auto-trip is ended. Default: 5.
 */
data class GeneralSettings(
    val isDarkTheme: Boolean = true,
    val accentIndex: Int = 0,
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val dataRetentionDays: Int = 90,
    val autoTripEndTimeoutMinutes: Int = 5,
)

/** Display unit for speed values shown in the UI. */
enum class SpeedUnit {
    /** US customary miles per hour. */
    MPH,
    /** Metric kilometres per hour. */
    KMH,
}

/** Display unit for temperature values shown in the UI. */
enum class TemperatureUnit {
    /** Fahrenheit — US customary. */
    FAHRENHEIT,
    /** Celsius — metric. */
    CELSIUS,
}
