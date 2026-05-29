package ghart.space.pi_drive

import androidx.lifecycle.SavedStateHandle
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.data.DemoVehicleDataSource
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.dao.ManualTripDao
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.db.entity.ManualTripEntity
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.detection.AlertManager
import ghart.space.pi_drive.shared.obd.ConnectionManager
import ghart.space.pi_drive.shared.obd.MockTransport
import ghart.space.pi_drive.shared.trip.ManualTripManager
import ghart.space.pi_drive.ui.viewmodel.LiveDashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** Builds a no-op [ManualTripManager] suitable for ViewModel tests that don't test trip behavior. */
private fun noOpManualTripManager(scope: CoroutineScope): ManualTripManager {
    val fakeDao = object : ManualTripDao {
        override suspend fun insert(trip: ManualTripEntity) = 0L
        override suspend fun update(trip: ManualTripEntity) {}
        override suspend fun getActive(): ManualTripEntity? = null
        override fun getAll(): Flow<List<ManualTripEntity>> = emptyFlow()
    }
    return ManualTripManager(
        snapshots = MutableStateFlow(VehicleSnapshot.EMPTY),
        connectionState = MutableStateFlow(ConnectionState.Disconnected()),
        dao = fakeDao,
        scope = scope,
    )
}

/** Builds a no-op [AlertManager] suitable for ViewModel tests that don't test alert behavior. */
private fun noOpAlertManager(scope: CoroutineScope): AlertManager {
    val fakeDao = object : DrivingEventDao {
        override suspend fun insert(event: DrivingEventEntity) = 0L
        override suspend fun getByTimeRange(from: Instant, to: Instant) = emptyList<DrivingEventEntity>()
        override suspend fun getByTripId(tripId: Long) = emptyList<DrivingEventEntity>()
        override suspend fun countByTypeAndTimeRange(type: EventType, from: Instant, to: Instant) = 0
    }
    return AlertManager(
        drivingEvents = kotlinx.coroutines.flow.emptyFlow(),
        healthAlerts = kotlinx.coroutines.flow.emptyFlow(),
        eventDao = fakeDao,
        scope = scope,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveDashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppConfig.isDemoMode = true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppConfig.isDemoMode = false
    }

    // ── Metadata tests (no coroutine needed) ──────────────────────────────────

    @Test
    fun `featuredMetricId defaults to SPEED`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )
        assertEquals(MetricId.SPEED, viewModel.featuredMetricId)
    }

    @Test
    fun `featuredUnit and featuredLabel match SPEED defaults`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )
        assertEquals("mph", viewModel.featuredUnit)
        assertEquals("SPEED", viewModel.featuredLabel)
    }

    @Test
    fun `savedStateHandle overrides default metric`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        val handle = SavedStateHandle(mapOf("featured_metric" to "RPM"))
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            savedStateHandle = handle,
        )
        assertEquals(MetricId.RPM, viewModel.featuredMetricId)
        assertEquals("rpm", viewModel.featuredUnit)
        assertEquals("RPM", viewModel.featuredLabel)
    }

    @Test
    fun `invalid savedStateHandle metric falls back to SPEED`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        val handle = SavedStateHandle(mapOf("featured_metric" to "NOT_A_METRIC"))
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            savedStateHandle = handle,
        )
        assertEquals(MetricId.SPEED, viewModel.featuredMetricId)
    }

    // ── Live data tests ───────────────────────────────────────────────────────

    @Test
    fun `featuredValue emits non-dash speed values after CRUISE starts`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )

        val values = mutableListOf<String>()
        val collectJob = launch { viewModel.featuredValue.take(6).toList(values) }
        advanceTimeBy(2_000)
        collectJob.join()

        assertTrue("Expected non-dash speed values, got: $values", values.any { it != "—" })
    }

    @Test
    fun `featuredValue changes over time in CRUISE scenario`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )

        val values = mutableListOf<String>()
        val collectJob = launch { viewModel.featuredValue.take(8).toList(values) }
        advanceTimeBy(3_000)
        collectJob.join()

        val nonDash = values.filter { it != "—" }
        assertTrue("Expected at least 2 distinct speed values, got: $nonDash", nonDash.toSet().size >= 2)
    }

    @Test
    fun `sparklineData accumulates values from snapshot emissions`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )

        val sparklineLists = mutableListOf<List<Float>>()
        val collectJob = launch { viewModel.sparklineData.take(10).toList(sparklineLists) }
        advanceTimeBy(3_000)
        collectJob.join()

        val lastList = sparklineLists.lastOrNull() ?: emptyList()
        assertTrue("Sparkline should have accumulated data, got ${lastList.size}", lastList.isNotEmpty())
    }

    @Test
    fun `isLive becomes true after CRUISE polling starts`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )

        // Subscribe first — SharingStarted.WhileSubscribed won't update without a subscriber
        val liveValues = mutableListOf<Boolean>()
        val collectJob = launch { viewModel.isLive.take(2).toList(liveValues) }

        // Advance past the 500ms simulated handshake delay
        advanceTimeBy(1_500)
        collectJob.join()

        // Should go false (Disconnected) → true (Connected)
        assertTrue("isLive should become true after demo connects, got: $liveValues", liveValues.contains(true))
    }
}
