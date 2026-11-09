package com.example.bai1.rescue.routing.network

import com.example.bai1.rescue.routing.model.RoutingStatusResponse
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.sos.network.AccessTokenProvider
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

interface RoutingApiClient {
    suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse>
}

class OkHttpRoutingApiClient(
    private val baseUrl: String,
    private val tokenProvider: AccessTokenProvider,
    client: OkHttpClient = OkHttpClient(),
    private val transportPolicy: RoutingTransportPolicy = RoutingTransportPolicy(),
    private val limits: RoutingResourceLimits = RoutingResourceLimits(),
    private val elapsedMillis: () -> Long = RoutingTime::elapsedMillis
) : RoutingApiClient {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .authenticator(okhttp3.Authenticator.NONE).proxyAuthenticator(okhttp3.Authenticator.NONE)
        .addNetworkInterceptor { chain ->
            val expected = transportPolicy.url("${baseUrl.trimEnd('/')}/routing/current")
            require(chain.request().url == expected) { "API origin/path changed" }
            chain.proceed(chain.request())
        }.build()

    override suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val url = transportPolicy.url("${baseUrl.trimEnd('/')}/routing/current")
            require(url.query == null) { "API base URL query is forbidden" }
            val token = tokenProvider.getAccessToken()?.trim().orEmpty()
            if (token.isEmpty()) return@withContext failure(RoutingApiErrorKind.NO_TOKEN)
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
                .header("Accept", "application/json").header("Accept-Encoding", "identity").get().build()
            // Conservative lease origin: response transit/parse time consumes TTL rather than extending it.
            val started = elapsedMillis()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val kind = when (response.code) {
                        401 -> RoutingApiErrorKind.UNAUTHORIZED
                        403 -> RoutingApiErrorKind.FORBIDDEN
                        404 -> RoutingApiErrorKind.NOT_FOUND
                        in 300..499 -> RoutingApiErrorKind.BAD_REQUEST
                        in 500..599 -> RoutingApiErrorKind.SERVER
                        else -> RoutingApiErrorKind.UNKNOWN
                    }
                    return@withContext failure(kind, response.code)
                }
                RoutingTransportPolicy.content(response, true)
                val body = response.body ?: return@withContext failure(RoutingApiErrorKind.PARSE_ERROR)
                require(body.contentLength() <= limits.apiBytes) { "API body too large" }
                val text = RoutingBoundedInputStream(body.byteStream(), limits.apiBytes, true).use { input ->
                    val output = java.io.ByteArrayOutputStream(8192)
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
                coroutineContext.ensureActive()
                val parsed = try { RoutingJsonParser.parseRoutingStatus(text, started) }
                    catch (_: IllegalArgumentException) { return@withContext failure(RoutingApiErrorKind.PARSE_ERROR) }
                if (tokenProvider.getAccessToken()?.trim() != token) return@withContext failure(RoutingApiErrorKind.NO_TOKEN)
                // Server claims/ATTACHED are not production authority or a live-safe certificate.
                RoutingApiResult.Success(parsed.copy(strictRoutingReady = false, liveHazardReady = false,
                    canAttemptStrictRouting = false, overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES))
            }
        } catch (e: CancellationException) { throw e }
        catch (_: java.io.InterruptedIOException) { failure(RoutingApiErrorKind.TIMEOUT) }
        catch (_: RoutingLimitException) { failure(RoutingApiErrorKind.PARSE_ERROR) }
        catch (_: IllegalArgumentException) { failure(RoutingApiErrorKind.BAD_REQUEST) }
        catch (_: org.json.JSONException) { failure(RoutingApiErrorKind.PARSE_ERROR) }
        catch (_: IOException) { failure(RoutingApiErrorKind.NO_INTERNET) }
        catch (_: Exception) { failure(RoutingApiErrorKind.UNKNOWN) }
    }

    private fun failure(kind: RoutingApiErrorKind, code: Int? = null) = RoutingApiResult.Failure(
        RoutingApiError(kind, "Routing request rejected (${kind.name})", code))
}
