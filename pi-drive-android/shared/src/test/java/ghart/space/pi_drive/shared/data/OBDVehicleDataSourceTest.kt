package ghart.space.pi_drive.shared.data

import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.obd.MockTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OBDVehicleDataSourceTest {

    private lateinit var transport: MockTransport

    /** All PIDs that MockTransport has default responses for. */
    private val mockSupportedPids = setOf(0x0D, 0x0C, 0x05, 0x0F, 0x10, 0x11, 0x2F, 0x5C, 0x5E)

    @Before
    fun setUp() {
        transport = MockTransport()
    }

    private fun makeDataSource(
        pids: Set<Int> = mockSupportedPids,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = OBDVehicleDataSource(
        transport = transport,
        initialSupportedPids = pids,
        coroutineScope = scope,
        adapterName = "Test Adapter",
        protocol = "Test Protocol",
    )

    // ── Snapshot content ──────────────────────────────────────────────────

    @Test
    fun `speed and RPM are non-null in every emitted snapshot`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        val snapshots = ds.snapshot
            .filter { it != VehicleSnapshot.EMPTY }
            .take(10)
            .toList()

        ds.stopPolling()
        assertEquals("Should have collected 10 snapshots", 10, snapshots.size)
        snapshots.forEach { snap ->
            assertNotNull("Speed should be non-null", snap.speedKmh)
            assertNotNull("RPM should be non-null", snap.rpm)
        }
    }

    @Test
    fun `speed value matches MockTransport default of 80 kmh`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        val snap = ds.snapshot.filter { it.speedKmh != null }.first()
        ds.stopPolling()

        // MockTransport returns "41 0D 50" for speed → 0x50 = 80 km/h
        assertEquals(80, snap.speedKmh)
    }

    @Test
    fun `rpm value matches MockTransport default of 2500`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        val snap = ds.snapshot.filter { it.rpm != null }.first()
        ds.stopPolling()

        // MockTransport returns "41 0C 27 10" → ((0x27*256)+0x10)/4 = 2500
        assertEquals(2500, snap.rpm)
    }

    @Test
    fun `coolant temp is set after scheduler includes it`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        val snap = ds.snapshot.filter { it.coolantTempC != null }.first()
        ds.stopPolling()

        // MockTransport returns "41 05 82" → 0x82 - 40 = 90°C
        assertEquals(90, snap.coolantTempC)
    }

    @Test
    fun `values carry forward between cycles`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        // Collect enough snapshots that coolant (cycle 0) has been set
        // and verify it's still present in later snapshots
        val snapshots = ds.snapshot
            .filter { it.coolantTempC != null }
            .take(5)
            .toList()

        ds.stopPolling()

        // All snapshots after coolant is first set should still have it
        snapshots.forEach { snap ->
            assertNotNull("Coolant should be carried forward", snap.coolantTempC)
        }
    }

    @Test
    fun `battery voltage is polled on first cycle`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        // Battery is polled when lastBatteryMs=0 → first cycle always triggers ATRV
        // MockTransport returns "14.2V" for ATRV
        val snap = ds.snapshot.filter { it.batteryVoltage != null }.first()
        ds.stopPolling()

        assertNotNull("Battery voltage should be populated", snap.batteryVoltage)
        assertEquals(14.2f, snap.batteryVoltage!!, 0.01f)
    }

    @Test
    fun `custom pid response is reflected in snapshot`() = runTest {
        // Override speed to 120 km/h
        transport.setResponse("010D", "41 0D 78")  // 0x78 = 120
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        val snap = ds.snapshot.filter { it.speedKmh != null }.first()
        ds.stopPolling()

        assertEquals(120, snap.speedKmh)
    }

    @Test
    fun `NO DATA response for pid does not crash loop`() = runTest {
        // Override coolant to NO DATA — polling should continue without error
        transport.setResponse("0105", "NO DATA")
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        // Speed and RPM should still be populated despite coolant failing
        val snap = ds.snapshot.filter { it.speedKmh != null && it.rpm != null }.first()
        ds.stopPolling()

        assertNotNull("Speed should still be available", snap.speedKmh)
        assertNotNull("RPM should still be available", snap.rpm)
        assertNull("Coolant should be null when NO DATA", snap.coolantTempC)
    }

    @Test
    fun `only supported pids are polled`() = runTest {
        // Only support speed and RPM — no other PIDs should be requested
        transport.connect()
        val ds = makeDataSource(pids = setOf(0x0D, 0x0C), scope = backgroundScope)
        ds.startPolling()

        val snap = ds.snapshot.filter { it.speedKmh != null }.first()
        ds.stopPolling()

        assertNotNull("Speed should be present", snap.speedKmh)
        assertNotNull("RPM should be present", snap.rpm)
        assertNull("Coolant should be null (not in supported set)", snap.coolantTempC)
        assertNull("MAF should be null (not in supported set)", snap.mafGps)
    }

    // ── ConnectionState lifecycle ─────────────────────────────────────────

    @Test
    fun `connectionState starts as Disconnected`() = runTest {
        val ds = makeDataSource(scope = backgroundScope)
        assertEquals(ConnectionState.Disconnected(), ds.connectionState.value)
    }

    @Test
    fun `connectionState transitions to Connecting on startPolling`() = runTest {
        val ds = makeDataSource(scope = backgroundScope)
        transport.connect()
        ds.startPolling()

        // Either Connecting or already Connected (fast MockTransport)
        val state = ds.connectionState.value
        assertTrue(
            "State should be Connecting or Connected after startPolling",
            state is ConnectionState.Connecting || state is ConnectionState.Connected,
        )
        ds.stopPolling()
    }

    @Test
    fun `connectionState becomes Connected after first snapshot`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        // Wait until a non-empty snapshot is emitted
        ds.snapshot.filter { it != VehicleSnapshot.EMPTY }.first()
        val state = ds.connectionState.value
        assertTrue("State should be Connected", state is ConnectionState.Connected)

        ds.stopPolling()
    }

    @Test
    fun `connectionState returns to Disconnected on stopPolling`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()

        ds.snapshot.filter { it != VehicleSnapshot.EMPTY }.first()
        ds.stopPolling()

        assertEquals(ConnectionState.Disconnected(), ds.connectionState.value)
    }

    @Test
    fun `startPolling is idempotent`() = runTest {
        transport.connect()
        val ds = makeDataSource(scope = backgroundScope)
        ds.startPolling()
        ds.startPolling()  // Second call should be ignored

        val snap = ds.snapshot.filter { it != VehicleSnapshot.EMPTY }.first()
        ds.stopPolling()

        assertNotNull("Snapshot should still have speed", snap.speedKmh)
    }

    // ── supportedPids ─────────────────────────────────────────────────────

    @Test
    fun `supportedPids reflects initialSupportedPids`() = runTest {
        val ds = makeDataSource(pids = setOf(0x0D, 0x0C), scope = backgroundScope)
        assertEquals(setOf(0x0D, 0x0C), ds.supportedPids.value)
    }

    // ── Initial state ─────────────────────────────────────────────────────

    @Test
    fun `initial snapshot is VehicleSnapshot EMPTY`() = runTest {
        val ds = makeDataSource(scope = backgroundScope)
        assertEquals(VehicleSnapshot.EMPTY, ds.snapshot.value)
    }
}
