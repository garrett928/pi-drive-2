package ghart.space.pi_drive.shared.auto

import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Unit tests for [buildDialsTemplateData].
 *
 * Tests operate on the pure data-transformation function rather than [DialsScreen.onGetTemplate]
 * directly, avoiding the need for a [CarContext] in unit tests. This covers:
 * - Speed km/h → mph conversion and null handling.
 * - RPM danger threshold prefix "⚠".
 * - Coolant danger threshold prefix "⚠".
 * - Trip distance and MPG selection (auto trip preferred over manual).
 * - Battery voltage formatting and null fallback.
 * - [isStreaming] flag from connection state.
 */
class DialsScreenTest {

    private val connectedState = ConnectionState.Connected(
        adapterName = "OBDLink LX",
        protocol = "ISO 15765-4",
        pollRateHz = 4f,
    )
    private val disconnectedState = ConnectionState.Disconnected()

    private val emptyManualTrip = ManualTripState(
        isActive = false,
        distanceMiles = 0f,
        durationMs = 0L,
        avgSpeedMph = 0f,
        maxSpeedMph = 0f,
        avgMpg = null,
        startDate = null,
    )

    // ── Speed ─────────────────────────────────────────────────────────────────

    @Test
    fun speedText_convertsKmhToMph() {
        val snap = VehicleSnapshot(speedKmh = 97)  // 97 km/h ≈ 60 mph
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertEquals("60", data.speedText)
    }

    @Test
    fun speedText_null_showsDash() {
        val snap = VehicleSnapshot(speedKmh = null)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertEquals("—", data.speedText)
    }

    // ── RPM ───────────────────────────────────────────────────────────────────

    @Test
    fun rpmText_belowDangerThreshold_noPrefix() {
        val snap = VehicleSnapshot(rpm = 3000)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertEquals("3000", data.rpmText)
    }

    @Test
    fun rpmText_atDangerThreshold_hasWarningPrefix() {
        val snap = VehicleSnapshot(rpm = 6500)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertTrue("expected ⚠ prefix", data.rpmText.startsWith("⚠"))
    }

    @Test
    fun rpmText_aboveDangerThreshold_hasWarningPrefix() {
        val snap = VehicleSnapshot(rpm = 7200)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertTrue("expected ⚠ prefix", data.rpmText.startsWith("⚠"))
        assertTrue("expected rpm value in text", data.rpmText.contains("7200"))
    }

    @Test
    fun rpmText_null_showsDash() {
        val snap = VehicleSnapshot(rpm = null)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertEquals("—", data.rpmText)
    }

    // ── Coolant ───────────────────────────────────────────────────────────────

    @Test
    fun coolantText_normalTemp_noPrefix() {
        // 93°C = 199°F — below 240°F danger
        val snap = VehicleSnapshot(coolantTempC = 93)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertFalse("should not have ⚠ at normal temp", data.coolantText.startsWith("⚠"))
        assertTrue("should contain °F", data.coolantText.contains("°F"))
    }

    @Test
    fun coolantText_aboveDangerThreshold_hasWarningPrefix() {
        // 116°C = 241°F — above 240°F danger
        val snap = VehicleSnapshot(coolantTempC = 116)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertTrue("expected ⚠ prefix for high coolant", data.coolantText.startsWith("⚠"))
    }

    // ── Trip distance (auto preferred over manual) ─────────────────────────────

    @Test
    fun tripDistText_autoTripTakesPrecedenceOverManual() {
        val autoTrip = makeAutoTripState(distanceMiles = 14.3f)
        val manual = emptyManualTrip.copy(isActive = true, distanceMiles = 248.6f)
        val data = buildDialsTemplateData(VehicleSnapshot.EMPTY, manual, autoTrip, disconnectedState)
        // auto trip distance wins
        assertEquals("14.3 mi", data.tripDistText)
    }

    @Test
    fun tripDistText_noAutoTrip_usesManualWhenActive() {
        val manual = emptyManualTrip.copy(isActive = true, distanceMiles = 248.6f)
        val data = buildDialsTemplateData(VehicleSnapshot.EMPTY, manual, null, disconnectedState)
        assertEquals("248.6 mi", data.tripDistText)
    }

    @Test
    fun tripDistText_noTripAtAll_showsDash() {
        val data = buildDialsTemplateData(VehicleSnapshot.EMPTY, emptyManualTrip, null, disconnectedState)
        assertEquals("—", data.tripDistText)
    }

    // ── Battery ───────────────────────────────────────────────────────────────

    @Test
    fun batteryText_formatsOneDecimalPlace() {
        val snap = VehicleSnapshot(batteryVoltage = 14.2f)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertEquals("14.2 V", data.batteryText)
    }

    @Test
    fun batteryText_null_showsDash() {
        val snap = VehicleSnapshot(batteryVoltage = null)
        val data = buildDialsTemplateData(snap, emptyManualTrip, null, disconnectedState)
        assertEquals("—", data.batteryText)
    }

    // ── Streaming flag ─────────────────────────────────────────────────────────

    @Test
    fun isStreaming_true_whenConnected() {
        val data = buildDialsTemplateData(VehicleSnapshot.EMPTY, emptyManualTrip, null, connectedState)
        assertTrue(data.isStreaming)
    }

    @Test
    fun isStreaming_false_whenDisconnected() {
        val data = buildDialsTemplateData(VehicleSnapshot.EMPTY, emptyManualTrip, null, disconnectedState)
        assertFalse(data.isStreaming)
    }

    // ── Item count (integration-style sanity check) ────────────────────────────

    @Test
    fun buildDialsTemplateData_allFieldsPresent() {
        val snap = VehicleSnapshot(
            speedKmh = 80,
            rpm = 2500,
            coolantTempC = 90,
            batteryVoltage = 13.8f,
        )
        val manual = emptyManualTrip.copy(isActive = true, distanceMiles = 12.0f, avgMpg = 28.5f)
        val data = buildDialsTemplateData(snap, manual, null, connectedState)
        // Verify all six fields are non-empty
        assertTrue(data.speedText.isNotBlank())
        assertTrue(data.rpmText.isNotBlank())
        assertTrue(data.coolantText.isNotBlank())
        assertTrue(data.tripDistText.isNotBlank())
        assertTrue(data.tripMpgText.isNotBlank())
        assertTrue(data.batteryText.isNotBlank())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeAutoTripState(distanceMiles: Float = 0f, avgMpg: Float? = null): AutoTripState =
        AutoTripState(
            tripId = 1L,
            startTime = Instant.now(),
            distanceMiles = distanceMiles,
            durationMs = 0L,
            avgSpeedMph = 0f,
            maxSpeedMph = 0f,
            avgMpg = avgMpg,
            eventCount = 0,
        )
}
