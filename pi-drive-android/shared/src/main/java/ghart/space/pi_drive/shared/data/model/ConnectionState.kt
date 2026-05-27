package ghart.space.pi_drive.shared.data.model

/**
 * Represents the current state of the OBD adapter connection.
 *
 * Transitions:
 * ```
 * Disconnected -> Connecting -> Connected
 *                Connecting -> Error
 *                Connected  -> Disconnected (on explicit disconnect or I/O error)
 *                Connected  -> Error
 *                Error      -> Connecting (on retry)
 * ```
 *
 * UI should observe this via [VehicleDataSource.connectionState] and render
 * the appropriate banner/chip on the dashboard.
 */
sealed class ConnectionState {

    /**
     * No connection attempt is in progress. The adapter is not connected.
     * This is the initial state before the user initiates a Bluetooth pairing.
     */
    data object Disconnected : ConnectionState()

    /**
     * A connection attempt is in progress — Bluetooth RFCOMM socket is opening
     * or the ELM327 initialization sequence (ATZ, ATE0, ATSP0, …) is running.
     */
    data object Connecting : ConnectionState()

    /**
     * The adapter is connected and the OBD polling loop is active.
     *
     * @param adapterName  Bluetooth device name (e.g. "OBDII").
     * @param protocol     ELM327 protocol string returned by ATDP (e.g. "ISO 15765-4 CAN").
     * @param pollRateHz   Actual poll rate achieved, in Hz (updates per second).
     */
    data class Connected(
        val adapterName: String,
        val protocol: String,
        val pollRateHz: Float,
    ) : ConnectionState()

    /**
     * The connection failed or was lost unexpectedly.
     *
     * @param message Human-readable description of the error, suitable for display.
     */
    data class Error(val message: String) : ConnectionState()
}
