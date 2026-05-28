package ghart.space.pi_drive.shared.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PidSupportTest {

    // ── Bitmask decoding ──────────────────────────────────────────────────

    /**
     * Response "4100BE3EB813" encodes dataBytes=[0xBE, 0x3E, 0xB8, 0x13].
     *
     * Bit mapping for 0xBE3EB813:
     * 0xBE = 10111110 → PIDs 01,03,04,05,06,07
     * 0x3E = 00111110 → PIDs 0B,0C,0D,0E,0F
     * 0xB8 = 10111000 → PIDs 11,13,14,15
     * 0x13 = 00010011 → PIDs 1C,1F,20
     */
    @Test
    fun `decode 0xBE3EB813 returns correct pid set`() {
        val response = ResponseParser.parse("4100BE3EB813")
        assertTrue(response is OBDResponse.Success)
        val result = PidSupport.decode(response as OBDResponse.Success)

        // PIDs that must be in the set (unambiguous from the bit pattern)
        assertTrue("PID 0x01 should be supported", 0x01 in result)
        assertTrue("PID 0x03 should be supported", 0x03 in result)
        assertTrue("PID 0x0C should be supported", 0x0C in result)
        assertTrue("PID 0x0D should be supported", 0x0D in result)
        assertTrue("PID 0x11 should be supported", 0x11 in result)
        assertTrue("PID 0x13 should be supported", 0x13 in result)
        assertTrue("PID 0x1C should be supported", 0x1C in result)
        assertTrue("PID 0x20 should be supported (range chaining bit)", 0x20 in result)

        // PIDs that must NOT be in the set
        assertFalse("PID 0x02 should not be supported", 0x02 in result)
        assertFalse("PID 0x08 should not be supported", 0x08 in result)
        assertFalse("PID 0x09 should not be supported", 0x09 in result)
        assertFalse("PID 0x10 should not be supported", 0x10 in result)
    }

    @Test
    fun `decode all-zeros returns empty set`() {
        val response = ResponseParser.parse("41 00 00 00 00 00")
        assertTrue(response is OBDResponse.Success)
        val result = PidSupport.decode(response as OBDResponse.Success)
        assertTrue("All-zero bitmask should produce empty set", result.isEmpty())
    }

    @Test
    fun `decode 0x00000001 returns only range boundary pid 0x20`() {
        // Only the last bit set: PID 0x20 (chaining indicator for the 0x21-0x40 range)
        val response = ResponseParser.parse("41 00 00 00 00 01")
        assertTrue(response is OBDResponse.Success)
        val result = PidSupport.decode(response as OBDResponse.Success)
        assertEquals(setOf(0x20), result)
    }

    @Test
    fun `decode all-zeros for range 0x20 returns empty set`() {
        // Response to PID 0x20 query with nothing supported in 0x21-0x40
        val response = ResponseParser.parse("41 20 00 00 00 00")
        assertTrue(response is OBDResponse.Success)
        val result = PidSupport.decode(response as OBDResponse.Success)
        assertTrue("Empty range should produce empty set", result.isEmpty())
    }

    @Test
    fun `decode uses correct base offset for range 0x20`() {
        // Bit 31 set → PID 0x21 (base 0x20 + bit 0 + 1)
        val response = ResponseParser.parse("41 20 80 00 00 00")
        assertTrue(response is OBDResponse.Success)
        val result = PidSupport.decode(response as OBDResponse.Success)
        assertEquals(setOf(0x21), result)
    }

    @Test
    fun `decode all-ones returns all 32 pids in range`() {
        val response = ResponseParser.parse("41 00 FF FF FF FF")
        assertTrue(response is OBDResponse.Success)
        val result = PidSupport.decode(response as OBDResponse.Success)
        assertEquals(32, result.size)
        assertTrue("PID 0x01 should be first", 0x01 in result)
        assertTrue("PID 0x20 should be last", 0x20 in result)
    }

    @Test
    fun `decode too-short response returns empty set`() {
        val response = OBDResponse.Success(serviceResponse = 0x41, pid = 0x00, dataBytes = byteArrayOf(0xBE.toByte()))
        val result = PidSupport.decode(response)
        assertTrue(result.isEmpty())
    }

    // ── isSupported ───────────────────────────────────────────────────────

    @Test
    fun `isSupported returns true when pid in set`() {
        assertTrue(PidSupport.isSupported(0x0D, setOf(0x0C, 0x0D, 0x0F)))
    }

    @Test
    fun `isSupported returns false when pid not in set`() {
        assertFalse(PidSupport.isSupported(0x10, setOf(0x0C, 0x0D, 0x0F)))
    }

    // ── shouldQueryNextRange ──────────────────────────────────────────────

    @Test
    fun `shouldQueryNextRange returns true when 0x20 is in range 0x00 result`() {
        assertTrue(PidSupport.shouldQueryNextRange(0x00, setOf(0x01, 0x0D, 0x20)))
    }

    @Test
    fun `shouldQueryNextRange returns false when 0x20 not in range 0x00 result`() {
        assertFalse(PidSupport.shouldQueryNextRange(0x00, setOf(0x01, 0x0D, 0x0F)))
    }

    @Test
    fun `shouldQueryNextRange returns false for range 0x60 regardless of content`() {
        // 0x60 is the last range; no next range to query
        assertFalse(PidSupport.shouldQueryNextRange(0x60, setOf(0x61, 0x80)))
    }

    // ── queryRanges ───────────────────────────────────────────────────────

    @Test
    fun `queryRanges returns four commands`() {
        val ranges = PidSupport.queryRanges()
        assertEquals(4, ranges.size)
    }

    @Test
    fun `queryRanges wire strings are correct`() {
        val expected = listOf("0100", "0120", "0140", "0160")
        assertEquals(expected, PidSupport.queryRanges().map { it.toRawString() })
    }
}
