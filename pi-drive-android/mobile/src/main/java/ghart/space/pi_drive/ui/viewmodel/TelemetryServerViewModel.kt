package ghart.space.pi_drive.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ghart.space.pi_drive.shared.telemetry.TelemetryConfig
import ghart.space.pi_drive.shared.telemetry.TelemetryConfigRepository
import ghart.space.pi_drive.shared.telemetry.TelemetryUploader
import ghart.space.pi_drive.shared.telemetry.VinSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TelemetryUploader"

/**
 * UI model for the VIN subsection of the server settings screen.
 *
 * @param vin       Current VIN string (may be blank if not yet configured).
 * @param source    How the VIN was obtained — from OBD, entered manually, or not yet set.
 * @param isBlank   True when [vin] is empty or blank — used to show the upload-paused warning.
 */
data class VinState(
    val vin: String,
    val source: VinSource,
    val isBlank: Boolean,
)

/**
 * Result of a server connection test.
 *
 * Transitions: [Idle] → [Testing] → [Healthy] or [Unhealthy].
 */
sealed class HealthState {
    /** No test has been run since the screen was opened. */
    object Idle : HealthState()

    /** Test is in progress — show a loading indicator. */
    object Testing : HealthState()

    /**
     * Server responded successfully.
     * @param latencyMs Round-trip time in milliseconds.
     */
    data class Healthy(val latencyMs: Long) : HealthState()

    /**
     * Server could not be reached or returned an error.
     * @param error Human-readable failure reason.
     */
    data class Unhealthy(val error: String) : HealthState()
}

/**
 * ViewModel for the Telemetry Server settings screen.
 *
 * Loads and persists [TelemetryConfig] via [TelemetryConfigRepository] and exposes
 * observable state for all sections of the settings screen. Networking (health check,
 * last-sync fetch) is performed via [TelemetryUploader] created on demand from the
 * current config.
 */
@HiltViewModel
class TelemetryServerViewModel @Inject constructor(
    private val configRepository: TelemetryConfigRepository,
) : ViewModel() {

    /**
     * Constructor for unit tests.
     *
     * Injects a custom [uploaderFactory] so tests can supply a mock [TelemetryUploader]
     * without hitting the network. The primary `@Inject` constructor is used by Hilt.
     */
    internal constructor(
        configRepository: TelemetryConfigRepository,
        uploaderFactory: (TelemetryConfig) -> TelemetryUploader,
    ) : this(configRepository) {
        this.uploaderFactory = uploaderFactory
    }

    internal var uploaderFactory: (TelemetryConfig) -> TelemetryUploader = { cfg ->
        TelemetryUploader(
            serverUrl = cfg.serverUrl,
            apiKey = cfg.apiKey,
            deviceId = cfg.deviceId,
        )
    }

    private val _config = MutableStateFlow(configRepository.load())

    /** Full telemetry config — the single source of truth for all settings fields. */
    val config: StateFlow<TelemetryConfig> = _config.asStateFlow()

    /**
     * VIN display state — derived from [config].
     * Updated immediately on [saveVin]; also updates when [config] changes externally.
     */
    val vinState: StateFlow<VinState> = _config
        .map { c -> VinState(vin = c.vin, source = c.vinSource, isBlank = c.vin.isBlank()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _config.value.let { c ->
                VinState(vin = c.vin, source = c.vinSource, isBlank = c.vin.isBlank())
            },
        )

    private val _healthState = MutableStateFlow<HealthState>(HealthState.Idle)

    /** Result of the most recent connection test, or [HealthState.Idle] before any test. */
    val healthState: StateFlow<HealthState> = _healthState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String?>(null)

    /**
     * Server's most recently received ISO 8601 timestamp for this VIN, or null if unavailable.
     * Fetched on screen entry (when VIN and URL are both set) and after a successful upload.
     */
    val lastSyncTime: StateFlow<String?> = _lastSyncTime.asStateFlow()

    private val _urlError = MutableStateFlow<String?>(null)

    /** Validation error for the server URL field, or null when the URL is valid. */
    val urlError: StateFlow<String?> = _urlError.asStateFlow()

    init {
        val c = _config.value
        if (c.vin.isNotBlank() && c.serverUrl.isNotBlank()) {
            viewModelScope.launch { fetchLastSyncTime() }
        }
    }

    /**
     * Validates and persists [updated].
     *
     * Sets [urlError] if the URL uses HTTP instead of HTTPS (upload would fail anyway).
     * Clears [urlError] on a valid URL. No-ops on blank URL (the URL can be left empty
     * until the user is ready to configure telemetry).
     */
    fun saveConfig(updated: TelemetryConfig) {
        if (updated.serverUrl.isNotBlank() && !updated.serverUrl.startsWith("https://")) {
            _urlError.value = "Server URL must use HTTPS"
            return
        }
        _urlError.value = null
        _config.value = updated
        configRepository.save(updated)
    }

    /**
     * Saves [vin] with [VinSource.MANUAL] and persists the updated config.
     *
     * Trims whitespace from the input. If the VIN is cleared, the warning banner
     * will reappear via [vinState].
     */
    fun saveVin(vin: String) {
        val trimmed = vin.trim()
        val updated = _config.value.copy(vin = trimmed, vinSource = VinSource.MANUAL)
        _config.value = updated
        configRepository.save(updated)
    }

    /**
     * Triggers an OBD re-read of the VIN.
     *
     * In Phase 7 this is a no-op stub — OBD VIN reading is wired in Phase 2.
     * A warning is logged so it is visible during development.
     */
    fun retriggerVinDetection() {
        Log.w(TAG, "retriggerVinDetection: OBD VIN reading requires Phase 2 integration; no-op in Phase 7")
    }

    /**
     * Calls [TelemetryUploader.checkHealth] and updates [healthState] with the result and latency.
     *
     * Sets [HealthState.Unhealthy] immediately if [config] has no server URL. Otherwise
     * transitions [Idle] → [Testing] → [Healthy] or [Unhealthy].
     */
    fun testConnection() {
        val c = _config.value
        if (c.serverUrl.isBlank()) {
            _healthState.value = HealthState.Unhealthy("Server URL is not configured")
            return
        }
        viewModelScope.launch {
            _healthState.value = HealthState.Testing
            val uploader = uploaderFactory(c)
            val start = System.currentTimeMillis()
            val result = uploader.checkHealth()
            val latencyMs = System.currentTimeMillis() - start
            _healthState.value = if (result.isSuccess) {
                HealthState.Healthy(latencyMs)
            } else {
                HealthState.Unhealthy(result.exceptionOrNull()?.message ?: "Connection failed")
            }
        }
    }

    /**
     * Fetches the server's most recently received timestamp for the current VIN.
     *
     * No-op when [config] has a blank VIN or server URL. On success, updates [lastSyncTime].
     * Network failures are silently ignored — the field stays blank.
     */
    fun fetchLastSyncTime() {
        val c = _config.value
        if (c.vin.isBlank() || c.serverUrl.isBlank()) return
        viewModelScope.launch {
            val uploader = uploaderFactory(c)
            uploader.getLatestTimestamp(c.vin).onSuccess { ts ->
                _lastSyncTime.value = ts
            }
        }
    }
}
