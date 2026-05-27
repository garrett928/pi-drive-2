package ghart.space.pi_drive.shared.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MetricIdTest {

    @Test
    fun `exactly 16 metric enum values exist`() {
        assertEquals(16, MetricId.entries.size)
    }

    @Test
    fun `all 16 expected metric ids are present`() {
        val expected = setOf(
            "SPEED", "MPG_INSTANT", "MPG_TRIP", "MPG_MANUAL",
            "RPM", "THROTTLE", "COOLANT", "INTAKE", "OIL_TEMP",
            "BATTERY", "FUEL", "MAF", "G_FORCE", "ACCEL",
            "DISTANCE", "MANUAL_TRIP",
        )
        val actual = MetricId.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `each metric has a non-blank display label`() {
        MetricId.entries.forEach { metric ->
            assertFalse(
                "MetricId.${metric.name} has blank displayLabel",
                metric.displayLabel.isBlank(),
            )
        }
    }

    @Test
    fun `each metric has a non-blank unit`() {
        MetricId.entries.forEach { metric ->
            assertFalse(
                "MetricId.${metric.name} has blank unit",
                metric.unit.isBlank(),
            )
        }
    }

    @Test
    fun `speed has correct label and mph unit`() {
        assertEquals("Speed", MetricId.SPEED.displayLabel)
        assertEquals("mph", MetricId.SPEED.unit)
    }

    @Test
    fun `rpm has correct label and unit`() {
        assertEquals("RPM", MetricId.RPM.displayLabel)
        assertEquals("rpm", MetricId.RPM.unit)
    }

    @Test
    fun `temperature metrics use celsius unit`() {
        val temperatureMetrics = listOf(MetricId.COOLANT, MetricId.INTAKE, MetricId.OIL_TEMP)
        temperatureMetrics.forEach { metric ->
            assertEquals(
                "MetricId.${metric.name} should use °C",
                "°C",
                metric.unit,
            )
        }
    }

    @Test
    fun `fuel economy metrics all use mpg unit`() {
        listOf(MetricId.MPG_INSTANT, MetricId.MPG_TRIP, MetricId.MPG_MANUAL).forEach { metric ->
            assertEquals("mpg", metric.unit)
        }
    }
}
