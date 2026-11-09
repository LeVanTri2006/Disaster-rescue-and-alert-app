package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.UniformCostSearchRouter
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.download.*
import com.example.bai1.rescue.routing.loader.*
import com.example.bai1.rescue.routing.model.*
import com.example.bai1.rescue.routing.network.*
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import com.example.bai1.rescue.routing.repository.DefaultRoutingRepository
import kotlinx.coroutines.*
import com.example.bai1.auth.AuthRepository
import com.example.bai1.auth.network.*
import com.example.bai1.auth.session.*
import com.google.gson.Gson
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * P5-A01R security acceptance of real production consumers. F04/F05 now assert content binding and transactional preservation.
 * No production verifier or consumer is implemented here. Transport alone is replaced.
 * P5-A01R environment: NOT_RUN until an installed JDK is available.
 */
class P5A01ConsumerAuditTest {
    private lateinit var ownedDir: File
    private val hash: (ByteArray) -> String = { bytes ->
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    private val graphJson = """{
      "contractVersion":"2.1.0","graphVersion":"audit_v1","vehicleProfile":"RESCUE_TRUCK_4X4",
      "isRestrictionStrictRoutingEligible":true,"restrictionStrictRoutingReadiness":"READY",
      "canAttemptStrictRouting":true,"isStrictRoutingEligible":true,
      "strictRoutingReadiness":"READY","overallStrictRoutingReadiness":"READY",
      "edgeSafetySummary":{"totalEdges":1,"verifiedCompatibleEdges":1,"unverifiedEdges":0,
        "blockedEdges":0,"conflictedEdges":0,"strictSafeEdgesPercent":100,"blockedWays":0},
      "nodes":[{"id":"a","lat":0,"lon":0},{"id":"b","lat":0,"lon":0.001}],
      "edges":[{"id":"edge_a_b","fromNodeId":"a","toNodeId":"b","lengthMeters":111,
        "geometry":[{"lat":0,"lon":0},{"lat":0,"lon":0.001}],
        "compatibilityStatus":"VERIFIED_COMPATIBLE","isEligibleForStrictRouting":true}],
      "turnRestrictions":[]
    }""".trimIndent()

    @Before fun setup() { ownedDir = Files.createTempDirectory("p5a01-audit-").toFile() }
    @After fun cleanup() { ownedDir.deleteRecursively() }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()

    private fun status(text: String = graphJson): RoutingStatusResponse {
        val bytes = gzip(text)
        val manifest = RoutingManifest(
            manifestVersion = "2.1.0", generatedAt = "2000-01-01T00:00:00Z",
            graphVersion = "audit_v1", osmSnapshotTimestamp = "2000-01-01T00:00:00Z",
            importerVersion = "audit-fixture", vehicleProfile = "RESCUE_TRUCK_4X4", boundingBoxType = "FIXTURE",
            bundle = RoutingBundleInfo("BaseRoadGraph.json.gz", bytes.size.toLong(),
                text.toByteArray(Charsets.UTF_8).size.toLong(), hash(bytes)),
            activeHazardSnapshotVersion = "stale_fixture_hazard", hazardSnapshotStatus = "ATTACHED",
            hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            isRestrictionStrictRoutingEligible = true, restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(0), canAttemptStrictRouting = true,
            isStrictRoutingEligible = true, overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            strictRoutingReadiness = StrictRoutingReadiness.READY,
            edgeSafetySummary = EdgeSafetySummary(1, 1, 0, 0, 0, 100.0, 0)
        )
        return RoutingStatusResponse(manifest, GraphDownloadInfo("https://fixture.invalid/graph.gz", 900),
            downloadAvailable = true, strictRoutingReady = true, liveHazardReady = false,
            canAttemptStrictRouting = true, overallStrictRoutingReadiness = StrictRoutingReadiness.READY)
    }

    private fun parse(text: String = graphJson): RoadGraph = StreamingRoadGraphParser()
        .parseInputStream(text.byteInputStream(Charsets.UTF_8), status(text).manifest)

    private fun downloader(bytes: ByteArray, code: Int = 200): OkHttpGraphDownloader {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            // Fail immediately for any unexpected URL; this test never opens a network connection.
            check(chain.request().url.host == "fixture.invalid")
            check(chain.request().header("Authorization") == null)
            Response.Builder().header("Content-Type", "application/gzip").request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("audit fixture").body(bytes.toResponseBody()).build()
        }.build()
        return OkHttpGraphDownloader(ownedDir, client)
    }

    private class StatusTransport(var reply: RoutingApiResult<RoutingStatusResponse>) : RoutingApiClient {
        override suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse> = reply
    }

    @Test fun legacyRawGraphCannotAuthorizeStrictRouting() {
        val result = UniformCostSearchRouter().findRoute(parse(), "a", "b")
        // Legacy SUCCESS was unsafe: no production-authoritative evidence was supplied.
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertTrue(result.pathEdges.isEmpty())
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(
            parse(), "a", "b", currentEpochMillis = 0L, liveHazardReady = true).status)
    }

    @Test fun contextCannotClaimPermissionFromMetadataAndMatchingClaimedDigest() {
        val graph = parse()
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val manifest = status().manifest
        val context = RescueRoutingContext(graph, TurnRestrictionIndex.build(graph), dfa, manifest, manifest.bundle.sha256)
        assertFalse(context.isStrictRoutingPermitted)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(context, "a", "b").status)
    }

    @Test fun coldBackendFalseDeniesLoaderAndExportedContext() = runBlocking {
        val ready = status()
        val response = ready.copy(strictRoutingReady = false, canAttemptStrictRouting = false)
        val transport = StatusTransport(RoutingApiResult.Success(response))
        val download = downloader(gzip(graphJson))
        val loader = DefaultRoutingGraphLoader(DefaultRoutingRepository(transport, download), download,
            StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val result = loader.loadGraph() as GraphLoaderResult.Success
        assertFalse(result.isStrictRoutingPermitted)
        val exported = requireNotNull(result.context)
        assertFalse(exported.isStrictRoutingPermitted)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(exported, "a", "b").status)
    }

    @Test fun sameVersionRefreshToBackendFalseRevalidatesAndHeldContextStaysDenied() = runBlocking {
        val ready = status()
        val transport = StatusTransport(RoutingApiResult.Success(ready))
        val download = downloader(gzip(graphJson))
        val loader = DefaultRoutingGraphLoader(DefaultRoutingRepository(transport, download), download,
            StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val first = loader.loadGraph() as GraphLoaderResult.Success
        assertFalse(first.isStrictRoutingPermitted)
        val lockedManifest = ready.manifest.copy(canAttemptStrictRouting = false, isStrictRoutingEligible = false,
            isRestrictionStrictRoutingEligible = false, restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES,
            strictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES,
            edgeSafetySummary = ready.manifest.edgeSafetySummary.copy(verifiedCompatibleEdges = 0, unverifiedEdges = 1, strictSafeEdgesPercent = 0.0))
        transport.reply = RoutingApiResult.Success(ready.copy(manifest = lockedManifest, strictRoutingReady = false,
            canAttemptStrictRouting = false, overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES))
        val second = loader.loadGraph() as GraphLoaderResult.Success
        // Revalidate even when graphVersion is unchanged; neither held nor new contexts authorize.
        assertFalse(second.isStrictRoutingPermitted)
        assertNotSame(first.context, second.context)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED,
            UniformCostSearchRouter().findRoute(requireNotNull(first.context), "a", "b").status)
        assertFalse(requireNotNull(second.context).isStrictRoutingPermitted)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(requireNotNull(second.context), "a", "b").status)
    }

    @Test fun repositoryClearsCachedStatusAcrossHttpAndOfflineErrors() = runBlocking {
        val ready = status()
        val transport = StatusTransport(RoutingApiResult.Success(ready))
        val repository = DefaultRoutingRepository(transport)
        repository.getCurrentRouting()
        for (code in listOf(401, 403, 404, 500, 503)) {
            val kind = when (code) {
                401 -> RoutingApiErrorKind.UNAUTHORIZED
                403 -> RoutingApiErrorKind.FORBIDDEN
                404 -> RoutingApiErrorKind.NOT_FOUND
                else -> RoutingApiErrorKind.SERVER
            }
            transport.reply = RoutingApiResult.Success(ready)
            repository.getCurrentRouting()
            assertFalse(requireNotNull(repository.currentRoutingStatus.value).canNavigateSafely)
            transport.reply = RoutingApiResult.Failure(RoutingApiError(kind, "audit", code))
            assertTrue(repository.getCurrentRouting() is RoutingApiResult.Failure)
            assertNull(repository.currentRoutingStatus.value)
        }
        transport.reply = RoutingApiResult.Success(ready)
        repository.getCurrentRouting()
        transport.reply = RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.NO_INTERNET, "audit offline"))
        repository.getCurrentRouting()
        assertNull(repository.currentRoutingStatus.value)
    }

    @Test fun cachedJsonTamperingAtSameLengthIsRejectedAndReplaced() = runBlocking {
        val response = status()
        val download = downloader(gzip(graphJson))
        val first = download.downloadAndPrepareGraph(response) as GraphDownloadResult.Success
        val changed = graphJson.replace("111", "999")
        assertNotEquals(graphJson, changed)
        assertEquals(graphJson.toByteArray().size, changed.toByteArray().size)
        first.file.writeText(changed)
        assertFalse(download.isGraphCached(response.manifest))
        val cached = download.downloadAndPrepareGraph(response) as GraphDownloadResult.Success
        assertFalse(cached.fromCache)
        assertEquals(graphJson, cached.file.readText())
        assertEquals(response.manifest.bundle.sha256, cached.verifiedSha256)
        assertEquals(111.0, requireNotNull(cached.parsedGraph).directedEdges.single().lengthMeters, 0.0)
    }

    @Test fun failedGzipRefreshPreservesPreviousGeneration() = runBlocking {
        val ready = status()
        val good = downloader(gzip(graphJson))
        val first = good.downloadAndPrepareGraph(ready) as GraphDownloadResult.Success
        val bad = ByteArray(64) { 65 }
        val badStatus = ready.copy(manifest = ready.manifest.copy(bundle = ready.manifest.bundle.copy(
            compressedSizeBytes = bad.size.toLong(), sha256 = hash(bad))))
        val result = downloader(bad).downloadAndPrepareGraph(badStatus, forceRefresh = true)
        assertTrue(result is GraphDownloadResult.Failure)
        assertTrue(File(first.file.parentFile, "BaseRoadGraph.json.gz").exists())
        assertEquals(graphJson, first.file.readText())
        assertTrue(good.isGraphCached(ready.manifest))
        assertEquals(first.file, good.getCachedGraphFile(ready.manifest))
    }

    @Test fun unverifiedEdgeDoesNotBecomeTraversableMerelyBecauseGraphCountsSayReady() {
        val graph = parse(graphJson.replace("VERIFIED_COMPATIBLE", "UNVERIFIED"))
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(graph, "a", "b").status)
    }
    private fun context(): RescueRoutingContext {
        val graph = parse()
        val manifest = status().manifest
        return RescueRoutingContext(graph, TurnRestrictionIndex.build(graph), ViaWayTurnRestrictionDfa.empty(),
            manifest, manifest.bundle.sha256)
    }

    @Test fun copiedAndJsonDtosCannotMintPermission() {
        val fabricated = status().copy(strictRoutingReady = true, canAttemptStrictRouting = true, liveHazardReady = true)
        val parsed = RoutingJsonParser.parseRoutingStatus(Gson().toJson(fabricated))
        assertFalse(parsed.canNavigateSafely)
        assertFalse(context().copy().isStrictRoutingPermitted)
        val c = context()
        val state = GraphLoaderState.GraphLoaded(c.roadGraph, c.manifest, c)
        val snapshot = GraphLoaderSnapshot.loaded(c.roadGraph, c.manifest, c, state).copy()
        assertFalse(snapshot.isStrictRoutingPermitted)
        assertFalse(GraphLoaderResult.Success(c.roadGraph, c.manifest, state, c).copy().isStrictRoutingPermitted)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED,
            UniformCostSearchRouter().findRoute(c.copy(), "a", "b").status)
    }

    @Test fun sameVersionDifferentDigestMustRevalidateBytes() = runBlocking {
        val transport = StatusTransport(RoutingApiResult.Success(status()))
        val download = downloader(gzip(graphJson))
        val loader = DefaultRoutingGraphLoader(DefaultRoutingRepository(transport), download,
            StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val held = (loader.loadGraph() as GraphLoaderResult.Success).context!!
        val old = status()
        transport.reply = RoutingApiResult.Success(old.copy(manifest = old.manifest.copy(
            bundle = old.manifest.bundle.copy(sha256 = "0".repeat(64)))))
        assertTrue(loader.loadGraph() is GraphLoaderResult.Failure)
        assertNull(loader.currentContext)
        assertFalse(held.isStrictRoutingPermitted)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(held, "a", "b").status)
    }

    @Test fun sameVersionDifferentProfileMustRevalidateParserIdentity() = runBlocking {
        val transport = StatusTransport(RoutingApiResult.Success(status()))
        val download = downloader(gzip(graphJson))
        val loader = DefaultRoutingGraphLoader(DefaultRoutingRepository(transport), download,
            StreamingRoadGraphParser(), Dispatchers.Unconfined)
        loader.loadGraph()
        val old = status()
        transport.reply = RoutingApiResult.Success(old.copy(manifest = old.manifest.copy(vehicleProfile = "OTHER_PROFILE")))
        val failure = loader.loadGraph() as GraphLoaderResult.Failure
        assertEquals(GraphLoaderErrorKind.VEHICLE_PROFILE_MISMATCH, failure.error.kind)
        assertNull(loader.currentContext)
        assertFalse(loader.isStrictRoutingPermitted())
    }

    @Test fun downloadAndAttachedHazardNeverGrantQualificationOrLiveSafety() = runBlocking {
        val input = status().copy(liveHazardReady = true)
        val transport = StatusTransport(RoutingApiResult.Success(input))
        val download = downloader(gzip(graphJson))
        val repository = DefaultRoutingRepository(transport, download)
        assertTrue(repository.downloadGraph() is GraphDownloadResult.Success)
        assertFalse(repository.currentRoutingStatus.value!!.canNavigateSafely)
        assertFalse(repository.currentRoutingStatus.value!!.liveHazardReady)
        val loader = DefaultRoutingGraphLoader(repository, download, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val result = loader.loadGraph() as GraphLoaderResult.Success
        assertFalse((result.state as GraphLoaderState.GraphLoadedButRoutingDisabled).liveHazardReady)
        assertFalse(result.context!!.isStrictRoutingPermitted)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(
            result.context!!, "a", "a", currentEpochMillis = 0L, liveHazardReady = true).status)
    }

    @Test fun loadingAndFailedSnapshotsDoNotRetainNestedContexts() {
        val c = context()
        val previous = GraphLoaderSnapshot.loaded(c.roadGraph, c.manifest, c,
            GraphLoaderState.GraphLoaded(c.roadGraph, c.manifest, c))
        assertNull(GraphLoaderSnapshot.loading(GraphLoaderState.FetchingStatus, previous).context)
        assertNull(GraphLoaderSnapshot.failed(GraphLoaderError(GraphLoaderErrorKind.UNKNOWN, "fixture"), previous).context)
        assertFalse(previous.isStrictRoutingPermitted)
    }

    @Test fun repeatedLoadRefreshAndErrorsStayDisabled() = runBlocking {
        val transport = StatusTransport(RoutingApiResult.Success(status()))
        val download = downloader(gzip(graphJson))
        val repository = DefaultRoutingRepository(transport, download)
        val loader = DefaultRoutingGraphLoader(repository, download, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        repeat(3) {
            transport.reply = RoutingApiResult.Success(status())
            val loaded = loader.loadGraph(forceRefresh = it % 2 == 0) as GraphLoaderResult.Success
            val held = loaded.context!!
            assertFalse(loaded.isStrictRoutingPermitted)
            transport.reply = RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.SERVER, "fixture", 500))
            assertTrue(loader.loadGraph() is GraphLoaderResult.Failure)
            assertNull(repository.currentRoutingStatus.value)
            assertNull(loader.currentContext)
            assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(held, "a", "b").status)
        }
    }

    @Test fun invalidationRejectsLateRepositoryResponse() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = DefaultRoutingRepository(object : RoutingApiClient {
            override suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse> {
                started.complete(Unit)
                release.await()
                return RoutingApiResult.Success(status())
            }
        })
        val job = async { repository.getCurrentRouting() }
        started.await()
        repository.invalidateSession()
        release.complete(Unit)
        assertTrue(job.await() is RoutingApiResult.Failure)
        assertNull(repository.currentRoutingStatus.value)
        assertFalse(repository.isLoading.value)
    }

    @Test fun logoutDuringLoadCannotRepublishOldSessionSnapshot() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = DefaultRoutingRepository(object : RoutingApiClient {
            override suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse> {
                started.complete(Unit)
                release.await()
                return RoutingApiResult.Success(status())
            }
        })
        val download = downloader(gzip(graphJson))
        val loader = DefaultRoutingGraphLoader(repository, download, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val job = async { loader.loadGraph() }
        started.await()
        RoutingAppDependencies.invalidateRoutingSession(repository, loader)
        release.complete(Unit)
        assertTrue(job.await() is GraphLoaderResult.Failure)
        assertSame(GraphLoaderState.Idle, loader.state.value)
        assertNull(loader.currentContext)
        assertNull(repository.currentRoutingStatus.value)
    }

    private class MemoryTokens : TokenStorage {
        var access: String? = "admin-token"
        var refresh: String? = "admin-refresh"
        override fun save(accessToken: String, refreshToken: String): Boolean {
            access = accessToken; refresh = refreshToken; return true
        }
        override fun getAccessToken() = access
        override fun getRefreshToken() = refresh
        override fun clear() { access = null; refresh = null }
        override fun isLoggedIn() = access != null
    }

    private fun authApi(expired: Boolean = false): AuthApiClient = Proxy.newProxyInstance(
        AuthApiClient::class.java.classLoader, arrayOf(AuthApiClient::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "login" -> AuthApiResult.Success(AuthTokens("user-token", "user-refresh"))
            "refresh" -> AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "fixture expired refresh"))
            "me" -> if (expired) AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "fixture expired token"))
                else AuthApiResult.Success(AuthUser("fixture-user", "Fixture", "fixture@example.invalid", null,
                "USER", "ACTIVE", null))
            else -> error("Unexpected auth transport call: ${method.name}")
        }
    } as AuthApiClient

    @Test fun realLogoutClearsRoutingMemoryAndPreservesGraphFiles() = runBlocking {
        val transport = StatusTransport(RoutingApiResult.Success(status()))
        val download = downloader(gzip(graphJson))
        val repository = DefaultRoutingRepository(transport, download)
        val loader = DefaultRoutingGraphLoader(repository, download, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val held = (loader.loadGraph() as GraphLoaderResult.Success).context!!
        val tokens = MemoryTokens()
        val auth = AuthRepository(authApi(), tokens, invalidateRoutingSession = {
            RoutingAppDependencies.invalidateRoutingSession(repository, loader)
        })
        auth.logout()
        assertNull(tokens.access)
        assertSame(AuthState.Unauthenticated, auth.state.value)
        assertNull(repository.currentRoutingStatus.value)
        assertNull(loader.currentContext)
        assertTrue(download.isGraphCached(status().manifest))
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(held, "a", "b").status)
    }

    @Test fun userLoginCannotInheritAdminRoutingState() = runBlocking {
        val transport = StatusTransport(RoutingApiResult.Success(status()))
        val download = downloader(gzip(graphJson))
        val repository = DefaultRoutingRepository(transport, download)
        val loader = DefaultRoutingGraphLoader(repository, download, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val held = (loader.loadGraph() as GraphLoaderResult.Success).context!!
        val tokens = MemoryTokens()
        val auth = AuthRepository(authApi(), tokens, invalidateRoutingSession = {
            RoutingAppDependencies.invalidateRoutingSession(repository, loader)
        })
        auth.login("fixture-user", "fixture-password")
        assertEquals("USER", (auth.state.value as AuthState.Authenticated).user.role)
        assertNull(repository.currentRoutingStatus.value)
        assertNull(loader.currentContext)
        transport.reply = RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.FORBIDDEN, "USER denied", 403))
        assertTrue(repository.getCurrentRouting() is RoutingApiResult.Failure)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(held, "a", "b").status)
    }

    @Test fun timeoutAndThrownTransportErrorsClearPriorData() = runBlocking {
        val transport = StatusTransport(RoutingApiResult.Success(status()))
        val repository = DefaultRoutingRepository(transport)
        repository.getCurrentRouting()
        transport.reply = RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.TIMEOUT, "fixture timeout"))
        repository.getCurrentRouting()
        assertNull(repository.currentRoutingStatus.value)
        var shouldThrow = false
        val throwingRepository = DefaultRoutingRepository(object : RoutingApiClient {
            override suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse> {
                if (shouldThrow) throw IllegalStateException("fixture failure")
                return RoutingApiResult.Success(status())
            }
        })
        throwingRepository.getCurrentRouting()
        shouldThrow = true
        assertTrue(throwingRepository.getCurrentRouting() is RoutingApiResult.Failure)
        assertNull(throwingRepository.currentRoutingStatus.value)
        assertFalse(throwingRepository.isLoading.value)
    }

    @Test fun lateDownloadAfterLogoutCannotRepublishContext() = runBlocking {
        val downloaded = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val realDownload = downloader(gzip(graphJson))
        val delayedDelivery = object : GraphDownloader by realDownload {
            override suspend fun downloadAndPrepareGraph(statusResponse: RoutingStatusResponse, forceRefresh: Boolean): GraphDownloadResult {
                val result = realDownload.downloadAndPrepareGraph(statusResponse, forceRefresh)
                downloaded.complete(Unit)
                release.await()
                return result
            }
        }
        val repository = DefaultRoutingRepository(StatusTransport(RoutingApiResult.Success(status())))
        val loader = DefaultRoutingGraphLoader(repository, delayedDelivery, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val job = async { loader.loadGraph() }
        downloaded.await()
        RoutingAppDependencies.invalidateRoutingSession(repository, loader)
        release.complete(Unit)
        assertTrue(job.await() is GraphLoaderResult.Failure)
        assertSame(GraphLoaderState.Idle, loader.state.value)
        assertNull(loader.currentContext)
        assertNull(repository.currentRoutingStatus.value)
        assertTrue(realDownload.isGraphCached(status().manifest))
    }

    @Test fun expiredTokenRestoreRevokesRoutingSession() = runBlocking {
        val download = downloader(gzip(graphJson))
        val repository = DefaultRoutingRepository(StatusTransport(RoutingApiResult.Success(status())))
        val loader = DefaultRoutingGraphLoader(repository, download, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val held = (loader.loadGraph() as GraphLoaderResult.Success).context!!
        val tokens = MemoryTokens()
        val auth = AuthRepository(authApi(expired = true), tokens, invalidateRoutingSession = {
            RoutingAppDependencies.invalidateRoutingSession(repository, loader)
        })
        auth.restoreSession()
        assertSame(AuthState.Unauthenticated, auth.state.value)
        assertNull(tokens.access)
        assertNull(repository.currentRoutingStatus.value)
        assertNull(loader.currentContext)
        assertFalse(held.isStrictRoutingPermitted)
    }

}
