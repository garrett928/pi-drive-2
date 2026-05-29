package ghart.space.pi_drive.shared.telemetry

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Loads and persists [TelemetryConfig] to [android.content.SharedPreferences].
 *
 * [TelemetryConfig.deviceId] is auto-generated using [UUID.randomUUID] on first access and
 * stored alongside the user-configurable fields. The UUID never changes for the lifetime of
 * the app installation.
 *
 * All fields are serialized as a single JSON blob under the key [KEY_CONFIG]. This means
 * a [save] call always writes the full config — partial updates are not supported.
 */
class TelemetryConfigRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Loads the persisted [TelemetryConfig], or creates and saves a default config with a
     * freshly-generated [TelemetryConfig.deviceId] if none has been saved yet.
     */
    fun load(): TelemetryConfig {
        val stored = prefs.getString(KEY_CONFIG, null)
        return if (stored != null) {
            runCatching { json.decodeFromString<TelemetryConfig>(stored) }
                .getOrDefault(createDefault())
        } else {
            createDefault().also { save(it) }
        }
    }

    /** Persists [config] to SharedPreferences, replacing any previously saved config. */
    fun save(config: TelemetryConfig) {
        prefs.edit { putString(KEY_CONFIG, json.encodeToString(config)) }
    }

    private fun createDefault(): TelemetryConfig =
        TelemetryConfig(deviceId = UUID.randomUUID().toString())

    companion object {
        private const val PREFS_NAME = "pi_drive_telemetry_config"
        private const val KEY_CONFIG = "config_v1"
    }
}
