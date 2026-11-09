package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.download.*
import com.example.bai1.rescue.routing.model.*
import com.example.bai1.rescue.routing.network.*
import com.example.bai1.rescue.routing.loader.*
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import com.example.bai1.rescue.routing.repository.DefaultRoutingRepository
import com.example.bai1.rescue.routing.algorithm.UniformCostSearchRouter
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer

class GraphCacheSecurityTest {
    private lateinit var owned: File
    private lateinit var root: File
    @Before fun setup() { owned = Files.createTempDirectory("p5cache-security-").toFile(); root = File(owned, "cache").apply { mkdir() } }
    @After fun cleanup() { owned.deleteRecursively() }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun graph(length: Int = 111, version: String = "cache_v1") = """{
      "contractVersion":"2.1.0","graphVersion":"$version","vehicleProfile":"RESCUE_TRUCK_4X4",
      "nodes":[{"id":"a","lat":0,"lon":0},{"id":"b","lat":0,"lon":0.001}],
      "edges":[{"id":"ab","fromNodeId":"a","toNodeId":"b","lengthMeters":$length,
        "geometry":[{"lat":0,"lon":0},{"lat":0,"lon":0.001}],"compatibilityStatus":"VERIFIED_COMPATIBLE"}],
      "turnRestrictions":[]
    }""".trimIndent()
    private fun status(text: String = graph(), version: String = "cache_v1", bytes: ByteArray = gzip(text)): RoutingStatusResponse {
        val manifest = RoutingManifest(
            manifestVersion = "2.1.0", generatedAt = "2026-11-06T00:00:00Z", graphVersion = version,
            osmSnapshotTimestamp = "2026-11-01T00:00:00Z", importerVersion = "fixture", vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "FIXTURE", bundle = RoutingBundleInfo("BaseRoadGraph.json.gz", bytes.size.toLong(), text.toByteArray().size.toLong(), sha(bytes)),
            hazardSnapshotStatus = "NOT_INCLUDED", hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            isRestrictionStrictRoutingEligible = false, restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(0), canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES,
            edgeSafetySummary = EdgeSafetySummary(1, 0, 1, 0, 0, 0.0, 0))
        return RoutingStatusResponse(manifest, GraphDownloadInfo("https://cache-fixture.invalid/graph", 900), true, false, false, false,
            StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES)
    }
    private class Wire(var bytes: ByteArray, var online: Boolean = true) {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            check(chain.request().url.host == "cache-fixture.invalid")
            check(chain.request().header("Authorization") == null)
            calls++
            if (!online) throw IOException("fixture offline")
            Response.Builder().header("Content-Type", "application/gzip").request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(bytes.toResponseBody()).build()
        }.build()
    }
    private fun pointer(version: String = "cache_v1") = File(root, "graph-cache-v2/$version/current")
    private fun success(r: GraphDownloadResult) = r as GraphDownloadResult.Success
    private fun snapshot(file: File) = file.walkTopDown().filter { it.isFile }.associate { it.absolutePath to Pair(sha(it.readBytes()), Files.getLastModifiedTime(it.toPath()).toString()) }
    private fun assertSnapshot(saved: Map<String, Pair<String, String>>) {
        for ((path, info) in saved) {
            val file = File(path)
            assertTrue("Preserve $path", file.isFile)
            assertEquals(info, Pair(sha(file.readBytes()), Files.getLastModifiedTime(file.toPath()).toString()))
        }
    }
    private fun transport(value: RoutingStatusResponse) = object : RoutingApiClient {
        override suspend fun getCurrentRouting() = RoutingApiResult.Success(value)
    }

    @Test fun validCacheAndLoaderRemainDownloadOnly() = runBlocking {
        val wire = Wire(gzip(graph()))
        val downloader = OkHttpGraphDownloader(root, wire.client)
        val repository = DefaultRoutingRepository(transport(status()), downloader)
        val loader = DefaultRoutingGraphLoader(repository, downloader, StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val result = loader.loadGraph() as GraphLoaderResult.Success
        assertFalse(result.isStrictRoutingPermitted)
        assertFalse(result.context!!.isStrictRoutingPermitted)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, UniformCostSearchRouter().findRoute(result.context!!, "a", "b").status)
        assertTrue(downloader.isGraphCached(status().manifest))
        assertTrue(success(downloader.downloadAndPrepareGraph(status())).fromCache)
        assertEquals(1, wire.calls)
    }

    @Test fun loaderUsesImmutableGraphFromBoundStreamEvenIfReturnedFileLaterChanges() = runBlocking {
        val wire = Wire(gzip(graph()))
        val real = OkHttpGraphDownloader(root, wire.client)
        val delayedFileTamper = object : GraphDownloader by real {
            override suspend fun downloadAndPrepareGraph(statusResponse: RoutingStatusResponse, forceRefresh: Boolean): GraphDownloadResult {
                val result = success(real.downloadAndPrepareGraph(statusResponse, forceRefresh))
                result.file.writeText(graph(999))
                return result
            }
        }
        val loader = DefaultRoutingGraphLoader(DefaultRoutingRepository(transport(status())), delayedFileTamper,
            StreamingRoadGraphParser(), Dispatchers.Unconfined)
        val result = loader.loadGraph() as GraphLoaderResult.Success
        assertEquals(111.0, result.graph.directedEdges.single().lengthMeters, 0.0)
        assertFalse(result.isStrictRoutingPermitted)
        assertFalse(real.isGraphCached(status().manifest))
    }

    @Test fun sameLengthAndTruncatedJsonCannotBecomeVerifiedOffline() = runBlocking {
        for (modified in listOf(graph(999), "{")) {
            val wire = Wire(gzip(graph()))
            val downloader = OkHttpGraphDownloader(root, wire.client)
            val file = success(downloader.downloadAndPrepareGraph(status(), true)).file
            file.writeText(modified)
            assertFalse(downloader.isGraphCached(status().manifest))
            wire.online = false
            assertTrue(downloader.downloadAndPrepareGraph(status()) is GraphDownloadResult.Failure)
        }
    }

    @Test fun changedAndTruncatedGzipCannotBecomeVerifiedOffline() = runBlocking {
        for (truncate in listOf(false, true)) {
            val wire = Wire(gzip(graph()))
            val downloader = OkHttpGraphDownloader(root, wire.client)
            val result = success(downloader.downloadAndPrepareGraph(status(), true))
            val gz = File(result.file.parentFile!!, "BaseRoadGraph.json.gz")
            val bytes = gz.readBytes()
            gz.writeBytes(if (truncate) bytes.copyOf(10) else bytes.apply { this[12] = (this[12].toInt() xor 1).toByte() })
            assertFalse(downloader.isGraphCached(status().manifest))
            wire.online = false
            assertTrue(downloader.downloadAndPrepareGraph(status()) is GraphDownloadResult.Failure)
        }
    }

    @Test fun failedRefreshAtEveryValidationStepPreservesGenerationA() = runBlocking {
        val a = status()
        val wire = Wire(gzip(graph()))
        val downloader = OkHttpGraphDownloader(root, wire.client)
        val old = success(downloader.downloadAndPrepareGraph(a)).file
        val saved = snapshot(old.parentFile!!) + snapshot(pointer())
        val malformed = "{\"contractVersion\":\"wrong\"}"
        val variants = listOf(
            status(graph(222), bytes = ByteArray(64) { 65 }),
            status(malformed),
            status(graph(222).replace("cache_v1", "different_version")),
            status(graph(222)).let { it.copy(manifest = it.manifest.copy(vehicleProfile = "OTHER_PROFILE")) },
            status(graph(222)).let { it.copy(manifest = it.manifest.copy(bundle = it.manifest.bundle.copy(compressedSizeBytes = 9999))) },
            status(graph(222)).let { it.copy(manifest = it.manifest.copy(bundle = it.manifest.bundle.copy(uncompressedSizeBytes = 9999))) },
            status(graph(222)).let { it.copy(manifest = it.manifest.copy(bundle = it.manifest.bundle.copy(sha256 = "0".repeat(64)))) }
        )
        for ((index, b) in variants.withIndex()) {
            wire.bytes = when (index) { 0 -> ByteArray(64) { 65 }; 1 -> gzip(malformed); 2 -> gzip(graph(222).replace("cache_v1", "different_version")); else -> gzip(graph(222)) }
            assertTrue("variant $index must fail", downloader.downloadAndPrepareGraph(b, true) is GraphDownloadResult.Failure)
            assertSnapshot(saved)
            assertEquals(old, downloader.getCachedGraphFile(a.manifest))
        }
    }

    @Test fun networkAndTruncatedDownloadFailuresPreserveA() = runBlocking {
        val wire = Wire(gzip(graph()))
        val downloader = OkHttpGraphDownloader(root, wire.client)
        val old = success(downloader.downloadAndPrepareGraph(status())).file
        val saved = snapshot(old.parentFile!!) + snapshot(pointer())
        wire.online = false
        assertTrue(downloader.downloadAndPrepareGraph(status(graph(222)), true) is GraphDownloadResult.Failure)
        assertSnapshot(saved)
        wire.online = true
        wire.bytes = gzip(graph(222)).copyOf(10)
        assertTrue(downloader.downloadAndPrepareGraph(status(graph(222)), true) is GraphDownloadResult.Failure)
        assertSnapshot(saved)
    }

    @Test fun injectedWriteFailuresAtEveryCheckpointPreserveAAndReopen() = runBlocking {
        val wire = Wire(gzip(graph()))
        val first = OkHttpGraphDownloader(root, wire.client)
        val old = success(first.downloadAndPrepareGraph(status())).file
        val saved = snapshot(old.parentFile!!) + snapshot(pointer())
        wire.bytes = gzip(graph(222))
        for (point in CacheWritePoint.entries) {
            val failing = OkHttpGraphDownloader(root, wire.client) {
                if (it == point) {
                    if (point == CacheWritePoint.AFTER_DOWNLOAD) {
                        root.walkTopDown().first { file -> file.name == "BaseRoadGraph.json.gz" && file.parentFile!!.name.startsWith(".stage-") }
                            .writeBytes(byteArrayOf(1, 2))
                    }
                    throw IOException("fixture ENOSPC/write interruption at $point")
                }
            }
            assertTrue(failing.downloadAndPrepareGraph(status(graph(222)), true) is GraphDownloadResult.Failure)
            assertSnapshot(saved)
            val reopened = OkHttpGraphDownloader(root, wire.client)
            assertEquals(old, reopened.getCachedGraphFile(status().manifest))
            assertTrue(root.walkTopDown().none { it.name.startsWith(".stage-") })
        }
    }

    @Test fun successfulCommitKeepsHeldGenerationAndPublishesConsistentB() = runBlocking {
        val wire = Wire(gzip(graph()))
        val downloader = OkHttpGraphDownloader(root, wire.client)
        val a = success(downloader.downloadAndPrepareGraph(status())).file
        val saved = snapshot(a.parentFile!!)
        wire.bytes = gzip(graph(222))
        val b = success(downloader.downloadAndPrepareGraph(status(graph(222)), true))
        assertNotEquals(a.parentFile!!, b.file.parentFile!!)
        assertSnapshot(saved)
        assertEquals(graph(222), b.file.readText())
        assertEquals(gzip(graph(222)).toList(), File(b.file.parentFile!!, "BaseRoadGraph.json.gz").readBytes().toList())
        assertEquals(b.file.parentFile!!.name, pointer().readText())
        assertEquals(b.file, downloader.getCachedGraphFile(status(graph(222)).manifest))
    }

    @Test fun concurrentReadWaitsForCommitWithoutMixingGenerations() = runBlocking {
        val wire = Wire(gzip(graph()))
        val first = OkHttpGraphDownloader(root, wire.client)
        val a = success(first.downloadAndPrepareGraph(status())).file
        wire.bytes = gzip(graph(222))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val second = OkHttpGraphDownloader(root, wire.client) {
            if (it == CacheWritePoint.BEFORE_POINTER_COMMIT) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        }
        val refresh = async(Dispatchers.IO) { second.downloadAndPrepareGraph(status(graph(222)), true) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertEquals(a.parentFile!!.name, pointer().readText())
            assertEquals(graph(), a.readText())
            val readStarted = CountDownLatch(1)
            val read = async(Dispatchers.IO) { readStarted.countDown(); first.getCachedGraphFile(status(graph(222)).manifest) }
            assertTrue(readStarted.await(10, TimeUnit.SECONDS))
            assertFalse(read.isCompleted)
            release.countDown()
            val b = success(refresh.await())
            assertEquals(b.file, read.await())
            assertEquals(graph(), a.readText())
        } finally { release.countDown() }
    }

    @Test fun concurrentRefreshesReturnTheirOwnImmutableGenerations() = runBlocking {
        val first = OkHttpGraphDownloader(root, Wire(gzip(graph(222))).client)
        val second = OkHttpGraphDownloader(root, Wire(gzip(graph(333))).client)
        val jobs = listOf(async(Dispatchers.IO) { first.downloadAndPrepareGraph(status(graph(222)), true) },
            async(Dispatchers.IO) { second.downloadAndPrepareGraph(status(graph(333)), true) })
        val results = jobs.map { success(it.await()) }
        assertEquals(listOf(graph(222), graph(333)), results.map { it.file.readText() })
        assertEquals(2, results.map { it.file.parentFile!! }.toSet().size)
        assertTrue(results.any { it.file.parentFile!!.name == pointer().readText() })
    }

    @Test fun cancellationBeforePointerCommitPreservesA() = runBlocking {
        val wire = Wire(gzip(graph()))
        val first = OkHttpGraphDownloader(root, wire.client)
        val a = success(first.downloadAndPrepareGraph(status())).file
        val saved = snapshot(a.parentFile!!) + snapshot(pointer())
        wire.bytes = gzip(graph(222))
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val second = OkHttpGraphDownloader(root, wire.client) {
            if (it == CacheWritePoint.BEFORE_POINTER_COMMIT) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        }
        val job = launch(Dispatchers.IO) { second.downloadAndPrepareGraph(status(graph(222)), true) }
        try { assertTrue(entered.await(10, TimeUnit.SECONDS)); job.cancel() } finally { release.countDown() }
        job.join()
        assertTrue(job.isCancelled)
        assertSnapshot(saved)
        assertEquals(a, OkHttpGraphDownloader(root, wire.client).getCachedGraphFile(status().manifest))
    }

    @Test fun reopenedCacheIgnoresUncommittedStagingAndOrphans() = runBlocking {
        val wire = Wire(gzip(graph()))
        val downloader = OkHttpGraphDownloader(root, wire.client)
        assertFalse(downloader.isGraphCached(status().manifest))
        val orphan = File(root, "graph-cache-v2/cache_v1/g-00000000-0000-0000-0000-000000000000").apply { mkdirs() }
        File(orphan, "BaseRoadGraph.json").writeText(graph())
        File(orphan, "BaseRoadGraph.json.gz").writeBytes(gzip(graph()))
        val staging = File(orphan.parentFile!!, ".stage-interrupted").apply { mkdir() }
        File(staging, "BaseRoadGraph.json").writeText("partial")
        assertFalse(OkHttpGraphDownloader(root, wire.client).isGraphCached(status().manifest))
        val committed = success(downloader.downloadAndPrepareGraph(status())).file
        assertEquals(committed, OkHttpGraphDownloader(root, wire.client).getCachedGraphFile(status().manifest))
        assertEquals("partial", File(staging, "BaseRoadGraph.json").readText())
    }

    @Test fun corruptPointerAndForgedMetaCannotCreateVerifiedEntry() = runBlocking {
        val wire = Wire(gzip(graph()))
        val downloader = OkHttpGraphDownloader(root, wire.client)
        val a = success(downloader.downloadAndPrepareGraph(status())).file
        File(a.parentFile!!, "fake.meta").writeText(status().manifest.bundle.sha256)
        a.writeText(graph(999))
        assertFalse(downloader.isGraphCached(status().manifest))
        pointer().writeText("../outside")
        wire.online = false
        assertNull(downloader.getCachedGraphFile(status().manifest))
        assertTrue(downloader.downloadAndPrepareGraph(status()) is GraphDownloadResult.Failure)
    }

    @Test fun validIdentifiersIncludingMaximumLengthRemainUnchanged() = runBlocking {
        for (version in listOf("a", "dn_central_real_v1", "A-9_", "v".repeat(128))) {
            val text = graph(version = version); val wire = Wire(gzip(text))
            val d = OkHttpGraphDownloader(root, wire.client)
            val result = success(d.downloadAndPrepareGraph(status(text, version)))
            assertEquals(version, result.graphVersion)
            assertEquals(result.file, d.getCachedGraphFile(status(text, version).manifest))
        }
    }

    @Test fun invalidIdentifiersRejectEveryEntryPointBeforeFilesystemOperations() = runBlocking {
        val sentinel = File(owned, "sentinel").apply { writeText("preserve") }
        val wire = Wire(gzip(graph())); val downloader = OkHttpGraphDownloader(root, wire.client)
        val before = snapshot(owned)
        for (version in listOf("../sentinel", "/absolute", "nested/path", "nested\\path", "", ".", "..", " v", "v ", "v\n", "v\u0000", "v".repeat(129), "%2e%2e%2f")) {
            val bad = status().let { it.copy(manifest = it.manifest.copy(graphVersion = version)) }
            assertTrue(downloader.downloadAndPrepareGraph(bad) is GraphDownloadResult.Failure)
            assertFalse(downloader.isGraphCached(bad.manifest))
            assertNull(downloader.getCachedGraphFile(bad.manifest))
            assertTrue(runCatching { downloader.clearCache(version) }.isFailure)
            assertTrue(runCatching { RoutingJsonParser.parseRoutingStatus(Gson().toJson(bad)) }.isFailure)
            assertSnapshot(before)
        }
        assertEquals(0, wire.calls)
        assertEquals("preserve", sentinel.readText())
    }

    @Test fun symlinkedVersionCannotEscapeReadWriteOrCleanup() = runBlocking {
        val outside = File(owned, "outside").apply { mkdir() }
        val sentinel = File(outside, "sentinel").apply { writeText("preserve") }
        val parent = File(root, "graph-cache-v2").apply { mkdir() }
        Files.createSymbolicLink(File(parent, "cache_v1").toPath(), outside.toPath())
        val downloader = OkHttpGraphDownloader(root, Wire(gzip(graph())).client)
        assertTrue(downloader.downloadAndPrepareGraph(status()) is GraphDownloadResult.Failure)
        assertNull(downloader.getCachedGraphFile(status().manifest))
        assertTrue(runCatching { downloader.clearCache("cache_v1") }.isFailure)
        downloader.clearCache()
        assertEquals("preserve", sentinel.readText())
    }

    @Test fun symlinkedGenerationFileAndRootAreRejected() = runBlocking {
        val sentinel = File(owned, "sentinel").apply { writeText(graph()) }
        val wire = Wire(gzip(graph())); val downloader = OkHttpGraphDownloader(root, wire.client)
        val file = success(downloader.downloadAndPrepareGraph(status())).file
        assertTrue(file.delete())
        Files.createSymbolicLink(file.toPath(), sentinel.toPath())
        assertFalse(downloader.isGraphCached(status().manifest))
        downloader.clearCache()
        assertEquals(graph(), sentinel.readText())
        val link = File(owned, "linked-root")
        Files.createSymbolicLink(link.toPath(), root.toPath())
        assertTrue(OkHttpGraphDownloader(link, wire.client).downloadAndPrepareGraph(status()) is GraphDownloadResult.Failure)
    }

    @Test fun clearCachePreservesUnmanagedFilesAndOutsideSentinel() = runBlocking {
        val sentinel = File(owned, "outside-sentinel").apply { writeText("preserve") }
        val unrelated = File(root, "legacy-or-unrelated").apply { writeText("preserve legacy") }
        val d = OkHttpGraphDownloader(root, Wire(gzip(graph())).client)
        success(d.downloadAndPrepareGraph(status()))
        d.clearCache("cache_v1")
        assertFalse(d.isGraphCached(status().manifest))
        d.clearCache()
        assertEquals("preserve", sentinel.readText())
        assertEquals("preserve legacy", unrelated.readText())
    }
    @Test fun midStreamIOExceptionDoesNotPublishPartialDownload() = runBlocking {
        val wire = Wire(gzip(graph()))
        val good = OkHttpGraphDownloader(root, wire.client)
        val a = success(good.downloadAndPrepareGraph(status())).file
        val saved = snapshot(a.parentFile!!) + snapshot(pointer())
        var interrupted = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val data = Buffer().write(gzip(graph(222)))
            val source = object : Source {
                var reads = 0
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (reads++ > 0) { interrupted = true; throw IOException("fixture mid-stream disk/network write failure") }
                    return data.read(sink, minOf(byteCount, 10))
                }
                override fun close() = Unit
                override fun timeout() = Timeout.NONE
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = gzip(graph(222)).size.toLong()
                override fun source() = source
            }
            Response.Builder().header("Content-Type", "application/gzip").request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(body).build()
        }.build()
        val result = OkHttpGraphDownloader(root, client).downloadAndPrepareGraph(status(graph(222)), true) as GraphDownloadResult.Failure
        assertTrue(interrupted)
        assertEquals(GraphDownloadErrorKind.DOWNLOAD_FAILED, result.error.kind)
        assertSnapshot(saved)
        assertEquals(a, good.getCachedGraphFile(status().manifest))
    }

    @Test fun trailingJsonAndDigestMatchedMalformedJsonFailBeforeCommit() = runBlocking {
        val wire = Wire(gzip(graph()))
        val downloader = OkHttpGraphDownloader(root, wire.client)
        val a = success(downloader.downloadAndPrepareGraph(status())).file
        val saved = snapshot(a.parentFile!!) + snapshot(pointer())
        for (text in listOf(graph(222) + " trailing-garbage", graph(222) + graph(), "{broken-json")) {
            wire.bytes = gzip(text)
            val result = downloader.downloadAndPrepareGraph(status(text), true) as GraphDownloadResult.Failure
            assertEquals(GraphDownloadErrorKind.GRAPH_VALIDATION_FAILED, result.error.kind)
            assertSnapshot(saved)
        }
    }

}
