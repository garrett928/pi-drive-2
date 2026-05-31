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
 * Unit tests for [GForceDetector].
 *
 * ## Coordinate conventions
 * - OBD g-force: `abs((dSpeedKmh * 1000/3600) / dt_s) / 9.81`
 *   With dt=100ms and a -1 km/h step: `(1000/3600)/0.1/9.81 ≈ 0.283g` — above the
 *   default 0.265g brake threshold.
 * - GPS g-force: `abs(dSpeedMps / dt_s) / 9.81`
 *   With dt=100ms and a -0.278 m/s step (≈ -1 km/h): same ≈ 0.283g.
 * - Accelerometer: direct m/s² value, divided by 9.81 for g.
 *   2.6 m/s² ≈ 0.265g (just at threshold); 3.0 m/s² ≈ 0.306g (above).
 *
 * ## Severity threshold
 * Default [DetectionConfig.gForceSevereBrakeThreshold] is 0.50g.
 * With -2 km/h per 100ms: ≈ 0.566g — above severe threshold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GForceDetectorTest {

    private val dispatcher = StandardTestDispatcher()
    private val G = 9.81f

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Snapshot with OBD speed in km/h. GPS speed mirrors at the same rate
     * (converted m/s) to provide two confirming sources.
     */
    private fun snapOBD(kmh: Int, ms: Long) = VehicleSnapshot(
        timestamp = Instant.EPOCH.plusMillis(ms),
        speedKmh = kmh,
        gpsSpeedMps = kmh / 3.6f,
    )

    /** Snapshot with GPS speed only (no OBD). */
    private fun snapGPS(mps: Float, ms: Long) = VehicleSnapshot(
        timestamp = Instant.EPOCH.plusMillis(ms),
        speedKmh = null,
        gpsSpeedMps = mps,
    )

    /** Snapshot where OBD and GPS are steady (no change) — produces 0g. */
    private fun snapSteady(kmh: Int, ms: Long) = snapOBD(kmh, ms)

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

    // ── All 3 sources agree ───────────────────────────────────────────────────

    /**
     * All three sources (OBD, GPS, accelerometer) exceed the hard-brake threshold.
     * Cross-validation requires >= 2 sources — should fire HARD_BRAKE.
     *
     * OBD: -1 km/h per 100ms → 0.283g > 0.265g ✓
     * GPS: mirroring OBD at same rate → 0.283g ✓
     * Accel: set to -3.0 m/s² → 0.306g > 0.265g ✓
     */
    @Test
    fun `all 3 sources above threshold fires HARD_BRAKE`() = runTest(dispatcher) {
        val snapFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val accelFlow = MutableStateFlow<Float?>(null)
        val config = DetectionConfig(
            gForceEnabled = true,
            gForceHardBrakeThreshold = 0.265f,
            minEventDurationMs = 400,
            cooldownMs = 60_000,
        )
        val detector = GForceDetector(snapFlow, accelFlow, MutableStateFlow(config))
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // Set accelerometer to braking level
        accelFlow.value = -3.0f

        feedAll(snapFlow, listOf(
            snapOBD(60, 0),
            snapOBD(59, 100),
            snapOBD(58, 200),
            snapOBD(57, 300),
            snapOBD(56, 400),
            snapOBD(55, 500),
        ), this)

        job.cancel()

        assertEquals("Expected 1 HARD_BRAKE event", 1, events.size)
        assertEquals(EventType.HARD_BRAKE, events[0].type)
        assertTrue(events[0].sources.contains(DataSource.OBD))
    }

    // ── 2 of 3 above threshold ────────────────────────────────────────────────

    /**
     * Only OBD and GPS sources available (no accelerometer channel).
     * Both exceed threshold — event should fire.
     */
    @Test
    fun `2 of 3 sources above threshold fires event`() = runTest(dispatcher) {
        val snapFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            gForceEnabled = true,
            gForceHardBrakeThreshold = 0.265f,
            minEventDurationMs = 400,
            cooldownMs = 60_000,
        )
        // No accelerometer channel (accelMps2Flow = null)
        val detector = GForceDetector(snapFlow, accelMps2Flow = null, configFlow = MutableStateFlow(config))
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        feedAll(snapFlow, listOf(
            snapOBD(60, 0),
            snapOBD(59, 100),
            snapOBD(58, 200),
            snapOBD(57, 300),
            snapOBD(56, 400),
            snapOBD(55, 500),
        ), this)

        job.cancel()

        assertEquals("Expected 1 event with 2/3 sources", 1, events.size)
        assertEquals(EventType.HARD_BRAKE, events[0].type)
    }

    // ── Only 1 of 3 above threshold ───────────────────────────────────────────

    /**
     * OBD exceeds threshold but GPS speed is constant (0g) and no accelerometer.
     * Cross-validation requires >= 2 — only 1 source agrees — no event.
     */
    @Test
    fun `only 1 of 3 sources above threshold does not fire`() = runTest(dispatcher) {
        val snapFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            gForceEnabled = true,
            gForceHardBrakeThreshold = 0.265f,
            minEventDurationMs = 400,
        )
        val detector = GForceDetector(snapFlow, accelMps2Flow = null, configFlow = MutableStateFlow(config))
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // OBD: braking. GPS: constant (no change). Only 1 source exceeds.
        feedAll(snapFlow, listOf(
            VehicleSnapshot(timestamp = Instant.EPOCH, speedKmh = 60, gpsSpeedMps = 10f),
            VehicleSnapshot(timestamp = Instant.EPOCH.plusMillis(100), speedKmh = 59, gpsSpeedMps = 10f),
            VehicleSnapshot(timestamp = Instant.EPOCH.plusMillis(200), speedKmh = 58, gpsSpeedMps = 10f),
            VehicleSnapshot(timestamp = Instant.EPOCH.plusMillis(300), speedKmh = 57, gpsSpeedMps = 10f),
            VehicleSnapshot(timestamp = Instant.EPOCH.plusMillis(400), speedKmh = 56, gpsSpeedMps = 10f),
            VehicleSnapshot(timestamp = Instant.EPOCH.plusMillis(500), speedKmh = 55, gpsSpeedMps = 10f),
        ), this)

        job.cancel()

        assertTrue("Only 1 source should not fire", events.isEmpty())
    }

    // ── OBD + GPS agree, accelerometer below ─────────────────────────────────

    /**
     * OBD and GPS both exceed threshold; accelerometer is present but reads near-zero
     * (phone is stationary relative to the car). 2/3 agree — event fires.
     */
    @Test
    fun `OBD and GPS agree with accel below fires event`() = runTest(dispatcher) {
        val snapFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val accelFlow = MutableStateFlow<Float?>(0.1f)  // ~0.01g, well below threshold
        val config = DetectionConfig(
            gForceEnabled = true,
            gForceHardBrakeThreshold = 0.265f,
            minEventDurationMs = 400,
            cooldownMs = 60_000,
        )
        val detector = GForceDetector(snapFlow, accelFlow, MutableStateFlow(config))
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        feedAll(snapFlow, listOf(
            snapOBD(60, 0),
            snapOBD(59, 100),
            snapOBD(58, 200),
            snapOBD(57, 300),
            snapOBD(56, 400),
            snapOBD(55, 500),
        ), this)

        job.cancel()

        assertEquals("OBD+GPS should fire with accel below threshold", 1, events.size)
        assertTrue(events[0].sources.contains(DataSource.OBD))
        assertTrue(events[0].sources.contains(DataSource.GPS))
    }

    // ── Phone drop (accel spike only) ─────────────────────────────────────────

    /**
     * Accelerometer spikes (phone drop) but OBD and GPS show steady speed.
     * Only 1 source (accelerometer) exceeds — cross-validation prevents a false event.
     */
    @Test
    fun `accel spike with steady OBD and GPS does not fire`() = runTest(dispatcher) {
        val snapFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val accelFlow = MutableStateFlow<Float?>(-5.0f)  // ~0.51g, above threshold
        val config = DetectionConfig(
            gForceEnabled = true,
            gForceHardBrakeThreshold = 0.265f,
            minEventDurationMs = 400,
        )
        val detector = GForceDetector(snapFlow, accelFlow, MutableStateFlow(config))
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // Steady speed — no OBD or GPS change
        feedAll(snapFlow, listOf(
            snapSteady(60, 0),
            snapSteady(60, 100),
            snapSteady(60, 200),
            snapSteady(60, 300),
            snapSteady(60, 400),
            snapSteady(60, 500),
        ), this)

        job.cancel()

        assertTrue("Phone drop should not produce an event", events.isEmpty())
    }

    // ── Severe brake ──────────────────────────────────────────────────────────

    /**
     * Two or more sources exceed the severe-brake threshold (0.50g default).
     * The emitted event's [peakG] should be above the severe threshold.
     *
     * OBD: -2 km/h per 100ms → ≈ 0.566g > 0.50g ✓
     * GPS: mirrors OBD → same ✓
     * Accel: null (not provided)
     */
    @Test
    fun `severe brake 2 sources above severe threshold marks peakG above severe`() = runTest(dispatcher) {
        val snapFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(
            gForceEnabled = true,
            gForceHardBrakeThreshold = 0.265f,
            gForceSevereBrakeThreshold = 0.50f,
            minEventDurationMs = 400,
            cooldownMs = 60_000,
        )
        val detector = GForceDetector(snapFlow, accelMps2Flow = null, configFlow = MutableStateFlow(config))
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // -2 km/h per 100ms → 0.566g — above both brake and severe thresholds
        feedAll(snapFlow, listOf(
            snapOBD(60, 0),
            snapOBD(58, 100),
            snapOBD(56, 200),
            snapOBD(54, 300),
            snapOBD(52, 400),
            snapOBD(50, 500),
        ), this)

        job.cancel()

        assertEquals("Expected 1 severe event", 1, events.size)
        val e = events[0]
        assertTrue(
            "peakG should be >= severe threshold (0.50g), was ${e.peakG}",
            e.peakG != null && e.peakG!! >= 0.50f,
        )
    }

    // ── Disabled detector ─────────────────────────────────────────────────────

    @Test
    fun `disabled detector emits no events`() = runTest(dispatcher) {
        val snapFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val config = DetectionConfig(gForceEnabled = false)
        val detector = GForceDetector(snapFlow, configFlow = MutableStateFlow(config))
        val events = mutableListOf<ghart.space.pi_drive.shared.data.model.DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        feedAll(snapFlow, listOf(
            snapOBD(60, 0),
            snapOBD(58, 100),
            snapOBD(56, 200),
        ), this)

        job.cancel()

        assertTrue("Disabled GForce detector should emit no events", events.isEmpty())
    }
}
