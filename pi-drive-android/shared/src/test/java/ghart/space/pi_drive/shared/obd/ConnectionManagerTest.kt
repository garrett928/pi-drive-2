package ghart.space.pi_drive.shared.obd

import ghart.space.pi_drive.shared.data.model.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    /**
     * Creates a [ConnectionManager] backed by [MockTransport].
     *
     * Uses [TestScope.backgroundScope] so that the long-lived [ConnectionManager.monitorJob]
     * (which collects an infinite isConnected flow) is silently cancelled when [runTest] ends,
     * avoiding [kotlinx.coroutines.test.UncompletedCoroutinesError].
     *
     * Injects [TestScope.currentTime] as the clock so virtual-time advancement controls the
     * 5-minute retry window without depending on wall-clock time.
     */
    private fun managerWithMock(scope: TestScope): Pair<ConnectionManager, () -> MockTransport> {
        var lastTransport: MockTransport? = null
        val manager = ConnectionManager(
            scope = scope.backgroundScope,
            transportFactory = { MockTransport().also { lastTransport = it } },
            clock = { scope.currentTime },
        )
        return manager to { lastTransport!! }
    }

    @Test
    fun `initial state is Disconnected`() = runTest(testDispatcher) {
        val (manager, _) = managerWithMock(this)
        assertTrue(manager.connectionState.value is ConnectionState.Disconnected)
        assertFalse((manager.connectionState.value as ConnectionState.Disconnected).canRetry)
    }

    @Test
    fun `connect transitions to Connected via Connecting`() = runTest(testDispatcher) {
        val (manager, _) = managerWithMock(this)

        launch { manager.connect("TEST_DEVICE") }
        advanceUntilIdle()

        assertTrue(
            "Expected Connected, got ${manager.connectionState.value}",
            manager.connectionState.value is ConnectionState.Connected,
        )
    }

    @Test
    fun `onAdapterDisconnected starts reconnect loop with canRetry true`() = runTest(testDispatcher) {
        val (manager, _) = managerWithMock(this)

        // First connect succeeds
        launch { manager.connect("TEST_DEVICE") }
        advanceUntilIdle()
        assertTrue(manager.connectionState.value is ConnectionState.Connected)

        // Simulate unexpected adapter drop
        manager.onAdapterDisconnected()
        // Advance just enough to start the loop coroutine and emit the first Disconnected
        // state, but not enough to fire the 1-second countdown delay.
        advanceTimeBy(100)

        val state = manager.connectionState.value
        assertTrue("Expected Disconnected, got $state", state is ConnectionState.Disconnected)
        assertTrue("Expected canRetry=true", (state as ConnectionState.Disconnected).canRetry)
    }

    @Test
    fun `after 5 minutes of failed reconnects canRetry becomes false`() = runTest(testDispatcher) {
        // Transport that always fails after the first successful connect
        var attemptCount = 0
        val failingManager = ConnectionManager(
            scope = backgroundScope,
            transportFactory = {
                attemptCount++
                if (attemptCount == 1) MockTransport()
                else object : OBDTransport {
                    override val isConnected = MockTransport().isConnected
                    override suspend fun connect() = throw IOException("simulated failure")
                    override suspend fun send(command: String) = ""
                    override suspend fun disconnect() {}
                }
            },
            clock = { currentTime },
        )

        // First connect succeeds
        launch { failingManager.connect("TEST_DEVICE") }
        advanceUntilIdle()
        assertTrue(failingManager.connectionState.value is ConnectionState.Connected)

        // Drop the connection — reconnect loop starts using virtual clock
        failingManager.onAdapterDisconnected()
        advanceTimeBy(100)
        assertTrue((failingManager.connectionState.value as ConnectionState.Disconnected).canRetry)

        // Advance past the 5-minute retry window (30 retries × 10s + buffer)
        advanceTimeBy(ConnectionManager.MAX_RETRY_DURATION_MS + ConnectionManager.RETRY_INTERVAL_MS)
        advanceUntilIdle()

        val state = failingManager.connectionState.value
        assertTrue("Expected Disconnected, got $state", state is ConnectionState.Disconnected)
        assertFalse(
            "Expected canRetry=false after timeout",
            (state as ConnectionState.Disconnected).canRetry,
        )
    }

    @Test
    fun `reconnect succeeds after disconnect`() = runTest(testDispatcher) {
        val (manager, _) = managerWithMock(this)

        // Initial connect
        launch { manager.connect("TEST_DEVICE") }
        advanceUntilIdle()
        assertTrue(manager.connectionState.value is ConnectionState.Connected)

        // Drop connection
        manager.onAdapterDisconnected()
        advanceTimeBy(100)
        assertTrue((manager.connectionState.value as ConnectionState.Disconnected).canRetry)

        // Let the first reconnect attempt fire (10s countdown + small buffer)
        advanceTimeBy(ConnectionManager.RETRY_INTERVAL_MS + 1_000)
        advanceUntilIdle()

        assertTrue(
            "Expected Connected after reconnect, got ${manager.connectionState.value}",
            manager.connectionState.value is ConnectionState.Connected,
        )
    }

    @Test
    fun `disconnect cancels reconnect loop and resets state`() = runTest(testDispatcher) {
        val (manager, _) = managerWithMock(this)

        launch { manager.connect("TEST_DEVICE") }
        advanceUntilIdle()
        assertTrue(manager.connectionState.value is ConnectionState.Connected)

        manager.onAdapterDisconnected()
        advanceTimeBy(100)
        assertTrue((manager.connectionState.value as ConnectionState.Disconnected).canRetry)

        // Clean disconnect cancels the loop
        manager.disconnect()
        advanceUntilIdle()

        val state = manager.connectionState.value
        assertTrue(state is ConnectionState.Disconnected)
        assertFalse((state as ConnectionState.Disconnected).canRetry)
    }
}
