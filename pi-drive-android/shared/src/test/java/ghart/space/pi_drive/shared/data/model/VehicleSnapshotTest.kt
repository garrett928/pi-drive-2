package ghart.space.pi_drive.shared.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleSnapshotTest {

    @Test
    fun `default snapshot has all metric fields null`() {
        val snapshot = VehicleSnapshot()
        assertNull(snapshot.speedKmh)
        assertNull(snapshot.rpm)
        assertNull(snapshot.coolantTempC)
        assertNull(snapshot.intakeAirTempC)
        assertNull(snapshot.throttlePct)
        assertNull(snapshot.fuelLevelPct)
        assertNull(snapshot.oilTempC)
        assertNull(snapshot.mafGps)
        assertNull(snapshot.fuelRateLph)
        assertNull(snapshot.batteryVoltage)
        assertNull(snapshot.gpsLat)
        assertNull(snapshot.gpsLng)
        assertNull(snapshot.gpsSpeedMps)
        assertNull(snapshot.accelRateMphS)
        assertNull(snapshot.gForce)
    }

    @Test
    fun `EMPTY constant also has all fields null`() {
        val snapshot = VehicleSnapshot.EMPTY
        assertNull(snapshot.speedKmh)
        assertNull(snapshot.rpm)
        assertNull(snapshot.batteryVoltage)
    }

    @Test
    fun `snapshot with values round-trips via copy`() {
        val original = VehicleSnapshot(
            speedKmh = 96,
            rpm = 2400,
            coolantTempC = 92,
            throttlePct = 45.5f,
            batteryVoltage = 14.1f,
            fuelLevelPct = 63f,
        )
        val copy = original.copy()

        assertEquals(original.speedKmh, copy.speedKmh)
        assertEquals(original.rpm, copy.rpm)
        assertEquals(original.coolantTempC, copy.coolantTempC)
        assertEquals(original.throttlePct, copy.throttlePct)
        assertEquals(original.batteryVoltage, copy.batteryVoltage)
        assertEquals(original.fuelLevelPct, copy.fuelLevelPct)
        // Unset fields remain null
        assertNull(copy.mafGps)
        assertNull(copy.oilTempC)
    }

    @Test
    fun `two snapshots with same values and same timestamp are equal`() {
        // timestamp defaults to Instant.now() — pin it so both instances are equal
        val fixedTime = java.time.Instant.ofEpochMilli(1_000_000L)
        val a = VehicleSnapshot(timestamp = fixedTime, speedKmh = 80, rpm = 2000)
        val b = VehicleSnapshot(timestamp = fixedTime, speedKmh = 80, rpm = 2000)
        assertEquals(a, b)
    }

    @Test
    fun `two snapshots with different values are not equal`() {
        val a = VehicleSnapshot(speedKmh = 80)
        val b = VehicleSnapshot(speedKmh = 100)
        assertNotEquals(a, b)
    }

    @Test
    fun `snapshot timestamp defaults to a non-null instant`() {
        val snapshot = VehicleSnapshot()
        // Timestamp must be set (defaults to Instant.now())
        requireNotNull(snapshot.timestamp)
    }

    @Test
    fun `copy with modified speed preserves other fields`() {
        val original = VehicleSnapshot(speedKmh = 60, rpm = 1800, batteryVoltage = 13.8f)
        val faster = original.copy(speedKmh = 100)

        assertEquals(100, faster.speedKmh)
        assertEquals(1800, faster.rpm)
        assertEquals(13.8f, faster.batteryVoltage)
    }
}
