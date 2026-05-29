package ghart.space.pi_drive.shared.telemetry

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * Unit tests for [TelemetryUploader].
 *
 * Uses a mockk [OkHttpClient] to verify request URLs, headers, and body format without
 * making real network calls.
 */
class TelemetryUploaderTest {

    private val mockClient = mockk<OkHttpClient>()

    private fun makeUploader(
        serverUrl: String = "https://api.example.com",
        apiKey: String = "test-key",
        deviceId: String = "device-001",
    ) = TelemetryUploader(
        serverUrl = serverUrl,
        apiKey = apiKey,
        deviceId = deviceId,
        client = mockClient,
    )

    private fun mockResponse(
        request: Request,
        code: Int,
        body: String = """{"ok":true}""",
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(body.toResponseBody())
        .build()

    private fun stubCall(response: Response): Call {
        val call = mockk<Call>()
        every { call.execute() } returns response
        every { mockClient.newCall(any()) } returns call
        return call
    }

    private val samplePayload = TelemetryPayload(
        timestamp = "2026-05-24T22:15:30.123Z",
        deviceId = "device-001",
        vin = "1HGCM82633A123456",
        location = null,
        obd = OBDPayload(
            speedKmh = 105, rpm = 2400, coolantTempC = 92,
            intakeAirTempC = null, throttlePct = null, fuelLevelPct = null,
            oilTempC = null, mafGps = null, fuelRateLph = null, batteryVoltage = null,
        ),
        calculated = CalculatedPayload(fuelEconomyMpg = 28.5f, fuelEconomyKml = null),
        accelMps2 = null,
        events = emptyList(),
    )

    // ── upload() ──────────────────────────────────────────────────────────────

    @Test fun `upload POSTs to correct URL`() = runTest {
        val uploader = makeUploader()
        val capturedRequest = mutableListOf<Request>()
        val call = mockk<Call>()
        every { call.execute() } returns mockResponse(
            Request.Builder().url("https://api.example.com/telemetry").build(), 200
        )
        every { mockClient.newCall(capture(capturedRequest)) } returns call

        uploader.upload(samplePayload)

        assertEquals("https://api.example.com/telemetry", capturedRequest[0].url.toString())
        assertEquals("POST", capturedRequest[0].method)
    }

    @Test fun `upload includes Authorization and X-Device-Id headers`() = runTest {
        val uploader = makeUploader(apiKey = "secret-key", deviceId = "dev-123")
        val capturedRequest = mutableListOf<Request>()
        val call = mockk<Call>()
        every { call.execute() } returns mockResponse(
            Request.Builder().url("https://api.example.com/telemetry").build(), 200
        )
        every { mockClient.newCall(capture(capturedRequest)) } returns call

        uploader.upload(samplePayload)

        val req = capturedRequest[0]
        assertEquals("Bearer secret-key", req.header("Authorization"))
        assertEquals("dev-123", req.header("X-Device-Id"))
    }

    @Test fun `upload returns success on HTTP 200`() = runTest {
        val uploader = makeUploader()
        stubCall(mockResponse(Request.Builder().url("https://api.example.com/telemetry").build(), 200))

        val result = uploader.upload(samplePayload)
        assertTrue(result.isSuccess)
    }

    @Test fun `upload returns failure on HTTP 500`() = runTest {
        val uploader = makeUploader()
        stubCall(mockResponse(Request.Builder().url("https://api.example.com/telemetry").build(), 500))

        val result = uploader.upload(samplePayload)
        assertTrue(result.isFailure)
    }

    @Test fun `upload rejects HTTP url`() = runTest {
        val uploader = makeUploader(serverUrl = "http://insecure.example.com")

        val result = uploader.upload(samplePayload)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        // No network call should have been made
        verify(exactly = 0) { mockClient.newCall(any()) }
    }

    @Test fun `upload returns failure on network error`() = runTest {
        val uploader = makeUploader()
        val call = mockk<Call>()
        every { call.execute() } throws IOException("connection refused")
        every { mockClient.newCall(any()) } returns call

        val result = uploader.upload(samplePayload)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    // ── checkHealth() ─────────────────────────────────────────────────────────

    @Test fun `checkHealth GETs correct URL`() = runTest {
        val uploader = makeUploader()
        val capturedRequest = mutableListOf<Request>()
        val call = mockk<Call>()
        every { call.execute() } returns mockResponse(
            Request.Builder().url("https://api.example.com/health").build(), 200,
            """{"status":"ok"}""",
        )
        every { mockClient.newCall(capture(capturedRequest)) } returns call

        uploader.checkHealth()

        assertEquals("https://api.example.com/health", capturedRequest[0].url.toString())
        assertEquals("GET", capturedRequest[0].method)
    }

    @Test fun `checkHealth returns success on 200`() = runTest {
        val uploader = makeUploader()
        stubCall(mockResponse(
            Request.Builder().url("https://api.example.com/health").build(), 200,
            """{"status":"ok"}""",
        ))

        assertTrue(uploader.checkHealth().isSuccess)
    }

    @Test fun `checkHealth returns failure on 401`() = runTest {
        val uploader = makeUploader()
        stubCall(mockResponse(
            Request.Builder().url("https://api.example.com/health").build(), 401,
        ))

        assertTrue(uploader.checkHealth().isFailure)
    }

    // ── getLatestTimestamp() ──────────────────────────────────────────────────

    @Test fun `getLatestTimestamp GETs correct URL with VIN query param`() = runTest {
        val uploader = makeUploader()
        val capturedRequest = mutableListOf<Request>()
        val call = mockk<Call>()
        every { call.execute() } returns mockResponse(
            Request.Builder().url("https://api.example.com/telemetry/latest?vin=VIN123").build(), 200,
            """{"vin":"VIN123","latestTimestamp":"2026-05-24T22:15:30Z"}""",
        )
        every { mockClient.newCall(capture(capturedRequest)) } returns call

        uploader.getLatestTimestamp("VIN123")

        val url = capturedRequest[0].url.toString()
        assertTrue("URL should contain vin query param", url.contains("vin=VIN123"))
        assertTrue("URL should contain /telemetry/latest path", url.contains("/telemetry/latest"))
    }

    @Test fun `getLatestTimestamp returns timestamp string on 200`() = runTest {
        val uploader = makeUploader()
        stubCall(mockResponse(
            Request.Builder().url("https://api.example.com/telemetry/latest?vin=V").build(), 200,
            """{"vin":"V","latestTimestamp":"2026-05-24T22:15:30Z"}""",
        ))

        val result = uploader.getLatestTimestamp("V")
        assertTrue(result.isSuccess)
        assertEquals("2026-05-24T22:15:30Z", result.getOrThrow())
    }

    @Test fun `getLatestTimestamp returns null on 404`() = runTest {
        val uploader = makeUploader()
        stubCall(mockResponse(
            Request.Builder().url("https://api.example.com/telemetry/latest?vin=NEW").build(), 404,
        ))

        val result = uploader.getLatestTimestamp("NEW")
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }
}
