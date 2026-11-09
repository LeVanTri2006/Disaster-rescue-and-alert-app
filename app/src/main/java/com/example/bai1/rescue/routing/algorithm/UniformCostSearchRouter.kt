package com.example.bai1.rescue.routing.algorithm

import com.example.bai1.rescue.routing.model.EdgeCompatibilityStatus
import com.example.bai1.rescue.routing.model.FailClosedFloodAssessmentTrustValidator
import com.example.bai1.rescue.routing.model.FloodAssessmentContext
import com.example.bai1.rescue.routing.model.FloodAssessmentTrustValidator
import com.example.bai1.rescue.routing.model.FloodFreshnessPolicy
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.LiveFloodAssessment
import com.example.bai1.rescue.routing.model.RescueRoutingContext
import com.example.bai1.rescue.routing.model.RoadCondition
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.RoutingBoundingBox
import com.example.bai1.rescue.routing.model.StrictRoutingEvaluation
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.UcsRouteResult
import com.example.bai1.rescue.routing.model.UcsRouteStatus
import java.util.PriorityQueue

/**
 * Bộ định tuyến cứu hộ sử dụng thuật toán Uniform Cost Search (UCS) trên không gian trạng thái theo cạnh
 * (Edge-based Search State), tích hợp bộ chỉ mục hạn chế rẽ OpenStreetMap (TurnRestrictionIndex) và
 * cổng an toàn Fail-Closed (Fail-Closed Safety Gate).
 *
 * KHÔNG GIAN TRẠNG THÁI (STATE SPACE):
 * Thuật toán quản lý trạng thái bằng [UcsStateKey] bao gồm:
 * - nodeId: Nút hiện tại.
 * - incomingEdgeId: Cạnh vừa đi vào nút này (null tại nút xuất phát).
 * - restrictionStateId: Mã trạng thái phân biệt DFA mở rộng cho via-way restrictions (hiện tại = 0).
 *
 * Việc theo dõi theo (nodeId, incomingEdgeId) cho phép:
 * 1. Đánh giá chính xác các hạn chế rẽ (NO_LEFT_TURN, ONLY_STRAIGHT_ON,...) từ cạnh vào sang cạnh ra.
 * 2. Cho phép một nút giao lộ xuất hiện nhiều lần trên lộ trình khi xe cứu hộ cần quay đầu hoặc đi vòng.
 * 3. Bảo đảm tính tối ưu toàn cục mà không bị trạng thái đến sớm làm nghẽn các hướng rẽ hợp lệ khác.
 *
 * CỔNG AN TOÀN FAIL-CLOSED (STRICT RESCUE ROUTING GATE):
 * Để bảo đảm tuyệt đối tính mạng cho đội cứu hộ trong thiên tai:
 * 1. Đồ thị phải đáp ứng đầy đủ điều kiện nghiêm ngặt (canAttemptStrictRouting = true, overallStrictRoutingReadiness = READY).
 * 2. TurnRestrictionIndex phải tương thích hoàn toàn (isRestrictionIndexCompatible = true, không còn restriction chưa giải quyết).
 * 3. Chỉ được phép lưu thông qua các cạnh đã được xác minh tương thích xe cứu hộ (compatibilityStatus == VERIFIED_COMPATIBLE).
 *    Mọi cạnh UNVERIFIED và BLOCKED đều bị loại bỏ hoàn toàn.
 * 4. Điều kiện thiên tai (RoadCondition):
 *    - CLEAR: Được phép lưu thông (hệ số rủi ro = 1.0).
 *    - BLOCKED: Loại bỏ hoàn toàn.
 *    - UNKNOWN: Loại bỏ hoàn toàn.
 *    - FLOODED: Trong chế độ cứu hộ nghiêm ngặt, đoạn ngập TUYỆT ĐỐI KHÔNG tự động cho phép qua hệ số 50.0
 *      nếu chưa có xác nhận thực địa an toàn (isEligibleForStrictRouting = true hoặc thẻ field_verified).
 *      Nếu không có xác nhận thực địa, đoạn ngập bị loại bỏ.
 *
 * @property geometryToleranceMeters Ngưỡng dung sai khoảng cách (mét) để xác minh tính nhất quán giữa
 * geometry của cạnh và vị trí thực tế của nút giao lộ. Mặc định là 10.0m.
 */
class UniformCostSearchRouter(
    val geometryToleranceMeters: Double = DEFAULT_GEOMETRY_TOLERANCE_METERS
) {

    init {
        require(geometryToleranceMeters.isFinite()) {
            "Dung sai hình học (geometryToleranceMeters) phải là số hữu hạn, nhận được: $geometryToleranceMeters"
        }
        require(geometryToleranceMeters >= 0.0) {
            "Dung sai hình học (geometryToleranceMeters) không được âm, nhận được: $geometryToleranceMeters"
        }
    }

    companion object {
        const val RISK_MULTIPLIER_CLEAR = 1.0
        const val RISK_MULTIPLIER_FLOODED = 50.0

        /**
         * Cờ an toàn nghiêm ngặt cấp hệ thống (Fail-Closed Safety Gate).
         * Luôn luôn là true cho đến khi toàn bộ đồ thị được thẩm định an toàn thực địa.
         */
        const val STRICT_ROUTING_DISABLED: Boolean = true

        /**
         * Ngưỡng dung sai khoảng cách (10.0 mét) để xác minh tính nhất quán giữa
         * các điểm mút của geometry và tọa độ của hai nút giao lộ (RoadNode).
         */
        const val DEFAULT_GEOMETRY_TOLERANCE_METERS = 10.0
    }

    /**
     * Tìm kiếm tuyến đường cứu hộ an toàn nghiêm ngặt từ [startNodeId] đến [goalNodeId].
     *
     * Áp dụng toàn bộ quy tắc Fail-Closed Safety Gate và TurnRestrictionIndex.
     *
     * @param roadGraph Đồ thị mạng lưới đường giao thông.
     * @param startNodeId ID nút xuất phát.
     * @param goalNodeId ID nút đích đến.
     * @param turnRestrictionIndex Bộ chỉ mục Turn Restriction (tùy chọn, tự động build từ roadGraph nếu null).
     * @return [UcsRouteResult] chứa chi tiết kết quả định tuyến, vết đường đi và thống kê.
     */
    /**
     * Tìm kiếm tuyến đường cứu hộ an toàn nghiêm ngặt từ [startNodeId] đến [goalNodeId]
     * sử dụng snapshot ngữ cảnh [context] bất biến đã được xác minh toàn vẹn.
     *
     * @param context Ngữ cảnh định tuyến hoàn chỉnh gồm RoadGraph, TurnRestrictionIndex, ViaWayTurnRestrictionDfa và Manifest.
     * @param startNodeId ID nút xuất phát.
     * @param goalNodeId ID nút đích đến.
     * @param liveFloodAssessments Bản đồ đánh giá thực địa ngập lụt theo edgeId.
     * @param floodAssessmentContext Ngữ cảnh kiểm tra an toàn ngập (liveHazardReady, snapshot, freshness, trust validator).
     * @return [UcsRouteResult] chứa chi tiết kết quả định tuyến, vết đường đi và thống kê.
     */
    fun findRoute(
        context: RescueRoutingContext,
        startNodeId: String,
        goalNodeId: String,
        liveFloodAssessments: Map<String, LiveFloodAssessment> = emptyMap(),
        floodAssessmentContext: FloodAssessmentContext? = null
    ): UcsRouteResult {
        val roadGraph = context.roadGraph
        if (roadGraph.nodes.isEmpty()) {
            return UcsRouteResult(
                status = UcsRouteStatus.GRAPH_UNAVAILABLE,
                graphVersion = roadGraph.graphVersion
            )
        }

        if (startNodeId.isBlank() || goalNodeId.isBlank()) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        if (!roadGraph.hasNode(startNodeId) || !roadGraph.hasNode(goalNodeId)) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        val startNode = roadGraph.getNode(startNodeId)
        val goalNode = roadGraph.getNode(goalNodeId)
        if (startNode == null || goalNode == null ||
            !isValidGeoPoint(startNode.position) || !isValidGeoPoint(goalNode.position)
        ) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        // Authoritative Readiness Gate từ RescueRoutingContext
        val evaluation = context.readinessEvaluation
        if (evaluation is StrictRoutingEvaluation.Denied) {
            return UcsRouteResult(
                status = UcsRouteStatus.STRICT_ROUTING_DISABLED,
                graphVersion = roadGraph.graphVersion,
                routingDisabledReason = evaluation.detailMessage
            )
        }

        // Integrity and metadata diagnostics above never grant production authority.
        return UcsRouteResult(
            status = UcsRouteStatus.STRICT_ROUTING_DISABLED,
            graphVersion = roadGraph.graphVersion,
            routingDisabledReason = "Production qualification authority is unavailable"
        )
    }

    /**
     * Overload tìm kiếm tuyến đường với [RescueRoutingContext] và các tham số ngập lụt riêng lẻ.
     */
    fun findRoute(
        context: RescueRoutingContext,
        startNodeId: String,
        goalNodeId: String,
        liveFloodAssessments: Map<String, LiveFloodAssessment> = emptyMap(),
        currentEpochMillis: Long,
        requiredHazardSnapshotVersion: String? = null,
        liveHazardReady: Boolean = false,
        trustValidator: FloodAssessmentTrustValidator = FailClosedFloodAssessmentTrustValidator,
        freshnessPolicy: FloodFreshnessPolicy? = null
    ): UcsRouteResult {
        val fContext = FloodAssessmentContext(
            liveHazardReady = liveHazardReady,
            activeHazardSnapshotVersion = requiredHazardSnapshotVersion,
            freshnessPolicy = freshnessPolicy,
            trustValidator = trustValidator,
            currentEpochMillis = currentEpochMillis
        )
        return findRoute(
            context = context,
            startNodeId = startNodeId,
            goalNodeId = goalNodeId,
            liveFloodAssessments = liveFloodAssessments,
            floodAssessmentContext = fContext
        )
    }

    /**
     * Tìm kiếm tuyến đường cứu hộ an toàn nghiêm ngặt từ [startNodeId] đến [goalNodeId].
     *
     * Áp dụng toàn bộ quy tắc Fail-Closed Safety Gate, TurnRestrictionIndex, và thẩm định ngập thực địa.
     *
     * QUY TẮC AN TOÀN FAIL-CLOSED:
     * - Nếu đồ thị chứa via-way restriction nhưng caller không truyền [viaWayDfa]:
     *   Fail-closed trả về STRICT_ROUTING_DISABLED, tuyệt đối không fallback sang empty DFA.
     * - Nếu đồ thị không chứa via-way restriction nào và [viaWayDfa] == null:
     *   Khởi tạo empty DFA (ViaWayTurnRestrictionDfa.empty()) để duy trì tương thích ngược.
     *
     * @param roadGraph Đồ thị mạng lưới đường giao thông.
     * @param startNodeId ID nút xuất phát.
     * @param goalNodeId ID nút đích đến.
     * @param turnRestrictionIndex Bộ chỉ mục Turn Restriction (tùy chọn, tự động build từ roadGraph nếu null).
     * @param liveFloodAssessments Danh sách đánh giá thực địa ngập lụt theo edgeId.
     * @param floodAssessmentContext Ngữ cảnh kiểm tra an toàn ngập (liveHazardReady, snapshot, freshness, trust validator).
     * @param viaWayDfa Bộ máy DFA xử lý via-way restrictions (tùy chọn).
     * @return [UcsRouteResult] chứa chi tiết kết quả định tuyến, vết đường đi và thống kê.
     */
    fun findRoute(
        roadGraph: RoadGraph?,
        startNodeId: String,
        goalNodeId: String,
        turnRestrictionIndex: TurnRestrictionIndex? = null,
        liveFloodAssessments: Map<String, LiveFloodAssessment> = emptyMap(),
        floodAssessmentContext: FloodAssessmentContext? = null,
        viaWayDfa: ViaWayTurnRestrictionDfa? = null
    ): UcsRouteResult {
        // 1. Kiểm tra đầu vào cơ bản và tính sẵn sàng của đồ thị
        if (roadGraph == null || roadGraph.nodes.isEmpty()) {
            return UcsRouteResult(
                status = UcsRouteStatus.GRAPH_UNAVAILABLE,
                graphVersion = roadGraph?.graphVersion ?: ""
            )
        }

        if (startNodeId.isBlank() || goalNodeId.isBlank()) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        if (!roadGraph.hasNode(startNodeId) || !roadGraph.hasNode(goalNodeId)) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        val startNode = roadGraph.getNode(startNodeId)
        val goalNode = roadGraph.getNode(goalNodeId)
        if (startNode == null || goalNode == null ||
            !isValidGeoPoint(startNode.position) || !isValidGeoPoint(goalNode.position)
        ) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        // 2. Fail-Closed Safety Gate cho Strict Rescue Routing
        if (!roadGraph.canAttemptStrictRouting ||
            !roadGraph.isStrictRoutingEligible ||
            !roadGraph.isRestrictionStrictRoutingEligible ||
            roadGraph.overallStrictRoutingReadiness != StrictRoutingReadiness.READY ||
            roadGraph.restrictionStrictRoutingReadiness != StrictRoutingReadiness.READY
        ) {
            return UcsRouteResult(
                status = UcsRouteStatus.STRICT_ROUTING_DISABLED,
                graphVersion = roadGraph.graphVersion,
                routingDisabledReason = "RoadGraph does not meet strict routing readiness criteria (canAttemptStrictRouting=${roadGraph.canAttemptStrictRouting}, readiness=${roadGraph.overallStrictRoutingReadiness})"
            )
        }

        // Kiểm tra an toàn bắt buộc: Nếu đồ thị chứa via-way restrictions, DFA là bắt buộc
        val hasViaWayRestrictions = roadGraph.turnRestrictions.any { it.viaOsmWayIds.isNotEmpty() }
        if (hasViaWayRestrictions && viaWayDfa == null) {
            return UcsRouteResult(
                status = UcsRouteStatus.STRICT_ROUTING_DISABLED,
                graphVersion = roadGraph.graphVersion,
                routingDisabledReason = "Graph contains via-way turn restrictions but no ViaWayTurnRestrictionDfa was provided"
            )
        }

        val effectiveDfa = viaWayDfa ?: ViaWayTurnRestrictionDfa.empty()
        val index = turnRestrictionIndex ?: TurnRestrictionIndex.build(roadGraph, effectiveDfa.acceptedRestrictionIds)

        if (!index.isRestrictionIndexCompatible ||
            index.hasUnresolvedRestrictions ||
            index.undelegatedViaWayRestrictionIds.isNotEmpty() ||
            index.conditionalRestrictionIds.isNotEmpty()
        ) {
            return UcsRouteResult(
                status = UcsRouteStatus.STRICT_ROUTING_DISABLED,
                graphVersion = roadGraph.graphVersion,
                routingDisabledReason = "TurnRestrictionIndex contains unresolved restrictions, conflicts, undelegated via-way relations, or un-evaluated conditional restrictions (unresolvedCount=${index.unresolvedRestrictionIds.size}, conflictsCount=${index.conflicts.size}, undelegatedViaWayCount=${index.undelegatedViaWayRestrictionIds.size}, conditionalCount=${index.conditionalRestrictionIds.size})"
            )
        }

        // Integrity and metadata diagnostics above never grant production authority.
        return UcsRouteResult(
            status = UcsRouteStatus.STRICT_ROUTING_DISABLED,
            graphVersion = roadGraph.graphVersion,
            routingDisabledReason = "Production qualification authority is unavailable"
        )
    }

    /**
     * Tìm nút gần nhất trong đồ thị khớp với tọa độ địa lý đầu vào (Snapping to nearest graph node).
     *
     * @param roadGraph Đồ thị mạng lưới đường giao thông.
     * @param point Tọa độ địa lý cần snap.
     * @param maxRadiusMeters Bán kính tìm kiếm tối đa (mặc định 2000.0 mét).
     * @return RoadNode gần nhất nằm trong bán kính cho phép, hoặc null nếu vượt quá bán kính hoặc đồ thị rỗng.
     */
    fun findNearestNode(
        roadGraph: RoadGraph,
        point: GeoPoint,
        maxRadiusMeters: Double = 2000.0
    ): RoadNode? {
        if (!isValidGeoPoint(point) || roadGraph.nodes.isEmpty()) return null
        var bestNode: RoadNode? = null
        var minDistance = Double.POSITIVE_INFINITY

        // Tối ưu hóa: lọc nhanh delta tọa độ trước khi tính Haversine đầy đủ
        val maxDeltaDeg = (maxRadiusMeters / 111000.0) * 1.5

        for (node in roadGraph.nodes.values) {
            if (!isValidGeoPoint(node.position)) continue
            if (kotlin.math.abs(node.position.latitude - point.latitude) > maxDeltaDeg) continue
            if (kotlin.math.abs(node.position.longitude - point.longitude) > maxDeltaDeg) continue
            val dist = distanceMeters(point, node.position)
            if (dist < minDistance) {
                minDistance = dist
                bestNode = node
            }
        }

        return if (minDistance <= maxRadiusMeters) bestNode else null
    }

    /**
     * Xác định vùng bao địa lý (Bounding Box) của đồ thị.
     * Sử dụng boundingBox đã được gán trên đồ thị hoặc tính toán bao quát từ toàn bộ các nút.
     */
    fun getGraphBoundingBox(roadGraph: RoadGraph): RoutingBoundingBox? {
        if (roadGraph.boundingBox != null) return roadGraph.boundingBox
        if (roadGraph.nodes.isEmpty()) return null
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (node in roadGraph.nodes.values) {
            if (!isValidGeoPoint(node.position)) continue
            if (node.position.latitude < minLat) minLat = node.position.latitude
            if (node.position.latitude > maxLat) maxLat = node.position.latitude
            if (node.position.longitude < minLon) minLon = node.position.longitude
            if (node.position.longitude > maxLon) maxLon = node.position.longitude
        }
        if (!minLat.isFinite() || !maxLat.isFinite()) return null
        return RoutingBoundingBox(
            minLon = minLon,
            minLat = minLat,
            maxLon = maxLon,
            maxLat = maxLat,
            bufferedMeters = 500.0
        )
    }

    /**
     * Kiểm tra xem một tọa độ địa lý có nằm trong phạm vi bao phủ an toàn của đồ thị hay không.
     *
     * @param roadGraph Đồ thị đường bộ.
     * @param point Tọa độ cần kiểm tra.
     * @param bufferMeters Khoảng cách dung sai mở rộng ngoài biên đồ thị (mặc định 1000.0 mét).
     * @return true nếu nằm trong vùng bao phủ, false nếu nằm ngoài phạm vi (GRAPH_COVERAGE_MISSING).
     */
    fun isPointWithinCoverage(
        roadGraph: RoadGraph,
        point: GeoPoint,
        bufferMeters: Double = 1000.0
    ): Boolean {
        if (!isValidGeoPoint(point)) return false
        val bbox = getGraphBoundingBox(roadGraph) ?: return false
        val latBuffer = bufferMeters / 111139.0
        val cosLat = kotlin.math.cos(Math.toRadians(point.latitude)).coerceAtLeast(0.1)
        val lonBuffer = bufferMeters / (111139.0 * cosLat)
        return point.latitude in (bbox.minLat - latBuffer)..(bbox.maxLat + latBuffer) &&
                point.longitude in (bbox.minLon - lonBuffer)..(bbox.maxLon + lonBuffer)
    }

    /**
     * Tính toán tuyến đường bằng thuật toán Uniform Cost Search (UCS) trong chế độ chẩn đoán / mô phỏng.
     *
     * TUYỆT ĐỐI KHÔNG CẤP QUYỀN ĐIỀU HƯỚNG CỨU HỘ THỰC TẾ (NOT_AUTHORIZED).
     * Cổng an toàn nghiêm ngặt (Strict Routing Safety Gate) trên luồng [findRoute] production vẫn hoàn toàn fail-closed.
     * Phục vụ trực quan hóa đường đi và kiểm tra tính toán UCS trên bản đồ trong môi trường test/debug.
     */
    fun computeDiagnosticRoute(
        roadGraph: RoadGraph?,
        startNodeId: String,
        goalNodeId: String,
        turnRestrictionIndex: TurnRestrictionIndex? = null,
        viaWayDfa: ViaWayTurnRestrictionDfa? = null,
        allowUnverifiedForDiagnostic: Boolean = false
    ): UcsRouteResult {
        if (roadGraph == null || roadGraph.nodes.isEmpty()) {
            return UcsRouteResult(
                status = UcsRouteStatus.GRAPH_UNAVAILABLE,
                graphVersion = roadGraph?.graphVersion ?: ""
            )
        }

        if (startNodeId.isBlank() || goalNodeId.isBlank()) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        if (!roadGraph.hasNode(startNodeId) || !roadGraph.hasNode(goalNodeId)) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        val startNode = roadGraph.getNode(startNodeId)
        val goalNode = roadGraph.getNode(goalNodeId)
        if (startNode == null || goalNode == null ||
            !isValidGeoPoint(startNode.position) || !isValidGeoPoint(goalNode.position)
        ) {
            return UcsRouteResult(
                status = UcsRouteStatus.INVALID_INPUT,
                graphVersion = roadGraph.graphVersion
            )
        }

        val effectiveDfa = viaWayDfa ?: ViaWayTurnRestrictionDfa.empty()
        val index = turnRestrictionIndex ?: TurnRestrictionIndex.build(roadGraph, effectiveDfa.acceptedRestrictionIds)

        return executeSearchInternal(
            roadGraph = roadGraph,
            startNodeId = startNodeId,
            goalNodeId = goalNodeId,
            turnRestrictionIndex = index,
            viaWayDfa = effectiveDfa,
            liveFloodAssessments = emptyMap(),
            floodAssessmentContext = null,
            allowUnverifiedForDiagnostic = allowUnverifiedForDiagnostic
        )
    }

    /**
     * Thực thi thuật toán Uniform Cost Search (UCS) với đầy đủ 5 Safety Gates:
     * Gate 1: Edge & Node Safety (tính hợp lệ, VERIFIED_COMPATIBLE, không BLOCKED/UNKNOWN).
     * Gate 2: Via-node Turn Restriction (TurnRestrictionIndex).
     * Gate 3: Via-way Turn Restriction (ViaWayTurnRestrictionDfa - Aho-Corasick + Active ONLY Obligations).
     * Gate 4: Flooded Edge Safety (LiveFloodAssessment trust và freshness).
     * Gate 5: Geometry Validation & Normalization.
     */
    // Computational implementation only. No production strict entry point calls this kernel.
    private fun executeSearch(
        roadGraph: RoadGraph,
        startNodeId: String,
        goalNodeId: String,
        turnRestrictionIndex: TurnRestrictionIndex,
        viaWayDfa: ViaWayTurnRestrictionDfa,
        liveFloodAssessments: Map<String, LiveFloodAssessment>,
        floodAssessmentContext: FloodAssessmentContext?
    ): UcsRouteResult {
        return executeSearchInternal(
            roadGraph = roadGraph,
            startNodeId = startNodeId,
            goalNodeId = goalNodeId,
            turnRestrictionIndex = turnRestrictionIndex,
            viaWayDfa = viaWayDfa,
            liveFloodAssessments = liveFloodAssessments,
            floodAssessmentContext = floodAssessmentContext,
            allowUnverifiedForDiagnostic = false
        )
    }

    private fun executeSearchInternal(
        roadGraph: RoadGraph,
        startNodeId: String,
        goalNodeId: String,
        turnRestrictionIndex: TurnRestrictionIndex,
        viaWayDfa: ViaWayTurnRestrictionDfa,
        liveFloodAssessments: Map<String, LiveFloodAssessment>,
        floodAssessmentContext: FloodAssessmentContext?,
        allowUnverifiedForDiagnostic: Boolean = false
    ): UcsRouteResult {
        // 3. Trường hợp đặc biệt: Start trùng Goal
        if (startNodeId == goalNodeId) {
            val startNode = roadGraph.getNode(startNodeId)!!
            val geometry = listOf(startNode.position)
            return UcsRouteResult(
                status = UcsRouteStatus.SUCCESS,
                pathNodeIds = listOf(startNodeId),
                pathEdges = emptyList(),
                pathGeometry = geometry,
                totalCost = 0.0,
                totalDistanceMeters = 0.0,
                visitedNodesCount = 1,
                blockedEdgesSkipped = 0,
                floodedEdgesOnRoute = 0,
                turnRestrictionsSkipped = 0,
                graphVersion = roadGraph.graphVersion
            )
        }

        // 4. Khởi tạo Edge-based UCS State
        val bestCost = mutableMapOf<UcsStateKey, Double>()
        val bestDistance = mutableMapOf<UcsStateKey, Double>()
        val parentMap = mutableMapOf<UcsStateKey, ParentTransition>()

        val rootStateId = viaWayDfa.rootStateId
        if (!viaWayDfa.isValidStateId(rootStateId)) {
            return UcsRouteResult(
                status = UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE,
                routingDisabledReason = "DFA internal safety failure: rootStateId $rootStateId is not a valid state in DFA (valid range: 0 until ${viaWayDfa.stateCount})"
            )
        }
        val startKey = UcsStateKey(nodeId = startNodeId, incomingEdgeId = null, restrictionStateId = rootStateId)
        bestCost[startKey] = 0.0
        bestDistance[startKey] = 0.0

        var insertionCounter = 0L
        val priorityQueue = PriorityQueue<UcsSearchNode>()
        priorityQueue.add(UcsSearchNode(startKey, 0.0, insertionCounter++))

        var visitedNodesCount = 0
        var blockedEdgesSkipped = 0
        var turnRestrictionsSkipped = 0

        while (priorityQueue.isNotEmpty()) {
            val current = priorityQueue.poll() ?: break

            // Kiểm tra tính toàn vẹn của DFA restrictionStateId trong trạng thái lấy ra từ queue
            if (!viaWayDfa.isValidStateId(current.stateKey.restrictionStateId)) {
                return UcsRouteResult(
                    status = UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE,
                    routingDisabledReason = "DFA internal safety failure: popped state has invalid restrictionStateId: ${current.stateKey.restrictionStateId} (valid range: 0 until ${viaWayDfa.stateCount})"
                )
            }

            // Kiểm tra tính hữu hạn của chi phí lấy ra
            if (!current.costG.isFinite() || current.costG < 0.0) {
                continue
            }

            // Stale entry detection theo UcsStateKey
            val currentBestCost = bestCost[current.stateKey] ?: Double.POSITIVE_INFINITY
            if (current.costG > currentBestCost) {
                continue
            }

            visitedNodesCount++

            // Kiểm tra đến đích (Destination Semantics):
            // Khi state lấy ra có nodeId == goalNodeId, đạt đích thành công trước khi mở rộng outgoing edges.
            // Active ONLY obligations không ngăn cản việc hoàn thành tuyến đường tại goalNodeId.
            if (current.stateKey.nodeId == goalNodeId) {
                return reconstructResult(
                    startNodeId = startNodeId,
                    goalStateKey = current.stateKey,
                    rootStateId = rootStateId,
                    roadGraph = roadGraph,
                    parentMap = parentMap,
                    totalCost = current.costG,
                    totalDistance = bestDistance[current.stateKey] ?: 0.0,
                    visitedNodesCount = visitedNodesCount,
                    blockedEdgesSkipped = blockedEdgesSkipped,
                    turnRestrictionsSkipped = turnRestrictionsSkipped
                )
            }

            // 5. Duyệt các cạnh đi ra từ node hiện tại
            val outgoingEdges = roadGraph.getOutgoingEdges(current.stateKey.nodeId)
            for (edge in outgoingEdges) {
                // Gate 1: Edge & Node Safety
                if (!edge.isValid) {
                    continue
                }

                val fromNode = roadGraph.getNode(edge.fromNodeId) ?: continue
                val toNode = roadGraph.getNode(edge.toNodeId) ?: continue
                if (!isValidGeoPoint(fromNode.position) || !isValidGeoPoint(toNode.position)) {
                    continue
                }

                // Tiêu chí 1: Chỉ cho phép các cạnh đã VERIFIED_COMPATIBLE (hoặc cho phép trong chế độ chẩn đoán)
                if (!allowUnverifiedForDiagnostic && edge.compatibilityStatus != EdgeCompatibilityStatus.VERIFIED_COMPATIBLE) {
                    continue
                }

                // Tiêu chí 2: Loại bỏ cạnh BLOCKED, UNKNOWN
                if (edge.condition == RoadCondition.BLOCKED) {
                    blockedEdgesSkipped++
                    continue
                }

                if (!allowUnverifiedForDiagnostic && edge.condition == RoadCondition.UNKNOWN) {
                    continue
                }

                // Gate 2: Kiểm tra hạn chế rẽ tại nút giao (Via-node Turn Restriction)
                if (current.stateKey.incomingEdgeId != null) {
                    val transitionCheck = turnRestrictionIndex.checkTransition(
                        incomingEdgeId = current.stateKey.incomingEdgeId,
                        outgoingEdgeId = edge.id,
                        viaNodeId = current.stateKey.nodeId
                    )
                    if (transitionCheck.decision != TransitionDecision.ALLOW) {
                        turnRestrictionsSkipped++
                        continue
                    }
                }

                // Gate 3: Kiểm tra hạn chế rẽ qua nhiều cạnh (Via-way Turn Restriction DFA)
                val dfaResult = viaWayDfa.evaluateTransition(
                    currentStateId = current.stateKey.restrictionStateId,
                    candidateEdgeId = edge.id
                )

                when (dfaResult.decision) {
                    DfaDecision.DENY_PROHIBITED,
                    DfaDecision.DENY_NOT_IN_ALLOWED_SET,
                    DfaDecision.DENY_CONFLICT -> {
                        turnRestrictionsSkipped++
                        continue
                    }
                    DfaDecision.DENY_INVALID_STATE -> {
                        return UcsRouteResult(
                            status = UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE,
                            routingDisabledReason = "DFA internal safety failure: invalid state ID ${current.stateKey.restrictionStateId} during transition on edge '${edge.id}': ${dfaResult.diagnosticReason}"
                        )
                    }
                    DfaDecision.DENY_INVALID_EDGE -> {
                        return UcsRouteResult(
                            status = UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE,
                            routingDisabledReason = "DFA internal safety failure: invalid candidate edge ID '${edge.id}': ${dfaResult.diagnosticReason}"
                        )
                    }
                    DfaDecision.ALLOW -> {
                        // Tiếp tục kiểm tra nextStateId
                    }
                }

                // Xử lý nextStateId bắt buộc:
                // TUYỆT ĐỐI KHÔNG dùng nextStateId ?: 0, KHÔNG reset về ROOT
                val nextDfaStateId = dfaResult.nextStateId
                if (nextDfaStateId == null) {
                    return UcsRouteResult(
                        status = UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE,
                        routingDisabledReason = "DFA internal safety failure: transition allowed on edge '${edge.id}' but returned null nextStateId"
                    )
                }
                if (!viaWayDfa.isValidStateId(nextDfaStateId)) {
                    return UcsRouteResult(
                        status = UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE,
                        routingDisabledReason = "DFA internal safety failure: transition allowed on edge '${edge.id}' returned out-of-range nextStateId: $nextDfaStateId (valid range: 0 until ${viaWayDfa.stateCount})"
                    )
                }

                // Gate 4: Cạnh ngập lụt (FLOODED) bắt buộc phải có xác nhận thực địa hiện hành có thẩm quyền
                if (edge.condition == RoadCondition.FLOODED) {
                    val isPassable = isFloodedEdgePassableWithLiveAssessment(
                        edge = edge,
                        liveFloodAssessments = liveFloodAssessments,
                        context = floodAssessmentContext
                    )
                    if (!isPassable) {
                        continue
                    }
                }

                // Gate 5: Xác thực và chuẩn hóa geometry của cạnh
                val normalizedGeometry = validateAndNormalizeGeometry(
                    edge = edge,
                    fromPos = fromNode.position,
                    toPos = toNode.position
                )
                if (normalizedGeometry == null) {
                    continue
                }

                val riskMultiplier = when (edge.condition) {
                    RoadCondition.CLEAR -> RISK_MULTIPLIER_CLEAR
                    RoadCondition.FLOODED -> RISK_MULTIPLIER_FLOODED
                    else -> if (allowUnverifiedForDiagnostic) RISK_MULTIPLIER_CLEAR else continue
                }

                // 6. Tính toán chi phí chuyển tiếp và khoảng cách (Chỉ tính sau khi TẤT CẢ các gates đã ALLOW)
                val edgeCost = edge.lengthMeters * riskMultiplier
                if (!edgeCost.isFinite() || edgeCost < 0.0) {
                    continue
                }

                val newCost = current.costG + edgeCost
                val newDistance = (bestDistance[current.stateKey] ?: 0.0) + edge.lengthMeters

                if (!newCost.isFinite() || !newDistance.isFinite() || newCost < 0.0 || newDistance < 0.0) {
                    continue
                }

                // 7. Tạo state key kế tiếp với restrictionStateId từ DFA
                val nextStateKey = UcsStateKey(
                    nodeId = edge.toNodeId,
                    incomingEdgeId = edge.id,
                    restrictionStateId = nextDfaStateId
                )

                val existingBestCost = bestCost[nextStateKey] ?: Double.POSITIVE_INFINITY
                if (newCost < existingBestCost) {
                    bestCost[nextStateKey] = newCost
                    bestDistance[nextStateKey] = newDistance
                    parentMap[nextStateKey] = ParentTransition(
                        previousStateKey = current.stateKey,
                        traversedEdge = edge
                    )
                    priorityQueue.add(UcsSearchNode(nextStateKey, newCost, insertionCounter++))
                }
            }
        }

        // Hàng đợi rỗng mà không đến được Goal -> NO_PATH
        return UcsRouteResult(
            status = UcsRouteStatus.NO_PATH,
            visitedNodesCount = visitedNodesCount,
            blockedEdgesSkipped = blockedEdgesSkipped,
            turnRestrictionsSkipped = turnRestrictionsSkipped,
            graphVersion = roadGraph.graphVersion
        )
    }

    /**
     * Tái tạo lộ trình từ [goalStateKey] ngược về [startNodeId] qua [parentMap].
     *
     * Hỗ trợ đầy đủ:
     * - Truy vết theo [UcsStateKey].
     * - Cho phép cùng một nodeId xuất hiện nhiều lần nếu incomingEdgeId hoặc restrictionStateId khác nhau.
     * - Chống lặp chu trình vô hạn bằng tập hợp [seenStates] kiểu `Set<UcsStateKey>`.
     * - Chuẩn hóa và nối chuỗi polyline hình học liên tục, chính xác từ Start đến Goal.
     */
    // Result assembly is a computational helper, never a strict authorization decision.
    internal fun reconstructResult(
        startNodeId: String,
        goalStateKey: UcsStateKey,
        rootStateId: Int = 0,
        roadGraph: RoadGraph,
        parentMap: Map<UcsStateKey, ParentTransition>,
        totalCost: Double,
        totalDistance: Double,
        visitedNodesCount: Int,
        blockedEdgesSkipped: Int,
        turnRestrictionsSkipped: Int
    ): UcsRouteResult {
        val startStateKey = UcsStateKey(nodeId = startNodeId, incomingEdgeId = null, restrictionStateId = rootStateId)
        val pathEdgesReversed = mutableListOf<RoadEdge>()
        val seenStates = mutableSetOf<UcsStateKey>()
        var currState = goalStateKey

        while (currState != startStateKey) {
            if (!seenStates.add(currState)) {
                // Phát hiện chu trình lặp vô hạn giữa các trạng thái
                return UcsRouteResult(
                    status = UcsRouteStatus.NO_PATH,
                    visitedNodesCount = visitedNodesCount,
                    blockedEdgesSkipped = blockedEdgesSkipped,
                    turnRestrictionsSkipped = turnRestrictionsSkipped,
                    graphVersion = roadGraph.graphVersion
                )
            }
            val transition = parentMap[currState] ?: return UcsRouteResult(
                status = UcsRouteStatus.NO_PATH,
                visitedNodesCount = visitedNodesCount,
                blockedEdgesSkipped = blockedEdgesSkipped,
                turnRestrictionsSkipped = turnRestrictionsSkipped,
                graphVersion = roadGraph.graphVersion
            )
            pathEdgesReversed.add(transition.traversedEdge)
            currState = transition.previousStateKey
        }

        val pathEdges = pathEdgesReversed.reversed()
        val pathNodeIds = mutableListOf<String>()
        pathNodeIds.add(startNodeId)
        for (edge in pathEdges) {
            pathNodeIds.add(edge.toNodeId)
        }

        // Ghép chuỗi tọa độ geometry liên tục, đúng chiều từ start đến goal
        val pathGeometry = mutableListOf<GeoPoint>()
        for (edge in pathEdges) {
            val fromPos = roadGraph.getNode(edge.fromNodeId)?.position
            val toPos = roadGraph.getNode(edge.toNodeId)?.position
            val edgePoints = validateAndNormalizeGeometry(edge, fromPos, toPos)
            if (edgePoints == null || edgePoints.isEmpty()) {
                // Không cho phép tuyến SUCCESS thiếu dữ liệu polyline
                return UcsRouteResult(
                    status = UcsRouteStatus.NO_PATH,
                    visitedNodesCount = visitedNodesCount,
                    blockedEdgesSkipped = blockedEdgesSkipped,
                    turnRestrictionsSkipped = turnRestrictionsSkipped,
                    graphVersion = roadGraph.graphVersion
                )
            }

            for (point in edgePoints) {
                // Không nhân đôi điểm nối giao lộ giữa 2 cạnh kế tiếp
                if (pathGeometry.isEmpty() || pathGeometry.last() != point) {
                    pathGeometry.add(point)
                }
            }
        }

        val floodedCount = pathEdges.count { it.condition == RoadCondition.FLOODED }

        return UcsRouteResult(
            status = UcsRouteStatus.SUCCESS,
            pathNodeIds = pathNodeIds,
            pathEdges = pathEdges,
            pathGeometry = pathGeometry,
            totalCost = totalCost,
            totalDistanceMeters = totalDistance,
            visitedNodesCount = visitedNodesCount,
            blockedEdgesSkipped = blockedEdgesSkipped,
            turnRestrictionsSkipped = turnRestrictionsSkipped,
            floodedEdgesOnRoute = floodedCount,
            graphVersion = roadGraph.graphVersion
        )
    }

    /**
     * Overload tìm kiếm tuyến đường cứu hộ hỗ trợ truyền các tham số riêng rẽ về thẩm định ngập.
     * Tự động khởi tạo [FloodAssessmentContext] tương ứng.
     */
    fun findRoute(
        roadGraph: RoadGraph?,
        startNodeId: String,
        goalNodeId: String,
        turnRestrictionIndex: TurnRestrictionIndex? = null,
        liveFloodAssessments: Map<String, LiveFloodAssessment> = emptyMap(),
        currentEpochMillis: Long,
        requiredHazardSnapshotVersion: String? = null,
        liveHazardReady: Boolean = false,
        trustValidator: FloodAssessmentTrustValidator = FailClosedFloodAssessmentTrustValidator,
        freshnessPolicy: FloodFreshnessPolicy? = null,
        viaWayDfa: ViaWayTurnRestrictionDfa? = null
    ): UcsRouteResult {
        val context = FloodAssessmentContext(
            liveHazardReady = liveHazardReady,
            activeHazardSnapshotVersion = requiredHazardSnapshotVersion,
            freshnessPolicy = freshnessPolicy,
            trustValidator = trustValidator,
            currentEpochMillis = currentEpochMillis
        )
        return findRoute(
            roadGraph = roadGraph,
            startNodeId = startNodeId,
            goalNodeId = goalNodeId,
            turnRestrictionIndex = turnRestrictionIndex,
            liveFloodAssessments = liveFloodAssessments,
            floodAssessmentContext = context,
            viaWayDfa = viaWayDfa
        )
    }

    /**
     * Xác minh tính hợp lệ, nguồn gốc thẩm quyền và độ tươi mới của đánh giá thực địa đối với đoạn đường ngập (FLOODED).
     *
     * Áp dụng FAIL-CLOSED ROAD CONDITION SAFETY GATE:
     * 1. context != null và liveHazardReady == true
     * 2. activeHazardSnapshotVersion không rỗng
     * 3. freshnessPolicy != null (chính sách thời hạn hiệu lực)
     * 4. assessment != null và edgeId trùng khớp
     * 5. isPassableForRescueTruck == true
     * 6. maxWaterDepthMeters (nếu có) phải hữu hạn và >= 0.0
     * 7. trustValidator.isTrusted(assessment) == true
     * 8. assessment.hazardSnapshotVersion khớp activeHazardSnapshotVersion
     * 9. assessment.isFreshAccordingToPolicy(currentEpochMillis, freshnessPolicy) == true
     */
    internal fun isFloodedEdgePassableWithLiveAssessment(
        edge: RoadEdge,
        liveFloodAssessments: Map<String, LiveFloodAssessment>,
        context: FloodAssessmentContext?
    ): Boolean {
        if (context == null || !context.liveHazardReady) {
            return false
        }

        val activeSnapshot = context.activeHazardSnapshotVersion
        if (activeSnapshot.isNullOrBlank()) {
            return false
        }

        val policy = context.freshnessPolicy ?: return false

        val assessment = liveFloodAssessments[edge.id] ?: return false
        if (assessment.edgeId != edge.id) {
            return false
        }

        if (!assessment.isPassableForRescueTruck) {
            return false
        }

        val depth = assessment.maxWaterDepthMeters
        if (depth != null && (!depth.isFinite() || depth < 0.0)) {
            return false
        }

        if (!context.trustValidator.isTrusted(assessment)) {
            return false
        }

        val assessmentSnapshot = assessment.hazardSnapshotVersion
        if (assessmentSnapshot.isNullOrBlank() || assessmentSnapshot.trim() != activeSnapshot.trim()) {
            return false
        }

        return assessment.isFreshAccordingToPolicy(context.currentEpochMillis, policy)
    }

    /**
     * Xác thực và phân loại geometry của một cạnh (RoadEdge).
     */
    internal fun validateAndNormalizeGeometry(
        edge: RoadEdge,
        fromPos: GeoPoint?,
        toPos: GeoPoint?
    ): List<GeoPoint>? {
        val rawGeom = edge.geometry

        // Trường hợp A: geometry rỗng
        if (rawGeom.isEmpty()) {
            return if (fromPos != null && toPos != null && isValidGeoPoint(fromPos) && isValidGeoPoint(toPos)) {
                listOf(fromPos, toPos)
            } else {
                null
            }
        }

        // Nếu geometry không rỗng, cả hai nút đầu cuối phải tồn tại và có tọa độ WGS84 hợp lệ
        if (fromPos == null || toPos == null || !isValidGeoPoint(fromPos) || !isValidGeoPoint(toPos)) {
            return null
        }

        // Đoạn đường có geometry chi tiết phải có tối thiểu 2 điểm
        if (rawGeom.size < 2) {
            return null
        }

        // Kiểm tra toàn bộ tất cả tọa độ trong geometry (kể cả các điểm ở giữa)
        for (point in rawGeom) {
            if (!isValidGeoPoint(point)) {
                return null
            }
        }

        val first = rawGeom.first()
        val last = rawGeom.last()

        val distFirstFrom = distanceMeters(first, fromPos)
        val distLastTo = distanceMeters(last, toPos)
        val distFirstTo = distanceMeters(first, toPos)
        val distLastFrom = distanceMeters(last, fromPos)

        if (!distFirstFrom.isFinite() || !distLastTo.isFinite() ||
            !distFirstTo.isFinite() || !distLastFrom.isFinite()
        ) {
            return null
        }

        val forwardError = distFirstFrom + distLastTo
        val reversedError = distFirstTo + distLastFrom

        val isForward = forwardError <= reversedError &&
                distFirstFrom <= geometryToleranceMeters &&
                distLastTo <= geometryToleranceMeters

        val isReversed = reversedError < forwardError &&
                distFirstTo <= geometryToleranceMeters &&
                distLastFrom <= geometryToleranceMeters

        if (!isForward && !isReversed) {
            return null
        }

        // Bán kính giới hạn không gian
        val maxPointRadius = maxOf(edge.lengthMeters * 1.5, edge.lengthMeters + geometryToleranceMeters)
        var polylineLength = 0.0

        for (i in 0 until rawGeom.size) {
            val pt = rawGeom[i]
            val dFrom = distanceMeters(pt, fromPos)
            val dTo = distanceMeters(pt, toPos)
            if (dFrom > maxPointRadius || dTo > maxPointRadius) {
                return null
            }

            if (i < rawGeom.size - 1) {
                val segDist = distanceMeters(pt, rawGeom[i + 1])
                if (!segDist.isFinite() || segDist < 0.0) {
                    return null
                }
                polylineLength += segDist
            }
        }

        // Kiểm tra tổng chiều dài polyline: không được vượt quá 2 lần chiều dài thực của đoạn đường
        val maxPolylineLength = maxOf(edge.lengthMeters * 2.0, edge.lengthMeters + geometryToleranceMeters * 2.0)
        if (polylineLength > maxPolylineLength) {
            return null
        }

        return if (isForward) {
            val normalized = mutableListOf<GeoPoint>()
            normalized.add(fromPos)
            for (i in 1 until rawGeom.size - 1) {
                normalized.add(rawGeom[i])
            }
            normalized.add(toPos)
            normalized
        } else {
            val reversed = rawGeom.reversed()
            val normalized = mutableListOf<GeoPoint>()
            normalized.add(fromPos)
            for (i in 1 until reversed.size - 1) {
                normalized.add(reversed[i])
            }
            normalized.add(toPos)
            normalized
        }
    }

    /**
     * Xác thực tọa độ WGS84.
     */
    internal fun isValidGeoPoint(point: GeoPoint?): Boolean {
        if (point == null) return false
        val lat = point.latitude
        val lon = point.longitude
        return lat.isFinite() && lon.isFinite() &&
                lat >= -90.0 && lat <= 90.0 &&
                lon >= -180.0 && lon <= 180.0
    }

    /**
     * Tính khoảng cách trắc địa an toàn theo công thức Haversine giữa 2 tọa độ (tính bằng mét).
     */
    internal fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        if (!isValidGeoPoint(p1) || !isValidGeoPoint(p2)) {
            return Double.POSITIVE_INFINITY
        }
        if (p1.latitude == p2.latitude && p1.longitude == p2.longitude) {
            return 0.0
        }
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val sinDLat2 = Math.sin(dLat / 2.0)
        val sinDLon2 = Math.sin(dLon / 2.0)

        val a = sinDLat2 * sinDLat2 +
                sinDLon2 * sinDLon2 * Math.cos(lat1Rad) * Math.cos(lat2Rad)

        val clampedA = a.coerceIn(0.0, 1.0)
        val c = 2.0 * Math.atan2(Math.sqrt(clampedA), Math.sqrt(1.0 - clampedA))
        val distance = 6371000.0 * c

        return if (distance.isFinite() && distance >= 0.0) distance else Double.POSITIVE_INFINITY
    }
}
