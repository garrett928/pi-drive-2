package ghart.space.pi_drive.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import kotlin.math.roundToInt

/**
 * Tappable connection status banner shown above the featured metric card.
 *
 * Renders differently for each [ConnectionState]:
 * - [ConnectionState.Connected]: BT icon, adapter name, protocol, poll rate, chevron.
 * - [ConnectionState.Disconnected] with `canRetry=false`: gray BT icon, "Not connected", chevron.
 * - [ConnectionState.Disconnected] with `canRetry=true`: spinning icon, countdown, "Reconnect now" button.
 * - [ConnectionState.Connecting]: animated spinning icon, "Connecting…".
 * - [ConnectionState.Error]: red error icon, message, retry button.
 *
 * Tapping the banner calls [onTap], which should navigate to the connect flow.
 * When the reconnect button is shown, [onReconnectNow] is called instead.
 *
 * @param connectionState Current connection state from [VehicleDataSource] or [ConnectionManager].
 * @param onTap           Invoked when the user taps the banner (navigates to connect flow).
 * @param onReconnectNow  Invoked when the user taps "Reconnect now" during auto-retry.
 * @param modifier        Applied to the outer [PDCard].
 */
@Composable
fun ConnectionBanner(
    connectionState: ConnectionState,
    onTap: () -> Unit,
    onReconnectNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PDCard(
        contentPadding = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
    ) {
        when (connectionState) {
            is ConnectionState.Connected    -> ConnectedRow(connectionState)
            is ConnectionState.Disconnected ->
                if (connectionState.canRetry) ReconnectingRow(connectionState, onReconnectNow)
                else DisconnectedRow()
            is ConnectionState.Connecting   -> ConnectingRow()
            is ConnectionState.Error        -> ErrorRow(connectionState.message, onTap)
        }
    }
}

@Composable
private fun ConnectedRow(state: ConnectionState.Connected) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(18.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.adapterName,
                style = type.bodySmall,
                color = colors.fg,
            )
            Text(
                text = "${state.protocol} · ${state.pollRateHz.roundToInt()} Hz",
                style = type.bodySmall,
                color = colors.fgDim,
            )
        }

        PDPill(text = "LIVE", style = PillStyle.LIVE, showDot = true)

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = colors.fgDim,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ReconnectingRow(state: ConnectionState.Disconnected, onReconnectNow: () -> Unit) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val infiniteTransition = rememberInfiniteTransition(label = "reconnect_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "reconnect_spin_angle",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            tint = colors.accent.base,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation),
        )
        Column(modifier = Modifier.weight(1f)) {
            val label = if (state.retryIn != null) "Reconnecting in ${state.retryIn}s…" else "Reconnecting…"
            Text(text = label, style = type.bodySmall, color = colors.fgMuted)
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onReconnectNow)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(text = "Now", style = type.bodySmall, color = colors.accent.base)
        }
    }
}

@Composable
private fun DisconnectedRow() {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.BluetoothDisabled,
            contentDescription = null,
            tint = colors.fgDim,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Not connected",
            style = type.bodySmall,
            color = colors.fgMuted,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = colors.fgDim,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ConnectingRow() {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val infiniteTransition = rememberInfiniteTransition(label = "connecting_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin_angle",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.BluetoothSearching,
            contentDescription = null,
            tint = colors.accent.base,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation),
        )
        Text(
            text = "Connecting…",
            style = type.bodySmall,
            color = colors.fgMuted,
        )
    }
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = type.bodySmall,
            color = colors.fgMuted,
            modifier = Modifier.weight(1f),
        )
        PDPill(
            text = "Retry",
            style = PillStyle.DANGER,
            modifier = Modifier.clickable(onClick = onRetry),
        )
    }
}
