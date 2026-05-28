package ghart.space.pi_drive.shared.obd

import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException

/**
 * Reads raw bytes from a transport [InputStream] until the ELM327 prompt character (`>`) is
 * received, then returns the trimmed response string.
 *
 * Encapsulates the framing logic shared by [BluetoothTransport] and [TcpTransport] so that
 * both use identical parsing behavior regardless of the underlying socket type.
 *
 * **Framing contract:**
 * - Reads one byte at a time from [inputStream].
 * - Accumulates bytes until `>` (ASCII 62) is encountered; the prompt is not included.
 * - Skips null bytes (0x00), which some adapters use as padding.
 * - Strips all `\r` (CR) and `\n` (LF) characters from the accumulated buffer.
 * - Trims leading/trailing whitespace from the final string.
 * - Returns a single response per call; subsequent calls read the next response from the stream.
 *
 * **Timeout handling:**
 * If the underlying socket throws [SocketTimeoutException] (via `socket.soTimeout`),
 * [readResponse] catches it and re-throws as [OBDTimeoutException].
 * For Bluetooth sockets (which do not support `soTimeout`), callers should wrap
 * [readResponse] in `withTimeoutOrNull { runInterruptible { ... } }`.
 *
 * @param inputStream The transport's input stream. Must remain open for the life of this framer.
 */
class ResponseFramer(private val inputStream: InputStream) {

    companion object {
        private const val PROMPT_BYTE = '>'.code
        private const val NULL_BYTE = 0
    }

    /**
     * Reads from [inputStream] until the `>` prompt is received and returns the response string.
     *
     * @return The trimmed response with `\r` and `\n` removed.
     * @throws OBDTimeoutException if a [SocketTimeoutException] is received before the prompt.
     * @throws IOException         if the stream closes before the prompt is received.
     */
    fun readResponse(): String {
        val buffer = StringBuilder()
        try {
            while (true) {
                val byte = inputStream.read()
                when {
                    byte == -1          -> throw IOException("Stream closed before prompt received")
                    byte == PROMPT_BYTE -> break
                    byte == NULL_BYTE   -> { /* skip null padding bytes */ }
                    else                -> buffer.append(byte.toChar())
                }
            }
        } catch (e: SocketTimeoutException) {
            throw OBDTimeoutException("No prompt received: ${e.message}", e)
        }
        return buffer.toString()
            .replace("\r", "")
            .replace("\n", "")
            .trim()
    }
}
