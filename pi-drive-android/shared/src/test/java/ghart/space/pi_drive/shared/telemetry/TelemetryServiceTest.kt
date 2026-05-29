package ghart.space.pi_drive.shared.telemetry

import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import ghart.space.pi_drive.shared.data.db.entity.PendingUploadEntity
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * Unit tests for [TelemetryUploadController].
 *
 * Tests the core upload-loop business logic in isolation — no Android Service lifecycle or
 * Flow required. Calls [TelemetryUploadController.processSnapshot] directly.
 */
class TelemetryServiceTest {

    private val mockUploader = mockk<TelemetryUploader>()
    private val mockDao = mockk<PendingUploadDao>()

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
        pendingDao = mockDao,
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

    @Test fun `successful upload does not queue to Room`() = runTest {
        coEvery { mockUploader.upload(any()) } returns Result.success(Unit)
        val controller = makeController()

        controller.processSnapshot(snapshot, emptyList(), configWithVin)

        coVerify(exactly = 0) { mockDao.insert(any()) }
    }

    // ── Upload failure → queued ────────────────────────────────────────────────

    @Test fun `upload failure queues payload to Room`() = runTest {
        coEvery { mockUploader.upload(any()) } returns Result.failure(IOException("timeout"))
        val insertedSlot = slot<PendingUploadEntity>()
        coEvery { mockDao.insert(capture(insertedSlot)) } returns 1L
        coEvery { mockDao.countPending() } returns 1

        val controller = makeController()
        controller.processSnapshot(snapshot, emptyList(), configWithVin)

        coVerify(exactly = 1) { mockDao.insert(any()) }
        assertTrue("Queued payload should not be blank", insertedSlot.captured.payload.isNotBlank())
        assertEquals(snapshot.timestamp, insertedSlot.captured.timestamp)
    }

    @Test fun `upload failure with bufferWhenOffline=false does not queue`() = runTest {
        coEvery { mockUploader.upload(any()) } returns Result.failure(IOException("timeout"))
        val config = configWithVin.copy(bufferWhenOffline = false)
        val controller = makeController()

        controller.processSnapshot(snapshot, emptyList(), config)

        coVerify(exactly = 0) { mockDao.insert(any()) }
    }

    // ── Blank VIN → skip ──────────────────────────────────────────────────────

    @Test fun `blank VIN skips upload without queuing`() = runTest {
        val controller = makeController()

        controller.processSnapshot(snapshot, emptyList(), configBlankVin)

        coVerify(exactly = 0) { mockUploader.upload(any()) }
        coVerify(exactly = 0) { mockDao.insert(any()) }
    }

    @Test fun `blank VIN with 5 snapshots never uploads or queues`() = runTest {
        val controller = makeController()

        repeat(5) {
            controller.processSnapshot(snapshot, emptyList(), configBlankVin)
        }

        coVerify(exactly = 0) { mockUploader.upload(any()) }
        coVerify(exactly = 0) { mockDao.insert(any()) }
    }
}
