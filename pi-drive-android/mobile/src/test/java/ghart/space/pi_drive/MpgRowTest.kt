package ghart.space.pi_drive

import ghart.space.pi_drive.ui.components.formatMpg
import ghart.space.pi_drive.ui.components.formatMpgOrDash
import org.junit.Assert.assertEquals
import org.junit.Test

class MpgRowTest {

    @Test
    fun `formatMpg formats to exactly one decimal place`() {
        assertEquals("25.3", 25.3f.formatMpg())
        assertEquals("0.0", 0.0f.formatMpg())
        assertEquals("99.9", 99.9f.formatMpg())
        assertEquals("32.0", 32.0f.formatMpg())
    }

    @Test
    fun `formatMpg rounds correctly`() {
        assertEquals("12.4", 12.44f.formatMpg())
        // 12.46f is reliably above the midpoint after float representation
        assertEquals("12.5", 12.46f.formatMpg())
    }

    @Test
    fun `formatMpgOrDash returns em-dash for null`() {
        assertEquals("—", (null as Float?).formatMpgOrDash())
    }

    @Test
    fun `formatMpgOrDash formats non-null to one decimal`() {
        assertEquals("32.5", 32.5f.formatMpgOrDash())
        assertEquals("0.0", 0.0f.formatMpgOrDash())
    }
}
