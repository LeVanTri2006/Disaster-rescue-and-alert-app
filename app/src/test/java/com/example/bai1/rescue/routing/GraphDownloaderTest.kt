package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.download.GraphDownloadErrorKind
import com.example.bai1.rescue.routing.download.GraphDownloadResult
import com.example.bai1.rescue.routing.download.GraphDownloadState
import com.example.bai1.rescue.routing.download.OkHttpGraphDownloader
import com.example.bai1.rescue.routing.model.EdgeSafetySummary
import com.example.bai1.rescue.routing.model.GraphDownloadInfo
import com.example.bai1.rescue.routing.model.RoutingBundleInfo
import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.RoutingStatusResponse
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.UnsupportedRestrictionsSummary
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class GraphDownloaderTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("test_routing_graphs").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun validGraph(): String = """{
      "contractVersion":"2.1.0","graphVersion":"dn_central_real_v1","vehicleProfile":"RESCUE_TRUCK_4X4",
      "nodes":[{"id":"a","lat":0,"lon":0},{"id":"b","lat":0,"lon":0.001}],
      "edges":[{"id":"ab","fromNodeId":"a","toNodeId":"b","lengthMeters":111,
        "geometry":[{"lat":0,"lon":0},{"lat":0,"lon":0.001}],"compatibilityStatus":"UNVERIFIED"}],
      "turnRestrictions":[]
    }""".trimIndent()

    private fun gzipString(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256").digest(bytes)
        return md.joinToString("") { "%02x".format(it) }
    }

    private fun createStatus(
        uncompressedContent: String,
        compressedBytes: ByteArray = gzipString(uncompressedContent),
        sha256Override: String? = null,
        compressedSizeOverride: Long? = null,
        uncompressedSizeOverride: Long? = null,
        downloadAvailable: Boolean = true,
        downloadUrl: String = "https://pub-r2.example.com/graphs/dn_central_real_v1/BaseRoadGraph.json.gz?signature=valid_sig"
    ): RoutingStatusResponse {
        val compressedSize = compressedSizeOverride ?: compressedBytes.size.toLong()
        val uncompressedSize = uncompressedSizeOverride ?: uncompressedContent.toByteArray(Charsets.UTF_8).size.toLong()
        val sha256 = sha256Override ?: sha256Hex(compressedBytes)

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
                compressedSizeBytes = compressedSize,
                uncompressedSizeBytes = uncompressedSize,
                sha256 = sha256
            ),
            hazardSnapshotStatus = "NOT_INCLUDED",
            hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            isRestrictionStrictRoutingEligible = false,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(totalCount = 16),
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            edgeSafetySummary = EdgeSafetySummary(
                totalEdges = 16651L,
                verifiedCompatibleEdges = 0L,
                unverifiedEdges = 16651L,
                blockedEdges = 0L,
                conflictedEdges = 0L,
                strictSafeEdgesPercent = 0.0,
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
            strictRoutingReady = false,
            liveHazardReady = false,
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS
        )
    }

    private fun buildMockClient(interceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @Test
    fun `1_successful download, sha256 verification and gzip decompression`() = runTest {
        val jsonContent = validGraph()
        val gzipBytes = gzipString(jsonContent)
        val status = createStatus(jsonContent, gzipBytes)

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val result = downloader.downloadAndPrepareGraph(status)

        assertTrue("Expected Success, got $result", result is GraphDownloadResult.Success)
        val success = result as GraphDownloadResult.Success
        assertFalse("Must not be from cache on first download", success.fromCache)
        assertEquals("dn_central_real_v1", success.graphVersion)
        assertTrue(success.file.exists())
        assertEquals("BaseRoadGraph.json", success.file.name)
        assertEquals(jsonContent, success.file.readText())

        // Verify state is Completed
        val state = downloader.state.value
        assertTrue(state is GraphDownloadState.Completed)
        assertEquals(jsonContent.toByteArray(Charsets.UTF_8).size.toLong(), (state as GraphDownloadState.Completed).uncompressedBytes)

        // Verify temp files are cleaned up
        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals("No temp files should remain after successful download", 0, tmpFiles.size)
    }

    @Test
    fun `2_does not download when downloadAvailable is false`() = runTest {
        var networkCalled = false
        val client = buildMockClient { chain ->
            networkCalled = true
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val status = createStatus("test", downloadAvailable = false)

        val result = downloader.downloadAndPrepareGraph(status)
        assertFalse("Network must not be called when downloadAvailable is false", networkCalled)
        assertTrue(result is GraphDownloadResult.Failure)
        val failure = result as GraphDownloadResult.Failure
        assertEquals(GraphDownloadErrorKind.PRECONDITION_FAILED, failure.error.kind)
    }

    @Test
    fun `3_missing or invalid download URL fails precondition`() = runTest {
        var networkCalled = false
        val client = buildMockClient { chain ->
            networkCalled = true
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val status = createStatus("test", downloadUrl = "   ")

        val result = downloader.downloadAndPrepareGraph(status)
        assertFalse(networkCalled)
        assertTrue(result is GraphDownloadResult.Failure)
        assertEquals(GraphDownloadErrorKind.PRECONDITION_FAILED, (result as GraphDownloadResult.Failure).error.kind)
    }

    @Test
    fun `4_http 403 maps to URL_EXPIRED and deletes temp files`() = runTest {
        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body("<?xml version=\"1.0\"?><Error><Code>AccessDenied</Code></Error>".toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val status = createStatus("test")

        val result = downloader.downloadAndPrepareGraph(status)
        assertTrue(result is GraphDownloadResult.Failure)
        val failure = result as GraphDownloadResult.Failure
        assertEquals(GraphDownloadErrorKind.URL_EXPIRED, failure.error.kind)
        assertEquals(403, failure.error.httpCode)

        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun `5_download timeout maps to DOWNLOAD_TIMEOUT and cleans up`() = runTest {
        val client = buildMockClient {
            throw SocketTimeoutException("Connection timed out after 30s")
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val status = createStatus("test")

        val result = downloader.downloadAndPrepareGraph(status)
        assertTrue(result is GraphDownloadResult.Failure)
        assertEquals(GraphDownloadErrorKind.DOWNLOAD_TIMEOUT, (result as GraphDownloadResult.Failure).error.kind)

        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun `6_no internet or IO failure maps to DOWNLOAD_FAILED and cleans up`() = runTest {
        val client = buildMockClient {
            throw IOException("Unable to resolve host r2.cloudflarestorage.com")
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val status = createStatus("test")

        val result = downloader.downloadAndPrepareGraph(status)
        assertTrue(result is GraphDownloadResult.Failure)
        assertEquals(GraphDownloadErrorKind.DOWNLOAD_FAILED, (result as GraphDownloadResult.Failure).error.kind)

        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun `7_compressed size mismatch fails with SIZE_MISMATCH and deletes temp file`() = runTest {
        val gzipBytes = gzipString("some test data")
        // Tamper compressedSizeBytes in manifest to expect 99999 bytes
        val status = createStatus("some test data", gzipBytes, compressedSizeOverride = 99999L)

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val result = downloader.downloadAndPrepareGraph(status)

        assertTrue(result is GraphDownloadResult.Failure)
        val failure = result as GraphDownloadResult.Failure
        assertEquals(GraphDownloadErrorKind.SIZE_MISMATCH, failure.error.kind)

        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun `8_sha256 mismatch fails with CHECKSUM_MISMATCH and deletes temp file`() = runTest {
        val gzipBytes = gzipString("authentic graph data")
        val fakeSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        val status = createStatus("authentic graph data", gzipBytes, sha256Override = fakeSha256)

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val result = downloader.downloadAndPrepareGraph(status)

        assertTrue(result is GraphDownloadResult.Failure)
        val failure = result as GraphDownloadResult.Failure
        assertEquals(GraphDownloadErrorKind.CHECKSUM_MISMATCH, failure.error.kind)

        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals(0, tmpFiles.size)

        // Make sure no published json exists
        assertFalse(File(tempDir, "dn_central_real_v1_BaseRoadGraph.json").exists())
    }

    @Test
    fun `9_corrupted or invalid gzip stream fails with GZIP_INVALID`() = runTest {
        // Return plain uncompressed text as the payload (not valid gzip)
        val fakeCompressedBytes = "this is definitely not a valid gzip byte stream".toByteArray(Charsets.UTF_8)
        val status = createStatus(
            uncompressedContent = "expected content",
            compressedBytes = fakeCompressedBytes
        )

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(fakeCompressedBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val result = downloader.downloadAndPrepareGraph(status)

        assertTrue(result is GraphDownloadResult.Failure)
        val failure = result as GraphDownloadResult.Failure
        assertEquals(GraphDownloadErrorKind.GZIP_INVALID, failure.error.kind)

        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun `10_uncompressed size mismatch fails with DECOMPRESSED_SIZE_MISMATCH`() = runTest {
        val actualContent = "short data"
        val gzipBytes = gzipString(actualContent)
        // Set expected uncompressed size to 5000 bytes while actual is ~10 bytes
        val status = createStatus(
            uncompressedContent = actualContent,
            compressedBytes = gzipBytes,
            uncompressedSizeOverride = 5000L
        )

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val result = downloader.downloadAndPrepareGraph(status)

        assertTrue(result is GraphDownloadResult.Failure)
        val failure = result as GraphDownloadResult.Failure
        assertEquals(GraphDownloadErrorKind.DECOMPRESSED_SIZE_MISMATCH, failure.error.kind)

        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals(0, tmpFiles.size)
        assertFalse(File(tempDir, "dn_central_real_v1_BaseRoadGraph.json").exists())
    }

    @Test
    fun `11_cache hit returns existing graph without calling network`() = runTest {
        val jsonContent = validGraph()
        val gzipBytes = gzipString(jsonContent)
        val status = createStatus(jsonContent, gzipBytes)

        var callCount = 0
        val client = buildMockClient { chain ->
            callCount++
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)

        // First download -> should call network
        val result1 = downloader.downloadAndPrepareGraph(status)
        assertTrue(result1 is GraphDownloadResult.Success)
        assertFalse((result1 as GraphDownloadResult.Success).fromCache)
        assertEquals(1, callCount)
        assertTrue(downloader.isGraphCached(status.manifest))

        // Second download -> must hit cache without network call
        val result2 = downloader.downloadAndPrepareGraph(status, forceRefresh = false)
        assertTrue(result2 is GraphDownloadResult.Success)
        assertTrue((result2 as GraphDownloadResult.Success).fromCache)
        assertEquals("Network must not be called on cache hit", 1, callCount)
        assertEquals(jsonContent, (result2 as GraphDownloadResult.Success).file.readText())

        // Third call with forceRefresh = true -> must call network
        val result3 = downloader.downloadAndPrepareGraph(status, forceRefresh = true)
        assertTrue(result3 is GraphDownloadResult.Success)
        assertFalse((result3 as GraphDownloadResult.Success).fromCache)
        assertEquals("Network must be called on forceRefresh", 2, callCount)
    }

    @Test
    fun `12_deletes all temporary files on failure`() = runTest {
        val corruptGzip = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val status = createStatus("test", corruptGzip)

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(corruptGzip.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val result = downloader.downloadAndPrepareGraph(status)

        assertTrue(result is GraphDownloadResult.Failure)
        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals("All temp files must be deleted", 0, tmpFiles.size)
    }

    @Test
    fun `13_never sends Authorization header or credentials to R2 presigned URL`() = runTest {
        val jsonContent = validGraph()
        val gzipBytes = gzipString(jsonContent)
        val status = createStatus(jsonContent, gzipBytes)

        var capturedAuthorizationHeader: String? = "SHOULD_BE_NULL"

        val client = buildMockClient { chain ->
            val req = chain.request()
            capturedAuthorizationHeader = req.header("Authorization")

            Response.Builder().header("Content-Type", "application/gzip")
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val result = downloader.downloadAndPrepareGraph(status)

        assertTrue(result is GraphDownloadResult.Success)
        assertNull("Authorization header must NEVER be sent to Cloudflare R2", capturedAuthorizationHeader)
    }

    @Test
    fun `14_rejects payload that exceeds safe decompression bomb limit`() = runTest {
        // Create repeating string of 1MB
        val largeUncompressed = "A".repeat(1024 * 1024)
        val gzipBytes = gzipString(largeUncompressed)
        // In manifest, set uncompressed size to only 100 bytes (making 1MB trigger > uncompressedSizeBytes * 2)
        val status = createStatus(
            uncompressedContent = largeUncompressed,
            compressedBytes = gzipBytes,
            uncompressedSizeOverride = 100L
        )

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val result = downloader.downloadAndPrepareGraph(status)

        assertTrue(result is GraphDownloadResult.Failure)
        val failure = result as GraphDownloadResult.Failure
        // Should be DECOMPRESSED_SIZE_MISMATCH or MAX_SIZE_EXCEEDED
        assertTrue(
            failure.error.kind == GraphDownloadErrorKind.MAX_SIZE_EXCEEDED ||
                    failure.error.kind == GraphDownloadErrorKind.DECOMPRESSED_SIZE_MISMATCH
        )
        val tmpFiles = tempDir.walkTopDown().filter { it.name.startsWith(".stage-") }.toList()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun `15_cache generation valid - rehashes and binds parsed content`() = runTest {
        val jsonContent = validGraph()
        val gzipBytes = gzipString(jsonContent)
        val status = createStatus(jsonContent, gzipBytes)

        var callCount = 0
        val client = buildMockClient { chain ->
            callCount++
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val res1 = downloader.downloadAndPrepareGraph(status)
        assertTrue(res1 is GraphDownloadResult.Success)
        assertEquals(1, callCount)
        assertTrue(downloader.isGraphCached(status.manifest))

        // Cache hit -> phải rehash file gzip thực tế
        val res2 = downloader.downloadAndPrepareGraph(status, forceRefresh = false)
        assertTrue(res2 is GraphDownloadResult.Success)
        val success = res2 as GraphDownloadResult.Success
        assertTrue(success.fromCache)
        assertEquals(status.manifest.bundle.sha256, success.verifiedSha256)
        assertEquals(1, callCount)
    }

    @Test
    fun `16_cache file tampered on disk but meta retains old hash - rejects cache`() = runTest {
        val jsonContent = validGraph()
        val gzipBytes = gzipString(jsonContent)
        val status = createStatus(jsonContent, gzipBytes)

        var callCount = 0
        val client = buildMockClient { chain ->
            callCount++
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        downloader.downloadAndPrepareGraph(status)
        assertEquals(1, callCount)

        // Sửa nội dung file .json.gz trên đĩa (thay đổi byte thực tế)
        val cachedGzip = File(requireNotNull(downloader.getCachedGraphFile(status.manifest)).parentFile, "BaseRoadGraph.json.gz")
        assertTrue(cachedGzip.exists())
        val tamperedBytes = gzipBytes.clone()
        tamperedBytes[tamperedBytes.size - 1] = (tamperedBytes[tamperedBytes.size - 1] + 1).toByte()
        cachedGzip.writeBytes(tamperedBytes)

        // The active pointer remains unchanged; it is not an integrity credential.
        val pointer = File(tempDir, "graph-cache-v2/${status.manifest.graphVersion}/current")
        assertTrue(pointer.readText().startsWith("g-"))

        // isGraphCached phải trả về false vì rehash file thực tế không khớp!
        assertFalse(downloader.isGraphCached(status.manifest))

        // downloadAndPrepareGraph với forceRefresh = false phải từ chối cache và tải lại
        val res = downloader.downloadAndPrepareGraph(status, forceRefresh = false)
        assertTrue(res is GraphDownloadResult.Success)
        assertFalse("Phải tải mới thay vì tin cache bị sửa", (res as GraphDownloadResult.Success).fromCache)
        assertEquals(2, callCount)
    }

    @Test
    fun `17_cache file truncated but meta valid - rejects cache`() = runTest {
        val jsonContent = validGraph()
        val gzipBytes = gzipString(jsonContent)
        val status = createStatus(jsonContent, gzipBytes)

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        downloader.downloadAndPrepareGraph(status)

        // Truncate file .json.gz xuống 10 bytes
        val cachedGzip = File(requireNotNull(downloader.getCachedGraphFile(status.manifest)).parentFile, "BaseRoadGraph.json.gz")
        cachedGzip.writeBytes(gzipBytes.copyOf(10))

        assertFalse("Cache bị truncate phải bị từ chối", downloader.isGraphCached(status.manifest))
    }

    @Test
    fun `18_cache metadata valid but gzip content does not match manifest - fails closed without returning verified artifact when network unavailable`() = runTest {
        val jsonContent = validGraph()
        val gzipBytes = gzipString(jsonContent)
        val status = createStatus(jsonContent, gzipBytes)

        var networkEnabled = true
        val client = buildMockClient { chain ->
            if (!networkEnabled) {
                Response.Builder().header("Content-Type", "application/gzip")
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Server Error")
                    .body("{}".toResponseBody())
                    .build()
            } else {
                Response.Builder().header("Content-Type", "application/gzip")
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(gzipBytes.toResponseBody())
                    .build()
            }
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        downloader.downloadAndPrepareGraph(status)

        // Làm hỏng file gzip
        val cachedGzip = File(requireNotNull(downloader.getCachedGraphFile(status.manifest)).parentFile, "BaseRoadGraph.json.gz")
        cachedGzip.writeBytes("corrupted_content".toByteArray())

        // Ngắt mạng
        networkEnabled = false

        // Phải fail-closed, tuyệt đối không trả VerifiedGraphArtifact
        val res = downloader.downloadAndPrepareGraph(status, forceRefresh = false)
        assertTrue(res is GraphDownloadResult.Failure)
    }

    @Test
    fun `19_cache checksum mismatch rejects the generation and safely downloads anew`() = runTest {
        val jsonContent = validGraph()
        val gzipBytes = gzipString(jsonContent)
        val status = createStatus(jsonContent, gzipBytes)

        var callCount = 0
        val client = buildMockClient { chain ->
            callCount++
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        downloader.downloadAndPrepareGraph(status)
        assertEquals(1, callCount)

        // Làm hỏng cache
        val cachedGzip = File(requireNotNull(downloader.getCachedGraphFile(status.manifest)).parentFile, "BaseRoadGraph.json.gz")
        cachedGzip.writeBytes("bad".toByteArray())

        val res = downloader.downloadAndPrepareGraph(status, forceRefresh = false)
        assertTrue(res is GraphDownloadResult.Success)
        assertFalse((res as GraphDownloadResult.Success).fromCache)
        assertEquals(2, callCount)
    }

    @Test
    fun `20_network download checksum mismatch does not publish partial files or meta`() = runTest {
        val gzipBytes = gzipString("test")
        val status = createStatus("test", gzipBytes, sha256Override = "wrong_hash_value_expected")

        val client = buildMockClient { chain ->
            Response.Builder().header("Content-Type", "application/gzip")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(gzipBytes.toResponseBody())
                .build()
        }

        val downloader = OkHttpGraphDownloader(tempDir, client)
        val res = downloader.downloadAndPrepareGraph(status)
        assertTrue(res is GraphDownloadResult.Failure)

        // Không xuất bản file json, gzip chính thức hay meta
        assertFalse(File(tempDir, "${status.manifest.graphVersion}_BaseRoadGraph.json").exists())
        assertFalse(File(tempDir, "${status.manifest.graphVersion}.json.gz").exists())
        assertFalse(downloader.isGraphCached(status.manifest))
        assertFalse(File(tempDir, "graph-cache-v2/${status.manifest.graphVersion}/current").exists())
    }
}

