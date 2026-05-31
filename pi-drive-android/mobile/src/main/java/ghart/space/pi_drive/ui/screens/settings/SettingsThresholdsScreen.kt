package ghart.space.pi_drive.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ghart.space.pi_drive.shared.detection.DetectionConfig
import ghart.space.pi_drive.shared.detection.HealthMonitorConfig
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDRow
import ghart.space.pi_drive.shared.ui.components.PDToggle
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.viewmodel.ThresholdsViewModel
import kotlin.math.roundToInt

/**
 * Thresholds + alerts editor screen.
 *
 * Shows two detection strategy cards (Acceleration and G-Force), Speed & RPM thresholds,
 * notification preferences, and vehicle health alert settings. All changes are persisted
 * immediately via [ThresholdsViewModel] and flow through to the live detectors.
 *
 * Navigation is handled by [ghart.space.pi_drive.ui.components.PiDriveScaffold].
 */
@Composable
fun SettingsThresholdsScreen(viewModel: ThresholdsViewModel = hiltViewModel()) {
    val detection by viewModel.detectionConfig.collectAsStateWithLifecycle()
    val health by viewModel.healthMonitorConfig.collectAsStateWithLifecycle()
    val brakeCountWeek by viewModel.hardBrakeCountWeek.collectAsStateWithLifecycle()
    val accelCountWeek by viewModel.hardAccelCountWeek.collectAsStateWithLifecycle()
    val isCalibrated = viewModel.isAccelerometerCalibrated

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AccelerationStrategyCard(
            config = detection,
            brakeCountWeek = brakeCountWeek,
            accelCountWeek = accelCountWeek,
            onUpdate = viewModel::updateDetectionConfig,
        )

        GForceStrategyCard(
            config = detection,
            isCalibrated = isCalibrated,
            onUpdate = viewModel::updateDetectionConfig,
        )

        SpeedRpmSection(
            healthConfig = health,
            onUpdateHealth = viewModel::updateHealthMonitorConfig,
        )

        WhenTriggeredSection(
            config = detection,
            onUpdate = viewModel::updateDetectionConfig,
        )

        VehicleHealthSection(
            config = health,
            onUpdate = viewModel::updateHealthMonitorConfig,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Acceleration strategy card ────────────────────────────────────────────────

@Composable
private fun AccelerationStrategyCard(
    config: DetectionConfig,
    brakeCountWeek: Int,
    accelCountWeek: Int,
    onUpdate: (DetectionConfig) -> Unit,
) {
    StrategySection(
        title = "Acceleration",
        unitBadge = "mph/s",
        subtitle = "Detects hard acceleration and braking from OBD or GPS speed changes",
        enabled = config.accelEnabled,
        onToggle = { onUpdate(config.copy(accelEnabled = it)) },
    ) {
        SourceChipRow(chips = listOf("OBD speed (PID 0D)", "GPS speed"))

        AnimatedVisibility(
            visible = config.accelEnabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThresholdSliderRow(
                    label = "Hard acceleration",
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    value = config.accelHardAccelThreshold,
                    valueRange = 3f..20f,
                    format = { "%.1f mph/s".format(it) },
                    badge = if (accelCountWeek > 0) "${accelCountWeek}× this week" else null,
                    onValueChange = { onUpdate(config.copy(accelHardAccelThreshold = it)) },
                )
                ThresholdSliderRow(
                    label = "Hard braking",
                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                    value = config.accelHardBrakeThreshold,
                    valueRange = 3f..20f,
                    format = { "%.1f mph/s".format(it) },
                    badge = if (brakeCountWeek > 0) "${brakeCountWeek}× this week" else null,
                    onValueChange = { onUpdate(config.copy(accelHardBrakeThreshold = it)) },
                )
                ThresholdSliderRow(
                    label = "Min event duration",
                    icon = Icons.Rounded.Speed,
                    value = config.minEventDurationMs / 1000f,
                    valueRange = 0.2f..2.0f,
                    format = { "%.1f s".format(it) },
                    onValueChange = { onUpdate(config.copy(minEventDurationMs = (it * 1000).toLong())) },
                )
            }
        }
    }
}

// ── G-Force strategy card ─────────────────────────────────────────────────────

@Composable
private fun GForceStrategyCard(
    config: DetectionConfig,
    isCalibrated: Boolean,
    onUpdate: (DetectionConfig) -> Unit,
) {
    StrategySection(
        title = "G-Force",
        unitBadge = "g",
        subtitle = "Cross-validates 2 of 3 sources (OBD, GPS, accelerometer) to detect events",
        enabled = config.gForceEnabled,
        onToggle = { onUpdate(config.copy(gForceEnabled = it)) },
    ) {
        SourceChipRow(chips = listOf("OBD speed", "GPS speed", "TYPE_LINEAR_ACCELERATION"))

        AnimatedVisibility(
            visible = config.gForceEnabled && !isCalibrated,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            CalibrationWarningBanner()
        }

        AnimatedVisibility(
            visible = config.gForceEnabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThresholdSliderRow(
                    label = "Hard acceleration",
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    value = config.gForceHardAccelThreshold,
                    valueRange = 0.10f..0.80f,
                    format = { "%.3f g".format(it) },
                    onValueChange = { onUpdate(config.copy(gForceHardAccelThreshold = it)) },
                )
                ThresholdSliderRow(
                    label = "Hard braking",
                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                    value = config.gForceHardBrakeThreshold,
                    valueRange = 0.10f..0.80f,
                    format = { "%.3f g".format(it) },
                    onValueChange = { onUpdate(config.copy(gForceHardBrakeThreshold = it)) },
                )
                ThresholdSliderRow(
                    label = "Severe braking",
                    icon = Icons.Rounded.Warning,
                    value = config.gForceSevereBrakeThreshold,
                    valueRange = 0.30f..1.00f,
                    format = { "%.3f g".format(it) },
                    onValueChange = { onUpdate(config.copy(gForceSevereBrakeThreshold = it)) },
                )
                ThresholdSliderRow(
                    label = "Min event duration",
                    icon = Icons.Rounded.Speed,
                    value = config.minEventDurationMs / 1000f,
                    valueRange = 0.2f..2.0f,
                    format = { "%.1f s".format(it) },
                    onValueChange = { onUpdate(config.copy(minEventDurationMs = (it * 1000).toLong())) },
                )
            }
        }
    }
}

// ── Speed & RPM section ───────────────────────────────────────────────────────

@Composable
private fun SpeedRpmSection(
    healthConfig: HealthMonitorConfig,
    onUpdateHealth: (HealthMonitorConfig) -> Unit,
) {
    ThresholdsSectionWrapper(title = "Speed & RPM") {
        PDRow(
            title = "Overspeed alert",
            subtitle = if (healthConfig.overspeedEnabled)
                "Alert above ${"%.0f".format(healthConfig.overspeedThresholdMph)} mph"
            else "Disabled",
            leadingIcon = Icons.Rounded.Speed,
            trailing = {
                PDToggle(
                    checked = healthConfig.overspeedEnabled,
                    onCheckedChange = { onUpdateHealth(healthConfig.copy(overspeedEnabled = it)) },
                )
            },
        )

        AnimatedVisibility(
            visible = healthConfig.overspeedEnabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            ThresholdSliderRow(
                label = "Speed limit",
                icon = Icons.Rounded.DirectionsCar,
                value = healthConfig.overspeedThresholdMph,
                valueRange = 25f..130f,
                format = { "${"%.0f".format(it)} mph" },
                onValueChange = { onUpdateHealth(healthConfig.copy(overspeedThresholdMph = it)) },
                showDivider = false,
            )
        }

        Divider()

        ThresholdSliderRow(
            label = "High RPM alert",
            icon = Icons.Rounded.Speed,
            value = healthConfig.highRpmThreshold,
            valueRange = 3000f..9000f,
            format = { "${"%.0f".format(it / 100).toDouble().roundToInt() * 100} rpm" },
            onValueChange = { onUpdateHealth(healthConfig.copy(highRpmThreshold = it)) },
            showDivider = false,
        )
    }
}

// ── When triggered section ────────────────────────────────────────────────────

@Composable
private fun WhenTriggeredSection(
    config: DetectionConfig,
    onUpdate: (DetectionConfig) -> Unit,
) {
    val colors = PiDriveTheme.colors

    ThresholdsSectionWrapper(title = "When triggered") {
        PDRow(
            title = "Sound alert",
            subtitle = "Play a sound when an event is detected",
            leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
            trailing = {
                PDToggle(
                    checked = config.soundAlertEnabled,
                    onCheckedChange = { onUpdate(config.copy(soundAlertEnabled = it)) },
                )
            },
        )
        Divider()
        PDRow(
            title = "Haptic feedback",
            subtitle = "Vibrate on detection",
            leadingIcon = Icons.Rounded.Sensors,
            trailing = {
                PDToggle(
                    checked = config.hapticFeedbackEnabled,
                    onCheckedChange = { onUpdate(config.copy(hapticFeedbackEnabled = it)) },
                )
            },
        )
        Divider()
        PDRow(
            title = "Android Auto toast",
            subtitle = "Show notification on the head unit",
            leadingIcon = Icons.Rounded.DirectionsCar,
            trailing = {
                PDToggle(
                    checked = config.aaToastEnabled,
                    onCheckedChange = { onUpdate(config.copy(aaToastEnabled = it)) },
                )
            },
        )
        Divider()
        PDRow(
            title = "Flag event in stream",
            subtitle = "Always logged — cannot be disabled",
            leadingIcon = Icons.Rounded.Lock,
            trailing = {
                PDToggle(
                    checked = true,
                    onCheckedChange = {},
                    enabled = false,
                )
            },
        )
    }
}

// ── Vehicle health section ────────────────────────────────────────────────────

@Composable
private fun VehicleHealthSection(
    config: HealthMonitorConfig,
    onUpdate: (HealthMonitorConfig) -> Unit,
) {
    ThresholdsSectionWrapper(title = "Vehicle health alerts") {
        // High coolant
        PDRow(
            title = "High coolant temperature",
            subtitle = if (config.highCoolantEnabled)
                "Alert above ${"%.0f".format(config.highCoolantThresholdC * 9f / 5f + 32f)}°F"
            else "Disabled",
            leadingIcon = Icons.Rounded.Thermostat,
            trailing = {
                PDToggle(
                    checked = config.highCoolantEnabled,
                    onCheckedChange = { onUpdate(config.copy(highCoolantEnabled = it)) },
                )
            },
        )
        AnimatedVisibility(
            visible = config.highCoolantEnabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            // Threshold displayed in °F, stored in °C
            val thresholdF = config.highCoolantThresholdC * 9f / 5f + 32f
            ThresholdSliderRow(
                label = "Coolant threshold",
                icon = Icons.Rounded.Thermostat,
                value = thresholdF,
                valueRange = 200f..260f,
                format = { "${"%.0f".format(it)}°F" },
                onValueChange = { fahr ->
                    onUpdate(config.copy(highCoolantThresholdC = (fahr - 32f) * 5f / 9f))
                },
                showDivider = false,
            )
        }
        Divider()

        // Low fuel
        PDRow(
            title = "Low fuel",
            subtitle = if (config.lowFuelEnabled)
                "Alert below ${"%.0f".format(config.lowFuelThresholdPct)}%"
            else "Disabled",
            leadingIcon = Icons.Rounded.Info,
            trailing = {
                PDToggle(
                    checked = config.lowFuelEnabled,
                    onCheckedChange = { onUpdate(config.copy(lowFuelEnabled = it)) },
                )
            },
        )
        AnimatedVisibility(
            visible = config.lowFuelEnabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            ThresholdSliderRow(
                label = "Fuel threshold",
                icon = Icons.Rounded.Info,
                value = config.lowFuelThresholdPct,
                valueRange = 5f..25f,
                format = { "${"%.0f".format(it)}%" },
                onValueChange = { onUpdate(config.copy(lowFuelThresholdPct = it)) },
                showDivider = false,
            )
        }
        Divider()

        // Low battery
        PDRow(
            title = "Low battery voltage",
            subtitle = if (config.lowBatteryEnabled)
                "Alert below ${"%.1f".format(config.lowBatteryThresholdV)}V"
            else "Disabled",
            leadingIcon = Icons.Rounded.Warning,
            trailing = {
                PDToggle(
                    checked = config.lowBatteryEnabled,
                    onCheckedChange = { onUpdate(config.copy(lowBatteryEnabled = it)) },
                )
            },
        )
        AnimatedVisibility(
            visible = config.lowBatteryEnabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            ThresholdSliderRow(
                label = "Battery threshold",
                icon = Icons.Rounded.Warning,
                value = config.lowBatteryThresholdV,
                valueRange = 10f..13f,
                format = { "${"%.1f".format(it)}V" },
                onValueChange = { onUpdate(config.copy(lowBatteryThresholdV = it)) },
                showDivider = false,
            )
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

/**
 * Card wrapper for a labeled settings section.
 *
 * @param title   All-caps section label shown above the card.
 * @param content Row content inside the card.
 */
@Composable
private fun ThresholdsSectionWrapper(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title.uppercase(),
            style = PiDriveTheme.typography.labelSmall,
            color = PiDriveTheme.colors.fgDim,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        PDCard(contentPadding = 0.dp) {
            content()
        }
    }
}

/**
 * Labeled card for a detection strategy (Acceleration or G-Force).
 *
 * Renders the strategy header row (title, unit badge, enable toggle) followed by [content].
 *
 * @param title     Strategy name ("Acceleration", "G-Force").
 * @param unitBadge Short unit label shown as a pill next to the title.
 * @param subtitle  Brief description line shown below the title.
 * @param enabled   Current toggle state — controls the accent ring on the title row.
 * @param onToggle  Callback when the toggle is flipped.
 * @param content   Source chips and sliders shown below the header.
 */
@Composable
private fun StrategySection(
    title: String,
    unitBadge: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Column {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = type.labelSmall,
                color = if (enabled) colors.accent.base else colors.fgDim,
                fontWeight = FontWeight.W600,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (enabled) colors.accent.soft else colors.surface2)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = unitBadge,
                    style = type.labelSmall,
                    color = if (enabled) colors.accent.base else colors.fgDim,
                    fontWeight = FontWeight.W600,
                )
            }
        }

        PDCard(contentPadding = 0.dp) {
            PDRow(
                title = title,
                subtitle = subtitle,
                leadingIcon = if (title == "G-Force") Icons.Rounded.Sensors else Icons.Rounded.Speed,
                trailing = {
                    PDToggle(checked = enabled, onCheckedChange = onToggle)
                },
            )
            content()
        }
    }
}

/**
 * A labeled row containing a [Slider] with an optional contextual badge.
 *
 * @param label         Row label shown above the slider.
 * @param icon          Leading icon.
 * @param value         Current slider value.
 * @param valueRange    Allowed range for the slider.
 * @param format        Formats the current value into a display string.
 * @param badge         Optional contextual badge text (e.g., "4× this week").
 * @param showDivider   Whether to draw a bottom divider (default true).
 * @param onValueChange Called with the new value as the user drags the slider.
 */
@Composable
private fun ThresholdSliderRow(
    label: String,
    icon: ImageVector,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    badge: String? = null,
    showDivider: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.fgMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = type.bodySmall,
                color = colors.fgMuted,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.accent.soft)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badge,
                        style = type.labelSmall,
                        color = colors.accent.base,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
            Text(
                text = format(value),
                style = type.bodySmall,
                color = colors.fg,
                fontWeight = FontWeight.W600,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent.base,
                activeTrackColor = colors.accent.base,
                inactiveTrackColor = colors.surface2,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDivider) {
        Divider()
    }
}

/**
 * Row of read-only source identifier chips (OBD, GPS, accelerometer).
 *
 * Visually communicates which data sources this strategy uses.
 */
@Composable
private fun SourceChipRow(chips: List<String>) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { label ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surface2)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = type.labelSmall,
                    color = colors.fgMuted,
                )
            }
        }
    }
}

/** Warning banner shown when G-Force is enabled but the accelerometer isn't calibrated. */
@Composable
private fun CalibrationWarningBanner() {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.warn.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = colors.warn,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Calibration needed — G-Force accuracy may be reduced",
            style = type.bodySmall,
            color = colors.warn,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { /* TODO: launch calibration flow in a future phase */ }) {
            Text(
                text = "Calibrate",
                style = type.labelSmall,
                color = colors.warn,
                fontWeight = FontWeight.W600,
            )
        }
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = PiDriveTheme.colors.borderS,
    )
}
