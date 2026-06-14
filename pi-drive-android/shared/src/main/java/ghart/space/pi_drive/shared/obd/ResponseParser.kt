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

            // Attempt to parse as a hex OBD-II response.
            //
            // Normal (headers off): response is even-length hex, bytes[0] = service byte.
            //   e.g. "41 0C 1A F8" → hexOnly "410C1AF8" → bytes[0]=0x41 → Success
            //
            // CAN 11-bit headers ON (ATH0 failed): ELM327 prepends a 3-nibble CAN address
            // (e.g. "7E8"), making the total nibble count ODD:
            //   "7E8 06 41 00 BE 3F B8 13" → hexOnly "7E8064100BE3FB813" (17 nibbles)
            // The even-length guard would reject this and fall through to ATResponse, causing
            // the PID range scan to get ATResponse instead of Success → supportedPids stays
            // empty → OBDPollScheduler polls nothing → every gauge blank.
            //
            // Fix: accept odd-length hex by trying nibble offset +1 to align to even bytes,
            // then scan ALL bytes for the first valid OBD service byte (0x41–0x49) rather than
            // requiring it to be bytes[0]. This transparently handles:
            //   • headers off: bytes[0] = 0x41 (found immediately)
            //   • CAN 11-bit headers on: 0x41 found after skipping 0xE8 + 0x06
            //   • CAN 29-bit headers on (even): 0x41 found after 4-byte CAN ID + length byte
            // CAN IDs (0x7Exx, 0x18DAxxxx) are never in 0x40–0x49, so the scan is safe.
            val hexOnly = upper.replace(" ", "")
            if (hexOnly.length >= 4 && hexOnly.all { it.isHexDigit() }) {
                // Even hex → use as-is (normal case).
                // Odd hex → drop the leading nibble (the extra half-byte of the CAN-11 address)
                // to produce an even-aligned byte stream.
                val alignedHex = if (hexOnly.length % 2 == 0) hexOnly else hexOnly.substring(1)
                if (alignedHex.length >= 4) {
                    val bytes = hexStringToBytes(alignedHex) ?: continue
                    for (i in bytes.indices) {
                        val b = bytes[i].toInt() and 0xFF
                        if (b in 0x40..0x49 && i + 1 < bytes.size) {
                            val pid = bytes[i + 1].toInt() and 0xFF
                            val data = if (i + 2 < bytes.size) bytes.copyOfRange(i + 2, bytes.size) else ByteArray(0)
                            return OBDResponse.Success(b, pid, data)
                        }
                    }
                }
                // All-hex content but no recognizable OBD frame (echo or unknown framing).
                // Continue to the next line rather than treating hex as an AT text response.
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
