package ghart.space.pi_drive.shared.obd

/**
 * Decodes raw OBD-II data bytes into typed values using the formulas defined in SAE J1979.
 *
 * All functions accept the `dataBytes` field from [OBDResponse.Success] (the payload after the
 * mode and PID header bytes) and return `null` when the byte array is too short to decode.
 *
 * Formula sources:
 * - PID 0D (speed):  A  km/h
 * - PID 0C (RPM):    ((A*256)+B)/4
 * - PID 05 (coolant): A-40  Celsius
 * - PID 0F (intake):  A-40  Celsius
 * - PID 10 (MAF):     ((A*256)+B)/100  g/s
 * - PID 11 (throttle): (A*100)/255  %
 * - PID 2F (fuel level): (A*100)/255  %
 * - PID 5C (oil temp): A-40  Celsius
 * - PID 5E (fuel rate): ((A*256)+B)/20  L/h
 */
object PidDecoder {

    /**
     * PID 0x0D — Vehicle speed.
     *
     * @param bytes Raw data bytes (must contain at least 1 byte).
     * @return Speed in km/h, or null if [bytes] is empty.
     */
    fun decodeSpeed(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        return bytes[0].toInt() and 0xFF
    }

    /**
     * PID 0x0C — Engine RPM.
     *
     * @param bytes Raw data bytes (must contain at least 2 bytes).
     * @return RPM as an integer, or null if [bytes] has fewer than 2 elements.
     */
    fun decodeRpm(bytes: ByteArray): Int? {
        if (bytes.size < 2) return null
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        return ((a * 256) + b) / 4
    }

    /**
     * PID 0x05 — Engine coolant temperature.
     *
     * @param bytes Raw data bytes (must contain at least 1 byte).
     * @return Temperature in degrees Celsius (range: -40 to 215), or null if [bytes] is empty.
     */
    fun decodeCoolantTemp(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        return (bytes[0].toInt() and 0xFF) - 40
    }

    /**
     * PID 0x0F — Intake air temperature.
     *
     * @param bytes Raw data bytes (must contain at least 1 byte).
     * @return Temperature in degrees Celsius (range: -40 to 215), or null if [bytes] is empty.
     */
    fun decodeIntakeTemp(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        return (bytes[0].toInt() and 0xFF) - 40
    }

    /**
     * PID 0x10 — Mass Air Flow rate.
     *
     * @param bytes Raw data bytes (must contain at least 2 bytes).
     * @return MAF in grams per second, or null if [bytes] has fewer than 2 elements.
     */
    fun decodeMaf(bytes: ByteArray): Float? {
        if (bytes.size < 2) return null
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        return ((a * 256) + b) / 100f
    }

    /**
     * PID 0x11 — Throttle position.
     *
     * @param bytes Raw data bytes (must contain at least 1 byte).
     * @return Throttle percentage (0.0–100.0), or null if [bytes] is empty.
     */
    fun decodeThrottle(bytes: ByteArray): Float? {
        if (bytes.isEmpty()) return null
        return ((bytes[0].toInt() and 0xFF) * 100f) / 255f
    }

    /**
     * PID 0x2F — Fuel tank level input.
     *
     * @param bytes Raw data bytes (must contain at least 1 byte).
     * @return Fuel level as percentage (0.0–100.0), or null if [bytes] is empty.
     */
    fun decodeFuelLevel(bytes: ByteArray): Float? {
        if (bytes.isEmpty()) return null
        return ((bytes[0].toInt() and 0xFF) * 100f) / 255f
    }

    /**
     * PID 0x5C — Engine oil temperature.
     *
     * @param bytes Raw data bytes (must contain at least 1 byte).
     * @return Temperature in degrees Celsius (range: -40 to 215), or null if [bytes] is empty.
     */
    fun decodeOilTemp(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        return (bytes[0].toInt() and 0xFF) - 40
    }

    /**
     * PID 0x5E — Engine fuel rate.
     *
     * @param bytes Raw data bytes (must contain at least 2 bytes).
     * @return Fuel consumption rate in litres per hour, or null if [bytes] has fewer than 2 elements.
     */
    fun decodeFuelRate(bytes: ByteArray): Float? {
        if (bytes.size < 2) return null
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        return ((a * 256) + b) / 20f
    }

    /**
     * Parses battery voltage from an ATRV (AT Read Voltage) response string.
     *
     * The ELM327 responds to ATRV with a string like "14.2V". This function strips the
     * trailing "V" and parses the numeric portion.
     *
     * @param atResponse The text field from [OBDResponse.ATResponse] for an ATRV command.
     * @return Voltage in volts, or null if [atResponse] does not match the expected format.
     */
    fun decodeBatteryVoltage(atResponse: String): Float? {
        val trimmed = atResponse.trim().removeSuffix("V")
        return trimmed.toFloatOrNull()
    }
}
