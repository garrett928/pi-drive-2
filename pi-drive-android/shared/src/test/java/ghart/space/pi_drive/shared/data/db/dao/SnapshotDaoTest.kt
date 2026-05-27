package ghart.space.pi_drive.shared.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ghart.space.pi_drive.shared.data.db.PiDriveDatabase
import ghart.space.pi_drive.shared.data.db.entity.SnapshotEntity
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
 * Unit tests for [SnapshotDao] using an in-memory Room database.
 *
 * Robolectric provides the Android context required to build the database
 * without a physical device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapshotDaoTest {

    private lateinit var database: PiDriveDatabase
    private lateinit var dao: SnapshotDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PiDriveDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.snapshotDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert 10 snapshots, getByTimeRange returns correct subset`() = runTest {
        val base = Instant.ofEpochMilli(1_000_000L)
        repeat(10) { i ->
            dao.insert(SnapshotEntity(timestamp = base.plusSeconds(i.toLong()), speedKmh = i * 10))
        }

        // Request seconds 3 through 6 inclusive → 4 snapshots (30, 40, 50, 60 km/h)
        val subset = dao.getByTimeRange(from = base.plusSeconds(3), to = base.plusSeconds(6))

        assertEquals(4, subset.size)
        assertEquals(30, subset.first().speedKmh)
        assertEquals(60, subset.last().speedKmh)
    }

    @Test
    fun `deleteOlderThan removes only rows before the cutoff`() = runTest {
        val base = Instant.ofEpochMilli(1_000_000L)
        repeat(10) { i ->
            dao.insert(SnapshotEntity(timestamp = base.plusSeconds(i.toLong())))
        }

        // Cutoff = second 5 → seconds 0..4 should be deleted (5 rows)
        val deleted = dao.deleteOlderThan(before = base.plusSeconds(5))

        assertEquals(5, deleted)

        val remaining = dao.getByTimeRange(from = Instant.EPOCH, to = Instant.ofEpochMilli(Long.MAX_VALUE / 2))
        assertEquals(5, remaining.size)
    }
}
