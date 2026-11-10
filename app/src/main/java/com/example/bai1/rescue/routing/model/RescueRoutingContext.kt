package com.example.bai1.rescue.routing.model

import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.loader.RoutingDisabledReason

/**
 * Kết quả đánh giá trạng thái sẵn sàng cho Strict Routing của [RescueRoutingContext].
 */
sealed class StrictRoutingEvaluation {
    object Permitted : StrictRoutingEvaluation()
    data class Denied(
        val reason: RoutingDisabledReason,
        val detailMessage: String
    ) : StrictRoutingEvaluation()
}

/**
 * Ngữ cảnh định tuyến cứu hộ bất biến (Immutable Rescue Routing Context).
 * Tập hợp toàn bộ tài nguyên định tuyến từ cùng một phiên bản đồ thị đã được phân tích cú pháp và xác thực.
 *
 * @property roadGraph Đồ thị mạng lưới đường giao thông đã qua xác thực parser.
 * @property turnRestrictionIndex Bộ chỉ mục hạn chế rẽ tại nút giao (via-node) được build từ chính [roadGraph].
 * @property viaWayDfa Bộ máy DFA xử lý hạn chế rẽ qua nhiều cạnh (via-way) được build từ chính [roadGraph].
 * @property manifest Bản kê khai định tuyến từ máy chủ tương ứng với đồ thị.
 * @property artifactSha256 Mã băm SHA-256 xác thực toàn vẹn của file đồ thị từ manifest bundle.
 *
 * LƯU Ý AN TOÀN: [isStrictRoutingPermitted] là một computed property được tính toán độc quyền
 * từ khóa production; metadata không cấp quyền. Caller TUYỆT ĐỐI KHÔNG THỂ truyền Boolean cấp quyền.
 */
data class RescueRoutingContext(
    val roadGraph: RoadGraph,
    val turnRestrictionIndex: TurnRestrictionIndex,
    val viaWayDfa: ViaWayTurnRestrictionDfa,
    val manifest: RoutingManifest,
    val artifactSha256: String
) {
    val graphVersion: String get() = roadGraph.graphVersion
    val vehicleProfile: String get() = roadGraph.vehicleProfile

    init {
        require(roadGraph.graphVersion.isNotBlank()) {
            "roadGraph.graphVersion must not be blank"
        }
        require(manifest.graphVersion.isNotBlank()) {
            "manifest.graphVersion must not be blank"
        }
        require(roadGraph.graphVersion == manifest.graphVersion) {
            "Graph version mismatch: roadGraph='${roadGraph.graphVersion}', manifest='${manifest.graphVersion}'"
        }
        require(roadGraph.vehicleProfile == manifest.vehicleProfile) {
            "Vehicle profile mismatch: roadGraph='${roadGraph.vehicleProfile}', manifest='${manifest.vehicleProfile}'"
        }
        require(turnRestrictionIndex.graphVersion == roadGraph.graphVersion) {
            "TurnRestrictionIndex version mismatch: index='${turnRestrictionIndex.graphVersion}', roadGraph='${roadGraph.graphVersion}'"
        }
        require(artifactSha256.isNotBlank()) {
            "artifactSha256 must not be blank"
        }
        require(artifactSha256.equals(manifest.bundle.sha256, ignoreCase = true)) {
            "artifactSha256 '$artifactSha256' does not match manifest bundle sha256 '${manifest.bundle.sha256}'"
        }
    }

    /**
     * Production strict routing remains disabled until an independently accepted authority exists.
     * Constructors, copy(), legacy metadata and matching digests cannot grant permission.
     */
    val readinessEvaluation: StrictRoutingEvaluation by lazy {
        evaluateStrictRoutingReadiness()
    }

    /**
     * Production authority is unavailable; every evaluation path currently denies permission.
     * Thuộc tính này là computed property read-only, TUYỆT ĐỐI KHÔNG nhận từ caller và KHÔNG cho phép bypass.
     */
    val isStrictRoutingPermitted: Boolean
        get() = readinessEvaluation is StrictRoutingEvaluation.Permitted

    /**
     * Kiểm tra xem một turn restriction có được giải quyết đầy đủ ở tầng runtime hay không.
     *
     * Phân biệt:
     * - Via-way COMPILED và được DFA tiếp nhận + Index delegated: Được coi là đã giải quyết ở runtime.
     * - Via-way UNRESOLVED hoặc không được DFA tiếp nhận / chưa delegate: Vẫn unresolved.
     * - Conditional restrictions chưa có solver: Vẫn unresolved.
     * - Mixed via-node/via-way members: Vẫn unresolved.
     * - Via-node restrictions chưa hỗ trợ hoặc unverified: Vẫn unresolved.
     */
    fun isRestrictionResolvedAtRuntime(restriction: TurnRestriction): Boolean {
        // 1. Conditional restriction chưa có solver -> Unresolved
        if (!restriction.conditionalExpression.isNullOrBlank()) {
            return false
        }

        // 2. Mixed via members (vừa có via node vừa có via way) -> Chưa hỗ trợ, Unresolved
        if (restriction.viaOsmNodeIds.isNotEmpty() && restriction.viaOsmWayIds.isNotEmpty()) {
            return false
        }

        // 3. Miễn trừ hồ sơ phương tiện (phải nhất quán cả flag và status)
        if (restriction.isExemptForProfile && restriction.supportStatus == RestrictionSupportStatus.EXEMPT_FOR_PROFILE) {
            return true
        }

        // 4. Via-way restriction đã biên dịch và được runtime delegation vào DFA & Index
        if (restriction.viaOsmWayIds.isNotEmpty()) {
            return restriction.viaWayCompilationStatus == ViaWayCompilationStatus.COMPILED &&
                    viaWayDfa.isRestrictionEnforced(restriction.id) &&
                    turnRestrictionIndex.delegatedViaWayRestrictionIds.contains(restriction.id) &&
                    !turnRestrictionIndex.undelegatedViaWayRestrictionIds.contains(restriction.id)
        }

        // 5. Via-node restriction
        return restriction.supportStatus == RestrictionSupportStatus.FULLY_SUPPORTED &&
                (restriction.prohibitedTransitions.isNotEmpty() || restriction.allowedTransitions.isNotEmpty())
    }

    private fun evaluateStrictRoutingReadiness(): StrictRoutingEvaluation {
        // 1. Kiểm tra mâu thuẫn giữa Manifest và Parsed RoadGraph (Fail-closed)
        if (manifest.canAttemptStrictRouting != roadGraph.canAttemptStrictRouting) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.CONTRADICTORY_READINESS,
                "Contradiction in canAttemptStrictRouting: manifest=${manifest.canAttemptStrictRouting}, graph=${roadGraph.canAttemptStrictRouting}"
            )
        }
        if (manifest.overallStrictRoutingReadiness != roadGraph.overallStrictRoutingReadiness) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.CONTRADICTORY_READINESS,
                "Contradiction in overallStrictRoutingReadiness: manifest=${manifest.overallStrictRoutingReadiness}, graph=${roadGraph.overallStrictRoutingReadiness}"
            )
        }
        if (manifest.restrictionStrictRoutingReadiness != roadGraph.restrictionStrictRoutingReadiness) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.CONTRADICTORY_READINESS,
                "Contradiction in restrictionStrictRoutingReadiness: manifest=${manifest.restrictionStrictRoutingReadiness}, graph=${roadGraph.restrictionStrictRoutingReadiness}"
            )
        }

        val rawUnsupportedInGraph = roadGraph.turnRestrictions.count {
            it.supportStatus != RestrictionSupportStatus.FULLY_SUPPORTED &&
            it.supportStatus != RestrictionSupportStatus.EXEMPT_FOR_PROFILE
        }
        val runtimeUnresolvedInGraph = roadGraph.turnRestrictions.count { !isRestrictionResolvedAtRuntime(it) }

        if (manifest.unsupportedRestrictionsSummary.totalCount != rawUnsupportedInGraph &&
            manifest.unsupportedRestrictionsSummary.totalCount != runtimeUnresolvedInGraph
        ) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.CONTRADICTORY_READINESS,
                "Contradiction in unsupported restrictions count: manifest=${manifest.unsupportedRestrictionsSummary.totalCount}, rawGraph=$rawUnsupportedInGraph, runtimeGraph=$runtimeUnresolvedInGraph"
            )
        }

        val unverifiedEdgesInGraph = roadGraph.edgeSafetySummary?.unverifiedEdges ?: 0L
        if (manifest.edgeSafetySummary.unverifiedEdges != unverifiedEdgesInGraph) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.CONTRADICTORY_READINESS,
                "Contradiction in unverified edges count: manifest=${manifest.edgeSafetySummary.unverifiedEdges}, graph=$unverifiedEdgesInGraph"
            )
        }

        // 2. Reject incompatible legacy metadata; these flags cannot grant permission.
        if (!manifest.canAttemptStrictRouting) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.MANIFEST_STRICT_ROUTING_DISABLED,
                "Manifest disallows strict routing (canAttemptStrictRouting=false)"
            )
        }
        if (!roadGraph.canAttemptStrictRouting || !roadGraph.isStrictRoutingEligible) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.BACKEND_STRICT_ROUTING_DISABLED,
                "RoadGraph disallows strict routing (canAttempt=${roadGraph.canAttemptStrictRouting}, isEligible=${roadGraph.isStrictRoutingEligible})"
            )
        }

        // Kiểm tra an toàn: Tuyệt đối không cho phép DFA có test evaluator trong production context
        if (viaWayDfa.hasTestEvaluator) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.BACKEND_STRICT_ROUTING_DISABLED,
                "ViaWayTurnRestrictionDfa contains active testTransitionEvaluator (test seam injection is forbidden in production context)"
            )
        }

        // 3. Đánh giá tính sẵn sàng của Turn Restrictions (Via-node & Via-way)
        // A. Kiểm tra tính nhất quán Delegation giữa DFA và TurnRestrictionIndex
        if (viaWayDfa.acceptedRestrictionIds != turnRestrictionIndex.delegatedViaWayRestrictionIds) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "Delegation mismatch between ViaWayDfa (${viaWayDfa.acceptedRestrictionIds.size} accepted) and TurnRestrictionIndex (${turnRestrictionIndex.delegatedViaWayRestrictionIds.size} delegated)"
            )
        }

        val graphRestrictionIds = roadGraph.turnRestrictions.map { it.id }.toSet()
        val foreignDelegatedIds = turnRestrictionIndex.delegatedViaWayRestrictionIds - graphRestrictionIds
        if (foreignDelegatedIds.isNotEmpty()) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "TurnRestrictionIndex contains delegated restriction IDs not present in graph: $foreignDelegatedIds"
            )
        }

        val overlappingUndelegated = turnRestrictionIndex.delegatedViaWayRestrictionIds.intersect(turnRestrictionIndex.undelegatedViaWayRestrictionIds)
        if (overlappingUndelegated.isNotEmpty()) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "TurnRestrictionIndex has overlapping delegated and undelegated IDs: $overlappingUndelegated"
            )
        }

        // B. Đánh giá số lượng restriction chưa giải quyết ở runtime
        if (runtimeUnresolvedInGraph > 0) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "RoadGraph has $runtimeUnresolvedInGraph unresolved turn restrictions at runtime"
            )
        }

        if (!manifest.isRestrictionStrictRoutingEligible || !roadGraph.isRestrictionStrictRoutingEligible) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "Restrictions not eligible for strict routing (manifest=${manifest.isRestrictionStrictRoutingEligible}, graph=${roadGraph.isRestrictionStrictRoutingEligible})"
            )
        }
        if (manifest.restrictionStrictRoutingReadiness != StrictRoutingReadiness.READY ||
            roadGraph.restrictionStrictRoutingReadiness != StrictRoutingReadiness.READY
        ) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "Restriction readiness is not READY: manifest=${manifest.restrictionStrictRoutingReadiness}, graph=${roadGraph.restrictionStrictRoutingReadiness}"
            )
        }
        if (turnRestrictionIndex.hasUnresolvedRestrictions) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "TurnRestrictionIndex has unresolved restrictions: ${turnRestrictionIndex.unresolvedRestrictionIds}"
            )
        }
        if (turnRestrictionIndex.hasConflicts) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "TurnRestrictionIndex has conflicting restrictions: ${turnRestrictionIndex.conflicts.size} conflicts"
            )
        }

        // Kiểm tra bổ sung: Không có via-way nào ở trạng thái UNRESOLVED hoặc chưa được DFA tiếp nhận
        val unresolvedViaWays = roadGraph.turnRestrictions.count {
            it.viaOsmWayIds.isNotEmpty() && (
                it.viaWayCompilationStatus != ViaWayCompilationStatus.COMPILED ||
                !viaWayDfa.isRestrictionEnforced(it.id)
            )
        }
        if (unresolvedViaWays > 0) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "RoadGraph has $unresolvedViaWays unresolved via-way restrictions"
            )
        }

        // Kiểm tra bổ sung: Không có restriction conditional nào chưa hỗ trợ
        val conditionalCount = roadGraph.turnRestrictions.count { !it.conditionalExpression.isNullOrBlank() }
        if (conditionalCount > 0) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS,
                "RoadGraph has $conditionalCount conditional restrictions"
            )
        }

        // 4. Đánh giá độ an toàn của tập cạnh (Edge Safety)
        val verifiedEdges = roadGraph.edgeSafetySummary?.verifiedCompatibleEdges ?: manifest.edgeSafetySummary.verifiedCompatibleEdges
        if (verifiedEdges == 0L) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.NO_VERIFIED_EDGES,
                "Graph has 0 verified compatible edges"
            )
        }
        val unverifiedEdges = roadGraph.edgeSafetySummary?.unverifiedEdges ?: manifest.edgeSafetySummary.unverifiedEdges
        if (unverifiedEdges > 0L) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.UNVERIFIED_EDGES,
                "Graph has $unverifiedEdges unverified edges"
            )
        }
        val blockedEdges = roadGraph.edgeSafetySummary?.blockedEdges ?: manifest.edgeSafetySummary.blockedEdges
        val conflictedEdges = roadGraph.edgeSafetySummary?.conflictedEdges ?: manifest.edgeSafetySummary.conflictedEdges
        if (blockedEdges > 0L || conflictedEdges > 0L) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.OTHER,
                "Graph has blocked ($blockedEdges) or conflicted ($conflictedEdges) edges"
            )
        }

        // 5. Đánh giá tính sẵn sàng tổng thể (Overall Readiness)
        if (manifest.overallStrictRoutingReadiness != StrictRoutingReadiness.READY ||
            roadGraph.overallStrictRoutingReadiness != StrictRoutingReadiness.READY ||
            roadGraph.strictRoutingReadiness != StrictRoutingReadiness.READY
        ) {
            return StrictRoutingEvaluation.Denied(
                RoutingDisabledReason.OTHER,
                "Overall readiness is not READY: manifest=${manifest.overallStrictRoutingReadiness}, graph=${roadGraph.overallStrictRoutingReadiness}"
            )
        }

        return StrictRoutingEvaluation.Denied(
            RoutingDisabledReason.PRODUCTION_AUTHORITY_UNAVAILABLE,
            "Production qualification authority is unavailable; metadata and byte integrity are not authorization"
        )
    }
}
