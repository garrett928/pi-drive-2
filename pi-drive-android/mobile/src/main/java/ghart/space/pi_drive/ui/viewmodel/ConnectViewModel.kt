package ghart.space.pi_drive.ui.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.obd.BluetoothTransport
import ghart.space.pi_drive.shared.obd.InitResult
import ghart.space.pi_drive.shared.obd.InitStep
import ghart.space.pi_drive.shared.obd.InitializationSequence
import ghart.space.pi_drive.shared.obd.MockTransport
import ghart.space.pi_drive.shared.obd.OBDTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

// ── Data models ───────────────────────────────────────────────────────────────

/** Visual state of a single initialization checklist item. */
enum class InitStepStatus { PENDING, IN_PROGRESS, SUCCESS, ERROR }

/**
 * One row in the initialization checklist.
 *
 * @param label        Human-readable step description.
 * @param status       Current visual state.
 * @param errorMessage Non-null only when [status] is [InitStepStatus.ERROR].
 */
data class InitStepItem(
    val label: String,
    val status: InitStepStatus,
    val errorMessage: String? = null,
)

/**
 * A Bluetooth device visible in the scan/bonded-device list.
 *
 * @param address      MAC address, e.g. "AA:BB:CC:DD:EE:FF". "DEMO" in demo mode.
 * @param name         Display name from `BluetoothDevice.getName()`.
 * @param signalBars   Mock signal strength (1–4). Bonded devices always show 3.
 * @param looksLikeObd True when the name contains a heuristic OBD adapter pattern.
 */
data class DiscoveredDevice(
    val address: String,
    val name: String,
    val signalBars: Int,
    val looksLikeObd: Boolean,
)

// ── ConnectCoordinator ────────────────────────────────────────────────────────

/**
 * Pure-Kotlin coordinator that manages the 6-step OBD initialization flow.
 *
 * Contains no Android dependencies so it is directly testable with [MockTransport].
 * [ConnectViewModel] owns one instance and delegates its state flows to the screens.
 *
 * The 6 steps mirror the connect-flow checklist:
 * 1. RFCOMM socket / TCP connect
 * 2. ATZ (adapter reset)
 * 3. ATE0/ATL0/ATS0/ATH0 (config)
 * 4. ATSP0 (protocol auto-detect)
 * 5. PID range scan
 * 6. VIN read
 */
class ConnectCoordinator {

    private val _initSteps = MutableStateFlow(buildInitialSteps())
    val initSteps: StateFlow<List<InitStepItem>> = _initSteps.asStateFlow()

    private val _initResult = MutableStateFlow<InitResult?>(null)
    val initResult: StateFlow<InitResult?> = _initResult.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

    /** Resets all step states and clears the previous result. */
    fun reset() {
        _initSteps.value = buildInitialSteps()
        _initResult.value = null
        _connectError.value = null
    }

    /**
     * Connects [transport] and runs the full [InitializationSequence], updating
     * [initSteps] as each step completes. Sets [initResult] on success.
     *
     * Must be called from a coroutine. This function suspends until the sequence
     * finishes or a connection error occurs.
     */
    suspend fun connect(transport: OBDTransport) {
        reset()

        // Step 0 — transport-level connect (RFCOMM or TCP)
        updateStep(0, InitStepStatus.IN_PROGRESS)
        try {
            transport.connect()
            updateStep(0, InitStepStatus.SUCCESS)
        } catch (e: IOException) {
            updateStep(0, InitStepStatus.ERROR, "Connection failed: ${e.message}")
            _connectError.value = e.message ?: "Connection failed"
            return
        }

        // Advance step 1 to in-progress before the init sequence starts emitting
        updateStep(1, InitStepStatus.IN_PROGRESS)

        InitializationSequence(transport).run().collect { step ->
            when (step) {
                is InitStep.AdapterReset -> {
                    val ok = step.success
                    updateStep(1,
                        if (ok) InitStepStatus.SUCCESS else InitStepStatus.ERROR,
                        if (!ok) "ATZ did not return ELM327" else null,
                    )
                    if (ok) updateStep(2, InitStepStatus.IN_PROGRESS)
                }
                is InitStep.ConfigApplied -> {
                    val ok = step.success
                    updateStep(2, if (ok) InitStepStatus.SUCCESS else InitStepStatus.ERROR)
                    if (ok) updateStep(3, InitStepStatus.IN_PROGRESS)
                }
                is InitStep.ProtocolSelected -> {
                    val ok = step.success
                    updateStep(3, if (ok) InitStepStatus.SUCCESS else InitStepStatus.ERROR)
                    if (ok) updateStep(4, InitStepStatus.IN_PROGRESS)
                }
                is InitStep.PidRangeScan -> {
                    // Multiple range scans; keep step 4 IN_PROGRESS until VIN starts
                    updateStep(4, InitStepStatus.IN_PROGRESS)
                }
                is InitStep.VinRead -> {
                    updateStep(4, InitStepStatus.SUCCESS)
                    updateStep(5, InitStepStatus.IN_PROGRESS)
                    updateStep(5, InitStepStatus.SUCCESS)
                }
                is InitStep.Complete -> {
                    _initResult.value = step.result
                }
            }
        }
    }

    private fun updateStep(index: Int, status: InitStepStatus, error: String? = null) {
        val current = _initSteps.value.toMutableList()
        if (index < current.size) {
            current[index] = current[index].copy(status = status, errorMessage = error)
            _initSteps.value = current
        }
    }
}

private fun buildInitialSteps(): List<InitStepItem> = listOf(
    InitStepItem("Connecting to adapter",      InitStepStatus.PENDING),
    InitStepItem("Resetting adapter (ATZ)",    InitStepStatus.PENDING),
    InitStepItem("Configuring adapter",        InitStepStatus.PENDING),
    InitStepItem("Auto-detecting protocol",    InitStepStatus.PENDING),
    InitStepItem("Scanning supported signals", InitStepStatus.PENDING),
    InitStepItem("Reading vehicle VIN",        InitStepStatus.PENDING),
)

// ── ConnectViewModel ──────────────────────────────────────────────────────────

/**
 * ViewModel for the 3-step Bluetooth connect flow.
 *
 * Owns a [ConnectCoordinator] that manages the initialization state machine.
 * In demo mode, shows a fake device and uses [MockTransport] so the connect
 * flow can be exercised without a real Bluetooth adapter.
 *
 * UI responsibilities:
 * - [ConnectScanScreen]: observe [devices], call [selectDevice] + [startInitialization]
 * - [ConnectPairScreen]: observe [initSteps] + [connectError], call [retryInitialization]
 * - [ConnectDoneScreen]: observe [initResult]
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val coordinator = ConnectCoordinator()

    val initSteps: StateFlow<List<InitStepItem>> = coordinator.initSteps
    val initResult: StateFlow<InitResult?> = coordinator.initResult
    val connectError: StateFlow<String?> = coordinator.connectError

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private var selectedAddress: String? = null

    init {
        if (AppConfig.isDemoMode) {
            _devices.value = listOf(
                DiscoveredDevice(
                    address = "DEMO",
                    name = "Demo OBD Adapter",
                    signalBars = 4,
                    looksLikeObd = true,
                )
            )
        }
    }

    /**
     * Loads bonded Bluetooth devices. No-op in demo mode (demo device is
     * already set in [init]). Call this after BLUETOOTH_CONNECT permission is granted.
     */
    @SuppressLint("MissingPermission")
    fun loadBondedDevices() {
        if (AppConfig.isDemoMode) return
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter ?: return
        _devices.value = (adapter.bondedDevices ?: emptySet()).map { device ->
            DiscoveredDevice(
                address = device.address,
                name = device.name ?: device.address,
                signalBars = 3,
                looksLikeObd = isObdName(device.name),
            )
        }
    }

    /**
     * Records which device the user tapped in the scan list.
     * Must be called before [startInitialization].
     */
    fun selectDevice(address: String) {
        selectedAddress = address
    }

    /**
     * Starts the connect + initialization flow for the device selected via [selectDevice].
     * No-op if no device has been selected.
     */
    fun startInitialization() {
        val address = selectedAddress ?: return
        viewModelScope.launch {
            val transport = createTransport(address) ?: run {
                Log.e("PiDrive", "ConnectViewModel: could not create transport for $address")
                return@launch
            }
            coordinator.connect(transport)
        }
    }

    /** Re-runs the initialization flow. Useful after a failure. */
    fun retryInitialization() = startInitialization()

    @SuppressLint("MissingPermission")
    private fun createTransport(deviceAddress: String): OBDTransport? {
        if (AppConfig.isDemoMode) return MockTransport()
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: return null
            val device = adapter.getRemoteDevice(deviceAddress)
            BluetoothTransport(device)
        } catch (e: Exception) {
            Log.e("PiDrive", "ConnectViewModel: failed to build BluetoothTransport: ${e.message}")
            null
        }
    }

    private fun isObdName(name: String?): Boolean {
        if (name == null) return false
        val up = name.uppercase()
        return "OBD" in up || "ELM" in up || "OBDLINK" in up || "VLINK" in up || "LINK" in up
    }
}
