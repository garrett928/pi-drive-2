package ghart.space.pi_drive.shared.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ghart.space.pi_drive.shared.data.db.PiDriveDatabase
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Unit tests for [AutoTripDao] using an in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoTripDaoTest {

    private lateinit var database: PiDriveDatabase
    private lateinit var dao: AutoTripDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PiDriveDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.autoTripDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert trip with null endTime, getActive returns it`() = runTest {
        val startTime = Instant.now()
        dao.insert(AutoTripEntity(startTime = startTime))

        val active = dao.getActive()

        assertNotNull(active)
        assertEquals(startTime.toEpochMilli(), active!!.startTime.toEpochMilli())
        assertNull(active.endTime)
    }

    @Test
    fun `update trip with endTime, getActive returns null`() = runTest {
        val startTime = Instant.now()
        val id = dao.insert(AutoTripEntity(startTime = startTime))

        // Close the trip by setting endTime
        val endTime = startTime.plusSeconds(300)
        dao.update(AutoTripEntity(id = id, startTime = startTime, endTime = endTime))

        assertNull(dao.getActive())
    }

    @Test
    fun `only the trip with null endTime is returned as active`() = runTest {
        val base = Instant.now()
        // Insert one closed trip and one active trip
        dao.insert(AutoTripEntity(startTime = base, endTime = base.plusSeconds(60)))
        dao.insert(AutoTripEntity(startTime = base.plusSeconds(120)))

        val active = dao.getActive()

        assertNotNull(active)
        assertNull(active!!.endTime)
    }
}
