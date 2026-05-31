package ghart.space.pi_drive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.model.EventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Seven-day aggregate statistics shown in the summary card at the top of the trip history screen.
 *
 * @param tripCount        Number of trips completed in the last 7 days.
 * @param totalDistanceMi  Sum of all trip distances in miles.
 * @param totalDurationMs  Sum of all trip durations in milliseconds.
 * @param avgMpg           Average MPG across trips that have fuel data; null if none do.
 * @param hardBrakeCount   Number of HARD_BRAKE events recorded in the last 7 days.
 */
data class WeeklySummary(
    val tripCount: Int,
    val totalDistanceMi: Float,
    val totalDurationMs: Long,
    val avgMpg: Float?,
    val hardBrakeCount: Int,
)

/**
 * A day-grouping of trips for the day-header sections in the trip history list.
 *
 * @param dayLabel Human-readable label: "Today", "Yesterday", or "Mon · May 26".
 * @param date     The [LocalDate] this group represents — used as a stable list key.
 * @param trips    All completed and in-progress trips that started on [date].
 */
data class TripGroup(
    val dayLabel: String,
    val date: LocalDate,
    val trips: List<AutoTripEntity>,
)

/**
 * Full UI state for [TripHistoryScreen].
 *
 * @param weeklySummary Seven-day aggregate; null while loading or when no trips exist.
 * @param groups        Trips grouped by day, newest group first.
 */
data class TripHistoryUiState(
    val weeklySummary: WeeklySummary? = null,
    val groups: List<TripGroup> = emptyList(),
)

/**
 * ViewModel for the trip history screen.
 *
 * Subscribes reactively to [AutoTripDao.getAll] and re-groups / re-summarises whenever
 * a trip is inserted or updated (e.g. the active trip accumulates distance while driving).
 *
 * @param autoTripDao  Provides the reactive trip list.
 * @param eventDao     Counts hard-brake events for the weekly summary badge.
 */
@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val autoTripDao: AutoTripDao,
    private val eventDao: DrivingEventDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripHistoryUiState())

    /** Full UI state — emits whenever the trip list or event counts change. */
    val uiState: StateFlow<TripHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            autoTripDao.getAll().collectLatest { trips ->
                val groups = groupByDay(trips)
                val weeklySummary = computeWeeklySummary(trips)
                _uiState.value = TripHistoryUiState(
                    weeklySummary = weeklySummary,
                    groups = groups,
                )
            }
        }
    }

    private suspend fun computeWeeklySummary(trips: List<AutoTripEntity>): WeeklySummary? {
        val now = Instant.now()
        val weekAgo = now.minus(7, ChronoUnit.DAYS)
        val weekTrips = trips.filter { it.startTime >= weekAgo }
        if (weekTrips.isEmpty()) return null

        val totalDistanceMi = weekTrips.sumOf { it.distanceMi.toDouble() }.toFloat()
        val totalDurationMs = weekTrips.sumOf { it.durationMs }
        val tripsWithMpg = weekTrips.mapNotNull { it.avgMpg }
        val avgMpg = if (tripsWithMpg.isNotEmpty()) tripsWithMpg.average().toFloat() else null
        val hardBrakeCount = eventDao.countByTypeAndTimeRange(EventType.HARD_BRAKE, weekAgo, now)

        return WeeklySummary(
            tripCount = weekTrips.size,
            totalDistanceMi = totalDistanceMi,
            totalDurationMs = totalDurationMs,
            avgMpg = avgMpg,
            hardBrakeCount = hardBrakeCount,
        )
    }

    private fun groupByDay(trips: List<AutoTripEntity>): List<TripGroup> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val formatter = DateTimeFormatter.ofPattern("EEE · MMM d")

        return trips
            .groupBy { trip -> trip.startTime.atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayTrips) ->
                val label = when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> date.format(formatter)
                }
                TripGroup(dayLabel = label, date = date, trips = dayTrips)
            }
    }
}
