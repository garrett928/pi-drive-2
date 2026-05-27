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
