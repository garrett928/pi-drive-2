package ghart.space.pi_drive.shared.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketTimeoutException

class ResponseFramerTest {

    @Test
    fun `PID hex response is returned trimmed without prompt`() {
        val framer = ResponseFramer("410D50\r\r>".toByteStream())
        assertEquals("410D50", framer.readResponse())
    }

    @Test
    fun `AT command text response is returned trimmed`() {
        val framer = ResponseFramer("ELM327 v1.4b\r\r>".toByteStream())
        assertEquals("ELM327 v1.4b", framer.readResponse())
    }

    @Test
    fun `stream SocketTimeoutException throws OBDTimeoutException`() {
        val timeoutStream = object : InputStream() {
            override fun read(): Int = throw SocketTimeoutException("simulated timeout")
        }
        assertThrows(OBDTimeoutException::class.java) {
            ResponseFramer(timeoutStream).readResponse()
        }
    }

    @Test
    fun `multiple queued responses are read one at a time`() {
        val framer = ResponseFramer("410D50\r\r>410C2710\r\r>".toByteStream())
        assertEquals("410D50", framer.readResponse())
        assertEquals("410C2710", framer.readResponse())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun String.toByteStream(): InputStream =
        ByteArrayInputStream(toByteArray(Charsets.US_ASCII))
}
