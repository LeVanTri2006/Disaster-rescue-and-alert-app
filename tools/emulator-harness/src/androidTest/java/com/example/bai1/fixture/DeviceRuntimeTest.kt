package com.example.bai1.fixture

import android.app.Application
import android.content.Intent
import android.os.Build
import android.security.NetworkSecurityPolicy
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bai1.BuildConfig
import com.example.bai1.rescue.routing.algorithm.*
import com.example.bai1.rescue.routing.download.*
import com.example.bai1.rescue.routing.loader.*
import com.example.bai1.rescue.routing.model.*
import com.example.bai1.rescue.routing.network.*
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import com.example.bai1.rescue.routing.repository.DefaultRoutingRepository
import com.example.bai1.sos.network.AccessTokenProvider
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/** Minimal actual socket transport, only listening on device loopback. No Internet destinations. */
private class LocalFixture(private val bindHost: String = "127.0.0.1", val handler: (String, Map<String, String>) -> Reply) : Closeable {
    val requests = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
    private val listener = ServerSocket(0, 10, InetAddress.getByName(bindHost))
    val base get() = "http://${if (bindHost.contains(':')) "[$bindHost]" else bindHost}:${listener.localPort}"
    private val worker = Thread({
        while (!listener.isClosed) {
            try {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val first = input.readLine() ?: return@use
                    val headers = linkedMapOf<String, String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                    }
                    requests.add(first to headers)
                    val reply = handler(first, headers)
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 ${reply.code} Fixture\r\nContent-Type: ${reply.mime}\r\n" +
                            "Content-Length: ${reply.declaredLength ?: reply.bytes.size}\r\nConnection: close\r\n" +
                            reply.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n").toByteArray())
                        write(reply.bytes); flush()
                    }
                }
            } catch (_: IOException) { /* Closing the owned listener is expected at teardown. */ }
        }
    }, "p5e01-loopback").apply { isDaemon = true; start() }
    override fun close() { listener.close(); worker.join(6000); check(!worker.isAlive) }
}
private data class Reply(val bytes: ByteArray = byteArrayOf(), val code: Int = 200,
    val mime: String = "application/gzip", val headers: Map<String, String> = emptyMap(), val declaredLength: Int? = null)

private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
private val target get() = instrumentation.targetContext
private fun assertIsolated() {
    assertEquals("com.example.bai1.p5e01.isolated", target.packageName)
    assertEquals(Application::class.java, target.applicationContext.javaClass)
    for (name in listOf("com.google.firebase.FirebaseApp", "com.example.bai1.MainActivity",
        "com.example.bai1.auth.AuthRepository", "com.example.bai1.sos.network.OkHttpSosApiClient",
        "com.example.bai1.rescue.routing.RoutingAppDependencies")) {
        assertTrue("Cloud/production initialization class must be absent", runCatching { target.classLoader.loadClass(name) }.isFailure)
    }
    val info = target.packageManager.getPackageInfo(target.packageName,
        android.content.pm.PackageManager.GET_PROVIDERS or android.content.pm.PackageManager.GET_SERVICES or android.content.pm.PackageManager.GET_RECEIVERS)
    assertTrue(info.providers.isNullOrEmpty()); assertTrue(info.services.isNullOrEmpty()); assertTrue(info.receivers.isNullOrEmpty())
}

@RunWith(AndroidJUnit4::class)
class DebugDeviceRuntimeTest {
    private lateinit var root: File
    private val servers = mutableListOf<LocalFixture>()
    private val fixtureToken = "p5e01-synthetic-token-never-log"
    @Volatile private var token: String? = fixtureToken
    private val provider = object : AccessTokenProvider { override fun getAccessToken() = token }
    private fun client() = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    @Before fun before() {
        assertTrue(BuildConfig.DEBUG); assertIsolated()
        root = File(target.cacheDir, "p5e01-${System.nanoTime()}").apply { check(mkdirs()) }
    }
    @After fun after() { servers.forEach { it.close() }; root.deleteRecursively() }
    private fun server(handler: (String, Map<String, String>) -> Reply) = LocalFixture(handler = handler).also { servers.add(it) }
    private fun graph() = """{"contractVersion":"2.1.0","graphVersion":"device_v1","vehicleProfile":"RESCUE_TRUCK_4X4",
      "nodes":[{"id":"a","lat":0,"lon":0},{"id":"b","lat":0,"lon":0.001}],
      "edges":[{"id":"ab","fromNodeId":"a","toNodeId":"b","lengthMeters":111,
      "geometry":[{"lat":0,"lon":0},{"lat":0,"lon":0.001}],"compatibilityStatus":"UNVERIFIED"}],"turnRestrictions":[]}"""
    private fun gzip() = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(graph().toByteArray()) } }.toByteArray()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun status(url: String, bytes: ByteArray = gzip()): RoutingStatusResponse {
        val m = RoutingManifest(manifestVersion = "2.1.0", generatedAt = "2000-01-01T00:00:00Z", graphVersion = "device_v1",
            osmSnapshotTimestamp = "2000-01-01T00:00:00Z", importerVersion = "isolated-fixture", vehicleProfile = "RESCUE_TRUCK_4X4", boundingBoxType = "FIXTURE",
            bundle = RoutingBundleInfo("BaseRoadGraph.json.gz", bytes.size.toLong(), graph().toByteArray().size.toLong(), sha(bytes)),
            hazardSnapshotStatus = "ATTACHED", activeHazardSnapshotVersion = "synthetic-untrusted", hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            isRestrictionStrictRoutingEligible = false, restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(0), canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES, edgeSafetySummary = EdgeSafetySummary(1, 0, 1, 0, 0, 0.0, 0))
        return RoutingStatusResponse(m, GraphDownloadInfo(url, 900), true, false, false, false, StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES)
    }
    private fun downloader(shared: OkHttpClient = client(), checkpoint: (CacheWritePoint) -> Unit = {}) =
        OkHttpGraphDownloader(root, shared, writeCheckpoint = checkpoint)
    private fun api(base: String, shared: OkHttpClient = client()) = OkHttpRoutingApiClient(base, provider, shared)
    private fun success(result: GraphDownloadResult): GraphDownloadResult.Success {
        assertTrue("Expected verified data artifact", result is GraphDownloadResult.Success)
        return result as GraphDownloadResult.Success
    }

    @Test fun runnerApiAndIsolation() {
        assertTrue(Build.VERSION.SDK_INT >= 30)
        assertEquals(37, Build.VERSION.SDK_INT)
        assertIsolated()
        val before = android.os.SystemClock.elapsedRealtime()
        assertTrue(RoutingTime.elapsedMillis() >= before)
        assertTrue(RoutingTime.elapsedMillis() <= android.os.SystemClock.elapsedRealtime())
    }

    @Test fun api37LocalNetworkPermissionIsGrantedOnlyToHarness() {
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            target.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK"))
    }

    @Test fun androidDebugPolicyExactAllowlist() {
        val p = NetworkSecurityPolicy.getInstance()
        for (host in listOf("10.0.2.2", "127.0.0.1", "localhost")) assertTrue(p.isCleartextTrafficPermitted(host))
        for (host in listOf("outside.invalid", "10.0.2.3", "localhost.outside.invalid", "192.168.1.1", "127.0.0.2", "::1", "ip6-localhost")) assertFalse(p.isCleartextTrafficPermitted(host))
        assertFalse(p.isCleartextTrafficPermitted)
    }

    @Test fun actualTransportRejectsNonAllowlistedHostWithoutInternet() {
        val local = server { _, _ -> Reply("unexpected".toByteArray(), mime = "text/plain") }
        val restricted = client().newBuilder().dns(object : Dns { override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1")) }).build()
        val url = "http://outside.invalid:${URI(local.base).port}/"
        val error = runCatching { restricted.newCall(Request.Builder().url(url).build()).execute().use { } }.exceptionOrNull()
        assertTrue(error is java.net.UnknownServiceException)
        assertTrue(local.requests.isEmpty())
    }

    @Test fun hostEmulatorBridgeHttpFixtureActuallyConnects() {
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
        require(port in 1024..65535)
        client().newCall(Request.Builder().url("http://10.0.2.2:$port/p5e01/health").build()).execute().use {
            assertEquals(200, it.code); assertEquals("P5E01_LOCAL_ONLY", it.body!!.string())
        }
    }

    @Test fun rawGzipHashAndProductionParserRunOnAndroid() = runBlocking {
        val compressed = gzip()
        val local = server { _, _ -> Reply(compressed) }
        val s = status(local.base + "/graph")
        val result = success(downloader().downloadAndPrepareGraph(s))
        assertEquals(sha(compressed), result.verifiedSha256)
        assertEquals(graph(), result.file.readText())
        assertEquals(2, result.parsedGraph!!.nodes.size); assertEquals(1, result.parsedGraph!!.directedEdges.size)
        assertEquals("identity", local.requests.single().second["accept-encoding"])
        assertNull(local.requests.single().second["authorization"])
        assertFalse(s.canNavigateSafely)
    }

    @Test fun sharedClientCredentialsNeverReachDownloadSocket() = runBlocking {
        val local = server { _, _ -> Reply(gzip()) }
        val shared = client().newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $fixtureToken")
                .header("Cookie", "fixture=value").header("Proxy-Authorization", "fixture-proxy").build())
        }.build()
        success(downloader(shared).downloadAndPrepareGraph(status(local.base + "/graph")))
        val headers = local.requests.single().second
        assertNull(headers["authorization"]); assertNull(headers["cookie"]); assertNull(headers["proxy-authorization"])
    }

    @Test fun apiRedirectDoesNotForwardBearer() = runBlocking {
        val sink = server { _, _ -> Reply() }
        val origin = server { _, _ -> Reply(code = 302, headers = mapOf("Location" to sink.base.replace("127.0.0.1", "localhost") + "/sink")) }
        assertTrue(api(origin.base).getCurrentRouting() is RoutingApiResult.Failure)
        assertEquals("Bearer $fixtureToken", origin.requests.single().second["authorization"])
        assertTrue(sink.requests.isEmpty())
    }

    @Test fun graphRedirectIsNotFollowedAndHasNoBearer() = runBlocking {
        val sink = server { _, _ -> Reply(gzip()) }
        val origin = server { _, _ -> Reply(code = 302, headers = mapOf("Location" to sink.base + "/sink")) }
        val result = downloader().downloadAndPrepareGraph(status(origin.base + "/graph")) as GraphDownloadResult.Failure
        assertEquals(GraphDownloadErrorKind.TRANSPORT_REJECTED, result.error.kind)
        assertNull(origin.requests.single().second["authorization"]); assertTrue(sink.requests.isEmpty())
    }

    @Test fun unverifiedGraphLoaderAndRouterRemainDisabled() = runBlocking {
        val graphServer = server { _, _ -> Reply(gzip()) }
        val s = status(graphServer.base + "/graph")
        val backend = server { _, _ -> Reply(Gson().toJson(s).toByteArray(), mime = "application/json") }
        val d = downloader(); val repo = DefaultRoutingRepository(api(backend.base), d)
        val loader = DefaultRoutingGraphLoader(repo, d, StreamingRoadGraphParser())
        assertTrue(loader.loadGraph() is GraphLoaderResult.Success)
        assertTrue(loader.state.value is GraphLoaderState.GraphLoadedButRoutingDisabled)
        assertFalse(loader.isStrictRoutingPermitted()); assertFalse(loader.snapshot.value.isStrictRoutingPermitted)
        val parsed = loader.currentGraph!!
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(parsed, "a", "b").status)
        assertFalse(repo.currentRoutingStatus.value!!.liveHazardReady)
    }

    @Test fun offlineErrorCannotPublishReady() = runBlocking {
        val closed = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")); val port = closed.localPort; closed.close()
        val d = downloader(); val repo = DefaultRoutingRepository(api("http://127.0.0.1:$port"), d)
        val loader = DefaultRoutingGraphLoader(repo, d, StreamingRoadGraphParser())
        assertTrue(loader.loadGraph() is GraphLoaderResult.Failure)
        assertNull(repo.currentRoutingStatus.value); assertFalse(loader.isStrictRoutingPermitted())
        assertNull(loader.currentContext)
    }

    @Test fun invalidationDuringRealHttpCannotReviveOldSession() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val s = status("http://127.0.0.1:9/unused")
        val backend = server { _, _ -> entered.countDown(); check(release.await(8, TimeUnit.SECONDS)); Reply(Gson().toJson(s).toByteArray(), mime = "application/json") }
        val repo = DefaultRoutingRepository(api(backend.base), downloader())
        val pending = async(Dispatchers.IO) { repo.getCurrentRouting() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); token = null; repo.invalidateSession()
        } finally { release.countDown() }
        assertTrue(pending.await() is RoutingApiResult.Failure); assertNull(repo.currentRoutingStatus.value)
        assertTrue(repo.getCurrentRouting() is RoutingApiResult.Failure)
        assertEquals(1, backend.requests.size)
    }

    @Test fun actualSocketReadTimeoutRemainsFailClosed() = runBlocking {
        val backend = server { _, _ -> Thread.sleep(16000); Reply(byteArrayOf(), mime = "application/json") }
        val repo = DefaultRoutingRepository(api(backend.base), downloader())
        val result = repo.getCurrentRouting() as RoutingApiResult.Failure
        assertEquals(RoutingApiErrorKind.TIMEOUT, result.error.kind)
        assertNull(repo.currentRoutingStatus.value)
        assertFalse(result.error.technicalMessage.contains(fixtureToken))
    }

    @Test fun expiredLeaseMakesNoDeviceRequest() = runBlocking {
        val local = server { _, _ -> Reply(gzip()) }
        val s = status(local.base + "/graph").let { it.copy(download = it.download.copy(expiresIn = 1,
            receivedAtElapsedMillis = RoutingTime.elapsedMillis() - 1000)) }
        val result = downloader().downloadAndPrepareGraph(s) as GraphDownloadResult.Failure
        assertEquals(GraphDownloadErrorKind.URL_EXPIRED, result.error.kind); assertTrue(local.requests.isEmpty())
    }

    @Test fun contentFailurePreservesPublishedGeneration() = runBlocking {
        var bad = false
        val local = server { _, _ -> if (bad) Reply(gzip(), headers = mapOf("Content-Encoding" to "gzip")) else Reply(gzip()) }
        val s = status(local.base + "/graph"); val d = downloader()
        val first = success(d.downloadAndPrepareGraph(s)); val content = first.file.readBytes()
        bad = true
        assertTrue(d.downloadAndPrepareGraph(s, true) is GraphDownloadResult.Failure)
        assertArrayEquals(content, first.file.readBytes())
        val cached = success(d.downloadAndPrepareGraph(s)); assertTrue(cached.fromCache)
    }

    @Test fun truncatedSocketResponseNeverPublishesGraph() = runBlocking {
        val bytes = gzip()
        val local = server { _, _ -> Reply(bytes.copyOf(bytes.size / 2), declaredLength = bytes.size) }
        assertTrue(downloader().downloadAndPrepareGraph(status(local.base + "/graph", bytes)) is GraphDownloadResult.Failure)
        assertTrue(root.walkTopDown().none { it.name == "current" })
    }

    @Test fun cancellationAtPublicationBoundaryPreservesA() = runBlocking {
        val local = server { _, _ -> Reply(gzip()) }; val s = status(local.base + "/graph")
        val d = downloader(); val first = success(d.downloadAndPrepareGraph(s)); val before = first.file.readBytes()
        val cancelled = downloader(checkpoint = { if (it == CacheWritePoint.BEFORE_POINTER_COMMIT) throw CancellationException("fixture cancellation") })
        try { cancelled.downloadAndPrepareGraph(s, true); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertArrayEquals(before, first.file.readBytes()); assertTrue(success(d.downloadAndPrepareGraph(s)).fromCache)
    }

    @Test fun localHarnessActivitySmokeOnly() {
        val activity = instrumentation.startActivitySync(Intent(target, FixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FixtureActivity
        try {
            instrumentation.runOnMainSync {
                val label = activity.window.decorView.findViewWithTag<TextView>("status")
                assertTrue(label.text.contains("STRICT ROUTING DISABLED"))
                activity.window.decorView.findViewWithTag<Button>("error").performClick()
                assertTrue(label.text.contains("network unavailable"))
                activity.window.decorView.findViewWithTag<Button>("logout").performClick()
                assertTrue(label.text.contains("session cleared"))
            }
            instrumentation.waitForIdleSync()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}

/** Same compiled production NSC as release, separate process/install from debug execution. */
@RunWith(AndroidJUnit4::class)
class ReleasePolicyDeviceRuntimeTest {
    @Test fun isolatedNonDebuggableReleaseLikeRuntime() {
        assertIsolated(); assertFalse(BuildConfig.DEBUG)
        assertEquals(0, target.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
        assertEquals(37, Build.VERSION.SDK_INT)
    }
    @Test fun releasePolicyDeniesEveryDebugHostAtRuntime() {
        val p = NetworkSecurityPolicy.getInstance()
        val hosts = listOf("10.0.2.2", "127.0.0.1", "localhost", "outside.invalid", "127.0.0.2", "::1", "ip6-localhost")
        val observed = org.json.JSONObject()
        for (host in hosts) observed.put(host, p.isCleartextTrafficPermitted(host))
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString("p5e01PolicySnapshot", observed.toString()) })
        assertFalse(p.isCleartextTrafficPermitted)
        for (host in hosts) assertFalse("Release HTTP must be denied for $host", p.isCleartextTrafficPermitted(host))
        assertTrue(runCatching { RoutingTransportPolicy(true).url("http://127.0.0.1:3000") }.isFailure)
    }
    @Test fun releaseLikeActualSocketHttpBlockedBeforeAnyRequest() {
        LocalFixture { _, _ -> Reply("unexpected".toByteArray()) }.use { local ->
            val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
            val error = runCatching { client.newCall(Request.Builder().url(local.base).build()).execute().use { } }.exceptionOrNull()
            assertTrue("Expected cleartext refusal; actual=${error?.javaClass?.simpleName}; receivedRequests=${local.requests.size}", error is UnknownServiceException)
            assertTrue(local.requests.isEmpty())
        }
    }
    @Test fun releaseActualSocketsDenyIpv4Ipv6AndLocalhostNames() {
        for ((host, address) in listOf("127.0.0.1" to "127.0.0.1", "127.0.0.2" to "127.0.0.2",
            "::1" to "::1", "localhost" to "127.0.0.1", "ip6-localhost" to "::1")) {
            LocalFixture(address) { _, _ -> Reply("owned-fixture".toByteArray(), mime = "text/plain") }.use { local ->
                val port = URI(local.base).port
                // Prove this exact listener is reachable; raw Socket doesn't implement HTTP policy.
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(InetAddress.getByName(address), port), 2000)
                    socket.soTimeout = 2000
                    socket.getOutputStream().write("GET /health HTTP/1.1\r\nHost: fixture\r\nConnection: close\r\n\r\n".toByteArray())
                    assertTrue(String(socket.getInputStream().readBytes()).startsWith("HTTP/1.1 200"))
                }
                assertEquals(1, local.requests.size); local.requests.clear()
                val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).dns(object : Dns {
                    override fun lookup(hostname: String) = listOf(InetAddress.getByName(address))
                }).build()
                val urlHost = if (host.contains(':')) "[$host]" else host
                val error = runCatching { client.newCall(Request.Builder().url("http://$urlHost:$port/").build()).execute().use { } }.exceptionOrNull()
                assertTrue("Platform must reject HTTP for $host", error is UnknownServiceException)
                assertTrue("No HTTP request at $host after health probe", local.requests.isEmpty())
            }
        }
        val hostPort = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
        val bridgeClient = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
        val bridgeError = runCatching { bridgeClient.newCall(Request.Builder()
            .url("http://10.0.2.2:$hostPort/p5e01/health").build()).execute().use { } }.exceptionOrNull()
        assertTrue("Release must reject the actual emulator host bridge HTTP request", bridgeError is UnknownServiceException)
    }
    @Test fun releaseNonLocalHostIsBlockedUsingControlledLoopbackDns() {
        LocalFixture { _, _ -> Reply() }.use { local ->
            val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).dns(object : Dns {
                override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
            }).build()
            val error = runCatching { client.newCall(Request.Builder()
                .url("http://outside.invalid:${URI(local.base).port}/").build()).execute().use { } }.exceptionOrNull()
            assertTrue(error is UnknownServiceException); assertTrue(local.requests.isEmpty())
        }
    }
    @Test fun productionRoutingClientRejectsHttpEvenWhenPlatformAllowsLoopback() = runBlocking {
        LocalFixture { _, _ -> Reply(mime = "application/json") }.use { local ->
            var reads = 0
            val provider = object : AccessTokenProvider { override fun getAccessToken(): String { reads++; return "synthetic-unused" } }
            val client = OkHttpRoutingApiClient(local.base, provider, OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build())
            val result = client.getCurrentRouting() as RoutingApiResult.Failure
            assertEquals(RoutingApiErrorKind.BAD_REQUEST, result.error.kind)
            assertEquals(0, reads); assertTrue(local.requests.isEmpty())
        }
    }
}

/** Separate invocation after denial/revocation, without touching the production package. */
@RunWith(AndroidJUnit4::class)
class LocalPermissionDeniedDeviceRuntimeTest {
    @Test fun localPermissionIsDeniedWhileCleartextFixturePolicyRemainsAllowed() {
        assertIsolated(); assertTrue(BuildConfig.DEBUG)
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
            target.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK"))
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("10.0.2.2"))
    }
    @Test fun bridgeSocketFailsClosedWhenLocalPermissionIsAbsent() {
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).connectTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val error = runCatching { client.newCall(Request.Builder().url("http://10.0.2.2:$port/p5e01/health").build()).execute().use { } }.exceptionOrNull()
        assertTrue("Permission-denied local traffic must fail", error is IOException)
        assertTrue(runCatching { RoutingTransportPolicy(false).url("http://10.0.2.2:$port") }.isFailure)
    }
}
