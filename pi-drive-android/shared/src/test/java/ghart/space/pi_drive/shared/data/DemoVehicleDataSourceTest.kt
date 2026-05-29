package ghart.space.pi_drive.shared.data

import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DemoVehicleDataSource].
 *
 * Uses [TestScope.backgroundScope] so the long-running polling loop does not
 * cause [kotlinx.coroutines.test.UncompletedCoroutinesError] when the test ends.
 * `backgroundScope` shares the same [kotlinx.coroutines.test.TestCoroutineScheduler],
 * so [advanceTimeBy] in the test body controls virtual time for the source as well.
 *
 * All tests supply [tickIntervalMs] = 50 ms so scenario phases progress quickly.
 *
 * Virtual-clock reference:
 * - t = 0: [startPolling] called
 * - t = 500: adapter-handshake [delay] completes → [ConnectionState.Connected]
 * - t = 500 + n × 50: tick n runs, [VehicleSnapshot] updated
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoVehicleDataSourceTest {

    /**
     * Creates a [DemoVehicleDataSource] tied to [TestScope.backgroundScope].
     *
     * Using `backgroundScope` rather than `this` avoids an
     * [kotlinx.coroutines.test.UncompletedCoroutinesError] at the end of each
     * test while still keeping virtual-clock control.
     */
    private fun TestScope.makeSource(scenario: DemoScenario) = DemoVehicleDataSource(
        scenario       = scenario,
        coroutineScope = backgroundScope,
        tickIntervalMs = 50L,
    )

    // ── Connection state ───────────────────────────────────────────────────

    @Test
    fun `initial connectionState is Disconnected`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        assertEquals(ConnectionState.Disconnected(), source.connectionState.value)
    }

    @Test
    fun `startPolling sets state to Connecting then Connected`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)

        source.startPolling()
        // runCurrent() drains the scheduler — the launched coroutine runs up to its
        // first delay() call and sets Connecting before suspending.
        runCurrent()
        assertEquals(ConnectionState.Connecting, source.connectionState.value)

        // Advance past the 500 ms simulated handshake; Connected is set immediately after.
        advanceTimeBy(600)
        assertTrue(
            "Expected Connected after handshake, got ${source.connectionState.value}",
            source.connectionState.value is ConnectionState.Connected,
        )
    }

    @Test
    fun `Connected adapterName mentions the scenario`() = runTest {
        val source = makeSource(DemoScenario.HIGHWAY)
        source.startPolling()
        advanceTimeBy(600)

        val state = source.connectionState.value as? ConnectionState.Connected
        assertNotNull("Expected Connected state", state)
        assertTrue(
            "adapterName '${state!!.adapterName}' should mention HIGHWAY",
            state.adapterName.contains("HIGHWAY"),
        )
    }

    @Test
    fun `stopPolling resets connectionState to Disconnected`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        source.startPolling()
        advanceTimeBy(600)

        source.stopPolling()
        assertEquals(ConnectionState.Disconnected(), source.connectionState.value)
    }

    @Test
    fun `stopPolling resets snapshot to EMPTY`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        source.startPolling()
        advanceTimeBy(700)  // let a few ticks emit

        source.stopPolling()
        // EMPTY has all nullable fields null; spot-check speedKmh
        assertEquals(VehicleSnapshot.EMPTY.speedKmh, source.snapshot.value.speedKmh)
    }

    @Test
    fun `startPolling is idempotent`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        source.startPolling()
        source.startPolling()  // second call must be a no-op

        advanceTimeBy(600)
        assertTrue(
            "Expected Connected after idempotent startPolling",
            source.connectionState.value is ConnectionState.Connected,
        )
    }

    // ── Snapshot emission ──────────────────────────────────────────────────

    @Test
    fun `CRUISE emits non-null speedKmh within 600 ms of start`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        source.startPolling()
        advanceTimeBy(600)  // past handshake + first tick at t=500

        assertNotNull(
            "CRUISE should emit speedKmh after connecting",
            source.snapshot.value.speedKmh,
        )
    }

    @Test
    fun `CRUISE speedKmh stays within expected highway band`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        source.startPolling()
        advanceTimeBy(1_000)  // several ticks

        val speed = source.snapshot.value.speedKmh
        assertNotNull(speed)
        assertTrue(
            "CRUISE speed $speed km/h should be 85–110",
            speed!! in 85..110,
        )
    }

    @Test
    fun `CRUISE emits non-null RPM`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        source.startPolling()
        advanceTimeBy(600)

        assertNotNull(source.snapshot.value.rpm)
    }

    @Test
    fun `CITY emits non-null speedKmh`() = runTest {
        val source = makeSource(DemoScenario.CITY)
        source.startPolling()
        advanceTimeBy(600)

        assertNotNull(source.snapshot.value.speedKmh)
    }

    @Test
    fun `COLD_START coolant temp is below 40 C at start`() = runTest {
        val source = makeSource(DemoScenario.COLD_START)
        source.startPolling()
        // tick 0 runs at t=500; after advanceTimeBy(600) ticks 0 and 1 have run.
        advanceTimeBy(600)

        val coolant = source.snapshot.value.coolantTempC
        assertNotNull(coolant)
        assertTrue(
            "Cold-start coolant $coolant °C should be below 40 °C",
            coolant!! < 40,
        )
    }

    @Test
    fun `HARD_BRAKE speed drops below 50 km-h during braking phase`() = runTest {
        val source = makeSource(DemoScenario.HARD_BRAKE)
        source.startPolling()

        // cycle = 80 ticks, brakeStart = 60, brakeLen = 8, recovLen = 12.
        // Phase 68 = first tick of recovery (speed = 20 km/h).
        // t = 500 (connect) + 68 × 50 (ticks) = 3 900 ms.
        advanceTimeBy(3_900)

        val speed = source.snapshot.value.speedKmh
        assertNotNull(speed)
        assertTrue(
            "HARD_BRAKE speed $speed km/h should be below 50 during braking/recovery",
            speed!! < 50,
        )
    }

    @Test
    fun `LOW_FUEL fuel level starts at or below 15 percent`() = runTest {
        val source = makeSource(DemoScenario.LOW_FUEL)
        source.startPolling()
        advanceTimeBy(600)

        val fuel = source.snapshot.value.fuelLevelPct
        assertNotNull(fuel)
        assertTrue(
            "LOW_FUEL should start at or below 15 %, got $fuel",
            fuel!! <= 15f,
        )
    }

    // ── Supported PIDs ─────────────────────────────────────────────────────

    @Test
    fun `supportedPids contains RPM and speed PIDs`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        assertTrue("Should support RPM (0x0C)", 0x0C in source.supportedPids.value)
        assertTrue("Should support speed (0x0D)", 0x0D in source.supportedPids.value)
    }

    @Test
    fun `supportedPids contains all 10 expected demo PIDs`() = runTest {
        val source = makeSource(DemoScenario.CRUISE)
        assertEquals(10, source.supportedPids.value.size)
    }
}
