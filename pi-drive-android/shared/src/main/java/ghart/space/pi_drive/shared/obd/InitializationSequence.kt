package ghart.space.pi_drive.shared.obd

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrates the ELM327 adapter initialization sequence over an [OBDTransport].
 *
 * The sequence:
 * 1. **ATZ** — full device reset; confirms adapter is present ("ELM327" in response).
 * 2. **ATE0** — echo off (prevents command echo in subsequent responses).
 * 3. **ATL0** — linefeed off (cleaner response parsing).
 * 4. **ATS0** — spaces off (compact hex responses).
 * 5. **ATH0** — headers off (strips address/PCI bytes from OBD frames).
 * 6. **ATSP0** — auto-detect OBD protocol.
 * 7. **ATDP** — query detected protocol name.
 * 8. **PID range scan** — query 0x00, then 0x20/0x40/0x60 if each range's boundary PID is set.
 * 9. **VIN query** — service 09, PID 02; parse and decode VIN.
 *
 * Each step is attempted with a configurable timeout. Failures are captured in
 * [InitResult.stepErrors] and do not abort the sequence — the sequence always completes
 * and returns whatever information it was able to gather.
 *
 * @param transport    The connected [OBDTransport] to send commands on. Assumed to be
 *                     already connected (i.e. [OBDTransport.connect] has been called).
 * @param stepTimeout  Per-step timeout in milliseconds. Defaults to 5 000 ms.
 */
class InitializationSequence(
    private val transport: OBDTransport,
    private val stepTimeout: Long = DEFAULT_STEP_TIMEOUT_MS,
) {

    companion object {
        const val DEFAULT_STEP_TIMEOUT_MS = 5_000L

        /** Logcat tag for the OBD data pipeline (init → poll → parse → decode → snapshot). */
        private const val TAG = "VehicleData"
    }

    /**
     * Runs the initialization sequence and emits [InitStep] progress events as each step
     * completes. The final emission is always [InitStep.Complete].
     *
     * Collect this flow in a coroutine. Example:
     * ```kotlin
     * initSeq.run().collect { step ->
     *     when (step) {
     *         is InitStep.AdapterReset -> updateChecklist(step)
     *         is InitStep.Complete     -> proceed(step.result)
     *         else                     -> {}
     *     }
     * }
     * ```
     */
    fun run(): Flow<InitStep> = flow {
        val errors = mutableMapOf<String, String>()
        var supportedPids = emptySet<Int>()
        var vin: String? = null
        var vehicleInfo: VehicleInfo? = null
        var protocol: String? = null

        Log.i(TAG, "InitSequence: starting (stepTimeout=${stepTimeout}ms)")

        // Step 1: ATZ — adapter reset
        val atzResponse = sendWithTimeout(OBDCommand.ATZ)
        val adapterReady = atzResponse != null && "ELM327" in atzResponse.uppercase()
        if (!adapterReady) errors["ATZ"] = atzResponse ?: "timeout"
        val adapterVersion = atzResponse?.lines()?.lastOrNull { "ELM327" in it.uppercase() }
        Log.i(TAG, "InitSequence: ATZ ready=$adapterReady version=$adapterVersion raw=${atzResponse?.toLogString()}")
        emit(InitStep.AdapterReset(success = adapterReady, adapterVersion = adapterVersion))

        // Steps 2–5: configuration AT commands
        val configCommands = listOf(OBDCommand.ATE0, OBDCommand.ATL0, OBDCommand.ATS0, OBDCommand.ATH0)
        val configOk = configCommands.all { cmd ->
            val resp = sendWithTimeout(cmd)
            val ok = resp?.trim()?.uppercase()?.let { "OK" in it || "ELM327" in it } ?: false
            if (!ok) errors[cmd.toRawString()] = resp ?: "timeout"
            Log.i(TAG, "InitSequence: ${cmd.toRawString()} ok=$ok raw=${resp?.toLogString()}")
            ok
        }
        emit(InitStep.ConfigApplied(success = configOk))

        // Step 6: ATSP0 — auto-detect protocol
        val atspResponse = sendWithTimeout(OBDCommand.ATSP(0))
        val protocolSelected = atspResponse?.trim()?.uppercase()?.let { "OK" in it } ?: false
        if (!protocolSelected) errors["ATSP0"] = atspResponse ?: "timeout"
        Log.i(TAG, "InitSequence: ATSP0 ok=$protocolSelected raw=${atspResponse?.toLogString()}")
        emit(InitStep.ProtocolSelected(success = protocolSelected))

        // Step 7: ATDP — query detected protocol
        val atdpResponse = sendWithTimeout(AtDP)
        protocol = atdpResponse?.trim()?.takeIf { it.isNotBlank() && it.uppercase() != "?" }
        Log.i(TAG, "InitSequence: ATDP protocol=$protocol raw=${atdpResponse?.toLogString()}")

        // Step 8: PID range scan
        val allPids = mutableSetOf<Int>()
        val rangesToQuery = listOf(0x00, 0x20, 0x40, 0x60)
        for (rangeBase in rangesToQuery) {
            val rangeHex = "0x%02X".format(rangeBase)
            val cmd = OBDCommand.PidRequest(service = 1, pid = rangeBase)
            val raw = sendWithTimeout(cmd)
            if (raw == null) {
                Log.w(TAG, "InitSequence: PID range $rangeHex query timed out — stopping scan")
                errors["PID_RANGE_$rangeHex"] = "timeout"
                break
            }
            val parsed = ResponseParser.parse(raw)
            if (parsed is OBDResponse.Success) {
                val rangeResult = PidSupport.decode(parsed)
                allPids.addAll(rangeResult)
                Log.i(TAG, "InitSequence: PID range $rangeHex found ${rangeResult.size} " +
                    "(${rangeResult.sorted().joinToString { "0x%02X".format(it) }}) raw=${raw.toLogString()}")
                emit(InitStep.PidRangeScan(rangeBase = rangeBase, foundCount = rangeResult.size))
                // Stop scanning if the next boundary PID is not present
                if (!PidSupport.shouldQueryNextRange(rangeBase, rangeResult)) break
            } else {
                // This is the most common reason live dials stay blank: the support scan
                // produced no Success frame, so the poll scheduler has no PIDs to request.
                Log.w(TAG, "InitSequence: PID range $rangeHex did NOT parse as a Success frame " +
                    "→ parsed=$parsed raw=${raw.toLogString()} (no PIDs added — dials will be blank)")
                errors["PID_RANGE_$rangeHex"] = parsed.toString()
                break
            }
        }
        supportedPids = allPids

        // Step 9: VIN query (service 09, PID 02)
        val vinRaw = sendWithTimeout(OBDCommand.PidRequest(service = 9, pid = 2))
        if (vinRaw != null) {
            vin = VinDecoder.parseVinResponse(vinRaw)
            vehicleInfo = vin?.let { VinDecoder.decodeVin(it) }
        } else {
            errors["VIN"] = "timeout"
        }
        Log.i(TAG, "InitSequence: VIN=${vin ?: "(none)"} info=${vehicleInfo}")
        emit(InitStep.VinRead(vin = vin, vehicleInfo = vehicleInfo))

        // Final summary — the single most useful line for the empty-dials investigation.
        Log.i(TAG, "InitSequence: COMPLETE — supportedPids=${supportedPids.size} " +
            "[${supportedPids.sorted().joinToString { "0x%02X".format(it) }}] " +
            "protocol=$protocol errors=$errors")
        if (supportedPids.isEmpty()) {
            Log.e(TAG, "InitSequence: supportedPids is EMPTY — OBDPollScheduler will request " +
                "no PIDs and every live dial will be blank. Check the PID-range raw responses above.")
        }

        // Final result
        emit(
            InitStep.Complete(
                result = InitResult(
                    supportedPids = supportedPids,
                    vin = vin,
                    vehicleInfo = vehicleInfo,
                    protocol = protocol,
                    stepErrors = errors,
                )
            )
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun sendWithTimeout(command: OBDCommand): String? =
        withTimeoutOrNull(stepTimeout) {
            try {
                transport.send(command.toRawString())
            } catch (e: Exception) {
                null
            }
        }
}

/**
 * Renders a raw adapter response for a single log line: control characters are made visible
 * (`\r`, `\n`, `>` prompt) so framing problems are obvious, and the whole thing is quoted.
 */
internal fun String.toLogString(): String =
    "\"" + this
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .take(200) + "\""

/** Pseudo-command for ATDP (Describe Protocol). Not in the main OBDCommand sealed class since it is init-only. */
private object AtDP : OBDCommand() {
    override fun toRawString() = "ATDP"
}

// ── Data types ────────────────────────────────────────────────────────────────

/**
 * A progress event emitted by [InitializationSequence.run].
 *
 * Collect these to update a UI checklist. The final emission is always [Complete].
 */
sealed class InitStep {

    /** Result of the ATZ adapter reset command. */
    data class AdapterReset(
        val success: Boolean,
        val adapterVersion: String?,
    ) : InitStep()

    /** Result of applying ATE0, ATL0, ATS0, ATH0 configuration commands. */
    data class ConfigApplied(val success: Boolean) : InitStep()

    /** Result of the ATSP0 protocol auto-detect command. */
    data class ProtocolSelected(val success: Boolean) : InitStep()

    /**
     * Result of scanning one PID range (0x00, 0x20, 0x40, or 0x60).
     *
     * @param rangeBase  The base PID of the scanned range.
     * @param foundCount Number of supported PIDs found in this range.
     */
    data class PidRangeScan(val rangeBase: Int, val foundCount: Int) : InitStep()

    /**
     * Result of the VIN query.
     *
     * @param vin         17-character VIN string, or null if not available.
     * @param vehicleInfo Decoded vehicle info, or null if VIN was null.
     */
    data class VinRead(val vin: String?, val vehicleInfo: VehicleInfo?) : InitStep()

    /** Final event — always emitted last, carries the complete [InitResult]. */
    data class Complete(val result: InitResult) : InitStep()
}

/**
 * The final output of [InitializationSequence.run].
 *
 * @param supportedPids Set of OBD Mode 01 PIDs the ECU reports as available.
 * @param vin           17-character VIN, or null if not available.
 * @param vehicleInfo   Decoded VehicleInfo, or null if VIN was not read.
 * @param protocol      OBD protocol string from ATDP (e.g. "AUTO, ISO 15765-4 (CAN 11/500)").
 * @param stepErrors    Map of step names to error descriptions for steps that failed.
 *                      An empty map means all steps succeeded.
 */
data class InitResult(
    val supportedPids: Set<Int>,
    val vin: String?,
    val vehicleInfo: VehicleInfo?,
    val protocol: String?,
    val stepErrors: Map<String, String> = emptyMap(),
)
