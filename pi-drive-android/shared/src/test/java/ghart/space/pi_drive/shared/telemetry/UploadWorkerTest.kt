package ghart.space.pi_drive.shared.telemetry

import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import ghart.space.pi_drive.shared.data.db.entity.PendingUploadEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * Unit tests for [UploadWorker.processBatch].
 *
 * Uses a [FakePendingUploadDao] and a mock [TelemetryUploader] to exercise the batch
 * processing logic without a WorkManager lifecycle or Android context.
 */
class UploadWorkerTest {

    private val mockUploader = mockk<TelemetryUploader>()

    private val samplePayloadJson = """{"timestamp":"2026-05-24T22:15:30.123Z","device_id":"dev","vin":"VIN","obd":{},"calculated":{},"events":[]}"""

    /** Creates [count] pre-populated [PendingUploadEntity] rows in the fake DAO. */
    private fun makeBuffer(count: Int): OfflineBuffer {
        val dao = FakePendingUploadDao()
        repeat(count) { i ->
            dao.items.add(
                PendingUploadEntity(
                    id = (i + 1).toLong(),
                    timestamp = Instant.now(),
                    payload = samplePayloadJson,
                    retryCount = 0,
                    nextRetryTime = Instant.EPOCH,
                )
            )
        }
        return OfflineBuffer(dao)
    }

    // ── All succeed ───────────────────────────────────────────────────────────

    @Test fun `3 queued, all succeed, all marked uploaded`() = runTest {
        coEvery { mockUploader.uploadRaw(any()) } returns Result.success(Unit)
        val buffer = makeBuffer(3)

        val hasMore = UploadWorker.processBatch(buffer, mockUploader)

        assertFalse("Queue should be empty", hasMore)
        assertEquals(0, buffer.pendingCount())
        coVerify(exactly = 3) { mockUploader.uploadRaw(any()) }
    }

    @Test fun `empty queue returns false without calling uploader`() = runTest {
        val buffer = makeBuffer(0)

        val hasMore = UploadWorker.processBatch(buffer, mockUploader)

        assertFalse(hasMore)
        coVerify(exactly = 0) { mockUploader.uploadRaw(any()) }
    }

    // ── Partial failure ───────────────────────────────────────────────────────

    @Test fun `uploader fails on 2nd item - 1st uploaded, 2nd retried, 3rd still queued`() = runTest {
        var callCount = 0
        coEvery { mockUploader.uploadRaw(any()) } answers {
            callCount++
            if (callCount == 2) Result.failure(IOException("server error")) else Result.success(Unit)
        }
        val buffer = makeBuffer(3)

        val hasMore = UploadWorker.processBatch(buffer, mockUploader)

        assertTrue("Should signal more work remaining", hasMore)
        // 1st item removed, 2nd has retryCount=1, 3rd untouched → 2 items remain
        assertEquals(2, buffer.pendingCount())
        // Only 2 upload attempts (stopped after 2nd failure)
        coVerify(exactly = 2) { mockUploader.uploadRaw(any()) }
    }

    @Test fun `first item fails, remaining items not attempted`() = runTest {
        coEvery { mockUploader.uploadRaw(any()) } returns Result.failure(IOException("offline"))
        val buffer = makeBuffer(3)

        val hasMore = UploadWorker.processBatch(buffer, mockUploader)

        assertTrue(hasMore)
        assertEquals(3, buffer.pendingCount())
        // Only 1 attempt — stopped after first failure
        coVerify(exactly = 1) { mockUploader.uploadRaw(any()) }
    }

    @Test fun `all 3 fail, hasMore is true`() = runTest {
        coEvery { mockUploader.uploadRaw(any()) } returns Result.failure(IOException("down"))
        val buffer = makeBuffer(3)

        val hasMore = UploadWorker.processBatch(buffer, mockUploader)

        assertTrue(hasMore)
    }

    // ── More items than batch ──────────────────────────────────────────────────

    @Test fun `returns true when more items remain after full batch succeeds`() = runTest {
        // processBatch uses BATCH_SIZE=50; fill 51 items so 1 remains after the batch
        coEvery { mockUploader.uploadRaw(any()) } returns Result.success(Unit)
        val dao = FakePendingUploadDao()
        repeat(51) { i ->
            dao.items.add(
                PendingUploadEntity(
                    id = (i + 1).toLong(),
                    timestamp = Instant.now(),
                    payload = samplePayloadJson,
                    nextRetryTime = Instant.EPOCH,
                )
            )
        }
        val buffer = OfflineBuffer(dao)

        val hasMore = UploadWorker.processBatch(buffer, mockUploader)

        assertTrue("Should return true when 1 item remains", hasMore)
        assertEquals(1, buffer.pendingCount())
    }

    // ── Fake DAO (duplicated from OfflineBufferTest for isolation) ─────────────

    private class FakePendingUploadDao : PendingUploadDao {
        val items = mutableListOf<PendingUploadEntity>()
        private var nextId = 1L

        override suspend fun insert(upload: PendingUploadEntity): Long {
            val id = nextId++
            items.add(upload.copy(id = id))
            return id
        }

        override suspend fun getNextBatch(limit: Int, now: Instant): List<PendingUploadEntity> =
            items.filter { it.nextRetryTime <= now }.sortedBy { it.timestamp }.take(limit)

        override suspend fun markUploaded(upload: PendingUploadEntity) {
            items.removeAll { it.id == upload.id }
        }

        override suspend fun deleteUploaded(ids: List<Long>): Int {
            val before = items.size
            items.removeAll { it.id in ids }
            return before - items.size
        }

        override suspend fun countPending(): Int = items.size

        override suspend fun getById(id: Long): PendingUploadEntity? =
            items.find { it.id == id }

        override suspend fun update(entity: PendingUploadEntity) {
            val index = items.indexOfFirst { it.id == entity.id }
            if (index >= 0) items[index] = entity
        }
    }
}
