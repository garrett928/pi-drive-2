package ghart.space.pi_drive.shared.obd

import ghart.space.pi_drive.shared.data.OBDVehicleDataSource
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests against a live ELM327 emulator over TCP.
 *
 * These tests are **skipped** unless the `obd.test.port` system property is set.
 * When running locally or in CI with an emulator:
 *
 * ```bash
 * # Start emulator
 * python3 -m elm -n 35000 -s car &
 * # Run tests
 * ./gradlew :shared:test -Pobd.test.port=35000
 * ```
 *
 * The Gradle property `obd.test.port` is forwarded to the JVM system property
 * `obd.test.port` via the testOptions block in `shared/build.gradle.kts`.
 *
 * When no emulator is running (property absent), all tests return early with a
 * log message — they do NOT fail, so the standard unit-test suite always passes.
 */
class ELM327IntegrationTest {

    /**
     * Verifies that a [TcpTransport] can connect to the emulator, that
     * [InitializationSequence] completes successfully, and that at least one
     * PID is reported as supported by the emulator's car scenario.
     */
    @Test
    fun `connects to ELM327 emulator and completes initialization`() = runTest {
        val port = obdTestPort() ?: return@runTest

        val transport = TcpTransport(host = "localhost", port = port)
        transport.connect()
        assertTrue("TcpTransport must be connected", transport.isConnected.value)

        val steps = InitializationSequence(transport, stepTimeout = 5_000L).run().toList()
        val complete = steps.filterIsInstance<InitStep.Complete>().firstOrNull()
        assertNotNull("Initialization should reach Complete step", complete)

        val result = complete!!.result
        assertTrue(
            "Emulator should report supported PIDs; got ${result.supportedPids}",
            result.supportedPids.isNotEmpty()
        )

        transport.disconnect()
    }

    /**
     * Verifies that [OBDVehicleDataSource] delivers at least one populated snapshot
     * when connected to the ELM327 emulator's "car" scenario.
     */
    @Test
    fun `receives populated vehicle snapshot from ELM327 emulator`() = runTest {
        val port = obdTestPort() ?: return@runTest

        val transport = TcpTransport(host = "localhost", port = port)
        transport.connect()

        // Quick init to discover supported PIDs
        val steps = InitializationSequence(transport, stepTimeout = 5_000L).run().toList()
        val result = steps.filterIsInstance<InitStep.Complete>().first().result

        val dataSource = OBDVehicleDataSource(
            transport = transport,
            initialSupportedPids = result.supportedPids,
            coroutineScope = backgroundScope,
            adapterName = "ELM327-emulator",
            protocol = result.protocol ?: "Auto",
        )
        dataSource.startPolling()

        val snapshot: VehicleSnapshot = dataSource.snapshot
            .filter { it != VehicleSnapshot.EMPTY && it.speedKmh != null }
            .first()

        dataSource.stopPolling()
        transport.disconnect()

        assertNotNull("Speed must be populated", snapshot.speedKmh)
        assertNotNull("RPM must be populated", snapshot.rpm)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the configured OBD emulator port, or `null` when the property is absent.
     * A null result causes the calling test to skip via early return.
     */
    private fun obdTestPort(): Int? {
        val raw = System.getProperty(OBD_PORT_PROP)
        if (raw == null) {
            println("SKIP: $OBD_PORT_PROP system property not set — ELM327 emulator tests skipped")
            return null
        }
        return raw.toIntOrNull().also { port ->
            if (port == null) {
                println("SKIP: $OBD_PORT_PROP='$raw' is not a valid port number")
            }
        }
    }

    private companion object {
        /** JVM system property name forwarded from the Gradle project property `obd.test.port`. */
        const val OBD_PORT_PROP = "obd.test.port"
    }
}
