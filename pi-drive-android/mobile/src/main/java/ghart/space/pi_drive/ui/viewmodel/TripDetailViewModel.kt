package ghart.space.pi_drive.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Intent
import ghart.space.pi_drive.CsvExporter
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full UI state for the trip detail screen.
 *
 * @param trip      The [AutoTripEntity] being viewed; null while loading or if not found.
 * @param events    All driving events recorded during this trip.
 * @param isLoading True until the initial data load completes.
 */
data class TripDetailUiState(
    val trip: AutoTripEntity? = null,
    val events: List<DrivingEventEntity> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * ViewModel for [ghart.space.pi_drive.ui.screens.TripDetailScreen].
 *
 * Loads the trip identified by the `tripId` navigation argument and its associated
 * driving events. The trip data is loaded once on creation — the detail screen does
 * not need to react to incremental updates the way the history list does.
 *
 * @param savedStateHandle Provides the `tripId` navigation argument.
 * @param autoTripDao      Used to look up the trip by its ID.
 * @param eventDao         Used to load the trip's driving events.
 */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val autoTripDao: AutoTripDao,
    private val eventDao: DrivingEventDao,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle["tripId"])

    private val _uiState = MutableStateFlow(TripDetailUiState())

    /** Trip detail state — switches from loading to loaded after the first DB read. */
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val trip = autoTripDao.getAll().first().firstOrNull { it.id == tripId }
            val events = if (trip != null) eventDao.getByTripId(tripId) else emptyList()
            _uiState.value = TripDetailUiState(trip = trip, events = events, isLoading = false)
        }
    }

    /**
     * Builds a share [Intent] containing a CSV file of all snapshots for this trip.
     *
     * Must be called from a coroutine (e.g. via [kotlinx.coroutines.CoroutineScope.launch])
     * because [CsvExporter.createShareIntent] performs a Room query on the calling thread.
     */
    suspend fun createShareIntent(tripTitle: String): Intent =
        csvExporter.createShareIntent(tripId, tripTitle)
}
