package ghart.space.pi_drive.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.EventType
import kotlinx.coroutines.delay

/** Milliseconds the alert banner stays visible before auto-dismissing. */
private const val AUTO_DISMISS_DELAY_MS = 3_000L

/**
 * Overlay banner that slides down from the top of the screen when an [AlertAction] fires.
 *
 * Behaviour:
 * - Triggers [HapticFeedbackType.LongPress] on appearance.
 * - Auto-dismisses after 3 s by invoking [onDismiss].
 * - The user can manually dismiss via the close button.
 *
 * Driven by a nullable [AlertAction]: the banner is visible when non-null and animates
 * out when set back to null.
 *
 * @param alert     The current alert to display, or null when no alert is active.
 * @param onDismiss Called when the banner should be hidden (timeout or manual close).
 */
@Composable
fun AlertOverlay(
    alert: AlertAction?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(alert) {
        if (alert != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(AUTO_DISMISS_DELAY_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        if (alert != null) {
            AlertBanner(alert = alert, onDismiss = onDismiss)
        }
    }
}

/**
 * Internal banner content rendered inside [AlertOverlay] when an alert is active.
 */
@Composable
private fun AlertBanner(
    alert: AlertAction,
    onDismiss: () -> Unit,
) {
    val isSevere: Boolean
    val title: String
    val detail: String

    when (alert) {
        is AlertAction.DrivingEventAlert -> {
            isSevere = alert.isSevere
            title = when (alert.event.type) {
                EventType.HARD_ACCEL -> "Hard Acceleration"
                EventType.HARD_BRAKE -> if (alert.isSevere) "Severe Braking" else "Hard Brake"
            }
            detail = alert.event.rateMphS
                ?.let { "%.1f mph/s".format(it) }
                ?: alert.event.peakG
                    ?.let { "%.2f g".format(it) }
                ?: ""
        }
        is AlertAction.HealthAlert -> {
            isSevere = true
            title = alert.type.displayName
            detail = alert.message
        }
    }

    val backgroundColor = if (isSevere)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.errorContainer

    val contentColor = if (isSevere)
        MaterialTheme.colorScheme.onError
    else
        MaterialTheme.colorScheme.onErrorContainer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = RoundedCornerShape(12.dp))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss alert",
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
