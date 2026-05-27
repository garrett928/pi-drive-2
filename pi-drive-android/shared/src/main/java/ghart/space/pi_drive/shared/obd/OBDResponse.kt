package ghart.space.pi_drive.shared.obd

/**
 * A parsed response from an ELM327 OBD-II adapter.
 *
 * Produced by [ResponseParser] from raw adapter text. Use exhaustive `when`
 * expressions to handle every case without branching on raw strings.
 */
sealed class OBDResponse {

    /**
     * A valid OBD-II service response with decoded data bytes.
     *
     * The [serviceResponse] byte equals the queried service number + 0x40 — e.g.
     * Mode 01 (current data) responses carry 0x41, Mode 09 (vehicle info) 0x49.
     *
     * @param serviceResponse Mode response byte (service + 0x40).
     * @param pid             The PID that was queried.
     * @param dataBytes       Raw payload bytes, excluding the mode and PID header bytes.
     */
    data class Success(
        val serviceResponse: Int,
        val pid: Int,
        val dataBytes: ByteArray,
    ) : OBDResponse() {

        // ByteArray uses reference equality in data classes; override for content equality.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return serviceResponse == other.serviceResponse
                && pid == other.pid
                && dataBytes.contentEquals(other.dataBytes)
        }

        override fun hashCode(): Int {
            var result = serviceResponse
            result = 31 * result + pid
            result = 31 * result + dataBytes.contentHashCode()
            return result
        }

        override fun toString(): String {
            val bytesHex = dataBytes.joinToString(", ") { "0x%02X".format(it) }
            return "Success(serviceResponse=0x%02X, pid=0x%02X, dataBytes=[$bytesHex])"
                .format(serviceResponse, pid)
        }
    }

    /**
     * The adapter returned "NO DATA".
     *
     * Indicates the queried PID is not supported by this vehicle, or the ECU
     * did not respond within the adapter's timeout window.
     */
    data object NoData : OBDResponse()

    /**
     * An error response from the adapter.
     *
     * Common [rawMessage] values: `"?"`, `"UNABLE TO CONNECT"`, `"BUS INIT ERROR"`,
     * `"CAN ERROR"`, `"STOPPED"`, `"BUS BUSY"`. Callers should log [rawMessage]
     * and skip the failing PID rather than aborting the polling loop.
     */
    data class Error(val rawMessage: String) : OBDResponse()

    /**
     * A textual response to an AT command.
     *
     * Examples:
     * - `"ELM327 v2.2"` (from [OBDCommand.ATZ])
     * - `"OK"` (from [OBDCommand.ATE0], [OBDCommand.ATL0], etc.)
     * - `"14.2V"` (from [OBDCommand.ATRV])
     * - `"AUTO, ISO 15765-4 (CAN 11/500)"` (from ATDP)
     */
    data class ATResponse(val text: String) : OBDResponse()
}
