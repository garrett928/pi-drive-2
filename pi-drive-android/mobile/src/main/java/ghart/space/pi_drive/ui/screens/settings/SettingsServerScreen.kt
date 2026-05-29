package ghart.space.pi_drive.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.telemetry.TelemetryConfig
import ghart.space.pi_drive.shared.telemetry.VinSource
import ghart.space.pi_drive.shared.ui.components.PDButtonPrimary
import ghart.space.pi_drive.shared.ui.components.PDButtonSecondary
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PDRow
import ghart.space.pi_drive.shared.ui.components.PDToggle
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.viewmodel.HealthState
import ghart.space.pi_drive.ui.viewmodel.TelemetryServerViewModel

// ── Signal categories for the selection section ───────────────────────────────

private data class SignalGroup(val label: String, val signals: List<Pair<String, String>>)

private val SIGNAL_GROUPS = listOf(
    SignalGroup("OBD PIDs", listOf(
        "speed_kmh"        to "Speed",
        "rpm"              to "RPM",
        "coolant_temp_c"   to "Coolant Temp",
        "intake_air_temp_c" to "Intake Air Temp",
        "throttle_pct"     to "Throttle",
        "fuel_level_pct"   to "Fuel Level",
        "oil_temp_c"       to "Oil Temp",
        "maf_gps"          to "MAF",
        "fuel_rate_lph"    to "Fuel Rate",
        "battery_voltage"  to "Battery",
    )),
    SignalGroup("Calculated", listOf(
        "fuel_economy_mpg" to "MPG",
        "fuel_economy_kml" to "km/L",
    )),
    SignalGroup("Phone Sensors", listOf(
        "location"   to "Location",
        "accel_mps2" to "G-Force",
    )),
    SignalGroup("Events", listOf(
        "events" to "Driving Events",
    )),
)

private val SAMPLE_RATE_PRESETS = listOf(1, 5, 10, 30, 60)

// ── Tail-masking visual transformation for the API key field ─────────────────

/**
 * Replaces all but the last [visibleTail] characters with bullet characters.
 * Used to partially obscure the API key while showing enough to verify which key is set.
 */
private class TailMaskTransformation(private val visibleTail: Int = 4) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val masked = if (text.length <= visibleTail) {
            text.text
        } else {
            "•".repeat(text.length - visibleTail) + text.text.takeLast(visibleTail)
        }
        return TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Telemetry server configuration screen.
 *
 * Sections: Vehicle (VIN), Endpoint (URL / device ID / API key), Connection health,
 * Streaming toggles, Sample rate slider, Signal selection chips.
 *
 * Navigation is handled by [PiDriveScaffold] (top bar with back arrow is already provided).
 */
@Composable
fun SettingsServerScreen(navController: NavController) {
    val viewModel: TelemetryServerViewModel = hiltViewModel()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val vinState by viewModel.vinState.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val urlError by viewModel.urlError.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (vinState.isBlank) {
            VinWarningBanner()
        }

        VehicleSection(config = config, vinSource = vinState.source, vin = vinState.vin, viewModel = viewModel)
        EndpointSection(config = config, urlError = urlError, viewModel = viewModel)
        HealthCard(config = config, healthState = healthState, lastSyncTime = lastSyncTime, viewModel = viewModel)
        StreamingTogglesSection(config = config, viewModel = viewModel)
        SampleRateSection(config = config, viewModel = viewModel)
        SignalSelectionSection(config = config, viewModel = viewModel)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Warning banner ────────────────────────────────────────────────────────────

@Composable
private fun VinWarningBanner() {
    val colors = PiDriveTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.warn.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = colors.warn,
            modifier = Modifier.size(18.dp).padding(top = 1.dp),
        )
        Text(
            text = "Uploads paused — VIN required. Connect your OBD adapter or enter the VIN manually.",
            style = PiDriveTheme.typography.bodySmall,
            color = colors.warn,
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = PiDriveTheme.typography.labelSmall,
        color = PiDriveTheme.colors.fgDim,
        fontWeight = FontWeight.W600,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

// ── Vehicle section ───────────────────────────────────────────────────────────

@Composable
private fun VehicleSection(
    config: TelemetryConfig,
    vinSource: VinSource,
    vin: String,
    viewModel: TelemetryServerViewModel,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    PDCard {
        SectionHeader("Vehicle")
        Spacer(Modifier.height(8.dp))

        when (vinSource) {
            VinSource.AUTO_OBD -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PDPill(text = "Detected from OBD", style = PillStyle.LIVE)
                    Text(
                        text = vin,
                        style = type.bodyMedium,
                        color = colors.fg,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            VinSource.MANUAL, VinSource.NONE -> {
                var vinInput by remember(vin) { mutableStateOf(vin) }
                PiDriveTextField(
                    value = vinInput,
                    onValueChange = { vinInput = it.uppercase() },
                    label = "Vehicle Identification Number",
                    placeholder = "Enter VIN manually",
                    onFocusLost = {
                        if (vinInput != vin) viewModel.saveVin(vinInput)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PDButtonSecondary(
            text = "Re-detect from OBD",
            onClick = { viewModel.retriggerVinDetection() },
            enabled = false, // requires OBD connection — wired in Phase 2
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Endpoint section ──────────────────────────────────────────────────────────

@Composable
private fun EndpointSection(
    config: TelemetryConfig,
    urlError: String?,
    viewModel: TelemetryServerViewModel,
) {
    val colors = PiDriveTheme.colors
    val clipboard = LocalClipboardManager.current

    PDCard {
        SectionHeader("Endpoint")
        Spacer(Modifier.height(8.dp))

        var serverUrlInput by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
        PiDriveTextField(
            value = serverUrlInput,
            onValueChange = { serverUrlInput = it },
            label = "Server URL",
            placeholder = "https://telemetry.example.com",
            onFocusLost = {
                if (serverUrlInput != config.serverUrl) {
                    viewModel.saveConfig(config.copy(serverUrl = serverUrlInput))
                }
            },
            isError = urlError != null,
            errorMessage = urlError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
        )

        Spacer(Modifier.height(12.dp))

        // Device ID — read-only, copyable
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Device ID",
                    style = PiDriveTheme.typography.labelSmall,
                    color = colors.fgDim,
                )
                Text(
                    text = config.deviceId,
                    style = PiDriveTheme.typography.bodySmall,
                    color = colors.fgMuted,
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(config.deviceId)) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy device ID",
                    tint = colors.fgDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // API key — tail-masked with show/hide toggle
        var apiKeyInput by remember(config.apiKey) { mutableStateOf(config.apiKey) }
        var apiKeyVisible by remember { mutableStateOf(false) }
        val isVerified = false // derived from healthState in the parent; TODO: pass down

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("API Key", style = PiDriveTheme.typography.bodySmall) },
            placeholder = {
                Text(
                    text = "Optional — leave blank for unauthenticated servers",
                    style = PiDriveTheme.typography.bodySmall,
                    color = colors.fgDim,
                )
            },
            visualTransformation = if (apiKeyVisible || apiKeyInput.isBlank()) {
                VisualTransformation.None
            } else {
                TailMaskTransformation()
            },
            trailingIcon = {
                if (apiKeyInput.isNotBlank()) {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                            tint = colors.fgDim,
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && apiKeyInput != config.apiKey) {
                        viewModel.saveConfig(config.copy(apiKey = apiKeyInput))
                    }
                },
            colors = piDriveTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            textStyle = PiDriveTheme.typography.bodyMedium.copy(
                color = colors.fg,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

// ── Health card ───────────────────────────────────────────────────────────────

@Composable
private fun HealthCard(
    config: TelemetryConfig,
    healthState: HealthState,
    lastSyncTime: String?,
    viewModel: TelemetryServerViewModel,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    PDCard {
        SectionHeader("Connection")
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (healthState) {
                    HealthState.Idle -> {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            tint = colors.fgDim,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Not tested",
                            style = type.bodyMedium,
                            color = colors.fgMuted,
                        )
                    }
                    HealthState.Testing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.accent.base,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Testing...",
                            style = type.bodyMedium,
                            color = colors.fgMuted,
                        )
                    }
                    is HealthState.Healthy -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Connected · ${healthState.latencyMs} ms",
                            style = type.bodyMedium,
                            color = colors.success,
                        )
                    }
                    is HealthState.Unhealthy -> {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Failed",
                            style = type.bodyMedium,
                            color = colors.danger,
                        )
                    }
                }
            }

            PDButtonSecondary(
                text = "Test",
                onClick = { viewModel.testConnection() },
                enabled = healthState !is HealthState.Testing && config.serverUrl.isNotBlank(),
            )
        }

        if (healthState is HealthState.Unhealthy) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = healthState.error,
                style = type.bodySmall,
                color = colors.danger,
            )
        }

        if (lastSyncTime != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Last synced",
                    style = type.bodySmall,
                    color = colors.fgDim,
                )
                Text(
                    text = formatTimeSince(lastSyncTime),
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
            }
        } else if (config.vin.isNotBlank() && config.serverUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No data synced yet for this VIN",
                style = type.bodySmall,
                color = colors.fgDim,
            )
        }
    }
}

// ── Streaming toggles ─────────────────────────────────────────────────────────

@Composable
private fun StreamingTogglesSection(
    config: TelemetryConfig,
    viewModel: TelemetryServerViewModel,
) {
    PDCard {
        SectionHeader("Streaming")
        Spacer(Modifier.height(4.dp))

        PDRow(
            title = "Stream while driving",
            subtitle = "Upload data in real time during active trips",
            trailing = {
                PDToggle(
                    checked = config.streamWhileDriving,
                    onCheckedChange = { viewModel.saveConfig(config.copy(streamWhileDriving = it)) },
                )
            },
        )
        PDRow(
            title = "Buffer when offline",
            subtitle = "Queue failed uploads for retry when connectivity returns",
            trailing = {
                PDToggle(
                    checked = config.bufferWhenOffline,
                    onCheckedChange = { viewModel.saveConfig(config.copy(bufferWhenOffline = it)) },
                )
            },
        )
        PDRow(
            title = "Wi-Fi only",
            subtitle = "Restrict uploads to Wi-Fi connections",
            trailing = {
                PDToggle(
                    checked = config.uploadOnWifiOnly,
                    onCheckedChange = { viewModel.saveConfig(config.copy(uploadOnWifiOnly = it)) },
                )
            },
        )
        PDRow(
            title = "Compress payloads",
            subtitle = "Reduce data usage with gzip compression",
            trailing = {
                PDToggle(
                    checked = config.compressPayloads,
                    onCheckedChange = { viewModel.saveConfig(config.copy(compressPayloads = it)) },
                )
            },
        )
    }
}

// ── Sample rate ───────────────────────────────────────────────────────────────

@Composable
private fun SampleRateSection(
    config: TelemetryConfig,
    viewModel: TelemetryServerViewModel,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography
    var sliderValue by remember(config.sampleRateHz) { mutableFloatStateOf(config.sampleRateHz.toFloat()) }

    PDCard {
        SectionHeader("Sample Rate")
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${sliderValue.toInt()} Hz",
                style = type.bodyMedium,
                color = colors.fg,
                fontWeight = FontWeight.W600,
            )
            Text(
                text = if (sliderValue.toInt() >= 30) "High" else if (sliderValue.toInt() >= 10) "Medium" else "Low",
                style = type.bodySmall,
                color = colors.fgMuted,
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                viewModel.saveConfig(config.copy(sampleRateHz = sliderValue.toInt()))
            },
            valueRange = 1f..60f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent.base,
                activeTrackColor = colors.accent.base,
                inactiveTrackColor = colors.surface2,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SAMPLE_RATE_PRESETS.forEach { preset ->
                val isSelected = config.sampleRateHz == preset
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) colors.accent.soft else colors.surface2,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable {
                            sliderValue = preset.toFloat()
                            viewModel.saveConfig(config.copy(sampleRateHz = preset))
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "$preset Hz",
                        style = type.labelSmall,
                        color = if (isSelected) colors.accent.base else colors.fgMuted,
                        fontWeight = if (isSelected) FontWeight.W600 else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Signal selection ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalSelectionSection(
    config: TelemetryConfig,
    viewModel: TelemetryServerViewModel,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    PDCard {
        SectionHeader("Signal Selection")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${config.enabledSignals.size} of ${TelemetryConfig.ALL_SIGNALS.size} signals enabled",
            style = type.bodySmall,
            color = colors.fgMuted,
        )
        Spacer(Modifier.height(12.dp))

        SIGNAL_GROUPS.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Spacer(Modifier.height(12.dp))

            Text(
                text = group.label,
                style = type.labelSmall,
                color = colors.fgDim,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.signals.forEach { (signalId, label) ->
                    val enabled = signalId in config.enabledSignals
                    FilterChip(
                        selected = enabled,
                        onClick = {
                            val updated = if (enabled) {
                                config.enabledSignals - signalId
                            } else {
                                config.enabledSignals + signalId
                            }
                            viewModel.saveConfig(config.copy(enabledSignals = updated))
                        },
                        label = {
                            Text(
                                text = label,
                                style = type.labelSmall,
                                fontWeight = if (enabled) FontWeight.W600 else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent.soft,
                            selectedLabelColor = colors.accent.base,
                            labelColor = colors.fgMuted,
                            containerColor = colors.surface2,
                            selectedLeadingIconColor = colors.accent.base,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = enabled,
                            borderColor = colors.border,
                            selectedBorderColor = colors.accent.base.copy(alpha = 0.4f),
                        ),
                    )
                }
            }
        }
    }
}

// ── Shared text field helpers ─────────────────────────────────────────────────

@Composable
private fun PiDriveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, style = type.bodySmall) },
            placeholder = { Text(placeholder, style = type.bodySmall, color = colors.fgDim) },
            singleLine = singleLine,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) onFocusLost() },
            colors = piDriveTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            textStyle = type.bodyMedium.copy(color = colors.fg),
        )
        if (isError && errorMessage != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = type.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun piDriveTextFieldColors() = run {
    val colors = PiDriveTheme.colors
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.fg,
        unfocusedTextColor = colors.fg,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.bgElev,
        focusedBorderColor = colors.accent.base,
        unfocusedBorderColor = colors.border,
        errorBorderColor = colors.danger,
        focusedLabelColor = colors.accent.base,
        unfocusedLabelColor = colors.fgDim,
        cursorColor = colors.accent.base,
    )
}

// ── Utility ───────────────────────────────────────────────────────────────────

/** Formats an ISO 8601 timestamp as a human-readable relative time (e.g., "2h ago"). */
private fun formatTimeSince(isoTimestamp: String): String = try {
    val instant = java.time.Instant.parse(isoTimestamp)
    val diffSeconds = java.time.Duration.between(instant, java.time.Instant.now()).seconds
    when {
        diffSeconds < 60    -> "Just now"
        diffSeconds < 3600  -> "${diffSeconds / 60}m ago"
        diffSeconds < 86400 -> "${diffSeconds / 3600}h ago"
        else                -> "${diffSeconds / 86400}d ago"
    }
} catch (_: Exception) {
    isoTimestamp
}
