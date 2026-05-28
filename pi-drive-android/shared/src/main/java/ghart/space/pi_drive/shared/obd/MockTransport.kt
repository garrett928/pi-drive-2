package ghart.space.pi_drive.shared.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield

/**
 * In-memory OBD transport that returns canned responses without any network
 * or Bluetooth connection.
 *
 * Used in unit tests that exercise OBD parsing logic and by [DemoVehicleDataSource]
 * as an optional backing transport when a caller needs controllable PID responses.
 *
 * Default responses mimic a warmed-up vehicle at steady highway speed:
 * - Speed: 80 km/h (PID 0x0D)
 * - RPM: 2 500 (PID 0x0C)
 * - Coolant: 90 °C (PID 0x05)
 * - Intake: 25 °C (PID 0x0F)
 * - Throttle: ~20 % (PID 0x11)
 * - Fuel level: ~75 % (PID 0x2F)
 * - Oil temp: 100 °C (PID 0x5C)
 * - MAF: 6.0 g/s (PID 0x10)
 * - Fuel rate: 4.0 L/h (PID 0x5E)
 * - Battery: 14.1 V (PID 0x42)
 *
 * Override any PID response with [setPidResponse]. Unsupported PIDs return "NO DATA".
 */
class MockTransport : OBDTransport {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** Custom PID responses injected via [setPidResponse]. Key: uppercase command string, e.g. "010D". */
    private val customResponses = mutableMapOf<String, String>()

    override suspend fun connect() {
        _isConnected.value = true
    }

    override suspend fun disconnect() {
        _isConnected.value = false
    }

    /**
     * Returns a canned response for [command].
     *
     * Resolution order:
     * 1. Custom response registered via [setPidResponse]
     * 2. Built-in AT command table
     * 3. Built-in default PID table
     * 4. "NO DATA" for anything unrecognised
     *
     * @param command AT command or OBD PID request (no trailing `\r` needed).
     * @return The simulated adapter response, trimmed.
     */
    override suspend fun send(command: String): String {
        // Real transports (Bluetooth, TCP) always suspend during I/O. yield() here
        // ensures cooperative coroutine scheduling in tests — without it, the infinite
        // polling loop would never yield and test coroutines would starve.
        yield()
        val upper = command.uppercase().trim()

        // 1. Caller-injected override
        customResponses[upper]?.let { return it }

        // 2. AT command table
        if (upper.startsWith("AT")) {
            return when (upper) {
                "ATZ"   -> "ELM327 v2.2"
                "ATRV"  -> "14.2V"
                "ATDP"  -> "AUTO, ISO 15765-4 (CAN 11/500)"
                else    -> "OK"
            }
        }

        // 3. Default OBD Mode 01 PID responses
        return defaultPidResponse(upper) ?: "NO DATA"
    }

    /**
     * Override the response returned when [command] is sent.
     *
     * @param command Exact command string as sent (e.g. "010D"). Case-insensitive.
     * @param response The response to return (e.g. "41 0D 60").
     */
    fun setResponse(command: String, response: String) {
        customResponses[command.uppercase()] = response
    }

    /**
     * Convenience override for a Mode 01 PID by its decimal PID number.
     *
     * @param pid     OBD PID number in decimal (e.g. 13 for speed, 12 for RPM).
     * @param hexBytes The response bytes string (e.g. "41 0D 60").
     */
    fun setPidResponse(pid: Int, hexBytes: String) {
        customResponses["01%02X".format(pid)] = hexBytes
    }

    // ── Default PID table ──────────────────────────────────────────────────

    /**
     * Returns a default response for recognised Mode 01 PID commands,
     * or null if the PID is not in the built-in table.
     */
    private fun defaultPidResponse(command: String): String? {
        if (!command.startsWith("01") || command.length < 4) return null
        val pid = command.substring(2)
        return when (pid) {
            // PID support bitmaps
            "00" -> "41 00 BE 1F A8 13"  // PIDs 01–20 supported
            "20" -> "41 20 A0 07 E0 11"  // PIDs 21–40 supported (includes 0x2F fuel)
            "40" -> "41 40 44 00 00 01"  // PIDs 41–60 supported (includes 0x42, 0x5C, 0x5E)
            "60" -> "41 60 00 00 00 00"  // PIDs 61–80 (none)

            // Sensor PIDs — default to a plausible warmed-up highway scenario
            "05" -> "41 05 82"        // Coolant 90 °C  (A - 40 = 130 - 40 = 90)
            "0C" -> "41 0C 27 10"     // RPM 2 500      ((0x27 * 256 + 0x10) / 4 = 2500)
            "0D" -> "41 0D 50"        // Speed 80 km/h  (A = 0x50 = 80)
            "0F" -> "41 0F 41"        // Intake 25 °C   (A - 40 = 65 - 40 = 25)
            "10" -> "41 10 02 58"     // MAF 6.00 g/s   ((2 * 256 + 88) / 100 = 6.0)
            "11" -> "41 11 33"        // Throttle ~20 % (0x33 * 100 / 255 ≈ 20)
            "2F" -> "41 2F BF"        // Fuel ~75 %     (0xBF * 100 / 255 ≈ 75)
            "42" -> "41 42 37 14"     // Battery 14.1 V ((0x37 * 256 + 0x14) / 1000 = 14.1)
            "5C" -> "41 5C 8C"        // Oil 100 °C     (A - 40 = 140 - 40 = 100)
            "5E" -> "41 5E 00 50"     // Fuel rate 4 L/h ((0 * 256 + 80) * 0.05 = 4.0)

            else -> null
        }
    }
}
