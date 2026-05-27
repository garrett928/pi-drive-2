package ghart.space.pi_drive.shared.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.dao.ManualTripDao
import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import ghart.space.pi_drive.shared.data.db.dao.SnapshotDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.db.entity.ManualTripEntity
import ghart.space.pi_drive.shared.data.db.entity.PendingUploadEntity
import ghart.space.pi_drive.shared.data.db.entity.SnapshotEntity

/**
 * Root Room database for Pi Drive, version 1.
 *
 * All five entity tables live in this single database. When the schema changes,
 * bump [version] and supply an [androidx.room.migration.Migration] — never
 * use `fallbackToDestructiveMigration` in production.
 *
 * Construct via [androidx.room.Room.databaseBuilder] using [DATABASE_NAME].
 * Provided as a singleton by the Hilt [ghart.space.pi_drive.di.DatabaseModule].
 */
@Database(
    entities = [
        SnapshotEntity::class,
        DrivingEventEntity::class,
        AutoTripEntity::class,
        ManualTripEntity::class,
        PendingUploadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PiDriveDatabase : RoomDatabase() {

    abstract fun snapshotDao(): SnapshotDao
    abstract fun drivingEventDao(): DrivingEventDao
    abstract fun autoTripDao(): AutoTripDao
    abstract fun manualTripDao(): ManualTripDao
    abstract fun pendingUploadDao(): PendingUploadDao

    companion object {
        const val DATABASE_NAME = "pi_drive.db"
    }
}
