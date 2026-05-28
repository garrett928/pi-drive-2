package ghart.space.pi_drive.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Canvas-based rolling sparkline graph.
 *
 * Renders a line graph of the most recent [data] values with an accent-colored
 * stroke, a semi-transparent gradient fill below the line, and a glowing dot at
 * the most recent (rightmost) data point.
 *
 * The Y-axis auto-scales to [data.min]..[data.max] with a 5% vertical margin on
 * each side so the line never touches the canvas edges. When min == max (all values
 * identical), the line is drawn at mid-height. Handles empty and single-point data
 * by drawing nothing.
 *
 * @param data     Rolling list of raw float values, oldest first.
 * @param modifier Applied to the canvas; defaults to full-width, 48dp tall.
 */
@Composable
fun SparklineGraph(
    data: List<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val accentColor = colors.accent.base
    val accentStrong = colors.accent.strong

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (data.size < 2) return@Canvas

        val minVal = data.min()
        val maxVal = data.max()
        val range = if (maxVal - minVal < 0.001f) 1f else maxVal - minVal
        val margin = 0.05f  // 5% vertical margin

        val stepX = size.width / (data.size - 1).toFloat()

        fun xAt(i: Int) = i * stepX
        fun yAt(v: Float): Float {
            val normalized = (v - minVal) / range
            // Flip Y (canvas Y grows downward); leave margin at top and bottom
            return size.height * (1f - margin - normalized * (1f - 2f * margin))
        }

        // Line path
        val linePath = Path().apply {
            moveTo(xAt(0), yAt(data[0]))
            for (i in 1 until data.size) {
                lineTo(xAt(i), yAt(data[i]))
            }
        }

        // Fill path — line + close down to bottom corners
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(xAt(data.size - 1), size.height)
            lineTo(xAt(0), size.height)
            close()
        }

        // Gradient fill below the line
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.28f),
                    accentColor.copy(alpha = 0.00f),
                ),
                startY = 0f,
                endY = size.height,
            ),
        )

        // Accent stroke
        drawPath(
            path = linePath,
            color = accentColor,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Current-value dot with glow halo
        val lastX = xAt(data.size - 1)
        val lastY = yAt(data.last())

        drawCircle(
            color = accentStrong.copy(alpha = 0.22f),
            radius = 10.dp.toPx(),
            center = Offset(lastX, lastY),
        )
        drawCircle(
            color = accentStrong,
            radius = 4.dp.toPx(),
            center = Offset(lastX, lastY),
        )
    }
}
