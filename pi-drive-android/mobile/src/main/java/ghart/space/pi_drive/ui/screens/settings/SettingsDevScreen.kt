package ghart.space.pi_drive.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.shared.settings.DevSettingsManager
import ghart.space.pi_drive.shared.ui.components.PDButtonSecondary
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDRow
import ghart.space.pi_drive.shared.ui.components.PDToggle
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.viewmodel.DevSettingsViewModel

/**
 * Developer settings screen, accessible after tapping the version row 7 times in Settings.
 *
 * Allows switching between TCP emulator mode, demo mode (with scenario picker), and
 * Bluetooth production mode without re-launching via adb commands. Changes take effect
 * on the **next** app launch since the transport is selected once during Hilt injection.
 *
 * @param navController Used for the back arrow in the top app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDevScreen(navController: NavController) {
    val viewModel: DevSettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    var tcpHostInput by remember(settings.tcpHost) { mutableStateOf(settings.tcpHost) }
    var tcpPortInput by remember(settings.tcpPort) { mutableStateOf(settings.tcpPort.toString()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Developer settings",
                    style = type.bodyMedium,
                    fontWeight = FontWeight.W600,
                    color = colors.fg,
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.fg,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RestartBanner()

            DemoModeSection(settings = settings, viewModel = viewModel)

            TcpModeSection(
                settings = settings,
                tcpHostInput = tcpHostInput,
                tcpPortInput = tcpPortInput,
                onHostChange = { tcpHostInput = it },
                onPortChange = { tcpPortInput = it },
                onSaveHost = {
                    val trimmed = tcpHostInput.trim().ifBlank { DevSettingsManager.DEFAULT_TCP_HOST }
                    viewModel.update(settings.copy(tcpHost = trimmed))
                    tcpHostInput = trimmed
                },
                onSavePort = {
                    val port = tcpPortInput.trim().toIntOrNull()
                        ?.coerceIn(1024, 65535)
                        ?: DevSettingsManager.DEFAULT_TCP_PORT
                    viewModel.update(settings.copy(tcpPort = port))
                    tcpPortInput = port.toString()
                },
                onToggle = { enabled ->
                    viewModel.update(
                        settings.copy(
                            isTcpMode = enabled,
                            isDemoMode = if (enabled) false else settings.isDemoMode,
                        )
                    )
                },
            )

            ActiveModeCard(settings)

            PDButtonSecondary(
                text = "Reset developer settings",
                onClick = viewModel::reset,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Restart banner ────────────────────────────────────────────────────────────

@Composable
private fun RestartBanner() {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    PDCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = "Changes take effect on restart",
                    style = type.bodySmall,
                    fontWeight = FontWeight.W600,
                    color = colors.danger,
                )
                Text(
                    text = "The transport is selected once at launch. Force-close and reopen the app after changing mode.",
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
            }
        }
    }
}

// ── Demo mode section ─────────────────────────────────────────────────────────

@Composable
private fun DemoModeSection(
    settings: DevSettingsManager.DevSettings,
    viewModel: DevSettingsViewModel,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    DevSection(title = "Demo mode") {
        PDRow(
            title = "Enable demo mode",
            subtitle = "Use scripted vehicle data instead of OBD",
            leadingIcon = Icons.Rounded.PlayArrow,
            trailing = {
                PDToggle(
                    checked = settings.isDemoMode,
                    onCheckedChange = { enabled ->
                        viewModel.update(
                            settings.copy(
                                isDemoMode = enabled,
                                isTcpMode = if (enabled) false else settings.isTcpMode,
                            )
                        )
                    },
                )
            },
        )
        if (settings.isDemoMode) {
            DevDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Scenario",
                    style = type.bodySmall,
                    fontWeight = FontWeight.W600,
                    color = colors.fgMuted,
                )
                ScenarioChips(
                    selected = settings.demoScenario,
                    onSelect = { scenario ->
                        viewModel.update(settings.copy(demoScenario = scenario.name))
                    },
                )
            }
        }
    }
}

@Composable
private fun ScenarioChips(selected: String, onSelect: (DemoScenario) -> Unit) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DemoScenario.entries.forEach { scenario ->
            val isSelected = scenario.name == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.accent.soft else colors.surface2)
                    .clickable { onSelect(scenario) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = scenario.name,
                    style = type.labelSmall,
                    color = if (isSelected) colors.accent.base else colors.fgMuted,
                    fontWeight = if (isSelected) FontWeight.W600 else FontWeight.Normal,
                )
            }
        }
    }
}

// ── TCP mode section ──────────────────────────────────────────────────────────

@Composable
private fun TcpModeSection(
    settings: DevSettingsManager.DevSettings,
    tcpHostInput: String,
    tcpPortInput: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSaveHost: () -> Unit,
    onSavePort: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    DevSection(title = "TCP / ELM327 emulator") {
        PDRow(
            title = "Enable TCP mode",
            subtitle = "Connect to ELM327-emulator over TCP",
            leadingIcon = Icons.Rounded.Lan,
            trailing = {
                PDToggle(
                    checked = settings.isTcpMode,
                    onCheckedChange = onToggle,
                )
            },
        )
        if (settings.isTcpMode) {
            DevDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "10.0.2.2 = localhost from Android emulator",
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
                OutlinedTextField(
                    value = tcpHostInput,
                    onValueChange = onHostChange,
                    label = { Text("Host", style = type.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = devTextFieldColors(),
                    trailingIcon = {
                        if (tcpHostInput.trim() != settings.tcpHost) {
                            TextButton(onClick = onSaveHost) {
                                Text("Save", style = type.labelSmall, color = colors.accent.base)
                            }
                        }
                    },
                )
                OutlinedTextField(
                    value = tcpPortInput,
                    onValueChange = onPortChange,
                    label = { Text("Port (1024–65535)", style = type.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = devTextFieldColors(),
                    trailingIcon = {
                        if (tcpPortInput.trim().toIntOrNull() != settings.tcpPort) {
                            TextButton(onClick = onSavePort) {
                                Text("Save", style = type.labelSmall, color = colors.accent.base)
                            }
                        }
                    },
                )
                Text(
                    text = "Launch emulator:\npython3 -m elm -n ${settings.tcpPort} -s car",
                    style = type.bodySmall,
                    color = colors.fgDim,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "ADB bridge:\nadb reverse tcp:${settings.tcpPort} tcp:${settings.tcpPort}",
                    style = type.bodySmall,
                    color = colors.fgDim,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Active mode card ──────────────────────────────────────────────────────────

@Composable
private fun ActiveModeCard(settings: DevSettingsManager.DevSettings) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val (modeLabel, modeDetail) = when {
        settings.isDemoMode -> "Demo mode" to "Scenario: ${settings.demoScenario}"
        settings.isTcpMode -> "TCP mode" to "${settings.tcpHost}:${settings.tcpPort}"
        else -> "Bluetooth (production)" to "Using real OBD adapter via Bluetooth"
    }

    PDCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.BugReport,
                contentDescription = null,
                tint = if (settings.isDemoMode || settings.isTcpMode) colors.accent.base else colors.fgDim,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = "Active on next launch: $modeLabel",
                    style = type.bodySmall,
                    fontWeight = FontWeight.W600,
                    color = colors.fg,
                )
                Text(
                    text = modeDetail,
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
            }
        }
    }
}

// ── Section wrapper ───────────────────────────────────────────────────────────

@Composable
private fun DevSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = PiDriveTheme.typography.labelSmall,
            color = PiDriveTheme.colors.fgDim,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        PDCard(contentPadding = 0.dp) { content() }
    }
}

@Composable
private fun DevDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = PiDriveTheme.colors.borderS,
    )
}

@Composable
private fun devTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PiDriveTheme.colors.accent.base,
    unfocusedBorderColor = PiDriveTheme.colors.borderS,
    focusedLabelColor = PiDriveTheme.colors.accent.base,
    unfocusedLabelColor = PiDriveTheme.colors.fgMuted,
    focusedTextColor = PiDriveTheme.colors.fg,
    unfocusedTextColor = PiDriveTheme.colors.fg,
    cursorColor = PiDriveTheme.colors.accent.base,
)
