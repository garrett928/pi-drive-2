package ghart.space.pi_drive.shared.diag

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * On-device diagnostic logger that persists the app's logcat output to files so a session
 * can be captured in the car (no laptop) and reviewed later.
 *
 * ## Why this exists
 * The whole app already logs to logcat via [android.util.Log] with consistent tags
 * (`PiDrive`, `OBDTransport`, `VehicleData`, …). Normally those logs only exist on a
 * connected `adb` session. [FileLogger] tees the same logcat stream to a rotating file in
 * the app's external files directory so the user can:
 * 1. Drive and reproduce a bug with the adapter connected.
 * 2. Bring the phone back inside.
 * 3. Share the log (in-app button, USB MTP, or `adb pull`) and commit it to the repo.
 *
 * ## How it captures
 * Two independent mechanisms, for redundancy:
 * - **logcat pump** — a background thread runs `logcat --pid=<ours>` and appends every line
 *   to the current session file. This captures *all* existing [Log] calls plus any
 *   framework/Car-App-Library output for our process, without modifying call sites.
 *   The Android Auto [androidx.car.app.CarAppService] runs in this same process, so its
 *   render crashes land in the same stream.
 * - **uncaught-exception handler** — guarantees a fatal crash's stack trace is flushed to a
 *   dedicated `*-crash-*.log` file *synchronously* before the process dies, even if the
 *   logcat pump thread is torn down first. It then delegates to the previously-installed
 *   handler so normal crash reporting still happens.
 *
 * ## Permissions
 * Reading `logcat --pid=<ours>` returns only this app's own UID logs and needs no special
 * permission. Writing to [Context.getExternalFilesDir] needs no permission since Android 4.4.
 *
 * The object is a process-wide singleton; [init] is idempotent.
 */
object FileLogger {

    private const val TAG = "PiDrive"

    /** Subdirectory of [Context.getExternalFilesDir] that holds rolling session logs. */
    private const val LOG_DIR = "logs"

    /** Roll to a new segment file once the current one passes this size. */
    private const val MAX_SEGMENT_BYTES = 4L * 1024 * 1024 // 4 MB

    /** Keep at most this many log + crash files; older ones are pruned on [init]. */
    private const val MAX_FILES_KEPT = 10

    /** SharedPreferences file storing the capture-enabled flag (read at process start). */
    private const val PREFS_NAME = "pi_drive_diag"
    private const val KEY_CAPTURE_ENABLED = "log_capture_enabled"

    /** FileProvider authority suffix — must match the `<provider>` in the mobile manifest. */
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    private val fileTimestampFmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val lineTimestampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // ── Mutable capture state (guarded by `this`) ───────────────────────────────

    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null
    @Volatile private var logcatProcess: Process? = null
    @Volatile private var pumpThread: Thread? = null
    @Volatile private var running = false

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Initializes diagnostics for the process. Call once from `Application.onCreate()`.
     *
     * Always installs the crash handler (cheap, high value). Starts the logcat pump only if
     * capture is currently enabled (default: true). Safe to call multiple times.
     *
     * @param context Any context; the application context is retained.
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true

        installCrashHandler()
        pruneOldFiles()

        if (isCaptureEnabled(context)) {
            startCapture()
        }
        Log.i(TAG, "FileLogger: initialized (capture=${isCaptureEnabled(context)}, dir=${logDir()?.absolutePath})")
    }

    /** Whether logcat-to-file capture is currently enabled. Defaults to true. */
    fun isCaptureEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CAPTURE_ENABLED, true)

    /**
     * Enables or disables logcat-to-file capture and applies it immediately.
     *
     * The crash handler stays installed regardless. The new value persists across launches.
     */
    @Synchronized
    fun setCaptureEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CAPTURE_ENABLED, enabled).apply()
        if (enabled) startCapture() else stopCapture()
        Log.i(TAG, "FileLogger: capture set to $enabled")
    }

    /**
     * A one-line human summary of captured logs for display in the dev-settings screen,
     * e.g. `"3 files · 1.2 MB"`. Returns a friendly message when there is nothing yet.
     */
    fun summary(context: Context): String {
        val files = logFiles()
        if (files.isEmpty()) return "No logs captured yet"
        val totalBytes = files.sumOf { it.length() }
        return "${files.size} file(s) · ${humanBytes(totalBytes)}"
    }

    /**
     * Zips every captured log + crash file into a single archive in the cache directory and
     * returns an [Intent.ACTION_SEND] chooser-ready intent for sharing (email, Drive, etc.).
     *
     * Served via [FileProvider] so the receiving app needs no storage permission. Returns
     * null when there are no logs to share.
     */
    fun createShareIntent(context: Context): Intent? {
        val files = logFiles()
        if (files.isEmpty()) return null

        val zip = File(context.cacheDir, "pidrive-logs-${fileTimestampFmt.format(Date())}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            for (file in files) {
                zos.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$FILE_PROVIDER_SUFFIX",
            zip,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Pi Drive diagnostic logs")
            putExtra(
                Intent.EXTRA_TEXT,
                "Pi Drive logs — device ${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "Android ${Build.VERSION.RELEASE}, captured ${lineTimestampFmt.format(Date())}.",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Deletes all captured log + crash files, then restarts capture if it was enabled. */
    @Synchronized
    fun clear(context: Context) {
        val wasRunning = running
        stopCapture()
        logFiles().forEach { it.delete() }
        if (wasRunning) startCapture()
        Log.i(TAG, "FileLogger: cleared logs")
    }

    /**
     * Writes a single line directly into the current session file, bypassing logcat.
     *
     * Useful for milestone markers (e.g. "user tapped Connect") that should appear even if
     * the logcat pump is momentarily behind. Also mirrors to logcat. No-op if not capturing.
     */
    fun mark(message: String) {
        Log.i(TAG, "MARK: $message")
        val file = currentFileOrNull() ?: return
        runCatching {
            FileWriter(file, /* append = */ true).use { w ->
                w.appendLine("${lineTimestampFmt.format(Date())}  ----- MARK: $message")
            }
        }
    }

    // ── logcat pump ───────────────────────────────────────────────────────────

    @Synchronized
    private fun startCapture() {
        if (running) return
        val dir = logDir() ?: return
        running = true

        val sessionFile = File(dir, "pidrive-${fileTimestampFmt.format(Date())}.log")
        currentFile = sessionFile

        val thread = Thread({ pumpLoop(sessionFile) }, "pidrive-logcat-pump").apply {
            isDaemon = true
        }
        pumpThread = thread
        thread.start()
    }

    @Synchronized
    private fun stopCapture() {
        running = false
        logcatProcess?.destroy()
        logcatProcess = null
        pumpThread?.interrupt()
        pumpThread = null
        currentFile = null
    }

    @Volatile private var currentFile: File? = null

    private fun currentFileOrNull(): File? = currentFile

    /**
     * Body of the pump thread: spawns `logcat` filtered to our own PID and appends each line
     * to [startFile], rolling to a fresh segment when it grows past [MAX_SEGMENT_BYTES].
     * Flushes after every line so an abrupt process death loses at most the in-flight line.
     */
    private fun pumpLoop(startFile: File) {
        var activeFile = startFile
        var writer = runCatching { FileWriter(activeFile, true) }.getOrNull() ?: run {
            running = false
            return
        }
        var bytesWritten = activeFile.length()

        // Header so each segment is self-describing in the repo.
        runCatching {
            writer.appendLine(sessionHeader())
            writer.flush()
        }

        try {
            // `--pid` limits output to this process (includes the Car App service, same UID).
            // `-v threadtime` adds date/time/pid/tid/level/tag to every line.
            val cmd = arrayOf("logcat", "-v", "threadtime", "--pid=${android.os.Process.myPid()}")
            val process = Runtime.getRuntime().exec(cmd)
            logcatProcess = process
            val reader = process.inputStream.bufferedReader()

            while (running && !Thread.currentThread().isInterrupted) {
                val line = reader.readLine() ?: break
                writer.append(line).append('\n')
                writer.flush()
                bytesWritten += line.length + 1

                if (bytesWritten >= MAX_SEGMENT_BYTES) {
                    runCatching { writer.close() }
                    activeFile = File(logDir(), "pidrive-${fileTimestampFmt.format(Date())}.log")
                    currentFile = activeFile
                    writer = FileWriter(activeFile, true)
                    writer.appendLine(sessionHeader())
                    writer.flush()
                    bytesWritten = 0
                    pruneOldFiles()
                }
            }
        } catch (_: InterruptedException) {
            // Normal stop.
        } catch (e: Exception) {
            Log.w(TAG, "FileLogger: pump loop ended: ${e.message}")
        } finally {
            runCatching { writer.close() }
            logcatProcess?.destroy()
        }
    }

    // ── Crash handler ───────────────────────────────────────────────────────────

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            // Preserve normal crash behaviour (system dialog, other reporters).
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Writes a full crash report to a dedicated file, synchronously and flushed. */
    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val dir = logDir() ?: return
        val file = File(dir, "pidrive-crash-${fileTimestampFmt.format(Date())}.log")
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        FileWriter(file).use { w ->
            w.appendLine(sessionHeader())
            w.appendLine("FATAL CRASH at ${lineTimestampFmt.format(Date())}")
            w.appendLine("thread: ${thread.name} (id=${thread.id})")
            w.appendLine("message: ${throwable.message}")
            w.appendLine("--- stack trace ---")
            w.append(stack)
            w.flush()
        }
        Log.e(TAG, "FileLogger: wrote crash report to ${file.name}", throwable)
    }

    // ── File helpers ─────────────────────────────────────────────────────────────

    /** The directory holding session + crash logs, or null if external storage is unavailable. */
    fun logDir(): File? {
        val dir = appContext?.getExternalFilesDir(LOG_DIR) ?: return null
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun logFiles(): List<File> =
        logDir()?.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedBy { it.name }
            ?: emptyList()

    /** Keeps only the [MAX_FILES_KEPT] newest log/crash files. */
    private fun pruneOldFiles() {
        val files = logFiles()
        if (files.size <= MAX_FILES_KEPT) return
        files.dropLast(MAX_FILES_KEPT).forEach { it.delete() }
    }

    private fun sessionHeader(): String =
        "===== Pi Drive log · pid=${android.os.Process.myPid()} · " +
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · " +
            "${lineTimestampFmt.format(Date())} ====="

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun humanBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
