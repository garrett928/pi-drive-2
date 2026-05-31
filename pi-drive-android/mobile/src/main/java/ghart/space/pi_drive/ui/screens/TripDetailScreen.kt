package ghart.space.pi_drive.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.model.DetectionStrategy
import ghart.space.pi_drive.shared.ui.components.PDButtonPrimary
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.theme.PiDriveFontFamily
import ghart.space.pi_drive.shared.ui.theme.PiDriveMonoFontFamily
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.viewmodel.TripDetailViewModel
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dtFormatter = DateTimeFormatter
    .ofPattern("MMM d, yyyy  h:mm a")
    .withZone(ZoneId.systemDefault())

private val timeOnlyFmt = DateTimeFormatter
    .ofPattern("h:mm a")
    .withZone(ZoneId.systemDefault())

/**
 * Trip detail screen — full trip summary, driving event list, and CSV export share button.
 *
 * Receives `tripId` via the navigation back stack; [TripDetailViewModel] extracts it
 * from [androidx.lifecycle.SavedStateHandle].
 */
@Composable
fun TripDetailScreen(navController: NavController) {
    val viewModel: TripDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = PiDriveTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent.base)
        }
        return
    }

    val trip = uiState.trip
    if (trip == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Trip not found.",
                style = PiDriveTheme.typography.bodyMedium,
                color = colors.fgMuted,
            )
        }
        return
    }

    val title = buildTripTitle(trip.startTime)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // ── Summary card ──────────────────────────────────────────────────────
        item(key = "summary") {
            PDCard {
                Text(
                    text = title,
                    style = PiDriveTheme.typography.titleMedium,
                    color = colors.fg,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dtFormatter.format(trip.startTime),
                    style = PiDriveTheme.typography.bodySmall,
                    color = colors.fgMuted,
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.borderS)
                Spacer(Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DetailStat("%.1f".format(trip.distanceMi), "mi", "Distance")
                        DetailStat(formatDurationMs(trip.durationMs), null, "Duration")
                        DetailStat("%.0f".format(trip.avgSpeedMph), "mph", "Avg speed")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DetailStat("%.0f".format(trip.maxSpeedMph), "mph", "Max speed")
                        DetailStat(
                            value = trip.avgMpg?.let { "%.1f".format(it) } ?: "--",
                            unit = if (trip.avgMpg != null) "mpg" else null,
                            label = "Avg MPG",
                            accent = trip.avgMpg != null,
                        )
                        DetailStat("${trip.eventCount}", null, "Events")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Driving events ────────────────────────────────────────────────────
        if (uiState.events.isNotEmpty()) {
            item(key = "events_header") {
                Text(
                    text = "EVENTS",
                    style = PiDriveTheme.typography.labelSmall,
                    color = colors.fgMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            item(key = "events_card") {
                PDCard(contentPadding = 0.dp) {
                    uiState.events.forEachIndexed { index, event ->
                        if (index > 0) HorizontalDivider(color = colors.borderS)
                        EventRow(event = event)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // ── Export button ─────────────────────────────────────────────────────
        item(key = "export") {
            PDButtonPrimary(
                text = "Export CSV",
                onClick = {
                    scope.launch {
                        val shareIntent = viewModel.createShareIntent(title)
                        context.startActivity(Intent.createChooser(shareIntent, "Share trip data"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun DetailStat(
    value: String,
    unit: String?,
    label: String,
    accent: Boolean = false,
) {
    val colors = PiDriveTheme.colors
    val valueColor = if (accent) colors.accent.base else colors.fg

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = PiDriveMonoFontFamily,
                    fontWeight = FontWeight.W500,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = valueColor,
            )
            if (unit != null) {
                Text(
                    text = " $unit",
                    style = TextStyle(fontFamily = PiDriveFontFamily, fontSize = 11.sp),
                    color = colors.fgMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(
            text = label,
            style = TextStyle(fontFamily = PiDriveFontFamily, fontSize = 10.sp),
            color = colors.fgMuted,
        )
    }
}

@Composable
private fun EventRow(event: DrivingEventEntity) {
    val colors = PiDriveTheme.colors
    // Use G-Force peak when available (sensor-fusion strategy), otherwise mph/s rate.
    val peakLabel = when (event.strategy) {
        DetectionStrategy.G_FORCE -> event.peakG?.let { "Peak: ${"%.2f".format(it)}g" }
        DetectionStrategy.ACCELERATION -> event.rateMphS?.let { "Peak: ${"%.1f".format(it)} mph/s" }
    }
    // Severe threshold: >0.5g for G-force or >12 mph/s for acceleration
    val isSevere = (event.peakG ?: 0f) > 0.5f || (event.rateMphS ?: 0f) > 12f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isSevere) Icons.Rounded.Warning else Icons.Rounded.DirectionsRun,
            contentDescription = null,
            tint = if (isSevere) colors.danger else colors.warn,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.type.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = TextStyle(
                    fontFamily = PiDriveFontFamily,
                    fontWeight = FontWeight.W500,
                    fontSize = 13.sp,
                ),
                color = colors.fg,
            )
            peakLabel?.let {
                Text(
                    text = it,
                    style = PiDriveTheme.typography.bodySmall,
                    color = colors.fgMuted,
                )
            }
        }
        Text(
            text = timeOnlyFmt.format(event.timestamp),
            style = TextStyle(fontFamily = PiDriveMonoFontFamily, fontSize = 11.sp),
            color = colors.fgMuted,
        )
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatDurationMs(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    val rem = minutes % 60L
    return if (hours > 0L) "${hours}h ${rem}m" else "${minutes}m"
}

private fun buildTripTitle(startTime: java.time.Instant): String {
    val hour = startTime.atZone(ZoneId.systemDefault()).hour
    return when {
        hour in 5..7   -> "Morning drive"
        hour in 8..10  -> "Morning commute"
        hour in 11..13 -> "Midday drive"
        hour in 14..16 -> "Afternoon drive"
        hour in 17..19 -> "Evening commute"
        hour in 20..23 -> "Evening drive"
        else           -> "Late night drive"
    }
}
