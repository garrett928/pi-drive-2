package ghart.space.pi_drive.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Hero metric display showing a large numeric value with unit and label.
 *
 * Used as the featured tile on the Live Dashboard. The value renders in a 76sp
 * monospace typeface in the accent-strong color. The unit label sits baseline-
 * aligned at the bottom of the value in a smaller muted style. An uppercase metric
 * label sits above the value row.
 *
 * @param value    Pre-formatted numeric string without unit, e.g. "49" or "2,500".
 * @param unit     Unit suffix rendered next to the value, e.g. "mph" or "rpm".
 * @param label    Short metric name shown above the value, e.g. "SPEED". Uppercased automatically.
 * @param modifier Applied to the outer column.
 */
@Composable
fun FeaturedMetric(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Column(modifier = modifier) {
        // Metric label — 10sp uppercase, W600, tracked
        Text(
            text = label.uppercase(),
            style = type.labelSmall.copy(
                fontWeight = FontWeight.W600,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            ),
            color = colors.fgMuted,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Value + unit row
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = type.metricHero,
                color = colors.accent.strong,
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Unit sits at the bottom of the hero number
            Text(
                text = unit,
                style = type.metricSmall,
                color = colors.fgMuted,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}
