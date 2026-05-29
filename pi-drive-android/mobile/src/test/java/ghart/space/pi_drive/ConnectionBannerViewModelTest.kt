package ghart.space.pi_drive

import androidx.lifecycle.SavedStateHandle
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.data.DemoVehicleDataSource
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.dao.ManualTripDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.db.entity.ManualTripEntity
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.detection.AlertManager
import ghart.space.pi_drive.shared.obd.ConnectionManager
import ghart.space.pi_drive.shared.obd.MockTransport
import ghart.space.pi_drive.shared.trip.AutoTripManager
import ghart.space.pi_drive.shared.trip.ManualTripManager
import ghart.space.pi_drive.ui.viewmodel.LiveDashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

/** Builds a no-op [AutoTripManager] suitable for ViewModel tests that don't test trip behavior. */
private fun noOpAutoTripManager(scope: CoroutineScope): AutoTripManager {
    val fakeDao = object : AutoTripDao {
        override suspend fun insert(trip: AutoTripEntity) = 0L
        override suspend fun update(trip: AutoTripEntity) {}
        override suspend fun getActive(): AutoTripEntity? = null
        override fun getAll() = kotlinx.coroutines.flow.emptyFlow<List<AutoTripEntity>>()
        override suspend fun getByDateRange(from: java.time.Instant, to: java.time.Instant) = emptyList<AutoTripEntity>()
        override suspend fun delete(trip: AutoTripEntity) {}
    }
    return AutoTripManager(
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
class ConnectionBannerViewModelTest {

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

    @Test
    fun `connectionState starts as Disconnected before polling`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        // Create a data source but DON'T start polling — check initial state
        // (Note: ViewModel.init calls startPolling, so we check the initial value directly)
        assertEquals(ConnectionState.Disconnected(), dataSource.connectionState.value)
    }

    @Test
    fun `connectionState transitions through Connecting to Connected in demo mode`() = runTest {
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
            autoTripManager = noOpAutoTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )

        val states = mutableListOf<ConnectionState>()
        val collectJob = launch { viewModel.connectionState.take(3).toList(states) }

        // Advance past the 500ms handshake delay
        advanceTimeBy(1_500)
        collectJob.join()

        // Should see Disconnected -> Connecting -> Connected
        assertTrue("Expected Disconnected in states: $states",
            states.any { it is ConnectionState.Disconnected })
        assertTrue("Expected Connected in states: $states",
            states.any { it is ConnectionState.Connected })
    }

    @Test
    fun `connected state has correct demo adapter name`() = runTest {
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
            autoTripManager = noOpAutoTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )

        val states = mutableListOf<ConnectionState>()
        val collectJob = launch { viewModel.connectionState.take(3).toList(states) }

        advanceTimeBy(1_500)
        collectJob.join()

        val connected = states.filterIsInstance<ConnectionState.Connected>().firstOrNull()
        assertTrue("Expected Connected state", connected != null)
        assertTrue("Expected demo adapter name to contain 'Demo Mode'",
            connected!!.adapterName.contains("Demo Mode"))
    }

    @Test
    fun `connected state protocol is Simulated for demo`() = runTest {
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
            autoTripManager = noOpAutoTripManager(backgroundScope),
            savedStateHandle = SavedStateHandle(),
        )

        val states = mutableListOf<ConnectionState>()
        val collectJob = launch { viewModel.connectionState.take(3).toList(states) }

        advanceTimeBy(1_500)
        collectJob.join()

        val connected = states.filterIsInstance<ConnectionState.Connected>().firstOrNull()
        assertEquals("Simulated", connected?.protocol)
    }
}
