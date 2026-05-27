package ghart.space.pi_drive.shared.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class PidDecoderTest {

    // ── Speed (PID 0x0D) ──────────────────────────────────────────────────

    @Test
    fun `decodeSpeed 0x50 returns 80 kmh`() {
        assertEquals(80, PidDecoder.decodeSpeed(byteArrayOf(0x50.toByte())))
    }

    @Test
    fun `decodeSpeed 0x00 returns 0`() {
        assertEquals(0, PidDecoder.decodeSpeed(byteArrayOf(0x00)))
    }

    @Test
    fun `decodeSpeed 0xFF returns 255`() {
        assertEquals(255, PidDecoder.decodeSpeed(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `decodeSpeed empty bytes returns null`() {
        assertNull(PidDecoder.decodeSpeed(byteArrayOf()))
    }

    // ── RPM (PID 0x0C) ───────────────────────────────────────────────────

    @Test
    fun `decodeRpm 0x1A 0xF8 returns 1726`() {
        assertEquals(1726, PidDecoder.decodeRpm(byteArrayOf(0x1A, 0xF8.toByte())))
    }

    @Test
    fun `decodeRpm 0x00 0x00 returns 0`() {
        assertEquals(0, PidDecoder.decodeRpm(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `decodeRpm single byte returns null`() {
        assertNull(PidDecoder.decodeRpm(byteArrayOf(0x1A)))
    }

    @Test
    fun `decodeRpm empty bytes returns null`() {
        assertNull(PidDecoder.decodeRpm(byteArrayOf()))
    }

    // ── Coolant temp (PID 0x05) ───────────────────────────────────────────

    @Test
    fun `decodeCoolantTemp 0x82 returns 90C`() {
        // 0x82 = 130; 130 - 40 = 90
        assertEquals(90, PidDecoder.decodeCoolantTemp(byteArrayOf(0x82.toByte())))
    }

    @Test
    fun `decodeCoolantTemp 0x28 returns 0C`() {
        // 0x28 = 40; 40 - 40 = 0
        assertEquals(0, PidDecoder.decodeCoolantTemp(byteArrayOf(0x28)))
    }

    @Test
    fun `decodeCoolantTemp 0x00 returns minus 40C`() {
        assertEquals(-40, PidDecoder.decodeCoolantTemp(byteArrayOf(0x00)))
    }

    @Test
    fun `decodeCoolantTemp empty bytes returns null`() {
        assertNull(PidDecoder.decodeCoolantTemp(byteArrayOf()))
    }

    // ── Throttle (PID 0x11) ───────────────────────────────────────────────

    @Test
    fun `decodeThrottle 0x00 returns 0 percent`() {
        assertEquals(0f, PidDecoder.decodeThrottle(byteArrayOf(0x00))!!, 0.1f)
    }

    @Test
    fun `decodeThrottle 0xFF returns 100 percent`() {
        assertEquals(100f, PidDecoder.decodeThrottle(byteArrayOf(0xFF.toByte()))!!, 0.1f)
    }

    @Test
    fun `decodeThrottle 0x80 returns approximately 50 percent`() {
        val result = PidDecoder.decodeThrottle(byteArrayOf(0x80.toByte()))!!
        assertEquals(50f, result, 1f)
    }

    @Test
    fun `decodeThrottle empty bytes returns null`() {
        assertNull(PidDecoder.decodeThrottle(byteArrayOf()))
    }

    // ── Fuel level (PID 0x2F) ─────────────────────────────────────────────

    @Test
    fun `decodeFuelLevel 0xFF returns 100 percent`() {
        assertEquals(100f, PidDecoder.decodeFuelLevel(byteArrayOf(0xFF.toByte()))!!, 0.1f)
    }

    @Test
    fun `decodeFuelLevel empty bytes returns null`() {
        assertNull(PidDecoder.decodeFuelLevel(byteArrayOf()))
    }

    // ── MAF (PID 0x10) ───────────────────────────────────────────────────

    @Test
    fun `decodeMaf two bytes returns correct gps`() {
        // ((0x03 * 256) + 0x4C) / 100 = (768 + 76) / 100 = 844 / 100 = 8.44 g/s
        val result = PidDecoder.decodeMaf(byteArrayOf(0x03, 0x4C))!!
        assertEquals(8.44f, result, 0.01f)
    }

    @Test
    fun `decodeMaf single byte returns null`() {
        assertNull(PidDecoder.decodeMaf(byteArrayOf(0x03)))
    }

    // ── Oil temp (PID 0x5C) ───────────────────────────────────────────────

    @Test
    fun `decodeOilTemp 0x82 returns 90C`() {
        assertEquals(90, PidDecoder.decodeOilTemp(byteArrayOf(0x82.toByte())))
    }

    @Test
    fun `decodeOilTemp empty bytes returns null`() {
        assertNull(PidDecoder.decodeOilTemp(byteArrayOf()))
    }

    // ── Fuel rate (PID 0x5E) ──────────────────────────────────────────────

    @Test
    fun `decodeFuelRate returns correct lph`() {
        // ((0x00 * 256) + 0x78) / 20 = 120 / 20 = 6.0 L/h
        assertEquals(6.0f, PidDecoder.decodeFuelRate(byteArrayOf(0x00, 0x78))!!, 0.01f)
    }

    @Test
    fun `decodeFuelRate single byte returns null`() {
        assertNull(PidDecoder.decodeFuelRate(byteArrayOf(0x00)))
    }

    // ── Battery voltage (ATRV) ────────────────────────────────────────────

    @Test
    fun `decodeBatteryVoltage parses 14_2V`() {
        assertEquals(14.2f, PidDecoder.decodeBatteryVoltage("14.2V")!!, 0.01f)
    }

    @Test
    fun `decodeBatteryVoltage parses 12_5V`() {
        assertEquals(12.5f, PidDecoder.decodeBatteryVoltage("12.5V")!!, 0.01f)
    }

    @Test
    fun `decodeBatteryVoltage handles whitespace`() {
        assertEquals(14.2f, PidDecoder.decodeBatteryVoltage(" 14.2V ")!!, 0.01f)
    }

    @Test
    fun `decodeBatteryVoltage returns null for malformed string`() {
        assertNull(PidDecoder.decodeBatteryVoltage("OK"))
        assertNull(PidDecoder.decodeBatteryVoltage(""))
    }
}
