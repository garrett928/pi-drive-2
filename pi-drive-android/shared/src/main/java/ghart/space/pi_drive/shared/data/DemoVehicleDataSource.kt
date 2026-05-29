package ghart.space.pi_drive.shared.data

import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Simulated vehicle data source that emits realistic [VehicleSnapshot] values
 * without a physical OBD adapter.
 *
 * Used during development, UI review, and automated testing. Activated at runtime
 * by passing `--ez demo_mode true` to the launch intent (see [MainActivity]).
 *
 * Each [DemoScenario] produces a distinct pattern:
 * - [DemoScenario.CRUISE]: steady 60 mph highway driving
 * - [DemoScenario.CITY]: 0–50 km/h stop-and-go
 * - [DemoScenario.HIGHWAY]: 65–80 mph sustained
 * - [DemoScenario.HARD_BRAKE]: periodic hard-braking events every ~20 s
 * - [DemoScenario.COLD_START]: coolant warms from 20 °C → 92 °C over 5 min
 * - [DemoScenario.LOW_FUEL]: fuel drains from 15 % → 5 % over 2.5 min
 * - [DemoScenario.OVERSPEED]: speed bursts above 75 mph every ~30 s
 * - [DemoScenario.DISCONNECT]: connection drops every 30 s
 *
 * @param scenario        The scenario to simulate. Defaults to [DemoScenario.CRUISE].
 * @param coroutineScope  Scope in which polling runs. Defaults to a new
 *                        [Dispatchers.Default]-backed scope; pass a [TestScope] in tests.
 * @param tickIntervalMs  Milliseconds between emitted snapshots. Defaults to 250 ms (4 Hz).
 */
class DemoVehicleDataSource(
    val scenario: DemoScenario = DemoScenario.CRUISE,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val tickIntervalMs: Long = 250L,
) : VehicleDataSource {

    private val _snapshot = MutableStateFlow(VehicleSnapshot.EMPTY)
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _supportedPids = MutableStateFlow<Set<Int>>(DEMO_SUPPORTED_PIDS)
    override val supportedPids: StateFlow<Set<Int>> = _supportedPids.asStateFlow()

    private var pollingJob: Job? = null

    override fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = coroutineScope.launch {
            _connectionState.value = ConnectionState.Connecting
            delay(500L) // simulate adapter handshake
            _connectionState.value = ConnectionState.Connected(
                adapterName = "Demo Mode (${scenario.name})",
                protocol = "Simulated",
                pollRateHz = 1000f / tickIntervalMs,
            )

            var tick = 0L
            while (isActive) {
                tickScenario(tick)
                delay(tickIntervalMs)
                tick++
            }
        }
    }

    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _connectionState.value = ConnectionState.Disconnected()
        _snapshot.value = VehicleSnapshot.EMPTY
    }

    // ── Scenario dispatcher ────────────────────────────────────────────────

    private fun tickScenario(tick: Long) {
        when (scenario) {
            DemoScenario.DISCONNECT -> tickDisconnect(tick)
            else                    -> _snapshot.value = buildSnapshot(tick)
        }
    }

    private fun tickDisconnect(tick: Long) {
        val phase = tick % 240L  // 60-second cycle at 4 Hz (240 ticks = 60 s)
        if (phase < 120) {
            // First 30 s: connected and emitting cruise data
            if (_connectionState.value is ConnectionState.Disconnected) {
                _connectionState.value = ConnectionState.Connected(
                    adapterName = "Demo Mode (DISCONNECT)",
                    protocol = "Simulated",
                    pollRateHz = 1000f / tickIntervalMs,
                )
            }
            _snapshot.value = buildCruise(tick)
        } else {
            // Next 30 s: simulate reconnect countdown (10-second retry interval)
            val disconnectedTick = (phase - 120).toInt()  // 0..119
            val retryIn = 10 - (disconnectedTick / 4) % 10  // counts 10..1 per 10s window
            _connectionState.value = ConnectionState.Disconnected(canRetry = true, retryIn = retryIn)
            _snapshot.value = VehicleSnapshot.EMPTY
        }
    }

    private fun buildSnapshot(tick: Long): VehicleSnapshot = when (scenario) {
        DemoScenario.CRUISE     -> buildCruise(tick)
        DemoScenario.CITY       -> buildCity(tick)
        DemoScenario.HIGHWAY    -> buildHighway(tick)
        DemoScenario.HARD_BRAKE -> buildHardBrake(tick)
        DemoScenario.COLD_START -> buildColdStart(tick)
        DemoScenario.LOW_FUEL   -> buildLowFuel(tick)
        DemoScenario.OVERSPEED  -> buildOverspeed(tick)
        DemoScenario.DISCONNECT -> buildCruise(tick)  // fallback (handled by tickDisconnect)
    }

    // ── Sine-wave helper ──────────────────────────────────────────────────

    /**
     * Returns `amplitude * sin(2π * tick / periodTicks + phase)`.
     *
     * @param tick        Current simulation tick (each tick = [tickIntervalMs] ms).
     * @param amplitude   Peak deviation from the centre value.
     * @param periodTicks Oscillation period expressed in ticks.
     * @param phase       Phase offset in radians.
     */
    private fun osc(tick: Long, amplitude: Double, periodTicks: Double, phase: Double = 0.0): Double =
        amplitude * sin(2 * PI * tick / periodTicks + phase)

    // ── Scenario builders ─────────────────────────────────────────────────

    /**
     * CRUISE: steady 60 mph with small sine-wave oscillations on all metrics.
     * Fuel level decreases slowly at ~0.1 % per 5 min.
     */
    private fun buildCruise(tick: Long): VehicleSnapshot {
        val speedKmh = (96 + osc(tick, 5.0, 80.0)).roundToInt()
        val rpm      = (2500 + osc(tick, 200.0, 60.0)).roundToInt()
        return VehicleSnapshot(
            speedKmh        = speedKmh,
            gpsSpeedMps     = speedKmh / 3.6f,
            rpm             = rpm,
            coolantTempC    = (91 + osc(tick, 3.0, 160.0)).roundToInt(),
            intakeAirTempC  = (35 + osc(tick, 5.0, 120.0)).roundToInt(),
            throttlePct     = (25 + osc(tick, 10.0, 80.0)).toFloat(),
            fuelLevelPct    = max(0.0, 70.0 - tick * 0.0008).toFloat(),
            oilTempC        = (100 + osc(tick, 5.0, 240.0)).roundToInt(),
            batteryVoltage  = (14.1 + osc(tick, 0.3, 40.0)).toFloat(),
            mafGps          = (5.5 + osc(tick, 1.0, 80.0)).toFloat(),
            fuelRateLph     = (3.5 + osc(tick, 0.5, 80.0)).toFloat(),
        )
    }

    /**
     * CITY: 0–50 km/h oscillation with idle periods; RPM tracks speed.
     */
    private fun buildCity(tick: Long): VehicleSnapshot {
        val rawSpeed = 25.0 + osc(tick, 30.0, 160.0) + osc(tick, 10.0, 48.0)
        val speedKmh = max(0, rawSpeed.roundToInt())
        val rpm      = (750 + speedKmh * 18).coerceIn(750, 2200)
        val moving   = speedKmh > 5
        return VehicleSnapshot(
            speedKmh        = speedKmh,
            gpsSpeedMps     = speedKmh / 3.6f,
            rpm             = rpm,
            coolantTempC    = (90 + osc(tick, 2.0, 160.0)).roundToInt(),
            intakeAirTempC  = (32 + osc(tick, 4.0, 120.0)).roundToInt(),
            throttlePct     = if (moving) (20 + osc(tick, 15.0, 80.0)).toFloat() else 0f,
            fuelLevelPct    = max(0.0, 55.0 - tick * 0.001).toFloat(),
            oilTempC        = (98 + osc(tick, 3.0, 200.0)).roundToInt(),
            batteryVoltage  = (14.0 + osc(tick, 0.2, 40.0)).toFloat(),
            mafGps          = if (moving) (3.5 + osc(tick, 1.5, 80.0)).toFloat() else 0.8f,
            fuelRateLph     = if (moving) (4.5 + osc(tick, 1.0, 80.0)).toFloat() else 0.5f,
        )
    }

    /**
     * HIGHWAY: 65–80 mph sustained with tight variance and low RPM.
     */
    private fun buildHighway(tick: Long): VehicleSnapshot {
        val speedKmh = (115 + osc(tick, 15.0, 120.0)).roundToInt()
        val rpm      = (2100 + osc(tick, 150.0, 80.0)).roundToInt()
        return VehicleSnapshot(
            speedKmh        = speedKmh,
            gpsSpeedMps     = speedKmh / 3.6f,
            rpm             = rpm,
            coolantTempC    = (92 + osc(tick, 2.0, 200.0)).roundToInt(),
            intakeAirTempC  = (38 + osc(tick, 3.0, 160.0)).roundToInt(),
            throttlePct     = (30 + osc(tick, 5.0, 80.0)).toFloat(),
            fuelLevelPct    = max(0.0, 80.0 - tick * 0.0005).toFloat(),
            oilTempC        = (102 + osc(tick, 3.0, 240.0)).roundToInt(),
            batteryVoltage  = (14.2 + osc(tick, 0.2, 40.0)).toFloat(),
            mafGps          = (8.0 + osc(tick, 1.0, 80.0)).toFloat(),
            fuelRateLph     = (5.0 + osc(tick, 0.5, 80.0)).toFloat(),
        )
    }

    /**
     * HARD_BRAKE: 20-second cycle — normal cruise for 15 s, then hard brake
     * (96 → 20 km/h in 2 s), then recovery (20 → 96 km/h in 3 s).
     *
     * accelRateMphS is set to ~−25 during braking and ~+15 during recovery.
     */
    private fun buildHardBrake(tick: Long): VehicleSnapshot {
        val cycleLen   = 80L  // 80 ticks = 20 s at 4 Hz
        val brakeStart = 60L  // ticks into cycle when braking begins
        val brakeLen   = 8L   // 2 s of braking
        val recovLen   = 12L  // 3 s of recovery

        val phase = tick % cycleLen

        val speedKmh: Int
        val throttle: Float
        val accelRate: Float

        when {
            phase < brakeStart -> {
                // Normal cruise
                speedKmh  = (96 + osc(tick, 3.0, 40.0)).roundToInt()
                throttle  = (25 + osc(tick, 8.0, 40.0)).toFloat()
                accelRate = osc(tick, 1.0, 40.0).toFloat()
            }
            phase < brakeStart + brakeLen -> {
                // Hard braking: 96 → 20 km/h
                val progress = (phase - brakeStart).toDouble() / brakeLen
                speedKmh  = (96 - progress * 76).roundToInt()
                throttle  = 2f
                accelRate = -25f
            }
            phase < brakeStart + brakeLen + recovLen -> {
                // Recovery: 20 → 96 km/h
                val progress = (phase - brakeStart - brakeLen).toDouble() / recovLen
                speedKmh  = (20 + progress * 76).roundToInt()
                throttle  = 70f
                accelRate = 15f
            }
            else -> {
                // Gap before next cycle
                speedKmh  = 96
                throttle  = 25f
                accelRate = 0f
            }
        }

        return VehicleSnapshot(
            speedKmh        = speedKmh,
            gpsSpeedMps     = speedKmh / 3.6f,
            rpm             = (speedKmh * 24 + 750).coerceIn(750, 4000),
            coolantTempC    = 91,
            intakeAirTempC  = 35,
            throttlePct     = throttle,
            fuelLevelPct    = 65f,
            oilTempC        = 100,
            batteryVoltage  = 14.1f,
            mafGps          = (speedKmh * 0.06f).coerceAtLeast(0.8f),
            fuelRateLph     = (speedKmh * 0.04f).coerceAtLeast(0.5f),
            accelRateMphS   = accelRate,
        )
    }

    /**
     * COLD_START: engine warms over 5 min.
     * - Coolant: 20 → 92 °C (linear over 1 200 ticks)
     * - RPM: 1 200 idle → 750 as engine warms (vehicle begins moving after tick 400)
     * - After full warmup: transitions to cruise behaviour
     */
    private fun buildColdStart(tick: Long): VehicleSnapshot {
        val warmupTicks = 1200L  // 5 min
        val warmupPct   = min(1.0, tick.toDouble() / warmupTicks)
        val coolant     = (20 + warmupPct * 72).roundToInt()  // 20 → 92 °C
        val idleRpm     = (1200 - warmupPct * 450).roundToInt()  // 1200 → 750

        val moving = tick > 400
        val speedKmh = if (!moving) 0 else {
            val driveTick = tick - 400
            (min(96.0, driveTick * 0.1) + osc(tick, 3.0, 80.0)).roundToInt()
        }
        val rpm = if (!moving) idleRpm else (idleRpm + speedKmh * 18).coerceIn(750, 3000)

        return VehicleSnapshot(
            speedKmh        = speedKmh,
            gpsSpeedMps     = speedKmh / 3.6f,
            rpm             = rpm,
            coolantTempC    = coolant,
            intakeAirTempC  = (15 + warmupPct * 20).roundToInt(),
            throttlePct     = if (!moving) 0f else (20 + osc(tick, 8.0, 80.0)).toFloat(),
            fuelLevelPct    = max(0.0, 80.0 - tick * 0.001).toFloat(),
            oilTempC        = (20 + warmupPct * 80).roundToInt(),
            batteryVoltage  = (13.8 + warmupPct * 0.3).toFloat(),
            mafGps          = (0.8 + warmupPct * 4.7 + osc(tick, 0.5, 60.0)).toFloat(),
            fuelRateLph     = (0.5 + warmupPct * 3.0).toFloat(),
        )
    }

    /**
     * LOW_FUEL: normal cruise but fuel drains from 15 % → 5 % over ~2.5 min.
     * Triggers low-fuel alert logic testing.
     */
    private fun buildLowFuel(tick: Long): VehicleSnapshot {
        val fuelPct = max(5.0, 15.0 - tick.toDouble() * 10.0 / 600.0)
        val base    = buildCruise(tick)
        return base.copy(fuelLevelPct = fuelPct.toFloat())
    }

    /**
     * OVERSPEED: normal cruise with a burst above 75 mph (121 km/h) every ~30 s.
     * The burst lasts ~5 s. Useful for testing speed-alert thresholds.
     */
    private fun buildOverspeed(tick: Long): VehicleSnapshot {
        val cycleLen  = 120L  // 30 s at 4 Hz
        val burstLen  = 20L   // 5 s
        val burstStart = 100L

        val phase = tick % cycleLen
        val speedKmh = if (phase >= burstStart) {
            // Burst: ramp up to 140 km/h and hold
            val progress = (phase - burstStart).toDouble() / burstLen
            (96 + progress * 44 + osc(tick, 3.0, 20.0)).roundToInt()
        } else {
            (96 + osc(tick, 5.0, 80.0)).roundToInt()
        }

        val base = buildCruise(tick)
        return base.copy(
            speedKmh    = speedKmh,
            gpsSpeedMps = speedKmh / 3.6f,
            rpm         = (speedKmh * 24 + 750).coerceIn(750, 4500),
            throttlePct = if (phase >= burstStart) 80f else base.throttlePct,
        )
    }

    // ── Constants ──────────────────────────────────────────────────────────

    companion object {
        /**
         * PID codes (decimal) reported as supported in all demo scenarios.
         * Mirrors the OBD PIDs that Pi Drive actually polls.
         */
        val DEMO_SUPPORTED_PIDS: Set<Int> = setOf(
            0x05, // Coolant temp
            0x0C, // RPM
            0x0D, // Speed
            0x0F, // Intake air temp
            0x10, // MAF
            0x11, // Throttle
            0x2F, // Fuel level
            0x42, // Battery voltage
            0x5C, // Oil temp
            0x5E, // Fuel rate
        )
    }
}
