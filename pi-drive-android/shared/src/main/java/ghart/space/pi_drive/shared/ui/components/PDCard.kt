package ghart.space.pi_drive.shared.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Pi Drive card container.
 *
 * Uses the card surface color from the current theme and draws a subtle border.
 * All metric tiles, settings rows, and info panels are wrapped in [PDCard].
 *
 * @param modifier    Applied to the outer [Surface].
 * @param elevated    If true, uses [PiDriveColorScheme.surface] (elevated);
 *                    otherwise uses [PiDriveColorScheme.bgElev] (standard card).
 * @param accentBorder When non-null, draws an accent-colored border ring instead
 *                     of the standard border. Used for enabled strategy cards.
 * @param cornerRadius Corner radius of the card. Defaults to 12dp.
 * @param contentPadding Padding applied inside the card around [content].
 * @param content     The card's child composables.
 */
@Composable
fun PDCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    cornerRadius: Dp = 12.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PiDriveTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val bgColor = if (elevated) colors.surface else colors.bgElev
    val borderColor = accentBorder ?: colors.borderS

    Surface(
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = shape),
        shape = shape,
        color = bgColor,
        tonalElevation = 0.dp, // We manage elevation via our own color system
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
