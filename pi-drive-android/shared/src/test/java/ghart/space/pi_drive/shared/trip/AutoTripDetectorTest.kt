package ghart.space.pi_drive.shared.trip

import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for [AutoTripDetector].
 *
 * Uses fake DAOs and [MutableStateFlow] inputs; [UnconfinedTestDispatcher] lets StateFlow
 * emissions run eagerly without manual [advanceUntilIdle] calls. [advanceTimeBy] is still
 * needed to trigger the [AUTO_TRIP_END_TIMEOUT_MS] countdown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoTripDetectorTest {

    private val dispatcher = UnconfinedTestDispatcher()

    // ── Fake DAO ──────────────────────────────────────────────────────────────

    /** In-memory [AutoTripDao] that records all inserts and updates. */
    private class FakeAutoTripDao : AutoTripDao {
        val trips = mutableListOf<AutoTripEntity>()
        private var nextId = 1L

        override suspend fun insert(trip: AutoTripEntity): Long {
            val id = nextId++
            trips.add(trip.copy(id = id))
            return id
        }

        override suspend fun update(trip: AutoTripEntity) {
            val index = trips.indexOfFirst { it.id == trip.id }
            if (index >= 0) trips[index] = trip
        }

        override suspend fun getActive(): AutoTripEntity? = trips.find { it.endTime == null }
        override fun getAll(): Flow<List<AutoTripEntity>> = flow { emit(trips.toList()) }
        override suspend fun getByDateRange(from: Instant, to: Instant) = emptyList<AutoTripEntity>()
        override suspend fun delete(trip: AutoTripEntity) { trips.removeIf { it.id == trip.id } }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private fun TestScope.buildDetector(
        snapshots: MutableStateFlow<VehicleSnapshot> = MutableStateFlow(VehicleSnapshot.EMPTY),
        connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected()),
        dao: AutoTripDao = FakeAutoTripDao(),
        clock: Clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC),
        endTimeoutMs: Long = AUTO_TRIP_END_TIMEOUT_MS,
    ) = AutoTripDetector(
        snapshots = snapshots,
        connectionState = connectionState,
        dao = dao,
        scope = backgroundScope,
        clock = clock,
        endTimeoutMs = endTimeoutMs,
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun connected() = ConnectionState.Connected(adapterName = "Demo", protocol = "Sim", pollRateHz = 4f)
    private fun disconnected() = ConnectionState.Disconnected()

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * When [ConnectionState.Connected] is first observed, the detector should create a new
     * active trip row in Room and expose a non-null [AutoTripDetector.currentTrip].
     */
    @Test
    fun `connection established starts a new trip`() = runTest(dispatcher) {
        val connectionFlow = MutableStateFlow<ConnectionState>(disconnected())
        val dao = FakeAutoTripDao()
        val detector = buildDetector(connectionState = connectionFlow, dao = dao)

        connectionFlow.value = connected()

        assertEquals("One trip should be inserted", 1, dao.trips.size)
        assertTrue("Inserted trip should be active (endTime == null)", dao.trips[0].endTime == null)
        assertNotNull("currentTrip should be non-null", detector.currentTrip.value)
        assertEquals("currentTrip.tripId matches DAO id", dao.trips[0].id, detector.currentTrip.value!!.tripId)
    }

    /**
     * When the connection drops and reconnects within the end timeout, the same trip
     * should continue — no new trip is created, no finalization occurs.
     */
    @Test
    fun `reconnect within timeout resumes same trip`() = runTest(dispatcher) {
        val connectionFlow = MutableStateFlow<ConnectionState>(disconnected())
        val dao = FakeAutoTripDao()
        val detector = buildDetector(
            connectionState = connectionFlow,
            dao = dao,
            endTimeoutMs = 30_000L,  // 30s for test speed
        )

        // Start trip
        connectionFlow.value = connected()
        val tripId = detector.currentTrip.value!!.tripId

        // Disconnect for less than the timeout
        connectionFlow.value = disconnected()
        advanceTimeBy(15_000L)  // 15s < 30s timeout

        // Reconnect — should resume, not start a new trip
        connectionFlow.value = connected()

        assertEquals("Still only one trip", 1, dao.trips.size)
        assertEquals("Trip ID unchanged after resume", tripId, detector.currentTrip.value!!.tripId)
    }

    /**
     * A disconnect longer than [endTimeoutMs] should finalize the trip:
     * [AutoTripDetector.currentTrip] becomes null and the DAO row has a non-null [endTime].
     */
    @Test
    fun `disconnect longer than timeout ends trip`() = runTest(dispatcher) {
        val connectionFlow = MutableStateFlow<ConnectionState>(disconnected())
        val dao = FakeAutoTripDao()
        val detector = buildDetector(
            connectionState = connectionFlow,
            dao = dao,
            endTimeoutMs = 30_000L,
        )

        connectionFlow.value = connected()
        val tripId = dao.trips[0].id

        connectionFlow.value = disconnected()
        advanceTimeBy(30_001L) // past the timeout

        assertNull("currentTrip must be null after trip ends", detector.currentTrip.value)
        val finalEntity = dao.trips.find { it.id == tripId }
        assertNotNull("Trip entity must exist", finalEntity)
        assertNotNull("endTime must be set on finalized trip", finalEntity!!.endTime)
    }

    /**
     * After the first trip ends (timeout), a fresh connection should start an entirely
     * new trip with a different ID.
     */
    @Test
    fun `two sessions more than timeout apart create two trips`() = runTest(dispatcher) {
        val connectionFlow = MutableStateFlow<ConnectionState>(disconnected())
        val dao = FakeAutoTripDao()
        val detector = buildDetector(
            connectionState = connectionFlow,
            dao = dao,
            endTimeoutMs = 30_000L,
        )

        // First session
        connectionFlow.value = connected()
        val firstId = detector.currentTrip.value!!.tripId

        connectionFlow.value = disconnected()
        advanceTimeBy(30_001L)  // first trip ends

        // Second session
        connectionFlow.value = connected()
        val secondId = detector.currentTrip.value!!.tripId

        assertEquals("Two distinct trips should exist", 2, dao.trips.size)
        assertTrue("Second trip ID is different from first", firstId != secondId)
        assertNotNull("First trip must be finalized", dao.trips.find { it.id == firstId }?.endTime)
        assertNull("Second trip must still be active", dao.trips.find { it.id == secondId }?.endTime)
    }

    /**
     * When connected and speed samples arrive, distance should accumulate in [currentTrip].
     */
    @Test
    fun `speed samples accumulate distance on active trip`() = runTest(dispatcher) {
        val snapshotsFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val connectionFlow = MutableStateFlow<ConnectionState>(connected())
        val dao = FakeAutoTripDao()
        val detector = buildDetector(
            snapshots = snapshotsFlow,
            connectionState = connectionFlow,
            dao = dao,
        )

        // 97 km/h ≈ 60.3 mph; two samples 1 s apart → ~0.0167 miles
        val t0 = 1_000_000L
        snapshotsFlow.value = VehicleSnapshot(
            speedKmh = 97,
            timestamp = Instant.ofEpochMilli(t0),
        )
        snapshotsFlow.value = VehicleSnapshot(
            speedKmh = 97,
            timestamp = Instant.ofEpochMilli(t0 + 1_000L),
        )

        assertTrue(
            "Distance should be positive after two speed samples",
            detector.currentTrip.value!!.distanceMiles > 0f,
        )
    }
}
