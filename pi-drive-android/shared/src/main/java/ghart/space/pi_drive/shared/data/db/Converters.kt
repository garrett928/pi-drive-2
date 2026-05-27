package ghart.space.pi_drive.shared.data.db

import androidx.room.TypeConverter
import ghart.space.pi_drive.shared.data.db.entity.SyncStatus
import ghart.space.pi_drive.shared.data.model.DataSource
import ghart.space.pi_drive.shared.data.model.DetectionStrategy
import ghart.space.pi_drive.shared.data.model.EventType
import java.time.Instant

/**
 * Room TypeConverters for types not natively supported by SQLite.
 *
 * Registered at the database level via [@TypeConverters][androidx.room.TypeConverters]
 * on [PiDriveDatabase], making them available to all entities and DAO query parameters
 * within the database.
 *
 * Enum types are persisted as their [Enum.name] string so that the stored values remain
 * readable in a SQLite browser and survive obfuscation (which would break ordinal storage).
 */
class Converters {

    /** Stores [Instant] as epoch milliseconds (SQLite INTEGER). */
    @TypeConverter
    fun instantToLong(value: Instant): Long = value.toEpochMilli()

    /** Restores [Instant] from epoch milliseconds. */
    @TypeConverter
    fun longToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun detectionStrategyToString(value: DetectionStrategy): String = value.name

    @TypeConverter
    fun stringToDetectionStrategy(value: String): DetectionStrategy =
        DetectionStrategy.valueOf(value)

    @TypeConverter
    fun eventTypeToString(value: EventType): String = value.name

    @TypeConverter
    fun stringToEventType(value: String): EventType = EventType.valueOf(value)

    /**
     * Persists a [Set]<[DataSource]> as a comma-separated string of enum names.
     *
     * Empty set is stored as an empty string, not "null", so that the column
     * remains non-null and the round-trip is lossless.
     */
    @TypeConverter
    fun dataSourceSetToString(value: Set<DataSource>): String =
        value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToDataSourceSet(value: String): Set<DataSource> =
        if (value.isBlank()) emptySet()
        else value.split(",").map { DataSource.valueOf(it.trim()) }.toSet()

    @TypeConverter
    fun syncStatusToString(value: SyncStatus): String = value.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
