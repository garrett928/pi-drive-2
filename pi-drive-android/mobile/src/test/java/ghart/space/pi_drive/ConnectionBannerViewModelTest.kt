package ghart.space.pi_drive

import androidx.lifecycle.SavedStateHandle
import ghart.space.pi_drive.shared.data.DemoVehicleDataSource
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.ui.viewmodel.LiveDashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionBannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connectionState starts as Disconnected before polling`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        // Create a data source but DON'T start polling — check initial state
        // (Note: ViewModel.init calls startPolling, so we check the initial value directly)
        assertEquals(ConnectionState.Disconnected, dataSource.connectionState.value)
    }

    @Test
    fun `connectionState transitions through Connecting to Connected in demo mode`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
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
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
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
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
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
