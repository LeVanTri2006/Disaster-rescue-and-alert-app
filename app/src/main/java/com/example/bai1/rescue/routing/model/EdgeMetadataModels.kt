package com.example.bai1.rescue.routing.model

/**
 * Trạng thái tương thích của đoạn đường đối với thông số phương tiện cứu hộ.
 * Phân biệt tuyệt đối với RoadCondition (điều kiện thiên tai thời gian thực).
 */
enum class EdgeCompatibilityStatus {
    VERIFIED_COMPATIBLE,
    UNVERIFIED,
    BLOCKED;

    companion object {
        fun fromString(value: String?): EdgeCompatibilityStatus = when (value?.trim()) {
            "VERIFIED_COMPATIBLE" -> VERIFIED_COMPATIBLE
            "BLOCKED" -> BLOCKED
            else -> UNVERIFIED // Mặc định fail-closed
        }
    }
}

/**
 * Chính sách hướng lưu thông của đoạn đường OSM.
 */
enum class WayFlowPolicy {
    FORWARD,
    REVERSE,
    BIDIRECTIONAL,
    CONDITIONAL,
    REVERSIBLE,
    ALTERNATING,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): WayFlowPolicy = when (value?.trim()) {
            "FORWARD" -> FORWARD
            "REVERSE" -> REVERSE
            "BIDIRECTIONAL" -> BIDIRECTIONAL
            "CONDITIONAL" -> CONDITIONAL
            "REVERSIBLE" -> REVERSIBLE
            "ALTERNATING" -> ALTERNATING
            else -> UNKNOWN
        }
    }
}

/**
 * Siêu dữ liệu chi tiết của đoạn đường trích xuất từ OpenStreetMap.
 */
data class EdgeMetadata(
    val name: String = "",
    val highwayType: String = "",
    val surface: String = "",
    val smoothness: String = "",
    val trackGrade: String? = null,
    val surfaceSuitability: String? = null,
    val widthMeters: Double? = null,
    val effectiveWidthMeters: Double? = null,
    val maxWidthMeters: Double? = null,
    val maxPhysicalWidthMeters: Double? = null,
    val maxHeightMeters: Double? = null,
    val maxPhysicalHeightMeters: Double? = null,
    val maxWeightTons: Double? = null,
    val maxWeightRatingTons: Double? = null,
    val maxAxleLoadTons: Double? = null,
    val maxSpeedKmH: Double? = null,
    val bridge: Boolean = false,
    val tunnel: Boolean = false,
    val layer: Int = 0,
    val rawTags: Map<String, String> = emptyMap(),
    val isDimensionUnverified: Boolean = false,
    val isPhysicalClearanceUnverified: Boolean = false,
    val unverifiedPhysicalDimensions: List<String> = emptyList(),
    val unverifiedLegalRestrictions: List<String> = emptyList(),
    val tagConflicts: List<String> = emptyList(),
    val tagWarnings: List<String> = emptyList()
) {
    /**
     * Tạo bản sao phòng thủ (defensive copy) bảo đảm các collection bên trong hoàn toàn bất biến.
     */
    fun toDefensive(): EdgeMetadata = copy(
        rawTags = java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(rawTags)),
        unverifiedPhysicalDimensions = java.util.Collections.unmodifiableList(java.util.ArrayList(unverifiedPhysicalDimensions)),
        unverifiedLegalRestrictions = java.util.Collections.unmodifiableList(java.util.ArrayList(unverifiedLegalRestrictions)),
        tagConflicts = java.util.Collections.unmodifiableList(java.util.ArrayList(tagConflicts)),
        tagWarnings = java.util.Collections.unmodifiableList(java.util.ArrayList(tagWarnings))
    )
}

