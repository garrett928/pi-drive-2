package ghart.space.pi_drive.shared.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Pi Drive styled toggle switch.
 *
 * Wraps Material3 [Switch] with Pi Drive accent colors so toggles consistently
 * use the current accent palette rather than Material3's default teal.
 *
 * @param checked   Whether the toggle is in the "on" state.
 * @param onCheckedChange Called when the user taps the toggle.
 * @param enabled   Whether the toggle is interactive. Disabled toggles are dimmed.
 * @param modifier  Applied to the underlying [Switch].
 */
@Composable
fun PDToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.bg,
            checkedTrackColor = colors.accent.base,
            uncheckedThumbColor = colors.fgDim,
            uncheckedTrackColor = colors.surface2,
            uncheckedBorderColor = colors.border,
        ),
    )
}
