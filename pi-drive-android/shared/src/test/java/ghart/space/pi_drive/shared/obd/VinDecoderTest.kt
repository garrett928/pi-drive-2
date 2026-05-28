package ghart.space.pi_drive.shared.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VinDecoderTest {

    // ── parseVinResponse ──────────────────────────────────────────────────

    /**
     * VIN: JF1VA1E66G9362045 (Subaru)
     * ASCII → hex: 4A 46 31 56 41 31 45 36 36 47 39 33 36 32 30 34 35
     * Full response bytes: 49 02 01 [17 VIN bytes]
     */
    @Test
    fun `parseVinResponse single-line spaced hex returns correct VIN`() {
        val raw = "49 02 01 4A 46 31 56 41 31 45 36 36 47 39 33 36 32 30 34 35"
        assertEquals("JF1VA1E66G9362045", VinDecoder.parseVinResponse(raw))
    }

    @Test
    fun `parseVinResponse unspaced hex returns correct VIN`() {
        val raw = "4902014A46315641314536364739333632303435"
        assertEquals("JF1VA1E66G9362045", VinDecoder.parseVinResponse(raw))
    }

    @Test
    fun `parseVinResponse multi-frame response with frame labels returns correct VIN`() {
        // Simulates ELM327 multi-frame response: "014" count line, then "1:", "2:", "3:" frames
        val raw = "014\n1:490201 4A4631\n2:56413145363647\n3:39333632303435"
        assertEquals("JF1VA1E66G9362045", VinDecoder.parseVinResponse(raw))
    }

    @Test
    fun `parseVinResponse NO DATA returns null`() {
        assertNull(VinDecoder.parseVinResponse("NO DATA"))
    }

    @Test
    fun `parseVinResponse empty string returns null`() {
        assertNull(VinDecoder.parseVinResponse(""))
    }

    @Test
    fun `parseVinResponse response too short returns null`() {
        // Only 10 VIN bytes, not 17
        val raw = "49 02 01 4A 46 31 56 41 31 45 36 36 47"
        assertNull(VinDecoder.parseVinResponse(raw))
    }

    // ── decodeVin — core fields ───────────────────────────────────────────

    @Test
    fun `decodeVin JF1VA1E66G9362045 returns year 2016 and make Subaru`() {
        val info = VinDecoder.decodeVin("JF1VA1E66G9362045")
        assertEquals(2016, info.year)
        assertEquals("Subaru", info.make)
        assertEquals("JF1VA1E66G9362045", info.vin)
    }

    @Test
    fun `decodeVin maskedVin shows first 5 and last 8 characters`() {
        // Format: vin[0..4] + " **** " + vin[9..16]
        val info = VinDecoder.decodeVin("JF1VA1E66G9362045")
        assertEquals("JF1VA **** G9362045", info.maskedVin)
    }

    @Test
    fun `decodeVin model is always null`() {
        assertNull(VinDecoder.decodeVin("JF1VA1E66G9362045").model)
    }

    @Test
    fun `decodeVin unknown WMI returns null make`() {
        // "ZZZ" is not in the WMI table
        assertNull(VinDecoder.decodeVin("ZZZ123456G1234567").make)
    }

    // ── decodeVin — year codes ────────────────────────────────────────────

    @Test
    fun `decodeVin digit 5 at position 10 maps to 2005`() {
        // VIN: 1HG12345651234567 — 17 chars, index 9 = '5'
        val info = VinDecoder.decodeVin("1HG12345651234567")
        assertEquals(2005, info.year)
    }

    @Test
    fun `decodeVin digit 9 at position 10 maps to 2009`() {
        // VIN: 1HGCM568991234567 — 17 chars, index 9 = '9'
        val info = VinDecoder.decodeVin("1HGCM568991234567")
        assertEquals(2009, info.year)
    }

    @Test
    fun `decodeVin letter A at position 10 maps to 2010`() {
        // VIN: W0L000000A0000001 — 17 chars, index 9 = 'A'
        val info = VinDecoder.decodeVin("W0L000000A0000001")
        assertEquals(2010, info.year)
    }

    @Test
    fun `decodeVin letter Y at position 10 maps to 2000`() {
        // VIN: 1HGCM5689Y0000001 — 17 chars, index 9 = 'Y'
        val info = VinDecoder.decodeVin("1HGCM5689Y0000001")
        assertEquals(2000, info.year)
    }

    @Test
    fun `decodeVin letter G at position 10 maps to 2016`() {
        // 'G' is ambiguous (1986 or 2016); decoder always prefers 2010+ for letter codes
        val info = VinDecoder.decodeVin("JF1VA1E66G9362045")
        assertEquals(2016, info.year)
    }

    @Test
    fun `decodeVin unknown year character returns null year`() {
        // 'I' is never a valid VIN year character; should return null
        val info = VinDecoder.decodeVin("1HGCM5689I0000001")
        assertNull(info.year)
    }

    // ── decodeVin — WMI → make lookups ────────────────────────────────────

    @Test
    fun `decodeVin BMW WMI returns BMW`() {
        val info = VinDecoder.decodeVin("WBAXXX1234A000001")
        assertEquals("BMW", info.make)
    }

    @Test
    fun `decodeVin Toyota WMI returns Toyota`() {
        val info = VinDecoder.decodeVin("JTD123456A1234567")
        assertEquals("Toyota", info.make)
    }

    @Test
    fun `decodeVin Honda WMI returns Honda`() {
        val info = VinDecoder.decodeVin("1HGCM5689A0000001")
        assertEquals("Honda", info.make)
    }

    // ── decodeVin — validation ────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `decodeVin with fewer than 17 characters throws`() {
        VinDecoder.decodeVin("JF1VA")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeVin with more than 17 characters throws`() {
        VinDecoder.decodeVin("JF1VA1E66G9362045X")
    }
}
