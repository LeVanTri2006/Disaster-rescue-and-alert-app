package com.example.bai1.rescue.routing.loader

import com.example.bai1.rescue.routing.algorithm.DfaBuildResult
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.download.GraphDownloadErrorKind
import com.example.bai1.rescue.routing.download.GraphDownloadResult
import com.example.bai1.rescue.routing.download.GraphDownloader
import com.example.bai1.rescue.routing.model.RescueRoutingContext
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.RoutingStatusResponse
import com.example.bai1.rescue.routing.model.StrictRoutingEvaluation
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.network.RoutingApiResult
import com.example.bai1.rescue.routing.parser.GraphParseException
import com.example.bai1.rescue.routing.parser.RoadGraphParser
import com.example.bai1.rescue.routing.repository.RoutingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Interface điều phối nạp đồ thị mạng lưới đường giao thông (Routing Graph Loader).
 * Tích hợp RoutingRepository, GraphDownloader và RoadGraphParser.
 */
interface RoutingGraphLoader {
    val state: StateFlow<GraphLoaderState>
    val snapshot: StateFlow<GraphLoaderSnapshot>
    val currentGraph: RoadGraph?
    val currentManifest: RoutingManifest?
    val currentContext: RescueRoutingContext?

    /**
     * Nạp đồ thị cứu hộ vào bộ nhớ:
     * 1. Lấy trạng thái hiện hành từ RoutingRepository.
     * 2. Kiểm tra downloadAvailable.
     * 3. Tải đồ thị qua GraphDownloader hoặc sử dụng cache hợp lệ.
     * 4. Gọi RoadGraphParser.parse() để phân tích cú pháp streaming.
     * 5. Kiểm tra tính toàn vẹn (version, vehicleProfile).
     * 6. Đánh giá fail-closed safety gate:
     *    - Always GraphLoadedButRoutingDisabled: production qualification authority is unavailable.
     * 7. Đồng bộ luồng: không chạy trùng lặp khi có 2 tác vụ tải đồng thời.
     */
    suspend fun loadGraph(forceRefresh: Boolean = false): GraphLoaderResult

    /**
     * Kiểm tra xem đồ thị hiện tại có được phép sử dụng cho định tuyến nghiêm ngặt hay không.
     * TUYỆT ĐỐI TUÂN THỦ FAIL-CLOSED: Chỉ trả về true khi TẤT CẢ các điều kiện an toàn đều thỏa mãn.
     */
    fun isStrictRoutingPermitted(): Boolean

    /**
     * Xóa đồ thị khỏi bộ nhớ runtime.
     */
    fun clearLoadedGraph()
}

/**
 * Lớp bọc StateFlow dẫn xuất đọc trực tiếp từ StateFlow nguồn, đảm bảo tính nhất quán nguyên tử
 * mà không cần duy trì biến MutableStateFlow độc lập thứ hai.
 */
class DerivedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R
) : StateFlow<R> {
    override val value: R
        get() = transform(source.value)

    override val replayCache: List<R>
        get() = listOf(value)

    override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<R>): Nothing {
        source.map { transform(it) }
            .distinctUntilChanged()
            .collect(collector)
        throw IllegalStateException("StateFlow collector completed unexpectedly")
    }
}

class DefaultRoutingGraphLoader(
    private val routingRepository: RoutingRepository,
    private val graphDownloader: GraphDownloader,
    private val roadGraphParser: RoadGraphParser,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RoutingGraphLoader {

    private val _snapshot = MutableStateFlow<GraphLoaderSnapshot>(GraphLoaderSnapshot.Idle)
    override val snapshot: StateFlow<GraphLoaderSnapshot> = _snapshot.asStateFlow()

    override val state: StateFlow<GraphLoaderState> = DerivedStateFlow(_snapshot) { it.state }

    override val currentGraph: RoadGraph?
        get() = _snapshot.value.graph

    override val currentManifest: RoutingManifest?
        get() = _snapshot.value.manifest

    override val currentContext: RescueRoutingContext?
        get() = _snapshot.value.context

    private val loadLock = Any()
    private var sessionGeneration = 0L
    private val executionMutex = Mutex()
    private var inFlightDeferred: CompletableDeferred<GraphLoaderResult>? = null

    private fun publishSnapshot(newSnapshot: GraphLoaderSnapshot, generation: Long) {
        synchronized(loadLock) {
            if (generation == sessionGeneration) _snapshot.value = newSnapshot
        }
    }

    override suspend fun loadGraph(forceRefresh: Boolean): GraphLoaderResult {
        // Đồng bộ hóa yêu cầu: Nếu đang có tác vụ tải chạy đồng thời và không ép buộc làm mới (forceRefresh = false),
        // tác vụ sau sẽ chờ kết quả của tác vụ trước thay vì tạo thêm request trùng lặp.
        val (deferred, isLeader, generation) = synchronized(loadLock) {
            val existing = inFlightDeferred
            if (!forceRefresh && existing != null && !existing.isCompleted) {
                Triple(existing, false, sessionGeneration)
            } else {
                val newDeferred = CompletableDeferred<GraphLoaderResult>()
                inFlightDeferred = newDeferred
                Triple(newDeferred, true, sessionGeneration)
            }
        }

        if (!isLeader) {
            return deferred.await()
        }

        try {
            val result = executionMutex.withLock {
                if (synchronized(loadLock) { generation != sessionGeneration }) {
                    GraphLoaderResult.Failure(GraphLoaderError(GraphLoaderErrorKind.FETCH_STATUS_FAILED, "Routing session invalidated"))
                } else {
                    executeLoadGraph(forceRefresh, generation)
                }
            }
            val currentResult = synchronized(loadLock) {
                if (generation == sessionGeneration) result else GraphLoaderResult.Failure(
                    GraphLoaderError(GraphLoaderErrorKind.FETCH_STATUS_FAILED, "Routing session invalidated")
                )
            }
            deferred.complete(currentResult)
            return currentResult
        } catch (e: Throwable) {
            val error = GraphLoaderError(
                kind = GraphLoaderErrorKind.UNKNOWN,
                technicalMessage = e.message ?: "Unexpected error during graph loading",
                cause = e
            )
            val failureResult = GraphLoaderResult.Failure(error)
            publishSnapshot(GraphLoaderSnapshot.failed(error), generation)
            deferred.complete(failureResult)
            return failureResult
        } finally {
            synchronized(loadLock) {
                if (inFlightDeferred === deferred) {
                    inFlightDeferred = null
                }
            }
        }
    }

    private suspend fun executeLoadGraph(forceRefresh: Boolean, generation: Long): GraphLoaderResult = withContext(ioDispatcher) {
        fun publishSnapshot(value: GraphLoaderSnapshot) = this@DefaultRoutingGraphLoader.publishSnapshot(value, generation)
        // 1. Lấy trạng thái định tuyến từ backend thông qua repository
        publishSnapshot(GraphLoaderSnapshot.loading(GraphLoaderState.FetchingStatus))
        val statusResult = routingRepository.getCurrentRouting()
        if (statusResult is RoutingApiResult.Failure) {
            val error = GraphLoaderError(
                kind = GraphLoaderErrorKind.FETCH_STATUS_FAILED,
                technicalMessage = "Failed to fetch routing status from backend: ${statusResult.error.technicalMessage}"
            )
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        }

        var statusResponse = (statusResult as RoutingApiResult.Success).data
        var manifest = statusResponse.manifest

        // Always revalidate the fresh manifest, digest, profile and readiness. Disk cache is data only.
        // 4. Kiểm tra điều kiện downloadAvailable từ backend
        if (!statusResponse.downloadAvailable) {
            val error = GraphLoaderError(
                kind = GraphLoaderErrorKind.DOWNLOAD_UNAVAILABLE,
                technicalMessage = "Graph download is marked unavailable by backend"
            )
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        }

        // 5. Tải graph hoặc sử dụng cache từ GraphDownloader
        var downloadResult = graphDownloader.downloadAndPrepareGraph(statusResponse, forceRefresh)
        if (downloadResult is GraphDownloadResult.Failure && downloadResult.error.kind == GraphDownloadErrorKind.URL_EXPIRED) {
            // One guarded Backend authorization refresh; old-session operations cannot start a new fetch.
            val fresh = routingRepository.refreshExpiredDownloadStatus(statusResponse)
            if (fresh is RoutingApiResult.Success) {
                statusResponse = fresh.data
                manifest = statusResponse.manifest
                downloadResult = graphDownloader.downloadAndPrepareGraph(statusResponse, forceRefresh)
            }
        }
        val finalDownloadResult = downloadResult
        if (finalDownloadResult is GraphDownloadResult.Failure) {
            val errorKind = when (finalDownloadResult.error.kind) {
                GraphDownloadErrorKind.PRECONDITION_FAILED -> GraphLoaderErrorKind.DOWNLOAD_UNAVAILABLE
                GraphDownloadErrorKind.GRAPH_VALIDATION_FAILED -> when (finalDownloadResult.error.cause) {
                    is GraphParseException.VehicleProfileMismatchException -> GraphLoaderErrorKind.VEHICLE_PROFILE_MISMATCH
                    is GraphParseException.GraphVersionMismatchException -> GraphLoaderErrorKind.VERSION_MISMATCH
                    else -> GraphLoaderErrorKind.PARSE_FAILED
                }
                else -> GraphLoaderErrorKind.DOWNLOAD_FAILED
            }
            val error = GraphLoaderError(
                kind = errorKind,
                technicalMessage = finalDownloadResult.error.technicalMessage,
                cause = finalDownloadResult.error.cause
            )
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        }

        val successDownload = downloadResult as GraphDownloadResult.Success
        val graphFile = successDownload.file
        val fromCache = successDownload.fromCache
        val verifiedSha256 = successDownload.verifiedSha256.trim()
        val artifact = successDownload.artifact

        // 5a. Xác minh graphVersion của artifact khớp với manifest (kể cả khi fromCache = true)
        if (artifact.graphVersion != manifest.graphVersion) {
            val error = GraphLoaderError(
                kind = GraphLoaderErrorKind.VERSION_MISMATCH,
                technicalMessage = "Artifact graphVersion '${artifact.graphVersion}' does not match manifest '${manifest.graphVersion}' (fromCache=$fromCache)"
            )
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        }

        // 5b. Xác minh tính hợp lệ của artifact checksum thực tế do Downloader cung cấp
        if (verifiedSha256.isBlank() || !verifiedSha256.equals(manifest.bundle.sha256.trim(), ignoreCase = true)) {
            val error = GraphLoaderError(
                kind = GraphLoaderErrorKind.CONTRADICTORY_READINESS,
                technicalMessage = "Artifact verification failed: computed SHA-256 '$verifiedSha256' does not match manifest '${manifest.bundle.sha256}'"
            )
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        }

        // 6. Streaming Parse file JSON giải nén
        publishSnapshot(GraphLoaderSnapshot.loading(GraphLoaderState.Parsing(graphFile, manifest.graphVersion), _snapshot.value))
        val parsedGraph = try {
            // Production downloader supplies the immutable graph parsed from the bound stream.
            // Legacy/custom downloaders remain subject to the existing parser/identity checks.
            successDownload.parsedGraph ?: roadGraphParser.parse(graphFile, manifest)
        } catch (e: GraphParseException.GraphVersionMismatchException) {
            val error = GraphLoaderError(GraphLoaderErrorKind.VERSION_MISMATCH, e.message ?: "Graph version mismatch", e)
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        } catch (e: GraphParseException.VehicleProfileMismatchException) {
            val error = GraphLoaderError(GraphLoaderErrorKind.VEHICLE_PROFILE_MISMATCH, e.message ?: "Vehicle profile mismatch", e)
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        } catch (e: FileNotFoundException) {
            val error = GraphLoaderError(GraphLoaderErrorKind.FILE_NOT_FOUND, e.message ?: "File not found: ${graphFile.path}", e)
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        } catch (e: GraphParseException) {
            val error = GraphLoaderError(GraphLoaderErrorKind.PARSE_FAILED, e.message ?: "Graph parse error", e)
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        } catch (e: Exception) {
            val error = GraphLoaderError(GraphLoaderErrorKind.PARSE_FAILED, "Unexpected error parsing graph: ${e.message}", e)
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        }

        // 7. Kiểm tra tính toàn vẹn phụ trợ (defensive verification)
        if (parsedGraph.graphVersion != manifest.graphVersion) {
            val error = GraphLoaderError(
                kind = GraphLoaderErrorKind.VERSION_MISMATCH,
                technicalMessage = "Parsed graph version '${parsedGraph.graphVersion}' does not match manifest '${manifest.graphVersion}'"
            )
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        }

        if (parsedGraph.vehicleProfile != manifest.vehicleProfile) {
            val error = GraphLoaderError(
                kind = GraphLoaderErrorKind.VEHICLE_PROFILE_MISMATCH,
                technicalMessage = "Parsed vehicle profile '${parsedGraph.vehicleProfile}' does not match manifest '${manifest.vehicleProfile}'"
            )
            publishSnapshot(GraphLoaderSnapshot.failed(error))
            return@withContext GraphLoaderResult.Failure(error)
        }

        // 8. Xây dựng ViaWayTurnRestrictionDfa và TurnRestrictionIndex từ chính parsedGraph
        val dfaBuildResult = ViaWayTurnRestrictionDfa.build(parsedGraph.turnRestrictions, parsedGraph)

        if (dfaBuildResult is DfaBuildResult.Failure) {
            val reason = RoutingDisabledReason.DFA_BUILD_FAILED
            val detailMessage = "ViaWayTurnRestrictionDfa build failed: ${dfaBuildResult.reason} (code: ${dfaBuildResult.diagnosticCode})"
            val disabledState = GraphLoaderState.GraphLoadedButRoutingDisabled(
                graph = parsedGraph,
                manifest = manifest,
                reason = reason,
                detailMessage = detailMessage,
                context = null,
                fromCache = fromCache,
                liveHazardReady = false
            )
            val disabledSnapshot = GraphLoaderSnapshot.disabled(
                graph = parsedGraph,
                manifest = manifest,
                reason = reason,
                detailMessage = detailMessage,
                context = null,
                state = disabledState
            )
            publishSnapshot(disabledSnapshot)
            return@withContext GraphLoaderResult.Success(
                graph = parsedGraph,
                manifest = manifest,
                state = disabledState,
                context = null
            )
        }

        val viaWayDfa = (dfaBuildResult as DfaBuildResult.Success).dfa
        val turnRestrictionIndex = TurnRestrictionIndex.build(parsedGraph, viaWayDfa.acceptedRestrictionIds)

        // 9. Tạo context dữ liệu từ cùng graph instance; không có production authority
        val routingContext = RescueRoutingContext(
            roadGraph = parsedGraph,
            turnRestrictionIndex = turnRestrictionIndex,
            viaWayDfa = viaWayDfa,
            manifest = manifest,
            artifactSha256 = verifiedSha256
        )

        // 10. Đánh giá fail-closed safety gate kết hợp Backend và Context Readiness
        val backendSafety = evaluateSafetyGate(statusResponse, parsedGraph)
        return@withContext run {
            val reason = when (backendSafety) {
                is SafetyEvaluation.Denied -> backendSafety.reason
                else -> (routingContext.readinessEvaluation as? StrictRoutingEvaluation.Denied)?.reason ?: RoutingDisabledReason.OTHER
            }
            val detailMessage = when (backendSafety) {
                is SafetyEvaluation.Denied -> backendSafety.detailMessage
                else -> (routingContext.readinessEvaluation as? StrictRoutingEvaluation.Denied)?.detailMessage ?: "Strict routing disabled"
            }
            val disabledState = GraphLoaderState.GraphLoadedButRoutingDisabled(
                graph = parsedGraph,
                manifest = manifest,
                reason = reason,
                detailMessage = detailMessage,
                context = routingContext,
                fromCache = fromCache,
                liveHazardReady = false
            )
            val disabledSnapshot = GraphLoaderSnapshot.disabled(
                graph = parsedGraph,
                manifest = manifest,
                reason = reason,
                detailMessage = detailMessage,
                context = routingContext,
                state = disabledState
            )
            publishSnapshot(disabledSnapshot)
            GraphLoaderResult.Success(
                graph = parsedGraph,
                manifest = manifest,
                state = disabledState,
                context = routingContext
            )
        }
    }

    private sealed class SafetyEvaluation {
        object MetadataConsistent : SafetyEvaluation()
        data class Denied(
            val reason: RoutingDisabledReason,
            val detailMessage: String
        ) : SafetyEvaluation()
    }

    private fun evaluateSafetyGate(
        statusResponse: RoutingStatusResponse,
        parsedGraph: RoadGraph
    ): SafetyEvaluation {
        val manifest = statusResponse.manifest

        // 1. Kiểm tra mâu thuẫn giữa Backend và Parsed Graph (Fail-closed)
        if (statusResponse.canAttemptStrictRouting != parsedGraph.canAttemptStrictRouting) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.CONTRADICTORY_READINESS,
                detailMessage = "Contradiction in canAttemptStrictRouting: backend=${statusResponse.canAttemptStrictRouting}, graph=${parsedGraph.canAttemptStrictRouting}"
            )
        }

        if (manifest.overallStrictRoutingReadiness != parsedGraph.overallStrictRoutingReadiness) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.CONTRADICTORY_READINESS,
                detailMessage = "Contradiction in overallStrictRoutingReadiness: backend=${manifest.overallStrictRoutingReadiness}, graph=${parsedGraph.overallStrictRoutingReadiness}"
            )
        }

        if (manifest.restrictionStrictRoutingReadiness != parsedGraph.restrictionStrictRoutingReadiness) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.CONTRADICTORY_READINESS,
                detailMessage = "Contradiction in restrictionStrictRoutingReadiness: backend=${manifest.restrictionStrictRoutingReadiness}, graph=${parsedGraph.restrictionStrictRoutingReadiness}"
            )
        }

        val unsupportedInGraph = parsedGraph.turnRestrictions.count {
            it.supportStatus != RestrictionSupportStatus.FULLY_SUPPORTED &&
            it.supportStatus != RestrictionSupportStatus.EXEMPT_FOR_PROFILE
        }
        if (manifest.unsupportedRestrictionsSummary.totalCount != unsupportedInGraph) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.CONTRADICTORY_READINESS,
                detailMessage = "Contradiction in unsupported restrictions: backend=${manifest.unsupportedRestrictionsSummary.totalCount}, graph=$unsupportedInGraph"
            )
        }

        val unverifiedEdgesInGraph = parsedGraph.edgeSafetySummary?.unverifiedEdges ?: 0L
        if (manifest.edgeSafetySummary.unverifiedEdges != unverifiedEdgesInGraph) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.CONTRADICTORY_READINESS,
                detailMessage = "Contradiction in unverified edges: backend=${manifest.edgeSafetySummary.unverifiedEdges}, graph=$unverifiedEdgesInGraph"
            )
        }

        // 2. Kiểm tra cờ cho phép từ phía backend
        if (!statusResponse.strictRoutingReady || !statusResponse.canAttemptStrictRouting) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.BACKEND_STRICT_ROUTING_DISABLED,
                detailMessage = "Backend disabled strict routing (strictRoutingReady=${statusResponse.strictRoutingReady}, canAttemptStrictRouting=${statusResponse.canAttemptStrictRouting})"
            )
        }

        if (!parsedGraph.canAttemptStrictRouting) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.BACKEND_STRICT_ROUTING_DISABLED,
                detailMessage = "Parsed graph canAttemptStrictRouting is false"
            )
        }

        // 3. Kiểm tra các hạn chế rẽ chưa được hỗ trợ (Turn Restrictions)
        if (manifest.unsupportedRestrictionsSummary.totalCount > 0 || unsupportedInGraph > 0) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                detailMessage = "Graph has $unsupportedInGraph unsupported turn restrictions"
            )
        }

        if (manifest.restrictionStrictRoutingReadiness != StrictRoutingReadiness.READY ||
            parsedGraph.restrictionStrictRoutingReadiness != StrictRoutingReadiness.READY
        ) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                detailMessage = "Restriction readiness is not READY: manifest=${manifest.restrictionStrictRoutingReadiness}, graph=${parsedGraph.restrictionStrictRoutingReadiness}"
            )
        }

        // 4. Kiểm tra độ an toàn của tập cạnh (Edge Safety)
        val verifiedEdges = parsedGraph.edgeSafetySummary?.verifiedCompatibleEdges ?: manifest.edgeSafetySummary.verifiedCompatibleEdges
        if (verifiedEdges == 0L) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.NO_VERIFIED_EDGES,
                detailMessage = "Graph has 0 verified compatible edges"
            )
        }

        val unverifiedEdges = parsedGraph.edgeSafetySummary?.unverifiedEdges ?: manifest.edgeSafetySummary.unverifiedEdges
        if (unverifiedEdges > 0L) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.UNVERIFIED_EDGES,
                detailMessage = "Graph has $unverifiedEdges unverified edges"
            )
        }

        // 5. Trạng thái sẵn sàng tổng thể (Overall Readiness)
        if (manifest.overallStrictRoutingReadiness != StrictRoutingReadiness.READY ||
            parsedGraph.overallStrictRoutingReadiness != StrictRoutingReadiness.READY
        ) {
            return SafetyEvaluation.Denied(
                reason = RoutingDisabledReason.OTHER,
                detailMessage = "Overall readiness is not READY: manifest=${manifest.overallStrictRoutingReadiness}, graph=${parsedGraph.overallStrictRoutingReadiness}"
            )
        }

        return SafetyEvaluation.MetadataConsistent
    }

    override fun isStrictRoutingPermitted(): Boolean {
        return _snapshot.value.isStrictRoutingPermitted
    }

    override fun clearLoadedGraph() {
        synchronized(loadLock) {
            sessionGeneration++
            inFlightDeferred = null
            _snapshot.value = GraphLoaderSnapshot.Idle
        }
    }
}
