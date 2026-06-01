package ghart.space.pi_drive.shared

import ghart.space.pi_drive.shared.data.OBDVehicleDataSource
import ghart.space.pi_drive.shared.detection.DetectionConfig
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.detection.AccelerationDetector
import ghart.space.pi_drive.shared.obd.InitStep
import ghart.space.pi_drive.shared.obd.InitializationSequence
import ghart.space.pi_drive.shared.obd.MockTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * End-to-end pipeline tests running entirely on the JVM (no Android framework).
 *
 * Test 1 — full stack:
 *   [MockTransport] → [InitializationSequence] → [OBDVehicleDataSource] → populated snapshots
 *
 * Test 2 — detection integration:
 *   hand-crafted [VehicleSnapshot] sequence → [AccelerationDetector] → [DrivingEvent]
 *
 * Both tests run in [runTest] with [StandardTestDispatcher] for deterministic coroutine
 * scheduling. The [OBDVehicleDataSource] polling loop runs in the test's [backgroundScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FullPipelineTest {

    // ── Transport → InitSequence → DataSource ─────────────────────────────────

    /**
     * Verifies that the full initialization path through [MockTransport] produces a
     * valid [InitStep.Complete] with a populated supported-PID set, and that the
     * subsequent [OBDVehicleDataSource] delivers snapshots with the expected default
     * speed value baked into [MockTransport] (80 km/h, 0x50).
     */
    @Test
    fun `full pipeline delivers populated snapshots from MockTransport`() = runTest {
        val transport = MockTransport()
        transport.connect()

        // Run init sequence and collect all steps
        val steps = InitializationSequence(transport).run().toList()
        val complete = steps.filterIsInstance<InitStep.Complete>().firstOrNull()
        assertNotNull("Initialization must complete", complete)
        val initResult = complete!!.result

        assertTrue("Supported PIDs must not be empty", initResult.supportedPids.isNotEmpty())
        // MockTransport's bitmap declares speed (0x0D) and RPM (0x0C) as supported
        assertTrue("Speed PID 0x0D must be supported", initResult.supportedPids.contains(0x0D))
        assertTrue("RPM PID 0x0C must be supported", initResult.supportedPids.contains(0x0C))

        // Create data source using the PIDs the init sequence discovered
        val dataSource = OBDVehicleDataSource(
            transport = transport,
            initialSupportedPids = initResult.supportedPids,
            coroutineScope = backgroundScope,
            adapterName = "MockAdapter",
            protocol = initResult.protocol ?: "Test",
        )
        dataSource.startPolling()

        // Collect snapshots until we have one with speed populated
        val snapshot = dataSource.snapshot
            .filter { it.speedKmh != null }
            .first()

        dataSource.stopPolling()

        // MockTransport returns "41 0D 50" → 0x50 = 80 km/h
        assertEquals("Speed should be MockTransport default (80 km/h)", 80, snapshot.speedKmh)
        assertNotNull("RPM must be populated", snapshot.rpm)
        assertEquals("RPM should be MockTransport default (2500)", 2500, snapshot.rpm)
    }

    /**
     * Verifies that the [OBDVehicleDataSource] produces at least [SNAPSHOT_COUNT]
     * consecutive snapshots, each carrying non-null speed and RPM.
     */
    @Test
    fun `data source emits multiple populated snapshots in sequence`() = runTest {
        val transport = MockTransport()
        transport.connect()

        val dataSource = OBDVehicleDataSource(
            transport = transport,
            initialSupportedPids = setOf(0x0D, 0x0C, 0x05, 0x0F, 0x10, 0x11, 0x2F, 0x5C, 0x5E),
            coroutineScope = backgroundScope,
        )
        dataSource.startPolling()

        val snapshots = dataSource.snapshot
            .filter { it.speedKmh != null }
            .take(SNAPSHOT_COUNT)
            .toList()

        dataSource.stopPolling()

        assertEquals("Should collect exactly $SNAPSHOT_COUNT snapshots", SNAPSHOT_COUNT, snapshots.size)
        snapshots.forEach { snap ->
            assertNotNull("Every snapshot must have speed", snap.speedKmh)
            assertNotNull("Every snapshot must have RPM", snap.rpm)
        }
    }

    // ── AccelerationDetector integration ──────────────────────────────────────

    /**
     * Feeds a hard-braking speed sequence directly into [AccelerationDetector] and
     * verifies a [EventType.HARD_BRAKE] event is emitted.
     *
     * Speed sequence (100 ms steps, each 2 km/h drop ≈ 12.4 mph/s):
     *   anchor: 97 km/h → detects at first drop → event fires after minEventDurationMs
     */
    @Test
    fun `AccelerationDetector emits HARD_BRAKE event on rapid deceleration`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshotFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val configFlow: MutableStateFlow<DetectionConfig> = MutableStateFlow(
            DetectionConfig(
                accelEnabled = true,
                accelHardBrakeThreshold = 6.5f,
                minEventDurationMs = 400,
                cooldownMs = 60_000,
            )
        )

        val detector = AccelerationDetector(snapshotFlow, configFlow)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val collectJob = launch(dispatcher) {
            detector.events().collect { events.add(it) }
        }

        // Emit speed-drop sequence to trigger detection
        val brakeSnaps = listOf(
            snap(97, 0),
            snap(95, 100),
            snap(93, 200),
            snap(91, 300),
            snap(89, 400),
            snap(87, 500),
        )
        for (s in brakeSnaps) {
            snapshotFlow.value = s
            advanceUntilIdle()
        }

        assertTrue(
            "Expected at least one HARD_BRAKE event, got: $events",
            events.any { it.type == EventType.HARD_BRAKE }
        )

        collectJob.cancel()
    }

    /**
     * Verifies that no event is emitted when the deceleration rate stays below the
     * configured threshold (simulates normal braking or GPS noise).
     */
    @Test
    fun `AccelerationDetector does NOT emit event below threshold`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshotFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val configFlow: MutableStateFlow<DetectionConfig> = MutableStateFlow(
            DetectionConfig(
                accelEnabled = true,
                accelHardBrakeThreshold = 15f, // high threshold — won't trigger
                minEventDurationMs = 400,
                cooldownMs = 60_000,
            )
        )

        val detector = AccelerationDetector(snapshotFlow, configFlow)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val collectJob = launch(dispatcher) {
            detector.events().collect { events.add(it) }
        }

        // 2 km/h drop per 100ms ≈ 12.4 mph/s < 15 mph/s threshold
        val softSnaps = listOf(
            snap(80, 0),
            snap(78, 100),
            snap(76, 200),
            snap(74, 300),
        )
        for (s in softSnaps) {
            snapshotFlow.value = s
            advanceUntilIdle()
        }

        assertTrue("No events expected below threshold, got: $events", events.isEmpty())

        collectJob.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a [VehicleSnapshot] with OBD speed at [kmh] and timestamp at [ms] after EPOCH. */
    private fun snap(kmh: Int, ms: Long) = VehicleSnapshot(
        timestamp = Instant.EPOCH.plusMillis(ms),
        speedKmh = kmh,
    )

    private companion object {
        const val SNAPSHOT_COUNT = 5
    }
}
