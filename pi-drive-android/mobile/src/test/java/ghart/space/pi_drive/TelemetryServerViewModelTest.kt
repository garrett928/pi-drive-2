package ghart.space.pi_drive

import ghart.space.pi_drive.shared.telemetry.TelemetryConfig
import ghart.space.pi_drive.shared.telemetry.TelemetryConfigRepository
import ghart.space.pi_drive.shared.telemetry.TelemetryUploader
import ghart.space.pi_drive.shared.telemetry.VinSource
import ghart.space.pi_drive.ui.viewmodel.HealthState
import ghart.space.pi_drive.ui.viewmodel.TelemetryServerViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [TelemetryServerViewModel].
 *
 * Uses [mockk] for [TelemetryConfigRepository] (which requires Android [Context]) and
 * [TelemetryUploader] (which makes network calls). The secondary [TelemetryServerViewModel]
 * constructor injects a custom [uploaderFactory] so tests control what the uploader returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryServerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockRepo = mockk<TelemetryConfigRepository>()
    private val mockUploader = mockk<TelemetryUploader>()

    private val baseConfig = TelemetryConfig(deviceId = "test-device-001")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepo.load() } returns baseConfig
        every { mockRepo.save(any()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(config: TelemetryConfig = baseConfig): TelemetryServerViewModel {
        every { mockRepo.load() } returns config
        return TelemetryServerViewModel(mockRepo) { mockUploader }
    }

    // ── VIN state ─────────────────────────────────────────────────────────────

    @Test fun `blank VIN on load - vinState isBlank is true`() = runTest {
        val vm = makeViewModel()
        assertTrue("Expected vinState.isBlank to be true for empty VIN", vm.vinState.value.isBlank)
    }

    @Test fun `non-blank VIN on load - vinState isBlank is false`() = runTest {
        val vm = makeViewModel(baseConfig.copy(vin = "1HGCM82633A123456", vinSource = VinSource.MANUAL))
        assertTrue("Expected vinState.isBlank to be false", !vm.vinState.value.isBlank)
    }

    @Test fun `saveVin stores VIN with MANUAL source`() = runTest {
        val vm = makeViewModel()
        vm.saveVin("1HGCM82633A123456")
        advanceUntilIdle()

        // config is the synchronous StateFlow; vinState propagates via coroutine
        assertEquals("1HGCM82633A123456", vm.config.value.vin)
        assertEquals(VinSource.MANUAL, vm.config.value.vinSource)
        assertEquals(VinSource.MANUAL, vm.vinState.value.source)
        verify { mockRepo.save(match { it.vin == "1HGCM82633A123456" && it.vinSource == VinSource.MANUAL }) }
    }

    @Test fun `saveVin trims whitespace before saving`() = runTest {
        val vm = makeViewModel()
        vm.saveVin("  1HGCM82633A123456  ")
        advanceUntilIdle()

        assertEquals("1HGCM82633A123456", vm.config.value.vin)
    }

    @Test fun `retriggerVinDetection when disconnected is a no-op - no uploader call`() = runTest {
        val vm = makeViewModel()
        vm.retriggerVinDetection()
        advanceUntilIdle()

        // No upload-related methods should be called
        verify(exactly = 0) { mockRepo.save(any()) }
    }

    // ── URL save / validation ─────────────────────────────────────────────────

    @Test fun `save HTTPS URL - read back - matches`() = runTest {
        val vm = makeViewModel()
        vm.saveConfig(baseConfig.copy(serverUrl = "https://telemetry.example.com"))

        assertEquals("https://telemetry.example.com", vm.config.value.serverUrl)
        assertNull("urlError should be null for valid HTTPS URL", vm.urlError.value)
    }

    @Test fun `invalid HTTP URL produces urlError and does not save`() = runTest {
        val vm = makeViewModel()
        vm.saveConfig(baseConfig.copy(serverUrl = "http://telemetry.example.com"))

        assertNotNull("urlError should be set for HTTP URL", vm.urlError.value)
        // Config should not have been updated to the invalid URL
        assertEquals("", vm.config.value.serverUrl)
        verify(exactly = 0) { mockRepo.save(any()) }
    }

    @Test fun `blank URL is allowed - no urlError`() = runTest {
        val vm = makeViewModel(baseConfig.copy(serverUrl = "https://telemetry.example.com"))
        vm.saveConfig(baseConfig.copy(serverUrl = ""))

        assertNull("urlError should be null for blank URL", vm.urlError.value)
        assertEquals("", vm.config.value.serverUrl)
    }

    @Test fun `saving valid URL clears previous urlError`() = runTest {
        val vm = makeViewModel()
        // Set an error first
        vm.saveConfig(baseConfig.copy(serverUrl = "http://bad.example.com"))
        assertNotNull(vm.urlError.value)

        // Fix it with HTTPS
        vm.saveConfig(baseConfig.copy(serverUrl = "https://good.example.com"))
        assertNull("urlError should be cleared after valid URL", vm.urlError.value)
    }

    // ── Test connection ───────────────────────────────────────────────────────

    @Test fun `testConnection with successful health check produces Healthy state with latency`() = runTest {
        coEvery { mockUploader.checkHealth() } returns Result.success(Unit)
        val vm = makeViewModel(baseConfig.copy(serverUrl = "https://telemetry.example.com"))

        vm.testConnection()
        advanceUntilIdle()

        val state = vm.healthState.value
        assertTrue("Expected Healthy state, got $state", state is HealthState.Healthy)
        assertTrue("latencyMs should be >= 0", (state as HealthState.Healthy).latencyMs >= 0)
    }

    @Test fun `testConnection failure produces Unhealthy state with error message`() = runTest {
        coEvery { mockUploader.checkHealth() } returns Result.failure(IOException("connection refused"))
        val vm = makeViewModel(baseConfig.copy(serverUrl = "https://telemetry.example.com"))

        vm.testConnection()
        advanceUntilIdle()

        val state = vm.healthState.value
        assertTrue("Expected Unhealthy state, got $state", state is HealthState.Unhealthy)
        assertTrue(
            "Error message should contain the IOException message",
            (state as HealthState.Unhealthy).error.contains("connection refused"),
        )
    }

    @Test fun `testConnection with blank serverUrl immediately sets Unhealthy without network call`() = runTest {
        val vm = makeViewModel() // serverUrl is blank by default

        vm.testConnection()
        advanceUntilIdle()

        val state = vm.healthState.value
        assertTrue("Expected Unhealthy state for blank URL", state is HealthState.Unhealthy)
        // No network call should have been made
        coVerify(exactly = 0) { mockUploader.checkHealth() }
    }

    // ── Toggle saves ──────────────────────────────────────────────────────────

    @Test fun `saveConfig with bufferWhenOffline=false persists and updates state`() = runTest {
        val vm = makeViewModel()
        vm.saveConfig(baseConfig.copy(bufferWhenOffline = false))

        assertTrue(!vm.config.value.bufferWhenOffline)
        verify { mockRepo.save(match { !it.bufferWhenOffline }) }
    }

    @Test fun `saveConfig with sampleRateHz=5 persists correctly`() = runTest {
        val vm = makeViewModel()
        vm.saveConfig(baseConfig.copy(sampleRateHz = 5))

        assertEquals(5, vm.config.value.sampleRateHz)
        verify { mockRepo.save(match { it.sampleRateHz == 5 }) }
    }
}
