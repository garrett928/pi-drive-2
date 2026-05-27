package ghart.space.pi_drive.shared.data

import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of live vehicle telemetry data.
 *
 * All consumers — the phone dashboard, Android Auto screens, trip accumulators,
 * event detectors, and the telemetry uploader — bind to this single interface.
 * This ensures that swapping data sources (OBD vs. Demo vs. future GPS-only mode)
 * requires no changes to any consumer.
 *
 * The interface is reactive: callers observe [snapshot] and [connectionState] as
 * hot [StateFlow]s. They never pull data directly; the implementation pushes updates.
 *
 * Lifecycle:
 * 1. Inject or obtain a [VehicleDataSource] (via Hilt, from [DataModule]).
 * 2. Observe [connectionState] to show connection UI.
 * 3. Call [startPolling] when the dashboard is visible.
 * 4. Observe [snapshot] for live metric values.
 * 5. Call [stopPolling] when the dashboard is off-screen (e.g., onStop or navBack).
 *
 * Implementations: `OBDVehicleDataSource`, `DemoVehicleDataSource`.
 */
interface VehicleDataSource {

    /**
     * The most recent vehicle telemetry snapshot.
     *
     * Initial value is [VehicleSnapshot.EMPTY] (all fields null). After [startPolling]
     * is called, this updates at the polling rate (~4 Hz for OBD, 4 Hz for demo).
     */
    val snapshot: StateFlow<VehicleSnapshot>

    /**
     * Current state of the adapter/data connection.
     *
     * Initial value is [ConnectionState.Disconnected]. Transitions through
     * [ConnectionState.Connecting] → [ConnectionState.Connected] on success,
     * or → [ConnectionState.Error] on failure.
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Set of OBD PID codes (decimal) that the adapter confirmed as supported.
     *
     * Empty until after the first PID support bitmap poll completes (usually a few
     * seconds after [startPolling]). Mock and demo implementations return a fixed set.
     */
    val supportedPids: StateFlow<Set<Int>>

    /**
     * Begin emitting [snapshot] updates.
     *
     * For [OBDVehicleDataSource]: initiates the Bluetooth connection and starts
     * the OBD polling coroutine.
     * For [DemoVehicleDataSource]: starts the scenario simulation coroutine.
     *
     * Idempotent — calling when already polling has no effect.
     */
    fun startPolling()

    /**
     * Stop emitting [snapshot] updates and release resources.
     *
     * For [OBDVehicleDataSource]: disconnects the Bluetooth socket.
     * For [DemoVehicleDataSource]: cancels the simulation coroutine.
     *
     * Idempotent — calling when already stopped has no effect.
     */
    fun stopPolling()
}
