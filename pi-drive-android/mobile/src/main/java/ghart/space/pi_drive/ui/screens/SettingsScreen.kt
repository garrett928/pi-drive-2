package ghart.space.pi_drive.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DriveEta
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.settings.GeneralSettings
import ghart.space.pi_drive.shared.settings.SpeedUnit
import ghart.space.pi_drive.shared.settings.TemperatureUnit
import ghart.space.pi_drive.shared.telemetry.TelemetryConfig
import ghart.space.pi_drive.shared.ui.components.PDButtonSecondary
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PDRow
import ghart.space.pi_drive.shared.ui.components.PDToggle
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.shared.ui.theme.AccentOptions
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.navigation.NavRoutes
import ghart.space.pi_drive.ui.viewmodel.DevSettingsViewModel
import ghart.space.pi_drive.ui.viewmodel.SettingsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Retention period options ──────────────────────────────────────────────────

private data class RetentionOption(val label: String, val days: Int)

private val RETENTION_OPTIONS = listOf(
    RetentionOption("30 days", 30),
    RetentionOption("60 days", 60),
    RetentionOption("90 days", 90),
    RetentionOption("Unlimited", -1),
)

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

// ── Root screen ───────────────────────────────────────────────────────────────

/**
 * Settings root screen.
 *
 * Renders the vehicle card, appearance controls, and all settings section rows.
 * Delegates reads and writes to [SettingsViewModel]. Dialogs are shown inline
 * (retention picker, reset confirmation) so no separate screen navigation is needed.
 */
@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val devViewModel: DevSettingsViewModel = hiltViewModel()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val manualTripState by viewModel.manualTripState.collectAsStateWithLifecycle()
    val generalSettings by viewModel.generalSettings.collectAsStateWithLifecycle()
    val telemetryConfig by viewModel.telemetryConfig.collectAsStateWithLifecycle()
    val devSettings by devViewModel.settings.collectAsStateWithLifecycle()

    var showRetentionDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VehicleCard(
            connectionState = connectionState,
            onPairNew = { navController.navigate(NavRoutes.CONNECT_SCAN) },
        )

        AppearanceSection(
            settings = generalSettings,
            onUpdate = viewModel::updateGeneralSettings,
        )

        SettingsSection(title = "Data & Display") {
            PDRow(
                title = "Phone home layout",
                subtitle = "Choose featured metric and tile widgets",
                leadingIcon = Icons.Rounded.Home,
                onClick = { navController.navigate(NavRoutes.SETTINGS_HOME_LAYOUT) },
                trailing = { ChevronIcon() },
            )
            Divider()
            PDRow(
                title = "Android Auto layout",
                subtitle = "Dials, graphs, and split-screen widgets",
                leadingIcon = Icons.Rounded.DriveEta,
                onClick = { navController.navigate(NavRoutes.SETTINGS_AA_LAYOUT) },
                trailing = { ChevronIcon() },
            )
            Divider()
            ManualTripRow(
                state = manualTripState,
                onReset = viewModel::resetManualTrip,
            )
            Divider()
            PDRow(
                title = "Telemetry stream",
                subtitle = "${telemetryConfig.enabledSignals.size} signals · ${telemetryConfig.sampleRateHz} Hz",
                leadingIcon = Icons.Rounded.Timeline,
                onClick = { navController.navigate(NavRoutes.SETTINGS_SERVER) },
                trailing = { ChevronIcon() },
            )
        }

        SettingsSection(title = "Cloud & Server") {
            PDRow(
                title = "Telemetry server",
                subtitle = telemetryConfig.serverUrl.ifBlank { "Not configured" },
                leadingIcon = Icons.Rounded.CloudUpload,
                onClick = { navController.navigate(NavRoutes.SETTINGS_SERVER) },
                trailing = { ChevronIcon() },
            )
            Divider()
            PDRow(
                title = "Wi-Fi only uploads",
                subtitle = "Restrict uploads to Wi-Fi connections",
                leadingIcon = Icons.Rounded.Wifi,
                trailing = {
                    PDToggle(
                        checked = telemetryConfig.uploadOnWifiOnly,
                        onCheckedChange = viewModel::setWifiOnly,
                    )
                },
            )
            Divider()
            RetentionRow(
                currentDays = generalSettings.dataRetentionDays,
                onClick = { showRetentionDialog = true },
            )
        }

        SettingsSection(title = "Driving Alerts") {
            PDRow(
                title = "Thresholds",
                subtitle = "Acceleration, G-force, speed, RPM limits",
                leadingIcon = Icons.Rounded.Speed,
                onClick = { navController.navigate(NavRoutes.SETTINGS_THRESHOLDS) },
                trailing = { ChevronIcon() },
            )
            Divider()
            PDRow(
                title = "Diagnostic codes",
                subtitle = "Check Engine codes from your vehicle",
                leadingIcon = Icons.Rounded.Warning,
                trailing = {
                    Text(
                        text = "0 codes",
                        style = PiDriveTheme.typography.labelSmall,
                        color = PiDriveTheme.colors.fgDim,
                    )
                },
            )
        }

        SettingsSection(title = "App") {
            AboutRow(
                tapCount = versionTapCount,
                isDevUnlocked = devSettings.isDevUnlocked,
                onTap = {
                    if (devSettings.isDevUnlocked) {
                        navController.navigate(NavRoutes.SETTINGS_DEV)
                    } else {
                        versionTapCount++
                        if (versionTapCount >= 7) {
                            devViewModel.unlock()
                            versionTapCount = 0
                        }
                    }
                },
            )
            if (devSettings.isDevUnlocked) {
                Divider()
                PDRow(
                    title = "Developer settings",
                    subtitle = when {
                        devSettings.isDemoMode -> "Demo mode: ${devSettings.demoScenario}"
                        devSettings.isTcpMode  -> "TCP mode: ${devSettings.tcpHost}:${devSettings.tcpPort}"
                        else                   -> "Bluetooth (production)"
                    },
                    leadingIcon = Icons.Rounded.Settings,
                    onClick = { navController.navigate(NavRoutes.SETTINGS_DEV) },
                    trailing = { ChevronIcon() },
                )
            }
            Divider()
            PDRow(
                title = "Reset all settings",
                subtitle = "Restore all preferences to defaults",
                leadingIcon = Icons.Rounded.Settings,
                onClick = { showResetDialog = true },
                trailing = {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = PiDriveTheme.colors.danger,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showRetentionDialog) {
        RetentionDialog(
            currentDays = generalSettings.dataRetentionDays,
            onSelect = { days ->
                viewModel.updateGeneralSettings(generalSettings.copy(dataRetentionDays = days))
                showRetentionDialog = false
            },
            onDismiss = { showRetentionDialog = false },
        )
    }

    if (showResetDialog) {
        ResetAllDialog(
            onConfirm = {
                viewModel.resetAllSettings()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

// ── Vehicle card ──────────────────────────────────────────────────────────────

/**
 * Top card showing OBD adapter connection state, vehicle name, and the
 * "Pair a new dongle" shortcut link.
 */
@Composable
private fun VehicleCard(
    connectionState: ConnectionState,
    onPairNew: () -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography
    val isConnected = connectionState is ConnectionState.Connected

    PDCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Car icon in rounded square
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color = colors.surface2, shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DirectionsCar,
                    contentDescription = null,
                    tint = if (isConnected) colors.accent.base else colors.fgDim,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Connected -> connectionState.adapterName
                            else -> "Not connected"
                        },
                        style = type.bodyMedium,
                        color = colors.fg,
                        fontWeight = FontWeight.W600,
                    )
                    if (isConnected) {
                        PDPill(text = "LIVE", style = PillStyle.LIVE)
                    }
                }
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected ->
                            "${connectionState.protocol} · ${connectionState.pollRateHz.toInt()} Hz"
                        is ConnectionState.Connecting -> "Connecting…"
                        is ConnectionState.Error -> connectionState.message
                        else -> "Tap below to pair a dongle"
                    },
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = colors.borderS,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPairNew),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = colors.accent.base,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Pair a new dongle",
                style = type.bodySmall,
                color = colors.accent.base,
                fontWeight = FontWeight.W600,
            )
        }
    }
}

// ── Appearance section ────────────────────────────────────────────────────────

/**
 * Settings card for visual and display preferences: theme, accent color, and units.
 */
@Composable
private fun AppearanceSection(
    settings: GeneralSettings,
    onUpdate: (GeneralSettings) -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    SettingsSection(title = "Appearance") {
        // Dark / light theme toggle
        PDRow(
            title = "Dark mode",
            subtitle = if (settings.isDarkTheme) "Currently dark" else "Currently light",
            leadingIcon = Icons.Rounded.DarkMode,
            trailing = {
                PDToggle(
                    checked = settings.isDarkTheme,
                    onCheckedChange = { onUpdate(settings.copy(isDarkTheme = it)) },
                )
            },
        )
        Divider()

        // Accent color picker — 4 colored dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Palette,
                contentDescription = null,
                tint = colors.fgMuted,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Accent color", style = type.bodyMedium, color = colors.fg)
                Text(
                    text = listOf("Warm orange", "Red", "Yellow", "Blue-teal")
                        .getOrElse(settings.accentIndex) { "Warm orange" },
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentOptions.all.forEachIndexed { index, palette ->
                    val isSelected = settings.accentIndex == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 28.dp else 24.dp)
                            .clip(CircleShape)
                            .background(palette.base)
                            .then(
                                if (isSelected) Modifier.border(
                                    width = 2.dp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    shape = CircleShape,
                                ) else Modifier
                            )
                            .clickable { onUpdate(settings.copy(accentIndex = index)) },
                    )
                }
            }
        }
        Divider()

        // Speed unit toggle
        PDRow(
            title = "Speed units",
            subtitle = if (settings.speedUnit == SpeedUnit.MPH) "Miles per hour (mph)" else "Kilometres per hour (km/h)",
            leadingIcon = Icons.Rounded.Speed,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    UnitChip(
                        label = "mph",
                        selected = settings.speedUnit == SpeedUnit.MPH,
                        onClick = { onUpdate(settings.copy(speedUnit = SpeedUnit.MPH)) },
                    )
                    UnitChip(
                        label = "km/h",
                        selected = settings.speedUnit == SpeedUnit.KMH,
                        onClick = { onUpdate(settings.copy(speedUnit = SpeedUnit.KMH)) },
                    )
                }
            },
        )
        Divider()

        // Temperature unit toggle
        PDRow(
            title = "Temperature units",
            subtitle = if (settings.temperatureUnit == TemperatureUnit.FAHRENHEIT) "Fahrenheit (°F)" else "Celsius (°C)",
            leadingIcon = Icons.Rounded.Thermostat,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    UnitChip(
                        label = "°F",
                        selected = settings.temperatureUnit == TemperatureUnit.FAHRENHEIT,
                        onClick = { onUpdate(settings.copy(temperatureUnit = TemperatureUnit.FAHRENHEIT)) },
                    )
                    UnitChip(
                        label = "°C",
                        selected = settings.temperatureUnit == TemperatureUnit.CELSIUS,
                        onClick = { onUpdate(settings.copy(temperatureUnit = TemperatureUnit.CELSIUS)) },
                    )
                }
            },
        )
    }
}

// ── Manual trip row ───────────────────────────────────────────────────────────

/**
 * Inline row showing the current manual trip distance and start date, plus a Reset button.
 */
@Composable
private fun ManualTripRow(
    state: ManualTripState,
    onReset: () -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Route,
            contentDescription = null,
            tint = colors.fgMuted,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Manual trip", style = type.bodyMedium, color = colors.fg)
            val distText = "%.1f mi".format(state.distanceMiles)
            val dateText = state.startDate?.let {
                " · since ${it.format(DATE_FORMATTER)}"
            } ?: ""
            Text(
                text = distText + dateText,
                style = type.bodySmall,
                color = colors.fgMuted,
            )
        }
        PDButtonSecondary(text = "Reset", onClick = onReset)
    }
}

// ── Retention row ─────────────────────────────────────────────────────────────

@Composable
private fun RetentionRow(currentDays: Int, onClick: () -> Unit) {
    val label = RETENTION_OPTIONS.find { it.days == currentDays }?.label ?: "$currentDays days"
    PDRow(
        title = "Data retention",
        subtitle = "Keep trip history for $label",
        leadingIcon = Icons.Rounded.ContentCopy,
        onClick = onClick,
        trailing = { ChevronIcon() },
    )
}

// ── About row ────────────────────────────────────────────────────────────────

@Composable
private fun AboutRow(
    tapCount: Int,
    isDevUnlocked: Boolean,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (_: PackageManager.NameNotFoundException) {
        "—"
    }
    val subtitle = when {
        isDevUnlocked -> "Version $versionName · Developer mode"
        tapCount in 1..6 -> "Version $versionName · ${7 - tapCount} more taps to unlock developer settings"
        else -> "Version $versionName"
    }
    PDRow(
        title = "About Pi Drive",
        subtitle = subtitle,
        leadingIcon = Icons.Rounded.Info,
        onClick = onTap,
    )
}

// ── Section wrapper ───────────────────────────────────────────────────────────

/**
 * Wraps [content] in a labelled section with a header and a [PDCard] border.
 *
 * @param title   All-caps section label shown above the card.
 * @param content Rows to show inside the card (separated by [Divider] calls).
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = PiDriveTheme.colors

    Column {
        Text(
            text = title.uppercase(),
            style = PiDriveTheme.typography.labelSmall,
            color = colors.fgDim,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )

        PDCard(contentPadding = 0.dp) {
            content()
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun ChevronIcon() {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = PiDriveTheme.colors.fgDim,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = PiDriveTheme.colors.borderS,
    )
}

/**
 * Small toggleable chip used in unit-selector rows (mph/km/h, °F/°C).
 */
@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = PiDriveTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.accent.soft else colors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PiDriveTheme.typography.labelSmall,
            color = if (selected) colors.accent.base else colors.fgMuted,
            fontWeight = if (selected) FontWeight.W600 else FontWeight.Normal,
        )
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

/**
 * Dialog for choosing the data retention period.
 *
 * Presents four radio-button options (30 / 60 / 90 days / Unlimited).
 */
@Composable
private fun RetentionDialog(
    currentDays: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PiDriveTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Data retention", style = PiDriveTheme.typography.bodyMedium, color = colors.fg) },
        text = {
            Column {
                RETENTION_OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.days) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.days == currentDays,
                            onClick = { onSelect(option.days) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.accent.base,
                                unselectedColor = colors.fgDim,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option.label, style = PiDriveTheme.typography.bodyMedium, color = colors.fg)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.accent.base)
            }
        },
        containerColor = colors.surface,
        titleContentColor = colors.fg,
        textContentColor = colors.fg,
    )
}

/**
 * Confirmation dialog shown before "Reset all settings" is executed.
 *
 * Uses danger color for the confirm button to signal the destructive nature of the action.
 */
@Composable
private fun ResetAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = PiDriveTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reset all settings?",
                style = PiDriveTheme.typography.bodyMedium,
                color = colors.fg,
            )
        },
        text = {
            Text(
                text = "All preferences will be restored to their defaults. Telemetry server, VIN, and streaming configuration will not be affected.",
                style = PiDriveTheme.typography.bodySmall,
                color = colors.fgMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Reset", color = colors.danger, fontWeight = FontWeight.W600)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.fgMuted)
            }
        },
        containerColor = colors.surface,
        titleContentColor = colors.fg,
        textContentColor = colors.fg,
    )
}
