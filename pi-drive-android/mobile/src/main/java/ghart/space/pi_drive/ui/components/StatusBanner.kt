package ghart.space.pi_drive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Compact status bar shown at the bottom of the dashboard (above the bottom nav).
 *
 * Displays the current recording and sync state in a single line:
 * - When connected and active: "LIVE" pill with adapter label.
 * - When disconnected: "IDLE" neutral pill.
 *
 * Phase 6 will wire the "RECORDING" pill + elapsed time.
 * Phase 7 will wire the "QUEUED" pill + sync timestamp.
 *
 * @param connectionState  Current connection state, used to determine LIVE/IDLE label.
 * @param isRecording      True when a trip is actively being recorded. Phase 6+.
 * @param modifier         Applied to the outer row.
 */
@Composable
fun StatusBanner(
    connectionState: ConnectionState,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (connectionState) {
            is ConnectionState.Connected -> {
                if (isRecording) {
                    PDPill(text = "RECORDING", style = PillStyle.DANGER, showDot = true)
                } else {
                    PDPill(text = "LIVE", style = PillStyle.LIVE, showDot = true)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = connectionState.adapterName,
                    style = type.bodySmall,
                    color = colors.fgDim,
                )
            }
            is ConnectionState.Connecting -> {
                PDPill(text = "CONNECTING", style = PillStyle.NEUTRAL)
            }
            is ConnectionState.Error -> {
                PDPill(text = "ERROR", style = PillStyle.DANGER)
                Text(
                    text = connectionState.message,
                    style = type.bodySmall,
                    color = colors.fgDim,
                )
            }
            is ConnectionState.Disconnected -> {
                PDPill(text = "IDLE", style = PillStyle.NEUTRAL)
            }
        }
    }
}
