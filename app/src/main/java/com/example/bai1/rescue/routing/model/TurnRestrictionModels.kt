package com.example.bai1.rescue.routing.model

/**
 * Các loại hạn chế rẽ theo tiêu chuẩn OSM Turn Restriction.
 */
enum class TurnRestrictionType {
    NO_LEFT_TURN,
    NO_RIGHT_TURN,
    NO_STRAIGHT_ON,
    NO_U_TURN,
    NO_ENTRY,
    NO_EXIT,
    ONLY_LEFT_TURN,
    ONLY_RIGHT_TURN,
    ONLY_STRAIGHT_ON,
    ONLY_U_TURN,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): TurnRestrictionType = when (value?.trim()) {
            "NO_LEFT_TURN" -> NO_LEFT_TURN
            "NO_RIGHT_TURN" -> NO_RIGHT_TURN
            "NO_STRAIGHT_ON" -> NO_STRAIGHT_ON
            "NO_U_TURN" -> NO_U_TURN
            "NO_ENTRY" -> NO_ENTRY
            "NO_EXIT" -> NO_EXIT
            "ONLY_LEFT_TURN" -> ONLY_LEFT_TURN
            "ONLY_RIGHT_TURN" -> ONLY_RIGHT_TURN
            "ONLY_STRAIGHT_ON" -> ONLY_STRAIGHT_ON
            "ONLY_U_TURN" -> ONLY_U_TURN
            else -> UNKNOWN
        }
    }
}

/**
 * Trạng thái hỗ trợ xử lý của Turn Restriction trong hệ thống.
 */
enum class RestrictionSupportStatus {
    FULLY_SUPPORTED,
    FAIL_CLOSED_UNRESOLVED,
    UNVERIFIED_WARNING,
    EXEMPT_FOR_PROFILE,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): RestrictionSupportStatus = when (value?.trim()) {
            "FULLY_SUPPORTED" -> FULLY_SUPPORTED
            "FAIL_CLOSED_UNRESOLVED" -> FAIL_CLOSED_UNRESOLVED
            "UNVERIFIED_WARNING" -> UNVERIFIED_WARNING
            "EXEMPT_FOR_PROFILE" -> EXEMPT_FOR_PROFILE
            else -> FAIL_CLOSED_UNRESOLVED
        }
    }
}

/**
 * Trạng thái biên dịch chuỗi directed edge của via-way turn restriction.
 */
enum class ViaWayCompilationStatus {
    COMPILED,
    UNRESOLVED,
    NOT_APPLICABLE;

    companion object {
        fun fromString(value: String?): ViaWayCompilationStatus = when (value?.trim()) {
            "COMPILED" -> COMPILED
            "UNRESOLVED" -> UNRESOLVED
            "NOT_APPLICABLE" -> NOT_APPLICABLE
            else -> UNRESOLVED
        }
    }
}

/**
 * Thành viên của quan hệ OSM Relation (from way, via node/way, to way).
 */
data class OsmRelationMember(
    val type: String,
    val ref: Long,
    val role: String,
    val order: Int = 0
)

/**
 * Chuyển tiếp giữa 2 cạnh có hướng tại một nút giao lộ (FromEdge -> ViaNode -> ToEdge).
 */
data class EdgeTransition(
    val fromEdgeId: String,
    val toEdgeId: String,
    val viaNodeId: String
)

/**
 * Mô hình hạn chế rẽ tại giao lộ (Turn Restriction).
 * Bảo toàn đầy đủ cả các hạn chế chưa được hỗ trợ (FAIL_CLOSED_UNRESOLVED)
 * để tầng thuật toán xử lý fail-closed.
 */
data class TurnRestriction(
    val id: String,
    val type: TurnRestrictionType,
    val members: List<OsmRelationMember> = emptyList(),
    val fromOsmWayIds: List<Long> = emptyList(),
    val viaOsmNodeIds: List<Long> = emptyList(),
    val viaOsmWayIds: List<Long> = emptyList(),
    val toOsmWayIds: List<Long> = emptyList(),
    val restrictionMode: String? = null,
    val exceptModes: List<String> = emptyList(),
    val conditionalExpression: String? = null,
    val supportStatus: RestrictionSupportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
    val isExemptForProfile: Boolean = false,
    val validationError: String? = null,
    val prohibitedTransitions: List<EdgeTransition> = emptyList(),
    val allowedTransitions: List<EdgeTransition> = emptyList(),
    val viaWayCompilationStatus: ViaWayCompilationStatus = ViaWayCompilationStatus.NOT_APPLICABLE,
    val orderedDirectedEdgeIds: List<String> = emptyList(),
    val viaWayCompilationError: String? = null
) {
    /**
     * Tạo bản sao phòng thủ (defensive copy) bảo đảm các nested collection bên trong hoàn toàn bất biến.
     */
    fun toDefensive(): TurnRestriction = copy(
        members = java.util.Collections.unmodifiableList(java.util.ArrayList(members)),
        fromOsmWayIds = java.util.Collections.unmodifiableList(java.util.ArrayList(fromOsmWayIds)),
        viaOsmNodeIds = java.util.Collections.unmodifiableList(java.util.ArrayList(viaOsmNodeIds)),
        viaOsmWayIds = java.util.Collections.unmodifiableList(java.util.ArrayList(viaOsmWayIds)),
        toOsmWayIds = java.util.Collections.unmodifiableList(java.util.ArrayList(toOsmWayIds)),
        exceptModes = java.util.Collections.unmodifiableList(java.util.ArrayList(exceptModes)),
        prohibitedTransitions = java.util.Collections.unmodifiableList(java.util.ArrayList(prohibitedTransitions)),
        allowedTransitions = java.util.Collections.unmodifiableList(java.util.ArrayList(allowedTransitions)),
        orderedDirectedEdgeIds = java.util.Collections.unmodifiableList(java.util.ArrayList(orderedDirectedEdgeIds))
    )
}

