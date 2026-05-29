package ghart.space.pi_drive.shared.telemetry

import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * Unit tests for [TelemetryUploadController].
 *
 * Tests the core upload-loop business logic — no Android Service lifecycle or Flow required.
 * Calls [TelemetryUploadController.processSnapshot] directly.
 */
class TelemetryServiceTest {

    private val mockUploader = mockk<TelemetryUploader>()
    private val mockBuffer = mockk<OfflineBuffer>()

    private val configWithVin = TelemetryConfig(
        deviceId = "device-001",
        vin = "1HGCM82633A123456",
        serverUrl = "https://api.example.com",
        bufferWhenOffline = true,
        sampleRateHz = 1,
    )

    private val configBlankVin = configWithVin.copy(vin = "")

    private val snapshot = VehicleSnapshot(
        timestamp = Instant.parse("2026-05-24T22:15:30.000Z"),
        speedKmh = 80,
        rpm = 2000,
    )

    private fun makeController() = TelemetryUploadController(
        snapshots = kotlinx.coroutines.flow.emptyFlow(),
        uploader = mockUploader,
        offlineBuffer = mockBuffer,
    )

    // ── Upload attempts ────────────────────────────────────────────────────────

    @Test fun `5 snapshots cause 5 upload attempts`() = runTest {
        coEvery { mockUploader.upload(any()) } returns Result.success(Unit)
        val controller = makeController()

        repeat(5) {
            controller.processSnapshot(
                snapshot.copy(timestamp = Instant.ofEpochSecond(it.toLong())),
                emptyList(),
                configWithVin,
            )
        }

        coVerify(exactly = 5) { mockUploader.upload(any()) }
    }

    @Test fun `successful upload does not enqueue to offline buffer`() = runTest {
        coEvery { mockUploader.upload(any()) } returns Result.success(Unit)
        val controller = makeController()

        controller.processSnapshot(snapshot, emptyList(), configWithVin)

        coVerify(exactly = 0) { mockBuffer.enqueue(any()) }
    }

    // ── Upload failure → queued ────────────────────────────────────────────────

    @Test fun `upload failure enqueues payload to offline buffer`() = runTest {
        coEvery { mockUploader.upload(any()) } returns Result.failure(IOException("timeout"))
        coEvery { mockBuffer.enqueue(any()) } returns Unit
        val controller = makeController()

        controller.processSnapshot(snapshot, emptyList(), configWithVin)

        coVerify(exactly = 1) { mockBuffer.enqueue(any()) }
    }

    @Test fun `upload failure with bufferWhenOffline=false does not enqueue`() = runTest {
        coEvery { mockUploader.upload(any()) } returns Result.failure(IOException("timeout"))
        val config = configWithVin.copy(bufferWhenOffline = false)
        val controller = makeController()

        controller.processSnapshot(snapshot, emptyList(), config)

        coVerify(exactly = 0) { mockBuffer.enqueue(any()) }
    }

    // ── Blank VIN → skip ──────────────────────────────────────────────────────

    @Test fun `blank VIN skips upload without enqueueing`() = runTest {
        val controller = makeController()

        controller.processSnapshot(snapshot, emptyList(), configBlankVin)

        coVerify(exactly = 0) { mockUploader.upload(any()) }
        coVerify(exactly = 0) { mockBuffer.enqueue(any()) }
    }

    @Test fun `blank VIN with 5 snapshots never uploads or enqueues`() = runTest {
        val controller = makeController()

        repeat(5) {
            controller.processSnapshot(snapshot, emptyList(), configBlankVin)
        }

        coVerify(exactly = 0) { mockUploader.upload(any()) }
        coVerify(exactly = 0) { mockBuffer.enqueue(any()) }
    }
}
