package com.example.bai1.rescue.routing.algorithm

import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.ViaWayCompilationStatus

/**
 * Cấu hình giới hạn bộ nhớ và kích thước mẫu cho Via-way Turn Restriction DFA.
 * Ngăn chặn cạn kiệt bộ nhớ hoặc tràn số nguyên (integer overflow).
 */
data class ViaWayDfaLimits(
    val maxPatterns: Int = 100,
    val maxPatternLength: Int = 20,
    val maxStates: Int = 1000,
    val maxTransitions: Int = 10000
)

/**
 * Quyết định đánh giá chuyển tiếp cạnh của Via-way DFA.
 */
enum class DfaDecision {
    ALLOW,
    DENY_PROHIBITED,
    DENY_NOT_IN_ALLOWED_SET,
    DENY_CONFLICT,
    DENY_INVALID_STATE,
    DENY_INVALID_EDGE
}

/**
 * Kết quả đánh giá chuyển tiếp qua một cạnh có hướng trong DFA.
 *
 * @property decision Quyết định cho phép hoặc từ chối.
 * @property nextStateId Trạng thái DFA tiếp theo nếu được ALLOW (null khi bị từ chối).
 * @property violatedRestrictionIds Danh sách mã định danh restriction vi phạm hoặc xung đột.
 * @property diagnosticReason Thông tin chẩn đoán chi tiết khi bị từ chối.
 */
data class DfaEvaluationResult(
    val decision: DfaDecision,
    val nextStateId: Int? = null,
    val violatedRestrictionIds: List<String> = emptyList(),
    val diagnosticReason: String? = null
)

/**
 * Mẫu dữ liệu đầu vào cho một via-way restriction trong DFA.
 */
data class ViaWayPattern(
    val relationId: String,
    val restrictionType: TurnRestrictionType,
    val orderedDirectedEdgeIds: List<String>
) {
    /**
     * Tạo bản sao phòng thủ (defensive copy) bảo đảm danh sách cạnh không bị thay đổi từ bên ngoài.
     */
    fun toDefensive(): ViaWayPattern = copy(
        orderedDirectedEdgeIds = java.util.Collections.unmodifiableList(java.util.ArrayList(orderedDirectedEdgeIds))
    )
}

/**
 * Kết quả khởi tạo bộ máy DFA.
 */
sealed class DfaBuildResult {
    data class Success(val dfa: ViaWayTurnRestrictionDfa) : DfaBuildResult()
    data class Failure(val reason: String, val diagnosticCode: String) : DfaBuildResult()
}

/**
 * Thông tin một nghĩa vụ ONLY đang hoạt động tại một trạng thái DFA.
 */
data class ActiveObligation(
    val restrictionId: String,
    val requiredNextEdgeId: String,
    val currentDepth: Int,
    val totalLength: Int
)

/**
 * Trạng thái hữu hạn đơn định trong DFA (bất biến sau khi khởi tạo).
 *
 * @property id Mã định danh trạng thái duy nhất (0 là ROOT).
 * @property depth Độ sâu của trạng thái trong Trie.
 * @property transitions Bảng chuyển tiếp trực tiếp: candidateEdgeId -> nextStateId.
 * @property failLink Chỉ số trạng thái thất bại (Aho-Corasick Failure Link).
 * @property activeOnlyObligations Tập nghĩa vụ ONLY đang hoạt động (tổng hợp trực tiếp và từ failure chain).
 * @property effectiveAllowedEdge Cạnh duy nhất được phép tiếp theo nếu có nghĩa vụ ONLY hoạt động.
 * @property hasOnlyConflict Cờ báo hiệu có từ 2 nghĩa vụ ONLY trở lên yêu cầu các cạnh khác nhau (giao rỗng).
 * @property prohibitedPatternIds Danh sách các NO pattern hoàn thành tại trạng thái này hoặc failure chain.
 */
data class DfaState(
    val id: Int,
    val depth: Int,
    val transitions: Map<String, Int>,
    val failLink: Int,
    val activeOnlyObligations: List<ActiveObligation>,
    val effectiveAllowedEdge: String?,
    val hasOnlyConflict: Boolean,
    val prohibitedPatternIds: List<String>
)

/**
 * Interceptor kiểm soát đánh giá chuyển tiếp DFA phục vụ nội bộ cho test suite (Controlled Test Seam).
 * Production code tuyệt đối KHÔNG sử dụng.
 */
internal fun interface DfaTransitionEvaluator {
    fun evaluate(currentStateId: Int, candidateEdgeId: String, defaultResult: DfaEvaluationResult): DfaEvaluationResult
}

/**
 * Bộ máy trạng thái hữu hạn xác định (Deterministic Multi-Pattern Automaton) xử lý
 * các quan hệ cấm rẽ qua nhiều cạnh (Via-way Turn Restrictions).
 *
 * Kiến trúc kết hợp:
 * 1. Aho-Corasick Trie + Failure Links để nhận diện đa chuỗi NO_* vi phạm.
 * 2. Active Obligation Enforcement để áp đặt đường đi bắt buộc của ONLY_*,
 *    không cho phép dùng failure links để thoát nghĩa vụ rẽ bắt buộc.
 *
 * Instance hoàn toàn bất biến (immutable), an toàn cho đa luồng đọc đồng thời,
 * và KHÔNG cho phép kế thừa hay ghi đè phương thức (Final Production Safety).
 */
class ViaWayTurnRestrictionDfa private constructor(
    states: Array<DfaState>,
    val patternCount: Int,
    val limits: ViaWayDfaLimits,
    acceptedRestrictionIds: Set<String>,
    val rootStateId: Int,
    private val testTransitionEvaluator: DfaTransitionEvaluator?
) {
    private val states: Array<DfaState> = states.clone()
    val stateCount: Int get() = states.size
    val totalTransitionsCount: Int = states.sumOf { it.transitions.size }
    val acceptedRestrictionIds: Set<String> = java.util.Collections.unmodifiableSet(java.util.LinkedHashSet(acceptedRestrictionIds))

    /**
     * Kiểm tra xem một stateId có nằm trong phạm vi chỉ số trạng thái hợp lệ của DFA hay không.
     */
    fun isValidStateId(stateId: Int): Boolean = stateId in 0 until stateCount

    /**
     * Kiểm tra xem một restrictionId có đang được DFA này tích cực áp đặt và quản lý hay không.
     */
    fun isRestrictionEnforced(restrictionId: String): Boolean = acceptedRestrictionIds.contains(restrictionId)

    /**
     * Cờ đánh dấu DFA này đang sử dụng test interceptor (chỉ cho phép trong test nội bộ).
     */
    internal val hasTestEvaluator: Boolean get() = testTransitionEvaluator != null

    companion object {
        const val ROOT_STATE_ID = 0

        private val EMPTY_DFA: ViaWayTurnRestrictionDfa by lazy {
            val res = buildFromPatterns(emptyList())
            (res as DfaBuildResult.Success).dfa
        }

        /**
         * Trả về một thể hiện DFA rỗng (chỉ có trạng thái ROOT duy nhất, không có restriction nào).
         */
        fun empty(): ViaWayTurnRestrictionDfa = EMPTY_DFA

        /**
         * Test seam nội bộ có kiểm soát, chỉ phục vụ các test kiểm thử lỗi an toàn nội bộ (Task 4B.3).
         * Được giới hạn trong nội bộ module (internal), production code KHÔNG sử dụng hàm này.
         */
        internal fun createForTesting(
            rootStateId: Int = ROOT_STATE_ID,
            testTransitionEvaluator: DfaTransitionEvaluator? = null
        ): ViaWayTurnRestrictionDfa {
            val empty = EMPTY_DFA
            return ViaWayTurnRestrictionDfa(
                states = empty.states,
                patternCount = empty.patternCount,
                limits = empty.limits,
                acceptedRestrictionIds = empty.acceptedRestrictionIds,
                rootStateId = rootStateId,
                testTransitionEvaluator = testTransitionEvaluator
            )
        }

        fun TurnRestrictionType.isNoType(): Boolean = when (this) {
            TurnRestrictionType.NO_LEFT_TURN,
            TurnRestrictionType.NO_RIGHT_TURN,
            TurnRestrictionType.NO_STRAIGHT_ON,
            TurnRestrictionType.NO_U_TURN,
            TurnRestrictionType.NO_ENTRY,
            TurnRestrictionType.NO_EXIT -> true
            else -> false
        }

        fun TurnRestrictionType.isOnlyType(): Boolean = when (this) {
            TurnRestrictionType.ONLY_LEFT_TURN,
            TurnRestrictionType.ONLY_RIGHT_TURN,
            TurnRestrictionType.ONLY_STRAIGHT_ON,
            TurnRestrictionType.ONLY_U_TURN -> true
            else -> false
        }

        /**
         * Xây dựng DFA từ danh sách TurnRestriction và đồ thị RoadGraph tùy chọn.
         */
        fun build(
            restrictions: List<TurnRestriction>,
            graph: RoadGraph? = null,
            limits: ViaWayDfaLimits = ViaWayDfaLimits()
        ): DfaBuildResult {
            val candidatePatterns = ArrayList<ViaWayPattern>()
            val edgesById = graph?.directedEdges?.associateBy { it.id }

            for (restr in restrictions) {
                // Chỉ xử lý via-way restrictions
                if (restr.viaOsmWayIds.isEmpty()) {
                    continue
                }

                // Quan hệ hỗn hợp cả via-node và via-way chưa được hỗ trợ bởi engine -> bỏ qua để giữ unresolved (Fail-closed)
                if (restr.viaOsmNodeIds.isNotEmpty()) {
                    continue
                }

                // Không đưa restriction chưa compiled vào engine
                if (restr.viaWayCompilationStatus != ViaWayCompilationStatus.COMPILED) {
                    continue
                }

                // Không đưa restriction conditional chưa được hỗ trợ vào engine
                if (!restr.conditionalExpression.isNullOrBlank()) {
                    continue
                }

                // Không đưa restriction type không xác định vào engine
                if (!restr.type.isNoType() && !restr.type.isOnlyType()) {
                    continue
                }

                val edgeIds = restr.orderedDirectedEdgeIds
                if (edgeIds.isEmpty()) {
                    continue
                }

                if (edgeIds.size < 2) {
                    return DfaBuildResult.Failure(
                        "Restriction '${restr.id}' has sequence length ${edgeIds.size} < 2",
                        "INVALID_PATTERN_LENGTH"
                    )
                }

                // Xác thực với RoadGraph nếu được cung cấp
                if (graph != null && edgesById != null) {
                    // A. Xác minh tất cả edges tồn tại
                    for (edgeId in edgeIds) {
                        if (!edgesById.containsKey(edgeId)) {
                            return DfaBuildResult.Failure(
                                "Referenced edge '$edgeId' in restriction '${restr.id}' does not exist in graph",
                                "INVALID_EDGE_REFERENCE"
                            )
                        }
                    }

                    val edges = edgeIds.map { edgesById[it]!! }

                    // B. Xác minh tính liên tục topology
                    for (i in 0 until edges.size - 1) {
                        val curr = edges[i]
                        val next = edges[i + 1]
                        if (curr.toNodeId != next.fromNodeId) {
                            return DfaBuildResult.Failure(
                                "Discontinuous edge sequence in restriction '${restr.id}': edge '${curr.id}' toNode '${curr.toNodeId}' != next edge '${next.id}' fromNode '${next.fromNodeId}'",
                                "INVALID_TOPOLOGY_DISCONTINUOUS"
                            )
                        }
                    }

                    // C. Xác minh thứ tự OSM member ways
                    val collapsedWays = ArrayList<Long>()
                    for (edge in edges) {
                        if (collapsedWays.isEmpty() || collapsedWays.last() != edge.osmWayId) {
                            collapsedWays.add(edge.osmWayId)
                        }
                    }

                    val firstWay = collapsedWays.first()
                    val lastWay = collapsedWays.last()

                    if (restr.fromOsmWayIds.isNotEmpty() && !restr.fromOsmWayIds.contains(firstWay)) {
                        return DfaBuildResult.Failure(
                            "From-approach edge osmWayId $firstWay not in fromOsmWayIds ${restr.fromOsmWayIds} for restriction '${restr.id}'",
                            "INVALID_OSM_WAY_ALIGNMENT"
                        )
                    }

                    if (restr.toOsmWayIds.isNotEmpty() && !restr.toOsmWayIds.contains(lastWay)) {
                        return DfaBuildResult.Failure(
                            "To-departure edge osmWayId $lastWay not in toOsmWayIds ${restr.toOsmWayIds} for restriction '${restr.id}'",
                            "INVALID_OSM_WAY_ALIGNMENT"
                        )
                    }

                    if (restr.viaOsmWayIds.isNotEmpty()) {
                        val intermediateWays = if (collapsedWays.size >= 2) {
                            collapsedWays.subList(1, collapsedWays.size - 1)
                        } else {
                            emptyList()
                        }
                        if (intermediateWays != restr.viaOsmWayIds) {
                            return DfaBuildResult.Failure(
                                "Via-path edges osmWayId sequence $intermediateWays does not match expected viaOsmWayIds ${restr.viaOsmWayIds} for restriction '${restr.id}'",
                                "INVALID_OSM_WAY_ALIGNMENT"
                            )
                        }
                    }
                }

                candidatePatterns.add(
                    ViaWayPattern(
                        relationId = restr.id,
                        restrictionType = restr.type,
                        orderedDirectedEdgeIds = edgeIds
                    )
                )
            }

            return buildFromPatterns(candidatePatterns, limits)
        }

        /**
         * Xây dựng DFA trực tiếp từ danh sách mẫu ViaWayPattern thuần túy.
         */
        fun buildFromPatterns(
            patterns: List<ViaWayPattern>,
            limits: ViaWayDfaLimits = ViaWayDfaLimits()
        ): DfaBuildResult {
            if (patterns.size > limits.maxPatterns) {
                return DfaBuildResult.Failure(
                    "Pattern count ${patterns.size} exceeds maximum limit ${limits.maxPatterns}",
                    "LIMIT_EXCEEDED_PATTERNS"
                )
            }

            // Sắp xếp đơn định theo relationId và phòng thủ các pattern đầu vào
            val sortedPatterns = patterns.map { it.toDefensive() }.sortedBy { it.relationId }

            for (p in sortedPatterns) {
                if (p.orderedDirectedEdgeIds.size < 2) {
                    return DfaBuildResult.Failure(
                        "Pattern '${p.relationId}' length ${p.orderedDirectedEdgeIds.size} < 2",
                        "INVALID_PATTERN_LENGTH"
                    )
                }
                if (p.orderedDirectedEdgeIds.size > limits.maxPatternLength) {
                    return DfaBuildResult.Failure(
                        "Pattern '${p.relationId}' length ${p.orderedDirectedEdgeIds.size} exceeds maximum limit ${limits.maxPatternLength}",
                        "LIMIT_EXCEEDED_PATTERN_LENGTH"
                    )
                }
                for (edgeId in p.orderedDirectedEdgeIds) {
                    if (edgeId.isBlank()) {
                        return DfaBuildResult.Failure(
                            "Pattern '${p.relationId}' contains blank edge ID",
                            "INVALID_EDGE_ID"
                        )
                    }
                }
            }

            // 1. Dựng Trie
            val builderNodes = ArrayList<BuilderNode>()
            val root = BuilderNode(id = 0, depth = 0)
            builderNodes.add(root)

            for (p in sortedPatterns) {
                var curr = root
                val totalLength = p.orderedDirectedEdgeIds.size

                for (d in 0 until totalLength) {
                    val edgeId = p.orderedDirectedEdgeIds[d]
                    var child = curr.children[edgeId]
                    if (child == null) {
                        if (builderNodes.size >= limits.maxStates) {
                            return DfaBuildResult.Failure(
                                "DFA state count exceeds maximum limit of ${limits.maxStates}",
                                "LIMIT_EXCEEDED_STATES"
                            )
                        }
                        child = BuilderNode(
                            id = builderNodes.size,
                            depth = curr.depth + 1,
                            edgeFromParent = edgeId
                        )
                        curr.children[edgeId] = child
                        builderNodes.add(child)
                    }
                    curr = child

                    // Active ONLY obligation enforcement:
                    // Sau khi đi qua cạnh d (độ sâu d + 1 trong pattern):
                    // Nếu d + 1 < totalLength, nghĩa vụ kích hoạt và yêu cầu cạnh tiếp theo tại d + 1.
                    if (p.restrictionType.isOnlyType()) {
                        val nextDepthInPattern = d + 1
                        if (nextDepthInPattern < totalLength) {
                            val requiredEdge = p.orderedDirectedEdgeIds[nextDepthInPattern]
                            curr.directOnlyObligations.add(
                                ActiveObligation(
                                    restrictionId = p.relationId,
                                    requiredNextEdgeId = requiredEdge,
                                    currentDepth = nextDepthInPattern,
                                    totalLength = totalLength
                                )
                            )
                        }
                    }
                }

                // Với NO pattern: chạm tới terminal node
                if (p.restrictionType.isNoType()) {
                    curr.directNoPatterns.add(p.relationId)
                }
            }

            val totalTransitions = builderNodes.sumOf { it.children.size }
            if (totalTransitions > limits.maxTransitions) {
                return DfaBuildResult.Failure(
                    "Total transitions count $totalTransitions exceeds maximum limit of ${limits.maxTransitions}",
                    "LIMIT_EXCEEDED_TRANSITIONS"
                )
            }

            // 2. BFS tính Failure Links (Aho-Corasick)
            val queue = ArrayDeque<BuilderNode>()
            root.failLink = 0

            // Các nút ở độ sâu 1 trỏ failure link về Root (0)
            for (child in root.children.toSortedMap().values) {
                child.failLink = 0
                queue.add(child)
            }

            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()

                for ((edgeId, child) in curr.children.toSortedMap()) {
                    var v = curr.failLink
                    while (v != 0 && !builderNodes[v].children.containsKey(edgeId)) {
                        v = builderNodes[v].failLink
                    }
                    child.failLink = builderNodes[v].children[edgeId]?.id ?: 0
                    queue.add(child)
                }
            }

            // 3. Hoàn tất các đối tượng DfaState bất biến theo thứ tự độ sâu
            val finalStates = arrayOfNulls<DfaState>(builderNodes.size)

            // Nhóm theo depth để đảm bảo failLink luôn được xử lý trước
            val nodesByDepth = builderNodes.groupBy { it.depth }.toSortedMap()

            for ((_, nodesAtDepth) in nodesByDepth) {
                for (node in nodesAtDepth) {
                    val failLink = node.failLink
                    val inheritedOnly = if (node.id == 0) emptyList() else finalStates[failLink]!!.activeOnlyObligations
                    val inheritedNo = if (node.id == 0) emptyList() else finalStates[failLink]!!.prohibitedPatternIds

                    // Tổng hợp prohibited NO patterns
                    val allNo = (node.directNoPatterns + inheritedNo).distinct()

                    // Tổng hợp active ONLY obligations
                    val allOnly = node.directOnlyObligations + inheritedOnly
                    val requiredEdges = allOnly.map { it.requiredNextEdgeId }.toSet()
                    val hasOnlyConflict = requiredEdges.size > 1
                    val effectiveAllowedEdge = if (hasOnlyConflict || requiredEdges.isEmpty()) {
                        null
                    } else {
                        requiredEdges.first()
                    }

                    val transitions = node.children.toSortedMap().mapValues { it.value.id }

                    finalStates[node.id] = DfaState(
                        id = node.id,
                        depth = node.depth,
                        transitions = java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(transitions)),
                        failLink = failLink,
                        activeOnlyObligations = java.util.Collections.unmodifiableList(java.util.ArrayList(allOnly)),
                        effectiveAllowedEdge = effectiveAllowedEdge,
                        hasOnlyConflict = hasOnlyConflict,
                        prohibitedPatternIds = java.util.Collections.unmodifiableList(java.util.ArrayList(allNo))
                    )
                }
            }

            @Suppress("UNCHECKED_CAST")
            val nonNullStates = finalStates as Array<DfaState>

            val acceptedIds = sortedPatterns.map { it.relationId }.toSet()

            return DfaBuildResult.Success(
                ViaWayTurnRestrictionDfa(
                    states = nonNullStates,
                    patternCount = sortedPatterns.size,
                    limits = limits,
                    acceptedRestrictionIds = acceptedIds,
                    rootStateId = ROOT_STATE_ID,
                    testTransitionEvaluator = null
                )
            )
        }
    }

    /**
     * Truy xuất thông tin trạng thái theo ID.
     */
    fun getState(stateId: Int): DfaState? {
        return if (stateId in states.indices) states[stateId] else null
    }

    /**
     * Đánh giá bước chuyển tiếp qua một cạnh [candidateEdgeId] từ trạng thái hiện tại [currentStateId].
     *
     * Thứ tự đánh giá an toàn nghiêm ngặt:
     * 1. Kiểm tra tính hợp lệ của state ID và edge ID (Fail-Closed nếu không hợp lệ).
     * 2. Kiểm tra nghĩa vụ ONLY tại trạng thái hiện tại:
     *    - Nếu có xung đột ONLY (giao rỗng) -> DENY_CONFLICT.
     *    - Nếu có cạnh bắt buộc và candidate không khớp -> DENY_NOT_IN_ALLOWED_SET (Không dùng failure links để thoát!).
     * 3. Tính trạng thái tiếp theo qua Trie và Failure Links.
     * 4. Kiểm tra các mẫu NO hoàn chỉnh tại trạng thái tiếp theo -> DENY_PROHIBITED nếu vi phạm.
     * 5. ALLOW và trả về nextStateId.
     */
    fun evaluateTransition(
        currentStateId: Int,
        candidateEdgeId: String
    ): DfaEvaluationResult {
        // 1. Kiểm tra tính hợp lệ đầu vào
        if (currentStateId < 0 || currentStateId >= states.size) {
            val invalidStateResult = DfaEvaluationResult(
                decision = DfaDecision.DENY_INVALID_STATE,
                nextStateId = null,
                violatedRestrictionIds = emptyList(),
                diagnosticReason = "Invalid DFA state ID: $currentStateId (valid range: 0 until ${states.size})"
            )
            return testTransitionEvaluator?.evaluate(currentStateId, candidateEdgeId, invalidStateResult) ?: invalidStateResult
        }
        if (candidateEdgeId.isBlank()) {
            val invalidEdgeResult = DfaEvaluationResult(
                decision = DfaDecision.DENY_INVALID_EDGE,
                nextStateId = null,
                violatedRestrictionIds = emptyList(),
                diagnosticReason = "Candidate edge ID is blank or empty"
            )
            return testTransitionEvaluator?.evaluate(currentStateId, candidateEdgeId, invalidEdgeResult) ?: invalidEdgeResult
        }

        val state = states[currentStateId]

        // 2. Kiểm tra ONLY obligations đang hoạt động tại trạng thái hiện tại
        if (state.hasOnlyConflict) {
            val conflictingIds = state.activeOnlyObligations.map { it.restrictionId }.distinct()
            val conflictResult = DfaEvaluationResult(
                decision = DfaDecision.DENY_CONFLICT,
                nextStateId = null,
                violatedRestrictionIds = conflictingIds,
                diagnosticReason = "Conflicting active ONLY obligations with disjoint required next edges"
            )
            return testTransitionEvaluator?.evaluate(currentStateId, candidateEdgeId, conflictResult) ?: conflictResult
        }

        if (state.effectiveAllowedEdge != null && candidateEdgeId != state.effectiveAllowedEdge) {
            val activeIds = state.activeOnlyObligations.map { it.restrictionId }.distinct()
            val notInAllowedSetResult = DfaEvaluationResult(
                decision = DfaDecision.DENY_NOT_IN_ALLOWED_SET,
                nextStateId = null,
                violatedRestrictionIds = activeIds,
                diagnosticReason = "Candidate edge '$candidateEdgeId' violates active ONLY obligation requiring '${state.effectiveAllowedEdge}'"
            )
            return testTransitionEvaluator?.evaluate(currentStateId, candidateEdgeId, notInAllowedSetResult) ?: notInAllowedSetResult
        }

        // 3. Tính trạng thái tiếp theo qua Aho-Corasick Trie & Failure Links
        var curr = currentStateId
        while (curr != 0 && !states[curr].transitions.containsKey(candidateEdgeId)) {
            curr = states[curr].failLink
        }
        val nextStateId = states[curr].transitions[candidateEdgeId] ?: 0
        val nextState = states[nextStateId]

        // 4. Kiểm tra các mẫu NO bị cấm hoàn tất tại trạng thái mới
        if (nextState.prohibitedPatternIds.isNotEmpty()) {
            val prohibitedResult = DfaEvaluationResult(
                decision = DfaDecision.DENY_PROHIBITED,
                nextStateId = null,
                violatedRestrictionIds = nextState.prohibitedPatternIds,
                diagnosticReason = "Transition on '$candidateEdgeId' completes prohibited NO turn restriction(s): ${nextState.prohibitedPatternIds}"
            )
            return testTransitionEvaluator?.evaluate(currentStateId, candidateEdgeId, prohibitedResult) ?: prohibitedResult
        }

        // 5. Chuyển tiếp hợp lệ
        val allowResult = DfaEvaluationResult(
            decision = DfaDecision.ALLOW,
            nextStateId = nextStateId,
            violatedRestrictionIds = emptyList(),
            diagnosticReason = null
        )
        return testTransitionEvaluator?.evaluate(currentStateId, candidateEdgeId, allowResult) ?: allowResult
    }

    private class BuilderNode(
        val id: Int,
        val depth: Int,
        val edgeFromParent: String? = null
    ) {
        val children = HashMap<String, BuilderNode>()
        var failLink: Int = 0
        val directNoPatterns = ArrayList<String>()
        val directOnlyObligations = ArrayList<ActiveObligation>()
    }
}

