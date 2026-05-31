package ghart.space.pi_drive

import ghart.space.pi_drive.shared.data.db.entity.SnapshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [CsvExporter.toCsv] — the pure CSV generation function.
 *
 * [CsvExporter.createShareIntent] is not tested here because it performs Android I/O
 * (FileProvider, cache directory) that requires instrumentation. The serialization
 * logic in [CsvExporter.toCsv] is kept pure so it can be exercised with plain JUnit.
 */
class CsvExporterTest {

    private fun snap(
        id: Long = 1L,
        timestamp: Instant = Instant.parse("2026-05-01T10:00:00Z"),
        speedKmh: Int? = null,
        rpm: Int? = null,
        coolantTempC: Int? = null,
        throttlePct: Float? = null,
        fuelLevelPct: Float? = null,
        oilTempC: Int? = null,
        mafGps: Float? = null,
        fuelRateLph: Float? = null,
        batteryVoltage: Float? = null,
        gpsLat: Double? = null,
        gpsLng: Double? = null,
        accelRateMphS: Float? = null,
        gForce: Float? = null,
    ) = SnapshotEntity(
        id = id,
        tripId = 42L,
        timestamp = timestamp,
        speedKmh = speedKmh,
        rpm = rpm,
        coolantTempC = coolantTempC,
        intakeAirTempC = null,
        throttlePct = throttlePct,
        fuelLevelPct = fuelLevelPct,
        oilTempC = oilTempC,
        mafGps = mafGps,
        fuelRateLph = fuelRateLph,
        batteryVoltage = batteryVoltage,
        gpsLat = gpsLat,
        gpsLng = gpsLng,
        gpsSpeedMps = null,
        accelRateMphS = accelRateMphS,
        gForce = gForce,
    )

    @Test
    fun `5 snapshots produce 6 lines (header + 5 data rows)`() {
        val snapshots = (1..5).map { snap(id = it.toLong()) }
        val csv = CsvExporter.toCsv(snapshots)
        val nonEmptyLines = csv.lines().filter { it.isNotBlank() }
        assertEquals(6, nonEmptyLines.size)
    }

    @Test
    fun `first line is the expected header`() {
        val csv = CsvExporter.toCsv(listOf(snap()))
        val header = csv.lines().first()
        assertEquals(CsvExporter.CSV_HEADER, header)
    }

    @Test
    fun `header has 14 columns`() {
        val colCount = CsvExporter.CSV_HEADER.split(',').size
        assertEquals(14, colCount)
    }

    @Test
    fun `speed is converted from km_h to mph`() {
        // 100 km/h * 0.621371 = 62.14 mph
        val snap = snap(speedKmh = 100)
        val csv = CsvExporter.toCsv(listOf(snap))
        val row = csv.lines()[1]
        val cols = row.split(',')
        // speed_mph is column index 3
        assertEquals("62.14", cols[3])
    }

    @Test
    fun `coolant temp is converted from C to F`() {
        // 100°C = (100 * 9/5) + 32 = 212.0°F
        val snap = snap(coolantTempC = 100)
        val csv = CsvExporter.toCsv(listOf(snap))
        val cols = csv.lines()[1].split(',')
        // coolant_temp_f is column index 5
        assertEquals("212.0", cols[5])
    }

    @Test
    fun `null fields are emitted as empty strings`() {
        // snap with no data — all nullable fields are null
        val snap = snap()
        val csv = CsvExporter.toCsv(listOf(snap))
        val row = csv.lines()[1]
        // Count commas — 14 columns means 13 commas
        assertEquals(13, row.count { it == ',' })
        // Columns 1-13 should be empty (except timestamp in col 0 which is always set)
        val cols = row.split(',')
        assertTrue("lat should be empty", cols[1].isEmpty())
        assertTrue("lng should be empty", cols[2].isEmpty())
        assertTrue("speed_mph should be empty", cols[3].isEmpty())
    }

    @Test
    fun `timestamp is formatted as ISO-8601 UTC`() {
        val ts = Instant.parse("2026-05-25T14:30:00Z")
        val snap = snap(timestamp = ts)
        val csv = CsvExporter.toCsv(listOf(snap))
        val tsCell = csv.lines()[1].split(',')[0]
        assertEquals("2026-05-25T14:30:00Z", tsCell)
    }

    @Test
    fun `gps coordinates are preserved as-is`() {
        val snap = snap(gpsLat = 37.7749, gpsLng = -122.4194)
        val csv = CsvExporter.toCsv(listOf(snap))
        val cols = csv.lines()[1].split(',')
        assertEquals("37.7749", cols[1])
        assertEquals("-122.4194", cols[2])
    }

    @Test
    fun `oil temp is converted from C to F`() {
        // 80°C = (80 * 9/5) + 32 = 176.0°F
        val snap = snap(oilTempC = 80)
        val csv = CsvExporter.toCsv(listOf(snap))
        val cols = csv.lines()[1].split(',')
        // oil_temp_f is column index 8
        assertEquals("176.0", cols[8])
    }

    @Test
    fun `empty snapshot list produces only header line`() {
        val csv = CsvExporter.toCsv(emptyList())
        val nonEmptyLines = csv.lines().filter { it.isNotBlank() }
        assertEquals(1, nonEmptyLines.size)
        assertEquals(CsvExporter.CSV_HEADER, nonEmptyLines[0])
    }
}
