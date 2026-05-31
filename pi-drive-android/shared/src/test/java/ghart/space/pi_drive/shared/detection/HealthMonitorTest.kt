package ghart.space.pi_drive.shared.detection

import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.HealthAlertType
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HealthMonitor].
 *
 * Verifies threshold detection, cooldown suppression, PID-based auto-disable,
 * and per-type enable flags.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthMonitorTest {

    private val dispatcher = StandardTestDispatcher()

    /** All standard OBD PIDs that [HealthMonitor] checks. */
    private val allPids = MutableStateFlow<Set<Int>>(setOf(0x05, 0x2F, 0x0C, 0x0D, 0x42))

    // ── Coolant ───────────────────────────────────────────────────────────────

    @Test
    fun `coolant above threshold fires HIGH_COOLANT alert`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(highCoolantEnabled = true, highCoolantThresholdC = 110f)),
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }

        snapshots.value = VehicleSnapshot(coolantTempC = 115)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, alerts.size)
        assertEquals(HealthAlertType.HIGH_COOLANT, alerts[0].type)
        assertEquals(115f, alerts[0].value)
    }

    @Test
    fun `coolant below threshold does not fire alert`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(highCoolantEnabled = true, highCoolantThresholdC = 110f)),
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }

        snapshots.value = VehicleSnapshot(coolantTempC = 90)
        advanceUntilIdle()
        job.cancel()

        assertTrue("Below threshold should not fire", alerts.isEmpty())
    }

    // ── Cooldown ──────────────────────────────────────────────────────────────

    /**
     * A second coolant alert fired within the cooldown window should be suppressed.
     * The wall clock is advanced to 30 s, but the cooldown is 60 s.
     *
     * The subscriber and the monitor's collection coroutine start with an initial idle
     * advance so that the cooldown clock's zero-time epoch doesn't block the first fire.
     */
    @Test
    fun `second alert within cooldown window is suppressed`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        var currentTime = 0L
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(
                highCoolantEnabled = true,
                highCoolantThresholdC = 110f,
                highCoolantCooldownMs = 60_000L,
            )),
            clock = { currentTime },
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }
        advanceUntilIdle() // let the monitor start collecting the initial EMPTY snapshot

        // First fire at t=0 — lastFire is null so it always fires
        snapshots.value = VehicleSnapshot(coolantTempC = 115)
        advanceUntilIdle()

        // Still within cooldown — 30 s < 60 s
        currentTime = 30_000L
        snapshots.value = VehicleSnapshot(coolantTempC = 116)
        advanceUntilIdle()

        job.cancel()

        assertEquals("Only first alert fires within cooldown", 1, alerts.size)
    }

    /**
     * After the cooldown expires the same alert type should fire again.
     */
    @Test
    fun `alert refires after cooldown window expires`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        var currentTime = 0L
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(
                highCoolantEnabled = true,
                highCoolantThresholdC = 110f,
                highCoolantCooldownMs = 60_000L,
            )),
            clock = { currentTime },
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }
        advanceUntilIdle() // let the monitor start collecting the initial EMPTY snapshot

        // First fire at t=0
        snapshots.value = VehicleSnapshot(coolantTempC = 115)
        advanceUntilIdle()

        currentTime = 61_000L // past cooldown
        snapshots.value = VehicleSnapshot(coolantTempC = 116)
        advanceUntilIdle()

        job.cancel()

        assertEquals("Alert fires twice after cooldown expires", 2, alerts.size)
    }

    // ── Low fuel ──────────────────────────────────────────────────────────────

    @Test
    fun `fuel below threshold fires LOW_FUEL alert`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(lowFuelEnabled = true, lowFuelThresholdPct = 10f)),
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }

        snapshots.value = VehicleSnapshot(fuelLevelPct = 8f)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, alerts.size)
        assertEquals(HealthAlertType.LOW_FUEL, alerts[0].type)
        assertEquals(8f, alerts[0].value)
    }

    @Test
    fun `fuel above threshold does not fire alert`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(lowFuelEnabled = true, lowFuelThresholdPct = 10f)),
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }

        snapshots.value = VehicleSnapshot(fuelLevelPct = 15f)
        advanceUntilIdle()
        job.cancel()

        assertTrue("Above threshold should not fire", alerts.isEmpty())
    }

    // ── PID auto-disable ──────────────────────────────────────────────────────

    /**
     * If the vehicle does not expose the coolant-temperature PID, the alert must
     * be silently skipped even if the snapshot contains a value above threshold.
     */
    @Test
    fun `unsupported PID suppresses alert`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        val noPids = MutableStateFlow<Set<Int>>(emptySet())
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = noPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(highCoolantEnabled = true, highCoolantThresholdC = 110f)),
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }

        snapshots.value = VehicleSnapshot(coolantTempC = 120)
        advanceUntilIdle()
        job.cancel()

        assertTrue("Alert must be suppressed for unsupported PID", alerts.isEmpty())
    }

    // ── Disabled alert ────────────────────────────────────────────────────────

    @Test
    fun `disabled alert type never fires`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(highCoolantEnabled = false)),
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }

        snapshots.value = VehicleSnapshot(coolantTempC = 125)
        advanceUntilIdle()
        job.cancel()

        assertTrue("Disabled alert type must not fire", alerts.isEmpty())
    }

    // ── Overspeed (disabled by default) ──────────────────────────────────────

    @Test
    fun `overspeed alert disabled by default does not fire`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            // default config — overspeedEnabled = false
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }

        // 100 mph — well above 75 mph default threshold
        snapshots.value = VehicleSnapshot(speedKmh = 161)
        advanceUntilIdle()
        job.cancel()

        assertTrue("Overspeed disabled by default should not fire", alerts.isEmpty())
    }

    // ── Multiple alert types ──────────────────────────────────────────────────

    /**
     * Multiple different health conditions exceeding their thresholds simultaneously
     * should each fire their own alert independently.
     */
    @Test
    fun `multiple health alert types fire independently`() = runTest(dispatcher) {
        val snapshots = MutableStateFlow(VehicleSnapshot.EMPTY)
        val monitor = HealthMonitor(
            snapshots = snapshots,
            supportedPids = allPids,
            configFlow = MutableStateFlow(HealthMonitorConfig(
                highCoolantEnabled = true,
                highCoolantThresholdC = 110f,
                lowFuelEnabled = true,
                lowFuelThresholdPct = 10f,
            )),
        )

        val alerts = mutableListOf<AlertAction.HealthAlert>()
        val job = launch { monitor.alerts().collect { alerts.add(it) } }

        snapshots.value = VehicleSnapshot(coolantTempC = 115, fuelLevelPct = 5f)
        advanceUntilIdle()
        job.cancel()

        assertEquals("Both HIGH_COOLANT and LOW_FUEL should fire", 2, alerts.size)
        val types = alerts.map { it.type }.toSet()
        assertTrue(HealthAlertType.HIGH_COOLANT in types)
        assertTrue(HealthAlertType.LOW_FUEL in types)
    }
}
