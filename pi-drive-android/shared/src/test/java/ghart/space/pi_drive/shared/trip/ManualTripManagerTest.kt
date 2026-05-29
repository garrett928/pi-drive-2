package ghart.space.pi_drive.shared.trip

import ghart.space.pi_drive.shared.data.db.dao.ManualTripDao
import ghart.space.pi_drive.shared.data.db.entity.ManualTripEntity
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.ManualTripState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for [ManualTripManager].
 *
 * Uses a fake [ManualTripDao] (no Room or Robolectric needed) and a [StandardTestDispatcher]
 * to drive coroutines deterministically. The manager is created with [TestScope.backgroundScope]
 * so its infinite coroutines do not trigger [kotlinx.coroutines.test.UncompletedCoroutinesError].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualTripManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    // ── Fake DAO ──────────────────────────────────────────────────────────────

    /** In-memory [ManualTripDao] that records all inserts and updates. */
    private class FakeManualTripDao : ManualTripDao {
        val trips = mutableListOf<ManualTripEntity>()
        private var nextId = 1L

        override suspend fun insert(trip: ManualTripEntity): Long {
            val id = nextId++
            trips.add(trip.copy(id = id))
            return id
        }

        override suspend fun update(trip: ManualTripEntity) {
            val index = trips.indexOfFirst { it.id == trip.id }
            if (index >= 0) trips[index] = trip
        }

        override suspend fun getActive(): ManualTripEntity? = trips.find { it.isActive }

        override fun getAll(): Flow<List<ManualTripEntity>> = flow { emit(trips.toList()) }

        /** Seeds an entity with a specific ID for restore tests — does not go through insert(). */
        fun seed(entity: ManualTripEntity) {
            trips.add(entity)
            if (entity.id >= nextId) nextId = entity.id + 1
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private fun makeSnapshot(speedKmh: Int, timestampMs: Long) = VehicleSnapshot(
        speedKmh = speedKmh,
        timestamp = Instant.ofEpochMilli(timestampMs),
    )

    /** Builds a [ManualTripManager] backed by [TestScope.backgroundScope]. */
    private fun TestScope.buildManager(
        snapshots: MutableStateFlow<VehicleSnapshot> = MutableStateFlow(VehicleSnapshot.EMPTY),
        connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(
            ConnectionState.Connected(adapterName = "Demo", protocol = "Sim", pollRateHz = 4f)
        ),
        dao: ManualTripDao = FakeManualTripDao(),
        clock: Clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC),
    ) = ManualTripManager(
        snapshots = snapshots,
        connectionState = connectionState,
        dao = dao,
        scope = backgroundScope,
        clock = clock,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * After [ManualTripManager.reset], feeding speed samples increases [ManualTripState.distanceMiles].
     */
    @Test
    fun `start trip then feed speed samples accumulates distance`() = runTest(dispatcher) {
        val snapshotsFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val manager = buildManager(snapshots = snapshotsFlow)
        advanceUntilIdle() // let init coroutine run

        manager.reset()
        advanceUntilIdle()

        // 97 km/h ≈ 60.3 mph; two samples 1 s apart → ~0.0167 miles
        val t0 = 1_000_000L
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0)
        advanceUntilIdle()
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 1_000L)
        advanceUntilIdle()

        assertTrue(
            "Distance should be positive after speed samples",
            manager.state.value.distanceMiles > 0f,
        )
    }

    /**
     * When the connection goes to [ConnectionState.Disconnected], distance stops accumulating
     * even when speed samples continue to arrive.
     */
    @Test
    fun `pause on disconnect stops distance accumulation`() = runTest(dispatcher) {
        val snapshotsFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val connectionFlow = MutableStateFlow<ConnectionState>(
            ConnectionState.Connected("Demo", "Sim", 4f)
        )
        val manager = buildManager(snapshots = snapshotsFlow, connectionState = connectionFlow)
        advanceUntilIdle()

        manager.reset()
        advanceUntilIdle()

        val t0 = 1_000_000L
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0)
        advanceUntilIdle()
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 1_000L)
        advanceUntilIdle()

        val distBefore = manager.state.value.distanceMiles
        assertTrue("Should have accumulated distance before pause", distBefore > 0f)

        // Disconnect
        connectionFlow.value = ConnectionState.Disconnected()
        advanceUntilIdle()

        // More snapshots arrive — should NOT accumulate
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 2_000L)
        advanceUntilIdle()
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 3_000L)
        advanceUntilIdle()

        assertEquals(
            "Distance must not change while paused",
            distBefore,
            manager.state.value.distanceMiles,
            0.001f,
        )
    }

    /**
     * After a pause/disconnect, [ConnectionState.Connected] resumes accumulation.
     * The gap while paused is excluded from distance and duration.
     */
    @Test
    fun `resume after disconnect accumulates new distance`() = runTest(dispatcher) {
        val snapshotsFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val connectionFlow = MutableStateFlow<ConnectionState>(
            ConnectionState.Connected("Demo", "Sim", 4f)
        )
        val manager = buildManager(snapshots = snapshotsFlow, connectionState = connectionFlow)
        advanceUntilIdle()

        manager.reset()
        advanceUntilIdle()

        val t0 = 1_000_000L
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0)
        advanceUntilIdle()
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 1_000L)
        advanceUntilIdle()

        val distBeforePause = manager.state.value.distanceMiles

        // Pause
        connectionFlow.value = ConnectionState.Disconnected()
        advanceUntilIdle()
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 100_000L)
        advanceUntilIdle()

        // Resume
        connectionFlow.value = ConnectionState.Connected("Demo", "Sim", 4f)
        advanceUntilIdle()

        // First sample after resume establishes time origin (no Δt); second adds distance.
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 200_000L)
        advanceUntilIdle()
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 201_000L)
        advanceUntilIdle()

        assertTrue(
            "Distance must increase after resume",
            manager.state.value.distanceMiles > distBeforePause,
        )
    }

    /**
     * [ManualTripManager.reset] zeroes [ManualTripState.distanceMiles] and sets a new [ManualTripState.startDate].
     */
    @Test
    fun `reset zeroes distance and updates start date`() = runTest(dispatcher) {
        val snapshotsFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val manager = buildManager(snapshots = snapshotsFlow)
        advanceUntilIdle()

        // First reset to create a trip
        manager.reset()
        advanceUntilIdle()

        // Accumulate distance
        val t0 = 1_000_000L
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0)
        advanceUntilIdle()
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 1_000L)
        advanceUntilIdle()
        assertTrue("Should have positive distance before second reset", manager.state.value.distanceMiles > 0f)

        // Second reset
        manager.reset()
        advanceUntilIdle()

        assertEquals(0f, manager.state.value.distanceMiles, 0.001f)
        assertNotNull("startDate must be set after reset", manager.state.value.startDate)
    }

    /**
     * When the manager starts with an active trip in Room, it restores the persisted
     * [distanceMiles] and [durationMs] into the initial [ManualTripState].
     */
    @Test
    fun `restore from dao reads existing active trip values`() = runTest(dispatcher) {
        val dao = FakeManualTripDao()
        dao.seed(
            ManualTripEntity(
                id = 1L,
                startTime = Instant.EPOCH,
                distanceMi = 12.5f,
                durationMs = 720_000L,
                avgSpeedMph = 62f,
                maxSpeedMph = 80f,
                avgMpg = 32f,
                isActive = true,
            )
        )

        val manager = buildManager(dao = dao)
        advanceUntilIdle() // let init coroutine restore from DB

        val state: ManualTripState = manager.state.value
        assertTrue("Trip should be active after restore", state.isActive)
        assertEquals("Restored distance must match DB", 12.5f, state.distanceMiles, 0.01f)
        assertEquals("Restored duration must match DB", 720_000L, state.durationMs)
    }

    /**
     * State is persisted to Room after the [SAVE_INTERVAL_MS] delay elapses.
     */
    @Test
    fun `periodic save writes current state to dao`() = runTest(dispatcher) {
        val snapshotsFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val dao = FakeManualTripDao()
        val manager = buildManager(snapshots = snapshotsFlow, dao = dao)
        advanceUntilIdle()

        manager.reset()
        advanceUntilIdle()

        val t0 = 1_000_000L
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0)
        advanceUntilIdle()
        snapshotsFlow.value = makeSnapshot(speedKmh = 97, timestampMs = t0 + 1_000L)
        advanceUntilIdle()

        val distInMemory = manager.state.value.distanceMiles
        assertTrue("In-memory distance should be positive", distInMemory > 0f)

        // Advance past the 10 s save interval
        advanceTimeBy(10_001L)
        advanceUntilIdle()

        val saved = dao.trips.find { it.isActive }
        assertNotNull("Active trip must exist in DAO", saved)
        assertEquals("Saved distance must match in-memory", distInMemory, saved!!.distanceMi, 0.001f)
    }
}
