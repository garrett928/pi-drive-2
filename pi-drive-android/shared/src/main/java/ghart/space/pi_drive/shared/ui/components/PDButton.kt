package ghart.space.pi_drive.shared.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

// ---------------------------------------------------------------------------
// PDButton — three variants used across Pi Drive:
//   PRIMARY   → filled accent background (main CTAs: "Go to dashboard")
//   SECONDARY → outlined with accent border (secondary actions)
//   DANGER    → filled danger color (destructive: "Reset all settings")
// ---------------------------------------------------------------------------

/** Filled primary button with accent background. */
@Composable
fun PDButtonPrimary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent.base,
            contentColor = Color.Black,
            disabledContainerColor = colors.surface2,
            disabledContentColor = colors.fgDim,
        ),
    ) {
        Text(text = text, style = type.bodyMedium, fontWeight = FontWeight.W600)
    }
}

/** Outlined secondary button with accent-colored border. */
@Composable
fun PDButtonSecondary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (enabled) colors.accent.base else colors.border,
                shape = RoundedCornerShape(8.dp),
            ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (enabled) colors.accent.base else colors.fgDim,
        ),
    ) {
        Text(text = text, style = type.bodyMedium, fontWeight = FontWeight.W500)
    }
}

/** Filled danger button for destructive actions. */
@Composable
fun PDButtonDanger(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.danger,
            contentColor = Color.White,
            disabledContainerColor = colors.surface2,
            disabledContentColor = colors.fgDim,
        ),
    ) {
        Text(text = text, style = type.bodyMedium, fontWeight = FontWeight.W600)
    }
}
