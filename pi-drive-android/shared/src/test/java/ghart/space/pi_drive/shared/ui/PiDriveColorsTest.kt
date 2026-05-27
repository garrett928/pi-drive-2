package ghart.space.pi_drive.shared.ui

import ghart.space.pi_drive.shared.ui.theme.AccentOptions
import ghart.space.pi_drive.shared.ui.theme.darkColorScheme
import ghart.space.pi_drive.shared.ui.theme.lightColorScheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that Pi Drive color tokens are correctly structured and distinct.
 * These are pure-JVM tests — no Android context required.
 */
class PiDriveColorsTest {

    @Test
    fun `all four accent palettes produce distinct base colors`() {
        val accents = AccentOptions.all
        val baseColors = accents.map { it.base }
        // Every pair should be different
        for (i in baseColors.indices) {
            for (j in (i + 1)..baseColors.lastIndex) {
                assertNotEquals(
                    "Accent $i and $j should have different base colors",
                    baseColors[i],
                    baseColors[j],
                )
            }
        }
    }

    @Test
    fun `soft variant is lower alpha than base`() {
        AccentOptions.all.forEach { accent ->
            assertTrue(
                "soft.alpha (${accent.soft.alpha}) should be less than base.alpha (${accent.base.alpha})",
                accent.soft.alpha < accent.base.alpha,
            )
        }
    }

    @Test
    fun `strong variant is different from base`() {
        AccentOptions.all.forEach { accent ->
            assertNotEquals(
                "strong should differ from base",
                accent.base,
                accent.strong,
            )
        }
    }

    @Test
    fun `dark color scheme uses dark background`() {
        val dark = darkColorScheme()
        // Dark bg luminance should be very low (near-black)
        // We check by comparing red channel — near-black has low RGB values
        assertTrue(dark.bg.red < 0.2f)
        assertTrue(dark.isDark)
    }

    @Test
    fun `light color scheme uses light background`() {
        val light = lightColorScheme()
        // Light bg should have high luminance
        assertTrue(light.bg.red > 0.9f)
        assertTrue(!light.isDark)
    }

    @Test
    fun `dark and light schemes have different backgrounds`() {
        val dark = darkColorScheme()
        val light = lightColorScheme()
        assertNotEquals(dark.bg, light.bg)
        assertNotEquals(dark.fg, light.fg)
    }

    @Test
    fun `all accent options list has exactly 4 entries`() {
        // Matches the 4 options defined in REQUIREMENTS.md section 1.1
        assertTrue(AccentOptions.all.size == 4)
    }
}
