package com.example.bai1.rescue.routing.model

/**
 * Trạng thái sẵn sàng định tuyến nghiêm ngặt (Strict Routing Readiness)
 * Đồng bộ với hợp đồng Backend StrictRoutingReadiness tại src/routing/routing.types.ts
 */
enum class StrictRoutingReadiness {
    READY,
    NOT_READY_UNRESOLVED_RESTRICTIONS,
    NOT_READY_NO_VERIFIED_EDGES,
    NOT_READY_HAS_UNVERIFIED_EDGES;

    companion object {
        fun fromString(value: String?): StrictRoutingReadiness = when (value?.trim()) {
            "READY" -> READY
            "NOT_READY_UNRESOLVED_RESTRICTIONS" -> NOT_READY_UNRESOLVED_RESTRICTIONS
            "NOT_READY_NO_VERIFIED_EDGES" -> NOT_READY_NO_VERIFIED_EDGES
            "NOT_READY_HAS_UNVERIFIED_EDGES" -> NOT_READY_HAS_UNVERIFIED_EDGES
            else -> NOT_READY_UNRESOLVED_RESTRICTIONS
        }
    }
}

/**
 * Tọa độ hộp bao địa lý (Bounding Box)
 */
data class RoutingBoundingBox(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val bufferedMeters: Double? = null
)

/**
 * Tóm tắt mức độ an toàn của tập cạnh (Edge Safety Summary)
 */
data class EdgeSafetySummary(
    val totalEdges: Long,
    val verifiedCompatibleEdges: Long,
    val unverifiedEdges: Long,
    val blockedEdges: Long,
    val conflictedEdges: Long,
    val strictSafeEdgesPercent: Double,
    val blockedWays: Long,
    val excludedBlockedWays: Long? = null,
    val retainedBlockedWays: Long? = null
)

/**
 * Thông tin gói đồ thị nén tải từ Cloudflare R2
 */
data class RoutingBundleInfo(
    val fileName: String,
    val compressedSizeBytes: Long,
    val uncompressedSizeBytes: Long,
    val sha256: String
)

/**
 * Tóm tắt các hạn chế rẽ chưa được hỗ trợ (Turn Restrictions)
 */
data class UnsupportedRestrictionsSummary(
    val totalCount: Int,
    val enforcementPolicy: String = "FAIL_CLOSED_IN_STRICT_MODE"
)

/**
 * Manifest định tuyến phiên bản 2.1.0 từ backend
 */
data class RoutingManifest(
    val schema: String? = null,
    val manifestVersion: String,
    val generatedAt: String,
    val graphVersion: String,
    val osmSnapshotTimestamp: String,
    val fixtureGeneratedAt: String? = null,
    val importerVersion: String,
    val vehicleProfile: String,
    val boundingBoxType: String,
    val boundingBox: RoutingBoundingBox? = null,
    val targetProductionBoundingBox: RoutingBoundingBox? = null,
    val bundle: RoutingBundleInfo,
    val activeHazardSnapshotVersion: String? = null,
    val hazardSnapshotStatus: String, // 'NOT_INCLUDED' | 'ATTACHED'
    val hazardAssessmentPolicy: String,
    val isRestrictionStrictRoutingEligible: Boolean,
    val restrictionStrictRoutingReadiness: StrictRoutingReadiness,
    val unsupportedRestrictionsSummary: UnsupportedRestrictionsSummary,
    val canAttemptStrictRouting: Boolean,
    val isStrictRoutingEligible: Boolean? = null,
    val overallStrictRoutingReadiness: StrictRoutingReadiness,
    val strictRoutingReadiness: StrictRoutingReadiness? = null,
    val edgeSafetySummary: EdgeSafetySummary
)

/**
 * Thông tin tải đồ thị với presigned URL từ Cloudflare R2
 */
data class GraphDownloadInfo(
    val url: String,
    val expiresIn: Long,
    /** Local monotonic lease origin. Never read from wire JSON or persisted as a credential. */
    @Transient val receivedAtElapsedMillis: Long = com.example.bai1.rescue.routing.network.RoutingTime.elapsedMillis()
) {
    override fun toString(): String = "GraphDownloadInfo(url=<redacted>, expiresIn=$expiresIn)"
}

/**
 * Phản hồi trạng thái định tuyến từ API GET /routing/current
 */
data class RoutingStatusResponse(
    val manifest: RoutingManifest,
    val download: GraphDownloadInfo,
    val downloadAvailable: Boolean,
    val strictRoutingReady: Boolean,
    val liveHazardReady: Boolean,
    val canAttemptStrictRouting: Boolean,
    val overallStrictRoutingReadiness: StrictRoutingReadiness
) {
    /**
     * Quy tắc an toàn: downloadAvailable = true CHỈ biểu thị file đồ thị có thể tải về từ R2.
     * downloadAvailable KHÔNG ĐỒNG NGHĨA VỚI VIỆC ĐƯỢC PHÉP ĐỊNH TUYẾN NGAY.
     * No production authority is integrated: even fabricated/copied READY DTOs cannot authorize navigation.
     */
    val canNavigateSafely: Boolean
        get() = false // Transport claims are not production authorization.
}

