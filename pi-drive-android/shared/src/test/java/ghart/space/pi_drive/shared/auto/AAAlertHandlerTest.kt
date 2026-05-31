package ghart.space.pi_drive.shared.auto

import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.DataSource
import ghart.space.pi_drive.shared.data.model.DetectionStrategy
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.data.model.HealthAlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [buildAAToastMessage], [shouldShowAAToast], and [alertKeyForAA].
 *
 * Tests operate on the internal pure functions extracted from [AAAlertHandler] to avoid
 * needing a [androidx.car.app.CarContext] or coroutine infrastructure in unit tests.
 *
 * Covered scenarios:
 * - Driving event → correct "Hard brake: N mph/s" or "Hard accel: Ng" message
 * - Health alert → message forwarded verbatim
 * - [shouldShowAAToast] respects the [DetectionConfig.aaToastEnabled] flag
 * - [shouldShowAAToast] enforces per-type cooldown
 * - First-ever alert is always shown regardless of cooldown state
 */
class AAAlertHandlerTest {

    // ── buildAAToastMessage ───────────────────────────────────────────────────

    @Test
    fun buildAAToastMessage_hardBrakeWithRate_formatsCorrectly() {
        val event = makeDrivingEvent(type = EventType.HARD_BRAKE, rateMphS = 8.2f)
        val alert = AlertAction.DrivingEventAlert(event)
        assertEquals("Hard brake: 8.2 mph/s", buildAAToastMessage(alert))
    }

    @Test
    fun buildAAToastMessage_hardAccelWithG_formatsCorrectly() {
        val event = makeDrivingEvent(type = EventType.HARD_ACCEL, peakG = 1.1f)
        val alert = AlertAction.DrivingEventAlert(event)
        assertEquals("Hard accel: 1.10g", buildAAToastMessage(alert))
    }

    @Test
    fun buildAAToastMessage_ratePreferredOverPeakG() {
        // Both rateMphS and peakG set — rateMphS takes precedence.
        val event = makeDrivingEvent(
            type = EventType.HARD_BRAKE,
            rateMphS = 6.5f,
            peakG = 0.45f,
        )
        val alert = AlertAction.DrivingEventAlert(event)
        val msg = buildAAToastMessage(alert)
        assertTrue("should show mph/s when rateMphS is set", msg.contains("mph/s"))
        assertFalse("should not show g when rateMphS takes precedence", msg.contains("g"))
    }

    @Test
    fun buildAAToastMessage_noDetailFields_showsTypeOnly() {
        val event = makeDrivingEvent(type = EventType.HARD_BRAKE, rateMphS = null, peakG = null)
        val alert = AlertAction.DrivingEventAlert(event)
        assertEquals("Hard brake", buildAAToastMessage(alert))
    }

    @Test
    fun buildAAToastMessage_healthAlert_forwardsMessageVerbatim() {
        val alert = AlertAction.HealthAlert(
            type = HealthAlertType.HIGH_COOLANT,
            message = "Coolant 235°F",
            value = 235f,
        )
        assertEquals("Coolant 235°F", buildAAToastMessage(alert))
    }

    // ── shouldShowAAToast ─────────────────────────────────────────────────────

    @Test
    fun shouldShowAAToast_disabledFlag_returnsFalse() {
        assertFalse(
            shouldShowAAToast(
                enabled = false,
                key = "HARD_BRAKE",
                lastToastTime = emptyMap(),
                now = 1000L,
                cooldownMs = 10_000L,
            )
        )
    }

    @Test
    fun shouldShowAAToast_enabledAndNoPriorToast_returnsTrue() {
        assertTrue(
            shouldShowAAToast(
                enabled = true,
                key = "HARD_BRAKE",
                lastToastTime = emptyMap(),
                now = 1000L,
                cooldownMs = 10_000L,
            )
        )
    }

    @Test
    fun shouldShowAAToast_withinCooldown_returnsFalse() {
        val lastShown = 1000L
        val now = lastShown + 5_000L  // 5s < 10s cooldown
        assertFalse(
            shouldShowAAToast(
                enabled = true,
                key = "HARD_BRAKE",
                lastToastTime = mapOf("HARD_BRAKE" to lastShown),
                now = now,
                cooldownMs = 10_000L,
            )
        )
    }

    @Test
    fun shouldShowAAToast_afterCooldownExpired_returnsTrue() {
        val lastShown = 1000L
        val now = lastShown + 10_000L  // exactly at cooldown boundary
        assertTrue(
            shouldShowAAToast(
                enabled = true,
                key = "HARD_BRAKE",
                lastToastTime = mapOf("HARD_BRAKE" to lastShown),
                now = now,
                cooldownMs = 10_000L,
            )
        )
    }

    @Test
    fun shouldShowAAToast_differentKeyNotCoolingDown_returnsTrue() {
        // HARD_BRAKE was shown recently, but HARD_ACCEL has no prior record.
        assertTrue(
            shouldShowAAToast(
                enabled = true,
                key = "HARD_ACCEL",
                lastToastTime = mapOf("HARD_BRAKE" to System.currentTimeMillis()),
                now = System.currentTimeMillis(),
                cooldownMs = 10_000L,
            )
        )
    }

    // ── alertKeyForAA ─────────────────────────────────────────────────────────

    @Test
    fun alertKeyForAA_drivingEvent_usesEventTypeName() {
        val alert = AlertAction.DrivingEventAlert(makeDrivingEvent(EventType.HARD_BRAKE))
        assertEquals("HARD_BRAKE", alertKeyForAA(alert))
    }

    @Test
    fun alertKeyForAA_healthAlert_usesHealthAlertTypeName() {
        val alert = AlertAction.HealthAlert(HealthAlertType.LOW_FUEL, "Low fuel", 15f)
        assertEquals("LOW_FUEL", alertKeyForAA(alert))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeDrivingEvent(
        type: EventType = EventType.HARD_BRAKE,
        rateMphS: Float? = null,
        peakG: Float? = null,
    ): DrivingEvent = DrivingEvent(
        strategy = DetectionStrategy.ACCELERATION,
        type = type,
        timestamp = Instant.now(),
        durationMs = 500L,
        rateMphS = rateMphS,
        peakG = peakG,
        peakAccelMps2 = 0f,
        startSpeedMph = 40f,
        endSpeedMph = 30f,
        location = null,
        sources = setOf(DataSource.OBD),
    )
}
