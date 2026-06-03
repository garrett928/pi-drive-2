package ghart.space.pi_drive.shared.obd

import android.util.Log
import ghart.space.pi_drive.shared.data.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Manages the full OBD adapter connection lifecycle: initial connect, monitoring, and
 * automatic reconnection after an unexpected disconnect.
 *
 * Reconnect behavior:
 * - On disconnect (detected via [onAdapterDisconnected] or transport's [OBDTransport.isConnected] flow):
 *   retries every [RETRY_INTERVAL_MS] milliseconds for up to [MAX_RETRY_DURATION_MS].
 * - While retrying: [connectionState] is [ConnectionState.Disconnected] with `canRetry=true`
 *   and a `retryIn` countdown in seconds.
 * - After exhausting retries: [connectionState] is [ConnectionState.Disconnected] with `canRetry=false`.
 * - A manual [reconnectNow] call resets the retry window.
 *
 * @param scope            Coroutine scope for reconnect and monitor jobs.
 * @param transportFactory Factory that creates a fresh [OBDTransport] for a given device address.
 *                         Called for every connection attempt, including retries.
 * @param clock            Time source used to measure the 5-minute retry window.
 *                         Override in tests to control elapsed time.
 */
class ConnectionManager(
    private val scope: CoroutineScope,
    private val transportFactory: suspend (address: String) -> OBDTransport,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        /** Delay between automatic reconnect attempts. */
        const val RETRY_INTERVAL_MS = 10_000L

        /** Maximum duration to keep retrying before giving up. */
        const val MAX_RETRY_DURATION_MS = 300_000L  // 5 minutes
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    /** Current adapter connection state, updated throughout the lifecycle. */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var deviceAddress: String? = null
    private var currentTransport: OBDTransport? = null
    private var reconnectJob: Job? = null
    private var monitorJob: Job? = null

    /**
     * The set of Mode 01 PIDs reported as supported by the currently connected vehicle's ECU.
     * Empty when not connected. Populated by [acceptReadyTransport] and [doConnect].
     */
    var activeSupportedPids: Set<Int> = emptySet()
        private set

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Initiates a fresh connection to [address], cancelling any in-progress reconnect loop.
     * On failure, starts the automatic reconnect loop.
     *
     * This is a suspending function; it returns after the initialization sequence completes
     * (successfully or with an error). Observe [connectionState] for live updates.
     */
    suspend fun connect(address: String) {
        deviceAddress = address
        reconnectJob?.cancel()
        val success = doConnect(address)
        if (!success) startReconnectLoop(startTime = clock())
    }

    /**
     * Triggers an immediate reconnect attempt, resetting the 5-minute retry window.
     * No-op if no device address has been recorded from a prior [connect] call.
     */
    fun reconnectNow() {
        val address = deviceAddress ?: return
        reconnectJob?.cancel()
        monitorJob?.cancel()
        reconnectJob = scope.launch {
            val success = doConnect(address)
            if (!success) startReconnectLoop(startTime = clock())
        }
    }

    /**
     * Called by [AdapterWatcher] when a Bluetooth ACL_DISCONNECTED broadcast is received
     * for the connected device's address.
     *
     * Cleans up the transport and starts the automatic reconnect loop.
     */
    fun onAdapterDisconnected() {
        Log.i("PiDrive", "ConnectionManager: adapter disconnected — starting reconnect loop")
        currentTransport?.let { t -> scope.launch { t.disconnect() } }
        currentTransport = null
        monitorJob?.cancel()
        startReconnectLoop(startTime = clock())
    }

    /**
     * Performs a clean disconnect, cancels all reconnect attempts, and resets
     * [connectionState] to [ConnectionState.Disconnected].
     */
    fun disconnect() {
        reconnectJob?.cancel()
        monitorJob?.cancel()
        val t = currentTransport
        currentTransport = null
        activeSupportedPids = emptySet()
        t?.let { scope.launch { it.disconnect() } }
        _connectionState.value = ConnectionState.Disconnected()
        Log.i("PiDrive", "ConnectionManager: disconnected cleanly")
    }

    /**
     * Accepts a transport that has already been connected and initialized by [ConnectViewModel],
     * skipping the initialization sequence. Immediately transitions [connectionState] to
     * [ConnectionState.Connected] and starts monitoring for disconnects.
     *
     * This is called after the user completes the Connect screen flow so the dashboard
     * banner and reconnect logic reflect the live Bluetooth connection.
     *
     * @param address   Bluetooth MAC address of the adapter.
     * @param transport An already-connected, ELM327-initialized [OBDTransport].
     * @param result    The [InitResult] produced by [InitializationSequence].
     */
    fun acceptReadyTransport(address: String, transport: OBDTransport, result: InitResult) {
        deviceAddress = address
        reconnectJob?.cancel()
        monitorJob?.cancel()
        currentTransport = transport
        activeSupportedPids = result.supportedPids
        _connectionState.value = ConnectionState.Connected(
            adapterName = address,
            protocol = result.protocol ?: "Unknown",
            pollRateHz = 0f,
        )
        Log.i("PiDrive", "ConnectionManager: accepted ready transport for $address, " +
            "protocol=${result.protocol}, pids=${result.supportedPids.size}")
        monitorJob = scope.launch {
            transport.isConnected.collect { connected ->
                if (!connected && _connectionState.value is ConnectionState.Connected) {
                    Log.i("PiDrive", "ConnectionManager: transport dropped — starting reconnect loop")
                    currentTransport = null
                    activeSupportedPids = emptySet()
                    startReconnectLoop(startTime = clock())
                }
            }
        }
    }

    /** Returns the currently active [OBDTransport], or null if not connected. */
    fun getActiveTransport(): OBDTransport? = currentTransport

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Attempts a single connect + initialization. Returns true on success.
     * Callers are responsible for starting the reconnect loop on failure.
     */
    private suspend fun doConnect(address: String): Boolean {
        _connectionState.value = ConnectionState.Connecting
        monitorJob?.cancel()
        return try {
            val transport = transportFactory(address)
            currentTransport = transport
            transport.connect()

            // Run the full ELM327 initialization sequence
            var initResult: InitResult? = null
            InitializationSequence(transport).run().collect { step ->
                if (step is InitStep.Complete) initResult = step.result
            }
            val result = initResult ?: return false

            activeSupportedPids = result.supportedPids
            _connectionState.value = ConnectionState.Connected(
                adapterName = address,
                protocol = result.protocol ?: "Unknown",
                pollRateHz = 0f,
            )
            Log.i("PiDrive", "ConnectionManager: connected to $address, " +
                "protocol=${result.protocol}, pids=${result.supportedPids.size}")

            // Monitor the transport's live isConnected signal
            monitorJob = scope.launch {
                transport.isConnected.collect { connected ->
                    if (!connected && _connectionState.value is ConnectionState.Connected) {
                        Log.i("PiDrive", "ConnectionManager: transport dropped — starting reconnect loop")
                        currentTransport = null
                        startReconnectLoop(startTime = clock())
                    }
                }
            }
            true
        } catch (e: IOException) {
            Log.w("PiDrive", "ConnectionManager: connect failed — ${e.message}")
            currentTransport = null
            false
        }
    }

    /** Launches the reconnect loop coroutine, replacing any existing one. */
    private fun startReconnectLoop(startTime: Long) {
        val address = deviceAddress ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch { runReconnectLoop(address, startTime) }
    }

    /**
     * Countdown-and-retry loop.
     *
     * Counts down [RETRY_INTERVAL_MS] seconds per attempt. After [MAX_RETRY_DURATION_MS]
     * total elapsed time, stops and sets [connectionState] to non-retryable Disconnected.
     */
    private suspend fun runReconnectLoop(address: String, startTime: Long) {
        val intervalSec = (RETRY_INTERVAL_MS / 1000).toInt()
        while (currentCoroutineContext().isActive) {
            val elapsed = clock() - startTime
            if (elapsed >= MAX_RETRY_DURATION_MS) {
                _connectionState.value = ConnectionState.Disconnected(canRetry = false)
                Log.i("PiDrive", "ConnectionManager: gave up reconnecting after 5 min")
                return
            }

            // Emit countdown
            for (i in intervalSec downTo 1) {
                if (!currentCoroutineContext().isActive) return
                _connectionState.value = ConnectionState.Disconnected(canRetry = true, retryIn = i)
                delay(1_000)
            }

            // Attempt reconnect
            Log.i("PiDrive", "ConnectionManager: attempting reconnect to $address")
            val success = doConnect(address)
            if (success) return
        }
    }
}
