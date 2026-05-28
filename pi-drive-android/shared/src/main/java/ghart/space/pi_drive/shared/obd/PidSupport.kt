package ghart.space.pi_drive.shared.obd

/**
 * Decodes OBD-II PID support bitmask responses and manages the range-chaining query sequence.
 *
 * OBD-II supports up to 256 PIDs per service, split into ranges of 32 PIDs each. A special
 * "support PID" at the boundary of each range (0x00, 0x20, 0x40, 0x60) returns a 4-byte bitmask
 * indicating which PIDs in the next 32 are available. If the bitmask includes the boundary PID
 * itself (e.g. 0x20 included when querying 0x00), the next range must also be queried.
 *
 * Bit mapping for range 0x00 (PIDs 0x01–0x20):
 * - Bit 31 (MSB of byte A) = PID 0x01
 * - Bit 30 = PID 0x02
 * - …
 * - Bit 0 (LSB of byte D) = PID 0x20
 *
 * The same bit layout applies to ranges 0x20, 0x40, and 0x60 with their respective base offsets.
 */
object PidSupport {

    /** The four standard PID range boundaries used to query Mode 01 PID availability. */
    private val RANGE_PIDS = listOf(0x00, 0x20, 0x40, 0x60)

    /**
     * Returns the OBD commands to query each of the four standard PID ranges.
     *
     * Callers should iterate these in order, stopping when the most recent decoded set does
     * not include the next range's boundary PID (see [shouldQueryNextRange]).
     */
    fun queryRanges(): List<OBDCommand> = RANGE_PIDS.map { pid ->
        OBDCommand.PidRequest(service = 1, pid = pid)
    }

    /**
     * Decodes a 4-byte PID support bitmask from a Mode 01 range-query response.
     *
     * The [response] must be a successful reply to PID 0x00, 0x20, 0x40, or 0x60.
     * The [OBDResponse.Success.pid] field determines the base offset for PID numbering.
     *
     * @param response Parsed response from a PID 0x00/0x20/0x40/0x60 query.
     * @return Set of PID numbers (e.g. 0x0D, 0x0C) that the ECU reports as supported.
     *         Returns an empty set if [response] has fewer than 4 data bytes.
     */
    fun decode(response: OBDResponse.Success): Set<Int> {
        val bytes = response.dataBytes
        if (bytes.size < 4) return emptySet()

        val base = response.pid
        val combined =
            ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
             (bytes[3].toInt() and 0xFF)

        val result = mutableSetOf<Int>()
        for (bit in 0..31) {
            if (combined and (1 shl (31 - bit)) != 0) {
                result.add(base + bit + 1)
            }
        }
        return result
    }

    /**
     * Returns true if [pid] is present in [supportedPids].
     */
    fun isSupported(pid: Int, supportedPids: Set<Int>): Boolean = pid in supportedPids

    /**
     * Returns true when the next range beyond [rangeBasePid] should be queried.
     *
     * The boundary PID of a range (e.g. 0x20 for the 0x00–0x20 range) acts as a flag:
     * if it is supported, the following range (0x21–0x40) has at least one supported PID
     * and its query command (0x0120) should be sent.
     *
     * @param rangeBasePid The PID used for the current range query (0x00, 0x20, or 0x40).
     * @param decoded      The PID set decoded from the current range response.
     * @return True if the next range should be queried.
     */
    fun shouldQueryNextRange(rangeBasePid: Int, decoded: Set<Int>): Boolean {
        val nextBoundary = rangeBasePid + 0x20
        return nextBoundary <= 0x60 && nextBoundary in decoded
    }
}
