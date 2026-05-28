package ghart.space.pi_drive.shared.obd

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

private const val TCP_TAG = "OBDTransport"

/**
 * [OBDTransport] implementation over TCP/IP for use with ELM327 emulators during development.
 *
 * Connects to [host]:[port] using a plain [Socket]. This transport is used when the app is
 * launched in TCP mode (`tcp_mode=true` intent extra) so developers can test the full OBD
 * protocol stack against a software emulator without a real Bluetooth dongle.
 *
 * The socket's `soTimeout` is set to [timeoutMs] so that [ResponseFramer] throws
 * [OBDTimeoutException] when no prompt is received within the configured window.
 *
 * **Lifecycle:**
 * 1. Call [connect] — opens a TCP socket and transitions [isConnected] to `true`.
 * 2. Call [send] to exchange AT commands and OBD PID requests.
 * 3. Call [disconnect] when done.
 *
 * @param host       Hostname or IP address of the ELM327 emulator (e.g. "127.0.0.1").
 * @param port       TCP port the emulator is listening on (e.g. 35000).
 * @param timeoutMs  Per-command read timeout in milliseconds. Applied as `socket.soTimeout`.
 *                   Defaults to [DEFAULT_TIMEOUT_MS].
 */
class TcpTransport(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : OBDTransport {

    companion object {
        /** Default per-command read timeout. */
        const val DEFAULT_TIMEOUT_MS = 2_000
    }

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** Single-threaded context prevents concurrent reads and writes on the socket. */
    private val transportDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var framer: ResponseFramer? = null

    /**
     * Opens a TCP connection to [host]:[port] and prepares the I/O streams.
     *
     * Sets `soTimeout` on the socket so that [ResponseFramer] enforces read deadlines
     * via [java.net.SocketTimeoutException] → [OBDTimeoutException].
     *
     * @throws IOException if the connection cannot be established.
     */
    override suspend fun connect() {
        withContext(transportDispatcher) {
            val sock = Socket(host, port)
            sock.soTimeout = timeoutMs
            socket = sock
            outputStream = sock.getOutputStream()
            framer = ResponseFramer(sock.getInputStream())
            _isConnected.value = true
            Log.i(TCP_TAG, "TcpTransport: connected to $host:$port")
        }
    }

    /**
     * Closes the TCP socket and releases all associated resources.
     *
     * Safe to call even if already disconnected. [isConnected] becomes `false` immediately.
     */
    override suspend fun disconnect() {
        withContext(transportDispatcher) {
            _isConnected.value = false
            try { outputStream?.close() } catch (_: IOException) {}
            try { socket?.close() } catch (_: IOException) {}
            socket = null
            outputStream = null
            framer = null
            Log.i(TCP_TAG, "TcpTransport: disconnected from $host:$port")
        }
    }

    /**
     * Sends [command] to the ELM327 emulator and returns the trimmed response string.
     *
     * Appends `\r` to [command] per ELM327 spec. [ResponseFramer] enforces the timeout via
     * the socket's `soTimeout`; a [java.net.SocketTimeoutException] is re-thrown as
     * [OBDTimeoutException].
     *
     * @param command AT command or OBD PID request (no trailing `\r` needed).
     * @return Trimmed response with the `>` prompt stripped.
     * @throws OBDTimeoutException if no `>` prompt arrives within [timeoutMs].
     * @throws IOException         if the transport is not connected or an I/O error occurs.
     */
    override suspend fun send(command: String): String {
        return try {
            withContext(transportDispatcher) {
                val out = outputStream ?: throw IOException("TcpTransport is not connected")
                val fr = framer ?: throw IOException("TcpTransport is not connected")
                out.write("$command\r".toByteArray(Charsets.US_ASCII))
                out.flush()
                fr.readResponse()
            }
        } catch (e: OBDTimeoutException) {
            Log.w(TCP_TAG, "TcpTransport: timeout after ${timeoutMs}ms for '$command' ($host:$port)")
            throw e
        } catch (e: IOException) {
            _isConnected.value = false
            Log.e(TCP_TAG, "TcpTransport: I/O error for '$command': ${e.message}")
            throw e
        }
    }
}
