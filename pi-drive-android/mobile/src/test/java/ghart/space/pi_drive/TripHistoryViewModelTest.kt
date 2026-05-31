package ghart.space.pi_drive

import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.db.entity.SyncStatus
import ghart.space.pi_drive.shared.data.model.DataSource
import ghart.space.pi_drive.shared.data.model.DetectionStrategy
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.ui.viewmodel.TripHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// ── Fakes ─────────────────────────────────────────────────────────────────────

private class FakeAutoTripDao(initialTrips: List<AutoTripEntity> = emptyList()) : AutoTripDao {
    val tripsFlow = MutableStateFlow(initialTrips)

    override suspend fun insert(trip: AutoTripEntity): Long = 0L
    override suspend fun update(trip: AutoTripEntity) {}
    override suspend fun getActive(): AutoTripEntity? = null
    override fun getAll(): Flow<List<AutoTripEntity>> = tripsFlow
    override suspend fun getByDateRange(from: Instant, to: Instant): List<AutoTripEntity> =
        tripsFlow.value.filter { it.startTime >= from && it.startTime <= to }
    override suspend fun delete(trip: AutoTripEntity) {}
    override suspend fun deleteOlderThan(before: Instant) {}
}

private class FakeDrivingEventDao : DrivingEventDao {
    var hardBrakeCount = 0

    override suspend fun insert(event: DrivingEventEntity): Long = 0L
    override suspend fun getByTimeRange(from: Instant, to: Instant): List<DrivingEventEntity> = emptyList()
    override suspend fun getByTripId(tripId: Long): List<DrivingEventEntity> = emptyList()
    override suspend fun countByTypeAndTimeRange(type: EventType, from: Instant, to: Instant): Int =
        if (type == EventType.HARD_BRAKE) hardBrakeCount else 0
}

// ── Tests ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class TripHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    /**
     * Creates an [AutoTripEntity] whose start time is on a specific [LocalDate].
     *
     * Using explicit dates avoids the time-of-day sensitivity that fractional-day offsets
     * produce when tests run early in the morning (e.g. 1.5 days ago might cross a date
     * boundary differently depending on when the test runs).
     *
     * @param daysAgo  Number of whole calendar days before today; 0 = today, 1 = yesterday.
     * @param hourOfDay  Hour within the chosen calendar day (default noon).
     */
    private fun trip(
        id: Long,
        daysAgo: Int,
        hourOfDay: Int = 12,
        distanceMi: Float = 10f,
        durationMs: Long = 30 * 60_000L,
        avgMpg: Float? = null,
        eventCount: Int = 0,
    ): AutoTripEntity {
        val date = LocalDate.now().minusDays(daysAgo.toLong())
        val startTime = date.atTime(LocalTime.of(hourOfDay, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
        return AutoTripEntity(
            id = id,
            startTime = startTime,
            endTime = startTime.plusMillis(durationMs),
            distanceMi = distanceMi,
            durationMs = durationMs,
            avgSpeedMph = 20f,
            maxSpeedMph = 50f,
            avgMpg = avgMpg,
            eventCount = eventCount,
            syncStatus = SyncStatus.SYNCED,
        )
    }

    /** Overload that accepts a fractional daysAgo for the weekly-filter tests that only care about the 7-day cutoff. */
    private fun tripDaysAgo(
        id: Long,
        daysAgo: Double,
        distanceMi: Float = 10f,
        durationMs: Long = 30 * 60_000L,
        avgMpg: Float? = null,
    ): AutoTripEntity {
        val startTime = Instant.now().minusMillis((daysAgo * 24 * 60 * 60 * 1000).toLong())
        return AutoTripEntity(
            id = id,
            startTime = startTime,
            endTime = startTime.plusMillis(durationMs),
            distanceMi = distanceMi,
            durationMs = durationMs,
            avgSpeedMph = 20f,
            maxSpeedMph = 50f,
            avgMpg = avgMpg,
            eventCount = 0,
            syncStatus = SyncStatus.SYNCED,
        )
    }

    @Test
    fun `3 trips across 2 days group into 2 day buckets`() = runTest {
        // Use Int daysAgo + explicit hour to guarantee calendar-date placement
        // regardless of what time of day the test runs.
        val todayTrip      = trip(id = 1, daysAgo = 0, hourOfDay = 10)
        val yesterdayTrip1 = trip(id = 2, daysAgo = 1, hourOfDay = 9)
        val yesterdayTrip2 = trip(id = 3, daysAgo = 1, hourOfDay = 14)

        val dao = FakeAutoTripDao(listOf(todayTrip, yesterdayTrip1, yesterdayTrip2))
        val vm  = TripHistoryViewModel(dao, FakeDrivingEventDao())

        advanceUntilIdle()
        val state = vm.uiState.value

        assertEquals(2, state.groups.size)
        assertEquals("Today", state.groups[0].dayLabel)
        assertEquals(1, state.groups[0].trips.size)
        assertEquals("Yesterday", state.groups[1].dayLabel)
        assertEquals(2, state.groups[1].trips.size)
    }

    @Test
    fun `weekly summary sums distance and duration correctly`() = runTest {
        val t1 = tripDaysAgo(id = 1, daysAgo = 0.5, distanceMi = 12.4f, durationMs = 24 * 60_000L)
        val t2 = tripDaysAgo(id = 2, daysAgo = 2.0, distanceMi = 18.6f, durationMs = 52 * 60_000L)

        val dao = FakeAutoTripDao(listOf(t1, t2))
        val vm  = TripHistoryViewModel(dao, FakeDrivingEventDao())

        advanceUntilIdle()
        val summary = vm.uiState.value.weeklySummary

        assertNotNull(summary)
        assertEquals(2, summary!!.tripCount)
        assertEquals(12.4f + 18.6f, summary.totalDistanceMi, 0.01f)
        assertEquals((24 + 52) * 60_000L, summary.totalDurationMs)
    }

    @Test
    fun `weekly summary includes hard brake count from event dao`() = runTest {
        val eventDao = FakeDrivingEventDao().also { it.hardBrakeCount = 4 }
        val vm = TripHistoryViewModel(
            FakeAutoTripDao(listOf(tripDaysAgo(id = 1, daysAgo = 0.5))),
            eventDao,
        )

        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.weeklySummary?.hardBrakeCount)
    }

    @Test
    fun `weekly summary computes avg mpg over trips that have fuel data`() = runTest {
        val t1 = tripDaysAgo(id = 1, daysAgo = 0.5, avgMpg = 30f)
        val t2 = tripDaysAgo(id = 2, daysAgo = 1.0, avgMpg = 20f)
        val t3 = tripDaysAgo(id = 3, daysAgo = 2.0, avgMpg = null) // no fuel data

        val vm = TripHistoryViewModel(FakeAutoTripDao(listOf(t1, t2, t3)), FakeDrivingEventDao())
        advanceUntilIdle()

        assertEquals(25f, vm.uiState.value.weeklySummary?.avgMpg ?: 0f, 0.1f)
    }

    @Test
    fun `trip older than 7 days is excluded from weekly summary`() = runTest {
        val recentTrip = tripDaysAgo(id = 1, daysAgo = 3.0)
        val oldTrip    = tripDaysAgo(id = 2, daysAgo = 8.0)

        val vm = TripHistoryViewModel(FakeAutoTripDao(listOf(recentTrip, oldTrip)), FakeDrivingEventDao())
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.weeklySummary?.tripCount)
    }

    @Test
    fun `empty trip list produces null summary and empty groups`() = runTest {
        val vm = TripHistoryViewModel(FakeAutoTripDao(emptyList()), FakeDrivingEventDao())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(null, state.weeklySummary)
        assertEquals(0, state.groups.size)
    }

    @Test
    fun `ui state updates when trip flow emits new list`() = runTest {
        val dao = FakeAutoTripDao(emptyList())
        val vm  = TripHistoryViewModel(dao, FakeDrivingEventDao())

        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.groups.size)

        // Simulate a trip being added (e.g. auto-detect starts a new trip)
        dao.tripsFlow.value = listOf(trip(id = 1, daysAgo = 0))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.groups.size)
    }
}
