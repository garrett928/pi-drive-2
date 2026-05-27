package ghart.space.pi_drive.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ghart.space.pi_drive.shared.data.db.PiDriveDatabase
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.dao.ManualTripDao
import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import ghart.space.pi_drive.shared.data.db.dao.SnapshotDao
import javax.inject.Singleton

/**
 * Hilt module that provides [PiDriveDatabase] and all its DAOs.
 *
 * The database itself is a [Singleton] — one instance for the entire process lifetime.
 * Each DAO is derived from that singleton and need not be individually scoped; Room
 * returns the same DAO object from each `database.fooDao()` call on a given instance.
 *
 * Individual DAO bindings let consumers depend on a specific DAO without holding a
 * reference to the whole database, which keeps the injection graph narrow and testable.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the application-scoped Room database.
     *
     * The database file is created on first access at the default Room path:
     * `<data-dir>/databases/pi_drive.db`. Construction is deferred until the first
     * DAO is requested.
     */
    @Provides
    @Singleton
    fun providePiDriveDatabase(@ApplicationContext context: Context): PiDriveDatabase =
        Room.databaseBuilder(context, PiDriveDatabase::class.java, PiDriveDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideSnapshotDao(db: PiDriveDatabase): SnapshotDao = db.snapshotDao()

    @Provides
    fun provideDrivingEventDao(db: PiDriveDatabase): DrivingEventDao = db.drivingEventDao()

    @Provides
    fun provideAutoTripDao(db: PiDriveDatabase): AutoTripDao = db.autoTripDao()

    @Provides
    fun provideManualTripDao(db: PiDriveDatabase): ManualTripDao = db.manualTripDao()

    @Provides
    fun providePendingUploadDao(db: PiDriveDatabase): PendingUploadDao = db.pendingUploadDao()
}
