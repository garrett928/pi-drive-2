package ghart.space.pi_drive.shared.obd

/**
 * Stateless parser that converts raw ELM327 adapter output into typed [OBDResponse] values.
 *
 * Handles all common ELM327 response formats:
 * - Unspaced hex: `"410D50"`
 * - Spaced hex: `"41 0D 50"`
 * - Multi-line responses (multiple ECU replies or `SEARCHING...` status prefix)
 * - Echo residue (command echoed before the response when ATE0 failed)
 * - Standard error strings: `"?"`, `"UNABLE TO CONNECT"`, `"CAN ERROR"`, etc.
 *
 * The parser iterates response lines, skips transient status messages, and returns
 * the **first** recognisable result. Subsequent lines are ignored once a result is
 * found — this correctly handles multi-ECU duplicate responses.
 */
object ResponseParser {

    /** Strings that unambiguously signal "no data available for this PID". */
    private val NO_DATA_TOKENS = setOf("NODATA", "NO DATA")

    /** Prefixes that identify adapter error conditions (checked case-insensitively). */
    private val ERROR_PREFIXES = listOf(
        "UNABLE TO CONNECT",
        "CAN ERROR",
        "BUS INIT ERROR",
        "STOPPED",
        "BUS BUSY",
        "DATA ERROR",
        "<DATA ERROR>",
        "BUFFER FULL",
        "LINK ERROR",
        "LV RESET",
        "FB ERROR",
        "ERR",
    )

    /**
     * Parses a raw adapter response string into a typed [OBDResponse].
     *
     * @param rawResponse Raw string received from the adapter, possibly containing
     *                    CR, LF, `">"` prompt characters, and multiple lines.
     * @return The most specific [OBDResponse] subtype that matches the content.
     */
    fun parse(rawResponse: String): OBDResponse {
        if (rawResponse.isBlank()) {
            return OBDResponse.Error(rawMessage = rawResponse)
        }

        val lines = rawResponse
            .replace("\r", "\n")
            .split("\n")
            .map { it.replace(">", "").trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return OBDResponse.Error(rawMessage = rawResponse)
        }

        for (line in lines) {
            val upper = line.uppercase()

            // Skip transient status messages emitted during protocol search
            if (upper.contains("SEARCHING") || upper.startsWith("BUS INIT:")) continue

            // "NO DATA" — PID not supported or ECU timeout
            if (NO_DATA_TOKENS.any { upper.replace(" ", "").startsWith(it) }) {
                return OBDResponse.NoData
            }

            // Known error strings
            if (upper == "?" || ERROR_PREFIXES.any { upper.startsWith(it) }) {
                return OBDResponse.Error(rawMessage = line)
            }

            // Attempt to parse as a hex OBD-II response
            val hexOnly = upper.replace(" ", "")
            if (hexOnly.length >= 4
                && hexOnly.length % 2 == 0
                && hexOnly.all { it.isHexDigit() }
            ) {
                val bytes = hexStringToBytes(hexOnly) ?: continue
                val firstByte = bytes[0].toInt() and 0xFF

                // Valid OBD service response byte = service + 0x40
                // Mode 01 → 0x41, Mode 09 (VIN) → 0x49. Echo residue has firstByte < 0x40.
                if (firstByte in 0x40..0x49 && bytes.size >= 2) {
                    val pid = bytes[1].toInt() and 0xFF
                    val data = if (bytes.size > 2) bytes.copyOfRange(2, bytes.size) else ByteArray(0)
                    return OBDResponse.Success(firstByte, pid, data)
                }
                // Not a valid response frame (echo or unknown header) — keep looking
                continue
            }

            // Non-hex, non-error text → AT command response (version strings, "OK", voltage)
            return OBDResponse.ATResponse(text = line)
        }

        return OBDResponse.Error(rawMessage = rawResponse)
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun hexStringToBytes(hex: String): ByteArray? = try {
        ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    } catch (e: NumberFormatException) {
        null
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'A'..'F'
}
