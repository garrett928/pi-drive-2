package ghart.space.pi_drive.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// PiDriveTheme
//
// Provides custom colors (via LocalPiDriveColors) and typography (via
// LocalPiDriveTypography) to the entire Compose tree.
//
// Also wraps MaterialTheme so Material3 components render with Pi Drive
// colors. Components should prefer reading from [PiDriveTheme] rather than
// [MaterialTheme] directly for pixel-perfect accuracy.
// ---------------------------------------------------------------------------

/**
 * Convenience accessor for Pi Drive's design tokens inside any composable.
 *
 * Usage:
 * ```kotlin
 * val colors = PiDriveTheme.colors
 * val type   = PiDriveTheme.typography
 * ```
 */
object PiDriveTheme {
    /** Current resolved color scheme (dark or light + accent). */
    val colors: PiDriveColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalPiDriveColors.current

    /** Current typography scale. */
    val typography: PiDriveTypography
        @Composable @ReadOnlyComposable
        get() = LocalPiDriveTypography.current
}

/**
 * Root composable theme for Pi Drive.
 *
 * @param darkTheme    Whether to use the dark color scheme. Defaults to the
 *                     system setting. Pass `false` to force light.
 * @param accent       Which of the 4 accent palettes to apply.
 * @param content      The composable subtree to theme.
 */
@Composable
fun PiDriveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentPalette = AccentOptions.WarmOrange,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkColorScheme(accent) else lightColorScheme(accent)

    // Map Pi Drive colors onto Material3's color scheme so Material components
    // (buttons, dialogs, etc.) inherit the correct palette.
    val m3Colors = if (darkTheme) {
        darkColorScheme(
            primary          = colors.accent.base,
            onPrimary        = Color.Black,
            primaryContainer = colors.accent.soft,
            background       = colors.bg,
            surface          = colors.surface,
            surfaceVariant   = colors.surface2,
            onBackground     = colors.fg,
            onSurface        = colors.fg,
            onSurfaceVariant = colors.fgMuted,
            error            = colors.danger,
            outline          = colors.border,
            outlineVariant   = colors.borderS,
        )
    } else {
        lightColorScheme(
            primary          = colors.accent.base,
            onPrimary        = Color.White,
            primaryContainer = colors.accent.soft,
            background       = colors.bg,
            surface          = colors.surface,
            surfaceVariant   = colors.surface2,
            onBackground     = colors.fg,
            onSurface        = colors.fg,
            onSurfaceVariant = colors.fgMuted,
            error            = colors.danger,
            outline          = colors.border,
            outlineVariant   = colors.borderS,
        )
    }

    CompositionLocalProvider(
        LocalPiDriveColors provides colors,
        LocalPiDriveTypography provides PiDriveTypography(),
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            content     = content,
        )
    }
}
