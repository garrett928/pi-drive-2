package ghart.space.pi_drive.shared.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Pi Drive typography
//
// Primary font: Geist (system-ui fallback — no bundled font file needed).
// Monospace:    Geist Mono (JetBrains Mono / ui-monospace fallback).
// All metric values (speed, RPM, etc.) use monospace with tabular-nums.
// ---------------------------------------------------------------------------

/** Sans-serif stack matching the Geist / system-ui design spec. */
val PiDriveFontFamily: FontFamily = FontFamily.SansSerif

/** Monospace stack matching Geist Mono / JetBrains Mono. */
val PiDriveMonoFontFamily: FontFamily = FontFamily.Monospace

/**
 * Complete set of text styles used across Pi Drive screens.
 *
 * Named after their *role*, not their size, so refactoring font sizes
 * doesn't require hunting down every usage site.
 */
data class PiDriveTypography(
    /**
     * Hero metric value — speed, RPM, etc. on the featured tile.
     * Large, monospace, tabular figures (76sp).
     */
    val metricHero: TextStyle = TextStyle(
        fontFamily = PiDriveMonoFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 76.sp,
        letterSpacing = (-1).sp,
        lineHeight = 80.sp,
    ),
    /**
     * Secondary metric values — dials, stat boxes (48sp).
     */
    val metricLarge: TextStyle = TextStyle(
        fontFamily = PiDriveMonoFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 48.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 52.sp,
    ),
    /**
     * Compact metric values — tiles, pills, AA split panel (24sp).
     */
    val metricMedium: TextStyle = TextStyle(
        fontFamily = PiDriveMonoFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 24.sp,
        letterSpacing = 0.sp,
        lineHeight = 28.sp,
    ),
    /**
     * Small monospace values — stat strips, badges (16sp).
     */
    val metricSmall: TextStyle = TextStyle(
        fontFamily = PiDriveMonoFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        letterSpacing = 0.sp,
        lineHeight = 20.sp,
    ),
    /** Screen and section titles (20sp semi-bold). */
    val titleLarge: TextStyle = TextStyle(
        fontFamily = PiDriveFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    /** Card and widget labels (16sp medium). */
    val titleMedium: TextStyle = TextStyle(
        fontFamily = PiDriveFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    /** Settings row subtitles, info notes (14sp regular). */
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = PiDriveFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    /** Small body text, descriptions (12sp). */
    val bodySmall: TextStyle = TextStyle(
        fontFamily = PiDriveFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    /**
     * Uppercase chip/badge labels — "LIVE", "RECORDING", "mph/s".
     * Used in PDPill and unit badges (11sp medium, tracked).
     */
    val labelSmall: TextStyle = TextStyle(
        fontFamily = PiDriveFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    ),
)

/** CompositionLocal providing the current typography set. */
val LocalPiDriveTypography = staticCompositionLocalOf { PiDriveTypography() }
