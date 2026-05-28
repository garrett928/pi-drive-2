package ghart.space.pi_drive.ui.components.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Large centered numeric readout widget.
 *
 * Suitable for single-number metrics that don't need a gauge (e.g. battery voltage).
 * Displays an uppercase label above a large monospace value with a muted unit suffix.
 * Shows "—" when [value] is null.
 *
 * @param value    Current metric value, or null if unavailable.
 * @param label    Short uppercase label shown above the value.
 * @param unit     Unit suffix shown next to the value.
 * @param modifier Applied to the outer column.
 */
@Composable
fun NumberWidget(
    value: Float?,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = type.labelSmall,
            color = colors.fgMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (value != null) formatNumberValue(value) else "—",
                style = type.metricLarge,
                color = colors.fg,
            )
            if (value != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = type.metricSmall,
                    color = colors.fgMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

private fun formatNumberValue(value: Float): String = "%.1f".format(value)
