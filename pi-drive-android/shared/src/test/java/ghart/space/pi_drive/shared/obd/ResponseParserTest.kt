package ghart.space.pi_drive.shared.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {

    // ── Success cases ─────────────────────────────────────────────────────

    @Test
    fun `unspaced hex response parses to Success`() {
        val result = ResponseParser.parse("410D50")
        assertTrue(result is OBDResponse.Success)
        val success = result as OBDResponse.Success
        assertEquals(0x41, success.serviceResponse)
        assertEquals(0x0D, success.pid)
        assertEquals(1, success.dataBytes.size)
        assertEquals(0x50.toByte(), success.dataBytes[0])
    }

    @Test
    fun `spaced hex response parses to Success`() {
        val result = ResponseParser.parse("41 0C 1A F8")
        assertTrue(result is OBDResponse.Success)
        val success = result as OBDResponse.Success
        assertEquals(0x41, success.serviceResponse)
        assertEquals(0x0C, success.pid)
        assertEquals(2, success.dataBytes.size)
        assertEquals(0x1A.toByte(), success.dataBytes[0])
        assertEquals(0xF8.toByte(), success.dataBytes[1])
    }

    @Test
    fun `SEARCHING prefix is skipped and valid response returned`() {
        val raw = "SEARCHING...\r\n410D50"
        val result = ResponseParser.parse(raw)
        assertTrue(result is OBDResponse.Success)
        val success = result as OBDResponse.Success
        assertEquals(0x0D, success.pid)
    }

    @Test
    fun `multi-line duplicate ECU responses use first valid line`() {
        // Two ECUs both respond — parser should return first and ignore second
        val raw = "410D50\r\n410D50"
        val result = ResponseParser.parse(raw)
        assertTrue(result is OBDResponse.Success)
        val success = result as OBDResponse.Success
        assertEquals(0x0D, success.pid)
        assertEquals(0x50.toByte(), success.dataBytes[0])
    }

    // ── NoData cases ──────────────────────────────────────────────────────

    @Test
    fun `NO DATA response returns NoData`() {
        assertEquals(OBDResponse.NoData, ResponseParser.parse("NO DATA"))
    }

    @Test
    fun `NODATA without space returns NoData`() {
        assertEquals(OBDResponse.NoData, ResponseParser.parse("NODATA"))
    }

    // ── Error cases ───────────────────────────────────────────────────────

    @Test
    fun `question mark returns Error`() {
        assertTrue(ResponseParser.parse("?") is OBDResponse.Error)
    }

    @Test
    fun `empty string returns Error`() {
        assertTrue(ResponseParser.parse("") is OBDResponse.Error)
    }

    @Test
    fun `blank whitespace returns Error`() {
        assertTrue(ResponseParser.parse("   \r\n") is OBDResponse.Error)
    }

    @Test
    fun `UNABLE TO CONNECT returns Error`() {
        assertTrue(ResponseParser.parse("UNABLE TO CONNECT") is OBDResponse.Error)
    }

    @Test
    fun `CAN ERROR returns Error`() {
        assertTrue(ResponseParser.parse("CAN ERROR") is OBDResponse.Error)
    }

    // ── AT response cases ─────────────────────────────────────────────────

    @Test
    fun `ELM327 version string returns ATResponse`() {
        val result = ResponseParser.parse("ELM327 v2.2")
        assertTrue(result is OBDResponse.ATResponse)
        assertEquals("ELM327 v2.2", (result as OBDResponse.ATResponse).text)
    }

    @Test
    fun `OK returns ATResponse`() {
        val result = ResponseParser.parse("OK")
        assertTrue(result is OBDResponse.ATResponse)
        assertEquals("OK", (result as OBDResponse.ATResponse).text)
    }

    @Test
    fun `voltage string returns ATResponse`() {
        val result = ResponseParser.parse("14.2V")
        assertTrue(result is OBDResponse.ATResponse)
        assertEquals("14.2V", (result as OBDResponse.ATResponse).text)
    }

    // ── Headers-on (ATH0 failed) ──────────────────────────────────────────
    //
    // When ATH0 does not take effect the adapter returns CAN/ISO headers prepended
    // to every response. The parser must still extract the OBD payload.

    @Test
    fun `CAN 11-bit header prefix is stripped and valid response returned`() {
        // "7E8" = 3-nibble CAN-11 address; total nibbles = 17 (odd). This was the
        // bug: the old even-length guard rejected it and returned ATResponse.
        val raw = "7E8 06 41 0D 50"    // speed = 80 km/h
        val result = ResponseParser.parse(raw)
        assertTrue("Should parse Success despite 3-nibble CAN header", result is OBDResponse.Success)
        val s = result as OBDResponse.Success
        assertEquals(0x41, s.serviceResponse)
        assertEquals(0x0D, s.pid)
        assertEquals(1, s.dataBytes.size)
        assertEquals(0x50.toByte(), s.dataBytes[0])
    }

    @Test
    fun `CAN 11-bit header on PID-00 support scan parses correctly`() {
        // This is the exact failure mode for empty dials: the 0100 PID-support scan
        // returns a CAN-11 framed response which was previously mis-parsed as ATResponse.
        val raw = "7E8 06 41 00 BE 3F B8 13"
        val result = ResponseParser.parse(raw)
        assertTrue(result is OBDResponse.Success)
        val s = result as OBDResponse.Success
        assertEquals(0x41, s.serviceResponse)
        assertEquals(0x00, s.pid)
        assertEquals(4, s.dataBytes.size)
        assertEquals(0xBE.toByte(), s.dataBytes[0])
        assertEquals(0x3F.toByte(), s.dataBytes[1])
    }

    @Test
    fun `CAN 29-bit header prefix is stripped and valid response returned`() {
        // 29-bit CAN IDs show as 8 nibbles (4 bytes) — even total, so the old code
        // could enter the hex block, but bytes[0]=0x18 is not in 0x40–0x49. The new
        // byte-scan finds 0x41 at the correct position.
        val raw = "18DAF110 06 41 0C 1A F8"   // RPM
        val result = ResponseParser.parse(raw)
        assertTrue("Should parse Success despite 8-nibble CAN-29 header", result is OBDResponse.Success)
        val s = result as OBDResponse.Success
        assertEquals(0x41, s.serviceResponse)
        assertEquals(0x0C, s.pid)
        assertEquals(2, s.dataBytes.size)
    }

    @Test
    fun `SEARCHING prefix followed by CAN header still parses correctly`() {
        // SEARCHING... (skipped) then CAN-11 framed response — both problems at once.
        val raw = "SEARCHING...\r7E8 06 41 0D 64"   // speed = 100 km/h
        val result = ResponseParser.parse(raw)
        assertTrue(result is OBDResponse.Success)
        val s = result as OBDResponse.Success
        assertEquals(0x0D, s.pid)
        assertEquals(0x64.toByte(), s.dataBytes[0])
    }

    // ── Prompt stripping ──────────────────────────────────────────────────

    @Test
    fun `prompt character is stripped before parsing`() {
        val result = ResponseParser.parse("410D50>")
        assertTrue(result is OBDResponse.Success)
    }

    @Test
    fun `only prompt character returns Error`() {
        assertTrue(ResponseParser.parse(">") is OBDResponse.Error)
    }
}
