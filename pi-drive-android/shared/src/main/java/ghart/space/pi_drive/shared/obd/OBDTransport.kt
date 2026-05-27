package ghart.space.pi_drive.shared.obd

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the physical communication channel to an ELM327 OBD-II adapter.
 *
 * The interface deliberately hides the transport mechanism so that production code
 * (Bluetooth RFCOMM), testing (TCP emulator), and unit tests (mock) can all use
 * the same polling logic without modification.
 *
 * All [send] calls are suspend functions — callers should invoke them from a
 * coroutine tied to the OBD polling scope. The implementation is responsible for
 * thread safety and read/write serialization on the underlying socket.
 *
 * Typical lifecycle:
 * 1. Call [connect] — opens the socket and runs the ELM327 init sequence (ATZ, ATE0, …).
 * 2. Call [send] in a loop to poll PIDs.
 * 3. Call [disconnect] when the session ends or the user navigates away.
 *
 * Implementations: `BluetoothTransport`, `TcpTransport`, `MockTransport`.
 */
interface OBDTransport {

    /**
     * Whether the transport currently has an open, usable connection.
     * Transitions to `false` immediately when [disconnect] is called or an
     * unrecoverable I/O error occurs.
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Open the transport connection and complete the ELM327 initialization sequence.
     *
     * On success, [isConnected] becomes `true`.
     * Throws [java.io.IOException] if the connection cannot be established.
     */
    suspend fun connect()

    /**
     * Close the transport connection and release all resources.
     *
     * Safe to call even if [isConnected] is already `false`.
     * After this call, [isConnected] becomes `false`.
     */
    suspend fun disconnect()

    /**
     * Send an AT command or OBD PID request and return the adapter's response.
     *
     * The [command] should NOT include a trailing `\r` — implementations append it.
     * The returned string is trimmed and has the trailing `>` prompt stripped.
     *
     * @param command The ELM327 AT command (e.g. "ATZ") or OBD request (e.g. "010D").
     * @return The adapter response string (e.g. "41 0D 50" for 80 km/h speed).
     * @throws java.io.IOException if the transport is disconnected or a read/write fails.
     */
    suspend fun send(command: String): String
}
