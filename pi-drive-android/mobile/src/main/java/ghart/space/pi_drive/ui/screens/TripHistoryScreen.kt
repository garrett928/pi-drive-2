package ghart.space.pi_drive.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.db.entity.SyncStatus
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.shared.ui.theme.PiDriveFontFamily
import ghart.space.pi_drive.shared.ui.theme.PiDriveMonoFontFamily
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.navigation.NavRoutes
import ghart.space.pi_drive.ui.viewmodel.TripGroup
import ghart.space.pi_drive.ui.viewmodel.TripHistoryViewModel
import ghart.space.pi_drive.ui.viewmodel.WeeklySummary
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Private formatting helpers ────────────────────────────────────────────────

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    val rem = minutes % 60L
    return if (hours > 0L) "${hours} h ${rem} min" else "${minutes} min"
}

private fun tripTitle(trip: AutoTripEntity): String {
    val hour = trip.startTime.atZone(ZoneId.systemDefault()).hour
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

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Trip history screen — weekly summary card + day-grouped reactive trip list.
 *
 * Navigates to [NavRoutes.TRIP_DETAIL] when a trip card is tapped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripHistoryScreen(navController: NavController) {
    val viewModel: TripHistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = PiDriveTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // ── Weekly summary card ───────────────────────────────────────────────
        uiState.weeklySummary?.let { summary ->
            item(key = "summary") {
                WeeklySummaryCard(summary = summary)
                Spacer(Modifier.height(14.dp))
            }
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (uiState.groups.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "You're ready to record your first trip.",
                        style = PiDriveTheme.typography.bodyMedium,
                        color = colors.fgMuted,
                    )
                }
            }
        }

        // ── Day-grouped trip list ─────────────────────────────────────────────
        for (group in uiState.groups) {
            stickyHeader(key = "header_${group.date}") {
                DayHeader(
                    label = group.dayLabel,
                    modifier = Modifier.background(colors.bg),
                )
            }
            item(key = "group_${group.date}") {
                TripGroupCard(
                    trips = group.trips,
                    onTripClick = { tripId ->
                        navController.navigate(NavRoutes.tripDetail(tripId))
                    },
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun WeeklySummaryCard(summary: WeeklySummary) {
    val colors = PiDriveTheme.colors

    PDCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "THIS WEEK",
                style = PiDriveTheme.typography.labelSmall,
                color = colors.fgMuted,
            )
            PDPill(
                text = "${summary.tripCount} trip${if (summary.tripCount != 1) "s" else ""}",
                style = PillStyle.ACCENT,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryStatColumn(
                value = "%.1f".format(summary.totalDistanceMi),
                unit = "mi",
                label = "Distance",
                accentValue = false,
            )
            SummaryStatColumn(
                value = formatDuration(summary.totalDurationMs),
                unit = null,
                label = "Drive time",
                accentValue = false,
            )
            SummaryStatColumn(
                value = summary.avgMpg?.let { "%.1f".format(it) } ?: "--",
                unit = "mpg",
                label = "Avg MPG",
                accentValue = true,
            )
            SummaryStatColumn(
                value = "${summary.hardBrakeCount}",
                unit = null,
                label = "Hard brakes",
                accentValue = false,
            )
        }
    }
}

@Composable
private fun SummaryStatColumn(
    value: String,
    unit: String?,
    label: String,
    accentValue: Boolean,
) {
    val colors = PiDriveTheme.colors
    val valueColor = if (accentValue) colors.accent.base else colors.fg

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
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = TextStyle(fontFamily = PiDriveFontFamily, fontSize = 10.sp),
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
private fun DayHeader(label: String, modifier: Modifier = Modifier) {
    val colors = PiDriveTheme.colors
    Text(
        text = label.uppercase(),
        style = PiDriveTheme.typography.labelSmall,
        color = colors.fgMuted,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun TripGroupCard(
    trips: List<AutoTripEntity>,
    onTripClick: (Long) -> Unit,
) {
    val colors = PiDriveTheme.colors

    PDCard(contentPadding = 0.dp) {
        trips.forEachIndexed { index, trip ->
            if (index > 0) {
                HorizontalDivider(color = colors.borderS, thickness = 1.dp)
            }
            TripRow(
                trip = trip,
                onClick = { onTripClick(trip.id) },
            )
        }
    }
}

@Composable
private fun TripRow(trip: AutoTripEntity, onClick: () -> Unit) {
    val colors = PiDriveTheme.colors
    val isLive = trip.endTime == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Route icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Route,
                contentDescription = null,
                tint = colors.accent.base,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title + stats
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tripTitle(trip),
                    style = TextStyle(
                        fontFamily = PiDriveFontFamily,
                        fontWeight = FontWeight.W500,
                        fontSize = 14.sp,
                    ),
                    color = colors.fg,
                )
                if (trip.eventCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    PDPill(
                        text = "⚠ ${trip.eventCount}",
                        style = PillStyle.QUEUED,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val monoSmall = TextStyle(
                    fontFamily = PiDriveMonoFontFamily,
                    fontSize = 11.sp,
                )
                Text(
                    text = timeFormatter.format(trip.startTime),
                    style = monoSmall,
                    color = colors.fgMuted,
                )
                Text("·", style = monoSmall, color = colors.fgDim)
                Text(
                    text = formatDuration(trip.durationMs),
                    style = monoSmall,
                    color = colors.fgMuted,
                )
                Text("·", style = monoSmall, color = colors.fgDim)
                Text(
                    text = "${"%.1f".format(trip.distanceMi)} mi",
                    style = monoSmall,
                    color = colors.fgMuted,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Max speed + sync status
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${trip.maxSpeedMph.toInt()}",
                    style = TextStyle(
                        fontFamily = PiDriveMonoFontFamily,
                        fontWeight = FontWeight.W500,
                        fontSize = 15.sp,
                    ),
                    color = colors.fg,
                )
                Text(
                    text = " mph",
                    style = TextStyle(fontFamily = PiDriveFontFamily, fontSize = 10.sp),
                    color = colors.fgMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            when {
                isLive -> PDPill(text = "LIVE", style = PillStyle.LIVE, showDot = true)
                trip.syncStatus == SyncStatus.PENDING ->
                    PDPill(text = "QUEUED", style = PillStyle.QUEUED, showDot = true)
                trip.syncStatus == SyncStatus.FAILED ->
                    PDPill(text = "FAILED", style = PillStyle.DANGER)
                else -> {
                    // SYNCED — show subtle cloud indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = "Synced",
                            tint = colors.fgDim,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "synced",
                            style = TextStyle(fontFamily = PiDriveFontFamily, fontSize = 10.sp),
                            color = colors.fgDim,
                        )
                    }
                }
            }
        }
    }
}
