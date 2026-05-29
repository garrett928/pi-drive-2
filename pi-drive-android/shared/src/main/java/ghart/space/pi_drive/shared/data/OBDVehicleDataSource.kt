package ghart.space.pi_drive.shared.data

import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.obd.OBDCommand
import ghart.space.pi_drive.shared.obd.OBDResponse
import ghart.space.pi_drive.shared.obd.OBDTransport
import ghart.space.pi_drive.shared.obd.PidDecoder
import ghart.space.pi_drive.shared.obd.ResponseParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Production [VehicleDataSource] that polls a live ELM327 adapter via [OBDTransport].
 *
 * The polling loop runs on [coroutineScope] and is structured as follows:
 * 1. [OBDPollScheduler] determines which PIDs to request each cycle.
 * 2. Each PID command is sent through [transport]; the raw response is parsed by
 *    [ResponseParser] and decoded by [PidDecoder].
 * 3. Decoded values are merged into a running [VehicleSnapshot] accumulator, which
 *    carries forward values that were not polled in the current cycle.
 * 4. The accumulator is emitted to [snapshot] after every cycle.
 * 5. Battery voltage (ATRV) is sampled every [BATTERY_POLL_INTERVAL_MS] milliseconds.
 * 6. Poll rate (Hz) is updated every [POLL_RATE_WINDOW_MS] and published in [connectionState].
 *
 * Transport errors on individual PID requests are caught and skipped; the loop never
 * crashes on a single bad response. Unrecoverable errors (e.g. socket closed) propagate
 * to [startPolling]'s try/catch and transition [connectionState] to [ConnectionState.Error].
 *
 * @param transport            Connected ELM327 transport. Must already be connected and
 *                             initialised (via [InitializationSequence]) before polling starts.
 * @param initialSupportedPids Set of Mode 01 PIDs reported as supported by the ECU.
 * @param coroutineScope       Scope that owns the polling coroutine.
 * @param adapterName          Display name for the adapter (e.g. Bluetooth device name).
 * @param protocol             OBD protocol string from ATDP (e.g. "ISO 15765-4 CAN").
 */
class OBDVehicleDataSource(
    private val transport: OBDTransport,
    initialSupportedPids: Set<Int>,
    private val coroutineScope: CoroutineScope,
    private val adapterName: String = "OBD Adapter",
    private val protocol: String = "Auto",
) : VehicleDataSource {

    companion object {
        /** How often to sample battery voltage via ATRV (ms). */
        const val BATTERY_POLL_INTERVAL_MS = 30_000L

        /** How often to recalculate and publish the poll rate (ms). */
        const val POLL_RATE_WINDOW_MS = 1_000L
    }

    private val _snapshot = MutableStateFlow(VehicleSnapshot.EMPTY)
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _supportedPids = MutableStateFlow(initialSupportedPids)
    override val supportedPids: StateFlow<Set<Int>> = _supportedPids.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * Starts the OBD polling loop on [coroutineScope].
     *
     * Idempotent — has no effect if polling is already active.
     * Transitions [connectionState] to [ConnectionState.Connecting] immediately, then
     * to [ConnectionState.Connected] once the first snapshot is emitted.
     */
    override fun startPolling() {
        if (pollingJob?.isActive == true) return
        _connectionState.value = ConnectionState.Connecting
        pollingJob = coroutineScope.launch {
            try {
                pollingLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection lost")
            }
        }
    }

    /**
     * Stops the polling loop and releases the polling coroutine.
     *
     * Idempotent — safe to call when already stopped. Transitions [connectionState]
     * to [ConnectionState.Disconnected].
     */
    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _connectionState.value = ConnectionState.Disconnected()
    }

    // ── Polling loop ──────────────────────────────────────────────────────

    private suspend fun pollingLoop() {
        val scheduler = OBDPollScheduler(_supportedPids.value)
        var current = VehicleSnapshot.EMPTY
        var cycleNumber = 0
        var lastBatteryMs = 0L
        var windowStartMs = System.currentTimeMillis()
        var cyclesInWindow = 0
        var connectedEmitted = false

        while (true) {
            val cycleStartMs = System.currentTimeMillis()
            val commands = scheduler.commandsForCycle(cycleNumber)

            for (command in commands) {
                try {
                    val raw = transport.send(command.toRawString())
                    val response = ResponseParser.parse(raw)
                    if (response is OBDResponse.Success) {
                        current = applyPidResponse(current, response)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Skip this PID; IO errors on individual PIDs do not abort the cycle.
                }
            }

            // Battery voltage: sampled via ATRV, not a standard PID
            if (cycleStartMs - lastBatteryMs >= BATTERY_POLL_INTERVAL_MS) {
                current = pollBattery(current)
                lastBatteryMs = cycleStartMs
            }

            current = current.copy(timestamp = Instant.now())
            _snapshot.value = current

            // Transition to Connected on first snapshot
            if (!connectedEmitted) {
                _connectionState.value = ConnectionState.Connected(adapterName, protocol, 0f)
                connectedEmitted = true
            }

            // Update poll rate every second
            cyclesInWindow++
            val elapsed = System.currentTimeMillis() - windowStartMs
            if (elapsed >= POLL_RATE_WINDOW_MS) {
                val hz = cyclesInWindow * 1000f / elapsed
                _connectionState.value = ConnectionState.Connected(adapterName, protocol, hz)
                cyclesInWindow = 0
                windowStartMs = System.currentTimeMillis()
            }

            cycleNumber++
        }
    }

    private suspend fun pollBattery(current: VehicleSnapshot): VehicleSnapshot {
        return try {
            val raw = transport.send(OBDCommand.ATRV.toRawString())
            val response = ResponseParser.parse(raw)
            if (response is OBDResponse.ATResponse) {
                val voltage = PidDecoder.decodeBatteryVoltage(response.text)
                if (voltage != null) current.copy(batteryVoltage = voltage) else current
            } else current
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            current
        }
    }

    // ── PID → VehicleSnapshot mapping ────────────────────────────────────

    private fun applyPidResponse(
        current: VehicleSnapshot,
        response: OBDResponse.Success,
    ): VehicleSnapshot = when (response.pid) {
        0x0D -> current.copy(speedKmh    = PidDecoder.decodeSpeed(response.dataBytes))
        0x0C -> current.copy(rpm         = PidDecoder.decodeRpm(response.dataBytes))
        0x05 -> current.copy(coolantTempC = PidDecoder.decodeCoolantTemp(response.dataBytes))
        0x0F -> current.copy(intakeAirTempC = PidDecoder.decodeIntakeTemp(response.dataBytes))
        0x10 -> current.copy(mafGps      = PidDecoder.decodeMaf(response.dataBytes))
        0x11 -> current.copy(throttlePct = PidDecoder.decodeThrottle(response.dataBytes))
        0x2F -> current.copy(fuelLevelPct = PidDecoder.decodeFuelLevel(response.dataBytes))
        0x5C -> current.copy(oilTempC    = PidDecoder.decodeOilTemp(response.dataBytes))
        0x5E -> current.copy(fuelRateLph  = PidDecoder.decodeFuelRate(response.dataBytes))
        else -> current
    }
}
