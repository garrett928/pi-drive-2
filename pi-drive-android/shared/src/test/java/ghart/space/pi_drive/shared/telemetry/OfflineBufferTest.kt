package ghart.space.pi_drive.shared.telemetry

import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import ghart.space.pi_drive.shared.data.db.entity.PendingUploadEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [OfflineBuffer].
 *
 * Uses an in-memory [FakePendingUploadDao] rather than a real Room database so tests
 * run as plain JVM unit tests without Robolectric.
 */
class OfflineBufferTest {

    private val dao = FakePendingUploadDao()
    private val buffer = OfflineBuffer(dao)

    private val samplePayload = TelemetryPayload(
        timestamp = "2026-05-24T22:15:30.123Z",
        deviceId = "dev",
        vin = "1HGCM82633A123456",
        location = null,
        obd = OBDPayload(null, null, null, null, null, null, null, null, null, null),
        calculated = CalculatedPayload(null, null),
        accelMps2 = null,
        events = emptyList(),
    )

    // ── Enqueue + batch retrieval ──────────────────────────────────────────────

    @Test fun `enqueue adds item to DAO`() = runTest {
        buffer.enqueue(samplePayload)
        assertEquals(1, dao.items.size)
    }

    @Test fun `enqueued payload is not blank`() = runTest {
        buffer.enqueue(samplePayload)
        assertTrue(dao.items.first().payload.isNotBlank())
    }

    @Test fun `getNextBatch with limit 3 returns at most 3 items`() = runTest {
        repeat(5) { buffer.enqueue(samplePayload) }
        val batch = buffer.getNextBatch(3)
        assertEquals(3, batch.size)
    }

    @Test fun `markUploaded removes items from queue`() = runTest {
        repeat(5) { buffer.enqueue(samplePayload) }

        val batch = buffer.getNextBatch(3)
        assertEquals(3, batch.size)

        buffer.markUploaded(batch.map { it.id })

        val remaining = buffer.getNextBatch(10)
        assertEquals(2, remaining.size)
    }

    @Test fun `pendingCount reflects current queue depth`() = runTest {
        repeat(5) { buffer.enqueue(samplePayload) }
        assertEquals(5, buffer.pendingCount())

        val batch = buffer.getNextBatch(3)
        buffer.markUploaded(batch.map { it.id })
        assertEquals(2, buffer.pendingCount())
    }

    // ── Exponential backoff ────────────────────────────────────────────────────

    @Test fun `incrementRetry bumps retryCount`() = runTest {
        buffer.enqueue(samplePayload)
        val id = dao.items.first().id

        buffer.incrementRetry(id)

        assertEquals(1, dao.items.first().retryCount)
    }

    @Test fun `incrementRetry advances nextRetryTime`() = runTest {
        buffer.enqueue(samplePayload)
        val id = dao.items.first().id
        val before = Instant.now()

        buffer.incrementRetry(id)

        val entity = dao.items.first()
        assertTrue(
            "nextRetryTime should be after now",
            entity.nextRetryTime.isAfter(before),
        )
    }

    @Test fun `each incrementRetry increases nextRetryTime exponentially`() = runTest {
        buffer.enqueue(samplePayload)
        val id = dao.items.first().id

        buffer.incrementRetry(id)
        val firstDelay = dao.items.first().nextRetryTime

        // Manually reset retryCount to 1 (simulate second failure)
        dao.items[0] = dao.items[0].copy(retryCount = 1, nextRetryTime = Instant.EPOCH)

        val beforeSecond = Instant.now()
        buffer.incrementRetry(id)
        val secondDelay = dao.items.first().nextRetryTime

        // Second back-off (60 s) > first back-off (30 s)
        assertTrue(
            "Second retry delay should be after first",
            secondDelay.epochSecond > firstDelay.epochSecond,
        )
        // And both are in the future
        assertTrue(secondDelay.isAfter(beforeSecond))
    }

    @Test fun `incrementRetry at max retries discards the item`() = runTest {
        buffer.enqueue(samplePayload)
        val id = dao.items.first().id
        // Fast-forward to 1 below the max
        dao.items[0] = dao.items[0].copy(retryCount = 10)

        buffer.incrementRetry(id)

        assertTrue("Item should be deleted after max retries", dao.items.isEmpty())
    }

    // ── Fake DAO ──────────────────────────────────────────────────────────────

    /** Simple in-memory implementation of [PendingUploadDao] for unit tests. */
    private class FakePendingUploadDao : PendingUploadDao {

        val items = mutableListOf<PendingUploadEntity>()
        private var nextId = 1L

        override suspend fun insert(upload: PendingUploadEntity): Long {
            val id = nextId++
            items.add(upload.copy(id = id))
            return id
        }

        override suspend fun getNextBatch(limit: Int, now: Instant): List<PendingUploadEntity> =
            items.filter { it.nextRetryTime <= now }
                .sortedBy { it.timestamp }
                .take(limit)

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
