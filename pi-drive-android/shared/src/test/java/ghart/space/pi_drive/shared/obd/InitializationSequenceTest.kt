package ghart.space.pi_drive.shared.obd

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [InitializationSequence] run against [MockTransport].
 *
 * All tests use [runTest] from kotlinx.coroutines.test for structured concurrency.
 * The MockTransport's default responses (ATZ → "ELM327 v2.2", AT cmds → "OK",
 * PID 0100 → "41 00 BE 1F A8 13", etc.) simulate a connected adapter.
 */
class InitializationSequenceTest {

    private lateinit var transport: MockTransport

    @Before
    fun setUp() {
        transport = MockTransport()
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    fun `run against MockTransport completes successfully`() = runTest {
        transport.connect()
        val steps = InitializationSequence(transport).run().toList()

        val complete = steps.filterIsInstance<InitStep.Complete>().firstOrNull()
        assertNotNull("Flow should end with Complete step", complete)
        val result = complete!!.result
        assertTrue("Supported PIDs should not be empty", result.supportedPids.isNotEmpty())
        assertTrue("No step errors expected", result.stepErrors.isEmpty())
    }

    @Test
    fun `run emits AdapterReset step with ELM327 version`() = runTest {
        transport.connect()
        val steps = InitializationSequence(transport).run().toList()

        val reset = steps.filterIsInstance<InitStep.AdapterReset>().first()
        assertTrue("AdapterReset should succeed", reset.success)
        assertNotNull("AdapterReset should include version", reset.adapterVersion)
        assertTrue(reset.adapterVersion!!.contains("ELM327", ignoreCase = true))
    }

    @Test
    fun `run emits ConfigApplied step`() = runTest {
        transport.connect()
        val steps = InitializationSequence(transport).run().toList()

        val config = steps.filterIsInstance<InitStep.ConfigApplied>().first()
        assertTrue("ConfigApplied should succeed", config.success)
    }

    @Test
    fun `run emits ProtocolSelected step`() = runTest {
        transport.connect()
        val steps = InitializationSequence(transport).run().toList()

        val proto = steps.filterIsInstance<InitStep.ProtocolSelected>().first()
        assertTrue("ProtocolSelected should succeed", proto.success)
    }

    @Test
    fun `run scans at least the first pid range`() = runTest {
        transport.connect()
        val steps = InitializationSequence(transport).run().toList()

        val scans = steps.filterIsInstance<InitStep.PidRangeScan>()
        assertTrue("At least one PID range scan expected", scans.isNotEmpty())
        val firstScan = scans.first()
        assertEquals("First scan should be range 0x00", 0x00, firstScan.rangeBase)
        assertTrue("First range should have some PIDs", firstScan.foundCount > 0)
    }

    @Test
    fun `run includes PID 0x0D and 0x0C in supported set`() = runTest {
        transport.connect()
        val result = completeResult()

        // MockTransport's default 0100 response (41 00 BE 1F A8 13) should include speed+RPM
        assertTrue("Speed PID 0x0D should be supported", 0x0D in result.supportedPids)
        assertTrue("RPM PID 0x0C should be supported", 0x0C in result.supportedPids)
    }

    @Test
    fun `run detects protocol from ATDP`() = runTest {
        transport.connect()
        val result = completeResult()

        // MockTransport returns "AUTO, ISO 15765-4 (CAN 11/500)" for ATDP
        assertNotNull("Protocol should be detected", result.protocol)
        assertTrue(result.protocol!!.contains("CAN", ignoreCase = true))
    }

    // ── VIN handling ──────────────────────────────────────────────────────

    @Test
    fun `run reads VIN when transport returns valid response`() = runTest {
        // Set up a valid VIN response for service 09 PID 02
        transport.setResponse("0902", "49 02 01 4A 46 31 56 41 31 45 36 36 47 39 33 36 32 30 34 35")
        transport.connect()
        val result = completeResult()

        assertEquals("JF1VA1E66G9362045", result.vin)
        assertNotNull("VehicleInfo should be decoded", result.vehicleInfo)
        assertEquals("Subaru", result.vehicleInfo!!.make)
        assertEquals(2016, result.vehicleInfo.year)
    }

    @Test
    fun `run handles NO DATA VIN response gracefully`() = runTest {
        // MockTransport default returns "NO DATA" for unrecognised commands
        transport.connect()
        val result = completeResult()

        assertNull("VIN should be null when not supported", result.vin)
        assertNull("VehicleInfo should be null when VIN is null", result.vehicleInfo)
    }

    @Test
    fun `run emits VinRead step`() = runTest {
        transport.connect()
        val steps = InitializationSequence(transport).run().toList()

        val vinStep = steps.filterIsInstance<InitStep.VinRead>().firstOrNull()
        assertNotNull("VinRead step should always be emitted", vinStep)
    }

    // ── Error resilience ──────────────────────────────────────────────────

    @Test
    fun `run continues when ATSP fails`() = runTest {
        // Override ATSP0 to return an error
        transport.setResponse("ATSP0", "?")
        transport.connect()

        val steps = InitializationSequence(transport).run().toList()
        val complete = steps.filterIsInstance<InitStep.Complete>().firstOrNull()

        assertNotNull("Sequence should complete even if ATSP fails", complete)
        val result = complete!!.result
        assertTrue("ATSP0 error should be recorded", "ATSP0" in result.stepErrors)
        // PID scan should still proceed
        assertTrue("Supported PIDs should still be collected", result.supportedPids.isNotEmpty())
    }

    @Test
    fun `run continues when ATZ does not return ELM327`() = runTest {
        transport.setResponse("ATZ", "ERROR")
        transport.connect()

        val steps = InitializationSequence(transport).run().toList()
        val complete = steps.filterIsInstance<InitStep.Complete>().firstOrNull()

        assertNotNull("Sequence should complete even if ATZ fails", complete)
        val result = complete!!.result
        assertTrue("ATZ error should be recorded", "ATZ" in result.stepErrors)
    }

    @Test
    fun `run completes and always emits Complete as last step`() = runTest {
        transport.connect()
        val steps = InitializationSequence(transport).run().toList()

        assertTrue("Last step should be Complete", steps.last() is InitStep.Complete)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private suspend fun completeResult(): InitResult {
        val steps = InitializationSequence(transport).run().toList()
        return steps.filterIsInstance<InitStep.Complete>().first().result
    }
}
