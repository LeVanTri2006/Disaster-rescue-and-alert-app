package com.example.bai1.rescue.routing.download

import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.RoutingStatusResponse
import com.example.bai1.rescue.routing.network.*
import com.example.bai1.rescue.routing.parser.GraphParseException
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.DigestInputStream
import com.example.bai1.rescue.routing.model.RoadGraph
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext

interface GraphDownloader {
    val state: StateFlow<GraphDownloadState>
    /** Integrity/schema success is data availability, never strict rescue authorization. */
    suspend fun downloadAndPrepareGraph(statusResponse: RoutingStatusResponse, forceRefresh: Boolean = false): GraphDownloadResult
    fun isGraphCached(manifest: RoutingManifest): Boolean
    fun getCachedGraphFile(manifest: RoutingManifest): File?
    /** Clears only the managed v2 namespace; callers must release held file readers first. */
    fun clearCache(graphVersion: String? = null)
}

class OkHttpGraphDownloader(
    storageDir: File,
    downloadClient: OkHttpClient = OkHttpClient(),
    private val transportPolicy: RoutingTransportPolicy = RoutingTransportPolicy(),
    private val limits: RoutingResourceLimits = RoutingResourceLimits(),
    private val elapsedMillis: () -> Long = RoutingTime::elapsedMillis,
    private val writeCheckpoint: (CacheWritePoint) -> Unit = {}
) : GraphDownloader {
    private val downloadClient = downloadClient.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES).authenticator(okhttp3.Authenticator.NONE)
        .proxyAuthenticator(okhttp3.Authenticator.NONE)
        .addNetworkInterceptor { chain ->
            transportPolicy.url(chain.request().url.toString())
            chain.proceed(chain.request().newBuilder().removeHeader("Authorization")
                .removeHeader("Proxy-Authorization").removeHeader("Cookie").build())
        }.build()
    private val cache = GraphCacheStorage(storageDir)
    private val parser = StreamingRoadGraphParser()
    private val _state = MutableStateFlow<GraphDownloadState>(GraphDownloadState.Idle)
    override val state: StateFlow<GraphDownloadState> = _state.asStateFlow()

    private class Rejected(val kind: GraphDownloadErrorKind, message: String, val code: Int? = null) : IOException(message)
    private fun reject(kind: GraphDownloadErrorKind, message: String): Nothing = throw Rejected(kind, message)
    private fun hex(digest: MessageDigest) = digest.digest().joinToString("") { "%02x".format(it) }
    private fun digest() = MessageDigest.getInstance("SHA-256")

    private fun validateManifest(manifest: RoutingManifest) {
        GraphCacheIdentity.requireValid(manifest.graphVersion)
        require(manifest.manifestVersion == "2.1.0") { "Unsupported manifest version" }
        require(manifest.bundle.compressedSizeBytes > 0 && manifest.bundle.uncompressedSizeBytes > 0) { "Invalid bundle sizes" }
        if (manifest.bundle.compressedSizeBytes > limits.compressedBytes || manifest.bundle.uncompressedSizeBytes > limits.uncompressedBytes) {
            reject(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, "Bundle size exceeds independent limits")
        }
    }

    private fun rawHash(file: File, context: CoroutineContext? = null, budget: RoutingProcessingBudget = RoutingProcessingBudget()): String {
        val hash = digest()
        RoutingBoundedInputStream(FileInputStream(cache.checked(file)), limits.compressedBytes, budget = budget).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                context?.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                hash.update(buffer, 0, read)
            }
        }
        return hex(hash)
    }

    /** Hash actual expanded bytes; optionally write those same bytes into a NEW staging JSON. */
    private fun expand(gzip: File, manifest: RoutingManifest, output: File? = null, context: CoroutineContext? = null, budget: RoutingProcessingBudget = RoutingProcessingBudget()): String {
        val hash = digest()
        var size = 0L
        val expected = manifest.bundle.uncompressedSizeBytes
        val limit = limits.uncompressedBytes
        val out = output?.let { Files.newOutputStream(cache.checked(it).toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE) }
        val compressedHash = digest()
        try {
            DigestInputStream(RoutingBoundedInputStream(FileInputStream(cache.checked(gzip)), limits.compressedBytes, budget = budget), compressedHash).use { raw ->
            GZIPInputStream(raw).use { input ->
                out.use { sink ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        context?.ensureActive()
                        budget.check()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (size > limit - read) reject(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, "Decompression budget exceeded")
                        size += read
                        hash.update(buffer, 0, read)
                        sink?.write(buffer, 0, read)
                    }
                }
                // Include any trailing compressed bytes in integrity verification as well.
                val drain = ByteArray(8192)
                while (raw.read(drain) != -1) context?.ensureActive()
            }
            }
        } catch (e: Rejected) { throw e }
        catch (e: CancellationException) { throw e }
        catch (e: RoutingLimitException) { throw e }
        catch (e: IOException) { reject(GraphDownloadErrorKind.GZIP_INVALID, "Invalid GZIP or decompression write failure") }
        finally { out?.close() }
        if (!hex(compressedHash).equals(manifest.bundle.sha256.trim(), true)) {
            reject(GraphDownloadErrorKind.CHECKSUM_MISMATCH, "Expanded stream GZIP SHA-256 mismatch")
        }
        if (size != expected) reject(GraphDownloadErrorKind.DECOMPRESSED_SIZE_MISMATCH, "Decompressed size mismatch")
        return hex(hash)
    }

    private fun verifyGzip(gzip: File, manifest: RoutingManifest, context: CoroutineContext? = null, budget: RoutingProcessingBudget = RoutingProcessingBudget()): String {
        if (cache.checked(gzip).length() != manifest.bundle.compressedSizeBytes) reject(GraphDownloadErrorKind.SIZE_MISMATCH, "Compressed size mismatch")
        val sha = rawHash(gzip, context, budget)
        if (!sha.equals(manifest.bundle.sha256.trim(), true)) reject(GraphDownloadErrorKind.CHECKSUM_MISMATCH, "GZIP SHA-256 mismatch")
        return sha
    }

    private data class Validated(val file: File, val graph: RoadGraph)
    private fun boundJson(generation: File, manifest: RoutingManifest, context: CoroutineContext? = null, budget: RoutingProcessingBudget = RoutingProcessingBudget()): Validated {
        val gzip = cache.child(generation, "BaseRoadGraph.json.gz")
        val json = cache.child(generation, "BaseRoadGraph.json")
        verifyGzip(gzip, manifest, context, budget)
        if (!json.isFile || json.length() != manifest.bundle.uncompressedSizeBytes) {
            reject(GraphDownloadErrorKind.CHECKSUM_MISMATCH, "JSON length differs from verified expansion")
        }
        val expandedHash = expand(gzip, manifest, context = context, budget = budget)
        val parsedHash = digest()
        // Hash the exact input stream consumed by the production parser, not a later file lookup.
        val graph = DigestInputStream(RoutingBoundedInputStream(FileInputStream(cache.checked(json)), limits.uncompressedBytes, true, budget), parsedHash).use {
            parser.parseInputStream(it, manifest)
        }
        context?.ensureActive()
        if (hex(parsedHash) != expandedHash) reject(GraphDownloadErrorKind.CHECKSUM_MISMATCH, "Parsed JSON is not bound to verified GZIP bytes")
        return Validated(json, graph)
    }

    override fun isGraphCached(manifest: RoutingManifest): Boolean = getCachedGraphFile(manifest) != null
    override fun getCachedGraphFile(manifest: RoutingManifest): File? {
        if (RoutingTime.isMainThread()) return null
        return cache.lock.withLock {
        try {
            validateManifest(manifest)
            cache.active(manifest.graphVersion)?.let { boundJson(it, manifest).file }
        } catch (_: Exception) { null }
        }
    }
    override fun clearCache(graphVersion: String?) {
        check(!RoutingTime.isMainThread()) { "Clear routing cache on an I/O thread" }
        cache.lock.withLock {
            if (graphVersion != null) GraphCacheIdentity.requireValid(graphVersion)
            cache.clear(graphVersion)
        }
    }

    override suspend fun downloadAndPrepareGraph(statusResponse: RoutingStatusResponse, forceRefresh: Boolean): GraphDownloadResult = withContext(Dispatchers.IO) {
        val context = coroutineContext
        val budget = RoutingProcessingBudget()
        cache.lock.withLock {
            context.ensureActive()
            var staging: File? = null
            try {
                val manifest = statusResponse.manifest
                validateManifest(manifest)
                require(statusResponse.downloadAvailable) { "Download is unavailable" }
                val url = transportPolicy.url(statusResponse.download.url)
                RoutingTransportPolicy.ttl(statusResponse.download.expiresIn)
                if (!forceRefresh) {
                    val cached = try { cache.active(manifest.graphVersion)?.let { boundJson(it, manifest, context, budget) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { null }
                    if (cached != null) return@withLock complete(cached.file, manifest, true, cached.graph)
                }
                if (RoutingTransportPolicy.expired(statusResponse.download, elapsedMillis())) reject(GraphDownloadErrorKind.URL_EXPIRED, "Download lease expired")
                budget.check()
                staging = cache.stage(manifest.graphVersion)
                val gzip = cache.child(staging, "BaseRoadGraph.json.gz")
                var bytes = 0L
                _state.value = GraphDownloadState.Downloading(0, manifest.bundle.compressedSizeBytes)
                val request = Request.Builder().url(url).header("Accept-Encoding", "identity").get().build()
                try {
                    downloadClient.newCall(request).execute().use { response ->
                        if (response.code == 401 || response.code == 403) throw Rejected(GraphDownloadErrorKind.URL_EXPIRED, "Download URL denied", response.code)
                        if (response.code in 300..399) reject(GraphDownloadErrorKind.TRANSPORT_REJECTED, "Download redirect refused")
                        if (!response.isSuccessful) throw Rejected(GraphDownloadErrorKind.DOWNLOAD_FAILED, "Download HTTP failure", response.code)
                        try { RoutingTransportPolicy.content(response, false) }
                        catch (_: IllegalArgumentException) { reject(GraphDownloadErrorKind.TRANSPORT_REJECTED, "Download content contract rejected") }
                        val body = response.body ?: reject(GraphDownloadErrorKind.DOWNLOAD_FAILED, "Missing download body")
                        if (body.contentLength() >= 0 && body.contentLength() != manifest.bundle.compressedSizeBytes) reject(GraphDownloadErrorKind.SIZE_MISMATCH, "Response length mismatch")
                        response.header("Content-Length")?.let { if (it.toLong() != manifest.bundle.compressedSizeBytes) reject(GraphDownloadErrorKind.SIZE_MISMATCH, "Header length mismatch") }
                        body.byteStream().use { input ->
                            Files.newOutputStream(gzip.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    context.ensureActive()
                                    budget.check()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (bytes > limits.compressedBytes - read) reject(GraphDownloadErrorKind.MAX_SIZE_EXCEEDED, "Download exceeds independent limit")
                                    if (bytes > manifest.bundle.compressedSizeBytes - read) reject(GraphDownloadErrorKind.SIZE_MISMATCH, "Compressed size exceeded")
                                    output.write(buffer, 0, read)
                                    bytes += read
                                    _state.value = GraphDownloadState.Downloading(bytes, manifest.bundle.compressedSizeBytes)
                                }
                            }
                        }
                    }
                } catch (e: Rejected) { throw e }
                catch (e: RoutingLimitException) { throw e }
                catch (e: InterruptedIOException) { throw Rejected(GraphDownloadErrorKind.DOWNLOAD_TIMEOUT, "Download timed out") }
                catch (e: IOException) { throw Rejected(GraphDownloadErrorKind.DOWNLOAD_FAILED, "Download I/O failure") }
                writeCheckpoint(CacheWritePoint.AFTER_DOWNLOAD)
                context.ensureActive()
                _state.value = GraphDownloadState.Verifying
                verifyGzip(gzip, manifest, context, budget)
                val json = cache.child(staging, "BaseRoadGraph.json")
                _state.value = GraphDownloadState.Decompressing(0, manifest.bundle.uncompressedSizeBytes)
                expand(gzip, manifest, json, context, budget)
                writeCheckpoint(CacheWritePoint.AFTER_DECOMPRESSION)
                // Re-read actual bytes and validate schema/graph/profile BEFORE publication.
                val validated = boundJson(staging, manifest, context, budget)
                context.ensureActive()
                val generation = cache.commit(manifest.graphVersion, staging) { point ->
                    writeCheckpoint(point)
                    context.ensureActive()
                }
                complete(cache.child(generation, "BaseRoadGraph.json"), manifest, false, validated.graph)
            } catch (e: CancellationException) {
                _state.value = GraphDownloadState.Idle
                throw e
            } catch (e: Exception) {
                val kind = when (e) {
                    is RoutingLimitException -> GraphDownloadErrorKind.MAX_SIZE_EXCEEDED
                    is Rejected -> e.kind
                    is IllegalArgumentException -> GraphDownloadErrorKind.PRECONDITION_FAILED
                    is GraphParseException -> if (generateSequence<Throwable>(e) { it.cause }.any { it is RoutingLimitException }) GraphDownloadErrorKind.MAX_SIZE_EXCEEDED else GraphDownloadErrorKind.GRAPH_VALIDATION_FAILED
                    else -> GraphDownloadErrorKind.STORAGE_ERROR
                }
                val safeCause = when (e) {
                    is GraphParseException.VehicleProfileMismatchException -> GraphParseException.VehicleProfileMismatchException("Vehicle profile mismatch")
                    is GraphParseException.GraphVersionMismatchException -> GraphParseException.GraphVersionMismatchException("Graph version mismatch")
                    else -> null
                }
                val error = GraphDownloadError(kind, "Graph preparation rejected (${kind.name})", (e as? Rejected)?.code, safeCause)
                _state.value = GraphDownloadState.Failed(error)
                GraphDownloadResult.Failure(error)
            } finally {
                staging?.let { runCatching { cache.cleanupStage(it) } }
            }
        }
    }

    private fun complete(file: File, manifest: RoutingManifest, fromCache: Boolean, graph: RoadGraph): GraphDownloadResult.Success {
        _state.value = GraphDownloadState.Completed(file, manifest.graphVersion, file.length(), fromCache)
        return GraphDownloadResult.Success(file, manifest.graphVersion, fromCache, manifest.bundle.sha256.trim().lowercase(), graph)
    }
}
