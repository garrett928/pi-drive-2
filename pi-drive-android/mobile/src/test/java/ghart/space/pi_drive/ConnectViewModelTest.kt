package ghart.space.pi_drive

import ghart.space.pi_drive.shared.obd.MockTransport
import ghart.space.pi_drive.ui.viewmodel.ConnectCoordinator
import ghart.space.pi_drive.ui.viewmodel.InitStepStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {

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
    fun `initial steps are all PENDING`() {
        val coordinator = ConnectCoordinator()
        val steps = coordinator.initSteps.value
        assertEquals(6, steps.size)
        assertTrue("All steps should start PENDING",
            steps.all { it.status == InitStepStatus.PENDING })
    }

    @Test
    fun `connect with mock transport completes all steps as SUCCESS`() = runTest {
        val coordinator = ConnectCoordinator()

        launch { coordinator.connect(MockTransport()) }
        advanceUntilIdle()

        val steps = coordinator.initSteps.value
        assertEquals(6, steps.size)
        val failedSteps = steps.filter { it.status != InitStepStatus.SUCCESS }
        assertTrue(
            "All steps should be SUCCESS, failed: $failedSteps",
            failedSteps.isEmpty(),
        )
    }

    @Test
    fun `connect with mock transport produces non-empty supported PIDs`() = runTest {
        val coordinator = ConnectCoordinator()

        launch { coordinator.connect(MockTransport()) }
        advanceUntilIdle()

        val result = coordinator.initResult.value
        assertNotNull("initResult should be set after connect", result)
        assertTrue(
            "Supported PIDs should not be empty",
            result!!.supportedPids.isNotEmpty(),
        )
    }

    @Test
    fun `steps transition away from PENDING during connect`() = runTest {
        val coordinator = ConnectCoordinator()
        assertTrue("Pre-check: all PENDING before connect",
            coordinator.initSteps.value.all { it.status == InitStepStatus.PENDING })

        launch { coordinator.connect(MockTransport()) }
        advanceUntilIdle()

        // After a successful connect, no step should remain PENDING
        val pending = coordinator.initSteps.value.filter { it.status == InitStepStatus.PENDING }
        assertTrue("No steps should remain PENDING after connect: $pending", pending.isEmpty())
    }

    @Test
    fun `reset clears steps back to PENDING`() = runTest {
        val coordinator = ConnectCoordinator()

        launch { coordinator.connect(MockTransport()) }
        advanceUntilIdle()

        // All done at this point
        assertTrue(coordinator.initSteps.value.all { it.status == InitStepStatus.SUCCESS })

        coordinator.reset()

        assertTrue(
            "After reset, all steps should be PENDING",
            coordinator.initSteps.value.all { it.status == InitStepStatus.PENDING },
        )
    }
}
