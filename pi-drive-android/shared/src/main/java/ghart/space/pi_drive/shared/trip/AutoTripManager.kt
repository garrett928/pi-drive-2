package ghart.space.pi_drive.shared.trip

import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.model.AutoTripState
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock

/**
 * Application-level coordinator for automatically-detected driving trips.
 *
 * Owns an [AutoTripDetector] that watches the OBD connection and accumulates trip data.
 * Exposes the current active trip as [currentTrip] and the full trip history as [tripHistory].
 *
 * Designed as a singleton injected into the ViewModel layer; its background coroutines
 * run for the lifetime of [scope] (typically the application scope).
 *
 * @param snapshots       Vehicle snapshot stream from [VehicleDataSource].
 * @param connectionState Connection state stream from [VehicleDataSource].
 * @param dao             Room DAO for persisting and querying [AutoTripEntity] records.
 * @param scope           Application-lifetime coroutine scope.
 * @param clock           Wall-clock source; injectable for testing.
 */
class AutoTripManager(
    snapshots: StateFlow<VehicleSnapshot>,
    connectionState: StateFlow<ConnectionState>,
    private val dao: AutoTripDao,
    scope: CoroutineScope,
    clock: Clock = Clock.systemUTC(),
) {

    private val detector = AutoTripDetector(
        snapshots = snapshots,
        connectionState = connectionState,
        dao = dao,
        scope = scope,
        clock = clock,
    )

    /**
     * Current auto-trip state.
     * Emits `null` when no trip is in progress (engine off, not yet connected, etc.).
     * Updated on every [VehicleSnapshot] while a trip is active.
     */
    val currentTrip: StateFlow<AutoTripState?> = detector.currentTrip

    /**
     * All recorded auto-trips, newest first.
     *
     * This is a hot [Flow] backed by Room's observable query; it emits a new list whenever
     * any trip row is inserted or updated, making it suitable for the trip history screen.
     */
    val tripHistory: Flow<List<AutoTripEntity>> = dao.getAll()
}
