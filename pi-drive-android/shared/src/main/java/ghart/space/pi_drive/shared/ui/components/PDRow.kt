package ghart.space.pi_drive.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Standard settings / list row with leading icon, title, subtitle,
 * and an optional trailing composable (chevron, toggle, badge, etc.).
 *
 * Used in the settings root screen, section rows, and any list-item UI.
 *
 * @param title         Primary label (body weight).
 * @param subtitle      Secondary description in muted text. Null to omit.
 * @param leadingIcon   Icon shown to the left of the text block. Null to omit.
 * @param onClick       Called when the row is tapped. Null to make non-clickable.
 * @param trailing      Optional composable anchored to the row's end (e.g. a toggle).
 * @param modifier      Applied to the outer container.
 */
@Composable
fun PDRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.fgMuted,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.bodyMedium,
                color = colors.fg,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
            }
        }

        trailing?.invoke()
    }
}
