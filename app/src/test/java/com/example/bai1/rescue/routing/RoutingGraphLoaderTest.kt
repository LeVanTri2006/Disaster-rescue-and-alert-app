package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.algorithm.DfaDecision
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.download.GraphDownloadError
import com.example.bai1.rescue.routing.download.GraphDownloadErrorKind
import com.example.bai1.rescue.routing.download.GraphDownloadResult
import com.example.bai1.rescue.routing.download.GraphDownloadState
import com.example.bai1.rescue.routing.download.GraphDownloader
import com.example.bai1.rescue.routing.loader.DefaultRoutingGraphLoader
import com.example.bai1.rescue.routing.loader.GraphLoaderErrorKind
import com.example.bai1.rescue.routing.loader.GraphLoaderResult
import com.example.bai1.rescue.routing.loader.GraphLoaderSnapshot
import com.example.bai1.rescue.routing.loader.GraphLoaderState
import com.example.bai1.rescue.routing.loader.RoutingDisabledReason
import com.example.bai1.rescue.routing.model.EdgeMetadata
import com.example.bai1.rescue.routing.model.EdgeSafetySummary
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.GraphDownloadInfo
import com.example.bai1.rescue.routing.model.OsmRelationMember
import com.example.bai1.rescue.routing.model.RescueRoutingContext
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.RoutingBoundingBox
import com.example.bai1.rescue.routing.model.RoutingBundleInfo
import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.RoutingStatusResponse
import com.example.bai1.rescue.routing.model.StrictRoutingEvaluation
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.UnsupportedRestrictionsSummary
import com.example.bai1.rescue.routing.model.ViaWayCompilationStatus
import com.example.bai1.rescue.routing.network.RoutingApiError
import com.example.bai1.rescue.routing.network.RoutingApiErrorKind
import com.example.bai1.rescue.routing.network.RoutingApiResult
import com.example.bai1.rescue.routing.parser.GraphParseException
import com.example.bai1.rescue.routing.parser.RoadGraphParser
import com.example.bai1.rescue.routing.repository.RoutingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutingGraphLoaderTest {

    // ==========================================
    // FAKE IMPLEMENTATIONS CHO CÁC DEPENDENCIES
    // ==========================================

    private class FakeRoutingRepository : RoutingRepository {
        var statusResult: RoutingApiResult<RoutingStatusResponse> = RoutingApiResult.Failure(
            RoutingApiError(RoutingApiErrorKind.UNKNOWN, "Not initialized")
        )
        var getStatusCallCount = 0
        var delayMs = 0L

        private val _currentStatus = MutableStateFlow<RoutingStatusResponse?>(null)
        override val currentRoutingStatus: StateFlow<RoutingStatusResponse?> = _currentStatus.asStateFlow()
        override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)
        override val lastError: StateFlow<RoutingApiError?> = MutableStateFlow(null)
        override val downloadState: StateFlow<GraphDownloadState> = MutableStateFlow(GraphDownloadState.Idle)

        override suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse> {
            getStatusCallCount++
            if (delayMs > 0) delay(delayMs)
            if (statusResult is RoutingApiResult.Success) {
                _currentStatus.value = (statusResult as RoutingApiResult.Success).data
            }
            return statusResult
        }

        override suspend fun downloadGraph(forceRefresh: Boolean): GraphDownloadResult =
            throw UnsupportedOperationException("Should be called via GraphDownloader")

        override suspend fun downloadGraphWithAutoRefresh(forceRefresh: Boolean): GraphDownloadResult =
            throw UnsupportedOperationException("Should be called via GraphDownloader")

        override fun isCurrentGraphCached(): Boolean = false
        override fun getCachedGraphFile(): File? = null
    }

    private class FakeGraphDownloader : GraphDownloader {
        var downloadResult: GraphDownloadResult = GraphDownloadResult.Failure(
            GraphDownloadError(GraphDownloadErrorKind.PRECONDITION_FAILED, "Not initialized")
        )
        var downloadCallCount = 0
        var lastPassedStatusResponse: RoutingStatusResponse? = null
        var lastPassedForceRefresh: Boolean? = null
        var delayMs = 0L
        var isCached = false
        var autoFillSha256 = true

        private val _state = MutableStateFlow<GraphDownloadState>(GraphDownloadState.Idle)
        override val state: StateFlow<GraphDownloadState> = _state.asStateFlow()

        override suspend fun downloadAndPrepareGraph(
            statusResponse: RoutingStatusResponse,
            forceRefresh: Boolean
        ): GraphDownloadResult {
            downloadCallCount++
            lastPassedStatusResponse = statusResponse
            lastPassedForceRefresh = forceRefresh
            if (delayMs > 0) delay(delayMs)
            val result = downloadResult
            if (autoFillSha256 && result is GraphDownloadResult.Success && result.verifiedSha256.isBlank()) {
                return result.copy(verifiedSha256 = statusResponse.manifest.bundle.sha256)
            }
            return result
        }

        override fun isGraphCached(manifest: RoutingManifest): Boolean = isCached

        override fun getCachedGraphFile(manifest: RoutingManifest): File? =
            if (isCached) File("/tmp/mock_cached_${manifest.graphVersion}.json") else null

        override fun clearCache(graphVersion: String?) {}
    }

    private class FakeRoadGraphParser : RoadGraphParser {
        var parseResult: RoadGraph? = null
        var parseException: Exception? = null
        var parseCallCount = 0
        var lastPassedFile: File? = null
        var lastPassedManifest: RoutingManifest? = null
        var delayMs = 0L

        override suspend fun parse(file: File, expectedManifest: RoutingManifest?): RoadGraph {
            parseCallCount++
            lastPassedFile = file
            lastPassedManifest = expectedManifest
            if (delayMs > 0) delay(delayMs)
            parseException?.let { throw it }
            return parseResult ?: throw IllegalStateException("Neither parseResult nor parseException was set")
        }

        override fun parseSync(file: File, expectedManifest: RoutingManifest?): RoadGraph {
            return parseResult ?: throw IllegalStateException("Sync parse not supported in this mock")
        }
    }

    // ==========================================
    // HELPER BUILDERS CHO TEST FIXTURES
    // ==========================================

    private fun createSampleManifest(
        graphVersion: String = "dn_central_real_v1",
        vehicleProfile: String = "RESCUE_TRUCK_4X4",
        canAttemptStrictRouting: Boolean = false,
        overallStrictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
        restrictionStrictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
        unsupportedRestrictionsCount: Int = 16,
        isRestrictionStrictRoutingEligible: Boolean = (unsupportedRestrictionsCount == 0),
        unverifiedEdges: Long = 16651L,
        verifiedEdges: Long = 0L
    ): RoutingManifest {
        return RoutingManifest(
            schema = "https://emergency-rescue.vn/schemas/v2/routing-manifest.json",
            manifestVersion = "2.1.0",
            generatedAt = "2026-11-04T12:24:50.392Z",
            graphVersion = graphVersion,
            osmSnapshotTimestamp = "2026-11-03T21:24:23Z",
            importerVersion = "osm-importer-v2.3",
            vehicleProfile = vehicleProfile,
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(minLon = 108.15, minLat = 15.95, maxLon = 108.35, maxLat = 16.15),
            bundle = RoutingBundleInfo(
                fileName = "BaseRoadGraph.json.gz",
                compressedSizeBytes = 1173443L,
                uncompressedSizeBytes = 33002111L,
                sha256 = "9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b"
            ),
            hazardSnapshotStatus = "NOT_INCLUDED",
            hazardAssessmentPolicy = "FAIL_CLOSED_IN_STRICT_MODE",
            isRestrictionStrictRoutingEligible = isRestrictionStrictRoutingEligible,
            restrictionStrictRoutingReadiness = restrictionStrictRoutingReadiness,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(
                totalCount = unsupportedRestrictionsCount,
                enforcementPolicy = "FAIL_CLOSED_IN_STRICT_MODE"
            ),
            canAttemptStrictRouting = canAttemptStrictRouting,
            overallStrictRoutingReadiness = overallStrictRoutingReadiness,
            edgeSafetySummary = EdgeSafetySummary(
                totalEdges = verifiedEdges + unverifiedEdges,
                verifiedCompatibleEdges = verifiedEdges,
                unverifiedEdges = unverifiedEdges,
                blockedEdges = 0L,
                conflictedEdges = 0L,
                strictSafeEdgesPercent = if (verifiedEdges + unverifiedEdges > 0) (verifiedEdges.toDouble() / (verifiedEdges + unverifiedEdges)) * 100.0 else 0.0,
                blockedWays = 0L
            )
        )
    }

    private fun createSampleStatusResponse(
        manifest: RoutingManifest = createSampleManifest(),
        downloadAvailable: Boolean = true,
        strictRoutingReady: Boolean = false,
        canAttemptStrictRouting: Boolean = false,
        liveHazardReady: Boolean = false
    ): RoutingStatusResponse {
        return RoutingStatusResponse(
            manifest = manifest,
            download = GraphDownloadInfo(
                url = "https://pub-r2.example.com/graphs/${manifest.graphVersion}/BaseRoadGraph.json.gz?signature=mocked",
                expiresIn = 900L
            ),
            downloadAvailable = downloadAvailable,
            strictRoutingReady = strictRoutingReady,
            liveHazardReady = liveHazardReady,
            canAttemptStrictRouting = canAttemptStrictRouting,
            overallStrictRoutingReadiness = manifest.overallStrictRoutingReadiness
        )
    }

    private fun createSampleRoadGraph(
        manifest: RoutingManifest,
        canAttemptStrictRouting: Boolean = manifest.canAttemptStrictRouting,
        overallStrictRoutingReadiness: StrictRoutingReadiness = manifest.overallStrictRoutingReadiness,
        restrictionStrictRoutingReadiness: StrictRoutingReadiness = manifest.restrictionStrictRoutingReadiness,
        isRestrictionStrictRoutingEligible: Boolean = manifest.isRestrictionStrictRoutingEligible,
        unsupportedRestrictionsCount: Int = manifest.unsupportedRestrictionsSummary.totalCount,
        unverifiedEdges: Long = manifest.edgeSafetySummary.unverifiedEdges,
        verifiedEdges: Long = manifest.edgeSafetySummary.verifiedCompatibleEdges,
        nodes: Map<String, RoadNode> = mapOf(
            "node_1" to RoadNode(
                id = "node_1",
                position = GeoPoint(16.05, 108.20),
                osmNodeId = 1001L
            )
        ),
        directedEdges: List<RoadEdge> = emptyList(),
        turnRestrictions: List<TurnRestriction>? = null
    ): RoadGraph {
        val defaultRestrictionsList = (0 until unsupportedRestrictionsCount).map { i ->
            TurnRestriction(
                id = "rel_$i",
                type = TurnRestrictionType.NO_LEFT_TURN,
                supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
            )
        }

        return RoadGraph(
            nodes = nodes,
            directedEdges = directedEdges,
            graphVersion = manifest.graphVersion,
            contractVersion = "2.1.0",
            vehicleProfile = manifest.vehicleProfile,
            boundingBoxType = manifest.boundingBoxType,
            boundingBox = manifest.boundingBox,
            isRestrictionStrictRoutingEligible = isRestrictionStrictRoutingEligible,
            restrictionStrictRoutingReadiness = restrictionStrictRoutingReadiness,
            canAttemptStrictRouting = canAttemptStrictRouting,
            isStrictRoutingEligible = canAttemptStrictRouting,
            strictRoutingReadiness = overallStrictRoutingReadiness,
            overallStrictRoutingReadiness = overallStrictRoutingReadiness,
            edgeSafetySummary = EdgeSafetySummary(
                totalEdges = verifiedEdges + unverifiedEdges,
                verifiedCompatibleEdges = verifiedEdges,
                unverifiedEdges = unverifiedEdges,
                blockedEdges = 0L,
                conflictedEdges = 0L,
                strictSafeEdgesPercent = if (verifiedEdges + unverifiedEdges > 0) (verifiedEdges.toDouble() / (verifiedEdges + unverifiedEdges)) * 100.0 else 0.0,
                blockedWays = 0L
            ),
            turnRestrictions = turnRestrictions ?: defaultRestrictionsList
        )
    }

    // ==========================================
    // 12 BẮT BUỘC UNIT TESTS
    // ==========================================

    @Test
    fun `test 1 - loadGraph reads legacy ready data but keeps strict routing disabled`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsCount = 0,
            unverifiedEdges = 0L,
            verifiedEdges = 100L
        )
        val statusResponse = createSampleStatusResponse(
            manifest = manifest,
            downloadAvailable = true,
            strictRoutingReady = true,
            canAttemptStrictRouting = true
        )
        repo.statusResult = RoutingApiResult.Success(statusResponse)

        val mockFile = File("/tmp/downloaded_BaseRoadGraph.json")
        downloader.downloadResult = GraphDownloadResult.Success(
            file = mockFile,
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        val sampleGraph = createSampleRoadGraph(manifest)
        parser.parseResult = sampleGraph

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue("Expected Success result", result is GraphLoaderResult.Success)
        val successResult = result as GraphLoaderResult.Success
        assertFalse("Production authority is unavailable", successResult.isStrictRoutingPermitted)
        assertFalse("Loader must remain disabled", loader.isStrictRoutingPermitted())
        assertEquals(sampleGraph, loader.currentGraph)
        assertEquals(manifest, loader.currentManifest)
        assertTrue(loader.state.value is GraphLoaderState.GraphLoadedButRoutingDisabled)
        assertFalse((loader.state.value as GraphLoaderState.GraphLoadedButRoutingDisabled).fromCache)
    }

    @Test
    fun `test 2 - loadGraph uses valid cache`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        val statusResponse = createSampleStatusResponse(manifest = manifest)
        repo.statusResult = RoutingApiResult.Success(statusResponse)

        val cachedFile = File("/tmp/cached_BaseRoadGraph.json")
        downloader.downloadResult = GraphDownloadResult.Success(
            file = cachedFile,
            graphVersion = manifest.graphVersion,
            fromCache = true
        )

        val sampleGraph = createSampleRoadGraph(manifest)
        parser.parseResult = sampleGraph

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        val state = loader.state.value
        assertTrue(state is GraphLoaderState.GraphLoadedButRoutingDisabled)
        assertTrue((state as GraphLoaderState.GraphLoadedButRoutingDisabled).fromCache)
        assertFalse(downloader.lastPassedForceRefresh ?: true)
    }

    @Test
    fun `test 3 - does not download when downloadAvailable is false`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        val statusResponse = createSampleStatusResponse(
            manifest = manifest,
            downloadAvailable = false
        )
        repo.statusResult = RoutingApiResult.Success(statusResponse)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Failure)
        val failure = result as GraphLoaderResult.Failure
        assertEquals(GraphLoaderErrorKind.DOWNLOAD_UNAVAILABLE, failure.error.kind)
        assertEquals(0, downloader.downloadCallCount)
        assertEquals(0, parser.parseCallCount)
        assertTrue(loader.state.value is GraphLoaderState.Failed)
    }

    @Test
    fun `test 4 - parser receives exact file returned by downloader`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))

        val specificFile = File("/data/user/0/com.example.bai1/cache/routing_graphs/test_exact.json")
        downloader.downloadResult = GraphDownloadResult.Success(
            file = specificFile,
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        loader.loadGraph(forceRefresh = false)

        assertEquals(specificFile, parser.lastPassedFile)
        assertEquals(manifest, parser.lastPassedManifest)
    }

    @Test
    fun `test 5 - graph version mismatch fails loader`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest(graphVersion = "dn_central_real_v1")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))

        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = "dn_central_real_v1",
            fromCache = false
        )

        parser.parseException = GraphParseException.GraphVersionMismatchException(
            "Graph version mismatch: expected 'dn_central_real_v1', got 'dn_central_real_v2'"
        )

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Failure)
        val failure = result as GraphLoaderResult.Failure
        assertEquals(GraphLoaderErrorKind.VERSION_MISMATCH, failure.error.kind)
        assertTrue(loader.state.value is GraphLoaderState.Failed)
        assertFalse(loader.isStrictRoutingPermitted())
    }

    @Test
    fun `test 6 - vehicle profile mismatch fails loader`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest(vehicleProfile = "RESCUE_TRUCK_4X4")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))

        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        parser.parseException = GraphParseException.VehicleProfileMismatchException(
            "Vehicle profile mismatch: expected 'RESCUE_TRUCK_4X4', got 'AMBULANCE'"
        )

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Failure)
        val failure = result as GraphLoaderResult.Failure
        assertEquals(GraphLoaderErrorKind.VEHICLE_PROFILE_MISMATCH, failure.error.kind)
        assertTrue(loader.state.value is GraphLoaderState.Failed)
    }

    @Test
    fun `test 7 - parser failure transitions to Failed state`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))

        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        parser.parseException = GraphParseException.MalformedJsonException("Malformed JSON syntax at offset 120")

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Failure)
        val failure = result as GraphLoaderResult.Failure
        assertEquals(GraphLoaderErrorKind.PARSE_FAILED, failure.error.kind)
        assertTrue(loader.state.value is GraphLoaderState.Failed)
        assertNull(loader.currentGraph)
    }

    @Test
    fun `test 8 - download failure does not call parser and sets Failed state`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))

        downloader.downloadResult = GraphDownloadResult.Failure(
            GraphDownloadError(
                kind = GraphDownloadErrorKind.DOWNLOAD_FAILED,
                technicalMessage = "Connection refused by storage host"
            )
        )

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Failure)
        val failure = result as GraphLoaderResult.Failure
        assertEquals(GraphLoaderErrorKind.DOWNLOAD_FAILED, failure.error.kind)
        assertEquals(0, parser.parseCallCount)
        assertTrue(loader.state.value is GraphLoaderState.Failed)
    }

    @Test
    fun `test 9 - parsed graph loaded into RAM but strict routing disabled (Danang Production Scenario)`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        // Bối cảnh thực tế đồ thị Đà Nẵng: 16 turn restrictions chưa hỗ trợ, 16651 unverified edges
        val manifest = createSampleManifest(
            graphVersion = "dn_central_real_v1",
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsCount = 16,
            unverifiedEdges = 16651L,
            verifiedEdges = 0L
        )
        val statusResponse = createSampleStatusResponse(
            manifest = manifest,
            downloadAvailable = true,
            strictRoutingReady = false,
            canAttemptStrictRouting = false
        )
        repo.statusResult = RoutingApiResult.Success(statusResponse)

        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/Danang_BaseRoadGraph.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        val realDanangGraph = createSampleRoadGraph(manifest)
        parser.parseResult = realDanangGraph

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue("Graph must load into RAM successfully", result is GraphLoaderResult.Success)
        val success = result as GraphLoaderResult.Success
        assertFalse("Strict routing MUST NOT be permitted", success.isStrictRoutingPermitted)
        assertFalse("loader isStrictRoutingPermitted must be false", loader.isStrictRoutingPermitted())
        assertNotNull("Graph must be retained in memory for diagnostics", loader.currentGraph)
        assertEquals(realDanangGraph, loader.currentGraph)

        val state = loader.state.value
        assertTrue("State must be GraphLoadedButRoutingDisabled", state is GraphLoaderState.GraphLoadedButRoutingDisabled)
        val disabledState = state as GraphLoaderState.GraphLoadedButRoutingDisabled
        assertEquals(RoutingDisabledReason.BACKEND_STRICT_ROUTING_DISABLED, disabledState.reason)
    }

    @Test
    fun `test 10 - contradictory readiness between backend and parsed graph rejects strict routing`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        // Backend báo: Sẵn sàng (READY)
        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsCount = 0,
            unverifiedEdges = 0L,
            verifiedEdges = 500L
        )
        val statusResponse = createSampleStatusResponse(
            manifest = manifest,
            strictRoutingReady = true,
            canAttemptStrictRouting = true
        )
        repo.statusResult = RoutingApiResult.Success(statusResponse)

        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        // NHƯNG Graph trong file sau parse lại mâu thuẫn: canAttemptStrictRouting = false
        val contradictoryGraph = createSampleRoadGraph(
            manifest = manifest,
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS
        )
        parser.parseResult = contradictoryGraph

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        assertFalse("Fail-closed: strict routing must be rejected due to contradiction", loader.isStrictRoutingPermitted())

        val state = loader.state.value
        assertTrue(state is GraphLoaderState.GraphLoadedButRoutingDisabled)
        val disabledState = state as GraphLoaderState.GraphLoadedButRoutingDisabled
        assertEquals(RoutingDisabledReason.CONTRADICTORY_READINESS, disabledState.reason)
    }

    @Test
    fun `test 11 - concurrent loadGraph requests do not duplicate download or parse`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = FakeRoutingRepository().apply { delayMs = 50 }
        val downloader = FakeGraphDownloader().apply { delayMs = 50 }
        val parser = FakeRoadGraphParser().apply { delayMs = 50 }

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, testDispatcher)

        // Bắn 2 request đồng thời
        val deferred1 = async(testDispatcher) { loader.loadGraph(forceRefresh = false) }
        val deferred2 = async(testDispatcher) { loader.loadGraph(forceRefresh = false) }

        testScheduler.advanceUntilIdle()

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        assertTrue(result1 is GraphLoaderResult.Success)
        assertTrue(result2 is GraphLoaderResult.Success)

        // Kiểm tra downloader và parser chỉ được gọi đúng 1 lần duy nhất!
        assertEquals("Downloader should only be executed once", 1, downloader.downloadCallCount)
        assertEquals("Parser should only be executed once", 1, parser.parseCallCount)
        assertEquals("Repository should only be fetched once", 1, repo.getStatusCallCount)
    }

    @Test
    fun `test 12 - manifest version change invalidates old graph and loads new version`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        // 1. Nạp graph v1 trước
        val manifestV1 = createSampleManifest(graphVersion = "dn_central_real_v1")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV1))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/v1.json"),
            graphVersion = "dn_central_real_v1",
            fromCache = false
        )
        val graphV1 = createSampleRoadGraph(manifestV1)
        parser.parseResult = graphV1

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val resultV1 = loader.loadGraph(forceRefresh = false)
        assertTrue(resultV1 is GraphLoaderResult.Success)
        assertEquals("dn_central_real_v1", loader.currentGraph?.graphVersion)

        // 2. Backend đổi sang graph v2
        val manifestV2 = createSampleManifest(graphVersion = "dn_central_real_v2")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV2))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/v2.json"),
            graphVersion = "dn_central_real_v2",
            fromCache = false
        )
        val graphV2 = createSampleRoadGraph(manifestV2)
        parser.parseResult = graphV2

        val resultV2 = loader.loadGraph(forceRefresh = false)
        assertTrue(resultV2 is GraphLoaderResult.Success)
        assertEquals("dn_central_real_v2", loader.currentGraph?.graphVersion)
        assertEquals(manifestV2, loader.currentManifest)

        // 3. Nếu v3 bị lỗi tải, đồ thị cũ v2 không được dùng cho định tuyến
        val manifestV3 = createSampleManifest(graphVersion = "dn_central_real_v3")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV3))
        downloader.downloadResult = GraphDownloadResult.Failure(
            GraphDownloadError(GraphDownloadErrorKind.DOWNLOAD_FAILED, "Network down")
        )

        val resultV3 = loader.loadGraph(forceRefresh = false)
        assertTrue(resultV3 is GraphLoaderResult.Failure)
        assertFalse(loader.isStrictRoutingPermitted())
        assertNull("Old graph must be cleared upon version change", loader.currentGraph)
    }

    @Test
    fun `test 13 - clearLoadedGraph resets memory and sets state to Idle`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        loader.loadGraph(forceRefresh = false)
        assertNotNull(loader.currentGraph)

        loader.clearLoadedGraph()
        assertNull(loader.currentGraph)
        assertNull(loader.currentManifest)
        assertEquals(GraphLoaderState.Idle, loader.state.value)
        assertFalse(loader.isStrictRoutingPermitted())
    }

    @Test
    fun `test 14 - retry succeeds after initial repository failure`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        // Lần 1: Lỗi mạng
        repo.statusResult = RoutingApiResult.Failure(
            RoutingApiError(RoutingApiErrorKind.TIMEOUT, "Timeout connecting to backend")
        )

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val res1 = loader.loadGraph(forceRefresh = false)
        assertTrue(res1 is GraphLoaderResult.Failure)
        assertTrue(loader.state.value is GraphLoaderState.Failed)

        // Lần 2: Mạng hồi phục
        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val res2 = loader.loadGraph(forceRefresh = false)
        assertTrue(res2 is GraphLoaderResult.Success)
        assertNotNull(loader.currentGraph)
    }

    @Test
    fun `test 15 - routing context created atomically with graph, index, dfa, and manifest identity`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsCount = 0,
            unverifiedEdges = 0L,
            verifiedEdges = 100L
        )
        val statusResponse = createSampleStatusResponse(
            manifest = manifest,
            downloadAvailable = true,
            strictRoutingReady = true,
            canAttemptStrictRouting = true
        )
        repo.statusResult = RoutingApiResult.Success(statusResponse)
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )
        val roadGraph = createSampleRoadGraph(manifest)
        parser.parseResult = roadGraph

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        val success = result as GraphLoaderResult.Success
        assertNotNull(success.context)
        assertNotNull(loader.currentContext)

        val context = loader.currentContext!!
        assertSame(roadGraph, context.roadGraph)
        assertSame(loader.currentGraph, context.roadGraph)
        assertSame(manifest, context.manifest)
        assertSame(loader.currentManifest, context.manifest)
        assertSame(context, success.context)
        assertEquals(manifest.bundle.sha256, context.artifactSha256)
        assertEquals(manifest.graphVersion, context.graphVersion)
        assertEquals(manifest.vehicleProfile, context.vehicleProfile)
        assertFalse(context.isStrictRoutingPermitted)
        assertNotNull(context.turnRestrictionIndex)
        assertNotNull(context.viaWayDfa)
    }

    @Test
    fun `test 16 - dfa build failure does not fallback to empty dfa and sets DFA_BUILD_FAILED reason`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        val statusResponse = createSampleStatusResponse(manifest = manifest)
        repo.statusResult = RoutingApiResult.Success(statusResponse)
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        // Restriction COMPILED nhưng trỏ tới các cạnh không tồn tại trong graph -> DFA build trả về DfaBuildResult.Failure
        val invalidRestriction = TurnRestriction(
            id = "rel_invalid_edges",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(999L),
            fromOsmWayIds = listOf(111L),
            toOsmWayIds = listOf(222L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("non_existent_edge_1", "non_existent_edge_2")
        )

        val brokenGraph = createSampleRoadGraph(
            manifest = manifest,
            directedEdges = emptyList(),
            turnRestrictions = listOf(invalidRestriction)
        )
        parser.parseResult = brokenGraph

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        val success = result as GraphLoaderResult.Success
        assertFalse("Strict routing must not be permitted on DFA failure", success.isStrictRoutingPermitted)
        val disabledState = success.state as GraphLoaderState.GraphLoadedButRoutingDisabled
        assertEquals(RoutingDisabledReason.DFA_BUILD_FAILED, disabledState.reason)
        assertNull("Context must be null when DFA build fails", success.context)
        assertNull("loader.currentContext must be null when DFA build fails", loader.currentContext)
        assertFalse(loader.isStrictRoutingPermitted())

        val state = loader.state.value
        assertSame(disabledState, state)
        assertTrue(disabledState.detailMessage.contains("ViaWayTurnRestrictionDfa build failed"))
        assertNull(disabledState.context)
    }

    @Test
    fun `test 17 - graph with zero via-way patterns creates valid dfa with zero patterns`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest(
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsCount = 0
        )
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        // Graph không chứa via-way restriction
        val graphNoViaWays = createSampleRoadGraph(
            manifest = manifest,
            turnRestrictions = emptyList()
        )
        parser.parseResult = graphNoViaWays

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        assertNotNull(loader.currentContext)
        val dfa = loader.currentContext!!.viaWayDfa
        assertEquals(0, dfa.patternCount)
        assertEquals(1, dfa.stateCount)
    }

    @Test
    fun `test 18 - graph with compiled and unresolved via-ways retains correct diagnostics`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest(
            unsupportedRestrictionsCount = 1
        )
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )

        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val n3 = RoadNode("n3", GeoPoint(16.07, 108.22), 1003L)
        val n4 = RoadNode("n4", GeoPoint(16.08, 108.23), 1004L)
        val nodes = mapOf("n1" to n1, "n2" to n2, "n3" to n3, "n4" to n4)

        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)
        val e2 = RoadEdge(id = "e2", fromNodeId = "n2", toNodeId = "n3", lengthMeters = 100.0, osmWayId = 20L)
        val e3 = RoadEdge(id = "e3", fromNodeId = "n3", toNodeId = "n4", lengthMeters = 100.0, osmWayId = 30L)

        // Restriction 1: Đã compile thành công
        val compiledRestriction = TurnRestriction(
            id = "rel_compiled",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(20L),
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(30L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )

        // Restriction 2: Chưa giải quyết được (chưa compiled)
        val unresolvedRestriction = TurnRestriction(
            id = "rel_unresolved",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(99L),
            viaWayCompilationStatus = ViaWayCompilationStatus.UNRESOLVED,
            viaWayCompilationError = "NO_VALID_EDGE_SEQUENCE"
        )

        val mixedGraph = createSampleRoadGraph(
            manifest = manifest,
            nodes = nodes,
            directedEdges = listOf(e1, e2, e3),
            turnRestrictions = listOf(compiledRestriction, unresolvedRestriction)
        )
        parser.parseResult = mixedGraph

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        assertNotNull(loader.currentContext)
        val context = loader.currentContext!!

        // DFA biên dịch đúng 1 pattern (compiled), bỏ qua pattern UNRESOLVED
        assertEquals(1, context.viaWayDfa.patternCount)
        val res1 = context.viaWayDfa.evaluateTransition(0, "e1")
        assertEquals(DfaDecision.ALLOW, res1.decision)
        val res2 = context.viaWayDfa.evaluateTransition(res1.nextStateId!!, "e2")
        assertEquals(DfaDecision.ALLOW, res2.decision)
        val res3 = context.viaWayDfa.evaluateTransition(res2.nextStateId!!, "e3")
        assertEquals(DfaDecision.DENY_PROHIBITED, res3.decision)
        assertTrue(res3.violatedRestrictionIds.contains("rel_compiled"))

        // Safety gate vẫn chặn strict routing do còn unresolved restrictions
        assertFalse(context.isStrictRoutingPermitted)
        assertFalse(loader.isStrictRoutingPermitted())
    }

    @Test
    fun `test 19 - strict routing remains locked when unresolved restrictions or unverified edges exist`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        // Kịch bản production Đà Nẵng: 16 unsupported restrictions, 16651 unverified edges
        val manifest = createSampleManifest(
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsCount = 16,
            unverifiedEdges = 16651L,
            verifiedEdges = 0L
        )
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        val success = result as GraphLoaderResult.Success
        assertFalse(success.isStrictRoutingPermitted)
        val disabledState = success.state as GraphLoaderState.GraphLoadedButRoutingDisabled
        assertEquals(RoutingDisabledReason.BACKEND_STRICT_ROUTING_DISABLED, disabledState.reason)

        assertNotNull(loader.currentContext)
        assertFalse(loader.currentContext!!.isStrictRoutingPermitted)
        assertFalse(loader.isStrictRoutingPermitted())

        assertTrue(loader.state.value is GraphLoaderState.GraphLoadedButRoutingDisabled)
    }

    @Test
    fun `test 20 - clearLoadedGraph atomically clears context, graph, manifest, and resets state to Idle`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        loader.loadGraph(forceRefresh = false)

        assertNotNull(loader.currentGraph)
        assertNotNull(loader.currentManifest)
        assertNotNull(loader.currentContext)

        loader.clearLoadedGraph()

        assertNull(loader.currentGraph)
        assertNull(loader.currentManifest)
        assertNull(loader.currentContext)
        assertEquals(GraphLoaderState.Idle, loader.state.value)
        assertFalse(loader.isStrictRoutingPermitted())
    }

    @Test
    fun `test 21 - graph refresh atomically replaces context without mixing old and new resources`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        // Version 1
        val manifestV1 = createSampleManifest(graphVersion = "dn_v1")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV1))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock_v1.json"),
            graphVersion = manifestV1.graphVersion,
            fromCache = false
        )
        val graphV1 = createSampleRoadGraph(manifestV1)
        parser.parseResult = graphV1

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        loader.loadGraph(forceRefresh = false)

        val contextV1 = loader.currentContext
        assertNotNull(contextV1)
        assertSame(graphV1, contextV1!!.roadGraph)
        assertEquals("dn_v1", contextV1.graphVersion)

        // Version 2
        val manifestV2 = createSampleManifest(graphVersion = "dn_v2")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV2))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock_v2.json"),
            graphVersion = manifestV2.graphVersion,
            fromCache = false
        )
        val graphV2 = createSampleRoadGraph(manifestV2)
        parser.parseResult = graphV2

        loader.loadGraph(forceRefresh = true)

        val contextV2 = loader.currentContext
        assertNotNull(contextV2)
        assertSame(graphV2, contextV2!!.roadGraph)
        assertEquals("dn_v2", contextV2.graphVersion)

        // Xác minh không trộn lẫn giữa tài nguyên v1 và v2
        assertTrue(contextV1 !== contextV2)
        assertTrue(contextV1.roadGraph !== contextV2.roadGraph)
        assertTrue(contextV1.manifest !== contextV2.manifest)
        assertTrue(contextV1.turnRestrictionIndex !== contextV2.turnRestrictionIndex)
        assertTrue(contextV1.viaWayDfa !== contextV2.viaWayDfa)
    }

    @Test
    fun `test 22 - concurrent loadGraph calls share identical atomic context`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = FakeRoutingRepository().apply { delayMs = 50 }
        val downloader = FakeGraphDownloader().apply { delayMs = 50 }
        val parser = FakeRoadGraphParser().apply { delayMs = 50 }

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, testDispatcher)

        val deferred1 = async(testDispatcher) { loader.loadGraph(forceRefresh = false) }
        val deferred2 = async(testDispatcher) { loader.loadGraph(forceRefresh = false) }

        testScheduler.advanceUntilIdle()

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        assertTrue(result1 is GraphLoaderResult.Success)
        assertTrue(result2 is GraphLoaderResult.Success)

        val context1 = (result1 as GraphLoaderResult.Success).context
        val context2 = (result2 as GraphLoaderResult.Success).context

        assertNotNull(context1)
        assertNotNull(context2)
        assertSame(context1, context2)
        assertSame(loader.currentContext, context1)
        assertEquals(1, downloader.downloadCallCount)
        assertEquals(1, parser.parseCallCount)
        assertEquals(1, repo.getStatusCallCount)
    }

    @Test
    fun `test 23 - context with unresolved restrictions denies strict permission`() {
        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsCount = 2,
            unverifiedEdges = 0L,
            verifiedEdges = 100L
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            unsupportedRestrictionsCount = 2,
            unverifiedEdges = 0L,
            verifiedEdges = 100L
        )
        val index = TurnRestrictionIndex.build(roadGraph)
        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse("Context must not permit strict routing when unresolved restrictions exist", context.isStrictRoutingPermitted)
        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        val denied = context.readinessEvaluation as StrictRoutingEvaluation.Denied
        assertEquals(RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS, denied.reason)
    }

    @Test
    fun `test 24 - context with unverified edges denies strict permission`() {
        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_HAS_UNVERIFIED_EDGES,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsCount = 0,
            unverifiedEdges = 50L,
            verifiedEdges = 10L
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            unsupportedRestrictionsCount = 0,
            unverifiedEdges = 50L,
            verifiedEdges = 10L
        )
        val index = TurnRestrictionIndex.build(roadGraph)
        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse("Context must not permit strict routing when unverified edges exist", context.isStrictRoutingPermitted)
        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        val denied = context.readinessEvaluation as StrictRoutingEvaluation.Denied
        assertEquals(RoutingDisabledReason.UNVERIFIED_EDGES, denied.reason)
    }

    @Test
    fun `test 25 - dfa build success alone does not unlock strict routing`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val n3 = RoadNode("n3", GeoPoint(16.07, 108.22), 1003L)
        val n4 = RoadNode("n4", GeoPoint(16.08, 108.23), 1004L)
        val nodes = mapOf("n1" to n1, "n2" to n2, "n3" to n3, "n4" to n4)

        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)
        val e2 = RoadEdge(id = "e2", fromNodeId = "n2", toNodeId = "n3", lengthMeters = 100.0, osmWayId = 20L)
        val e3 = RoadEdge(id = "e3", fromNodeId = "n3", toNodeId = "n4", lengthMeters = 100.0, osmWayId = 30L)

        val compiledRestriction = TurnRestriction(
            id = "rel_compiled",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(20L),
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(30L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )

        val manifest = createSampleManifest(
            unsupportedRestrictionsCount = 1,
            unverifiedEdges = 100L,
            verifiedEdges = 0L
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            nodes = nodes,
            directedEdges = listOf(e1, e2, e3),
            turnRestrictions = listOf(compiledRestriction),
            unsupportedRestrictionsCount = 1,
            unverifiedEdges = 100L,
            verifiedEdges = 0L
        )

        val index = TurnRestrictionIndex.build(roadGraph)
        val dfaBuildResult = ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph)
        assertTrue("DFA must build successfully with 1 pattern", dfaBuildResult is com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success)
        val dfa = (dfaBuildResult as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa
        assertEquals(1, dfa.patternCount)

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse("DFA build success alone must NOT unlock strict routing", context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 26 - caller cannot bypass readiness gate via constructor or copy`() {
        val constructors = RescueRoutingContext::class.java.constructors
        for (ctor in constructors) {
            val paramTypes = ctor.parameterTypes
            assertFalse(
                "RescueRoutingContext constructor must not accept Boolean permission parameter",
                paramTypes.any { it == Boolean::class.javaPrimitiveType || it == java.lang.Boolean::class.java }
            )
        }

        val manifest = createSampleManifest(unsupportedRestrictionsCount = 5, unverifiedEdges = 1000L)
        val roadGraph = createSampleRoadGraph(manifest)
        val index = TurnRestrictionIndex.build(roadGraph)
        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse(context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 27 - legacy ready graph cannot supply production authority`() {
        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsCount = 0,
            unverifiedEdges = 0L,
            verifiedEdges = 500L
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsCount = 0,
            unverifiedEdges = 0L,
            verifiedEdges = 500L,
            turnRestrictions = emptyList()
        )
        val index = TurnRestrictionIndex.build(roadGraph)
        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        assertFalse(context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 28 - snapshot reader never observes new graph with old context or new manifest with old graph`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        // Nạp v1
        val manifestV1 = createSampleManifest(graphVersion = "dn_v1")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV1))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock_v1.json"),
            graphVersion = manifestV1.graphVersion,
            fromCache = false
        )
        val graphV1 = createSampleRoadGraph(manifestV1)
        parser.parseResult = graphV1

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        loader.loadGraph(forceRefresh = false)

        val snap1 = loader.snapshot.value
        assertEquals("dn_v1", snap1.graph?.graphVersion)
        assertEquals("dn_v1", snap1.manifest?.graphVersion)
        assertEquals("dn_v1", snap1.context?.graphVersion)
        assertSame(snap1.graph, loader.currentGraph)
        assertSame(snap1.manifest, loader.currentManifest)
        assertSame(snap1.context, loader.currentContext)

        // Nạp v2
        val manifestV2 = createSampleManifest(graphVersion = "dn_v2")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV2))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock_v2.json"),
            graphVersion = manifestV2.graphVersion,
            fromCache = false
        )
        val graphV2 = createSampleRoadGraph(manifestV2)
        parser.parseResult = graphV2

        loader.loadGraph(forceRefresh = true)

        val snap2 = loader.snapshot.value
        assertEquals("dn_v2", snap2.graph?.graphVersion)
        assertEquals("dn_v2", snap2.manifest?.graphVersion)
        assertEquals("dn_v2", snap2.context?.graphVersion)
        assertSame(snap2.graph, loader.currentGraph)
        assertSame(snap2.manifest, loader.currentManifest)
        assertSame(snap2.context, loader.currentContext)

        assertNotEquals(snap1.graph?.graphVersion, snap2.graph?.graphVersion)
        assertSame(graphV1, snap1.graph)
        assertSame(graphV2, snap2.graph)
    }

    @Test
    fun `test 29 - in-flight reader holding previous snapshot is immune to subsequent graph refresh`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifestV1 = createSampleManifest(graphVersion = "dn_v1")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV1))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock_v1.json"),
            graphVersion = manifestV1.graphVersion,
            fromCache = false
        )
        val graphV1 = createSampleRoadGraph(manifestV1)
        parser.parseResult = graphV1

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        loader.loadGraph(forceRefresh = false)

        val heldSnapshot = loader.snapshot.value
        assertNotNull(heldSnapshot.graph)
        assertEquals("dn_v1", heldSnapshot.graph?.graphVersion)

        val manifestV2 = createSampleManifest(graphVersion = "dn_v2")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV2))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock_v2.json"),
            graphVersion = manifestV2.graphVersion,
            fromCache = false
        )
        val graphV2 = createSampleRoadGraph(manifestV2)
        parser.parseResult = graphV2

        loader.loadGraph(forceRefresh = true)

        assertEquals("dn_v1", heldSnapshot.graph?.graphVersion)
        assertEquals("dn_v1", heldSnapshot.manifest?.graphVersion)
        assertSame(graphV1, heldSnapshot.graph)
        assertEquals("dn_v2", loader.snapshot.value.graph?.graphVersion)
    }

    @Test
    fun `test 30 - clearLoadedGraph atomically clears snapshot and all delegate properties`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        loader.loadGraph(forceRefresh = false)
        assertTrue(loader.snapshot.value.isLoaded)

        loader.clearLoadedGraph()

        assertEquals(GraphLoaderSnapshot.Idle, loader.snapshot.value)
        assertNull(loader.currentGraph)
        assertNull(loader.currentManifest)
        assertNull(loader.currentContext)
        assertEquals(GraphLoaderState.Idle, loader.state.value)
        assertFalse(loader.isStrictRoutingPermitted())
    }

    @Test
    fun `test 31 - deep immutability audit confirms no public mutation path for graph, index, or dfa`() {
        val manifest = createSampleManifest()
        val roadGraph = createSampleRoadGraph(manifest)
        val index = TurnRestrictionIndex.build(roadGraph)
        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa

        // 1. RoadGraph collections are read-only: verifying no public mutating methods exist
        val methods = roadGraph::class.java.methods.map { it.name }
        assertFalse(methods.contains("setNodes"))
        assertFalse(methods.contains("setDirectedEdges"))
        assertFalse(methods.contains("setTurnRestrictions"))
        assertFalse(methods.contains("clear"))
        assertFalse(methods.contains("add"))

        // 2. TurnRestrictionIndex has no mutable methods
        assertFalse(index.hasConflicts)
        assertEquals(0, index.conflicts.size)
        val indexMethods = index::class.java.methods.map { it.name }
        assertFalse(indexMethods.contains("addRestriction"))
        assertFalse(indexMethods.contains("removeRestriction"))

        // 3. ViaWayTurnRestrictionDfa DfaState is immutable
        val rootState = dfa.getState(0)
        assertNotNull(rootState)
        val stateMethods = rootState!!::class.java.methods.map { it.name }
        assertFalse(stateMethods.contains("setTransitions"))
        assertFalse(stateMethods.contains("setActiveOnlyObligations"))
    }

    @Test
    fun `test 32 - loader state is purely derived from snapshot without independent mutable state`() {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        assertSame(loader.snapshot.value.state, loader.state.value)

        // Kiểm tra Reflection: DefaultRoutingGraphLoader hoàn toàn KHÔNG có trường nào tên _state
        val fields = DefaultRoutingGraphLoader::class.java.declaredFields.map { it.name }
        assertFalse("DefaultRoutingGraphLoader must NOT maintain an independent _state field", fields.contains("_state"))
    }

    @Test
    fun `test 33 - context is created with actual verified checksum from downloader`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        val expectedSha = manifest.bundle.sha256
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false,
            verifiedSha256 = expectedSha
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        assertNotNull(loader.currentContext)
        assertEquals(expectedSha, loader.currentContext!!.artifactSha256)
        assertEquals(expectedSha, loader.snapshot.value.context!!.artifactSha256)
    }

    @Test
    fun `test 34 - downloader checksum mismatch prevents context creation and records failure`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        // Downloader trả về SHA không khớp với manifest
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false,
            verifiedSha256 = "corrupted_checksum_value_12345"
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Failure)
        val failure = result as GraphLoaderResult.Failure
        assertEquals(GraphLoaderErrorKind.CONTRADICTORY_READINESS, failure.error.kind)
        assertTrue(failure.error.technicalMessage.contains("Artifact verification failed"))
        assertNull(loader.currentContext)
        assertNull(loader.snapshot.value.context)
        assertTrue(loader.state.value is GraphLoaderState.Failed)
    }

    @Test
    fun `test 35 - blank or missing verified checksum prevents context creation`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        downloader.autoFillSha256 = false
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = manifest.graphVersion,
            fromCache = false,
            verifiedSha256 = "   " // Checksum rỗng hoặc trắng
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Failure)
        assertNull(loader.currentContext)
        assertNull(loader.snapshot.value.context)
    }

    @Test
    fun `test 36 - context constructor requires verified sha without default fallback`() {
        val ctor = RescueRoutingContext::class.java.constructors.first()
        assertEquals(5, ctor.parameterTypes.size)

        val manifest = createSampleManifest()
        val roadGraph = createSampleRoadGraph(manifest)
        val index = TurnRestrictionIndex.build(roadGraph)
        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa

        try {
            RescueRoutingContext(
                roadGraph = roadGraph,
                turnRestrictionIndex = index,
                viaWayDfa = dfa,
                manifest = manifest,
                artifactSha256 = ""
            )
            org.junit.Assert.fail("Empty artifactSha256 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("artifactSha256 must not be blank"))
        }

        try {
            RescueRoutingContext(
                roadGraph = roadGraph,
                turnRestrictionIndex = index,
                viaWayDfa = dfa,
                manifest = manifest,
                artifactSha256 = "mismatched_sha_123"
            )
            org.junit.Assert.fail("Mismatched artifactSha256 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("does not match manifest bundle sha256"))
        }
    }

    @Test
    fun `test 37 - refresh graph with new checksum replaces artifact identity and rejects old identity`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        // v1
        val manifestV1 = createSampleManifest(graphVersion = "dn_v1")
        val shaV1 = manifestV1.bundle.sha256
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV1))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock_v1.json"),
            graphVersion = manifestV1.graphVersion,
            fromCache = false,
            verifiedSha256 = shaV1
        )
        parser.parseResult = createSampleRoadGraph(manifestV1)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        loader.loadGraph(forceRefresh = false)

        val contextV1 = loader.currentContext!!
        assertEquals(shaV1, contextV1.artifactSha256)

        // v2
        val manifestV2 = createSampleManifest(graphVersion = "dn_v2").let {
            it.copy(bundle = it.bundle.copy(sha256 = "new_verified_sha256_bundle_v2"))
        }
        val shaV2 = manifestV2.bundle.sha256
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifestV2))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock_v2.json"),
            graphVersion = manifestV2.graphVersion,
            fromCache = false,
            verifiedSha256 = shaV2
        )
        parser.parseResult = createSampleRoadGraph(manifestV2)

        loader.loadGraph(forceRefresh = true)

        val contextV2 = loader.currentContext!!
        assertEquals(shaV2, contextV2.artifactSha256)
        assertNotEquals(shaV1, contextV2.artifactSha256)
    }

    @Test
    fun `test 38 - mutating source collection after passing to graph constructor does not affect graph`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n1", lengthMeters = 50.0)

        val mutableEdges = ArrayList<RoadEdge>()
        mutableEdges.add(e1)

        val graph = RoadGraph(
            nodesList = listOf(n1),
            directedEdges = mutableEdges
        )

        assertEquals(1, graph.directedEdges.size)

        // Xóa collection bên ngoài
        mutableEdges.clear()

        try {
            (graph.directedEdges as MutableList<RoadEdge>).add(e1)
            org.junit.Assert.fail("graph.directedEdges must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi ném UnsupportedOperationException
        }
    }

    @Test
    fun `test 39 - public graph collections cannot be mutated via cast to MutableCollection`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n1", lengthMeters = 50.0)

        val graph = RoadGraph(
            nodesList = listOf(n1),
            directedEdges = listOf(e1)
        )

        try {
            (graph.directedEdges as MutableList<RoadEdge>).clear()
            org.junit.Assert.fail("Must throw UnsupportedOperationException on mutating directedEdges")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        try {
            (graph.nodes as MutableMap<String, RoadNode>).clear()
            org.junit.Assert.fail("Must throw UnsupportedOperationException on mutating nodes")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        try {
            (graph.outgoingEdgesByNodeId as MutableMap<String, List<RoadEdge>>).clear()
            org.junit.Assert.fail("Must throw UnsupportedOperationException on mutating outgoingEdgesByNodeId")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }
    }

    @Test
    fun `test 40 - dfa transition mappings and active obligations cannot be mutated`() {
        val pattern = com.example.bai1.rescue.routing.algorithm.ViaWayPattern(
            relationId = "restr_only",
            restrictionType = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2")
        )

        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa
        val state = dfa.getState(0)!!

        try {
            (state.transitions as MutableMap<String, Int>)["e1"] = 999
            org.junit.Assert.fail("Must throw UnsupportedOperationException on mutating transitions")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        try {
            (state.activeOnlyObligations as MutableList<*>).clear()
            org.junit.Assert.fail("Must throw UnsupportedOperationException on mutating activeOnlyObligations")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        try {
            (state.prohibitedPatternIds as MutableList<*>).clear()
            org.junit.Assert.fail("Must throw UnsupportedOperationException on mutating prohibitedPatternIds")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }
    }

    @Test
    fun `test 41 - concurrent readers cannot alter shared graph or dfa state`() = runTest {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 50.0)
        val e2 = RoadEdge(id = "e2", fromNodeId = "n2", toNodeId = "n1", lengthMeters = 50.0)

        val graph = RoadGraph(
            nodesList = listOf(n1, n2),
            directedEdges = listOf(e1, e2)
        )
        val index = TurnRestrictionIndex.build(graph)
        val dfa = (ViaWayTurnRestrictionDfa.build(graph.turnRestrictions, graph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa

        val jobs = (1..20).map {
            async(Dispatchers.Default) {
                for (step in 0 until 100) {
                    val out = graph.getOutgoingEdges("n1")
                    assertEquals(1, out.size)
                    val transRes = dfa.evaluateTransition(0, "e1")
                    assertNotNull(transRes)
                    val checkRes = index.checkTransition(null, "e1", "n1")
                    assertTrue(checkRes.isAllowed)
                }
            }
        }
        jobs.forEach { it.await() }

        assertEquals(2, graph.directedEdges.size)
        assertEquals(0, dfa.patternCount)
    }

    @Test
    fun `test 42 - artifact graphVersion mismatch against manifest is rejected even if fromCache is true`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        // Downloader trả về artifact có graphVersion không khớp với manifest
        downloader.downloadResult = GraphDownloadResult.Success(
            file = File("/tmp/mock.json"),
            graphVersion = "mismatched_graph_version_v999",
            fromCache = true,
            verifiedSha256 = manifest.bundle.sha256
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Failure)
        val failure = result as GraphLoaderResult.Failure
        assertEquals(GraphLoaderErrorKind.VERSION_MISMATCH, failure.error.kind)
        assertTrue(failure.error.technicalMessage.contains("mismatched_graph_version_v999"))
        assertNull(loader.currentContext)
        assertNull(loader.snapshot.value.context)
    }

    @Test
    fun `test 43 - verified artifact passed to parser matches exact path and properties of download result`() = runTest {
        val repo = FakeRoutingRepository()
        val downloader = FakeGraphDownloader()
        val parser = FakeRoadGraphParser()

        val manifest = createSampleManifest()
        val mockFile = File("/tmp/specific_mock_graph_${manifest.graphVersion}.json")
        repo.statusResult = RoutingApiResult.Success(createSampleStatusResponse(manifest = manifest))
        downloader.downloadResult = GraphDownloadResult.Success(
            file = mockFile,
            graphVersion = manifest.graphVersion,
            fromCache = true,
            verifiedSha256 = manifest.bundle.sha256
        )
        parser.parseResult = createSampleRoadGraph(manifest)

        val loader = DefaultRoutingGraphLoader(repo, downloader, parser, Dispatchers.Unconfined)
        val result = loader.loadGraph(forceRefresh = false)

        assertTrue(result is GraphLoaderResult.Success)
        assertEquals(mockFile, parser.lastPassedFile)
        assertEquals(manifest, parser.lastPassedManifest)
    }

    @Test
    fun `test 44 - mutable source nodes map mutated after RoadGraph creation does not affect graph`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val sourceNodesMap = LinkedHashMap<String, RoadNode>()
        sourceNodesMap["n1"] = n1

        val graph = RoadGraph(
            nodes = sourceNodesMap,
            directedEdges = emptyList()
        )

        assertEquals(1, graph.nodes.size)
        assertTrue(graph.hasNode("n1"))
        assertFalse(graph.hasNode("n2"))

        // Thêm phần tử vào source map bên ngoài
        sourceNodesMap["n2"] = n2

        // Graph không được thay đổi!
        assertEquals(1, graph.nodes.size)
        assertFalse(graph.hasNode("n2"))
    }

    @Test
    fun `test 45 - mutable source edges list mutated after RoadGraph creation does not affect graph`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n1", lengthMeters = 50.0)
        val e2 = RoadEdge(id = "e2", fromNodeId = "n1", toNodeId = "n1", lengthMeters = 60.0)
        val sourceEdgesList = ArrayList<RoadEdge>()
        sourceEdgesList.add(e1)

        val graph = RoadGraph(
            nodes = mapOf("n1" to n1),
            directedEdges = sourceEdgesList
        )

        assertEquals(1, graph.directedEdges.size)

        // Thêm cạnh mới vào source list bên ngoài
        sourceEdgesList.add(e2)

        // Graph không bị ảnh hưởng!
        assertEquals(1, graph.directedEdges.size)
    }

    @Test
    fun `test 46 - mutable source turnRestrictions list mutated after RoadGraph creation does not affect graph`() {
        val restr1 = TurnRestriction(
            id = "r1",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(20L)
        )
        val restr2 = TurnRestriction(
            id = "r2",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            fromOsmWayIds = listOf(30L),
            toOsmWayIds = listOf(40L)
        )
        val sourceRestrictions = ArrayList<TurnRestriction>()
        sourceRestrictions.add(restr1)

        val graph = RoadGraph(
            nodes = emptyMap(),
            directedEdges = emptyList(),
            turnRestrictions = sourceRestrictions
        )

        assertEquals(1, graph.turnRestrictions.size)

        // Sửa source list bên ngoài
        sourceRestrictions.add(restr2)

        // Graph không đổi!
        assertEquals(1, graph.turnRestrictions.size)
    }

    @Test
    fun `test 47 - nested lists in TurnRestriction are isolated from source references`() {
        val mutableWays = ArrayList<Long>()
        mutableWays.add(100L)
        val mutableEdges = ArrayList<String>()
        mutableEdges.add("e1")
        val mutableMembers = ArrayList<OsmRelationMember>()
        mutableMembers.add(OsmRelationMember("way", 100L, "from"))

        val restr = TurnRestriction(
            id = "r1",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromOsmWayIds = mutableWays,
            orderedDirectedEdgeIds = mutableEdges,
            members = mutableMembers
        )

        val graph = RoadGraph(
            nodes = emptyMap(),
            directedEdges = emptyList(),
            turnRestrictions = listOf(restr)
        )

        val graphRestr = graph.turnRestrictions.first()
        assertEquals(1, graphRestr.fromOsmWayIds.size)
        assertEquals(1, graphRestr.orderedDirectedEdgeIds.size)
        assertEquals(1, graphRestr.members.size)

        // Mutate source collections bên ngoài
        mutableWays.add(999L)
        mutableEdges.add("e999")
        mutableMembers.clear()

        // Nested lists trong graph không đổi!
        assertEquals(1, graphRestr.fromOsmWayIds.size)
        assertEquals(1, graphRestr.orderedDirectedEdgeIds.size)
        assertEquals(1, graphRestr.members.size)

        // Kiểm tra ép kiểu mutate nested lists ném ngoại lệ
        try {
            (graphRestr.fromOsmWayIds as MutableList<Long>).add(888L)
            org.junit.Assert.fail("Nested list must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }
    }

    @Test
    fun `test 48 - nested maps and lists in RoadEdge are isolated from source references`() {
        val mutableGeometry = ArrayList<GeoPoint>()
        mutableGeometry.add(GeoPoint(16.0, 108.0))
        val mutableTags = LinkedHashMap<String, String>()
        mutableTags["maxspeed"] = "50"
        val mutableConflicts = ArrayList<String>()
        mutableConflicts.add("speed_conflict")

        val edge = RoadEdge(
            id = "e1",
            fromNodeId = "n1",
            toNodeId = "n2",
            lengthMeters = 100.0,
            geometry = mutableGeometry,
            metadata = EdgeMetadata(
                rawTags = mutableTags,
                tagConflicts = mutableConflicts
            )
        )

        val graph = RoadGraph(
            nodes = emptyMap(),
            directedEdges = listOf(edge)
        )

        val graphEdge = graph.directedEdges.first()
        assertEquals(1, graphEdge.geometry.size)
        assertEquals(1, graphEdge.metadata.rawTags.size)
        assertEquals(1, graphEdge.metadata.tagConflicts.size)

        // Mutate source collections bên ngoài
        mutableGeometry.clear()
        mutableTags["new_tag"] = "value"
        mutableConflicts.add("another_conflict")

        // Nested collections trong graph không bị ảnh hưởng!
        assertEquals(1, graphEdge.geometry.size)
        assertEquals(1, graphEdge.metadata.rawTags.size)
        assertEquals(1, graphEdge.metadata.tagConflicts.size)

        // Thử cast và mutate trực tiếp
        try {
            (graphEdge.geometry as MutableList<GeoPoint>).clear()
            org.junit.Assert.fail("geometry must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }
        try {
            (graphEdge.metadata.rawTags as MutableMap<String, String>)["hack"] = "fail"
            org.junit.Assert.fail("rawTags must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }
    }

    // ==========================================
    // TASK 4B.3: AUDIT STRICT READINESS & DELEGATION TESTS (49 - 56)
    // ==========================================

    @Test
    fun `test 49 - COMPILED and DFA accepted and Index delegated via-way is not counted as runtime unresolved`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val n3 = RoadNode("n3", GeoPoint(16.07, 108.22), 1003L)
        val n4 = RoadNode("n4", GeoPoint(16.08, 108.23), 1004L)
        val nodes = mapOf("n1" to n1, "n2" to n2, "n3" to n3, "n4" to n4)

        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)
        val e2 = RoadEdge(id = "e2", fromNodeId = "n2", toNodeId = "n3", lengthMeters = 100.0, osmWayId = 20L)
        val e3 = RoadEdge(id = "e3", fromNodeId = "n3", toNodeId = "n4", lengthMeters = 100.0, osmWayId = 30L)

        // Restriction có metadata cũ supportStatus = FAIL_CLOSED_UNRESOLVED nhưng đã COMPILED
        val compiledRestriction = TurnRestriction(
            id = "rel_compiled_valid",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(20L),
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(30L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )

        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            unsupportedRestrictionsCount = 1,
            unverifiedEdges = 0L,
            verifiedEdges = 300L
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            nodes = nodes,
            directedEdges = listOf(e1, e2, e3),
            turnRestrictions = listOf(compiledRestriction),
            unsupportedRestrictionsCount = 1,
            unverifiedEdges = 0L,
            verifiedEdges = 300L
        )

        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertTrue(context.isRestrictionResolvedAtRuntime(compiledRestriction))
        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        assertFalse("Compiled restrictions cannot grant production authority", context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 50 - COMPILED via-way but DFA not accepted remains unresolved`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val nodes = mapOf("n1" to n1, "n2" to n2)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)

        // Sequence chỉ có 1 cạnh -> DFA builder từ chối
        val badSequenceRestriction = TurnRestriction(
            id = "rel_short",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(10L),
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(10L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1")
        )

        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsCount = 1,
            unverifiedEdges = 0L,
            verifiedEdges = 100L
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            nodes = nodes,
            directedEdges = listOf(e1),
            turnRestrictions = listOf(badSequenceRestriction),
            unsupportedRestrictionsCount = 1,
            unverifiedEdges = 0L,
            verifiedEdges = 100L
        )

        val emptyDfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, emptyDfa.acceptedRestrictionIds)

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = emptyDfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse(context.isRestrictionResolvedAtRuntime(badSequenceRestriction))
        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        assertFalse(context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 51 - UNRESOLVED sequence remains unresolved`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val nodes = mapOf("n1" to n1, "n2" to n2)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)

        val unresolvedRestriction = TurnRestriction(
            id = "rel_unresolved",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(20L),
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(30L),
            viaWayCompilationStatus = ViaWayCompilationStatus.UNRESOLVED,
            orderedDirectedEdgeIds = emptyList()
        )

        val manifest = createSampleManifest(unsupportedRestrictionsCount = 1)
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            nodes = nodes,
            directedEdges = listOf(e1),
            turnRestrictions = listOf(unresolvedRestriction),
            unsupportedRestrictionsCount = 1
        )

        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)
        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse(context.isRestrictionResolvedAtRuntime(unresolvedRestriction))
        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        assertFalse(context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 52 - conditional restriction remains unresolved`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val nodes = mapOf("n1" to n1, "n2" to n2)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)

        val conditionalRestriction = TurnRestriction(
            id = "rel_conditional",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(20L),
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(30L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1"),
            conditionalExpression = "delivery @ (10:00-16:00)"
        )

        val manifest = createSampleManifest(unsupportedRestrictionsCount = 1)
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            nodes = nodes,
            directedEdges = listOf(e1),
            turnRestrictions = listOf(conditionalRestriction),
            unsupportedRestrictionsCount = 1
        )

        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)
        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse(context.isRestrictionResolvedAtRuntime(conditionalRestriction))
        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        assertFalse(context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 53 - via-way DFA accepted but index not delegated is rejected by context`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val n3 = RoadNode("n3", GeoPoint(16.07, 108.22), 1003L)
        val n4 = RoadNode("n4", GeoPoint(16.08, 108.23), 1004L)
        val nodes = mapOf("n1" to n1, "n2" to n2, "n3" to n3, "n4" to n4)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)
        val e2 = RoadEdge(id = "e2", fromNodeId = "n2", toNodeId = "n3", lengthMeters = 100.0, osmWayId = 20L)
        val e3 = RoadEdge(id = "e3", fromNodeId = "n3", toNodeId = "n4", lengthMeters = 100.0, osmWayId = 30L)

        val restr = TurnRestriction(
            id = "rel_accepted",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(20L),
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(30L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )

        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            unsupportedRestrictionsCount = 1
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            nodes = nodes,
            directedEdges = listOf(e1, e2, e3),
            turnRestrictions = listOf(restr),
            unsupportedRestrictionsCount = 1
        )

        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa
        assertEquals(setOf("rel_accepted"), dfa.acceptedRestrictionIds)

        // Không truyền delegated ids vào TurnRestrictionIndex (mismatch)
        val index = TurnRestrictionIndex.build(roadGraph, delegatedViaWayRestrictionIds = emptySet())

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        val denied = context.readinessEvaluation as StrictRoutingEvaluation.Denied
        assertEquals(RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS, denied.reason)
        assertTrue(denied.detailMessage.contains("Delegation mismatch"))
        assertFalse(context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 54 - index delegated ID not in DFA is rejected by context`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val nodes = mapOf("n1" to n1, "n2" to n2)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)

        val manifest = createSampleManifest(
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            unsupportedRestrictionsCount = 0
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            nodes = nodes,
            directedEdges = listOf(e1),
            turnRestrictions = emptyList(),
            unsupportedRestrictionsCount = 0
        )

        val dfa = ViaWayTurnRestrictionDfa.empty()
        // Index có delegated ID không tồn tại trong DFA
        val index = object : TurnRestrictionIndex by TurnRestrictionIndex.build(roadGraph) {
            override val delegatedViaWayRestrictionIds: Set<String> = setOf("phantom_restr")
        }

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        val denied = context.readinessEvaluation as StrictRoutingEvaluation.Denied
        assertEquals(RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS, denied.reason)
        assertFalse(context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 55 - manifest disallowing strict routing keeps context denied even if all DFA patterns are accepted`() {
        val n1 = RoadNode("n1", GeoPoint(16.05, 108.20), 1001L)
        val n2 = RoadNode("n2", GeoPoint(16.06, 108.21), 1002L)
        val n3 = RoadNode("n3", GeoPoint(16.07, 108.22), 1003L)
        val n4 = RoadNode("n4", GeoPoint(16.08, 108.23), 1004L)
        val nodes = mapOf("n1" to n1, "n2" to n2, "n3" to n3, "n4" to n4)
        val e1 = RoadEdge(id = "e1", fromNodeId = "n1", toNodeId = "n2", lengthMeters = 100.0, osmWayId = 10L)
        val e2 = RoadEdge(id = "e2", fromNodeId = "n2", toNodeId = "n3", lengthMeters = 100.0, osmWayId = 20L)
        val e3 = RoadEdge(id = "e3", fromNodeId = "n3", toNodeId = "n4", lengthMeters = 100.0, osmWayId = 30L)

        val restr = TurnRestriction(
            id = "rel_accepted_55",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(20L),
            fromOsmWayIds = listOf(10L),
            toOsmWayIds = listOf(30L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )

        // canAttemptStrictRouting = false từ manifest
        val manifest = createSampleManifest(
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsCount = 1
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            nodes = nodes,
            directedEdges = listOf(e1, e2, e3),
            turnRestrictions = listOf(restr),
            unsupportedRestrictionsCount = 1
        )

        val dfa = (ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph) as com.example.bai1.rescue.routing.algorithm.DfaBuildResult.Success).dfa
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        val denied = context.readinessEvaluation as StrictRoutingEvaluation.Denied
        assertEquals(RoutingDisabledReason.MANIFEST_STRICT_ROUTING_DISABLED, denied.reason)
        assertFalse(context.isStrictRoutingPermitted)
    }

    @Test
    fun `test 56 - production Da Nang remains strict routing disabled due to unmet conditions`() {
        // Mô phỏng đúng các thông số thực tế của graph Đà Nẵng production
        val manifest = createSampleManifest(
            graphVersion = "dn_central_real_v1",
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsCount = 14,
            unverifiedEdges = 16651L,
            verifiedEdges = 3521L
        )
        val roadGraph = createSampleRoadGraph(
            manifest = manifest,
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsCount = 14,
            unverifiedEdges = 16651L,
            verifiedEdges = 3521L
        )

        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertTrue("Da Nang production context must be Denied", context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        assertFalse("Strict routing must remain locked for production Da Nang", context.isStrictRoutingPermitted)
    }
}
