package ghart.space.pi_drive.shared.obd

/**
 * All commands the app sends to an ELM327 OBD-II adapter.
 *
 * Each command knows how to format itself for the wire via [toRawString].
 * No trailing carriage return is included — the [OBDTransport] implementation
 * appends `"\r"` as required by the ELM327 protocol.
 *
 * Use exhaustive `when` on this sealed class to ensure all command types are handled
 * wherever command formatting logic is needed.
 */
sealed class OBDCommand {

    /** Returns the command string exactly as sent over the adapter connection. */
    abstract fun toRawString(): String

    // ── AT commands ──────────────────────────────────────────────────────

    /** Full device reset — returns the adapter to its power-on defaults. */
    data object ATZ : OBDCommand() {
        override fun toRawString() = "ATZ"
    }

    /** Echo off — prevents the adapter from echoing commands back in responses. */
    data object ATE0 : OBDCommand() {
        override fun toRawString() = "ATE0"
    }

    /** Linefeed off — removes LF characters from all responses. */
    data object ATL0 : OBDCommand() {
        override fun toRawString() = "ATL0"
    }

    /** Spaces off — removes spaces between data bytes in hex responses. */
    data object ATS0 : OBDCommand() {
        override fun toRawString() = "ATS0"
    }

    /** Headers off — suppresses address and PCI bytes in OBD responses. */
    data object ATH0 : OBDCommand() {
        override fun toRawString() = "ATH0"
    }

    /**
     * Set protocol — instructs the adapter to use the specified OBD-II protocol.
     *
     * @param protocol ELM327 protocol number (0 = auto-detect).
     */
    data class ATSP(val protocol: Int) : OBDCommand() {
        override fun toRawString() = "ATSP$protocol"
    }

    /**
     * Read voltage — returns the adapter supply voltage as a string like "14.2V".
     *
     * Useful as a battery-voltage proxy since it does not require a dedicated OBD PID.
     */
    data object ATRV : OBDCommand() {
        override fun toRawString() = "ATRV"
    }

    // ── OBD PID request ──────────────────────────────────────────────────

    /**
     * OBD-II request for a single PID from a given service.
     *
     * The wire format is the two-digit service number followed by the two-digit
     * PID, both in uppercase hex with no spaces — e.g. service=1, pid=0x0D → "010D".
     *
     * @param service OBD service number (1 = current data, 9 = vehicle info).
     * @param pid     Parameter ID within the service.
     */
    data class PidRequest(val service: Int, val pid: Int) : OBDCommand() {
        override fun toRawString() = "%02X%02X".format(service, pid)
    }
}
