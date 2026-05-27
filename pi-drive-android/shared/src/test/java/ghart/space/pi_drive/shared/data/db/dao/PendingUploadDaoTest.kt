package ghart.space.pi_drive.shared.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ghart.space.pi_drive.shared.data.db.PiDriveDatabase
import ghart.space.pi_drive.shared.data.db.entity.PendingUploadEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Unit tests for [PendingUploadDao] using an in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingUploadDaoTest {

    private lateinit var database: PiDriveDatabase
    private lateinit var dao: PendingUploadDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PiDriveDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.pendingUploadDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert 3, getNextBatch 2 returns 2, markUploaded, then getNextBatch returns remaining 1`() = runTest {
        val now = Instant.now()
        // All three items have nextRetryTime = EPOCH (immediately eligible)
        dao.insert(PendingUploadEntity(timestamp = now, payload = """{"seq":1}"""))
        dao.insert(PendingUploadEntity(timestamp = now, payload = """{"seq":2}"""))
        dao.insert(PendingUploadEntity(timestamp = now, payload = """{"seq":3}"""))

        // Batch of 2 should be returned
        val batch = dao.getNextBatch(limit = 2, now = Instant.now())
        assertEquals(2, batch.size)

        // Simulate successful upload by removing these two items
        batch.forEach { dao.markUploaded(it) }

        // Only 1 item should remain
        val remaining = dao.getNextBatch(limit = 10, now = Instant.now())
        assertEquals(1, remaining.size)
    }

    @Test
    fun `items with nextRetryTime in the future are excluded from batch`() = runTest {
        val now = Instant.now()
        val futureRetry = now.plusSeconds(3600)
        dao.insert(PendingUploadEntity(timestamp = now, payload = """{"ready":true}"""))
        dao.insert(PendingUploadEntity(timestamp = now, payload = """{"ready":false}""", nextRetryTime = futureRetry))

        val batch = dao.getNextBatch(limit = 10, now = now)

        // Only the item with EPOCH nextRetryTime is eligible
        assertEquals(1, batch.size)
    }

    @Test
    fun `countPending returns total queue size`() = runTest {
        val now = Instant.now()
        dao.insert(PendingUploadEntity(timestamp = now, payload = "{}"))
        dao.insert(PendingUploadEntity(timestamp = now, payload = "{}"))

        assertEquals(2, dao.countPending())
    }

    @Test
    fun `deleteUploaded removes all specified ids`() = runTest {
        val now = Instant.now()
        val id1 = dao.insert(PendingUploadEntity(timestamp = now, payload = """{"a":1}"""))
        val id2 = dao.insert(PendingUploadEntity(timestamp = now, payload = """{"b":2}"""))
        dao.insert(PendingUploadEntity(timestamp = now, payload = """{"c":3}"""))

        val deleted = dao.deleteUploaded(listOf(id1, id2))

        assertEquals(2, deleted)
        assertEquals(1, dao.countPending())
    }
}
