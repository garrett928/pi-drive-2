package ghart.space.pi_drive.shared.telemetry

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "TelemetryUploader"

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * HTTP client for the Pi Drive telemetry server.
 *
 * All requests include `Content-Type: application/json`, `X-Device-Id: {deviceId}`, and
 * `Authorization: Bearer {apiKey}` (when an API key is configured).
 *
 * HTTPS is required — calls to an HTTP URL return [Result.failure] immediately without
 * making a network request.
 *
 * Timeouts: 10 s connect, 30 s read/write.
 *
 * @param serverUrl   Base URL of the telemetry server (must start with `https://`).
 * @param apiKey      Optional bearer token included in the `Authorization` header.
 * @param deviceId    Persistent per-device identifier sent in `X-Device-Id`.
 * @param client      OkHttpClient instance — injectable for testing.
 */
class TelemetryUploader(
    private val serverUrl: String,
    private val apiKey: String,
    private val deviceId: String,
    internal val client: OkHttpClient = defaultClient(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    /**
     * POSTs a pre-serialized JSON [payloadJson] string to `{serverUrl}/telemetry`.
     *
     * Used by [UploadWorker] to avoid a round-trip deserialize-then-reserialize cycle when
     * retrying payloads read from the [OfflineBuffer] (which already stores them as JSON).
     */
    suspend fun uploadRaw(payloadJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        validateHttps().onFailure { return@withContext Result.failure(it) }

        val body = payloadJson.toRequestBody(JSON_MEDIA)
        val request = buildRequest("$serverUrl/telemetry").post(body).build()

        execute(request) { response ->
            if (response.isSuccessful) {
                Log.d(TAG, "POST /telemetry (raw) → ${response.code}")
                Result.success(Unit)
            } else {
                val msg = "POST /telemetry (raw) failed: HTTP ${response.code}"
                Log.w(TAG, msg)
                Result.failure(IOException(msg))
            }
        }
    }

    /**
     * POSTs [payload] to `{serverUrl}/telemetry`.
     *
     * Returns [Result.success] on HTTP 200; [Result.failure] for HTTP errors, network
     * failures, or an HTTP (non-HTTPS) server URL.
     */
    suspend fun upload(payload: TelemetryPayload): Result<Unit> = withContext(Dispatchers.IO) {
        validateHttps().onFailure { return@withContext Result.failure(it) }

        val body = json.encodeToString(TelemetryPayload.serializer(), payload)
            .toRequestBody(JSON_MEDIA)

        val request = buildRequest("$serverUrl/telemetry")
            .post(body)
            .build()

        execute(request) { response ->
            if (response.isSuccessful) {
                Log.d(TAG, "POST /telemetry → ${response.code}")
                Result.success(Unit)
            } else {
                val msg = "POST /telemetry failed: HTTP ${response.code}"
                Log.w(TAG, msg)
                Result.failure(IOException(msg))
            }
        }
    }

    /**
     * GETs `{serverUrl}/health` to verify the server is reachable and the API key is valid.
     *
     * Returns [Result.success] on HTTP 200; [Result.failure] on 401, network error, or HTTP URL.
     */
    suspend fun checkHealth(): Result<Unit> = withContext(Dispatchers.IO) {
        validateHttps().onFailure { return@withContext Result.failure(it) }

        val request = buildRequest("$serverUrl/health").get().build()

        execute(request) { response ->
            if (response.isSuccessful) {
                Log.d(TAG, "GET /health → ${response.code}")
                Result.success(Unit)
            } else {
                val msg = "GET /health failed: HTTP ${response.code}"
                Log.w(TAG, msg)
                Result.failure(IOException(msg))
            }
        }
    }

    /**
     * GETs `{serverUrl}/telemetry/latest?vin={vin}` to retrieve the server's most recently
     * received timestamp for this vehicle.
     *
     * Returns [Result.success] with:
     * - A non-null ISO 8601 string when the server has telemetry for [vin].
     * - `null` when the server returns HTTP 404 (no telemetry yet for this VIN).
     *
     * Returns [Result.failure] on other HTTP errors, network failures, or an HTTP URL.
     */
    suspend fun getLatestTimestamp(vin: String): Result<String?> = withContext(Dispatchers.IO) {
        validateHttps().onFailure { return@withContext Result.failure(it) }

        val request = buildRequest("$serverUrl/telemetry/latest?vin=$vin").get().build()

        execute(request) { response ->
            when {
                response.code == 404 -> {
                    Log.d(TAG, "GET /telemetry/latest → 404 (no data for VIN)")
                    Result.success(null)
                }
                response.isSuccessful -> {
                    val bodyStr = response.body?.string() ?: ""
                    val timestamp = runCatching {
                        json.decodeFromString(JsonObject.serializer(), bodyStr)["latestTimestamp"]
                            ?.jsonPrimitive?.content
                    }.getOrNull()
                    Log.d(TAG, "GET /telemetry/latest → $timestamp")
                    Result.success(timestamp)
                }
                else -> {
                    val msg = "GET /telemetry/latest failed: HTTP ${response.code}"
                    Log.w(TAG, msg)
                    Result.failure(IOException(msg))
                }
            }
        }
    }

    private fun validateHttps(): Result<Unit> {
        if (!serverUrl.startsWith("https://")) {
            val msg = "Server URL must use HTTPS (got: $serverUrl)"
            Log.e(TAG, msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        return Result.success(Unit)
    }

    private fun buildRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("X-Device-Id", deviceId)
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }

    /** Executes [request] and passes the response to [block]. Closes the response body. */
    private inline fun <T> execute(request: Request, block: (okhttp3.Response) -> Result<T>): Result<T> =
        runCatching { client.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    response.use { block(it) }
                },
                onFailure = { e ->
                    Log.e(TAG, "Network error: ${e.message}", e)
                    Result.failure(e)
                }
            )

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
