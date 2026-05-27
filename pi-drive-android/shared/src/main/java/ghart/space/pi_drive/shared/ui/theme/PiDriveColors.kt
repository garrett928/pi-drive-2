package ghart.space.pi_drive.shared.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Pi Drive color tokens — source of truth is pd-tokens.jsx (oklch space).
// Values here are the sRGB equivalents of those oklch definitions.
// Each Color definition includes the original oklch value as a comment.
// ---------------------------------------------------------------------------

// ── Dark palette ─────────────────────────────────────────────────────────────
object DarkColors {
    /** oklch(0.155 0.005 60) — near-black, slight warm tint */
    val bg         = Color(0xFF201D19)
    /** oklch(0.195 0.005 60) — card surface */
    val bgElev     = Color(0xFF2A2723)
    /** oklch(0.215 0.005 60) — elevated surface */
    val surface    = Color(0xFF302D28)
    /** oklch(0.255 0.005 60) — input bg / row hover */
    val surface2   = Color(0xFF3C3935)
    /** oklch(0.30 0.005 60) — standard border */
    val border     = Color(0xFF48453F)
    /** oklch(0.24 0.005 60) — subtle border */
    val borderS    = Color(0xFF38352F)
    /** oklch(0.97 0.005 80) — primary text */
    val fg         = Color(0xFFF7F4EF)
    /** oklch(0.70 0.005 70) — muted text */
    val fgMuted    = Color(0xFFB0ABA1)
    /** oklch(0.50 0.005 70) — dim text */
    val fgDim      = Color(0xFF7B776D)
    /** oklch(0.66 0.20 25) — danger / error */
    val danger     = Color(0xFFE05540)
    /** oklch(0.74 0.16 150) — success */
    val success    = Color(0xFF4DCA85)
    /** oklch(0.80 0.15 80) — warning */
    val warn       = Color(0xFFD4B036)
}

// ── Light palette ─────────────────────────────────────────────────────────────
object LightColors {
    /** oklch(0.985 0.003 80) */
    val bg         = Color(0xFFFAF8F5)
    /** oklch(0.97 0.003 80) */
    val bgElev     = Color(0xFFF5F2EE)
    /** oklch(1.00 0 0) */
    val surface    = Color(0xFFFFFFFF)
    /** oklch(0.955 0.004 80) */
    val surface2   = Color(0xFFF0EDE8)
    /** oklch(0.89 0.005 70) */
    val border     = Color(0xFFE0DDD7)
    /** oklch(0.93 0.005 70) */
    val borderS    = Color(0xFFECE9E4)
    /** oklch(0.18 0.005 60) */
    val fg         = Color(0xFF2C2924)
    /** oklch(0.42 0.006 60) */
    val fgMuted    = Color(0xFF6B6760)
    /** oklch(0.62 0.006 60) */
    val fgDim      = Color(0xFF9C9890)
    /** oklch(0.55 0.22 25) */
    val danger     = Color(0xFFCC3A25)
    /** oklch(0.50 0.16 150) */
    val success    = Color(0xFF2E9960)
    /** oklch(0.55 0.18 80) */
    val warn       = Color(0xFFA08020)
}

// ── Accent palettes ─────────────────────────────────────────────────────────
/**
 * One accent option (base + 16%-opacity soft + brighter strong).
 * @param base  The primary accent color used for active states, icons, highlights.
 * @param soft  16% opacity overlay used for background tints on cards/chips.
 * @param strong Brighter variant used for emphasis (e.g. featured metric value).
 */
@Immutable
data class AccentPalette(
    val base: Color,
    val soft: Color,
    val strong: Color,
)

object AccentOptions {
    /** Default — warm orange: oklch(0.72 0.17 55) */
    val WarmOrange = AccentPalette(
        base   = Color(0xFFD88A30),  // oklch(0.72 0.17 55)
        soft   = Color(0x29D88A30),  // 16% opacity
        strong = Color(0xFFE09840),  // oklch(0.78 0.18 55)
    )
    /** Red: oklch(0.65 0.21 22) */
    val Red = AccentPalette(
        base   = Color(0xFFD94040),  // oklch(0.65 0.21 22)
        soft   = Color(0x29D94040),
        strong = Color(0xFFE55050),  // oklch(0.72 0.22 22)
    )
    /** Yellow: oklch(0.80 0.16 75) */
    val Yellow = AccentPalette(
        base   = Color(0xFFD4B330),  // oklch(0.80 0.16 75)
        soft   = Color(0x29D4B330),
        strong = Color(0xFFDDBF45),  // oklch(0.85 0.17 75)
    )
    /** Blue-teal: oklch(0.78 0.13 210) */
    val BlueTeal = AccentPalette(
        base   = Color(0xFF3BBECF),  // oklch(0.78 0.13 210)
        soft   = Color(0x293BBECF),
        strong = Color(0xFF55CFDF),  // oklch(0.85 0.14 210)
    )

    val all: List<AccentPalette> = listOf(WarmOrange, Red, Yellow, BlueTeal)
}

// ── Semantic color set (assembled for each theme) ────────────────────────────
/**
 * Full resolved color set passed through [LocalPiDriveColors].
 * Components read colors from here, not from [DarkColors]/[LightColors] directly.
 */
@Immutable
data class PiDriveColorScheme(
    val bg: Color,
    val bgElev: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val borderS: Color,
    val fg: Color,
    val fgMuted: Color,
    val fgDim: Color,
    val danger: Color,
    val success: Color,
    val warn: Color,
    val accent: AccentPalette,
    val isDark: Boolean,
)

fun darkColorScheme(accent: AccentPalette = AccentOptions.WarmOrange) = PiDriveColorScheme(
    bg = DarkColors.bg, bgElev = DarkColors.bgElev,
    surface = DarkColors.surface, surface2 = DarkColors.surface2,
    border = DarkColors.border, borderS = DarkColors.borderS,
    fg = DarkColors.fg, fgMuted = DarkColors.fgMuted, fgDim = DarkColors.fgDim,
    danger = DarkColors.danger, success = DarkColors.success, warn = DarkColors.warn,
    accent = accent, isDark = true,
)

fun lightColorScheme(accent: AccentPalette = AccentOptions.WarmOrange) = PiDriveColorScheme(
    bg = LightColors.bg, bgElev = LightColors.bgElev,
    surface = LightColors.surface, surface2 = LightColors.surface2,
    border = LightColors.border, borderS = LightColors.borderS,
    fg = LightColors.fg, fgMuted = LightColors.fgMuted, fgDim = LightColors.fgDim,
    danger = LightColors.danger, success = LightColors.success, warn = LightColors.warn,
    accent = accent, isDark = false,
)

/** CompositionLocal providing the current color scheme throughout the app. */
val LocalPiDriveColors = staticCompositionLocalOf { darkColorScheme() }
