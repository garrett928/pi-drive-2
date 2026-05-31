package ghart.space.pi_drive

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import ghart.space.pi_drive.shared.data.db.dao.SnapshotDao
import ghart.space.pi_drive.shared.data.db.entity.SnapshotEntity
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports trip snapshot data to a CSV file and provides an Android ShareSheet intent.
 *
 * The [toCsv] companion function is a pure conversion utility that is independently
 * testable. The [createShareIntent] instance method handles all Android I/O (cache file
 * creation and FileProvider URI generation) so the test-facing logic stays isolated.
 *
 * CSV column units:
 * - Speed: mph (converted from km/h)
 * - Temperature: °F (converted from °C)
 * - All other fields: SI units as reported by the OBD adapter
 *
 * @param context     App context used to resolve the cache directory and FileProvider authority.
 * @param snapshotDao Queries per-trip snapshots from Room.
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotDao: SnapshotDao,
) {

    /**
     * Queries snapshots for [tripId], writes them to a CSV in the app's cache directory,
     * and returns a chooser [Intent] for sharing via the Android ShareSheet.
     *
     * The file is placed in [Context.cacheDir] so the OS can evict it under storage
     * pressure, and it is served via [FileProvider] so other apps can read it without
     * requiring the WRITE_EXTERNAL_STORAGE permission.
     *
     * @param tripId    The trip whose snapshots to export.
     * @param tripTitle Used to derive the suggested filename.
     */
    suspend fun createShareIntent(tripId: Long, tripTitle: String): Intent {
        val snapshots = snapshotDao.getByTripId(tripId)
        val csv = toCsv(snapshots)
        val safeName = tripTitle.replace(Regex("[^\\w-]"), "_")
        val file = File(context.cacheDir, "pidrive_${safeName}_$tripId.csv")
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Pi Drive trip: $tripTitle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {

        /** Header row for all exported CSV files. */
        const val CSV_HEADER =
            "timestamp,lat,lng,speed_mph,rpm,coolant_temp_f,throttle_pct,fuel_level_pct," +
            "oil_temp_f,maf_gps,fuel_rate,battery_v,accel_mph_s,g_force"

        private val timestampFmt = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneId.of("UTC"))

        /**
         * Converts a list of [SnapshotEntity] records to CSV text.
         *
         * Pure function — no Android dependencies; directly testable without instrumentation.
         * Null fields are emitted as empty strings. Temperatures are converted from °C to °F.
         * Speed is converted from km/h to mph.
         *
         * @param snapshots Snapshots ordered by ascending timestamp (as returned by
         *                  [SnapshotDao.getByTripId]).
         */
        fun toCsv(snapshots: List<SnapshotEntity>): String = buildString {
            appendLine(CSV_HEADER)
            for (s in snapshots) {
                val ts = timestampFmt.format(s.timestamp)
                val speedMph = s.speedKmh?.let { it * 0.621371f }
                val coolantF = s.coolantTempC?.let { it * 9f / 5f + 32f }
                val oilF = s.oilTempC?.let { it * 9f / 5f + 32f }
                append(ts); append(',')
                append(s.gpsLat ?: ""); append(',')
                append(s.gpsLng ?: ""); append(',')
                append(speedMph?.let { "%.2f".format(it) } ?: ""); append(',')
                append(s.rpm ?: ""); append(',')
                append(coolantF?.let { "%.1f".format(it) } ?: ""); append(',')
                append(s.throttlePct?.let { "%.1f".format(it) } ?: ""); append(',')
                append(s.fuelLevelPct?.let { "%.1f".format(it) } ?: ""); append(',')
                append(oilF?.let { "%.1f".format(it) } ?: ""); append(',')
                append(s.mafGps?.let { "%.3f".format(it) } ?: ""); append(',')
                append(s.fuelRateLph?.let { "%.3f".format(it) } ?: ""); append(',')
                append(s.batteryVoltage?.let { "%.2f".format(it) } ?: ""); append(',')
                append(s.accelRateMphS?.let { "%.3f".format(it) } ?: ""); append(',')
                appendLine(s.gForce?.let { "%.3f".format(it) } ?: "")
            }
        }
    }
}
