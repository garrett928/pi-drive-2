package ghart.space.pi_drive.ui.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * 2D crosshair g-force plot.
 *
 * Displays lateral g-force (X axis, left/right) against longitudinal g-force
 * (Y axis, forward/backward) as a dot on a crosshair grid. Grid lines are drawn
 * at 0.25g intervals. A glow halo highlights the current position.
 *
 * When g-force data is unavailable (both [lateral] and [longitudinal] are 0), the
 * dot sits at the origin (center). G-force sensor fusion is wired in Phase 5.
 *
 * @param lateral       Lateral (left/right) g-force component.
 * @param longitudinal  Longitudinal (forward/brake) g-force component.
 * @param label         Short uppercase label shown above the plot.
 * @param modifier      Applied to the outer column.
 */
@Composable
fun XYWidget(
    lateral: Float,
    longitudinal: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = type.labelSmall,
            color = colors.fgMuted,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val maxG = 1.0f
            val scale = (size.width / 2) / maxG

            // Grid lines at 0.25g intervals
            for (g in listOf(-0.75f, -0.5f, -0.25f, 0.25f, 0.5f, 0.75f)) {
                val offset = g * scale
                // Vertical
                drawLine(
                    color = colors.borderS,
                    start = Offset(cx + offset, 0f),
                    end = Offset(cx + offset, size.height),
                    strokeWidth = 0.5.dp.toPx(),
                )
                // Horizontal
                drawLine(
                    color = colors.borderS,
                    start = Offset(0f, cy + offset),
                    end = Offset(size.width, cy + offset),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }

            // Center crosshair lines
            drawLine(
                color = colors.fgDim,
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = colors.fgDim,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 1.dp.toPx(),
            )

            // Current position dot
            val dotX = (cx + lateral.coerceIn(-maxG, maxG) * scale)
            val dotY = (cy - longitudinal.coerceIn(-maxG, maxG) * scale) // flip Y: positive = forward/up

            drawCircle(
                color = colors.accent.base.copy(alpha = 0.25f),
                radius = 14.dp.toPx(),
                center = Offset(dotX, dotY),
            )
            drawCircle(
                color = colors.accent.base,
                radius = 5.dp.toPx(),
                center = Offset(dotX, dotY),
            )
        }
    }
}
