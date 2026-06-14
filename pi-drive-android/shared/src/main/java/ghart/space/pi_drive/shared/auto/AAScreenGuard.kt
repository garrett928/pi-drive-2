package ghart.space.pi_drive.shared.auto

import android.util.Log
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/**
 * Tag used by all Android Auto screen rendering. Kept in sync with the manifest service tag.
 */
private const val AA_TAG = "PiDrive"

/**
 * Runs an Android Auto template build inside a guard that converts a render-time crash into a
 * logged, visible error instead of tearing down the whole Car App session.
 *
 * ## Why this matters
 * The Car App Library validates the [Template] returned from `Screen.onGetTemplate()` against a
 * long list of constraints (item counts, mandatory images on [androidx.car.app.model.GridItem],
 * non-empty lists, action limits, …). A single violation throws — and because the host calls
 * `onGetTemplate()` on the app's main thread, an unhandled throw crashes the AA app on the head
 * unit with no on-device trace. This wrapper:
 * 1. Logs the **full stack trace** (so [FileLogger] captures exactly which constraint failed).
 * 2. Returns a minimal, always-valid [MessageTemplate] so the head unit shows a readable error
 *    and stays alive, instead of the session dying.
 *
 * @param screenName Human-readable screen name for the log line (e.g. "DialsScreen").
 * @param build      Produces the real template. Any exception is caught and reported.
 */
internal inline fun safeAATemplate(screenName: String, build: () -> Template): Template =
    try {
        build()
    } catch (e: Exception) {
        Log.e(AA_TAG, "AA $screenName.onGetTemplate FAILED — ${e::class.java.simpleName}: ${e.message}", e)
        MessageTemplate.Builder(
            "Pi Drive couldn't render $screenName.\n" +
                "${e::class.java.simpleName}: ${e.message ?: "unknown error"}\n\n" +
                "A log was saved. On the phone: Settings → Developer → Share logs.",
        )
            .setTitle("Pi Drive — error")
            // APP_ICON is valid as a header action on both root and pushed screens, so the
            // fallback itself never violates a template constraint.
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
