package ghart.space.pi_drive.ui.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import kotlin.math.cos
import kotlin.math.sin

private const val DIAL_START_ANGLE = 135f
private const val DIAL_SWEEP_ANGLE = 270f
private const val TICK_COUNT = 11

/**
 * 270-degree arc gauge widget.
 *
 * Draws a background arc track and fills it from the start to the current [value].
 * Eleven evenly-spaced tick marks ring the arc. The arc turns [PiDriveColorScheme.danger]
 * when [value] exceeds [warningThreshold]. The numeric value and [unit] are centered
 * inside the arc face.
 *
 * @param value             Current metric value (clamped to [min]..[max]).
 * @param min               Minimum value at the arc start (7-o'clock position).
 * @param max               Maximum value at the arc end (5-o'clock position).
 * @param label             Short uppercase label shown above the gauge.
 * @param unit              Unit suffix shown below the centered value.
 * @param warningThreshold  If non-null and [value] >= this, arc and value turn danger red.
 * @param modifier          Applied to the outer column.
 */
@Composable
fun DialWidget(
    value: Float,
    min: Float,
    max: Float,
    label: String,
    unit: String,
    warningThreshold: Float? = null,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val fraction = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0f
    val isWarning = warningThreshold != null && value >= warningThreshold
    val arcColor = if (isWarning) colors.danger else colors.accent.base

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = type.labelSmall,
            color = colors.fgMuted,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                val strokeWidthPx = 10.dp.toPx()
                val padding = strokeWidthPx / 2 + 6.dp.toPx()
                val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
                val topLeft = Offset(padding, padding)
                val cx = size.width / 2
                val cy = size.height / 2
                val arcRadius = arcSize.width / 2

                // Background track
                drawArc(
                    color = colors.surface2,
                    startAngle = DIAL_START_ANGLE,
                    sweepAngle = DIAL_SWEEP_ANGLE,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )

                // Filled arc
                if (fraction > 0.001f) {
                    drawArc(
                        color = arcColor,
                        startAngle = DIAL_START_ANGLE,
                        sweepAngle = DIAL_SWEEP_ANGLE * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    )
                }

                // Tick marks evenly spaced along the arc
                val innerRadius = arcRadius - strokeWidthPx
                val outerRadius = arcRadius + strokeWidthPx * 0.3f
                for (i in 0 until TICK_COUNT) {
                    val tickFrac = i.toFloat() / (TICK_COUNT - 1)
                    val angleDeg = DIAL_START_ANGLE + DIAL_SWEEP_ANGLE * tickFrac
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val cosA = cos(angleRad).toFloat()
                    val sinA = sin(angleRad).toFloat()
                    drawLine(
                        color = colors.borderS,
                        start = Offset(cx + innerRadius * cosA, cy + innerRadius * sinA),
                        end = Offset(cx + outerRadius * cosA, cy + outerRadius * sinA),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }

            // Centered value + unit label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatDialValue(value),
                    style = type.metricMedium,
                    color = if (isWarning) colors.danger else colors.fg,
                )
                Text(
                    text = unit,
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
            }
        }
    }
}

private fun formatDialValue(value: Float): String = when {
    value >= 1000f -> "%,.0f".format(value)
    value < 10f   -> "%.1f".format(value)
    else          -> "%.0f".format(value)
}
