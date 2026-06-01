package ghart.space.pi_drive.ui.screens.connect

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ghart.space.pi_drive.ui.permissions.ExplainPermissionsSheet
import ghart.space.pi_drive.ui.permissions.PermissionState
import ghart.space.pi_drive.ui.permissions.PermanentlyDeniedSheet
import ghart.space.pi_drive.ui.permissions.rememberBluetoothPermissionManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.navigation.NavRoutes
import ghart.space.pi_drive.ui.viewmodel.ConnectViewModel
import ghart.space.pi_drive.ui.viewmodel.DiscoveredDevice

/**
 * Step 1 of the connect flow — scans paired Bluetooth devices and lets the user
 * pick the OBD adapter to connect to.
 *
 * In demo mode a synthetic "Demo OBD Adapter" device is shown without any
 * real Bluetooth API calls or permission requests.
 *
 * @param navController Used to navigate to [NavRoutes.CONNECT_PAIR] after device selection.
 * @param viewModel     Hilt-injected; shared with pair and done screens.
 */
@Composable
fun ConnectScanScreen(
    navController: NavController,
    viewModel: ConnectViewModel = hiltViewModel(
        remember(navController) { navController.getBackStackEntry(NavRoutes.CONNECT_GRAPH) }
    ),
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    // ── Permission handling (skipped in demo mode) ────────────────────────────
    var permissionState by remember {
        mutableStateOf<PermissionState>(PermissionState.NotRequested)
    }
    val permManager = rememberBluetoothPermissionManager { result ->
        permissionState = result
        if (result == PermissionState.Granted) viewModel.loadBondedDevices()
    }

    LaunchedEffect(Unit) {
        if (AppConfig.isDemoMode) {
            viewModel.loadBondedDevices()
            return@LaunchedEffect
        }
        val current = permManager.currentState()
        permissionState = current
        when (current) {
            PermissionState.Granted -> viewModel.loadBondedDevices()
            PermissionState.NotRequested,
            PermissionState.ShowRationale -> { /* show rationale sheet via state */ }
            PermissionState.PermanentlyDenied -> { /* show go-to-settings sheet */ }
        }
    }

    // Permission sheets shown as overlays when not in demo mode
    if (!AppConfig.isDemoMode) {
        when (permissionState) {
            PermissionState.ShowRationale, PermissionState.NotRequested -> {
                ExplainPermissionsSheet(
                    onAllow = { permManager.requestPermissions() },
                    onDismiss = { permissionState = permManager.currentState() },
                )
            }
            PermissionState.PermanentlyDenied -> {
                PermanentlyDeniedSheet(
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    },
                    onDismiss = { permissionState = permManager.currentState() },
                )
            }
            PermissionState.Granted -> { /* no overlay */ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PulsingBtAnimation()

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Select your OBD adapter",
            style = PiDriveTheme.typography.titleMedium,
            color = PiDriveTheme.colors.fg,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Paired devices are shown below.",
            style = PiDriveTheme.typography.bodySmall,
            color = PiDriveTheme.colors.fgDim,
        )

        Spacer(modifier = Modifier.height(20.dp))

        PDCard(
            contentPadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No paired devices found",
                        style = PiDriveTheme.typography.bodySmall,
                        color = PiDriveTheme.colors.fgDim,
                    )
                }
            } else {
                LazyColumn {
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(
                            device = device,
                            onClick = {
                                viewModel.selectDevice(device.address)
                                viewModel.startInitialization()
                                navController.navigate(NavRoutes.CONNECT_PAIR)
                            },
                        )
                        HorizontalDivider(
                            color = PiDriveTheme.colors.borderS,
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PairNewDeviceRow(
            onClick = {
                if (!AppConfig.isDemoMode) {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            },
        )
    }
}

// ── Private composables ──────────────────────────────────────────────────────

@Composable
private fun PulsingBtAnimation() {
    val colors = PiDriveTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "bt_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulsing halo
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .background(colors.accent.soft, CircleShape),
        )
        // Inner circle with BT icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.accent.base.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothSearching,
                contentDescription = null,
                tint = colors.accent.base,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // BT icon
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = if (device.looksLikeObd) colors.accent.base else colors.fgDim,
            modifier = Modifier.size(20.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = type.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.fg,
            )
            Text(
                text = device.address,
                style = type.bodySmall,
                color = colors.fgDim,
            )
        }

        SignalBarsWidget(bars = device.signalBars)

        if (device.looksLikeObd) {
            PDPill(text = "OBD", style = PillStyle.LIVE)
        } else {
            PDPill(text = "Paired", style = PillStyle.NEUTRAL)
        }
    }
}

@Composable
private fun SignalBarsWidget(bars: Int, maxBars: Int = 4) {
    val colors = PiDriveTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        (1..maxBars).forEach { i ->
            val height = (6 + i * 3).dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (i <= bars) colors.success else colors.border,
                    ),
            )
        }
    }
}

@Composable
private fun PairNewDeviceRow(onClick: () -> Unit) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = colors.accent.base,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Pair a new device in Bluetooth settings",
            style = type.bodySmall,
            color = colors.accent.base,
        )
    }
}
