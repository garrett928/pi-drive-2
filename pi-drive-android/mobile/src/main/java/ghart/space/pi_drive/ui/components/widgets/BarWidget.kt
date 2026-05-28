package ghart.space.pi_drive.ui.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Horizontal fill bar widget.
 *
 * Shows a label left-aligned and the formatted value + unit right-aligned above
 * a rounded fill bar. The bar fill fraction is [value] / [max], clamped to 0–1.
 * The bar turns [PiDriveColorScheme.danger] when [value] <= [warningThreshold] (low
 * threshold, e.g. for fuel level).
 *
 * @param value             Current metric value.
 * @param max               Maximum value corresponding to a full bar.
 * @param label             Short uppercase label shown top-left.
 * @param unit              Unit suffix appended to the formatted value.
 * @param warningThreshold  If non-null, threshold for danger coloring.
 * @param warningAbove      If true, danger when value >= threshold (e.g. high temp).
 *                          If false (default), danger when value <= threshold (e.g. low fuel).
 * @param modifier          Applied to the outer column.
 */
@Composable
fun BarWidget(
    value: Float,
    max: Float,
    label: String,
    unit: String,
    warningThreshold: Float? = null,
    warningAbove: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val fraction = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f
    val isWarning = when {
        warningThreshold == null -> false
        warningAbove -> value >= warningThreshold
        else -> value <= warningThreshold
    }
    val barColor = if (isWarning) colors.danger else colors.accent.base

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label.uppercase(),
                style = type.labelSmall,
                color = colors.fgMuted,
            )
            Text(
                text = "${formatBarValue(value)} $unit",
                style = type.metricSmall,
                color = colors.fg,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(colors.surface2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(100.dp))
                    .background(barColor),
            )
        }
    }
}

private fun formatBarValue(value: Float): String = when {
    value < 10f -> "%.1f".format(value)
    else        -> "%.0f".format(value)
}
