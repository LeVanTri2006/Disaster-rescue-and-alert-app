package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.download.GraphDownloadError
import com.example.bai1.rescue.routing.download.GraphDownloadErrorKind
import com.example.bai1.rescue.routing.download.GraphDownloadResult
import com.example.bai1.rescue.routing.download.GraphDownloadState
import com.example.bai1.rescue.routing.download.GraphDownloader
import com.example.bai1.rescue.routing.model.EdgeSafetySummary
import com.example.bai1.rescue.routing.model.GraphDownloadInfo
import com.example.bai1.rescue.routing.model.RoutingBundleInfo
import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.RoutingStatusResponse
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.UnsupportedRestrictionsSummary
import com.example.bai1.rescue.routing.network.RoutingApiClient
import com.example.bai1.rescue.routing.network.RoutingApiError
import com.example.bai1.rescue.routing.network.RoutingApiErrorKind
import com.example.bai1.rescue.routing.network.RoutingApiResult
import com.example.bai1.rescue.routing.repository.DefaultRoutingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutingRepositoryTest {

    private fun createSampleRoutingResponse(
        downloadAvailable: Boolean = true,
        strictRoutingReady: Boolean = false,
        liveHazardReady: Boolean = false,
        canAttemptStrictRouting: Boolean = false,
        downloadUrl: String = "https://pub-r2.example.com/graphs/dn_central_real_v1/BaseRoadGraph.json.gz?signature=mocked"
    ): RoutingStatusResponse {
        val manifest = RoutingManifest(
            schema = "https://emergency-rescue.vn/schemas/v2/routing-manifest.json",
            manifestVersion = "2.1.0",
            generatedAt = "2026-11-04T12:24:50.392Z",
            graphVersion = "dn_central_real_v1",
            osmSnapshotTimestamp = "2026-11-03T21:24:23Z",
            importerVersion = "osm-importer-v2.3-prototype",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            bundle = RoutingBundleInfo(
                fileName = "BaseRoadGraph.json.gz",
                compressedSizeBytes = 1173443L,
                uncompressedSizeBytes = 33002111L,
                sha256 = "9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b"
            ),
            hazardSnapshotStatus = if (liveHazardReady) "ATTACHED" else "NOT_INCLUDED",
            hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            isRestrictionStrictRoutingEligible = strictRoutingReady,
            restrictionStrictRoutingReadiness = if (strictRoutingReady) StrictRoutingReadiness.READY else StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(totalCount = 16),
            canAttemptStrictRouting = canAttemptStrictRouting,
            overallStrictRoutingReadiness = if (strictRoutingReady) StrictRoutingReadiness.READY else StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            edgeSafetySummary = EdgeSafetySummary(
                totalEdges = 16651L,
                verifiedCompatibleEdges = if (strictRoutingReady) 16651L else 0L,
                unverifiedEdges = if (strictRoutingReady) 0L else 16651L,
                blockedEdges = 0L,
                conflictedEdges = 0L,
                strictSafeEdgesPercent = if (strictRoutingReady) 100.0 else 0.0,
                blockedWays = 1425L
            )
        )

        return RoutingStatusResponse(
            manifest = manifest,
            download = GraphDownloadInfo(
                url = downloadUrl,
                expiresIn = 900L
            ),
            downloadAvailable = downloadAvailable,
            strictRoutingReady = strictRoutingReady,
            liveHazardReady = liveHazardReady,
            canAttemptStrictRouting = canAttemptStrictRouting,
            overallStrictRoutingReadiness = if (strictRoutingReady) StrictRoutingReadiness.READY else StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS
        )
    }

    private class FakeRoutingApiClient(
        var responseToReturn: RoutingApiResult<RoutingStatusResponse>
    ) : RoutingApiClient {
        var callCount = 0
        override suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse> {
            callCount++
            return responseToReturn
        }
    }

    private class FakeGraphDownloader : GraphDownloader {
        private val _state = MutableStateFlow<GraphDownloadState>(GraphDownloadState.Idle)
        override val state: StateFlow<GraphDownloadState> = _state.asStateFlow()

        var callCount = 0
        var resultsToReturn = mutableListOf<GraphDownloadResult>()
        var cached = false
        var cachedFile: File? = null

        override suspend fun downloadAndPrepareGraph(
            statusResponse: RoutingStatusResponse,
            forceRefresh: Boolean
        ): GraphDownloadResult {
            callCount++
            return if (resultsToReturn.isNotEmpty()) {
                resultsToReturn.removeAt(0)
            } else {
                GraphDownloadResult.Success(File("/tmp/test.json"), statusResponse.manifest.graphVersion)
            }
        }

        override fun isGraphCached(manifest: RoutingManifest): Boolean = cached
        override fun getCachedGraphFile(manifest: RoutingManifest): File? = cachedFile
        override fun clearCache(graphVersion: String?) {
            cached = false
            cachedFile = null
        }
    }

    @Test
    fun `successful routing fetch updates currentRoutingStatus StateFlow and clears errors`() = runTest {
        val mockResponse = createSampleRoutingResponse(
            downloadAvailable = true,
            strictRoutingReady = false
        )
        val fakeClient = FakeRoutingApiClient(RoutingApiResult.Success(mockResponse))
        val repository = DefaultRoutingRepository(fakeClient)

        // Initial state
        assertNull(repository.currentRoutingStatus.value)
        assertFalse(repository.isLoading.value)
        assertNull(repository.lastError.value)

        val result = repository.getCurrentRouting()

        assertTrue(result is RoutingApiResult.Success)
        val data = (result as RoutingApiResult.Success).data
        assertEquals("dn_central_real_v1", data.manifest.graphVersion)
        assertEquals(1173443L, data.manifest.bundle.compressedSizeBytes)
        assertEquals("9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b", data.manifest.bundle.sha256)
        assertTrue(data.downloadAvailable)
        assertFalse(data.strictRoutingReady)

        // StateFlow updated
        val flowValue = repository.currentRoutingStatus.first()
        assertNotNull(flowValue)
        assertEquals("dn_central_real_v1", flowValue!!.manifest.graphVersion)
        assertFalse(repository.isLoading.value)
        assertNull(repository.lastError.value)
    }

    @Test
    fun `http 401 unauthorized updates lastError StateFlow and leaves currentRoutingStatus null`() = runTest {
        val authError = RoutingApiError(
            kind = RoutingApiErrorKind.UNAUTHORIZED,
            technicalMessage = "HTTP 401 Unauthorized",
            httpCode = 401
        )
        val fakeClient = FakeRoutingApiClient(RoutingApiResult.Failure(authError))
        val repository = DefaultRoutingRepository(fakeClient)

        val result = repository.getCurrentRouting()

        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.UNAUTHORIZED, error.kind)
        assertEquals(401, error.httpCode)

        // StateFlow checks
        assertNull(repository.currentRoutingStatus.value)
        assertEquals(RoutingApiErrorKind.UNAUTHORIZED, repository.lastError.value?.kind)
        assertFalse(repository.isLoading.value)
    }

    @Test
    fun `http 403 forbidden updates lastError StateFlow distinctly`() = runTest {
        val forbiddenError = RoutingApiError(
            kind = RoutingApiErrorKind.FORBIDDEN,
            technicalMessage = "HTTP 403 Forbidden - Rescuer role required",
            httpCode = 403
        )
        val fakeClient = FakeRoutingApiClient(RoutingApiResult.Failure(forbiddenError))
        val repository = DefaultRoutingRepository(fakeClient)

        val result = repository.getCurrentRouting()

        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.FORBIDDEN, error.kind)
        assertEquals(403, error.httpCode)
        assertEquals(RoutingApiErrorKind.FORBIDDEN, repository.lastError.value?.kind)
    }

    @Test
    fun `network error updates lastError StateFlow with NO_INTERNET`() = runTest {
        val networkError = RoutingApiError(
            kind = RoutingApiErrorKind.NO_INTERNET,
            technicalMessage = "No route to host"
        )
        val fakeClient = FakeRoutingApiClient(RoutingApiResult.Failure(networkError))
        val repository = DefaultRoutingRepository(fakeClient)

        val result = repository.getCurrentRouting()

        assertTrue(result is RoutingApiResult.Failure)
        val error = (result as RoutingApiResult.Failure).error
        assertEquals(RoutingApiErrorKind.NO_INTERNET, error.kind)
        assertEquals(RoutingApiErrorKind.NO_INTERNET, repository.lastError.value?.kind)
    }

    @Test
    fun `downloadAvailable true alone does NOT satisfy routing readiness`() = runTest {
        val response = createSampleRoutingResponse(
            downloadAvailable = true,
            strictRoutingReady = false,
            canAttemptStrictRouting = false
        )

        val fakeClient = FakeRoutingApiClient(RoutingApiResult.Success(response))
        val repository = DefaultRoutingRepository(fakeClient)

        val result = (repository.getCurrentRouting() as RoutingApiResult.Success).data

        assertTrue("Download bundle is available on R2", result.downloadAvailable)
        assertFalse("Strict routing must not be ready", result.strictRoutingReady)
        assertFalse("Cannot attempt strict routing without verification", result.canAttemptStrictRouting)
        assertFalse("Safe navigation rule: must not allow navigation", result.canNavigateSafely)
    }

    @Test
    fun `legacy ready metadata is projected to download only`() = runTest {
        val response = createSampleRoutingResponse(
            downloadAvailable = true,
            strictRoutingReady = true,
            canAttemptStrictRouting = true
        )

        val fakeClient = FakeRoutingApiClient(RoutingApiResult.Success(response))
        val repository = DefaultRoutingRepository(fakeClient)

        val result = (repository.getCurrentRouting() as RoutingApiResult.Success).data

        assertTrue(result.downloadAvailable)
        assertFalse(result.strictRoutingReady)
        assertFalse(result.canAttemptStrictRouting)
        assertFalse("Metadata cannot authorize navigation", result.canNavigateSafely)
    }

    @Test
    fun `downloadGraphWithAutoRefresh retries with fresh presigned URL when initial attempt fails with URL_EXPIRED`() = runTest {
        val initialResponse = createSampleRoutingResponse(downloadUrl = "https://r2.example.com/expired")
        val refreshedResponse = createSampleRoutingResponse(downloadUrl = "https://r2.example.com/fresh_url")

        val fakeClient = FakeRoutingApiClient(RoutingApiResult.Success(initialResponse))
        val fakeDownloader = FakeGraphDownloader()

        // 1st attempt fails with URL_EXPIRED, 2nd attempt succeeds
        fakeDownloader.resultsToReturn.add(
            GraphDownloadResult.Failure(
                GraphDownloadError(
                    kind = GraphDownloadErrorKind.URL_EXPIRED,
                    technicalMessage = "Presigned URL expired",
                    httpCode = 403
                )
            )
        )
        val expectedFile = File("/tmp/dn_central_real_v1_BaseRoadGraph.json")
        fakeDownloader.resultsToReturn.add(
            GraphDownloadResult.Success(expectedFile, "dn_central_real_v1")
        )

        val repository = DefaultRoutingRepository(fakeClient, fakeDownloader)

        // When downloading with auto refresh:
        // Client returns refreshed response when getCurrentRouting is called during retry
        fakeClient.responseToReturn = RoutingApiResult.Success(refreshedResponse)

        val result = repository.downloadGraphWithAutoRefresh()

        assertTrue("Should succeed after auto refresh", result is GraphDownloadResult.Success)
        assertEquals(expectedFile, (result as GraphDownloadResult.Success).file)
        assertEquals(2, fakeDownloader.callCount)
        assertEquals(2, fakeClient.callCount)
    }

    @Test
    fun `isCurrentGraphCached and getCachedGraphFile delegate properly to downloader`() = runTest {
        val mockResponse = createSampleRoutingResponse()
        val fakeClient = FakeRoutingApiClient(RoutingApiResult.Success(mockResponse))
        val fakeDownloader = FakeGraphDownloader()
        val repository = DefaultRoutingRepository(fakeClient, fakeDownloader)

        // Before loading routing status
        assertFalse(repository.isCurrentGraphCached())
        assertNull(repository.getCachedGraphFile())

        // Load routing status
        repository.getCurrentRouting()

        // Simulate cached graph
        val fakeFile = File("/cache/graph.json")
        fakeDownloader.cached = true
        fakeDownloader.cachedFile = fakeFile

        assertTrue(repository.isCurrentGraphCached())
        assertEquals(fakeFile, repository.getCachedGraphFile())
    }
}
