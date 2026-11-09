package com.example.bai1.rescue.routing.algorithm

import com.example.bai1.rescue.routing.model.EdgeTransition
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import java.util.Collections

/**
 * Phân loại quyết định kiểm tra tính hợp lệ của chuyển tiếp giữa 2 cạnh tại nút giao.
 */
enum class TransitionDecision {
    ALLOW,
    DENY_PROHIBITED,
    DENY_NOT_IN_ALLOWED_SET,
    UNRESOLVED
}

/**
 * Kết quả kiểm tra chuyển tiếp (Edge Transition Check Result) có thông tin chẩn đoán chi tiết.
 */
data class TransitionCheckResult(
    val decision: TransitionDecision,
    val restrictionId: String? = null,
    val restrictionType: TurnRestrictionType? = null,
    val reason: String = ""
) {
    val isAllowed: Boolean
        get() = decision == TransitionDecision.ALLOW

    companion object {
        val Allowed = TransitionCheckResult(
            decision = TransitionDecision.ALLOW,
            reason = "No restrictions applied"
        )

        fun prohibited(restrictionId: String, type: TurnRestrictionType, reason: String) =
            TransitionCheckResult(
                decision = TransitionDecision.DENY_PROHIBITED,
                restrictionId = restrictionId,
                restrictionType = type,
                reason = reason
            )

        fun notInAllowedSet(restrictionId: String, type: TurnRestrictionType, reason: String) =
            TransitionCheckResult(
                decision = TransitionDecision.DENY_NOT_IN_ALLOWED_SET,
                restrictionId = restrictionId,
                restrictionType = type,
                reason = reason
            )

        fun unresolved(restrictionId: String, reason: String) =
            TransitionCheckResult(
                decision = TransitionDecision.UNRESOLVED,
                restrictionId = restrictionId,
                restrictionType = null,
                reason = reason
            )
    }
}

/**
 * Khóa chuyển tiếp có hướng giữa 2 cạnh tại một nút giao cụ thể (FromEdge -> ViaNode -> ToEdge).
 */
data class DirectedTransitionKey(
    val fromEdgeId: String,
    val toEdgeId: String,
    val viaNodeId: String
)

/**
 * Khóa hướng đi vào tại một nút giao (FromEdge -> ViaNode).
 */
data class IncomingNodeKey(
    val fromEdgeId: String,
    val viaNodeId: String
)

/**
 * Xung đột dữ liệu giữa các restriction (ví dụ NO_* và ONLY_* mâu thuẫn làm rỗng mọi lối đi,
 * hoặc 2 ONLY restrictions độc lập có tập giao nhau rỗng, hoặc mâu thuẫn cờ exemption).
 */
data class RestrictionConflict(
    val fromEdgeId: String,
    val toEdgeId: String,
    val viaNodeId: String,
    val conflictingRestrictionIds: List<String>,
    val reason: String
)

/**
 * Bộ chỉ mục Turn Restriction bất biến, phục vụ tra cứu O(1) các hạn chế rẽ trong mạng lưới đường.
 */
interface TurnRestrictionIndex {
    val graphVersion: String
    val totalRestrictionsCount: Int
    val supportedRestrictionsCount: Int
    val unresolvedRestrictionIds: Set<String>
    val unverifiedRestrictionIds: Set<String>
    val viaWayRestrictionIds: Set<String>
    val delegatedViaWayRestrictionIds: Set<String>
    val undelegatedViaWayRestrictionIds: Set<String>
    val conditionalRestrictionIds: Set<String>
    val exemptRestrictionIds: Set<String>
    val hasUnresolvedRestrictions: Boolean
    val hasConflicts: Boolean
    val conflicts: List<RestrictionConflict>

    /**
     * Xác nhận phân hệ restriction index có xử lý đầy đủ, không có mâu thuẫn hay chưa.
     * LƯU Ý BẢO MẬT/AN TOÀN: Cờ này CHỈ xác nhận riêng phân hệ turn restriction.
     * Hoàn toàn không đồng nghĩa với việc toàn bộ đồ thị đã sẵn sàng strict routing
     * (vì đồ thị còn phụ thuộc độc lập vào EdgeCompatibilityStatus và Live Hazards).
     */
    val isRestrictionIndexCompatible: Boolean

    /**
     * Alias tương thích ngược cho isRestrictionIndexCompatible.
     */
    val isStrictRoutingCompatible: Boolean

    val viaWayRestrictions: List<TurnRestriction>

    /**
     * Kiểm tra tính hợp lệ của chuyển tiếp từ [incomingEdgeId] qua [viaNodeId] sang [outgoingEdgeId].
     * Nếu [incomingEdgeId] là null (tại nút xuất phát Start), không áp dụng via-node restriction và trả về ALLOW.
     */
    fun checkTransition(
        incomingEdgeId: String?,
        outgoingEdgeId: String,
        viaNodeId: String
    ): TransitionCheckResult

    /**
     * Lấy tập hợp các ID cạnh ra bị cấm từ [incomingEdgeId] tại [viaNodeId].
     */
    fun getProhibitedTransitionsForIncoming(incomingEdgeId: String, viaNodeId: String): Set<String>

    /**
     * Lấy tập hợp các ID cạnh ra được phép từ [incomingEdgeId] tại [viaNodeId] (nếu bị ràng buộc bởi ONLY_*).
     * Trả về null nếu hướng vào này không bị ràng buộc bởi bất kỳ ONLY_* restriction nào.
     */
    fun getAllowedTransitionsForIncoming(incomingEdgeId: String, viaNodeId: String): Set<String>?

    /**
     * Kiểm tra nhanh xem tại nút giao [viaNodeId] có bất kỳ turn restriction nào hay không.
     */
    fun hasRestrictionsAtNode(viaNodeId: String): Boolean

    companion object {
        fun build(
            roadGraph: RoadGraph,
            delegatedViaWayRestrictionIds: Set<String> = emptySet()
        ): TurnRestrictionIndex = DefaultTurnRestrictionIndex(roadGraph, delegatedViaWayRestrictionIds)
    }
}

/**
 * Hiện thực mặc định của [TurnRestrictionIndex] với thuật toán kết hợp nghiêm ngặt:
 * - Nhiều ONLY độc lập kết hợp bằng INTERSECTION.
 * - Nhiều NO độc lập kết hợp bằng UNION.
 * - Áp dụng prohibited transitions lên kết quả ONLY (allowed = allowed - prohibited).
 * - Kiểm tra mâu thuẫn exemption (conservative fail-closed).
 */
class DefaultTurnRestrictionIndex(
    private val roadGraph: RoadGraph,
    delegatedViaWayRestrictionIds: Set<String> = emptySet()
) : TurnRestrictionIndex {

    override val graphVersion: String = roadGraph.graphVersion
    override val totalRestrictionsCount: Int = roadGraph.turnRestrictions.size

    private val edgesById: Map<String, RoadEdge> = roadGraph.directedEdges.associateBy { it.id }
    private val nodesById: Map<String, RoadNode> = roadGraph.nodes

    // Bản đồ chuyển tiếp bị cấm (NO_*)
    private val prohibitedMap: Map<DirectedTransitionKey, TurnRestriction>

    // Bản đồ chuyển tiếp được phép (đã qua INTERSECTION các ONLY và SUBTRACTION các NO)
    private val allowedMap: Map<IncomingNodeKey, Set<String>>
    private val onlyRestrictionMap: Map<IncomingNodeKey, TurnRestriction>

    // Các chuyển tiếp bị cấm theo từng incoming edge (UNION các NO)
    private val prohibitedByIncoming: Map<IncomingNodeKey, Set<String>>

    // Tập hợp nút giao có restriction
    private val nodesWithRestrictions: Set<String>

    // Nhóm các restriction chưa giải quyết (Fail-closed)
    private val unresolvedTransitionsMap: Map<DirectedTransitionKey, TurnRestriction>
    private val unresolvedByIncomingNode: Map<IncomingNodeKey, TurnRestriction>
    private val unresolvedWayRestrictionsList: List<TurnRestriction>

    override val unresolvedRestrictionIds: Set<String>
    override val unverifiedRestrictionIds: Set<String>
    override val viaWayRestrictionIds: Set<String>
    override val delegatedViaWayRestrictionIds: Set<String>
    override val undelegatedViaWayRestrictionIds: Set<String>
    override val conditionalRestrictionIds: Set<String>
    override val exemptRestrictionIds: Set<String>
    override val viaWayRestrictions: List<TurnRestriction>
    override val conflicts: List<RestrictionConflict>

    override val supportedRestrictionsCount: Int
    override val hasUnresolvedRestrictions: Boolean
    override val hasConflicts: Boolean
    override val isRestrictionIndexCompatible: Boolean
    override val isStrictRoutingCompatible: Boolean
        get() = isRestrictionIndexCompatible

    init {
        val tempProhibited = HashMap<DirectedTransitionKey, TurnRestriction>()
        val tempOnlyRestrictionsByInc = HashMap<IncomingNodeKey, MutableList<Pair<TurnRestriction, Set<String>>>>()
        val tempProhibitedByInc = HashMap<IncomingNodeKey, MutableSet<String>>()
        val tempNodesWithRestr = HashSet<String>()

        val tempUnresolvedTrans = HashMap<DirectedTransitionKey, TurnRestriction>()
        val tempUnresolvedByInc = HashMap<IncomingNodeKey, TurnRestriction>()
        val tempUnresolvedWays = ArrayList<TurnRestriction>()

        val tempUnresolvedIds = LinkedHashSet<String>()
        val tempUnverifiedIds = LinkedHashSet<String>()
        val tempViaWayIds = LinkedHashSet<String>()
        val tempDelegatedViaWayIds = LinkedHashSet<String>()
        val tempUndelegatedViaWayIds = LinkedHashSet<String>()
        val tempConditionalIds = LinkedHashSet<String>()
        val tempExemptIds = LinkedHashSet<String>()
        val tempSupportedIds = LinkedHashSet<String>()
        val tempViaWays = ArrayList<TurnRestriction>()
        val tempConflicts = ArrayList<RestrictionConflict>()

        for (restriction in roadGraph.turnRestrictions) {
            // 1. Kiểm tra miễn trừ hồ sơ phương tiện (Profile Exemption Audit)
            val isExemptFlag = restriction.isExemptForProfile
            val isExemptStatus = restriction.supportStatus == RestrictionSupportStatus.EXEMPT_FOR_PROFILE

            // Nếu 2 trường exemption mâu thuẫn nhau: Phải xử lý conservative và ghi nhận conflict!
            if (isExemptFlag != isExemptStatus) {
                tempConflicts.add(
                    RestrictionConflict(
                        fromEdgeId = "*",
                        toEdgeId = "*",
                        viaNodeId = restriction.viaOsmNodeIds.firstOrNull()?.let { "node_osm_$it" } ?: "*",
                        conflictingRestrictionIds = listOf(restriction.id),
                        reason = "Contradiction in profile exemption indicators for restriction '${restriction.id}': isExemptForProfile=$isExemptFlag while supportStatus=${restriction.supportStatus}"
                    )
                )
                tempUnresolvedIds.add(restriction.id)
                tempUnresolvedWays.add(restriction)
                continue
            }

            // Chỉ bỏ qua khi CẢ HAI trường đều xác nhận miễn trừ có thẩm quyền cho xe cứu hộ
            if (isExemptFlag && isExemptStatus) {
                tempExemptIds.add(restriction.id)
                continue
            }

            // Ghi nhận các nút giao liên quan
            for (nodeId in restriction.viaOsmNodeIds) {
                tempNodesWithRestr.add("node_osm_$nodeId")
            }
            for (t in restriction.prohibitedTransitions) {
                tempNodesWithRestr.add(t.viaNodeId)
            }
            for (t in restriction.allowedTransitions) {
                tempNodesWithRestr.add(t.viaNodeId)
            }

            // 2. Phát hiện Via-way restriction
            if (restriction.viaOsmWayIds.isNotEmpty()) {
                tempViaWayIds.add(restriction.id)
                tempViaWays.add(restriction)

                // Quan hệ hỗn hợp cả via-node và via-way chưa được hỗ trợ bởi engine -> Không delegate, giữ unresolved
                val hasMixedViaMembers = restriction.viaOsmNodeIds.isNotEmpty()
                if (hasMixedViaMembers) {
                    tempUndelegatedViaWayIds.add(restriction.id)
                    tempUnresolvedIds.add(restriction.id)
                    tempUnresolvedWays.add(restriction)
                    continue
                }

                if (delegatedViaWayRestrictionIds.contains(restriction.id)) {
                    tempDelegatedViaWayIds.add(restriction.id)
                    tempSupportedIds.add(restriction.id)
                    // Bàn giao cho DFA kiểm soát - không xếp vào unresolved của via-node index
                    continue
                } else {
                    tempUndelegatedViaWayIds.add(restriction.id)
                    tempUnresolvedIds.add(restriction.id)
                    tempUnresolvedWays.add(restriction)
                }
            }

            // 3. Phát hiện Conditional restriction
            if (!restriction.conditionalExpression.isNullOrBlank()) {
                tempConditionalIds.add(restriction.id)
            }

            // 4. Phân loại theo SupportStatus
            when (restriction.supportStatus) {
                RestrictionSupportStatus.EXEMPT_FOR_PROFILE -> {
                    // Đã xử lý ở bước 1
                    tempExemptIds.add(restriction.id)
                    continue
                }

                RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED -> {
                    tempUnresolvedIds.add(restriction.id)
                    tempUnresolvedWays.add(restriction)
                    for (t in restriction.prohibitedTransitions) {
                        val key = DirectedTransitionKey(t.fromEdgeId, t.toEdgeId, t.viaNodeId)
                        tempUnresolvedTrans[key] = restriction
                        tempUnresolvedByInc[IncomingNodeKey(t.fromEdgeId, t.viaNodeId)] = restriction
                    }
                }

                RestrictionSupportStatus.UNVERIFIED_WARNING -> {
                    tempUnverifiedIds.add(restriction.id)
                    tempUnresolvedWays.add(restriction)
                    for (t in restriction.prohibitedTransitions) {
                        val key = DirectedTransitionKey(t.fromEdgeId, t.toEdgeId, t.viaNodeId)
                        tempUnresolvedTrans[key] = restriction
                        tempUnresolvedByInc[IncomingNodeKey(t.fromEdgeId, t.viaNodeId)] = restriction
                    }
                }

                RestrictionSupportStatus.FULLY_SUPPORTED -> {
                    // Kiểm tra tính hợp lệ: FULLY_SUPPORTED nhưng thiếu cả prohibited và allowed transitions
                    // thì phải coi là malformed/unresolved theo nguyên tắc fail-closed
                    if (restriction.prohibitedTransitions.isEmpty() && restriction.allowedTransitions.isEmpty()) {
                        tempUnresolvedIds.add(restriction.id)
                        tempUnresolvedWays.add(restriction)
                        continue
                    }

                    tempSupportedIds.add(restriction.id)

                    // Lập chỉ mục NO_* transitions (UNION các cấm)
                    for (t in restriction.prohibitedTransitions) {
                        val key = DirectedTransitionKey(t.fromEdgeId, t.toEdgeId, t.viaNodeId)
                        tempProhibited[key] = restriction

                        val incKey = IncomingNodeKey(t.fromEdgeId, t.viaNodeId)
                        tempProhibitedByInc.getOrPut(incKey) { HashSet() }.add(t.toEdgeId)
                    }

                    // Gom nhóm các ONLY_* transitions theo từng relation độc lập
                    if (restriction.type.name.startsWith("ONLY_") || restriction.allowedTransitions.isNotEmpty()) {
                        val allowedByIncInThisRelation = HashMap<IncomingNodeKey, MutableSet<String>>()
                        for (t in restriction.allowedTransitions) {
                            val incKey = IncomingNodeKey(t.fromEdgeId, t.viaNodeId)
                            allowedByIncInThisRelation.getOrPut(incKey) { HashSet() }.add(t.toEdgeId)
                        }
                        for ((incKey, allowedSet) in allowedByIncInThisRelation) {
                            tempOnlyRestrictionsByInc.getOrPut(incKey) { ArrayList() }
                                .add(restriction to allowedSet)
                        }
                    }
                }

                RestrictionSupportStatus.UNKNOWN -> {
                    tempUnresolvedIds.add(restriction.id)
                    tempUnresolvedWays.add(restriction)
                }
            }
        }

        // 5. Kết hợp các ONLY restrictions độc lập bằng INTERSECTION và loại trừ NO bằng SUBTRACTION
        val tempFinalAllowed = HashMap<IncomingNodeKey, Set<String>>()
        val tempOnlyRepresentative = HashMap<IncomingNodeKey, TurnRestriction>()

        for ((incKey, onlyList) in tempOnlyRestrictionsByInc) {
            tempOnlyRepresentative[incKey] = onlyList.first().first

            // Bắt đầu với tập allowed của ONLY restriction đầu tiên
            var intersectedSet: Set<String> = onlyList.first().second

            // INTERSECTION tuần tự với các ONLY restrictions độc lập tiếp theo
            for (i in 1 until onlyList.size) {
                val nextSet = onlyList[i].second
                intersectedSet = intersectedSet.intersect(nextSet)
            }

            // Nếu INTERSECTION giữa các ONLY độc lập rỗng -> Xung đột không thể dung hòa!
            if (intersectedSet.isEmpty()) {
                tempConflicts.add(
                    RestrictionConflict(
                        fromEdgeId = incKey.fromEdgeId,
                        toEdgeId = "*",
                        viaNodeId = incKey.viaNodeId,
                        conflictingRestrictionIds = onlyList.map { it.first.id }.distinct(),
                        reason = "Independent ONLY restrictions on incoming edge '${incKey.fromEdgeId}' at '${incKey.viaNodeId}' produced empty intersection"
                    )
                )
                tempFinalAllowed[incKey] = emptySet()
                continue
            }

            // Áp dụng prohibited transitions (NO) lên kết quả ONLY
            val prohibitedForInc = tempProhibitedByInc[incKey] ?: emptySet()
            val effectiveAllowed = intersectedSet - prohibitedForInc

            // Nếu sau khi trừ NO, tập hợp cho phép rỗng -> Xung đột (mọi lối đi của ONLY đều bị NO cấm)
            if (effectiveAllowed.isEmpty()) {
                tempConflicts.add(
                    RestrictionConflict(
                        fromEdgeId = incKey.fromEdgeId,
                        toEdgeId = if (intersectedSet.size == 1) intersectedSet.first() else "*",
                        viaNodeId = incKey.viaNodeId,
                        conflictingRestrictionIds = (onlyList.map { it.first.id } + prohibitedForInc.mapNotNull { target ->
                            tempProhibited[DirectedTransitionKey(incKey.fromEdgeId, target, incKey.viaNodeId)]?.id
                        }).distinct(),
                        reason = "All allowed transitions of ONLY restrictions on incoming edge '${incKey.fromEdgeId}' at '${incKey.viaNodeId}' are prohibited by NO restrictions"
                    )
                )
            }

            tempFinalAllowed[incKey] = effectiveAllowed
        }

        // Đóng gói bất biến (Immutable Collections)
        this.prohibitedMap = Collections.unmodifiableMap(tempProhibited)
        this.allowedMap = Collections.unmodifiableMap(tempFinalAllowed.mapValues { Collections.unmodifiableSet(it.value) })
        this.onlyRestrictionMap = Collections.unmodifiableMap(tempOnlyRepresentative)
        this.prohibitedByIncoming = Collections.unmodifiableMap(tempProhibitedByInc.mapValues { Collections.unmodifiableSet(it.value) })
        this.nodesWithRestrictions = Collections.unmodifiableSet(tempNodesWithRestr)

        this.unresolvedTransitionsMap = Collections.unmodifiableMap(tempUnresolvedTrans)
        this.unresolvedByIncomingNode = Collections.unmodifiableMap(tempUnresolvedByInc)
        this.unresolvedWayRestrictionsList = Collections.unmodifiableList(tempUnresolvedWays)

        this.unresolvedRestrictionIds = Collections.unmodifiableSet(tempUnresolvedIds)
        this.unverifiedRestrictionIds = Collections.unmodifiableSet(tempUnverifiedIds)
        this.viaWayRestrictionIds = Collections.unmodifiableSet(tempViaWayIds)
        this.delegatedViaWayRestrictionIds = Collections.unmodifiableSet(tempDelegatedViaWayIds)
        this.undelegatedViaWayRestrictionIds = Collections.unmodifiableSet(tempUndelegatedViaWayIds)
        this.conditionalRestrictionIds = Collections.unmodifiableSet(tempConditionalIds)
        this.exemptRestrictionIds = Collections.unmodifiableSet(tempExemptIds)
        this.viaWayRestrictions = Collections.unmodifiableList(tempViaWays)
        this.conflicts = Collections.unmodifiableList(tempConflicts)

        this.supportedRestrictionsCount = tempSupportedIds.size
        this.hasConflicts = tempConflicts.isNotEmpty()
        this.hasUnresolvedRestrictions = tempUnresolvedIds.isNotEmpty() ||
                tempUnverifiedIds.isNotEmpty() ||
                tempUndelegatedViaWayIds.isNotEmpty() ||
                tempConflicts.isNotEmpty()

        // Index chỉ tương thích khi 100% restriction được giải quyết và không có xung đột
        this.isRestrictionIndexCompatible = !this.hasUnresolvedRestrictions && !this.hasConflicts
    }

    override fun checkTransition(
        incomingEdgeId: String?,
        outgoingEdgeId: String,
        viaNodeId: String
    ): TransitionCheckResult {
        // Nút xuất phát không có incoming edge: Không áp dụng via-node transition
        if (incomingEdgeId == null) {
            return TransitionCheckResult.Allowed
        }

        val transKey = DirectedTransitionKey(incomingEdgeId, outgoingEdgeId, viaNodeId)
        val incKey = IncomingNodeKey(incomingEdgeId, viaNodeId)

        // BƯỚC 1: Kiểm tra xung đột (Conflict Detection)
        for (conflict in conflicts) {
            val fromMatches = conflict.fromEdgeId == "*" || conflict.fromEdgeId == incomingEdgeId
            val toMatches = conflict.toEdgeId == "*" || conflict.toEdgeId == outgoingEdgeId
            val viaMatches = conflict.viaNodeId == "*" || conflict.viaNodeId == viaNodeId

            if (fromMatches && toMatches && viaMatches) {
                return TransitionCheckResult.unresolved(
                    restrictionId = conflict.conflictingRestrictionIds.joinToString(","),
                    reason = "Conflicting rules detected: ${conflict.reason}"
                )
            }
        }

        // BƯỚC 2: Kiểm tra Restriction chưa giải quyết (Unresolved / Unverified / Malformed)
        val unresolvedExact = unresolvedTransitionsMap[transKey]
        if (unresolvedExact != null) {
            return TransitionCheckResult.unresolved(
                restrictionId = unresolvedExact.id,
                reason = "Transition is part of unresolved restriction '${unresolvedExact.id}' (${unresolvedExact.supportStatus}): ${unresolvedExact.validationError ?: unresolvedExact.conditionalExpression ?: "Unresolved"}"
            )
        }

        val unresolvedByInc = unresolvedByIncomingNode[incKey]
        if (unresolvedByInc != null) {
            return TransitionCheckResult.unresolved(
                restrictionId = unresolvedByInc.id,
                reason = "Incoming edge is part of unresolved restriction '${unresolvedByInc.id}' (${unresolvedByInc.supportStatus})"
            )
        }

        // Đối chiếu cấp độ OSM Way/Node đối với restriction chưa có cụ thể transition (Fail-closed)
        val unresolvedWay = findUnresolvedWayRestriction(incomingEdgeId, outgoingEdgeId, viaNodeId)
        if (unresolvedWay != null) {
            return TransitionCheckResult.unresolved(
                restrictionId = unresolvedWay.id,
                reason = "Transition touches unresolved restriction '${unresolvedWay.id}' (${unresolvedWay.supportStatus}): ${unresolvedWay.validationError ?: unresolvedWay.conditionalExpression ?: "Unresolved way restriction"}"
            )
        }

        // BƯỚC 3: Áp dụng ONLY restrictions (Phải nằm trong tập hợp cho phép)
        val allowedSet = allowedMap[incKey]
        if (allowedSet != null) {
            if (!allowedSet.contains(outgoingEdgeId)) {
                // Kiểm tra xem cạnh này có bị cấm rõ ràng trong prohibitedMap hay không
                val prohibitedRestriction = prohibitedMap[transKey]
                if (prohibitedRestriction != null) {
                    return TransitionCheckResult.prohibited(
                        restrictionId = prohibitedRestriction.id,
                        type = prohibitedRestriction.type,
                        reason = "Transition is explicitly prohibited by ${prohibitedRestriction.type} (${prohibitedRestriction.id})"
                    )
                }
                val onlyRestriction = onlyRestrictionMap[incKey]
                return TransitionCheckResult.notInAllowedSet(
                    restrictionId = onlyRestriction?.id ?: "unknown",
                    type = onlyRestriction?.type ?: TurnRestrictionType.UNKNOWN,
                    reason = "Outgoing edge '$outgoingEdgeId' is not in allowed set $allowedSet for ${onlyRestriction?.type ?: "ONLY_*"} from '$incomingEdgeId' at '$viaNodeId'"
                )
            }
        }

        // BƯỚC 4: Áp dụng NO restrictions (Không được nằm trong tập cấm)
        val prohibitedRestriction = prohibitedMap[transKey]
        if (prohibitedRestriction != null) {
            return TransitionCheckResult.prohibited(
                restrictionId = prohibitedRestriction.id,
                type = prohibitedRestriction.type,
                reason = "Transition is explicitly prohibited by ${prohibitedRestriction.type} (${prohibitedRestriction.id})"
            )
        }

        // BƯỚC 5: Chuyển tiếp hợp lệ
        return TransitionCheckResult.Allowed
    }

    override fun getProhibitedTransitionsForIncoming(
        incomingEdgeId: String,
        viaNodeId: String
    ): Set<String> {
        val incKey = IncomingNodeKey(incomingEdgeId, viaNodeId)
        return prohibitedByIncoming[incKey] ?: emptySet()
    }

    override fun getAllowedTransitionsForIncoming(
        incomingEdgeId: String,
        viaNodeId: String
    ): Set<String>? {
        val incKey = IncomingNodeKey(incomingEdgeId, viaNodeId)
        return allowedMap[incKey]
    }

    override fun hasRestrictionsAtNode(viaNodeId: String): Boolean {
        return nodesWithRestrictions.contains(viaNodeId)
    }

    /**
     * Đối chiếu chuyển tiếp với các restriction chưa được giải quyết theo osmWayId và osmNodeId.
     * Bảo đảm fail-closed: Khi một restriction chưa giải quyết chạm vào cạnh và nút đang xét,
     * thuật toán không được phép suy đoán là an toàn.
     */
    private fun findUnresolvedWayRestriction(
        incomingEdgeId: String,
        outgoingEdgeId: String,
        viaNodeId: String
    ): TurnRestriction? {
        if (unresolvedWayRestrictionsList.isEmpty()) return null

        val incEdge = edgesById[incomingEdgeId] ?: return null
        val outEdge = edgesById[outgoingEdgeId] ?: return null
        val viaNode = nodesById[viaNodeId]

        for (r in unresolvedWayRestrictionsList) {
            // Kiểm tra khớp via-node
            val nodeMatches = if (viaNode?.osmNodeId != null) {
                r.viaOsmNodeIds.contains(viaNode.osmNodeId)
            } else {
                r.viaOsmNodeIds.any { viaNodeId == "node_osm_$it" }
            }

            // Kiểm tra khớp from-way và to-way
            val fromMatches = r.fromOsmWayIds.contains(incEdge.osmWayId)
            val toMatches = r.toOsmWayIds.contains(outEdge.osmWayId)

            if (fromMatches && (nodeMatches || r.viaOsmWayIds.isNotEmpty())) {
                if (toMatches || r.type.name.startsWith("ONLY_") || r.viaOsmWayIds.contains(outEdge.osmWayId)) {
                    return r
                }
            }
        }
        return null
    }
}
