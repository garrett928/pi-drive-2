package ghart.space.pi_drive.shared.obd

/**
 * Parses OBD-II VIN responses and decodes vehicle identification information from a 17-char VIN.
 *
 * The VIN is read via OBD service 09, PID 02. Because the 17-byte payload exceeds a single CAN
 * frame, the adapter returns a multi-frame response. This decoder handles both single-line
 * (spaces-on) and multi-line (ISO 15765-4 CAN) response formats by locating the "490201" header
 * bytes in the concatenated hex stream.
 *
 * VehicleInfo extraction is best-effort:
 * - Year: derived from the 10th VIN character using the SAE model-year code table.
 * - Make: looked up from the World Manufacturer Identifier (first 3 characters) against a
 *   table of common manufacturers. Returns null for unknown WMIs.
 * - Model: not decoded (insufficient data from OBD alone; requires a separate lookup table).
 *
 * Reference: SAE J1979, ISO 3779 (VIN structure), ISO 3780 (WMI).
 */
object VinDecoder {

    // ── Year code table ───────────────────────────────────────────────────

    /**
     * Maps the 10th VIN character (model year code) to a calendar year.
     *
     * The letter codes repeat on a 30-year cycle (e.g. 'G' = 1986 or 2016).
     * When a code appears in both cycles, the 2010+ value is used here because
     * this is a best-effort decoder aimed at modern vehicles.
     *
     * Characters 'I', 'O', and 'Q' are never used as VIN characters.
     */
    private val YEAR_CODES: Map<Char, Int> = mapOf(
        'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013,
        'E' to 2014, 'F' to 2015, 'G' to 2016, 'H' to 2017,
        'J' to 2018, 'K' to 2019, 'L' to 2020, 'M' to 2021,
        'N' to 2022, 'P' to 2023, 'R' to 2024, 'S' to 2025,
        'T' to 2026, 'V' to 2027, 'W' to 2028, 'X' to 2029,
        'Y' to 2000,
        '1' to 2001, '2' to 2002, '3' to 2003, '4' to 2004, '5' to 2005,
        '6' to 2006, '7' to 2007, '8' to 2008, '9' to 2009,
    )

    // ── WMI → Make table ──────────────────────────────────────────────────

    /** Maps the first 3 VIN characters (WMI) to a manufacturer name. */
    private val WMI_MAKE: Map<String, String> = mapOf(
        // United States
        "1G1" to "Chevrolet", "1G4" to "Buick", "1G6" to "Cadillac",
        "1FA" to "Ford", "1FB" to "Ford", "1FC" to "Ford",
        "1FD" to "Ford", "1FT" to "Ford",
        "1HG" to "Honda",
        "1J4" to "Jeep", "1J8" to "Jeep",
        "1N4" to "Nissan", "1N6" to "Nissan",
        "1VW" to "Volkswagen",
        "1YV" to "Mazda",
        // Canada
        "2HG" to "Honda", "2G1" to "Chevrolet",
        // Mexico
        "3N1" to "Nissan", "3VW" to "Volkswagen",
        // Japan
        "JF1" to "Subaru", "JF2" to "Subaru",
        "JHM" to "Honda",
        "JM1" to "Mazda", "JM3" to "Mazda",
        "JN1" to "Nissan", "JN3" to "Nissan", "JN8" to "Nissan",
        "JT2" to "Toyota", "JT3" to "Toyota", "JT4" to "Toyota",
        "JTA" to "Toyota", "JTD" to "Toyota", "JTE" to "Toyota",
        "JTH" to "Lexus", "JTJ" to "Lexus",
        // Korea
        "KL7" to "Chevrolet", "KM8" to "Hyundai",
        "KMH" to "Hyundai", "KNA" to "Kia", "KNB" to "Kia", "KND" to "Kia",
        // United Kingdom
        "SAJ" to "Jaguar", "SAL" to "Land Rover",
        // Germany
        "WA1" to "Audi", "WAU" to "Audi",
        "WBA" to "BMW", "WBS" to "BMW M",
        "WDB" to "Mercedes-Benz", "WDC" to "Mercedes-Benz", "WDD" to "Mercedes-Benz",
        "WMW" to "MINI",
        "WP0" to "Porsche", "WP1" to "Porsche",
        "WVW" to "Volkswagen", "WVG" to "Volkswagen",
        // Sweden
        "YV1" to "Volvo",
        // Italy
        "ZFF" to "Ferrari",
    )

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Extracts a 17-character ASCII VIN from a raw OBD service 09, PID 02 adapter response.
     *
     * Handles both single-line spaced hex (`"49 02 01 4A 46 31 …"`) and multi-line CAN
     * multi-frame responses (e.g. three lines prefixed with `"1:"`, `"2:"`, `"3:"`).
     *
     * The function searches for the `"490201"` header bytes in the concatenated hex stream,
     * then decodes the following 34 hex characters (17 bytes) as ASCII.
     *
     * @param rawResponse Raw string received from [OBDTransport.send] for a "0902" command.
     * @return 17-character VIN string, or null if the response cannot be parsed.
     */
    fun parseVinResponse(rawResponse: String): String? {
        // Normalize: strip frame counters (e.g. "014"), frame labels ("1:", "2:"), prompts, spaces
        val hexStream = rawResponse
            .replace("\r", "\n")
            .split("\n")
            .map { line ->
                line.replace(">", "")
                    .trim()
                    // Remove single/double-hex frame label prefix ("1:", "0A:", etc.)
                    .replace(Regex("^[0-9A-Fa-f]{1,2}:"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            // Drop pure count lines like "014" (3 hex chars, no spaces)
            .filterNot { it.length <= 3 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
            .joinToString("")
            .replace(" ", "")
            .uppercase()

        // Locate the "490201" service/PID/count header
        val headerIdx = hexStream.indexOf("490201")
        if (headerIdx == -1) return null

        // VIN data starts immediately after the 6-char header
        val vinHexStart = headerIdx + 6
        val vinHex = hexStream.drop(vinHexStart).take(34)
        if (vinHex.length < 34) return null

        val vin = buildString {
            for (i in 0 until 17) {
                val byte = vinHex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
                val char = byte.toChar()
                if (!char.isLetterOrDigit()) return null
                append(char)
            }
        }
        return if (vin.length == 17) vin else null
    }

    /**
     * Decodes a 17-character VIN into a [VehicleInfo] with year, make, and masked display string.
     *
     * @param vin A valid 17-character VIN string (alphanumeric, no I/O/Q).
     * @return [VehicleInfo] with best-effort year and make. [VehicleInfo.model] is always null
     *         because model cannot be reliably derived from the VIN alone.
     */
    fun decodeVin(vin: String): VehicleInfo {
        require(vin.length == 17) { "VIN must be exactly 17 characters, got ${vin.length}" }

        val wmi = vin.substring(0, 3)
        val yearChar = vin[9]
        val year = YEAR_CODES[yearChar]
        val make = WMI_MAKE[wmi]

        // Mask the middle 4 characters of the VDS (positions 5–8, indices 4–8)
        // to produce a privacy-safe display string: "XXXXX **** XXXXXXXXX"
        val maskedVin = "${vin.substring(0, 5)} **** ${vin.substring(9)}"

        return VehicleInfo(
            vin = vin,
            maskedVin = maskedVin,
            year = year,
            make = make,
            model = null,
        )
    }
}

/**
 * Best-effort vehicle identification information derived from a 17-character VIN.
 *
 * @param vin       The full 17-character VIN.
 * @param maskedVin Privacy-safe display string with positions 5–8 replaced by `"****"`.
 * @param year      Model year decoded from the 10th VIN character, or null if unknown.
 * @param make      Manufacturer name from the WMI (first 3 chars), or null if not in lookup table.
 * @param model     Always null — model cannot be determined from the VIN alone without a full
 *                  decode table.
 */
data class VehicleInfo(
    val vin: String,
    val maskedVin: String,
    val year: Int?,
    val make: String?,
    val model: String?,
)
