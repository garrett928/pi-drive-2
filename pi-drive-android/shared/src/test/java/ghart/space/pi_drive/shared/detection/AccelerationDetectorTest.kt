package ghart.space.pi_drive.shared.detection

import ghart.space.pi_drive.shared.data.model.DataSource
import ghart.space.pi_drive.shared.data.model.EventType
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
import java.time.Instant

/**
 * Unit tests for [AccelerationDetector].
 *
 * Speed values use OBD km/h unless stated otherwise. A 100ms interval at a 2 km/h drop
 * per step corresponds to roughly 1.24 mph per 0.1s = ~12.4 mph/s — comfortably above
 * both thresholds (9 accel, 6.5 brake). Tests that want a specific rate calibrate
 * km/h values explicitly.
 *
 * All instants start from [Instant.EPOCH] to keep tests deterministic and independent
 * of wall-clock time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccelerationDetectorTest {

    private val dispatcher = StandardTestDispatcher()

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a snapshot with OBD speed at the given km/h and explicit timestamp. */
    private fun snapKmh(kmh: Int, ms: Long) = VehicleSnapshot(
        timestamp = Instant.EPOCH.plusMillis(ms),
        speedKmh = kmh,
    )

    /** Creates a snapshot using GPS speed (m/s) with no OBD speed. */
    private fun snapGps(mps: Float, ms: Long) = VehicleSnapshot(
        timestamp = Instant.EPOCH.plusMillis(ms),
        speedKmh = null,
        gpsSpeedMps = mps,
    )

    /**
     * Feeds [snaps] into [flow] one at a time with [advanceUntilIdle] between each,
     * ensuring the detector processes every emission individually.
     */
    private suspend fun feedAll(
        flow: MutableStateFlow<VehicleSnapshot>,
        snaps: List<VehicleSnapshot>,
        testScope: kotlinx.coroutines.test.TestScope,
    ) {
        for (snap in snaps) {
            flow.value = snap
            testScope.advanceUntilIdle()
        }
    }

    // ── Hard brake ────────────────────────────────────────────────────────────

    /**
     * 60 → 50 mph over 1s is a 10 mph/s deceleration. With a 6.5 threshold and 500ms
     * minimum, this should produce a HARD_BRAKE event.
     *
     * Sequence (100ms steps, each -2 km/h = ~1.24 mph → ~12.4 mph/s):
     * snap0: 97 km/h (anchor)
     * snap1..5: 95, 93, 91, 89, 87 km/h → detection starts at snap1, event at snap5 (400ms)
     */
    @Test
    fun `hard brake emits event above threshold and after min duration`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            accelHardBrakeThreshold = 6.5f,
            minEventDurationMs = 400,
            cooldownMs = 60_000,
        )
        val detector = AccelerationDetector(flow, config)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()

        val job = launch { detector.events().collect { events.add(it) } }

        feedAll(flow, listOf(
            snapKmh(97, 0),
            snapKmh(95, 100),
            snapKmh(93, 200),
            snapKmh(91, 300),
            snapKmh(89, 400),
            snapKmh(87, 500),
        ), this)

        job.cancel()

        assertEquals("Expected 1 hard-brake event", 1, events.size)
        assertEquals(EventType.HARD_BRAKE, events[0].type)
        assertTrue("rate should exceed threshold", events[0].rateMphS!! >= 6.5f)
        assertTrue(events[0].sources.contains(DataSource.OBD))
    }

    // ── Hard accel ────────────────────────────────────────────────────────────

    /**
     * 30 → 42 mph over 1s is a 12 mph/s acceleration. With a 9 threshold and 500ms
     * minimum this should produce a HARD_ACCEL event.
     */
    @Test
    fun `hard accel emits event above threshold and after min duration`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            accelHardAccelThreshold = 9f,
            minEventDurationMs = 400,
            cooldownMs = 60_000,
        )
        val detector = AccelerationDetector(flow, config)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // ~30 mph = 48 km/h, +2 km/h per 100ms → 2*0.621371/0.1 = 12.4 mph/s
        feedAll(flow, listOf(
            snapKmh(48, 0),
            snapKmh(50, 100),
            snapKmh(52, 200),
            snapKmh(54, 300),
            snapKmh(56, 400),
            snapKmh(58, 500),
        ), this)

        job.cancel()

        assertEquals("Expected 1 hard-accel event", 1, events.size)
        assertEquals(EventType.HARD_ACCEL, events[0].type)
        assertTrue(events[0].rateMphS!! >= 9f)
    }

    // ── Below threshold ───────────────────────────────────────────────────────

    /**
     * 60 → 55 mph over 1s is only 5 mph/s, below the 6.5 brake threshold.
     * No event should be emitted.
     */
    @Test
    fun `below threshold does not emit event`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            accelHardBrakeThreshold = 6.5f,
            minEventDurationMs = 400,
        )
        val detector = AccelerationDetector(flow, config)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // ~60 mph = 97 km/h, -1 km/h per 200ms → 0.621/0.2 = 3.1 mph/s < 6.5
        feedAll(flow, listOf(
            snapKmh(97, 0),
            snapKmh(96, 200),
            snapKmh(95, 400),
            snapKmh(94, 600),
            snapKmh(93, 800),
        ), this)

        job.cancel()

        assertTrue("Expected no events below threshold", events.isEmpty())
    }

    // ── Transient spike ───────────────────────────────────────────────────────

    /**
     * A single snap that crosses the threshold but then immediately recovers does
     * not accumulate enough duration — no event should fire.
     */
    @Test
    fun `transient spike below min duration does not emit event`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            accelHardBrakeThreshold = 6.5f,
            minEventDurationMs = 500,
        )
        val detector = AccelerationDetector(flow, config)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        feedAll(flow, listOf(
            snapKmh(97, 0),
            snapKmh(85, 100),  // large drop → exceeds threshold (DETECTING starts)
            snapKmh(97, 200),  // immediate recovery → IDLE, only 100ms < 500ms
        ), this)

        job.cancel()

        assertTrue("Transient spike should not emit event", events.isEmpty())
    }

    // ── Sustained event ───────────────────────────────────────────────────────

    /**
     * A braking event sustained for 2s should fire, and the reported peak rate should
     * reflect the actual magnitude of deceleration.
     */
    @Test
    fun `sustained event fires after min duration and reports peak rate`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            accelHardBrakeThreshold = 6.5f,
            minEventDurationMs = 500,
            cooldownMs = 60_000,
        )
        val detector = AccelerationDetector(flow, config)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // -2 km/h per 100ms → ~12.4 mph/s for 1000ms total
        feedAll(flow, listOf(
            snapKmh(100, 0),
            snapKmh(98,  100),  // DETECTING
            snapKmh(96,  200),
            snapKmh(94,  300),
            snapKmh(92,  400),
            snapKmh(90,  500),
            snapKmh(88,  600),  // duration = 500ms → EVENT
        ), this)

        job.cancel()

        assertEquals(1, events.size)
        val e = events[0]
        assertTrue("Peak rate should be >= 12 mph/s", e.rateMphS!! >= 12f)
        assertTrue("Duration should be >= 500ms", e.durationMs >= 500)
    }

    // ── Zero speed ────────────────────────────────────────────────────────────

    /**
     * A stopped car (speed = 0 throughout) should never produce events.
     */
    @Test
    fun `zero speed car produces no events`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val detector = AccelerationDetector(flow)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        feedAll(flow, listOf(
            snapKmh(0, 0),
            snapKmh(0, 100),
            snapKmh(0, 200),
            snapKmh(0, 500),
            snapKmh(0, 1000),
        ), this)

        job.cancel()

        assertTrue("No events for stopped car", events.isEmpty())
    }

    // ── GPS fallback ──────────────────────────────────────────────────────────

    /**
     * When OBD speed goes stale (no update for > 500ms), the detector should fall
     * back to GPS speed and continue detecting events normally.
     */
    @Test
    fun `GPS fallback used when OBD speed is stale`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            accelHardBrakeThreshold = 6.5f,
            minEventDurationMs = 400,
            cooldownMs = 60_000,
        )
        val detector = AccelerationDetector(flow, config)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // Establish OBD baseline at t=0
        flow.value = VehicleSnapshot(
            timestamp = Instant.EPOCH,
            speedKmh = 100,  // ~62 mph
        )
        advanceUntilIdle()

        // OBD goes silent — now use GPS snapshots. Last OBD was at t=0, GPS starts at t=600ms
        // (OBD_STALE_THRESHOLD_MS = 500, so at t=600 OBD is stale).
        // GPS: 27.78 m/s ≈ 62 mph, then drop by ~0.56 m/s per 100ms → ~1.25 mph per 0.1s → ~12.5 mph/s
        for (i in 1..6) {
            flow.value = VehicleSnapshot(
                timestamp = Instant.EPOCH.plusMillis(600L + i * 100),
                speedKmh = null,
                gpsSpeedMps = 27.78f - i * 0.56f,
            )
            advanceUntilIdle()
        }

        job.cancel()

        assertEquals("Expected 1 event using GPS fallback", 1, events.size)
        assertTrue(events[0].sources.contains(DataSource.GPS))
    }

    // ── Multiple events ───────────────────────────────────────────────────────

    /**
     * Two separate hard-braking events 5 seconds apart should each produce a distinct
     * [DrivingEvent]. The 3-second cooldown expires before the second event starts.
     */
    @Test
    fun `two hard brakes 5s apart produce two separate events`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            accelHardBrakeThreshold = 6.5f,
            minEventDurationMs = 400,
            cooldownMs = 3_000,
        )
        val detector = AccelerationDetector(flow, config)
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // First brake event: t=0..600ms
        feedAll(flow, listOf(
            snapKmh(97, 0),
            snapKmh(95, 100),
            snapKmh(93, 200),
            snapKmh(91, 300),
            snapKmh(89, 400),
            snapKmh(87, 500),
        ), this)

        // Steady driving between events: t=1000..4500ms (well past 3s cooldown)
        feedAll(flow, listOf(
            snapKmh(80, 1_000),
            snapKmh(80, 2_000),
            snapKmh(80, 4_500),
        ), this)

        // Second brake event: t=5000..5600ms
        feedAll(flow, listOf(
            snapKmh(80, 5_000),
            snapKmh(78, 5_100),
            snapKmh(76, 5_200),
            snapKmh(74, 5_300),
            snapKmh(72, 5_400),
            snapKmh(70, 5_500),
        ), this)

        job.cancel()

        assertEquals("Expected 2 separate events", 2, events.size)
        assertEquals(EventType.HARD_BRAKE, events[0].type)
        assertEquals(EventType.HARD_BRAKE, events[1].type)
    }

    // ── Disabled detector ─────────────────────────────────────────────────────

    @Test
    fun `disabled detector emits no events`() = runTest(dispatcher) {
        val flow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val detector = AccelerationDetector(flow, DetectionConfig(accelEnabled = false))
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        feedAll(flow, listOf(
            snapKmh(97, 0),
            snapKmh(80, 100),
            snapKmh(60, 200),
        ), this)

        job.cancel()

        assertTrue("Disabled detector should emit no events", events.isEmpty())
    }
}
