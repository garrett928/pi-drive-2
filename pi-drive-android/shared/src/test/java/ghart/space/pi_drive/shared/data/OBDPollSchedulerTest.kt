package ghart.space.pi_drive.shared.data

import ghart.space.pi_drive.shared.obd.OBDCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OBDPollSchedulerTest {

    /** Full set of supported PIDs — all high + medium + low. */
    private val allPids = setOf(0x0D, 0x0C, 0x05, 0x11, 0x10, 0x0F, 0x2F, 0x5C, 0x5E)

    private fun pidOf(command: OBDCommand): Int =
        (command as OBDCommand.PidRequest).pid

    // ── High-priority PIDs appear every cycle ────────────────────────────

    @Test
    fun `every cycle includes speed and RPM`() {
        val scheduler = OBDPollScheduler(allPids)
        for (cycle in 0..9) {
            val pids = scheduler.commandsForCycle(cycle).map { pidOf(it) }
            assertTrue("Cycle $cycle: speed (0x0D) missing", 0x0D in pids)
            assertTrue("Cycle $cycle: RPM (0x0C) missing", 0x0C in pids)
        }
    }

    // ── Round-robin order ─────────────────────────────────────────────────

    @Test
    fun `cycle 0 includes coolant as first round-robin pid`() {
        val scheduler = OBDPollScheduler(allPids)
        val pids = scheduler.commandsForCycle(0).map { pidOf(it) }
        assertTrue("Coolant (0x05) should be the first round-robin PID", 0x05 in pids)
    }

    @Test
    fun `cycle 1 includes throttle as second round-robin pid`() {
        val scheduler = OBDPollScheduler(allPids)
        val pids = scheduler.commandsForCycle(1).map { pidOf(it) }
        assertTrue("Throttle (0x11) should be the second round-robin PID", 0x11 in pids)
    }

    @Test
    fun `cycle 2 includes MAF as third round-robin pid`() {
        val scheduler = OBDPollScheduler(allPids)
        val pids = scheduler.commandsForCycle(2).map { pidOf(it) }
        assertTrue("MAF (0x10) should be the third round-robin PID", 0x10 in pids)
    }

    @Test
    fun `cycle 3 includes intake as fourth round-robin pid`() {
        val scheduler = OBDPollScheduler(allPids)
        val pids = scheduler.commandsForCycle(3).map { pidOf(it) }
        assertTrue("Intake (0x0F) should be the fourth round-robin PID", 0x0F in pids)
    }

    @Test
    fun `cycle 4 includes fuel level as fifth round-robin pid`() {
        val scheduler = OBDPollScheduler(allPids)
        val pids = scheduler.commandsForCycle(4).map { pidOf(it) }
        assertTrue("Fuel level (0x2F) should be the fifth round-robin PID", 0x2F in pids)
    }

    @Test
    fun `round-robin wraps back to coolant after full rotation`() {
        val scheduler = OBDPollScheduler(allPids)
        val rotationSize = OBDPollScheduler.ROUND_ROBIN_PIDS.filter { it in allPids }.size
        // After one full rotation, cycle `rotationSize` should again produce coolant
        val pids = scheduler.commandsForCycle(rotationSize).map { pidOf(it) }
        assertTrue("Round-robin should wrap back to coolant (0x05)", 0x05 in pids)
    }

    // ── Supported PID filtering ───────────────────────────────────────────

    @Test
    fun `only speed and RPM when no round-robin pids are supported`() {
        val scheduler = OBDPollScheduler(setOf(0x0D, 0x0C))
        val pids = scheduler.commandsForCycle(0).map { pidOf(it) }
        assertEquals(listOf(0x0D, 0x0C), pids)
    }

    @Test
    fun `speed omitted when not in supported set`() {
        val scheduler = OBDPollScheduler(setOf(0x0C, 0x05))  // no speed
        val pids = scheduler.commandsForCycle(0).map { pidOf(it) }
        assertFalse("Speed should be omitted when not supported", 0x0D in pids)
        assertTrue("RPM should still be present", 0x0C in pids)
    }

    @Test
    fun `coolant skipped in round-robin when not supported`() {
        val noCoolantPids = allPids - 0x05
        val scheduler = OBDPollScheduler(noCoolantPids)
        // cycle 0 would normally be coolant, but since it's not supported it should be throttle
        val pids = scheduler.commandsForCycle(0).map { pidOf(it) }
        assertFalse("Coolant (0x05) should not appear when not supported", 0x05 in pids)
        assertTrue("Throttle (0x11) should be the first round-robin PID instead", 0x11 in pids)
    }

    @Test
    fun `empty supported pids returns empty list`() {
        val scheduler = OBDPollScheduler(emptySet())
        assertTrue(scheduler.commandsForCycle(0).isEmpty())
    }

    // ── Command format ────────────────────────────────────────────────────

    @Test
    fun `commands are Mode 01 PidRequest instances`() {
        val scheduler = OBDPollScheduler(allPids)
        val commands = scheduler.commandsForCycle(0)
        commands.forEach { command ->
            assertTrue("All commands should be PidRequest", command is OBDCommand.PidRequest)
            assertEquals("All commands should use service 1", 1, (command as OBDCommand.PidRequest).service)
        }
    }

    @Test
    fun `speed pid wire format is 010D`() {
        val scheduler = OBDPollScheduler(setOf(0x0D))
        val command = scheduler.commandsForCycle(0).first()
        assertEquals("010D", command.toRawString())
    }

    // ── Determinism ───────────────────────────────────────────────────────

    @Test
    fun `same cycle number always returns same commands`() {
        val scheduler = OBDPollScheduler(allPids)
        val first = scheduler.commandsForCycle(3).map { it.toRawString() }
        val second = scheduler.commandsForCycle(3).map { it.toRawString() }
        assertEquals(first, second)
    }

    // ── totalActivePids ───────────────────────────────────────────────────

    @Test
    fun `totalActivePids includes high and round-robin counts`() {
        val scheduler = OBDPollScheduler(allPids)
        // 2 high + 7 round-robin = 9
        assertEquals(9, scheduler.totalActivePids())
    }

    @Test
    fun `totalActivePids with only high pids`() {
        val scheduler = OBDPollScheduler(setOf(0x0D, 0x0C))
        assertEquals(2, scheduler.totalActivePids())
    }
}
