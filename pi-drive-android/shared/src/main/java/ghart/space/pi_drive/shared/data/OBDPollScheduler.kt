package ghart.space.pi_drive.shared.data

import ghart.space.pi_drive.shared.obd.OBDCommand

/**
 * Determines which OBD PIDs to request on each polling cycle.
 *
 * PIDs are divided into three tiers by polling frequency:
 * - **High priority** (0x0D speed, 0x0C RPM): included every cycle. These drive
 *   acceleration detection and the featured metric, so latency matters.
 * - **Medium priority** (0x05 coolant, 0x11 throttle, 0x10 MAF): round-robined through
 *   the extra slot, appearing roughly every 3 cycles.
 * - **Low priority** (0x0F intake, 0x2F fuel, 0x5C oil, 0x5E fuel rate): occupy the same
 *   extra slot after the medium PIDs, appearing roughly every 7 cycles.
 *
 * Each call to [commandsForCycle] is purely deterministic: the same cycle number always
 * returns the same commands, making the scheduler easy to unit-test and reason about.
 *
 * PIDs absent from [supportedPids] are silently omitted — the scheduler never requests
 * a PID the adapter doesn't support.
 *
 * @param supportedPids The set of Mode 01 PIDs confirmed as available by the ECU
 *                      (from [InitializationSequence]).
 */
class OBDPollScheduler(private val supportedPids: Set<Int>) {

    companion object {
        /** Always-polled PIDs — speed and RPM drive immediate UI + detector updates. */
        val HIGH_PRIORITY_PIDS = listOf(0x0D, 0x0C)

        /** Polled in the round-robin extra slot; appear roughly every 3 cycles. */
        val MEDIUM_PRIORITY_PIDS = listOf(0x05, 0x11, 0x10)

        /** Polled in the round-robin extra slot after medium PIDs; appear roughly every 7 cycles. */
        val LOW_PRIORITY_PIDS = listOf(0x0F, 0x2F, 0x5C, 0x5E)

        /** Combined round-robin queue order: medium first, then low. */
        val ROUND_ROBIN_PIDS = MEDIUM_PRIORITY_PIDS + LOW_PRIORITY_PIDS
    }

    private val activeHighPids = HIGH_PRIORITY_PIDS.filter { it in supportedPids }
    private val activeRoundRobinPids = ROUND_ROBIN_PIDS.filter { it in supportedPids }

    /**
     * Returns the list of OBD commands to send on cycle [cycleNumber].
     *
     * Always includes all supported high-priority PIDs. Additionally includes one PID
     * from the round-robin queue, chosen by `cycleNumber mod queue size`. If no
     * round-robin PIDs are supported, the result contains only the high-priority PIDs.
     *
     * @param cycleNumber Zero-based cycle counter maintained by the caller.
     * @return Ordered list of commands: high-priority first, then the round-robin PID.
     */
    fun commandsForCycle(cycleNumber: Int): List<OBDCommand> {
        val commands = mutableListOf<OBDCommand>()

        activeHighPids.forEach { pid ->
            commands.add(OBDCommand.PidRequest(service = 1, pid = pid))
        }

        if (activeRoundRobinPids.isNotEmpty()) {
            val pid = activeRoundRobinPids[cycleNumber % activeRoundRobinPids.size]
            commands.add(OBDCommand.PidRequest(service = 1, pid = pid))
        }

        return commands
    }

    /** Returns how many distinct PIDs will be polled per full round-robin rotation. */
    fun totalActivePids(): Int = activeHighPids.size + activeRoundRobinPids.size
}
