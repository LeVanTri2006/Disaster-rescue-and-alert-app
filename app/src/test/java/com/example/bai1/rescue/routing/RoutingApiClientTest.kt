package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.network.OkHttpRoutingApiClient
import com.example.bai1.rescue.routing.network.RoutingApiErrorKind
import com.example.bai1.rescue.routing.network.RoutingApiResult
import com.example.bai1.sos.network.AccessTokenProvider
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class RoutingApiClientTest {

    private val sampleValidJson = """
    {
      "manifest": {
        "manifestVersion": "2.1.0",
        "generatedAt": "2026-11-04T12:24:50.392Z",
        "graphVersion": "dn_central_real_v1",
        "osmSnapshotTimestamp": "2026-11-03T21:24:23Z",
        "importerVersion": "osm-importer-v2.3-prototype",
        "vehicleProfile": "RESCUE_TRUCK_4X4",
        "boundingBoxType": "PRODUCTION",
        "bundle": {
          "fileName": "BaseRoadGraph.json.gz",
          "compressedSizeBytes": 1173443,
          "uncompressedSizeBytes": 33002111,
          "sha256": "9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b"
        },
        "activeHazardSnapshotVersion": null,
        "hazardSnapshotStatus": "NOT_INCLUDED",
        "hazardAssessmentPolicy": "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
        "isRestrictionStrictRoutingEligible": false,
        "restrictionStrictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS",
        "unsupportedRestrictionsSummary": {
          "totalCount": 16,
          "enforcementPolicy": "FAIL_CLOSED_IN_STRICT_MODE"
        },
        "canAttemptStrictRouting": false,
        "overallStrictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS",
        "edgeSafetySummary": {
          "totalEdges": 16651,
          "verifiedCompatibleEdges": 0,
          "unverifiedEdges": 16651,
          "blockedEdges": 0,
          "conflictedEdges": 0,
          "strictSafeEdgesPercent": 0.0,
          "blockedWays": 1425
        }
      },
      "download": {
        "url": "https://pub-r2.example.com/graphs/dn_central_real_v1/BaseRoadGraph.json.gz?signature=token_secret_123",
        "expiresIn": 900
      },
      "downloadAvailable": true,
      "strictRoutingReady": false,
      "liveHazardReady": false,
      "canAttemptStrictRouting": false,
      "overallStrictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS"
    }
    """.trimIndent()

    private class FakeTokenProvider(private val token: String?) : AccessTokenProvider {
        override fun getAccessToken(): String? = token
    }

    private fun buildMockClient(interceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @Test
    fun `successful call returns parsed routing status and transmits authorization bearer header`() = runTest {
        var capturedAuthHeader = ""
        var capturedAcceptHeader = ""
        var capturedUrl = ""

        val client = buildMockClient { chain ->
            val req = chain.request()
            capturedAuthHeader = req.header("Authorization").orEmpty()
            capturedAcceptHeader = req.header("Accept").orEmpty()
            capturedUrl = req.url.toString()

            Response.Builder().header("Content-Type", "application/json")
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleValidJson.toResponseBody())
                .build()
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("valid_rescuer_jwt_token"),
            client = client
        )

        val result = apiClient.getCurrentRouting()

        assertTrue(result is RoutingApiResult.Success)
        val data = (result as RoutingApiResult.Success).data
        assertEquals("dn_central_real_v1", data.manifest.graphVersion)
        assertEquals(1173443L, data.manifest.bundle.compressedSizeBytes)
        assertEquals(33002111L, data.manifest.bundle.uncompressedSizeBytes)
        assertEquals("9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b", data.manifest.bundle.sha256)
        assertTrue(data.downloadAvailable)
        assertFalse(data.strictRoutingReady)
        assertFalse(data.liveHazardReady)

        assertEquals("Bearer valid_rescuer_jwt_token", capturedAuthHeader)
        assertEquals("application/json", capturedAcceptHeader)
        assertEquals("http://10.0.2.2:3000/routing/current", capturedUrl)
    }

    @Test
    fun `http 401 unauthorized maps to UNAUTHORIZED error kind`() = runTest {
        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/json")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("{\"message\":\"Token expired\"}".toResponseBody())
                .build()
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("expired_token"),
            client = client
        )

        val result = apiClient.getCurrentRouting()
        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.UNAUTHORIZED, error.kind)
        assertEquals(401, error.httpCode)
        assertTrue(error.userFriendlyMessage().contains("đăng nhập lại"))
    }

    @Test
    fun `http 403 forbidden maps to FORBIDDEN error kind`() = runTest {
        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/json")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body("{\"message\":\"Forbidden resource\"}".toResponseBody())
                .build()
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("unapproved_rescuer_token"),
            client = client
        )

        val result = apiClient.getCurrentRouting()
        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.FORBIDDEN, error.kind)
        assertEquals(403, error.httpCode)
        assertTrue(error.userFriendlyMessage().contains("Cứu hộ viên"))
    }

    @Test
    fun `http 404 not found maps to NOT_FOUND error kind`() = runTest {
        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/json")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("{\"message\":\"Manifest not found\"}".toResponseBody())
                .build()
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("valid_token"),
            client = client
        )

        val result = apiClient.getCurrentRouting()
        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.NOT_FOUND, error.kind)
        assertEquals(404, error.httpCode)
    }

    @Test
    fun `http 500 server error maps to SERVER error kind`() = runTest {
        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/json")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("{\"message\":\"Database connection error\"}".toResponseBody())
                .build()
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("valid_token"),
            client = client
        )

        val result = apiClient.getCurrentRouting()
        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.SERVER, error.kind)
        assertEquals(500, error.httpCode)
    }

    @Test
    fun `missing token fails immediately with NO_TOKEN without calling network`() = runTest {
        var networkCalled = false
        val client = buildMockClient { chain ->
            networkCalled = true
            Response.Builder().header("Content-Type", "application/json")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleValidJson.toResponseBody())
                .build()
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("   "),
            client = client
        )

        val result = apiClient.getCurrentRouting()
        assertFalse("Network must not be called when token is missing", networkCalled)
        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.NO_TOKEN, error.kind)
    }

    @Test
    fun `timeout maps to TIMEOUT error kind`() = runTest {
        val client = buildMockClient {
            throw SocketTimeoutException("connect timed out")
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("valid_token"),
            client = client
        )

        val result = apiClient.getCurrentRouting()
        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.TIMEOUT, error.kind)
    }

    @Test
    fun `network io exception maps to NO_INTERNET error kind`() = runTest {
        val client = buildMockClient {
            throw IOException("Unable to resolve host")
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("valid_token"),
            client = client
        )

        val result = apiClient.getCurrentRouting()
        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.NO_INTERNET, error.kind)
    }

    @Test
    fun `malformed json response maps to PARSE_ERROR`() = runTest {
        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/json")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{ \"invalid\": true }".toResponseBody())
                .build()
        }

        val apiClient = OkHttpRoutingApiClient(
            baseUrl = "http://10.0.2.2:3000",
            tokenProvider = FakeTokenProvider("valid_token"),
            client = client
        )

        val result = apiClient.getCurrentRouting()
        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.PARSE_ERROR, error.kind)
    }
}

