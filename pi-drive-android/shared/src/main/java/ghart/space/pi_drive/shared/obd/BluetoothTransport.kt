package ghart.space.pi_drive.shared.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

private const val TAG = "OBDTransport"
private const val SPP_UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB"

/**
 * Production [OBDTransport] implementation over Bluetooth Classic RFCOMM.
 *
 * Connects to an ELM327-compatible OBD-II adapter via the Bluetooth Serial Port Profile (SPP)
 * UUID. All socket I/O runs on a single-threaded dispatcher to prevent concurrent reads and
 * writes on the underlying [BluetoothSocket].
 *
 * **Lifecycle:**
 * 1. Call [connect] — opens the RFCOMM socket to [device] and transitions [isConnected] to `true`.
 * 2. Call [send] in a loop to exchange AT commands and OBD PID requests.
 * 3. Call [disconnect] when done; [isConnected] returns to `false`.
 *
 * **Timeouts:**
 * [BluetoothSocket] does not support `soTimeout`. Each [send] call is therefore wrapped in
 * [withTimeoutOrNull] + [runInterruptible] so that slow or unresponsive adapters do not block
 * the polling loop indefinitely.
 *
 * **Permissions:**
 * Requires `BLUETOOTH_CONNECT` (API 31+). The caller is responsible for requesting the
 * permission before invoking [connect].
 *
 * @param device     The paired [BluetoothDevice] representing the OBD adapter.
 * @param timeoutMs  Per-command read timeout in milliseconds. Defaults to [DEFAULT_TIMEOUT_MS].
 */
class BluetoothTransport(
    private val device: BluetoothDevice,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : OBDTransport {

    companion object {
        /** Bluetooth SPP UUID for ELM327 RFCOMM serial communication. */
        val SPP_UUID: UUID = UUID.fromString(SPP_UUID_STRING)

        /** Default per-command read timeout. */
        const val DEFAULT_TIMEOUT_MS = 2_000L
    }

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** Single-threaded context prevents concurrent reads and writes on the socket. */
    private val transportDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var framer: ResponseFramer? = null

    /**
     * Opens an RFCOMM socket to [device] using [SPP_UUID] and calls [BluetoothSocket.connect].
     *
     * On success, [isConnected] becomes `true`. On failure, the socket is closed and
     * the exception propagates to the caller.
     *
     * @throws IOException if the RFCOMM socket cannot be created or the connection fails.
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        withContext(transportDispatcher) {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                sock.connect()
            } catch (e: IOException) {
                try { sock.close() } catch (_: IOException) {}
                Log.e(TAG, "BluetoothTransport: failed to connect to ${device.address}: ${e.message}")
                throw e
            }
            socket = sock
            outputStream = sock.outputStream
            framer = ResponseFramer(sock.inputStream)
            _isConnected.value = true
            Log.i(TAG, "BluetoothTransport: connected to ${device.name ?: device.address}")
        }
    }

    /**
     * Closes the RFCOMM socket and all associated streams.
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
            Log.i(TAG, "BluetoothTransport: disconnected")
        }
    }

    /**
     * Sends [command] to the adapter and returns the trimmed response string.
     *
     * Appends `\r` to [command] per ELM327 spec before writing. Enforces [timeoutMs] using
     * [withTimeoutOrNull] + [runInterruptible] since [BluetoothSocket] lacks `soTimeout`.
     *
     * @param command AT command or OBD PID request (no trailing `\r` needed).
     * @return Trimmed response with the `>` prompt stripped.
     * @throws OBDTimeoutException if no `>` prompt arrives within [timeoutMs].
     * @throws IOException         if the transport is not connected or an I/O error occurs.
     */
    override suspend fun send(command: String): String = withContext(transportDispatcher) {
        val out = outputStream ?: throw IOException("BluetoothTransport is not connected")
        val fr = framer ?: throw IOException("BluetoothTransport is not connected")

        try {
            out.write("$command\r".toByteArray(Charsets.US_ASCII))
            out.flush()

            withTimeoutOrNull(timeoutMs) {
                runInterruptible { fr.readResponse() }
            } ?: run {
                Log.w(TAG, "BluetoothTransport: timeout after ${timeoutMs}ms for '$command'")
                throw OBDTimeoutException(
                    "No response from adapter within ${timeoutMs}ms (command: '$command')"
                )
            }
        } catch (e: OBDTimeoutException) {
            throw e
        } catch (e: IOException) {
            _isConnected.value = false
            Log.e(TAG, "BluetoothTransport: I/O error for '$command': ${e.message}")
            throw e
        }
    }
}
