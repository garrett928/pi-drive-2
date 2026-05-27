package ghart.space.pi_drive.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

// ---------------------------------------------------------------------------
// PDPill — status badge used throughout the dashboard and AA screens.
// Examples: "LIVE", "RECORDING", "QUEUED", unit badges like "mph/s".
// ---------------------------------------------------------------------------

/** Visual style variants for PDPill. */
enum class PillStyle {
    /** Green dot + text on success-soft background — "LIVE" state */
    LIVE,
    /** Amber on warn-soft background — "QUEUED" state */
    QUEUED,
    /** Danger color — error or alert state */
    DANGER,
    /** Accent color background — unit or category badge */
    ACCENT,
    /** Neutral surface2 background — inactive or informational */
    NEUTRAL,
}

/**
 * A compact rounded pill/badge composable.
 *
 * @param text        The uppercase label shown inside the pill.
 * @param style       Controls background and text color.
 * @param showDot     If true, a small filled circle is shown to the left of [text].
 *                    Typical for LIVE and RECORDING indicators.
 * @param modifier    Applied to the outer container.
 */
@Composable
fun PDPill(
    text: String,
    style: PillStyle = PillStyle.NEUTRAL,
    showDot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val (bgColor, textColor, dotColor) = when (style) {
        PillStyle.LIVE    -> Triple(colors.success.copy(alpha = 0.16f), colors.success, colors.success)
        PillStyle.QUEUED  -> Triple(colors.warn.copy(alpha = 0.16f), colors.warn, colors.warn)
        PillStyle.DANGER  -> Triple(colors.danger.copy(alpha = 0.16f), colors.danger, colors.danger)
        PillStyle.ACCENT  -> Triple(colors.accent.soft, colors.accent.base, colors.accent.base)
        PillStyle.NEUTRAL -> Triple(colors.surface2, colors.fgMuted, colors.fgMuted)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            text = text.uppercase(),
            style = type.labelSmall,
            color = textColor,
            fontWeight = FontWeight.W600,
        )
    }
}
