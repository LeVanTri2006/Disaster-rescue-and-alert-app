package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.network.*
import com.example.bai1.rescue.routing.download.*
import com.example.bai1.rescue.routing.model.*
import com.example.bai1.rescue.routing.repository.DefaultRoutingRepository
import com.example.bai1.rescue.routing.loader.*
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import com.example.bai1.sos.network.AccessTokenProvider
import com.google.gson.Gson
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.*
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class RoutingNetworkSecurityTest {
    private lateinit var root: File
    private var now = 1000L
    private val token = "fixture-jwt-never-log"
    private val signed = "https://download.invalid/g.gz?X-Amz-Signature=fixture-secret-signature"
    private val tokens = object : AccessTokenProvider { override fun getAccessToken() = token }
    @Before fun setup() { root = Files.createTempDirectory("p5net-owned-").toFile(); now = 1000 }
    @After fun cleanup() { root.deleteRecursively() }
    private fun graph(padding: String = "0") = """{
      "contractVersion":"2.1.0","graphVersion":"net_v1","vehicleProfile":"RESCUE_TRUCK_4X4",
      "nodes":[{"id":"a","lat":0,"lon":0},{"id":"b","lat":0,"lon":0.001}],
      "edges":[{"id":"ab","fromNodeId":"a","toNodeId":"b","lengthMeters":111,
       "geometry":[{"lat":0,"lon":0},{"lat":0,"lon":0.001}],"compatibilityStatus":"UNVERIFIED"}],
      "padding":$padding,"turnRestrictions":[]
    }""".trimIndent()
    private fun gzip(text: String) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun status(text: String = graph(), bytes: ByteArray = gzip(text), ttl: Long = 900, stamp: Long = now): RoutingStatusResponse {
        val manifest = RoutingManifest(manifestVersion = "2.1.0", generatedAt = "2000-01-01T00:00:00Z", graphVersion = "net_v1",
            osmSnapshotTimestamp = "2000-01-01T00:00:00Z", importerVersion = "fixture", vehicleProfile = "RESCUE_TRUCK_4X4", boundingBoxType = "FIXTURE",
            bundle = RoutingBundleInfo("BaseRoadGraph.json.gz", bytes.size.toLong(), text.toByteArray().size.toLong(), sha(bytes)),
            hazardSnapshotStatus = "ATTACHED", activeHazardSnapshotVersion = "unverifiable", hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            isRestrictionStrictRoutingEligible = false, restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(0), canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES, edgeSafetySummary = EdgeSafetySummary(1, 0, 1, 0, 0, 0.0, 0))
        return RoutingStatusResponse(manifest, GraphDownloadInfo(signed, ttl, stamp), true, false, false, false,
            StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES)
    }
    private fun json(value: RoutingStatusResponse = status()) = Gson().toJson(value)
    private class Wire(var bytes: ByteArray, var mime: String = "application/gzip", var code: Int = 200) {
        var calls = 0
        var authorization: String? = null
        var acceptEncoding: String? = null
        var encoding: String? = null
        var length: String? = null
        var chunked = false
        var unknownLength = false
        var exception: IOException? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++; authorization = chain.request().header("Authorization"); acceptEncoding = chain.request().header("Accept-Encoding")
            exception?.let { throw it }
            val body = if (!unknownLength) bytes.toResponseBody() else object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = -1L
                override fun source() = Buffer().write(bytes)
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .header("Content-Type", mime).apply {
                    encoding?.let { header("Content-Encoding", it) }
                    length?.let { header("Content-Length", it) }
                    if (chunked) header("Transfer-Encoding", "chunked")
                    if (code in 300..399) header("Location", "http://unsafe.invalid/stolen")
                }.body(body).build()
        }.build()
    }
    private fun api(wire: Wire, base: String = "https://api.invalid", debug: Boolean = false, limits: RoutingResourceLimits = RoutingResourceLimits()) =
        OkHttpRoutingApiClient(base, tokens, wire.client, RoutingTransportPolicy(debug), limits) { now }
    private fun download(wire: Wire, limits: RoutingResourceLimits = RoutingResourceLimits()) =
        OkHttpGraphDownloader(root, wire.client, RoutingTransportPolicy(false), limits, { now })
    private fun success(result: GraphDownloadResult): GraphDownloadResult.Success {
        assertTrue("Expected validated success: $result", result is GraphDownloadResult.Success)
        return result as GraphDownloadResult.Success
    }
    private fun rejected(result: GraphDownloadResult) = result as GraphDownloadResult.Failure

    @Test fun debugEmulatorAndLoopbackAllowancesAreExact() = runBlocking {
        for (host in listOf("10.0.2.2", "127.0.0.1", "localhost")) {
            val wire = Wire(json().toByteArray(), "application/json")
            assertTrue(api(wire, "http://$host:3000", true).getCurrentRouting() is RoutingApiResult.Success)
            assertEquals("Bearer $token", wire.authorization)
        }
        for (host in listOf("evil.invalid", "10.0.2.3", "192.168.1.1", "localhost.evil.invalid")) {
            val wire = Wire(json().toByteArray(), "application/json")
            assertTrue(api(wire, "http://$host", true).getCurrentRouting() is RoutingApiResult.Failure)
            assertEquals(0, wire.calls)
        }
    }

    @Test fun releaseHttpIsRejectedBeforeTokenOrRequestAndHttpsSucceeds() = runBlocking {
        val blocked = Wire(json().toByteArray(), "application/json")
        var tokenReads = 0
        val provider = object : AccessTokenProvider { override fun getAccessToken(): String { tokenReads++; return token } }
        val result = OkHttpRoutingApiClient("http://10.0.2.2:3000", provider, blocked.client, RoutingTransportPolicy(false)).getCurrentRouting()
        assertTrue(result is RoutingApiResult.Failure); assertEquals(0, blocked.calls); assertEquals(0, tokenReads)
        val valid = Wire(json().toByteArray(), "application/json")
        assertTrue(api(valid).getCurrentRouting() is RoutingApiResult.Success)
    }

    @Test fun apiRedirectUsesActualLoopbackServerAndNeverContactsDestination() = runBlocking {
        val destinationHits = AtomicInteger()
        val destination = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        destination.createContext("/") { exchange -> destinationHits.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close() }
        val origin = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var received: String? = null
        origin.createContext("/routing/current") { exchange ->
            received = exchange.requestHeaders.getFirst("Authorization")
            exchange.responseHeaders.add("Location", "http://localhost:${destination.address.port}/stolen")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        destination.start(); origin.start()
        try {
            val result = OkHttpRoutingApiClient("http://127.0.0.1:${origin.address.port}", tokens,
                transportPolicy = RoutingTransportPolicy(true)).getCurrentRouting()
            assertTrue(result is RoutingApiResult.Failure)
            assertEquals("Bearer $token", received)
            assertEquals(0, destinationHits.get())
        } finally { origin.stop(0); destination.stop(0) }
    }

    @Test fun downloadNeverSendsBearerAndRedirectIsRejected() = runBlocking {
        val good = Wire(gzip(graph()))
        success(download(good).downloadAndPrepareGraph(status()))
        assertNull(good.authorization); assertEquals("identity", good.acceptEncoding)
        val redirect = Wire(gzip(graph()), code = 302)
        assertEquals(GraphDownloadErrorKind.TRANSPORT_REJECTED, rejected(download(redirect).downloadAndPrepareGraph(status(), true)).error.kind)
        assertEquals(1, redirect.calls); assertNull(redirect.authorization)
    }

    @Test fun credentialsFragmentsAndUnsafeSchemesAreRejectedWithoutRequest() = runBlocking {
        for (url in listOf("http://unsafe.invalid/g", "https://user:secret@download.invalid/g", "https://download.invalid/g#fragment", "file:///tmp/x", "javascript:bad")) {
            val wire = Wire(gzip(graph()))
            val input = status().let { it.copy(download = it.download.copy(url = url)) }
            assertTrue(download(wire).downloadAndPrepareGraph(input) is GraphDownloadResult.Failure)
            assertEquals(0, wire.calls)
        }
    }

    @Test fun leaseExpiryExactBoundaryIsMonotonicAndCheckedBeforeNetwork() = runBlocking {
        val value = status(ttl = 1, stamp = 1000)
        now = 1999
        val good = Wire(gzip(graph())); success(download(good).downloadAndPrepareGraph(value))
        now = 2000
        val expired = Wire(gzip(graph()))
        assertEquals(GraphDownloadErrorKind.URL_EXPIRED, rejected(download(expired).downloadAndPrepareGraph(value, true)).error.kind)
        assertEquals(0, expired.calls)
        now = 999
        assertEquals(GraphDownloadErrorKind.URL_EXPIRED, rejected(download(expired).downloadAndPrepareGraph(value, true)).error.kind)
        assertEquals(0, expired.calls)
    }

    @Test fun expiredUrlMayUseAlreadyVerifiedReferenceCacheWithoutNetwork() = runBlocking {
        val value = status(ttl = 1); val wire = Wire(gzip(graph())); val d = download(wire)
        val a = success(d.downloadAndPrepareGraph(value))
        now += 1000
        val cached = success(d.downloadAndPrepareGraph(value))
        assertTrue(cached.fromCache); assertEquals(a.file, cached.file); assertEquals(1, wire.calls)
    }

    @Test fun malformedTtlAndOverflowAreRejectedAndWireCannotSupplyReceiptTime() {
        for (bad in listOf<Any>(-1, 0, 901, Long.MAX_VALUE, "900", 1.5, JSONObject.NULL)) {
            val obj = JSONObject(json()); obj.getJSONObject("download").put("expiresIn", bad)
            assertTrue("TTL $bad", runCatching { RoutingJsonParser.parseRoutingStatus(obj.toString(), 1234) }.isFailure)
        }
        val obj = JSONObject(json()); obj.getJSONObject("download").put("receivedAtElapsedMillis", Long.MAX_VALUE)
        assertEquals(1234L, RoutingJsonParser.parseRoutingStatus(obj.toString(), 1234).download.receivedAtElapsedMillis)
    }

    @Test fun receiptCopyDoesNotResetAgeAndRequestTransitConsumesTtl() = runBlocking {
        val old = status(ttl = 1, stamp = 1000).download
        assertEquals(1000L, old.copy(url = signed + "&copy=1").receivedAtElapsedMillis)
        val wire = Wire(json(status(ttl = 1)).toByteArray(), "application/json")
        val clock = { val result = now; now += 1000; result }
        val value = (OkHttpRoutingApiClient("https://api.invalid", tokens, wire.client,
            RoutingTransportPolicy(false), elapsedMillis = clock).getCurrentRouting() as RoutingApiResult.Success).data
        val body = Wire(gzip(graph()))
        assertEquals(GraphDownloadErrorKind.URL_EXPIRED, rejected(download(body).downloadAndPrepareGraph(value)).error.kind)
        assertEquals(0, body.calls)
    }

    @Test fun repositoryRefreshIsOneAuthorizedRetryAndNeverInfinite() = runBlocking {
        val apiWire = Wire(json().toByteArray(), "application/json")
        val denied = Wire(gzip(graph()), code = 403)
        val repository = DefaultRoutingRepository(api(apiWire), download(denied))
        assertTrue(repository.downloadGraphWithAutoRefresh() is GraphDownloadResult.Failure)
        assertEquals(2, apiWire.calls); assertEquals(2, denied.calls)
        assertFalse(repository.currentRoutingStatus.value!!.canNavigateSafely)
    }

    @Test fun expiredLeaseGetsFreshBackendUrlAndSucceeds() = runBlocking {
        val apiWire = Wire(json(status(ttl = 1)).toByteArray(), "application/json")
        val wire = Wire(gzip(graph())); val repository = DefaultRoutingRepository(api(apiWire), download(wire))
        repository.getCurrentRouting(); now += 1000
        success(repository.downloadGraphWithAutoRefresh())
        assertEquals(2, apiWire.calls); assertEquals(1, wire.calls)
        assertFalse(repository.currentRoutingStatus.value!!.canNavigateSafely)
    }

    @Test fun logoutDuringUrlDenialCannotStartAnotherBackendRequest() = runBlocking {
        val apiWire = Wire(json().toByteArray(), "application/json")
        lateinit var repository: DefaultRoutingRepository
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            repository.invalidateSession()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("denied").body(ByteArray(0).toResponseBody()).build()
        }.build()
        repository = DefaultRoutingRepository(api(apiWire), OkHttpGraphDownloader(root, client, elapsedMillis = { now }))
        assertTrue(repository.downloadGraphWithAutoRefresh() is GraphDownloadResult.Failure)
        assertEquals(1, apiWire.calls); assertNull(repository.currentRoutingStatus.value)
    }

    @Test fun loaderPerformsOneGuardedLeaseRefreshWithoutChangingStrictLock() = runBlocking {
        val apiWire = Wire(json(status(ttl = 1)).toByteArray(), "application/json")
        val wire = Wire(gzip(graph()))
        var inspections = 0
        val clock = { inspections++; if (inspections == 1) now + 1000 else now }
        val downloader = OkHttpGraphDownloader(root, wire.client, elapsedMillis = clock)
        val repository = DefaultRoutingRepository(api(apiWire), downloader)
        val loader = DefaultRoutingGraphLoader(repository, downloader, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val loaded = loader.loadGraph() as GraphLoaderResult.Success
        assertEquals(2, apiWire.calls); assertEquals(1, wire.calls)
        assertFalse(loaded.isStrictRoutingPermitted); assertFalse(loaded.context!!.isStrictRoutingPermitted)
    }

    @Test fun unsupportedMimeEncodingAndRedirectPreserveGenerationA() = runBlocking {
        val wire = Wire(gzip(graph())); val d = download(wire)
        val a = success(d.downloadAndPrepareGraph(status())).file
        val before = a.readBytes(); val pointer = File(a.parentFile!!.parentFile!!, "current").readText()
        for (mode in listOf("mime", "gzip", "br", "redirect")) {
            wire.mime = if (mode == "mime") "text/html" else "application/gzip"
            wire.encoding = when (mode) { "gzip" -> "gzip"; "br" -> "br"; else -> null }
            wire.code = if (mode == "redirect") 307 else 200
            assertEquals(GraphDownloadErrorKind.TRANSPORT_REJECTED, rejected(d.downloadAndPrepareGraph(status(), true)).error.kind)
            assertArrayEquals(before, a.readBytes()); assertEquals(pointer, File(a.parentFile!!.parentFile!!, "current").readText())
        }
    }

    @Test fun identityRawGzipWithChunkedUnknownLengthSucceeds() = runBlocking {
        val wire = Wire(gzip(graph())).apply { chunked = true; unknownLength = true; encoding = "identity" }
        val value = success(download(wire).downloadAndPrepareGraph(status()))
        assertEquals(sha(gzip(graph())), value.verifiedSha256)
        assertEquals(graph(), value.file.readText()); assertNull(wire.authorization)
    }

    @Test fun malformedLengthWrongLengthShortBodyAndStatusFailSafely() = runBlocking {
        for (mode in listOf("malformed", "mismatch", "short", "empty", "status", "conflict")) {
            val wire = Wire(gzip(graph()))
            when (mode) {
                "malformed" -> wire.length = "not-a-length"
                "mismatch" -> wire.length = "1"
                "short" -> wire.bytes = wire.bytes.copyOf(10)
                "empty" -> wire.bytes = ByteArray(0)
                "status" -> wire.code = 500
                "conflict" -> { wire.chunked = true; wire.length = wire.bytes.size.toString() }
            }
            assertTrue("mode $mode", download(wire).downloadAndPrepareGraph(status(), true) is GraphDownloadResult.Failure)
        }
    }

    @Test fun apiUnexpectedEncodingMimeAndOversizeAreRejectedWithoutDiagnosticLeaks() = runBlocking {
        for (mode in listOf("mime", "encoding", "size", "syntax")) {
            val wire = Wire(json().toByteArray(), "application/json")
            when (mode) {
                "mime" -> wire.mime = "text/html"
                "encoding" -> wire.encoding = "gzip"
                "size" -> wire.bytes = ByteArray(2048) { 65 }
                "syntax" -> wire.bytes = "broken $token $signed".toByteArray()
            }
            val result = api(wire, limits = RoutingResourceLimits(apiBytes = 1024)).getCurrentRouting() as RoutingApiResult.Failure
            assertFalse(result.error.technicalMessage.contains(token)); assertFalse(result.error.technicalMessage.contains(signed))
        }
    }

    @Test fun independentCompressedCapsHaveExactBoundaryAndCannotBeRaised() = runBlocking {
        val bytes = gzip(graph()); val caps = RoutingResourceLimits(compressedBytes = bytes.size.toLong())
        val wire = Wire(bytes); val d = download(wire, caps)
        success(d.downloadAndPrepareGraph(status()))
        val oversized = status().let { it.copy(manifest = it.manifest.copy(bundle = it.manifest.bundle.copy(compressedSizeBytes = bytes.size + 1L))) }
        assertEquals(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, rejected(d.downloadAndPrepareGraph(oversized, true)).error.kind)
        assertEquals(1, wire.calls)
        wire.bytes = bytes + byteArrayOf(0); wire.unknownLength = true
        assertEquals(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, rejected(d.downloadAndPrepareGraph(status(), true)).error.kind)
        assertTrue(runCatching { RoutingResourceLimits(compressedBytes = RoutingResourceLimits.MAX_COMPRESSED + 1) }.isFailure)
    }

    @Test fun independentUncompressedCapStopsActualExpansionAndPreservesA() = runBlocking {
        val text = graph(); val caps = RoutingResourceLimits(uncompressedBytes = text.toByteArray().size.toLong())
        val wire = Wire(gzip(text)); val d = download(wire, caps)
        val a = success(d.downloadAndPrepareGraph(status())).file
        val more = text + " "; wire.bytes = gzip(more)
        val declared = status(more)
        assertEquals(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, rejected(d.downloadAndPrepareGraph(declared, true)).error.kind)
        val lying = declared.copy(manifest = declared.manifest.copy(bundle = declared.manifest.bundle.copy(uncompressedSizeBytes = caps.uncompressedBytes)))
        assertEquals(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, rejected(d.downloadAndPrepareGraph(lying, true)).error.kind)
        assertEquals(text, a.readText())
    }

    @Test fun oversizedLongLengthsAndTtlCannotOverflowOrAllocateFromMetadata() = runBlocking {
        for (compressed in listOf(false, true)) {
            val wire = Wire(gzip(graph())); val good = status()
            val bad = good.copy(manifest = good.manifest.copy(bundle = if (compressed) good.manifest.bundle.copy(compressedSizeBytes = Long.MAX_VALUE) else good.manifest.bundle.copy(uncompressedSizeBytes = Long.MAX_VALUE)))
            assertEquals(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, rejected(download(wire).downloadAndPrepareGraph(bad)).error.kind)
            assertEquals(0, wire.calls)
        }
        val wire = Wire(gzip(graph()))
        assertTrue(download(wire).downloadAndPrepareGraph(status(ttl = Long.MAX_VALUE)) is GraphDownloadResult.Failure)
        assertEquals(0, wire.calls)
    }

    @Test fun jsonDepthAndStringCapsAreEnforcedAtExactBoundary() = runBlocking {
        for ((padding, allowed) in listOf(
            ("[".repeat(31) + "0" + "]".repeat(31)) to true,
            ("[".repeat(32) + "0" + "]".repeat(32)) to false,
            ("\"" + "x".repeat(RoutingResourceLimits.MAX_STRING_BYTES) + "\"") to true,
            ("\"" + "x".repeat(RoutingResourceLimits.MAX_STRING_BYTES + 1) + "\"") to false)) {
            val text = graph(padding); val wire = Wire(gzip(text))
            val result = download(wire).downloadAndPrepareGraph(status(text), true)
            if (allowed) success(result) else assertEquals(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, rejected(result).error.kind)
        }
    }

    @Test fun apiStreamCapHandlesUnknownLengthAndNeverUsesUntrustedAvailableForAllocation() = runBlocking {
        val wire = Wire(ByteArray(2049) { 65 }, "application/json").apply { unknownLength = true }
        assertTrue(api(wire, limits = RoutingResourceLimits(apiBytes = 2048)).getCurrentRouting() is RoutingApiResult.Failure)
        val stream = object : ByteArrayInputStream(ByteArray(17)) { override fun available() = Int.MAX_VALUE }
        val bounded = RoutingBoundedInputStream(stream, 16)
        assertEquals(16, bounded.available())
        assertTrue(runCatching { bounded.readBytes() }.isFailure)
    }

    @Test fun timeoutAndOfflineErrorsAreSanitizedAndNeverRestoreReady() = runBlocking {
        for (exception in listOf(SocketTimeoutException("$token $signed"), InterruptedIOException("$token $signed"), IOException("$token $signed"))) {
            val wire = Wire(gzip(graph())).apply { this.exception = exception }
            val result = rejected(download(wire).downloadAndPrepareGraph(status(), true))
            assertFalse(result.error.toString().contains(token)); assertFalse(result.error.toString().contains(signed))
            assertEquals(if (exception is InterruptedIOException) GraphDownloadErrorKind.DOWNLOAD_TIMEOUT else GraphDownloadErrorKind.DOWNLOAD_FAILED, result.error.kind)
            val apiWire = Wire(json().toByteArray(), "application/json").apply { this.exception = exception }
            val repository = DefaultRoutingRepository(api(apiWire), download(wire))
            val apiFailure = repository.getCurrentRouting() as RoutingApiResult.Failure
            assertEquals(if (exception is InterruptedIOException) RoutingApiErrorKind.TIMEOUT else RoutingApiErrorKind.NO_INTERNET, apiFailure.error.kind)
            assertNull(repository.currentRoutingStatus.value)
        }
    }

    @Test fun callerClaimsAndOldTimestampsCannotBecomeFreshnessOrHazardAuthority() = runBlocking {
        val forged = status().copy(strictRoutingReady = true, liveHazardReady = true, canAttemptStrictRouting = true)
        val wire = Wire(json(forged).toByteArray(), "application/json")
        val response = (api(wire).getCurrentRouting() as RoutingApiResult.Success).data
        assertEquals("2000-01-01T00:00:00Z", response.manifest.generatedAt)
        assertEquals("ATTACHED", response.manifest.hazardSnapshotStatus)
        assertFalse(response.liveHazardReady); assertFalse(response.canNavigateSafely); assertFalse(response.strictRoutingReady)
    }

    @Test fun publishedWorkloadFitsHardCapsAndParsesWithProductionDownloader() = runBlocking {
        val base = File("/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app/tools/osm-importer/output-dryrun-task3b")
        val manifest = RoutingJsonParser.parseManifest(JSONObject(File(base, "RoutingManifest.json").readText()))
        val bytes = File(base, "BaseRoadGraph.json.gz").readBytes() // Read-only; output always owned temp root.
        assertTrue(manifest.bundle.compressedSizeBytes < RoutingResourceLimits.MAX_COMPRESSED)
        assertTrue(manifest.bundle.uncompressedSizeBytes < RoutingResourceLimits.MAX_UNCOMPRESSED)
        val wire = Wire(bytes)
        val input = status().copy(manifest = manifest)
        val result = success(download(wire).downloadAndPrepareGraph(input))
        assertEquals(7294, result.parsedGraph!!.nodes.size); assertEquals(16651, result.parsedGraph!!.directedEdges.size)
        assertEquals(manifest.bundle.sha256, result.verifiedSha256)
    }
    @Test fun apiByteCapExactBoundaryAndUnknownLengthOverflow() = runBlocking {
        val bytes = json().toByteArray()
        val caps = RoutingResourceLimits(apiBytes = bytes.size.toLong())
        assertTrue(api(Wire(bytes, "application/json"), limits = caps).getCurrentRouting() is RoutingApiResult.Success)
        val over = Wire(bytes + byteArrayOf(32), "application/json").apply { unknownLength = true }
        assertTrue(api(over, limits = caps).getCurrentRouting() is RoutingApiResult.Failure)
        assertFalse(status().download.toString().contains(signed))
        assertFalse(status().toString().contains("fixture-secret-signature"))
    }

    @Test fun oversizedLiteralIsBoundedBeforeParserAllocation() = runBlocking {
        val allowed = graph("1." + "0".repeat(RoutingResourceLimits.MAX_LITERAL_BYTES - 2))
        StreamingRoadGraphParser().parseInputStream(allowed.byteInputStream(), status(allowed).manifest)
        success(download(Wire(gzip(allowed))).downloadAndPrepareGraph(status(allowed), true))
        val oversized = graph("1." + "0".repeat(RoutingResourceLimits.MAX_LITERAL_BYTES - 1))
        assertEquals(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED,
            rejected(download(Wire(gzip(oversized))).downloadAndPrepareGraph(status(oversized), true)).error.kind)
    }

    @Test fun tokenRotationDuringBackendResponseDiscardsOldSessionData() = runBlocking {
        var current: String? = token
        val provider = object : AccessTokenProvider { override fun getAccessToken() = current }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            current = null
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .header("Content-Type", "application/json").body(json().toByteArray().toResponseBody()).build()
        }.build()
        val result = OkHttpRoutingApiClient("https://api.invalid", provider, client).getCurrentRouting() as RoutingApiResult.Failure
        assertEquals(RoutingApiErrorKind.NO_TOKEN, result.error.kind)
    }

    @Test fun sharedClientCredentialsAreRemovedAtActualDownloadWireBoundary() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var receivedAuthorization: String? = "not-inspected"
        var receivedCookie: String? = "not-inspected"
        val bytes = gzip(graph())
        server.createContext("/graph") { exchange ->
            receivedAuthorization = exchange.requestHeaders.getFirst("Authorization")
            receivedCookie = exchange.requestHeaders.getFirst("Cookie")
            exchange.responseHeaders.add("Content-Type", "application/gzip")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val shared = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token")
                    .header("Cookie", "session=$token").build())
            }.build()
            val input = status().let { it.copy(download = it.download.copy(url = "http://127.0.0.1:${server.address.port}/graph")) }
            success(OkHttpGraphDownloader(root, shared, RoutingTransportPolicy(true), elapsedMillis = { now }).downloadAndPrepareGraph(input))
            assertNull(receivedAuthorization); assertNull(receivedCookie)
        } finally { server.stop(0) }
    }

}
