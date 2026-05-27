package ghart.space.pi_drive.shared.obd

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MockTransport].
 *
 * Verifies AT command handling, default PID responses, custom response
 * injection, and connect/disconnect state transitions.
 */
class MockTransportTest {

    private lateinit var transport: MockTransport

    @Before
    fun setUp() {
        transport = MockTransport()
    }

    // ── connect/disconnect ───────────────────────────────────────────────

    @Test
    fun `isConnected starts false`() = runTest {
        assertFalse(transport.isConnected.value)
    }

    @Test
    fun `connect sets isConnected to true`() = runTest {
        transport.connect()
        assertTrue(transport.isConnected.value)
    }

    @Test
    fun `disconnect sets isConnected to false`() = runTest {
        transport.connect()
        transport.disconnect()
        assertFalse(transport.isConnected.value)
    }

    // ── AT command handling ──────────────────────────────────────────────

    @Test
    fun `ATZ response contains ELM327`() = runTest {
        val response = transport.send("ATZ")
        assertTrue("ATZ should return ELM327 firmware string", response.contains("ELM327"))
    }

    @Test
    fun `ATRV returns voltage string`() = runTest {
        val response = transport.send("ATRV")
        assertTrue("ATRV should return voltage", response.endsWith("V"))
    }

    @Test
    fun `unknown AT command returns OK`() = runTest {
        val response = transport.send("ATSP0")
        assertEquals("OK", response)
    }

    @Test
    fun `AT commands are case-insensitive`() = runTest {
        val upper = transport.send("ATZ")
        val lower = transport.send("atz")
        assertEquals(upper, lower)
    }

    // ── default PID responses ────────────────────────────────────────────

    @Test
    fun `speed PID 0x0D returns 80 km-h response`() = runTest {
        val response = transport.send("010D")
        // Default: "41 0D 50" — A=0x50=80
        assertEquals("41 0D 50", response)
    }

    @Test
    fun `RPM PID 0x0C returns 2500 RPM response`() = runTest {
        val response = transport.send("010C")
        // Default: "41 0C 27 10" — (0x27*256+0x10)/4 = 2500
        assertEquals("41 0C 27 10", response)
    }

    @Test
    fun `coolant PID 0x05 returns 90-degree response`() = runTest {
        val response = transport.send("0105")
        // Default: "41 05 82" — A-40 = 130-40 = 90°C
        assertEquals("41 05 82", response)
    }

    @Test
    fun `unknown PID returns NO DATA`() = runTest {
        val response = transport.send("01FF")
        assertEquals("NO DATA", response)
    }

    @Test
    fun `PID support bitmap 0x00 returns non-empty response`() = runTest {
        val response = transport.send("0100")
        assertNotNull(response)
        assertTrue(response.startsWith("41 00"))
    }

    // ── custom response injection ────────────────────────────────────────

    @Test
    fun `setPidResponse overrides default response`() = runTest {
        // Override speed to 120 km/h (0x78 = 120)
        transport.setPidResponse(0x0D, "41 0D 78")
        val response = transport.send("010D")
        assertEquals("41 0D 78", response)
    }

    @Test
    fun `setResponse overrides response for exact command string`() = runTest {
        transport.setResponse("010C", "41 0C 1F 40")
        val response = transport.send("010C")
        assertEquals("41 0C 1F 40", response)
    }

    @Test
    fun `setResponse is case-insensitive on input command`() = runTest {
        transport.setResponse("010d", "41 0D 64")  // lowercase key
        val response = transport.send("010D")       // uppercase query
        assertEquals("41 0D 64", response)
    }

    @Test
    fun `custom response takes priority over built-in AT command`() = runTest {
        transport.setResponse("ATZ", "custom_response")
        assertEquals("custom_response", transport.send("ATZ"))
    }
}
