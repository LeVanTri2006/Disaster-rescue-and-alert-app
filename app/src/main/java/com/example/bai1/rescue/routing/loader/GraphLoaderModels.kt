package com.example.bai1.rescue.routing.loader

import com.example.bai1.rescue.routing.model.RescueRoutingContext
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoutingManifest
import java.io.File

/**
 * Lý do vì sao đồ thị đã tải và parse thành công nhưng không được phép định tuyến nghiêm ngặt (Fail-closed).
 */
enum class RoutingDisabledReason {
    PRODUCTION_AUTHORITY_UNAVAILABLE,
    UNRESOLVED_TURN_RESTRICTIONS,
    UNVERIFIED_EDGES,
    NO_VERIFIED_EDGES,
    CONTRADICTORY_READINESS,
    BACKEND_STRICT_ROUTING_DISABLED,
    MANIFEST_STRICT_ROUTING_DISABLED,
    VERSION_MISMATCH,
    DFA_BUILD_FAILED,
    OTHER
}

/**
 * Phân loại các nguyên nhân lỗi khi nạp đồ thị cứu hộ.
 */
enum class GraphLoaderErrorKind {
    FETCH_STATUS_FAILED,
    DOWNLOAD_UNAVAILABLE,
    DOWNLOAD_FAILED,
    FILE_NOT_FOUND,
    PARSE_FAILED,
    VERSION_MISMATCH,
    VEHICLE_PROFILE_MISMATCH,
    CONTRADICTORY_READINESS,
    UNKNOWN
}

/**
 * Thông tin lỗi chi tiết trong quá trình nạp đồ thị cứu hộ.
 */
data class GraphLoaderError(
    val kind: GraphLoaderErrorKind,
    val technicalMessage: String,
    val cause: Throwable? = null
) {
    fun userFriendlyMessage(): String = when (kind) {
        GraphLoaderErrorKind.FETCH_STATUS_FAILED -> "Không thể kiểm tra thông tin bản đồ cứu hộ từ máy chủ."
        GraphLoaderErrorKind.DOWNLOAD_UNAVAILABLE -> "Bản đồ cứu hộ hiện chưa sẵn sàng để tải về."
        GraphLoaderErrorKind.DOWNLOAD_FAILED -> "Tải dữ liệu bản đồ cứu hộ thất bại. Vui lòng thử lại sau."
        GraphLoaderErrorKind.FILE_NOT_FOUND -> "Không tìm thấy file bản đồ cứu hộ trên thiết bị."
        GraphLoaderErrorKind.PARSE_FAILED -> "Phân tích cú pháp dữ liệu bản đồ cứu hộ thất bại."
        GraphLoaderErrorKind.VERSION_MISMATCH -> "Phiên bản bản đồ không khớp với thông số từ máy chủ."
        GraphLoaderErrorKind.VEHICLE_PROFILE_MISMATCH -> "Loại phương tiện cứu hộ của bản đồ không tương thích."
        GraphLoaderErrorKind.CONTRADICTORY_READINESS -> "Dữ liệu an toàn bản đồ có mâu thuẫn giữa máy chủ và file đồ thị."
        GraphLoaderErrorKind.UNKNOWN -> "Đã xảy ra lỗi không xác định khi nạp bản đồ cứu hộ."
    }
}

/**
 * Trạng thái của Graph Loader theo StateFlow.
 * Phân biệt rõ giữa:
 * - Graph đã tải.
 * - Graph đã xác minh và parse vào RAM.
 * - Graph đủ điều kiện strict routing (GraphLoaded vs GraphLoadedButRoutingDisabled).
 * - Có dữ liệu live hazard hay không.
 */
sealed class GraphLoaderState {
    object Idle : GraphLoaderState()

    object FetchingStatus : GraphLoaderState()

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : GraphLoaderState()

    data class Parsing(
        val file: File,
        val graphVersion: String
    ) : GraphLoaderState()

    /**
     * Legacy data carrier retained for source compatibility; this is not an authorization credential.
     * The production loader does not emit this state.
     */
    data class GraphLoaded(
        val graph: RoadGraph,
        val manifest: RoutingManifest,
        val context: RescueRoutingContext? = null,
        val fromCache: Boolean = false,
        val liveHazardReady: Boolean = false
    ) : GraphLoaderState()

    /**
     * Đồ thị đã tải và parse thành công vào RAM (cho phép chẩn đoán/kiểm tra dữ liệu),
     * NHƯNG KHÔNG ĐƯỢC PHÉP ĐỊNH TUYẾN NGHIÊM NGẶT (Fail-closed safety gate).
     * isStrictRoutingPermitted == false
     */
    data class GraphLoadedButRoutingDisabled(
        val graph: RoadGraph,
        val manifest: RoutingManifest,
        val reason: RoutingDisabledReason,
        val detailMessage: String,
        val context: RescueRoutingContext? = null,
        val fromCache: Boolean = false,
        val liveHazardReady: Boolean = false
    ) : GraphLoaderState()

    /**
     * Thất bại trong tiến trình fetch status, download hoặc parse.
     */
    data class Failed(
        val error: GraphLoaderError
    ) : GraphLoaderState()
}

/**
 * Kết quả trả về của hàm loadGraph().
 */
sealed class GraphLoaderResult {
    data class Success(
        val graph: RoadGraph,
        val manifest: RoutingManifest,
        val state: GraphLoaderState,
        val context: RescueRoutingContext? = null
    ) : GraphLoaderResult() {
        val isStrictRoutingPermitted: Boolean get() = false
    }

    data class Failure(
        val error: GraphLoaderError
    ) : GraphLoaderResult()
}

/**
 * Snapshot bất biến đại diện cho toàn bộ trạng thái của RoutingGraphLoader tại một thời điểm nhất quán.
 * Đảm bảo các reader (UI, Router, ViewModels) không bao giờ quan sát trạng thái nửa cũ, nửa mới.
 */
data class GraphLoaderSnapshot(
    val state: GraphLoaderState,
    val graph: RoadGraph? = null,
    val manifest: RoutingManifest? = null,
    val context: RescueRoutingContext? = null,
    val disabledReason: RoutingDisabledReason? = null,
    val detailMessage: String? = null
) {
    val isStrictRoutingPermitted: Boolean get() = false
    val isLoaded: Boolean get() = graph != null && manifest != null
    val hasContext: Boolean get() = context != null
    val graphVersion: String? get() = graph?.graphVersion ?: manifest?.graphVersion

    companion object {
        val Idle = GraphLoaderSnapshot(
            state = GraphLoaderState.Idle,
            graph = null,
            manifest = null,
            context = null,
        )

        fun loading(
            state: GraphLoaderState,
            previous: GraphLoaderSnapshot? = null
        ): GraphLoaderSnapshot = GraphLoaderSnapshot(
            state = state,
            graph = previous?.graph,
            manifest = previous?.manifest,
            context = null,
            disabledReason = previous?.disabledReason,
            detailMessage = previous?.detailMessage
        )

        fun loaded(
            graph: RoadGraph,
            manifest: RoutingManifest,
            context: RescueRoutingContext,
            state: GraphLoaderState
        ): GraphLoaderSnapshot = GraphLoaderSnapshot(
            state = state,
            graph = graph,
            manifest = manifest,
            context = context,
            disabledReason = null,
            detailMessage = null
        )

        fun disabled(
            graph: RoadGraph,
            manifest: RoutingManifest,
            reason: RoutingDisabledReason,
            detailMessage: String,
            context: RescueRoutingContext?,
            state: GraphLoaderState
        ): GraphLoaderSnapshot = GraphLoaderSnapshot(
            state = state,
            graph = graph,
            manifest = manifest,
            context = context,
            disabledReason = reason,
            detailMessage = detailMessage
        )

        fun failed(
            error: GraphLoaderError,
            previous: GraphLoaderSnapshot? = null
        ): GraphLoaderSnapshot = GraphLoaderSnapshot(
            state = GraphLoaderState.Failed(error),
            graph = previous?.graph,
            manifest = previous?.manifest,
            context = null,
            disabledReason = null,
            detailMessage = error.technicalMessage
        )
    }
}

