package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.algorithm.DfaBuildResult
import com.example.bai1.rescue.routing.algorithm.DfaDecision
import com.example.bai1.rescue.routing.algorithm.DfaEvaluationResult
import com.example.bai1.rescue.routing.algorithm.ParentTransition
import com.example.bai1.rescue.routing.algorithm.TransitionDecision
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.UcsSearchNode
import com.example.bai1.rescue.routing.algorithm.UcsStateKey
import com.example.bai1.rescue.routing.algorithm.UniformCostSearchRouter
import com.example.bai1.rescue.routing.algorithm.ViaWayPattern
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.model.EdgeCompatibilityStatus
import com.example.bai1.rescue.routing.model.EdgeMetadata
import com.example.bai1.rescue.routing.model.EdgeSafetySummary
import com.example.bai1.rescue.routing.model.EdgeTransition
import com.example.bai1.rescue.routing.model.FailClosedFloodAssessmentProvenanceVerifier
import com.example.bai1.rescue.routing.model.FailClosedFloodAssessmentTrustValidator
import com.example.bai1.rescue.routing.model.FloodAssessmentContext
import com.example.bai1.rescue.routing.model.FloodAssessmentProvenanceVerifier
import com.example.bai1.rescue.routing.model.FloodAssessmentTrustValidator
import com.example.bai1.rescue.routing.model.FloodFreshnessPolicy
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.LiveFloodAssessment
import com.example.bai1.rescue.routing.model.PolicyBasedFloodAssessmentTrustValidator
import com.example.bai1.rescue.routing.model.RescueRoutingContext
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoadCondition
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.RoutingBoundingBox
import com.example.bai1.rescue.routing.model.RoutingBundleInfo
import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.UcsRouteStatus
import com.example.bai1.rescue.routing.model.UnsupportedRestrictionsSummary
import com.example.bai1.rescue.routing.model.ViaWayCompilationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bộ kiểm thử đơn vị độc lập cho thuật toán Uniform Cost Search (UCS) định tuyến cứu hộ
 * trên không gian trạng thái theo cạnh (Edge-based Search State) và tích hợp TurnRestrictionIndex.
 */
/**
 * P5-A01R: fixture computations inspect the PRIVATE production kernel only after asserting that
 * the real strict API denies the identical input. Computational SUCCESS is NOT authorization.
 * Acceptance tests use findRoute directly; no test approval seam is added to main sources.
 */
class UniformCostSearchRouterTest {

    private lateinit var router: UniformCostSearchRouter

    @Before
    fun setUp() {
        router = UniformCostSearchRouter()
    }

    private fun createNode(id: String, lat: Double = 16.0, lon: Double = 108.0): RoadNode {
        return RoadNode(id = id, position = GeoPoint(lat, lon))
    }

    private fun createEdge(
        id: String,
        fromNodeId: String,
        toNodeId: String,
        lengthMeters: Double,
        geometry: List<GeoPoint> = emptyList(),
        condition: RoadCondition = RoadCondition.CLEAR,
        osmWayId: Long = 0L,
        compatibilityStatus: EdgeCompatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE,
        isEligibleForStrictRouting: Boolean = true,
        metadata: EdgeMetadata = EdgeMetadata()
    ): RoadEdge = RoadEdge(
        id = id,
        fromNodeId = fromNodeId,
        toNodeId = toNodeId,
        lengthMeters = lengthMeters,
        geometry = geometry,
        condition = condition,
        osmWayId = osmWayId,
        compatibilityStatus = compatibilityStatus,
        isEligibleForStrictRouting = isEligibleForStrictRouting,
        metadata = metadata
    )

    private fun createGraph(
        nodesList: List<RoadNode>,
        directedEdges: List<RoadEdge>,
        graphVersion: String = "test-1.0",
        turnRestrictions: List<TurnRestriction> = emptyList(),
        canAttemptStrictRouting: Boolean = true,
        isStrictRoutingEligible: Boolean = true,
        isRestrictionStrictRoutingEligible: Boolean = true,
        overallStrictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.READY,
        restrictionStrictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.READY
    ): RoadGraph = RoadGraph(
        nodes = nodesList.associateBy { it.id },
        directedEdges = directedEdges,
        graphVersion = graphVersion,
        turnRestrictions = turnRestrictions,
        canAttemptStrictRouting = canAttemptStrictRouting,
        isStrictRoutingEligible = isStrictRoutingEligible,
        isRestrictionStrictRoutingEligible = isRestrictionStrictRoutingEligible,
        strictRoutingReadiness = overallStrictRoutingReadiness,
        overallStrictRoutingReadiness = overallStrictRoutingReadiness,
        restrictionStrictRoutingReadiness = restrictionStrictRoutingReadiness,
        edgeSafetySummary = EdgeSafetySummary(
            totalEdges = directedEdges.size.toLong(),
            verifiedCompatibleEdges = directedEdges.size.toLong(),
            unverifiedEdges = 0L,
            blockedEdges = 0L,
            conflictedEdges = 0L,
            strictSafeEdgesPercent = 100.0,
            blockedWays = 0L
        )
    )

    /**
     * Bộ xác minh chữ ký giả lập có kiểm soát dùng trong Unit Test.
     * TUYỆT ĐỐI KHÔNG phải là cơ chế chữ ký production.
     */
    class TestControlledProvenanceVerifier(
        private val shouldThrowException: Boolean = false,
        private val registeredSignatures: MutableMap<String, String> = mutableMapOf()
    ) : FloodAssessmentProvenanceVerifier {

        fun registerSignature(assessment: LiveFloodAssessment, signature: String) {
            registeredSignatures[assessment.createCanonicalProvenancePayload()] = signature
        }

        override fun verifyProvenance(assessment: LiveFloodAssessment): Boolean {
            if (shouldThrowException) {
                throw RuntimeException("Simulated cryptographic failure in test verifier")
            }
            val sig = assessment.provenanceSignature?.trim() ?: return false
            if (sig.isEmpty()) return false
            val canonicalPayload = assessment.createCanonicalProvenancePayload()
            val expectedSig = registeredSignatures[canonicalPayload] ?: computeSimulatedSignature(canonicalPayload)
            return sig == expectedSig
        }

        companion object {
            fun computeSimulatedSignature(payload: String): String {
                return "sim_sig_${payload.hashCode().toUInt()}"
            }
        }
    }

    private fun createValidFloodContext(
        now: Long = 1730700000000L,
        liveHazardReady: Boolean = true,
        activeHazardSnapshotVersion: String? = "hazard_snapshot_v1",
        freshnessPolicy: FloodFreshnessPolicy? = FloodFreshnessPolicy(
            maxAllowedTtlMillis = 300000L,
            maxAgeMillis = 600000L
        ),
        provenanceVerifier: FloodAssessmentProvenanceVerifier? = TestControlledProvenanceVerifier(),
        trustValidator: FloodAssessmentTrustValidator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = provenanceVerifier
        )
    ): FloodAssessmentContext = FloodAssessmentContext(
        liveHazardReady = liveHazardReady,
        activeHazardSnapshotVersion = activeHazardSnapshotVersion,
        freshnessPolicy = freshnessPolicy,
        trustValidator = trustValidator,
        currentEpochMillis = now
    )

    private fun createValidAssessment(
        edgeId: String = "e_flood",
        authorizedSource: String = "BAN_CHI_HUY_PCTT_DA_NANG",
        now: Long = 1730700000000L,
        verifiedAtEpochMillis: Long = now - 1000L,
        expiresAtEpochMillis: Long = now + 60000L,
        isPassableForRescueTruck: Boolean = true,
        maxWaterDepthMeters: Double? = 0.2,
        hazardSnapshotVersion: String? = "hazard_snapshot_v1",
        hasValidProvenance: Boolean = true,
        provenanceSignature: String? = null
    ): LiveFloodAssessment {
        val unsigned = LiveFloodAssessment(
            edgeId = edgeId,
            authorizedSource = authorizedSource,
            verifiedAtEpochMillis = verifiedAtEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            isPassableForRescueTruck = isPassableForRescueTruck,
            maxWaterDepthMeters = maxWaterDepthMeters,
            hazardSnapshotVersion = hazardSnapshotVersion,
            hasValidProvenance = hasValidProvenance,
            provenanceSignature = null
        )
        val sig = provenanceSignature ?: TestControlledProvenanceVerifier.computeSimulatedSignature(
            unsigned.createCanonicalProvenancePayload()
        )
        return unsigned.copy(provenanceSignature = sig)
    }

    private fun createNoTurnRestriction(
        id: String,
        type: TurnRestrictionType = TurnRestrictionType.NO_LEFT_TURN,
        fromEdgeId: String,
        toEdgeId: String,
        viaNodeId: String,
        supportStatus: RestrictionSupportStatus = RestrictionSupportStatus.FULLY_SUPPORTED
    ): TurnRestriction = TurnRestriction(
        id = id,
        type = type,
        supportStatus = supportStatus,
        prohibitedTransitions = listOf(
            EdgeTransition(fromEdgeId = fromEdgeId, toEdgeId = toEdgeId, viaNodeId = viaNodeId)
        )
    )

    private fun createOnlyTurnRestriction(
        id: String,
        type: TurnRestrictionType = TurnRestrictionType.ONLY_STRAIGHT_ON,
        fromEdgeId: String,
        allowedToEdgeIds: List<String>,
        viaNodeId: String,
        supportStatus: RestrictionSupportStatus = RestrictionSupportStatus.FULLY_SUPPORTED
    ): TurnRestriction = TurnRestriction(
        id = id,
        type = type,
        supportStatus = supportStatus,
        allowedTransitions = allowedToEdgeIds.map { toEdgeId ->
            EdgeTransition(fromEdgeId = fromEdgeId, toEdgeId = toEdgeId, viaNodeId = viaNodeId)
        }
    )

    // =========================================================================
    // PHẦN 1: 34 BÀI TEST UCS GỐC (ĐÃ CẬP NHẬT FIXTURE ĐẠT ĐIỀU KIỆN KIỂM THỬ)
    // =========================================================================

    // 1. Tìm đường trực tiếp thông thoáng (Direct clear route)
    @Test
    fun testDirectClearRoute() {
        val nodeA = createNode("A", 16.0, 108.0)
        val nodeB = createNode("B", 16.01, 108.01)
        val edgeAB = createEdge(
            id = "e_AB",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 150.0,
            condition = RoadCondition.CLEAR
        )
        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(edgeAB),
            graphVersion = "test-1.0"
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B"), result.pathNodeIds)
        assertEquals(1, result.pathEdges.size)
        assertEquals("e_AB", result.pathEdges[0].id)
        assertEquals(150.0, result.totalCost, 0.001)
        assertEquals(150.0, result.totalDistanceMeters, 0.001)
        assertEquals(0, result.floodedEdgesOnRoute)
        assertEquals(0, result.blockedEdgesSkipped)
    }

    // 2. Đường ngắn bị ngập và đường vòng thông thoáng
    @Test
    fun testShortFloodedVsLongClearDetour() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")

        // Tuyến trực tiếp: A -> B ngắn (100m) nhưng bị ngập (FLOODED). Có xác nhận thực địa. Chi phí = 100 * 50 = 5000
        val edgeDirectFlooded = createEdge(
            id = "e_direct",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 100.0,
            condition = RoadCondition.FLOODED,
            isEligibleForStrictRouting = true
        )

        // Tuyến vòng: A -> C (200m) và C -> B (200m) thông thoáng (CLEAR). Chi phí = 200 + 200 = 400
        val edgeAC = createEdge("e_AC", "A", "C", 200.0, condition = RoadCondition.CLEAR)
        val edgeCB = createEdge("e_CB", "C", "B", 200.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(edgeDirectFlooded, edgeAC, edgeCB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        // UCS phải chọn đường vòng A -> C -> B vì chi phí 400 < 5000 mặc dù khoảng cách dài hơn (400m > 100m)
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "C", "B"), result.pathNodeIds)
        assertEquals(400.0, result.totalCost, 0.001)
        assertEquals(400.0, result.totalDistanceMeters, 0.001)
        assertEquals(0, result.floodedEdgesOnRoute)

        // Kiểm tra chiều ngược lại: nếu đường vòng quá dài (6000m = chi phí 6000), UCS sẽ chấp nhận đường ngập có xác nhận thực địa có thẩm quyền (chi phí 5000)
        val edgeLongAC = createEdge("e_long_AC", "A", "C", 3000.0, condition = RoadCondition.CLEAR)
        val edgeLongCB = createEdge("e_long_CB", "C", "B", 3000.0, condition = RoadCondition.CLEAR)
        val graph2 = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(edgeDirectFlooded, edgeLongAC, edgeLongCB)
        )
        val now = 1730700000000L
        val floodContext = createValidFloodContext(now = now)
        val validAssessment = createValidAssessment(
            edgeId = "e_direct",
            now = now
        )
        val result2 = router.inspectFixtureComputation(
            roadGraph = graph2,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_direct" to validAssessment),
            floodAssessmentContext = floodContext
        )
        assertEquals(UcsRouteStatus.SUCCESS, result2.status)
        assertEquals(listOf("A", "B"), result2.pathNodeIds)
        assertEquals(5000.0, result2.totalCost, 0.001)
        assertEquals(100.0, result2.totalDistanceMeters, 0.001)
        assertEquals(1, result2.floodedEdgesOnRoute)
    }

    // 3. Đường bị phong tỏa (BLOCKED)
    @Test
    fun testBlockedRoadSkipped() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")

        // A -> B bị phong tỏa hoàn toàn
        val edgeBlocked = createEdge("e_blocked", "A", "B", 100.0, condition = RoadCondition.BLOCKED)
        // Đường vòng A -> C -> B
        val edgeAC = createEdge("e_AC", "A", "C", 200.0, condition = RoadCondition.CLEAR)
        val edgeCB = createEdge("e_CB", "C", "B", 300.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(edgeBlocked, edgeAC, edgeCB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "C", "B"), result.pathNodeIds)
        assertEquals(500.0, result.totalCost, 0.001)
        assertEquals(500.0, result.totalDistanceMeters, 0.001)
        assertTrue("Phải ghi nhận số cạnh BLOCKED bị bỏ qua", result.blockedEdgesSkipped >= 1)
    }

    // 4. Không có đường đi (NO_PATH)
    @Test
    fun testNoPathAvailable() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")
        val nodeD = createNode("D")

        // Hai cụm đồ thị bị ngắt quãng hoàn toàn: A -> B và C -> D
        val edgeAB = createEdge("e_AB", "A", "B", 100.0, condition = RoadCondition.CLEAR)
        val edgeCD = createEdge("e_CD", "C", "D", 100.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC, nodeD),
            directedEdges = listOf(edgeAB, edgeCD)
        )

        val result = router.inspectFixtureComputation(graph, "A", "D")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathNodeIds.isEmpty())
        assertTrue(result.pathEdges.isEmpty())
        assertTrue(result.pathGeometry.isEmpty())
        assertEquals(0.0, result.totalCost, 0.001)
        assertEquals(0.0, result.totalDistanceMeters, 0.001)
    }

    // 5. Có nhiều tuyến và phải chọn tổng chi phí thấp nhất
    @Test
    fun testMultipleRoutesLowestCostSelection() {
        val nodes = listOf("A", "B", "C", "D", "E").map { createNode(it) }

        // Tuyến 1: A -> B -> D (10 + 15 = 25)
        val eAB = createEdge("e_AB", "A", "B", 10.0, condition = RoadCondition.CLEAR)
        val eBD = createEdge("e_BD", "B", "D", 15.0, condition = RoadCondition.CLEAR)

        // Tuyến 2: A -> C -> D (5 + 30 = 35)
        val eAC = createEdge("e_AC", "A", "C", 5.0, condition = RoadCondition.CLEAR)
        val eCD = createEdge("e_CD", "C", "D", 30.0, condition = RoadCondition.CLEAR)

        // Tuyến 3: A -> E -> D (12 + 10 = 22) -> Tối ưu nhất
        val eAE = createEdge("e_AE", "A", "E", 12.0, condition = RoadCondition.CLEAR)
        val eED = createEdge("e_ED", "E", "D", 10.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = nodes,
            directedEdges = listOf(eAB, eBD, eAC, eCD, eAE, eED)
        )

        val result = router.inspectFixtureComputation(graph, "A", "D")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "E", "D"), result.pathNodeIds)
        assertEquals(22.0, result.totalCost, 0.001)
        assertEquals(22.0, result.totalDistanceMeters, 0.001)
    }

    // 6. Đường một chiều không được đi ngược
    @Test
    fun testOneWayRoadCannotBeTraversedInReverse() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")

        // Cạnh 1 chiều chỉ từ A -> B
        val edgeAB = createEdge("e_AB", "A", "B", 100.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(edgeAB)
        )

        val forwardResult = router.inspectFixtureComputation(graph, "A", "B")
        assertEquals(UcsRouteStatus.SUCCESS, forwardResult.status)

        val reverseResult = router.inspectFixtureComputation(graph, "B", "A")
        assertEquals(UcsRouteStatus.NO_PATH, reverseResult.status)
    }

    // 7. Start trùng Goal
    @Test
    fun testStartEqualsGoal() {
        val nodeA = createNode("A", 16.0544, 108.2022)
        val graph = createGraph(
            nodesList = listOf(nodeA),
            directedEdges = emptyList(),
            graphVersion = "v-single"
        )

        val result = router.inspectFixtureComputation(graph, "A", "A")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A"), result.pathNodeIds)
        assertTrue(result.pathEdges.isEmpty())
        assertEquals(1, result.pathGeometry.size)
        assertEquals(GeoPoint(16.0544, 108.2022), result.pathGeometry[0])
        assertEquals(0.0, result.totalCost, 0.001)
        assertEquals(0.0, result.totalDistanceMeters, 0.001)
        assertEquals(0, result.floodedEdgesOnRoute)
    }

    // 8. Cạnh có dữ liệu không hợp lệ (Negative length, NaN, Infinity)
    @Test
    fun testInvalidEdgeDataSkipped() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")
        val nodeD = createNode("D")

        // Cạnh lỗi: khoảng cách âm
        val edgeNegative = createEdge("e_neg", "A", "B", -50.0, condition = RoadCondition.CLEAR)
        // Cạnh lỗi: khoảng cách NaN
        val edgeNaN = createEdge("e_nan", "A", "C", Double.NaN, condition = RoadCondition.CLEAR)
        // Cạnh lỗi: khoảng cách Infinity
        val edgeInf = createEdge("e_inf", "A", "C", Double.POSITIVE_INFINITY, condition = RoadCondition.CLEAR)

        // Cạnh hợp lệ: A -> D (100m) và D -> B (100m)
        val edgeAD = createEdge("e_AD", "A", "D", 100.0, condition = RoadCondition.CLEAR)
        val edgeDB = createEdge("e_DB", "D", "B", 100.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC, nodeD),
            directedEdges = listOf(edgeNegative, edgeNaN, edgeInf, edgeAD, edgeDB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "D", "B"), result.pathNodeIds)
        assertEquals(200.0, result.totalCost, 0.001)
        assertEquals(200.0, result.totalDistanceMeters, 0.001)
    }

    // 9. PriorityQueue có phần tử cũ (Stale entries)
    @Test
    fun testPriorityQueueStaleEntryHandling() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")
        val nodeD = createNode("D")

        val eAB = createEdge("e_AB", "A", "B", 100.0, condition = RoadCondition.CLEAR)
        val eAC = createEdge("e_AC", "A", "C", 10.0, condition = RoadCondition.CLEAR)
        val eCB = createEdge("e_CB", "C", "B", 20.0, condition = RoadCondition.CLEAR)
        val eBD = createEdge("e_BD", "B", "D", 200.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC, nodeD),
            directedEdges = listOf(eAB, eAC, eCB, eBD)
        )

        val result = router.inspectFixtureComputation(graph, "A", "D")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "C", "B", "D"), result.pathNodeIds)
        assertEquals(230.0, result.totalCost, 0.001)
        assertEquals(230.0, result.totalDistanceMeters, 0.001)
        // Trong edge-based UCS: (A, null), (C, e_AC), (B, e_CB), (B, e_AB) và (D, e_BD) đều là các state hợp lệ
        assertEquals(5, result.visitedNodesCount)
    }

    // 10. Truy vết parentEdge chính xác
    @Test
    fun testAccurateParentEdgeBacktracking() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")

        val eHighway = createEdge("e_highway", "A", "B", 250.0, condition = RoadCondition.CLEAR)
        val eShortcut = createEdge("e_shortcut", "A", "B", 120.0, condition = RoadCondition.CLEAR)
        val eFlooded = createEdge("e_flooded", "A", "B", 50.0, condition = RoadCondition.FLOODED, isEligibleForStrictRouting = true) // 50 * 50 = 2500

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(eHighway, eShortcut, eFlooded)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(1, result.pathEdges.size)
        assertEquals("e_shortcut", result.pathEdges[0].id)
        assertEquals(120.0, result.totalCost, 0.001)
    }

    // 11. Geometry được ghép đúng chiều
    @Test
    fun testGeometryProperlyConcatenatedInCorrectDirection() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.1, 108.1)
        val posC = GeoPoint(16.2, 108.2)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)
        val nodeC = RoadNode("C", posC)

        val geomAB = listOf(posA, GeoPoint(16.05, 108.05), posB)
        val geomBC = listOf(posB, GeoPoint(16.15, 108.15), posC)

        val eAB = createEdge("e_AB", "A", "B", 16000.0, geometry = geomAB, condition = RoadCondition.CLEAR)
        val eBC = createEdge("e_BC", "B", "C", 16000.0, geometry = geomBC, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(eAB, eBC)
        )

        val result = router.inspectFixtureComputation(graph, "A", "C")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B", "C"), result.pathNodeIds)
        assertEquals(5, result.pathGeometry.size)
        assertEquals(posA, result.pathGeometry[0])
        assertEquals(GeoPoint(16.05, 108.05), result.pathGeometry[1])
        assertEquals(posB, result.pathGeometry[2])
        assertEquals(GeoPoint(16.15, 108.15), result.pathGeometry[3])
        assertEquals(posC, result.pathGeometry[4])
    }

    // 12. Đồ thị thiếu node hoặc cạnh không hợp lệ
    @Test
    fun testMissingNodesAndInvalidGraphInput() {
        val nodeA = createNode("A")
        val graph = createGraph(nodesList = listOf(nodeA), directedEdges = emptyList(), graphVersion = "v1")

        // 12.1. Đồ thị null hoặc rỗng
        assertEquals(UcsRouteStatus.GRAPH_UNAVAILABLE, router.findRoute(null, "A", "B").status)
        val emptyGraph = RoadGraph(nodes = emptyMap(), directedEdges = emptyList(), graphVersion = "v0")
        assertEquals(UcsRouteStatus.GRAPH_UNAVAILABLE, router.findRoute(emptyGraph, "A", "B").status)

        // 12.2. StartNode hoặc GoalNode không tồn tại trong đồ thị
        assertEquals(UcsRouteStatus.INVALID_INPUT, router.findRoute(graph, "NON_EXIST", "A").status)
        assertEquals(UcsRouteStatus.INVALID_INPUT, router.findRoute(graph, "A", "NON_EXIST").status)
    }

    // 13. Phòng ngừa chu trình lặp vô hạn (Cycle and loop safety)
    @Test
    fun testLoopAndCycleSafety() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")
        val nodeD = createNode("D")

        val eAB = createEdge("e_AB", "A", "B", 10.0, condition = RoadCondition.CLEAR)
        val eBC = createEdge("e_BC", "B", "C", 10.0, condition = RoadCondition.CLEAR)
        val eCA = createEdge("e_CA", "C", "A", 10.0, condition = RoadCondition.CLEAR)
        val eSelf = createEdge("e_self", "B", "B", 5.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC, nodeD),
            directedEdges = listOf(eAB, eBC, eCA, eSelf)
        )

        val result = router.inspectFixtureComputation(graph, "A", "D")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 14. Trường hợp C - Geometry bị đảo chiều
    @Test
    fun testGeometryReversedNormalized() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.002, 108.002)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val midPoint = GeoPoint(16.001, 108.001)
        val rawReversedGeom = listOf(posB, midPoint, posA)

        val edgeAB = createEdge("e_AB", "A", "B", 320.0, geometry = rawReversedGeom, condition = RoadCondition.CLEAR)
        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(edgeAB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B"), result.pathNodeIds)
        assertEquals(3, result.pathGeometry.size)
        assertEquals(posA, result.pathGeometry[0])
        assertEquals(midPoint, result.pathGeometry[1])
        assertEquals(posB, result.pathGeometry[2])
    }

    // 15. Trường hợp A - Geometry rỗng
    @Test
    fun testGeometryEmptyFallback() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.05, 108.05)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val edgeAB = createEdge("e_AB", "A", "B", 100.0, geometry = emptyList(), condition = RoadCondition.CLEAR)
        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(edgeAB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(2, result.pathGeometry.size)
        assertEquals(posA, result.pathGeometry[0])
        assertEquals(posB, result.pathGeometry[1])
    }

    // 16. Geometry sai lệch bị loại bỏ hoàn toàn khỏi UCS
    @Test
    fun testGeometryMismatchedRejectedSafely() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.002, 108.002)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val bogusGeom = listOf(GeoPoint(21.0, 105.0), GeoPoint(21.01, 105.01))
        val edgeAB = createEdge("e_AB", "A", "B", 100.0, geometry = bogusGeom, condition = RoadCondition.CLEAR)

        val graphOnlyBogus = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(edgeAB)
        )
        val resultNoPath = router.inspectFixtureComputation(graphOnlyBogus, "A", "B")
        assertEquals(UcsRouteStatus.NO_PATH, resultNoPath.status)
        assertTrue(resultNoPath.pathEdges.isEmpty())
        assertTrue(resultNoPath.pathNodeIds.isEmpty())
        assertTrue(resultNoPath.pathGeometry.isEmpty())

        val posC = GeoPoint(16.001, 108.001)
        val nodeC = RoadNode("C", posC)

        val geomAC = listOf(posA, posC)
        val geomCB = listOf(posC, posB)
        val eAC = createEdge("e_AC", "A", "C", 150.0, geometry = geomAC, condition = RoadCondition.CLEAR)
        val eCB = createEdge("e_CB", "C", "B", 150.0, geometry = geomCB, condition = RoadCondition.CLEAR)

        val graphWithDetour = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(edgeAB, eAC, eCB)
        )

        val detourResult = router.inspectFixtureComputation(graphWithDetour, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, detourResult.status)
        assertEquals(listOf("A", "C", "B"), detourResult.pathNodeIds)
        assertEquals(listOf("e_AC", "e_CB"), detourResult.pathEdges.map { it.id })
        assertEquals(300.0, detourResult.totalCost, 0.001)
        assertEquals(3, detourResult.pathGeometry.size)
        assertEquals(posA, detourResult.pathGeometry[0])
        assertEquals(posC, detourResult.pathGeometry[1])
        assertEquals(posB, detourResult.pathGeometry[2])
    }

    // 17. Nhiều cạnh liên tiếp có tọa độ nối nhau nhưng không bị lặp điểm
    @Test
    fun testGeometryMultiEdgeConcatenationNoDuplicates() {
        val pA = GeoPoint(16.0, 108.0)
        val pMidAB = GeoPoint(16.005, 108.005)
        val pB = GeoPoint(16.01, 108.01)
        val pMidBC = GeoPoint(16.015, 108.015)
        val pC = GeoPoint(16.02, 108.02)
        val pMidCD = GeoPoint(16.025, 108.025)
        val pD = GeoPoint(16.03, 108.03)

        val nodeA = RoadNode("A", pA)
        val nodeB = RoadNode("B", pB)
        val nodeC = RoadNode("C", pC)
        val nodeD = RoadNode("D", pD)

        val eAB = createEdge("e_AB", "A", "B", 1600.0, geometry = listOf(pA, pMidAB, pB))
        val eBC = createEdge("e_BC", "B", "C", 1600.0, geometry = listOf(pB, pMidBC, pC))
        val eCD = createEdge("e_CD", "C", "D", 1600.0, geometry = listOf(pC, pMidCD, pD))

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC, nodeD),
            directedEdges = listOf(eAB, eBC, eCD)
        )

        val result = router.inspectFixtureComputation(graph, "A", "D")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B", "C", "D"), result.pathNodeIds)
        assertEquals(3, result.pathEdges.size)
        assertEquals(7, result.pathGeometry.size)
        assertEquals(listOf(pA, pMidAB, pB, pMidBC, pC, pMidCD, pD), result.pathGeometry)
    }

    // 18. Kiểm tra tràn số do nhân hệ số FLOODED
    @Test
    fun testCostAndDistanceOverflowHandling() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")

        val eOverflow = createEdge("e_overflow", "A", "B", Double.MAX_VALUE, condition = RoadCondition.FLOODED, isEligibleForStrictRouting = true)
        val eAC = createEdge("e_AC", "A", "C", 200.0, condition = RoadCondition.CLEAR)
        val eCB = createEdge("e_CB", "C", "B", 200.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(eOverflow, eAC, eCB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "C", "B"), result.pathNodeIds)
        assertEquals(400.0, result.totalCost, 0.001)
        assertEquals(400.0, result.totalDistanceMeters, 0.001)
        assertTrue(result.totalCost.isFinite())
        assertTrue(result.totalDistanceMeters.isFinite())
    }

    // 19. Điểm giữa geometry có latitude NaN bị loại bỏ
    @Test
    fun testGeometryMiddlePointNaNRejected() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.01, 108.01)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val corruptGeom = listOf(posA, GeoPoint(Double.NaN, 108.005), posB)
        val edgeAB = createEdge("e_AB", "A", "B", 1600.0, geometry = corruptGeom, condition = RoadCondition.CLEAR)

        val graph = createGraph(nodesList = listOf(nodeA, nodeB), directedEdges = listOf(edgeAB))
        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 20. Điểm giữa geometry có longitude Infinity bị loại bỏ
    @Test
    fun testGeometryMiddlePointInfinityRejected() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.01, 108.01)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val corruptGeom = listOf(posA, GeoPoint(16.005, Double.POSITIVE_INFINITY), posB)
        val edgeAB = createEdge("e_AB", "A", "B", 1600.0, geometry = corruptGeom, condition = RoadCondition.CLEAR)

        val graph = createGraph(nodesList = listOf(nodeA, nodeB), directedEdges = listOf(edgeAB))
        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 21. Tọa độ ngoài phạm vi chuẩn WGS84 bị loại bỏ
    @Test
    fun testGeometryCoordinatesOutOfWGS84Rejected() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.01, 108.01)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val corruptLatGeom = listOf(posA, GeoPoint(95.0, 108.005), posB)
        val eLat = createEdge("e_lat", "A", "B", 1600.0, geometry = corruptLatGeom, condition = RoadCondition.CLEAR)
        assertEquals(UcsRouteStatus.NO_PATH, router.inspectFixtureComputation(createGraph(listOf(nodeA, nodeB), listOf(eLat)), "A", "B").status)

        val corruptLonGeom = listOf(posA, GeoPoint(16.005, 185.0), posB)
        val eLon = createEdge("e_lon", "A", "B", 1600.0, geometry = corruptLonGeom, condition = RoadCondition.CLEAR)
        assertEquals(UcsRouteStatus.NO_PATH, router.inspectFixtureComputation(createGraph(listOf(nodeA, nodeB), listOf(eLon)), "A", "B").status)
    }

    // 22. Điều chỉnh dung sai (Threshold tolerance 10m)
    @Test
    fun testGeometryToleranceThresholds() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.01, 108.01)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val geomWithinTolerance = listOf(GeoPoint(16.000036, 108.0), posB)
        val eWithin = createEdge("e_within", "A", "B", 1600.0, geometry = geomWithinTolerance, condition = RoadCondition.CLEAR)
        val resultWithin = router.inspectFixtureComputation(createGraph(listOf(nodeA, nodeB), listOf(eWithin)), "A", "B")
        assertEquals(UcsRouteStatus.SUCCESS, resultWithin.status)
        assertEquals(posA, resultWithin.pathGeometry[0])

        val geomExceedTolerance = listOf(GeoPoint(16.000225, 108.0), posB)
        val eExceed = createEdge("e_exceed", "A", "B", 1600.0, geometry = geomExceedTolerance, condition = RoadCondition.CLEAR)
        val resultExceed = router.inspectFixtureComputation(createGraph(listOf(nodeA, nodeB), listOf(eExceed)), "A", "B")
        assertEquals(UcsRouteStatus.NO_PATH, resultExceed.status)
    }

    // 23. Củng cố Haversine (Antipodal, identical, numerical stability)
    @Test
    fun testHaversineExtremeCoordinatesAndNumericalStability() {
        val p1 = GeoPoint(16.0544, 108.2022)
        val p2 = GeoPoint(16.0544, 108.2022)

        assertEquals(0.0, router.distanceMeters(p1, p2), 0.0001)

        val pEquator1 = GeoPoint(0.0, 0.0)
        val pEquator2 = GeoPoint(0.0, 180.0)
        val distAntipodal = router.distanceMeters(pEquator1, pEquator2)
        assertTrue(distAntipodal.isFinite())
        assertEquals(Math.PI * 6371000.0, distAntipodal, 1000.0)

        val invalidP = GeoPoint(Double.NaN, 0.0)
        assertTrue(router.distanceMeters(p1, invalidP).isInfinite())
    }

    // 24. Cộng dồn hai cạnh CLEAR gây tràn số
    @Test
    fun testAccumulatedCostAndDistanceOverflowBothClear() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")

        val hugeLength = Double.MAX_VALUE * 0.75
        val eAB = createEdge("e_AB", "A", "B", hugeLength, condition = RoadCondition.CLEAR)
        val eBC = createEdge("e_BC", "B", "C", hugeLength, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(eAB, eBC)
        )

        val result = router.inspectFixtureComputation(graph, "A", "C")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathNodeIds.isEmpty())
        assertTrue(result.pathEdges.isEmpty())
        assertTrue(result.pathGeometry.isEmpty())
        assertEquals(0.0, result.totalCost, 0.001)
        assertEquals(0.0, result.totalDistanceMeters, 0.001)
        assertTrue(result.totalCost.isFinite())
        assertTrue(result.totalDistanceMeters.isFinite())
    }

    // 25. Điểm giữa geometry bị lệch địa lý cực lớn (Hà Nội vs Đà Nẵng) bị loại bỏ
    @Test
    fun testGeometryIntermediateOutlierPointRejectedSinglePath() {
        val posA = GeoPoint(16.0544, 108.2022)
        val posB = GeoPoint(16.0580, 108.2060)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val posHanoi = GeoPoint(21.0285, 105.8542)
        val outlierGeom = listOf(posA, posHanoi, posB)

        val edgeAB = createEdge("e_AB", "A", "B", 600.0, geometry = outlierGeom, condition = RoadCondition.CLEAR)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(edgeAB))

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
        assertTrue(result.pathGeometry.isEmpty())
    }

    // 26. Điểm giữa outlier bị loại bỏ và UCS chọn tuyến đường vòng hợp lệ
    @Test
    fun testGeometryIntermediateOutlierPointRejectedWithValidDetour() {
        val posA = GeoPoint(16.0544, 108.2022)
        val posB = GeoPoint(16.0580, 108.2060)
        val posC = GeoPoint(16.0560, 108.2040)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)
        val nodeC = RoadNode("C", posC)

        val posHanoi = GeoPoint(21.0285, 105.8542)
        val outlierGeom = listOf(posA, posHanoi, posB)
        val directEdge = createEdge("e_direct", "A", "B", 600.0, geometry = outlierGeom, condition = RoadCondition.CLEAR)

        val eAC = createEdge("e_AC", "A", "C", 300.0, geometry = listOf(posA, posC), condition = RoadCondition.CLEAR)
        val eCB = createEdge("e_CB", "C", "B", 300.0, geometry = listOf(posC, posB), condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(directEdge, eAC, eCB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "C", "B"), result.pathNodeIds)
        assertEquals(listOf("e_AC", "e_CB"), result.pathEdges.map { it.id })
        assertEquals(600.0, result.totalCost, 0.001)
        assertEquals(3, result.pathGeometry.size)
        assertEquals(posA, result.pathGeometry[0])
        assertEquals(posC, result.pathGeometry[1])
        assertEquals(posB, result.pathGeometry[2])
    }

    // 27. Đoạn đường uốn lượn cong hợp lệ không bị từ chối
    @Test
    fun testValidCurvedRoadGeometryAccepted() {
        val posA = GeoPoint(16.0500, 108.2000)
        val p1 = GeoPoint(16.0510, 108.2015)
        val p2 = GeoPoint(16.0515, 108.2008)
        val p3 = GeoPoint(16.0525, 108.2025)
        val posB = GeoPoint(16.0530, 108.2030)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val curvedGeom = listOf(posA, p1, p2, p3, posB)
        val edgeAB = createEdge("e_AB", "A", "B", 550.0, geometry = curvedGeom, condition = RoadCondition.CLEAR)

        val graph = createGraph(listOf(nodeA, nodeB), listOf(edgeAB))
        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B"), result.pathNodeIds)
        assertEquals(5, result.pathGeometry.size)
        assertEquals(posA, result.pathGeometry[0])
        assertEquals(p1, result.pathGeometry[1])
        assertEquals(p2, result.pathGeometry[2])
        assertEquals(p3, result.pathGeometry[3])
        assertEquals(posB, result.pathGeometry[4])
    }

    // 28. Constructor từ chối dung sai âm
    @Test(expected = IllegalArgumentException::class)
    fun testConstructorRejectsNegativeTolerance() {
        UniformCostSearchRouter(geometryToleranceMeters = -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructorRejectsSmallNegativeTolerance() {
        UniformCostSearchRouter(geometryToleranceMeters = -0.0001)
    }

    // 29. Constructor từ chối dung sai NaN
    @Test(expected = IllegalArgumentException::class)
    fun testConstructorRejectsNaNTolerance() {
        UniformCostSearchRouter(geometryToleranceMeters = Double.NaN)
    }

    // 30. Constructor từ chối dung sai Infinity
    @Test(expected = IllegalArgumentException::class)
    fun testConstructorRejectsPositiveInfinityTolerance() {
        UniformCostSearchRouter(geometryToleranceMeters = Double.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructorRejectsNegativeInfinityTolerance() {
        UniformCostSearchRouter(geometryToleranceMeters = Double.NEGATIVE_INFINITY)
    }

    // 31. Constructor chấp nhận dung sai hợp lệ
    @Test
    fun testConstructorAcceptsValidTolerance() {
        val routerZero = UniformCostSearchRouter(geometryToleranceMeters = 0.0)
        assertEquals(0.0, routerZero.geometryToleranceMeters, 0.0)

        val routerDefault = UniformCostSearchRouter()
        assertEquals(UniformCostSearchRouter.DEFAULT_GEOMETRY_TOLERANCE_METERS, routerDefault.geometryToleranceMeters, 0.0)

        val routerCustom = UniformCostSearchRouter(geometryToleranceMeters = 25.5)
        assertEquals(25.5, routerCustom.geometryToleranceMeters, 0.0)
    }

    // 32. Bảo vệ reconstructResult không trả SUCCESS với geometry bị lỗi
    @Test
    fun testReconstructResultSafeguardWhenGeometryCannotBeNormalized() {
        val posA = GeoPoint(16.0, 108.0)
        val posB = GeoPoint(16.01, 108.01)

        val nodeA = RoadNode("A", posA)
        val nodeB = RoadNode("B", posB)

        val corruptEdge = createEdge(
            id = "e_corrupt",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 1600.0,
            geometry = listOf(GeoPoint(21.0, 105.0), GeoPoint(21.01, 105.01)),
            condition = RoadCondition.CLEAR
        )

        val graph = createGraph(listOf(nodeA, nodeB), listOf(corruptEdge))
        val goalKey = UcsStateKey("B", "e_corrupt", 0)
        val startKey = UcsStateKey("A", null, 0)
        val parentMap = mapOf(goalKey to ParentTransition(startKey, corruptEdge))

        val result = router.reconstructResult(
            startNodeId = "A",
            goalStateKey = goalKey,
            roadGraph = graph,
            parentMap = parentMap,
            totalCost = 1600.0,
            totalDistance = 1600.0,
            visitedNodesCount = 1,
            blockedEdgesSkipped = 0,
            turnRestrictionsSkipped = 0
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathGeometry.isEmpty())
        assertTrue(result.pathEdges.isEmpty())
        assertTrue(result.pathNodeIds.isEmpty())
    }

    // =========================================================================
    // PHẦN 2: 23 BÀI TEST MỚI (EDGE-BASED SEARCH STATE, TURN RESTRICTIONS, FAIL-CLOSED GATE)
    // =========================================================================

    // 33. Hai incoming paths đến cùng node có restriction state khác nhau
    @Test
    fun testTwoIncomingPathsToSameNodeWithDifferentRestrictionState() {
        val nodes = listOf("S", "N1", "N2", "V", "G").map { createNode(it) }

        // Path 1 qua N1: S -> N1 (40m) -> V (40m) [tổng 80m]
        val eS_N1 = createEdge("e_S_N1", "S", "N1", 40.0)
        val eN1_V = createEdge("e_N1_V", "N1", "V", 40.0)

        // Path 2 qua N2: S -> N2 (60m) -> V (60m) [tổng 120m]
        val eS_N2 = createEdge("e_S_N2", "S", "N2", 60.0)
        val eN2_V = createEdge("e_N2_V", "N2", "V", 60.0)

        // Cạnh đích: V -> G (30m)
        val eV_G = createEdge("e_V_G", "V", "G", 30.0)

        // Restriction: Cấm rẽ từ eN1_V vào eV_G tại V
        val restriction = createNoTurnRestriction(
            id = "restr_no_turn_from_N1",
            fromEdgeId = "e_N1_V",
            toEdgeId = "e_V_G",
            viaNodeId = "V"
        )

        val graph = createGraph(
            nodesList = nodes,
            directedEdges = listOf(eS_N1, eN1_V, eS_N2, eN2_V, eV_G),
            turnRestrictions = listOf(restriction)
        )

        val result = router.inspectFixtureComputation(graph, "S", "G")

        // UCS phải nhận ra state (V, e_N1_V) bị chặn sang eV_G, và dùng state (V, e_N2_V) để đến G
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("S", "N2", "V", "G"), result.pathNodeIds)
        assertEquals(listOf("e_S_N2", "e_N2_V", "e_V_G"), result.pathEdges.map { it.id })
        assertEquals(150.0, result.totalCost, 0.001)
        assertTrue(result.turnRestrictionsSkipped >= 1)
    }

    // 34. Đường đến rẻ hơn bị cấm rẽ, đường đến đắt hơn hợp lệ vẫn được giữ
    @Test
    fun testCheaperPathProhibitedMoreExpensivePathValidSucceeds() {
        val nodes = listOf("S", "Cheap", "Exp", "V", "G").map { createNode(it) }

        // Cheap path: S -> Cheap (10m) -> V (10m) = 20m
        val eS_C = createEdge("e_S_C", "S", "Cheap", 10.0)
        val eC_V = createEdge("e_C_V", "Cheap", "V", 10.0)

        // Expensive path: S -> Exp (50m) -> V (50m) = 100m
        val eS_E = createEdge("e_S_E", "S", "Exp", 50.0)
        val eE_V = createEdge("e_E_V", "Exp", "V", 50.0)

        // V -> G (20m)
        val eV_G = createEdge("e_V_G", "V", "G", 20.0)

        // Cấm Cheap -> V -> G
        val restriction = createNoTurnRestriction(
            id = "restr_ban_cheap",
            fromEdgeId = "e_C_V",
            toEdgeId = "e_V_G",
            viaNodeId = "V"
        )

        val graph = createGraph(
            nodesList = nodes,
            directedEdges = listOf(eS_C, eC_V, eS_E, eE_V, eV_G),
            turnRestrictions = listOf(restriction)
        )

        val result = router.inspectFixtureComputation(graph, "S", "G")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("S", "Exp", "V", "G"), result.pathNodeIds)
        assertEquals(120.0, result.totalCost, 0.001)
        assertEquals(120.0, result.totalDistanceMeters, 0.001)
    }

    // 35. NO_LEFT_TURN buộc chọn đường vòng
    @Test
    fun testNoLeftTurnForcesDetour() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")
        val nodeD = createNode("D")

        val eAB = createEdge("e_AB", "A", "B", 100.0)
        val eBC = createEdge("e_BC", "B", "C", 50.0) // Rẽ trái trực tiếp
        // Tuyến vòng: B -> D -> C (100 + 100 = 200m)
        val eBD = createEdge("e_BD", "B", "D", 100.0)
        val eDC = createEdge("e_DC", "D", "C", 100.0)

        val restrNoLeft = createNoTurnRestriction(
            id = "restr_no_left",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromEdgeId = "e_AB",
            toEdgeId = "e_BC",
            viaNodeId = "B"
        )

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC, nodeD),
            directedEdges = listOf(eAB, eBC, eBD, eDC),
            turnRestrictions = listOf(restrNoLeft)
        )

        val result = router.inspectFixtureComputation(graph, "A", "C")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B", "D", "C"), result.pathNodeIds)
        assertEquals(300.0, result.totalCost, 0.001)
        assertEquals(1, result.turnRestrictionsSkipped)
    }

    // 36. NO_RIGHT_TURN
    @Test
    fun testNoRightTurnForcesDetour() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeR = createNode("R")
        val nodeX = createNode("X")

        val eAB = createEdge("e_AB", "A", "B", 100.0)
        val eBR = createEdge("e_BR", "B", "R", 50.0) // Rẽ phải trực tiếp sang R

        // Đường tránh từ A qua X đến R: A -> X -> R (80 + 80 = 160m)
        val eAX = createEdge("e_AX", "A", "X", 80.0)
        val eXR = createEdge("e_XR", "X", "R", 80.0)

        val restrNoRight = createNoTurnRestriction(
            id = "restr_no_right",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            fromEdgeId = "e_AB",
            toEdgeId = "e_BR",
            viaNodeId = "B"
        )

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeR, nodeX),
            directedEdges = listOf(eAB, eBR, eAX, eXR),
            turnRestrictions = listOf(restrNoRight)
        )

        val result = router.inspectFixtureComputation(graph, "A", "R")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "X", "R"), result.pathNodeIds)
        assertEquals(160.0, result.totalCost, 0.001)
    }

    // 37. NO_STRAIGHT_ON
    @Test
    fun testNoStraightOnForcesTurn() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeStraight = createNode("S")
        val nodeDetour = createNode("Y")

        val eAB = createEdge("e_AB", "A", "B", 100.0)
        val eBS = createEdge("e_BS", "B", "S", 50.0) // Đi thẳng trực tiếp sang S

        // Tuyến vòng: B -> Y -> S (60 + 60 = 120m)
        val eBY = createEdge("e_BY", "B", "Y", 60.0)
        val eYS = createEdge("e_YS", "Y", "S", 60.0)

        val restrNoStraight = createNoTurnRestriction(
            id = "restr_no_straight",
            type = TurnRestrictionType.NO_STRAIGHT_ON,
            fromEdgeId = "e_AB",
            toEdgeId = "e_BS",
            viaNodeId = "B"
        )

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeStraight, nodeDetour),
            directedEdges = listOf(eAB, eBS, eBY, eYS),
            turnRestrictions = listOf(restrNoStraight)
        )

        val result = router.inspectFixtureComputation(graph, "A", "S")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B", "Y", "S"), result.pathNodeIds)
        assertEquals(220.0, result.totalCost, 0.001)
    }

    // 38. NO_U_TURN
    @Test
    fun testNoUTurnPreventsReverseOnSameEdge() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")
        val nodeD = createNode("D")

        val eAB = createEdge("e_AB", "A", "B", 50.0)
        val eBA = createEdge("e_BA", "B", "A", 50.0) // Cạnh quay đầu trực tiếp

        // Vòng tránh hợp lệ để quay đầu: B -> C -> D -> A (30 + 30 + 30 = 90m)
        val eBC = createEdge("e_BC", "B", "C", 30.0)
        val eCD = createEdge("e_CD", "C", "D", 30.0)
        val eDA = createEdge("e_DA", "D", "A", 30.0)

        val restrNoUTurn = createNoTurnRestriction(
            id = "restr_no_uturn",
            type = TurnRestrictionType.NO_U_TURN,
            fromEdgeId = "e_AB",
            toEdgeId = "e_BA",
            viaNodeId = "B"
        )

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC, nodeD),
            directedEdges = listOf(eAB, eBA, eBC, eCD, eDA),
            turnRestrictions = listOf(restrNoUTurn)
        )

        // Xuất phát từ A qua B để về lại A
        val result = router.inspectFixtureComputation(graph, "A", "A")

        // Điểm xuất phát trùng đích: trả về SUCCESS tại chỗ
        assertEquals(UcsRouteStatus.SUCCESS, result.status)

        // Bây giờ xét bài toán định tuyến từ A đến D qua B: không được quay ngược e_BA
        val resultToD = router.inspectFixtureComputation(graph, "A", "D")
        assertEquals(UcsRouteStatus.SUCCESS, resultToD.status)
        assertEquals(listOf("A", "B", "C", "D"), resultToD.pathNodeIds)
    }

    // 39. ONLY_STRAIGHT_ON
    @Test
    fun testOnlyStraightOnForbidsLeftAndRightTurns() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeS = createNode("S")
        val nodeL = createNode("L")
        val nodeR = createNode("R")

        val eAB = createEdge("e_AB", "A", "B", 100.0)
        val eBS = createEdge("e_BS", "B", "S", 50.0) // Đi thẳng
        val eBL = createEdge("e_BL", "B", "L", 50.0) // Rẽ trái
        val eBR = createEdge("e_BR", "B", "R", 50.0) // Rẽ phải

        val restrOnlyStraight = createOnlyTurnRestriction(
            id = "restr_only_straight",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            fromEdgeId = "e_AB",
            allowedToEdgeIds = listOf("e_BS"),
            viaNodeId = "B"
        )

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeS, nodeL, nodeR),
            directedEdges = listOf(eAB, eBS, eBL, eBR),
            turnRestrictions = listOf(restrOnlyStraight)
        )

        // Đi đến L: Bị cấm rẽ trái và không có đường vòng khác -> NO_PATH
        val resultL = router.inspectFixtureComputation(graph, "A", "L")
        assertEquals(UcsRouteStatus.NO_PATH, resultL.status)

        // Đi đến S: Được phép vì là hướng duy nhất hợp lệ
        val resultS = router.inspectFixtureComputation(graph, "A", "S")
        assertEquals(UcsRouteStatus.SUCCESS, resultS.status)
        assertEquals(listOf("A", "B", "S"), resultS.pathNodeIds)
    }

    // 40. ONLY_LEFT_TURN
    @Test
    fun testOnlyLeftTurnForbidsStraightAndRightTurns() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeS = createNode("S")
        val nodeL = createNode("L")

        val eAB = createEdge("e_AB", "A", "B", 100.0)
        val eBS = createEdge("e_BS", "B", "S", 50.0) // Đi thẳng
        val eBL = createEdge("e_BL", "B", "L", 50.0) // Rẽ trái

        val restrOnlyLeft = createOnlyTurnRestriction(
            id = "restr_only_left",
            type = TurnRestrictionType.ONLY_LEFT_TURN,
            fromEdgeId = "e_AB",
            allowedToEdgeIds = listOf("e_BL"),
            viaNodeId = "B"
        )

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeS, nodeL),
            directedEdges = listOf(eAB, eBS, eBL),
            turnRestrictions = listOf(restrOnlyLeft)
        )

        // Đi đến S: Bị cấm vì ONLY_LEFT_TURN
        val resultS = router.inspectFixtureComputation(graph, "A", "S")
        assertEquals(UcsRouteStatus.NO_PATH, resultS.status)

        // Đi đến L: Hợp lệ
        val resultL = router.inspectFixtureComputation(graph, "A", "L")
        assertEquals(UcsRouteStatus.SUCCESS, resultL.status)
        assertEquals(listOf("A", "B", "L"), resultL.pathNodeIds)
    }

    // 41. Multiple ONLY restrictions (Giao nhau / Intersection)
    @Test
    fun testMultipleOnlyRestrictionsIntersection() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val node1 = createNode("1")
        val node2 = createNode("2")
        val node3 = createNode("3")

        val eAB = createEdge("e_AB", "A", "B", 100.0)
        val eB1 = createEdge("e_B1", "B", "1", 50.0)
        val eB2 = createEdge("e_B2", "B", "2", 50.0)
        val eB3 = createEdge("e_B3", "B", "3", 50.0)

        // ONLY 1 cho phép {eB1, eB2}
        val r1 = createOnlyTurnRestriction("r1", TurnRestrictionType.ONLY_STRAIGHT_ON, "e_AB", listOf("e_B1", "e_B2"), "B")
        // ONLY 2 cho phép {eB2, eB3}
        val r2 = createOnlyTurnRestriction("r2", TurnRestrictionType.ONLY_STRAIGHT_ON, "e_AB", listOf("e_B2", "e_B3"), "B")

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, node1, node2, node3),
            directedEdges = listOf(eAB, eB1, eB2, eB3),
            turnRestrictions = listOf(r1, r2)
        )

        // Đi đến 1: Bị loại vì không nằm trong tập giao
        assertEquals(UcsRouteStatus.NO_PATH, router.inspectFixtureComputation(graph, "A", "1").status)
        // Đi đến 3: Bị loại vì không nằm trong tập giao
        assertEquals(UcsRouteStatus.NO_PATH, router.inspectFixtureComputation(graph, "A", "3").status)
        // Đi đến 2: Nằm trong tập giao {eB2} -> SUCCESS
        val result2 = router.inspectFixtureComputation(graph, "A", "2")
        assertEquals(UcsRouteStatus.SUCCESS, result2.status)
        assertEquals(listOf("A", "B", "2"), result2.pathNodeIds)
    }

    // 42. Multiple NO restrictions (Hợp nhau / Union)
    @Test
    fun testMultipleNoRestrictionsUnion() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val node1 = createNode("1")
        val node2 = createNode("2")
        val node3 = createNode("3")

        val eAB = createEdge("e_AB", "A", "B", 100.0)
        val eB1 = createEdge("e_B1", "B", "1", 50.0)
        val eB2 = createEdge("e_B2", "B", "2", 50.0)
        val eB3 = createEdge("e_B3", "B", "3", 50.0)

        // Cấm eB1 và cấm eB2
        val r1 = createNoTurnRestriction("r1", TurnRestrictionType.NO_LEFT_TURN, "e_AB", "e_B1", "B")
        val r2 = createNoTurnRestriction("r2", TurnRestrictionType.NO_RIGHT_TURN, "e_AB", "e_B2", "B")

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, node1, node2, node3),
            directedEdges = listOf(eAB, eB1, eB2, eB3),
            turnRestrictions = listOf(r1, r2)
        )

        assertEquals(UcsRouteStatus.NO_PATH, router.inspectFixtureComputation(graph, "A", "1").status)
        assertEquals(UcsRouteStatus.NO_PATH, router.inspectFixtureComputation(graph, "A", "2").status)

        val result3 = router.inspectFixtureComputation(graph, "A", "3")
        assertEquals(UcsRouteStatus.SUCCESS, result3.status)
        assertEquals(listOf("A", "B", "3"), result3.pathNodeIds)
    }

    // 43. Via-node restriction kết hợp với đường vòng
    @Test
    fun testViaNodeRestrictionWithDetourLoop() {
        val nodeS = createNode("S")
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")
        val nodeG = createNode("G")

        // S -> A -> G (đường trực tiếp ngắn nhưng A -> G bị cấm rẽ từ S -> A)
        val eSA = createEdge("e_SA", "S", "A", 100.0)
        val eAG = createEdge("e_AG", "A", "G", 100.0)

        // Vòng qua B và C để quay lại A từ hướng khác: A -> B -> C -> A -> G
        val eAB = createEdge("e_AB", "A", "B", 50.0)
        val eBC = createEdge("e_BC", "B", "C", 50.0)
        val eCA = createEdge("e_CA", "C", "A", 50.0)

        // Cấm rẽ từ eSA sang eAG tại A
        val r = createNoTurnRestriction("r", TurnRestrictionType.NO_STRAIGHT_ON, "e_SA", "e_AG", "A")

        val graph = createGraph(
            nodesList = listOf(nodeS, nodeA, nodeB, nodeC, nodeG),
            directedEdges = listOf(eSA, eAG, eAB, eBC, eCA),
            turnRestrictions = listOf(r)
        )

        val result = router.inspectFixtureComputation(graph, "S", "G")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        // Đường đi: S -> A -> B -> C -> A -> G (A xuất hiện 2 lần!)
        assertEquals(listOf("S", "A", "B", "C", "A", "G"), result.pathNodeIds)
        assertEquals(350.0, result.totalCost, 0.001)
    }

    // 44. Unresolved restriction không bị bỏ qua (Fail-closed)
    @Test
    fun testUnresolvedRestrictionCausesFailClosed() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eAB = createEdge("e_AB", "A", "B", 100.0)

        // Restriction chưa giải quyết
        val unresolvedRestr = TurnRestriction(
            id = "restr_unresolved",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            prohibitedTransitions = listOf(EdgeTransition("e_AB", "e_BC", "B"))
        )

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(eAB),
            turnRestrictions = listOf(unresolvedRestr)
        )

        val result = router.findRoute(graph, "A", "B")

        // Khi có restriction chưa giải quyết, strict routing gate phải từ chối ngay lập tức
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertNotNull(result.routingDisabledReason)
    }

    // 45. Graph readiness không đạt: strict route bị từ chối
    @Test
    fun testGraphReadinessNotMetRejectsStrictRoute() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eAB = createEdge("e_AB", "A", "B", 100.0)

        // Graph có canAttemptStrictRouting = false
        val graphNotReady = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(eAB),
            canAttemptStrictRouting = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES
        )

        val result = router.findRoute(graphNotReady, "A", "B")
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertTrue(result.routingDisabledReason!!.contains("canAttemptStrictRouting=false"))
    }

    // 46. Edge UNVERIFIED không được sử dụng trong strict mode
    @Test
    fun testUnverifiedEdgeSkippedInStrictMode() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")

        // A -> B là UNVERIFIED (nguy cơ mất an toàn)
        val eAB_Unverified = createEdge("e_AB", "A", "B", 100.0, compatibilityStatus = EdgeCompatibilityStatus.UNVERIFIED)
        // Đường vòng A -> C -> B là VERIFIED_COMPATIBLE
        val eAC = createEdge("e_AC", "A", "C", 150.0, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE)
        val eCB = createEdge("e_CB", "C", "B", 150.0, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(eAB_Unverified, eAC, eCB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        // UCS phải loại bỏ cạnh UNVERIFIED và chọn đường vòng qua C
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "C", "B"), result.pathNodeIds)
        assertEquals(300.0, result.totalCost, 0.001)

        // Nếu chỉ có duy nhất cạnh UNVERIFIED -> NO_PATH
        val graphOnlyUnverified = createGraph(listOf(nodeA, nodeB), listOf(eAB_Unverified))
        assertEquals(UcsRouteStatus.NO_PATH, router.inspectFixtureComputation(graphOnlyUnverified, "A", "B").status)
    }

    // 47. Edge BLOCKED không được sử dụng
    @Test
    fun testBlockedEdgeSkippedInStrictMode() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eBlocked = createEdge("e_blocked", "A", "B", 100.0, compatibilityStatus = EdgeCompatibilityStatus.BLOCKED)

        val graph = createGraph(listOf(nodeA, nodeB), listOf(eBlocked))
        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 48. RoadCondition UNKNOWN không được sử dụng trong strict mode
    @Test
    fun testUnknownConditionSkippedInStrictMode() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eUnknown = createEdge("e_unk", "A", "B", 100.0, condition = RoadCondition.UNKNOWN)

        val graph = createGraph(listOf(nodeA, nodeB), listOf(eUnknown))
        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 49. RoadCondition FLOODED không có xác nhận thực địa bị loại bỏ
    @Test
    fun testFloodedConditionWithoutFieldVerificationSkipped() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")

        // Cạnh FLOODED không có xác nhận thực địa (isEligibleForStrictRouting = false, không có tag)
        val eFloodedUnverified = createEdge(
            id = "e_flood_unverified",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 100.0,
            condition = RoadCondition.FLOODED,
            isEligibleForStrictRouting = false
        )

        // Đường vòng an toàn CLEAR qua C
        val eAC = createEdge("e_AC", "A", "C", 200.0, condition = RoadCondition.CLEAR)
        val eCB = createEdge("e_CB", "C", "B", 200.0, condition = RoadCondition.CLEAR)

        val graph = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(eFloodedUnverified, eAC, eCB)
        )

        val result = router.inspectFixtureComputation(graph, "A", "B")

        // Cạnh ngập chưa có xác nhận thực địa bắt buộc phải bị loại bỏ
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "C", "B"), result.pathNodeIds)
        assertEquals(0, result.floodedEdgesOnRoute)

        // Nếu cạnh ngập được thẩm định có đánh giá thực địa có thẩm quyền và còn hiệu lực
        val eFloodedVerified = createEdge(
            id = "e_flood_verified",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 5.0, // Rất ngắn, 5m * 50 = 250 < 400
            condition = RoadCondition.FLOODED
        )

        val graphWithVerifiedFlood = createGraph(
            nodesList = listOf(nodeA, nodeB, nodeC),
            directedEdges = listOf(eFloodedVerified, eAC, eCB)
        )

        val now = 1730700000000L
        val floodContext = createValidFloodContext(now = now)
        val assessment = createValidAssessment(
            edgeId = "e_flood_verified",
            now = now
        )

        val resultVerified = router.inspectFixtureComputation(
            roadGraph = graphWithVerifiedFlood,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood_verified" to assessment),
            floodAssessmentContext = floodContext
        )
        assertEquals(UcsRouteStatus.SUCCESS, resultVerified.status)
        assertEquals(listOf("A", "B"), resultVerified.pathNodeIds)
        assertEquals(250.0, resultVerified.totalCost, 0.001)
        assertEquals(1, resultVerified.floodedEdgesOnRoute)
    }

    // 50. Cùng một node có thể xuất hiện hai lần trong route nếu state khác nhau và route hợp lệ
    @Test
    fun testSameNodeAppearsTwiceInRouteWithDifferentStates() {
        val nodeS = createNode("S")
        val nodeX = createNode("X")
        val nodeY = createNode("Y")
        val nodeZ = createNode("Z")
        val nodeG = createNode("G")

        // S -> X -> Y -> Z -> X -> G
        val eSX = createEdge("e_SX", "S", "X", 100.0)
        val eXY = createEdge("e_XY", "X", "Y", 100.0)
        val eYZ = createEdge("e_YZ", "Y", "Z", 100.0)
        val eZX = createEdge("e_ZX", "Z", "X", 100.0)
        val eXG = createEdge("e_XG", "X", "G", 100.0)

        // Cấm rẽ trực tiếp từ eSX sang eXG tại X: Buộc phải đi qua vòng Y -> Z -> X rồi mới ra eXG
        val restrNoDirectExit = createNoTurnRestriction(
            id = "restr_no_direct_exit",
            type = TurnRestrictionType.NO_STRAIGHT_ON,
            fromEdgeId = "e_SX",
            toEdgeId = "e_XG",
            viaNodeId = "X"
        )

        val graph = createGraph(
            nodesList = listOf(nodeS, nodeX, nodeY, nodeZ, nodeG),
            directedEdges = listOf(eSX, eXY, eYZ, eZX, eXG),
            turnRestrictions = listOf(restrNoDirectExit)
        )

        val result = router.inspectFixtureComputation(graph, "S", "G")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("S", "X", "Y", "Z", "X", "G"), result.pathNodeIds)
        assertEquals(5, result.pathEdges.size)
        assertEquals(500.0, result.totalCost, 0.001)
        assertEquals(500.0, result.totalDistanceMeters, 0.001)
    }

    // 51. Route reconstruction theo parent state
    @Test
    fun testRouteReconstructionByParentState() {
        val pS = GeoPoint(16.0, 108.0)
        val pA = GeoPoint(16.01, 108.01)
        val pG = GeoPoint(16.02, 108.02)

        val nodeS = RoadNode("S", pS)
        val nodeA = RoadNode("A", pA)
        val nodeG = RoadNode("G", pG)

        val eSA = createEdge("e_SA", "S", "A", 1600.0, geometry = listOf(pS, pA))
        val eAG = createEdge("e_AG", "A", "G", 1600.0, geometry = listOf(pA, pG))

        val graph = createGraph(listOf(nodeS, nodeA, nodeG), listOf(eSA, eAG))

        val result = router.inspectFixtureComputation(graph, "S", "G")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("S", "A", "G"), result.pathNodeIds)
        assertEquals(listOf("e_SA", "e_AG"), result.pathEdges.map { it.id })
        assertEquals(3, result.pathGeometry.size)
        assertEquals(pS, result.pathGeometry[0])
        assertEquals(pA, result.pathGeometry[1])
        assertEquals(pG, result.pathGeometry[2])
    }

    // 52. Không có đường hợp lệ trả NO_PATH
    @Test
    fun testNoValidRouteReturnsNoPathOrSafeStatus() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eAB = createEdge("e_AB", "A", "B", 100.0)

        // Không có cạnh nào nối sang C
        val nodeC = createNode("C")
        val graph = createGraph(listOf(nodeA, nodeB, nodeC), listOf(eAB))

        val result = router.inspectFixtureComputation(graph, "A", "C")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 53. Priority queue stale detection theo state
    @Test
    fun testPriorityQueueStaleDetectionByStateKey() {
        val nodeS = createNode("S")
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeMid = createNode("Mid")
        val nodeGoal = createNode("Goal")
        val nodeZ = createNode("Z")

        // Tuyến chậm đến Mid: S -> A (100m) -> Mid (10m, qua e_A_Mid)
        val eSA = createEdge("e_SA", "S", "A", 100.0)
        val eA_Mid = createEdge("e_A_Mid", "A", "Mid", 10.0)

        // Tuyến nhanh đến Mid: S -> B (20m) -> Mid (20m, qua e_B_Mid)
        val eSB = createEdge("e_SB", "S", "B", 20.0)
        val eB_Mid = createEdge("e_B_Mid", "B", "Mid", 20.0)

        // Từ Mid vào Goal qua cùng cạnh e_Mid_G (10m) -> cùng state key: (Goal, e_Mid_G, 0)
        val eMid_G = createEdge("e_Mid_G", "Mid", "Goal", 10.0)
        // Từ Goal đến Z (200m)
        val eG_Z = createEdge("e_G_Z", "Goal", "Z", 200.0)

        val graph = createGraph(
            nodesList = listOf(nodeS, nodeA, nodeB, nodeMid, nodeGoal, nodeZ),
            directedEdges = listOf(eSA, eA_Mid, eSB, eB_Mid, eMid_G, eG_Z)
        )

        val result = router.inspectFixtureComputation(graph, "S", "Z")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("S", "B", "Mid", "Goal", "Z"), result.pathNodeIds)
        // Tổng chi phí tối ưu: 20 + 20 + 10 + 200 = 250.0
        assertEquals(250.0, result.totalCost, 0.001)
    }

    // 54. Chi phí và chiều dài route được tính chính xác
    @Test
    fun testCostAndDistanceCalculatedAccurately() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val nodeC = createNode("C")

        // Cạnh 1: CLEAR, 250m -> cost = 250.0
        val eAB = createEdge("e_AB", "A", "B", 250.0, condition = RoadCondition.CLEAR)
        // Cạnh 2: FLOODED có xác nhận thực địa, 10m -> cost = 10 * 50 = 500.0
        val eBC = createEdge("e_BC", "B", "C", 10.0, condition = RoadCondition.FLOODED, isEligibleForStrictRouting = true)

        val graph = createGraph(listOf(nodeA, nodeB, nodeC), listOf(eAB, eBC))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(now = now)
        val validAssessment = createValidAssessment(
            edgeId = "e_BC",
            now = now,
            verifiedAtEpochMillis = now - 500L,
            expiresAtEpochMillis = now + 120000L
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "C",
            liveFloodAssessments = mapOf("e_BC" to validAssessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(750.0, result.totalCost, 0.001)
        assertEquals(260.0, result.totalDistanceMeters, 0.001)
        assertEquals(1, result.floodedEdgesOnRoute)
    }

    // 55. Xác nhận đồ thị production Đà Nẵng bị từ chối strict routing (Fail-Closed Safety Gate)
    @Test
    fun testProductionGraphDaNangRejectedByStrictRoutingGate() {
        val node1 = createNode("node_osm_1")
        val node2 = createNode("node_osm_2")

        // Cạnh UNVERIFIED mô phỏng đồ thị production Đà Nẵng
        val edgeProd = RoadEdge(
            id = "edge_osm_1",
            fromNodeId = "node_osm_1",
            toNodeId = "node_osm_2",
            lengthMeters = 50.0,
            compatibilityStatus = EdgeCompatibilityStatus.UNVERIFIED
        )

        // Turn restriction chưa giải quyết mô phỏng 16 unresolved restrictions của Đà Nẵng
        val unresolvedProd = TurnRestriction(
            id = "relation_osm_100",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            prohibitedTransitions = listOf(EdgeTransition("edge_osm_1", "edge_osm_2", "node_osm_2"))
        )

        val prodGraph = RoadGraph(
            nodes = mapOf("node_osm_1" to node1, "node_osm_2" to node2),
            directedEdges = listOf(edgeProd),
            graphVersion = "dn_central_real_v1",
            canAttemptStrictRouting = false,
            isStrictRoutingEligible = false,
            isRestrictionStrictRoutingEligible = false,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            turnRestrictions = listOf(unresolvedProd)
        )

        val result = router.findRoute(prodGraph, "node_osm_1", "node_osm_2")

        // Cổng an toàn BẮT BUỘC phải khóa và từ chối nghiêm ngặt!
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertNotNull(result.routingDisabledReason)
        assertTrue(result.pathNodeIds.isEmpty())
        assertTrue(result.pathEdges.isEmpty())
    }

    // =========================================================================
    // PHẦN 3: AUDIT VÀ HARDEN ROAD CONDITION SAFETY GATE TRONG UCS (10 TESTS)
    // =========================================================================

    // 56. AUDIT 1: FLOODED + isEligibleForStrictRouting=true nhưng không có field verification đáng tin cậy -> bị loại
    @Test
    fun testAuditFloodedWithIsEligibleForStrictRoutingTrueRejectedWithoutLiveAssessment() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFloodedStaticEligible = createEdge(
            id = "e_flood_static_ok",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 100.0,
            condition = RoadCondition.FLOODED,
            isEligibleForStrictRouting = true
        )

        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFloodedStaticEligible))

        val result = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = emptyMap())

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 57. AUDIT 2: FLOODED + rawTags field_verified=true nhưng không có live assessment -> bị loại
    @Test
    fun testAuditFloodedWithRawTagsFieldVerifiedRejectedWithoutLiveAssessment() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFloodedWithOsmTag = createEdge(
            id = "e_flood_osm_tag",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 100.0,
            condition = RoadCondition.FLOODED,
            metadata = EdgeMetadata(rawTags = mapOf("field_verified" to "true", "flood:passable" to "yes"))
        )

        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFloodedWithOsmTag))

        val result = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = emptyMap())

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 58. AUDIT 3: FLOODED + field assessment hết hạn -> bị loại
    @Test
    fun testAuditFloodedWithExpiredFieldAssessmentRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlooded = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlooded))

        val now = 1730700000000L
        val expiredAssessment = LiveFloodAssessment(
            edgeId = "e_flood",
            authorizedSource = "BAN_CHI_HUY_PCTT_DA_NANG",
            verifiedAtEpochMillis = now - 600000L,
            expiresAtEpochMillis = now - 300000L,
            isPassableForRescueTruck = true
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to expiredAssessment),
            currentEpochMillis = now
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 59. AUDIT 4: FLOODED + assessment không gắn đúng edge -> bị loại
    @Test
    fun testAuditFloodedWithMismatchedEdgeAssessmentRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlooded = createEdge("e_flood_AB", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlooded))

        val now = 1730700000000L
        val assessmentOther = LiveFloodAssessment(
            edgeId = "e_flood_OTHER",
            authorizedSource = "BAN_CHI_HUY_PCTT_DA_NANG",
            verifiedAtEpochMillis = now - 1000L,
            expiresAtEpochMillis = now + 60000L,
            isPassableForRescueTruck = true
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood_AB" to assessmentOther),
            currentEpochMillis = now
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 60. AUDIT 5: FLOODED + không có hazard snapshot hoặc snapshot không khớp -> bị loại
    @Test
    fun testAuditFloodedWithoutRequiredHazardSnapshotRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlooded = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlooded))

        val now = 1730700000000L
        val assessmentOldSnapshot = LiveFloodAssessment(
            edgeId = "e_flood",
            authorizedSource = "BAN_CHI_HUY_PCTT_DA_NANG",
            verifiedAtEpochMillis = now - 1000L,
            expiresAtEpochMillis = now + 60000L,
            isPassableForRescueTruck = true,
            hazardSnapshotVersion = "hazard_20261101"
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessmentOldSnapshot),
            currentEpochMillis = now,
            requiredHazardSnapshotVersion = "hazard_20261105"
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 61. AUDIT 6: CLEAR + VERIFIED_COMPATIBLE -> được xét nếu các gate khác đạt
    @Test
    fun testAuditClearWithVerifiedCompatibleAccepted() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eClearVerified = createEdge(
            id = "e_clear_verified",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 80.0,
            condition = RoadCondition.CLEAR,
            compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE
        )
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eClearVerified))

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B"), result.pathNodeIds)
        assertEquals(80.0, result.totalCost, 0.001)
    }

    // 62. AUDIT 7: CLEAR + UNVERIFIED -> bị loại
    @Test
    fun testAuditClearWithUnverifiedRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eClearUnverified = createEdge(
            id = "e_clear_unverified",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 80.0,
            condition = RoadCondition.CLEAR,
            compatibilityStatus = EdgeCompatibilityStatus.UNVERIFIED
        )
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eClearUnverified))

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 63. AUDIT 8: BLOCKED luôn bị loại
    @Test
    fun testAuditBlockedAlwaysRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eBlocked = createEdge(
            id = "e_blocked",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 50.0,
            condition = RoadCondition.BLOCKED,
            compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE
        )
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eBlocked))

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertEquals(1, result.blockedEdgesSkipped)
    }

    // 64. AUDIT 9: UNKNOWN luôn bị loại
    @Test
    fun testAuditUnknownConditionAlwaysRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eUnknown = createEdge(
            id = "e_unknown",
            fromNodeId = "A",
            toNodeId = "B",
            lengthMeters = 50.0,
            condition = RoadCondition.UNKNOWN,
            compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE
        )
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eUnknown))

        val result = router.inspectFixtureComputation(graph, "A", "B")

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 65. AUDIT 10: Strict readiness false -> không chạy tìm đường, từ chối lập tức
    @Test
    fun testAuditStrictReadinessFalseAbortsSearchImmediately() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eAB = createEdge("e_AB", "A", "B", 100.0)

        val graphNotReady = createGraph(
            nodesList = listOf(nodeA, nodeB),
            directedEdges = listOf(eAB),
            canAttemptStrictRouting = true,
            isStrictRoutingEligible = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS
        )

        val result = router.findRoute(graphNotReady, "A", "B")

        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertNotNull(result.routingDisabledReason)
        assertEquals(0, result.visitedNodesCount)
        assertTrue(result.pathNodeIds.isEmpty())
    }

    // =========================================================================
    // HARDEN LIVE FLOOD ASSESSMENT TRUST, SNAPSHOT & FRESHNESS (15 UNIT TESTS)
    // =========================================================================

    // 66. HARDEN 1: authorizedSource có nội dung nhưng không có authenticated provenance -> REJECT
    @Test
    fun testHardenAuthorizedSourceWithoutProvenanceRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(
            now = now,
            trustValidator = PolicyBasedFloodAssessmentTrustValidator(
                trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
                requireProvenance = true
            )
        )
        // authorizedSource hợp lệ nhưng hasValidProvenance = false
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            authorizedSource = "BAN_CHI_HUY_PCTT_DA_NANG",
            now = now,
            hasValidProvenance = false
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 67. HARDEN 2: Nguồn thẩm định không nằm trong danh sách trusted source policy -> REJECT
    @Test
    fun testHardenSourceNotInTrustedSourcePolicyRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = PolicyBasedFloodAssessmentTrustValidator(
                trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
                provenanceVerifier = testVerifier
            )
        )
        // Nguồn trôi nổi, không có trong danh sách thẩm quyền của policy (dù chữ ký hợp lệ)
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            authorizedSource = "UNOFFICIAL_COMMUNITY_REPORT",
            now = now,
            hasValidProvenance = true
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 68. HARDEN 3: Assessment có provenance hợp lệ và nguồn có thẩm quyền -> ACCEPTED
    @Test
    fun testHardenAssessmentWithValidProvenanceAndTrustedSourceAccepted() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = PolicyBasedFloodAssessmentTrustValidator(
                trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
                provenanceVerifier = testVerifier
            )
        )
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            authorizedSource = "BAN_CHI_HUY_PCTT_DA_NANG",
            now = now,
            hasValidProvenance = true
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B"), result.pathNodeIds)
        assertEquals(5000.0, result.totalCost, 0.001)
        assertEquals(1, result.floodedEdgesOnRoute)
    }

    // 69. HARDEN 4: Ngữ cảnh thiếu active hazard snapshot version -> REJECT
    @Test
    fun testHardenMissingHazardSnapshotRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(
            now = now,
            activeHazardSnapshotVersion = null // Thiếu snapshot version
        )
        val assessment = createValidAssessment(edgeId = "e_flood", now = now)

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 70. HARDEN 5: liveHazardReady = false trong ngữ cảnh hiểm họa -> REJECT
    @Test
    fun testHardenLiveHazardReadyFalseRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(
            now = now,
            liveHazardReady = false // Lớp hiểm họa thời gian thực chưa sẵn sàng
        )
        val assessment = createValidAssessment(edgeId = "e_flood", now = now)

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 71. HARDEN 6: Snapshot version trong assessment không khớp với active snapshot -> REJECT
    @Test
    fun testHardenAssessmentSnapshotMismatchedActiveSnapshotRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(
            now = now,
            activeHazardSnapshotVersion = "hazard_snapshot_v2"
        )
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            hazardSnapshotVersion = "hazard_snapshot_v1" // Không khớp
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 72. HARDEN 7: Assessment không có hazard snapshot version (null hoặc blank) -> REJECT
    @Test
    fun testHardenAssessmentMissingSnapshotVersionRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(
            now = now,
            activeHazardSnapshotVersion = "hazard_snapshot_v1"
        )
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            hazardSnapshotVersion = null // Thiếu snapshot version
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 73. HARDEN 8: verifiedAtEpochMillis nằm trong tương lai (lớn hơn currentEpochMillis) -> REJECT
    @Test
    fun testHardenVerifiedAtInFutureRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(now = now)
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            verifiedAtEpochMillis = now + 10000L, // Tương lai
            expiresAtEpochMillis = now + 60000L
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 74. HARDEN 9: Assessment đã hết hạn (expiresAt < currentEpochMillis) -> REJECT
    @Test
    fun testHardenAssessmentExpiredRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(now = now)
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            verifiedAtEpochMillis = now - 50000L,
            expiresAtEpochMillis = now - 1000L // Hết hạn
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 75. HARDEN 10: Tuổi đánh giá (now - verifiedAt) vượt quá maxAgeMillis của policy -> REJECT
    @Test
    fun testHardenAssessmentExceedsMaxAgeRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val strictAgePolicy = FloodFreshnessPolicy(
            maxAllowedTtlMillis = 600000L,
            maxAgeMillis = 60000L // Tối đa 60s
        )
        val floodContext = createValidFloodContext(now = now, freshnessPolicy = strictAgePolicy)
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            verifiedAtEpochMillis = now - 70000L, // Đã 70s (> 60s maxAge)
            expiresAtEpochMillis = now + 100000L // Dù hạn còn
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 76. HARDEN 11: TTL (expiresAt - verifiedAt) vượt quá maxAllowedTtlMillis của policy -> REJECT
    @Test
    fun testHardenTtlExceedsPolicyRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val strictTtlPolicy = FloodFreshnessPolicy(
            maxAllowedTtlMillis = 300000L, // Tối đa 5 phút
            maxAgeMillis = 600000L
        )
        val floodContext = createValidFloodContext(now = now, freshnessPolicy = strictTtlPolicy)
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            verifiedAtEpochMillis = now - 1000L,
            expiresAtEpochMillis = now + 400000L // TTL = 401000L > 300000L
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 77. HARDEN 12: edgeId trong assessment không khớp với cạnh được duyệt -> REJECT
    @Test
    fun testHardenAssessmentMismatchedEdgeIdRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood_AB", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(now = now)
        val mismatchedAssessment = createValidAssessment(
            edgeId = "e_flood_OTHER", // Khác edgeId
            now = now
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood_AB" to mismatchedAssessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 78. HARDEN 13: isPassableForRescueTruck = false -> REJECT
    @Test
    fun testHardenIsPassableForRescueTruckFalseRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(now = now)
        val impassableAssessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            isPassableForRescueTruck = false // Báo không lưu thông được
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to impassableAssessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 79. HARDEN 14: maxWaterDepthMeters là NaN, Infinity hoặc âm -> REJECT; số thực dương -> ACCEPT
    @Test
    fun testHardenWaterDepthNaNOrInfinityOrNegativeRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(now = now)

        // 14a: NaN -> REJECT
        val assessmentNaN = createValidAssessment(edgeId = "e_flood", now = now, maxWaterDepthMeters = Double.NaN)
        val resultNaN = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to assessmentNaN), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultNaN.status)

        // 14b: Positive Infinity -> REJECT
        val assessmentInf = createValidAssessment(edgeId = "e_flood", now = now, maxWaterDepthMeters = Double.POSITIVE_INFINITY)
        val resultInf = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to assessmentInf), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultInf.status)

        // 14c: Negative Infinity -> REJECT
        val assessmentNegInf = createValidAssessment(edgeId = "e_flood", now = now, maxWaterDepthMeters = Double.NEGATIVE_INFINITY)
        val resultNegInf = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to assessmentNegInf), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultNegInf.status)

        // 14d: Âm (-0.5m) -> REJECT
        val assessmentNeg = createValidAssessment(edgeId = "e_flood", now = now, maxWaterDepthMeters = -0.5)
        val resultNeg = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to assessmentNeg), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultNeg.status)

        // 14e: Hợp lệ (0.35m) -> ACCEPT
        val assessmentValidDepth = createValidAssessment(edgeId = "e_flood", now = now, maxWaterDepthMeters = 0.35)
        val resultValid = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to assessmentValidDepth), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.SUCCESS, resultValid.status)
        assertEquals(listOf("A", "B"), resultValid.pathNodeIds)
    }

    // 80. HARDEN 15: Không cấu hình chính sách độ tươi mới (freshnessPolicy = null) -> FAIL-CLOSED
    @Test
    fun testHardenMissingFreshnessPolicyFailClosed() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val floodContext = createValidFloodContext(
            now = now,
            freshnessPolicy = null // Không có chính sách -> Fail-Closed
        )
        val assessment = createValidAssessment(edgeId = "e_flood", now = now)

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // =========================================================================
    // FINAL AUDIT PROVENANCE SIGNATURE VERIFICATION (10 UNIT TESTS)
    // =========================================================================

    // 81. AUDIT 1: Assessment có hasValidProvenance = true nhưng không có verifier (verifier == null) -> REJECT
    @Test
    fun testAuditProvenanceValidFlagTrueWithoutVerifierRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val validatorWithoutVerifier = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = null // Không có verifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = null,
            trustValidator = validatorWithoutVerifier
        )

        // Dữ liệu tự khai báo hasValidProvenance = true và có signature
        val assessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            hasValidProvenance = true,
            provenanceSignature = "self_asserted_sig"
        )

        assertFalse("TrustValidator phải từ chối khi không có verifier", validatorWithoutVerifier.isTrusted(assessment))

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 82. AUDIT 2: Assessment có chữ ký rỗng, blank hoặc null -> REJECT
    @Test
    fun testAuditProvenanceEmptySignatureRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val validator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = testVerifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = validator
        )

        // 2a: Chữ ký null
        val assessmentNullSig = createValidAssessment(edgeId = "e_flood", now = now).copy(provenanceSignature = null)
        assertFalse(validator.isTrusted(assessmentNullSig))
        val resultNullSig = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to assessmentNullSig), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultNullSig.status)

        // 2b: Chữ ký rỗng ("")
        val assessmentEmptySig = createValidAssessment(edgeId = "e_flood", now = now).copy(provenanceSignature = "")
        assertFalse(validator.isTrusted(assessmentEmptySig))
        val resultEmptySig = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to assessmentEmptySig), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultEmptySig.status)

        // 2c: Chữ ký toàn khoảng trắng ("   ")
        val assessmentBlankSig = createValidAssessment(edgeId = "e_flood", now = now).copy(provenanceSignature = "   ")
        assertFalse(validator.isTrusted(assessmentBlankSig))
        val resultBlankSig = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to assessmentBlankSig), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultBlankSig.status)
    }

    // 83. AUDIT 3: Assessment có chữ ký bất kỳ nhưng không xác minh được -> REJECT
    @Test
    fun testAuditProvenanceArbitraryUnverifiedSignatureRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val validator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = testVerifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = validator
        )

        // Chữ ký ngẫu nhiên, không khớp với canonical payload
        val assessmentInvalidSig = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            provenanceSignature = "unverified_random_garbage_signature_12345"
        )

        assertFalse(validator.isTrusted(assessmentInvalidSig))

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessmentInvalidSig),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 84. AUDIT 4: Assessment có chữ ký hợp lệ và nguồn được trusted policy chấp nhận -> ACCEPT (khi các gate khác đạt)
    @Test
    fun testAuditProvenanceValidSignatureAndTrustedSourceAccepted() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val validator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = testVerifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = validator
        )

        val validAssessment = createValidAssessment(
            edgeId = "e_flood",
            authorizedSource = "BAN_CHI_HUY_PCTT_DA_NANG",
            now = now
        )

        assertTrue("Validator phải chấp nhận assessment có chữ ký và nguồn hợp lệ", validator.isTrusted(validAssessment))

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to validAssessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("A", "B"), result.pathNodeIds)
        assertEquals(5000.0, result.totalCost, 0.001)
        assertEquals(1, result.floodedEdgesOnRoute)
    }

    // 85. AUDIT 5: Assessment có chữ ký hợp lệ nhưng nguồn không nằm trong trusted policy -> REJECT
    @Test
    fun testAuditProvenanceValidSignatureUntrustedSourceRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val validator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"), // Chỉ tin Ban chỉ huy PCTT Đà Nẵng
            provenanceVerifier = testVerifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = validator
        )

        // Nguồn người dân / đơn vị tư nhân chưa kiểm định (dù có chữ ký hợp lệ về cấu trúc payload)
        val assessmentUntrustedSource = createValidAssessment(
            edgeId = "e_flood",
            authorizedSource = "CIVILIAN_COMMUNITY_POST",
            now = now
        )

        assertFalse("Nguồn không nằm trong policy bắt buộc phải bị từ chối", validator.isTrusted(assessmentUntrustedSource))

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessmentUntrustedSource),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 86. AUDIT 6: Assessment có nguồn được trusted policy chấp nhận nhưng chữ ký sai -> REJECT
    @Test
    fun testAuditProvenanceTrustedSourceWrongSignatureRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val validator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = testVerifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = validator
        )

        // Nguồn chuẩn nhưng chữ ký giả mạo hoặc hư hỏng
        val assessmentWrongSig = createValidAssessment(
            edgeId = "e_flood",
            authorizedSource = "BAN_CHI_HUY_PCTT_DA_NANG",
            now = now,
            provenanceSignature = "sim_sig_corrupted_signature_9999"
        )

        assertFalse(validator.isTrusted(assessmentWrongSig))

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessmentWrongSig),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 87. AUDIT 7: Nội dung assessment bị thay đổi sau khi ký -> REJECT
    @Test
    fun testAuditProvenanceTamperedContentAfterSigningRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val validator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = testVerifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = validator
        )

        // 1. Ký hợp lệ trên assessment nguyên bản (depth = 0.2m)
        val originalAssessment = createValidAssessment(
            edgeId = "e_flood",
            now = now,
            maxWaterDepthMeters = 0.2
        )
        val originalSignature = originalAssessment.provenanceSignature
        assertNotNull(originalSignature)
        assertTrue(validator.isTrusted(originalAssessment))

        // 7a: Thay đổi maxWaterDepthMeters từ 0.2 lên 0.8 nhưng giữ nguyên chữ ký cũ -> REJECT
        val tamperedDepthAssessment = originalAssessment.copy(maxWaterDepthMeters = 0.8)
        assertFalse(validator.isTrusted(tamperedDepthAssessment))
        val resultDepth = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to tamperedDepthAssessment), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultDepth.status)

        // 7b: Thay đổi edgeId từ "e_flood" sang "e_flood_tampered" giữ chữ ký cũ -> REJECT
        val tamperedEdgeAssessment = originalAssessment.copy(edgeId = "e_flood_tampered")
        assertFalse(validator.isTrusted(tamperedEdgeAssessment))
        val resultEdge = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to tamperedEdgeAssessment), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultEdge.status)

        // 7c: Thay đổi hazardSnapshotVersion giữ chữ ký cũ -> REJECT
        val tamperedSnapshotAssessment = originalAssessment.copy(hazardSnapshotVersion = "tampered_snapshot_v99")
        assertFalse(validator.isTrusted(tamperedSnapshotAssessment))
        val resultSnapshot = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to tamperedSnapshotAssessment), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultSnapshot.status)

        // 7d: Thay đổi isPassableForRescueTruck giữ chữ ký cũ -> REJECT
        val tamperedPassableAssessment = originalAssessment.copy(isPassableForRescueTruck = false)
        assertFalse(validator.isTrusted(tamperedPassableAssessment))
        val resultPassable = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to tamperedPassableAssessment), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultPassable.status)

        // 7e: Thay đổi verifiedAtEpochMillis giữ chữ ký cũ -> REJECT
        val tamperedVerifiedAtAssessment = originalAssessment.copy(verifiedAtEpochMillis = now - 5000L)
        assertFalse(validator.isTrusted(tamperedVerifiedAtAssessment))
        val resultVerifiedAt = router.inspectFixtureComputation(graph, "A", "B", liveFloodAssessments = mapOf("e_flood" to tamperedVerifiedAtAssessment), floodAssessmentContext = floodContext)
        assertEquals(UcsRouteStatus.NO_PATH, resultVerifiedAt.status)
    }

    // 88. AUDIT 8: Verifier phát sinh exception trong quá trình xác minh -> REJECT (Fail-Closed, an toàn không crash)
    @Test
    fun testAuditProvenanceVerifierThrowsExceptionRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val faultyVerifier = TestControlledProvenanceVerifier(shouldThrowException = true)
        val validator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = faultyVerifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = faultyVerifier,
            trustValidator = validator
        )
        val assessment = createValidAssessment(edgeId = "e_flood", now = now)

        assertFalse("Validator phải bắt ngoại lệ và trả về false", validator.isTrusted(assessment))

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 89. AUDIT 9: Verifier chưa được cấu hình (FailClosedFloodAssessmentProvenanceVerifier) -> REJECT
    @Test
    fun testAuditProvenanceVerifierUnconfiguredRejected() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        // Khởi tạo validator mà không truyền provenanceVerifier -> mặc định là FailClosedFloodAssessmentProvenanceVerifier
        val unconfiguredValidator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG")
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = FailClosedFloodAssessmentProvenanceVerifier,
            trustValidator = unconfiguredValidator
        )
        val assessment = createValidAssessment(edgeId = "e_flood", now = now)

        assertFalse("Validator chưa cấu hình verifier phải từ chối mọi assessment", unconfiguredValidator.isTrusted(assessment))

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to assessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }

    // 90. AUDIT 10: Default constructor không tạo ra assessment có thể tự vượt qua trust gate
    @Test
    fun testAuditProvenanceDefaultConstructorCannotBypassTrustGate() {
        val nodeA = createNode("A")
        val nodeB = createNode("B")
        val eFlood = createEdge("e_flood", "A", "B", 100.0, condition = RoadCondition.FLOODED)
        val graph = createGraph(listOf(nodeA, nodeB), listOf(eFlood))

        val now = 1730700000000L
        val testVerifier = TestControlledProvenanceVerifier()
        val validator = PolicyBasedFloodAssessmentTrustValidator(
            trustedSourceIds = setOf("BAN_CHI_HUY_PCTT_DA_NANG"),
            provenanceVerifier = testVerifier
        )
        val floodContext = createValidFloodContext(
            now = now,
            provenanceVerifier = testVerifier,
            trustValidator = validator
        )

        // Tạo bằng constructor mặc định của LiveFloodAssessment, không truyền hasValidProvenance và provenanceSignature
        val defaultAssessment = LiveFloodAssessment(
            edgeId = "e_flood",
            authorizedSource = "BAN_CHI_HUY_PCTT_DA_NANG",
            verifiedAtEpochMillis = now - 1000L,
            expiresAtEpochMillis = now + 60000L,
            isPassableForRescueTruck = true
        )

        // 1. Kiểm tra default constructor values
        assertFalse("Mặc định hasValidProvenance phải là false", defaultAssessment.hasValidProvenance)
        assertNull("Mặc định provenanceSignature phải là null", defaultAssessment.provenanceSignature)

        // 2. Không thể tự vượt qua trust validator
        assertFalse("Default assessment không được phép vượt qua validator", validator.isTrusted(defaultAssessment))

        // 3. Không thể tự vượt qua UCS router
        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "A",
            goalNodeId = "B",
            liveFloodAssessments = mapOf("e_flood" to defaultAssessment),
            floodAssessmentContext = floodContext
        )

        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathEdges.isEmpty())
    }


    // ==========================================
    // TASK 4B.2: VIA-WAY DFA INTEGRATION TESTS
    // ==========================================

    private fun createValidContext(
        graph: RoadGraph,
        dfa: ViaWayTurnRestrictionDfa? = null
    ): RescueRoutingContext {
        val effectiveDfa = dfa ?: (ViaWayTurnRestrictionDfa.build(graph.turnRestrictions, graph) as DfaBuildResult.Success).dfa
        val index = TurnRestrictionIndex.build(graph, effectiveDfa.acceptedRestrictionIds)
        val dummySha = "9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b"
        val manifest = RoutingManifest(
            schema = "https://emergency-rescue.vn/schemas/v2/routing-manifest.json",
            manifestVersion = "2.1.0",
            generatedAt = "2026-11-04T12:24:50.392Z",
            graphVersion = graph.graphVersion,
            osmSnapshotTimestamp = "2026-11-03T21:24:23Z",
            importerVersion = "osm-importer-v2.3",
            vehicleProfile = graph.vehicleProfile,
            boundingBoxType = "TEST",
            boundingBox = RoutingBoundingBox(minLon = 108.0, minLat = 16.0, maxLon = 109.0, maxLat = 17.0),
            bundle = RoutingBundleInfo(
                fileName = "BaseRoadGraph.json.gz",
                compressedSizeBytes = 1000L,
                uncompressedSizeBytes = 5000L,
                sha256 = dummySha
            ),
            hazardSnapshotStatus = "NOT_INCLUDED",
            hazardAssessmentPolicy = "FAIL_CLOSED_IN_STRICT_MODE",
            isRestrictionStrictRoutingEligible = true,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(
                totalCount = 0,
                enforcementPolicy = "FAIL_CLOSED_IN_STRICT_MODE"
            ),
            canAttemptStrictRouting = true,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            edgeSafetySummary = EdgeSafetySummary(
                totalEdges = graph.directedEdges.size.toLong(),
                verifiedCompatibleEdges = graph.directedEdges.size.toLong(),
                unverifiedEdges = 0L,
                blockedEdges = 0L,
                conflictedEdges = 0L,
                strictSafeEdgesPercent = 100.0,
                blockedWays = 0L
            )
        )
        return RescueRoutingContext(
            roadGraph = graph,
            turnRestrictionIndex = index,
            viaWayDfa = effectiveDfa,
            manifest = manifest,
            artifactSha256 = dummySha
        )
    }

    private fun createCompiledViaWayRestriction(
        id: String,
        type: TurnRestrictionType,
        orderedDirectedEdgeIds: List<String>,
        fromOsmWayId: Long = 101L,
        viaOsmWayIds: List<Long> = listOf(102L),
        toOsmWayId: Long = 103L,
        supportStatus: RestrictionSupportStatus = RestrictionSupportStatus.FULLY_SUPPORTED
    ): TurnRestriction = TurnRestriction(
        id = id,
        type = type,
        fromOsmWayIds = listOf(fromOsmWayId),
        viaOsmNodeIds = emptyList(),
        viaOsmWayIds = viaOsmWayIds,
        toOsmWayIds = listOf(toOsmWayId),
        supportStatus = supportStatus,
        viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
        orderedDirectedEdgeIds = orderedDirectedEdgeIds
    )

    // 1. UCS blocks forbidden NO via-way sequence
    @Test
    fun testUcsBlocksForbiddenNoViaWaySequence() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val restr = createCompiledViaWayRestriction(
            id = "restr_no_1",
            type = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(listOf(n0, n1, n2, n3), listOf(e1, e2, e3), turnRestrictions = listOf(restr))
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N3")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.turnRestrictionsSkipped >= 1)
    }

    // 2. UCS finds valid alternative around NO restriction
    @Test
    fun testUcsFindsValidAlternativeAroundNoRestriction() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val nDetour = createNode("NDetour")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val eDetour1 = createEdge("e_detour1", "N1", "NDetour", 15.0, osmWayId = 201L)
        val eDetour2 = createEdge("e_detour2", "NDetour", "N3", 15.0, osmWayId = 202L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_no_2",
            type = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, nDetour),
            listOf(e1, e2, e3, eDetour1, eDetour2),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N3")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e1", "e_detour1", "e_detour2"), result.pathEdges.map { it.id })
        assertEquals(40.0, result.totalCost, 1e-6)
    }

    // 3. NO via-way pattern with multiple consecutive via edges
    @Test
    fun testNoViaWayPatternWithMultipleConsecutiveViaEdges() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val n4 = createNode("N4")
        val nAlt = createNode("NAlt")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val e4 = createEdge("e4", "N3", "N4", 10.0, osmWayId = 104L)
        val eAlt = createEdge("e_alt", "N2", "N4", 25.0, osmWayId = 201L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_multi_via",
            type = TurnRestrictionType.NO_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3", "e4"),
            fromOsmWayId = 101L,
            viaOsmWayIds = listOf(102L, 103L),
            toOsmWayId = 104L
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, n4, nAlt),
            listOf(e1, e2, e3, e4, eAlt),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N4")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e1", "e2", "e_alt"), result.pathEdges.map { it.id })
        assertEquals(45.0, result.totalCost, 1e-6)
    }

    // 4. Failure transition does not falsely block valid detour
    @Test
    fun testFailureTransitionDoesNotFalselyBlockValidDetour() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val n4 = createNode("N4")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val eDetour = createEdge("e_detour", "N2", "N4", 10.0, osmWayId = 201L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_fail_link",
            type = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, n4),
            listOf(e1, e2, e3, eDetour),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N4")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e1", "e2", "e_detour"), result.pathEdges.map { it.id })
    }

    // 5. Multiple NO outputs from one transition are denied
    @Test
    fun testMultipleNoOutputsFromOneTransitionAreDenied() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val nOther = createNode("NOther")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val eOther = createEdge("e_other", "NOther", "N1", 10.0, osmWayId = 201L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)

        val restr1 = createCompiledViaWayRestriction(
            id = "restr_1",
            type = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3"),
            fromOsmWayId = 101L
        )
        val restr2 = createCompiledViaWayRestriction(
            id = "restr_2",
            type = TurnRestrictionType.NO_U_TURN,
            orderedDirectedEdgeIds = listOf("e_other", "e2", "e3"),
            fromOsmWayId = 201L
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, nOther),
            listOf(e1, eOther, e2, e3),
            turnRestrictions = listOf(restr1, restr2)
        )
        val context = createValidContext(graph)

        val result1 = router.inspectFixtureComputation(context, "N0", "N3")
        assertEquals(UcsRouteStatus.NO_PATH, result1.status)

        val result2 = router.inspectFixtureComputation(context, "NOther", "N3")
        assertEquals(UcsRouteStatus.NO_PATH, result2.status)
    }

    // 6. UCS enforces ONLY mandatory next edge
    @Test
    fun testUcsEnforcesOnlyMandatoryNextEdge() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val n4 = createNode("N4")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val eMandatory = createEdge("e_mand", "N2", "N3", 20.0, osmWayId = 103L)
        val eCheat = createEdge("e_cheat", "N2", "N4", 5.0, osmWayId = 201L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_only",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e_mand")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, n4),
            listOf(e1, e2, eMandatory, eCheat),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        // Mục tiêu đến N4 qua e_cheat bị chặn bởi ONLY obligation
        val resultToN4 = router.inspectFixtureComputation(context, "N0", "N4")
        assertEquals(UcsRouteStatus.NO_PATH, resultToN4.status)

        // Mục tiêu đến N3 qua e_mand thành công
        val resultToN3 = router.inspectFixtureComputation(context, "N0", "N3")
        assertEquals(UcsRouteStatus.SUCCESS, resultToN3.status)
        assertEquals(listOf("e1", "e2", "e_mand"), resultToN3.pathEdges.map { it.id })
    }

    // 7. Active ONLY obligation blocks intermediate detour
    @Test
    fun testActiveOnlyObligationBlocksIntermediateDetour() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val nDetour = createNode("NDetour")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val eBranch = createEdge("e_branch", "N1", "NDetour", 5.0, osmWayId = 201L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_only_3way",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, nDetour),
            listOf(e1, e2, e3, eBranch),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "NDetour")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 8. Active ONLY obligation blocks wrong final turn
    @Test
    fun testActiveOnlyObligationBlocksWrongFinalTurn() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val nWrong = createNode("NWrong")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val eWrong = createEdge("e_wrong", "N2", "NWrong", 5.0, osmWayId = 201L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_only_final",
            type = TurnRestrictionType.ONLY_RIGHT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, nWrong),
            listOf(e1, e2, e3, eWrong),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "NWrong")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 9. Completing ONLY sequence releases the obligation
    @Test
    fun testCompletingOnlySequenceReleasesTheObligation() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val n4A = createNode("N4A")
        val n4B = createNode("N4B")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val e4A = createEdge("e4a", "N3", "N4A", 15.0, osmWayId = 104L)
        val e4B = createEdge("e4b", "N3", "N4B", 20.0, osmWayId = 105L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_only_complete",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, n4A, n4B),
            listOf(e1, e2, e3, e4A, e4B),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        val resultA = router.inspectFixtureComputation(context, "N0", "N4A")
        assertEquals(UcsRouteStatus.SUCCESS, resultA.status)
        assertEquals(listOf("e1", "e2", "e3", "e4a"), resultA.pathEdges.map { it.id })

        val resultB = router.inspectFixtureComputation(context, "N0", "N4B")
        assertEquals(UcsRouteStatus.SUCCESS, resultB.status)
        assertEquals(listOf("e1", "e2", "e3", "e4b"), resultB.pathEdges.map { it.id })
    }

    // 10. Destination reached while ONLY obligation remains active is accepted
    @Test
    fun testDestinationReachedWhileOnlyObligationRemainsActiveIsAccepted() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_only_dest",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3),
            listOf(e1, e2, e3),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N2")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e1", "e2"), result.pathEdges.map { it.id })
        assertEquals("N2", result.pathNodeIds.last())
    }

    // 11. Multiple ONLY obligations with compatible next edge are handled correctly
    @Test
    fun testMultipleOnlyObligationsWithCompatibleNextEdgeAreHandledCorrectly() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val nOther = createNode("NOther")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val eOther = createEdge("e_other", "N2", "NOther", 10.0, osmWayId = 201L)

        val restrA = createCompiledViaWayRestriction(
            id = "restr_only_a",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val restrB = createCompiledViaWayRestriction(
            id = "restr_only_b",
            type = TurnRestrictionType.ONLY_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, nOther),
            listOf(e1, e2, e3, eOther),
            turnRestrictions = listOf(restrA, restrB)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N3")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e1", "e2", "e3"), result.pathEdges.map { it.id })
    }

    // 12. Conflicting ONLY obligations produce DENY_CONFLICT
    @Test
    fun testConflictingOnlyObligationsProduceDenyConflict() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val nLeft = createNode("NLeft")
        val nRight = createNode("NRight")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val eLeft = createEdge("e_left", "N2", "NLeft", 10.0, osmWayId = 103L)
        val eRight = createEdge("e_right", "N2", "NRight", 10.0, osmWayId = 104L)

        val restrLeft = createCompiledViaWayRestriction(
            id = "restr_left",
            type = TurnRestrictionType.ONLY_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e_left"),
            toOsmWayId = 103L
        )
        val restrRight = createCompiledViaWayRestriction(
            id = "restr_right",
            type = TurnRestrictionType.ONLY_RIGHT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e_right"),
            toOsmWayId = 104L
        )
        val graph = createGraph(
            listOf(n0, n1, n2, nLeft, nRight),
            listOf(e1, e2, eLeft, eRight),
            turnRestrictions = listOf(restrLeft, restrRight)
        )
        val context = createValidContext(graph)

        val resultLeft = router.inspectFixtureComputation(context, "N0", "NLeft")
        assertEquals(UcsRouteStatus.NO_PATH, resultLeft.status)

        val resultRight = router.inspectFixtureComputation(context, "N0", "NRight")
        assertEquals(UcsRouteStatus.NO_PATH, resultRight.status)
    }

    // 13. NO vetoes an edge otherwise allowed by ONLY
    @Test
    fun testNoVetoesAnEdgeOtherwiseAllowedByOnly() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)

        val restrOnly = createCompiledViaWayRestriction(
            id = "restr_only",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val restrNo = createCompiledViaWayRestriction(
            id = "restr_no",
            type = TurnRestrictionType.NO_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3),
            listOf(e1, e2, e3),
            turnRestrictions = listOf(restrOnly, restrNo)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N3")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // 14. Via-node and via-way restrictions both apply
    @Test
    fun testViaNodeAndViaWayRestrictionsBothApply() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val n4 = createNode("N4")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val e4 = createEdge("e4", "N3", "N4", 10.0, osmWayId = 104L)

        val viaNodeRestr = TurnRestriction(
            id = "node_restr",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromOsmWayIds = listOf(101L),
            viaOsmNodeIds = listOf(1L),
            toOsmWayIds = listOf(102L),
            prohibitedTransitions = listOf(EdgeTransition("e1", "e2", "N1")),
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED
        )
        val viaWayRestr = createCompiledViaWayRestriction(
            id = "way_restr",
            type = TurnRestrictionType.NO_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e2", "e3", "e4"),
            fromOsmWayId = 102L,
            viaOsmWayIds = listOf(103L),
            toOsmWayId = 104L
        )

        val graph = createGraph(
            listOf(n0, n1, n2, n3, n4),
            listOf(e1, e2, e3, e4),
            turnRestrictions = listOf(viaNodeRestr, viaWayRestr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N4")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.turnRestrictionsSkipped >= 1)
    }

    // 15. Via-node NO overrides DFA ALLOW
    @Test
    fun testViaNodeNoOverridesDfaAllow() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)

        val viaNodeRestr = TurnRestriction(
            id = "node_no",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            fromOsmWayIds = listOf(101L),
            viaOsmNodeIds = listOf(1L),
            toOsmWayIds = listOf(102L),
            prohibitedTransitions = listOf(EdgeTransition("e1", "e2", "N1")),
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED
        )
        val graph = createGraph(listOf(n0, n1, n2), listOf(e1, e2), turnRestrictions = listOf(viaNodeRestr))
        val context = createValidContext(graph, ViaWayTurnRestrictionDfa.empty())

        val result = router.inspectFixtureComputation(context, "N0", "N2")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertEquals(1, result.turnRestrictionsSkipped)
    }

    // 16. DFA NO overrides via-node ALLOW
    @Test
    fun testDfaNoOverridesViaNodeAllow() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)

        val viaWayRestr = createCompiledViaWayRestriction(
            id = "way_no",
            type = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(listOf(n0, n1, n2, n3), listOf(e1, e2, e3), turnRestrictions = listOf(viaWayRestr))
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N3")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.turnRestrictionsSkipped >= 1)
    }

    // 17. Invalid DFA state fails closed
    @Test
    fun testInvalidDfaStateFailsClosed() {
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val resNegative = dfa.evaluateTransition(-1, "e1")
        assertEquals(DfaDecision.DENY_INVALID_STATE, resNegative.decision)

        val resOutOfBounds = dfa.evaluateTransition(dfa.stateCount + 5, "e1")
        assertEquals(DfaDecision.DENY_INVALID_STATE, resOutOfBounds.decision)
    }

    // 18. ALLOW with null nextStateId fails closed
    @Test
    fun testAllowWithNullNextStateIdFailsClosed() {
        val evalResult = DfaEvaluationResult(decision = DfaDecision.ALLOW, nextStateId = null)
        assertNull("ALLOW with null nextStateId must have null nextStateId", evalResult.nextStateId)
        val dfa = ViaWayTurnRestrictionDfa.empty()
        assertFalse("null state is not valid state in DFA", dfa.isValidStateId(evalResult.nextStateId ?: -1))
    }

    // 19. ALLOW with out-of-range nextStateId fails closed
    @Test
    fun testAllowWithOutOfRangeNextStateIdFailsClosed() {
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val outOfRangeStateId = 9999
        assertFalse("out-of-range state ID must be invalid", dfa.isValidStateId(outOfRangeStateId))
    }

    // 20. Different restriction states at the same node are not merged
    @Test
    fun testDifferentRestrictionStatesAtSameNodeAreNotMerged() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val nMid = createNode("NMid")
        val nGoal = createNode("NGoal")
        val e1A = createEdge("e1a", "N0", "N1", 10.0, osmWayId = 101L)
        val eViaA = createEdge("e_via_a", "N1", "NMid", 10.0, osmWayId = 102L)
        val e1B = createEdge("e1b", "N0", "NMid", 30.0, osmWayId = 104L)
        val e2 = createEdge("e2", "NMid", "NGoal", 10.0, osmWayId = 103L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_split",
            type = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1a", "e_via_a", "e2"),
            fromOsmWayId = 101L,
            viaOsmWayIds = listOf(102L),
            toOsmWayId = 103L
        )
        val graph = createGraph(listOf(n0, n1, nMid, nGoal), listOf(e1A, eViaA, e1B, e2), turnRestrictions = listOf(restr))
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "NGoal")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e1b", "e2"), result.pathEdges.map { it.id })
    }

    // 21. New findRoute call starts at DFA ROOT
    @Test
    fun testNewFindRouteCallStartsAtDfaRoot() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val graph = createGraph(listOf(n0, n1, n2), listOf(e1, e2))
        val context = createValidContext(graph)

        val res1 = router.inspectFixtureComputation(context, "N0", "N2")
        assertEquals(UcsRouteStatus.SUCCESS, res1.status)

        val res2 = router.inspectFixtureComputation(context, "N0", "N2")
        assertEquals(UcsRouteStatus.SUCCESS, res2.status)
        assertEquals(res1.pathEdges.map { it.id }, res2.pathEdges.map { it.id })
    }

    // 22. Route reconstruction preserves state history
    @Test
    fun testRouteReconstructionPreservesStateHistory() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_recon",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(listOf(n0, n1, n2, n3), listOf(e1, e2, e3), turnRestrictions = listOf(restr))
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N3")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e1", "e2", "e3"), result.pathEdges.map { it.id })
        assertEquals(listOf("N0", "N1", "N2", "N3"), result.pathNodeIds)
    }

    // 23. Loop route revisits node under a different restriction state
    @Test
    fun testLoopRouteRevisitsNodeUnderDifferentRestrictionState() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val n4 = createNode("N4")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val eLoop = createEdge("e_loop", "N3", "N1", 10.0, osmWayId = 104L)
        val eFinal = createEdge("e_final", "N1", "N4", 10.0, osmWayId = 105L)

        val viaNodeRestr = TurnRestriction(
            id = "node_no_direct",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromOsmWayIds = listOf(101L),
            viaOsmNodeIds = listOf(1L),
            toOsmWayIds = listOf(105L),
            prohibitedTransitions = listOf(EdgeTransition("e1", "e_final", "N1")),
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED
        )
        val graph = createGraph(
            listOf(n0, n1, n2, n3, n4),
            listOf(e1, e2, e3, eLoop, eFinal),
            turnRestrictions = listOf(viaNodeRestr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "N4")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e1", "e2", "e3", "e_loop", "e_final"), result.pathEdges.map { it.id })
    }

    // 24. Same graph and DFA produce deterministic route
    @Test
    fun testSameGraphAndDfaProduceDeterministicRoute() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)
        val graph = createGraph(listOf(n0, n1, n2, n3), listOf(e1, e2, e3))
        val context = createValidContext(graph)

        val res1 = router.inspectFixtureComputation(context, "N0", "N3")
        val res2 = router.inspectFixtureComputation(context, "N0", "N3")
        val res3 = router.inspectFixtureComputation(context, "N0", "N3")

        assertEquals(res1.pathEdges.map { it.id }, res2.pathEdges.map { it.id })
        assertEquals(res2.pathEdges.map { it.id }, res3.pathEdges.map { it.id })
        assertEquals(res1.totalCost, res2.totalCost, 1e-9)
    }

    // 25. Legacy findRoute overload cannot bypass missing via-way DFA
    @Test
    fun testLegacyFindRouteOverloadCannotBypassMissingViaWayDfa() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val n3 = createNode("N3")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val e3 = createEdge("e3", "N2", "N3", 10.0, osmWayId = 103L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_via_way",
            type = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val graph = createGraph(listOf(n0, n1, n2, n3), listOf(e1, e2, e3), turnRestrictions = listOf(restr))

        val result = router.findRoute(
            roadGraph = graph,
            startNodeId = "N0",
            goalNodeId = "N3",
            viaWayDfa = null
        )

        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertTrue(result.routingDisabledReason?.contains("no ViaWayTurnRestrictionDfa was provided") == true)
    }

    // 26. DFA build failure never falls back to empty DFA
    @Test
    fun testDfaBuildFailureNeverFallsBackToEmptyDfa() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)

        val invalidPattern = ViaWayPattern(
            relationId = "bad_pattern",
            restrictionType = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1")
        )
        val buildResult = ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(invalidPattern))
        assertTrue(buildResult is DfaBuildResult.Failure)
        val failure = buildResult as DfaBuildResult.Failure
        assertEquals("INVALID_PATTERN_LENGTH", failure.diagnosticCode)
    }

    // ==========================================
    // TASK 4B.3: INTEGRATED UCS RUNTIME SAFETY AUDIT TESTS (1 - 7)
    // ==========================================

    private fun createCorruptedDfa(
        evaluateTransitionOverride: (currentStateId: Int, candidateEdgeId: String) -> DfaEvaluationResult = { _, _ -> DfaEvaluationResult(DfaDecision.ALLOW, 0) },
        rootStateIdOverride: Int = 0
    ): ViaWayTurnRestrictionDfa {
        return ViaWayTurnRestrictionDfa.createForTesting(
            rootStateId = rootStateIdOverride,
            testTransitionEvaluator = { currentStateId, candidateEdgeId, _ ->
                evaluateTransitionOverride(currentStateId, candidateEdgeId)
            }
        )
    }

    // 1. ALLOW with null nextStateId aborts the entire search
    @Test
    fun testAllowWithNullNextStateIdAbortsEntireSearch() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val graph = createGraph(listOf(n0, n1), listOf(e1))

        val corruptedDfa = createCorruptedDfa(
            evaluateTransitionOverride = { _, _ -> DfaEvaluationResult(decision = DfaDecision.ALLOW, nextStateId = null) }
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "N0",
            goalNodeId = "N1",
            viaWayDfa = corruptedDfa
        )

        assertEquals(UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE, result.status)
        assertTrue(result.pathEdges.isEmpty())
        assertTrue("Reason must mention null nextStateId", result.routingDisabledReason?.contains("null nextStateId") == true)
    }

    // 2. ALLOW with invalid nextStateId aborts the entire search
    @Test
    fun testAllowWithInvalidNextStateIdAbortsEntireSearch() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val graph = createGraph(listOf(n0, n1), listOf(e1))

        val corruptedDfa = createCorruptedDfa(
            evaluateTransitionOverride = { _, _ -> DfaEvaluationResult(decision = DfaDecision.ALLOW, nextStateId = 9999) }
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "N0",
            goalNodeId = "N1",
            viaWayDfa = corruptedDfa
        )

        assertEquals(UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE, result.status)
        assertTrue(result.pathEdges.isEmpty())
        assertTrue("Reason must mention out-of-range nextStateId", result.routingDisabledReason?.contains("out-of-range nextStateId") == true)
    }

    // 3. Invalid current DFA state aborts the entire search
    @Test
    fun testInvalidCurrentDfaStateAbortsEntireSearch() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val graph = createGraph(listOf(n0, n1), listOf(e1))

        // Case A: DENY_INVALID_STATE during transition evaluation
        val corruptedDfa = createCorruptedDfa(
            evaluateTransitionOverride = { _, _ ->
                DfaEvaluationResult(
                    decision = DfaDecision.DENY_INVALID_STATE,
                    nextStateId = null,
                    diagnosticReason = "State ID corrupted"
                )
            }
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "N0",
            goalNodeId = "N1",
            viaWayDfa = corruptedDfa
        )

        assertEquals(UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE, result.status)
        assertTrue(result.pathEdges.isEmpty())
        assertTrue("Reason must mention invalid state ID", result.routingDisabledReason?.contains("invalid state ID") == true)

        // Case B: rootStateId is invalid
        val invalidRootDfa = createCorruptedDfa(rootStateIdOverride = -1)

        val resultRoot = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "N0",
            goalNodeId = "N1",
            viaWayDfa = invalidRootDfa
        )
        assertEquals(UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE, resultRoot.status)
        assertTrue(resultRoot.pathEdges.isEmpty())
        assertTrue(resultRoot.routingDisabledReason?.contains("rootStateId") == true)
    }

    // 4. A previously queued alternative route cannot produce SUCCESS after an internal DFA integrity failure
    @Test
    fun testPreviouslyQueuedAlternativeRouteCannotProduceSuccessAfterInternalDfaIntegrityFailure() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val nGoal = createNode("NGoal")
        // Branch 1: N0 -> N1 -> NGoal (costs: 10 + 10 = 20)
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val eGoal1 = createEdge("e_goal1", "N1", "NGoal", 10.0, osmWayId = 102L)
        // Branch 2: N0 -> N2 -> NGoal (costs: 15 + 15 = 30)
        val e2 = createEdge("e2", "N0", "N2", 15.0, osmWayId = 201L)
        val eGoal2 = createEdge("e_goal2", "N2", "NGoal", 15.0, osmWayId = 202L)

        val graph = createGraph(
            listOf(n0, n1, n2, nGoal),
            listOf(e1, e2, eGoal1, eGoal2)
        )

        // Corrupted DFA: khi duyệt Branch 1 (e_goal1), trả về ALLOW nhưng nextStateId == null
        val corruptedDfa = createCorruptedDfa(
            evaluateTransitionOverride = { _, edgeId ->
                if (edgeId == "e_goal1") {
                    DfaEvaluationResult(decision = DfaDecision.ALLOW, nextStateId = null)
                } else {
                    DfaEvaluationResult(decision = DfaDecision.ALLOW, nextStateId = 0)
                }
            }
        )

        val result = router.inspectFixtureComputation(
            roadGraph = graph,
            startNodeId = "N0",
            goalNodeId = "NGoal",
            viaWayDfa = corruptedDfa
        )

        // Tuyệt đối không được trả về SUCCESS qua nhánh e2 -> e_goal2!
        assertNotEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE, result.status)
        assertTrue(result.pathEdges.isEmpty())
        assertTrue(result.routingDisabledReason?.contains("null nextStateId") == true)
    }

    // 5. Ordinary DENY_PROHIBITED still permits exploration of valid alternative routes
    @Test
    fun testOrdinaryDenyProhibitedStillPermitsExplorationOfValidAlternativeRoutes() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val nVia = createNode("NVia")
        val n2 = createNode("N2")
        val nGoal = createNode("NGoal")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val eVia = createEdge("e_via", "N1", "NVia", 10.0, osmWayId = 102L)
        val eGoal1 = createEdge("e_goal1", "NVia", "NGoal", 10.0, osmWayId = 103L)
        val e2 = createEdge("e2", "N0", "N2", 15.0, osmWayId = 201L)
        val eGoal2 = createEdge("e_goal2", "N2", "NGoal", 15.0, osmWayId = 202L)

        // Cấm e1 -> e_via -> e_goal1 qua DENY_PROHIBITED
        val restr = createCompiledViaWayRestriction(
            id = "restr_prohibited",
            type = TurnRestrictionType.NO_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e_via", "e_goal1"),
            fromOsmWayId = 101L,
            viaOsmWayIds = listOf(102L),
            toOsmWayId = 103L
        )
        val graph = createGraph(
            listOf(n0, n1, nVia, n2, nGoal),
            listOf(e1, eVia, eGoal1, e2, eGoal2),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        val result = router.inspectFixtureComputation(context, "N0", "NGoal")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals(listOf("e2", "e_goal2"), result.pathEdges.map { it.id })
        assertTrue(result.turnRestrictionsSkipped >= 1)
    }

    // 6. Ordinary DENY_NOT_IN_ALLOWED_SET still permits valid alternative routes
    @Test
    fun testOrdinaryDenyNotInAllowedSetStillPermitsValidAlternativeRoutes() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val nMand = createNode("NMand")
        val nOther = createNode("NOther")
        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val eMand = createEdge("e_mand", "N2", "NMand", 10.0, osmWayId = 103L)
        val eForbidden = createEdge("e_forbid", "N2", "NOther", 5.0, osmWayId = 201L)

        val restr = createCompiledViaWayRestriction(
            id = "restr_only",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e_mand"),
            fromOsmWayId = 101L,
            toOsmWayId = 103L
        )
        val graph = createGraph(
            listOf(n0, n1, n2, nMand, nOther),
            listOf(e1, e2, eMand, eForbidden),
            turnRestrictions = listOf(restr)
        )
        val context = createValidContext(graph)

        // Tuyến đi sang NOther vi phạm ONLY obligation -> NO_PATH (DENY_NOT_IN_ALLOWED_SET loại e_forbid)
        val resultOther = router.inspectFixtureComputation(context, "N0", "NOther")
        assertEquals(UcsRouteStatus.NO_PATH, resultOther.status)

        // Tuyến đi sang NMand khớp ONLY obligation -> SUCCESS
        val resultMand = router.inspectFixtureComputation(context, "N0", "NMand")
        assertEquals(UcsRouteStatus.SUCCESS, resultMand.status)
        assertEquals(listOf("e1", "e2", "e_mand"), resultMand.pathEdges.map { it.id })
    }

    // 7. Ordinary DENY_CONFLICT blocks only the affected transition
    @Test
    fun testOrdinaryDenyConflictBlocksOnlyAffectedTransition() {
        val n0 = createNode("N0")
        val n1 = createNode("N1")
        val n2 = createNode("N2")
        val nRight = createNode("NRight")
        val nLeft = createNode("NLeft")
        val nAlt = createNode("NAlt")
        val nGoal = createNode("NGoal")

        val e1 = createEdge("e1", "N0", "N1", 10.0, osmWayId = 101L)
        val e2 = createEdge("e2", "N1", "N2", 10.0, osmWayId = 102L)
        val eRight = createEdge("e_right", "N2", "NRight", 10.0, osmWayId = 103L)
        val eLeft = createEdge("e_left", "N2", "NLeft", 10.0, osmWayId = 104L)
        // Nhánh thay thế độc lập từ N0 -> NAlt -> NGoal
        val eAlt1 = createEdge("e_alt1", "N0", "NAlt", 25.0, osmWayId = 301L)
        val eAlt2 = createEdge("e_alt2", "NAlt", "NGoal", 25.0, osmWayId = 302L)

        // 2 ONLY obligations mâu thuẫn tại N2 sau e1 -> e2
        val restr1 = createCompiledViaWayRestriction(
            id = "restr_only1",
            type = TurnRestrictionType.ONLY_RIGHT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e_right"),
            fromOsmWayId = 101L,
            toOsmWayId = 103L
        )
        val restr2 = createCompiledViaWayRestriction(
            id = "restr_only2",
            type = TurnRestrictionType.ONLY_LEFT_TURN,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e_left"),
            fromOsmWayId = 101L,
            toOsmWayId = 104L
        )

        val graph = createGraph(
            listOf(n0, n1, n2, nRight, nLeft, nAlt, nGoal),
            listOf(e1, e2, eRight, eLeft, eAlt1, eAlt2),
            turnRestrictions = listOf(restr1, restr2)
        )
        val context = createValidContext(graph)

        // Thử tìm đường tới NRight qua N2 bị xung đột (DENY_CONFLICT) -> NO_PATH
        val resultRight = router.inspectFixtureComputation(context, "N0", "NRight")
        assertEquals(UcsRouteStatus.NO_PATH, resultRight.status)

        // Tìm đường tới NGoal qua nhánh NAlt thành công vì không đi qua điểm xung đột
        val resultAlt = router.inspectFixtureComputation(context, "N0", "NGoal")
        assertEquals(UcsRouteStatus.SUCCESS, resultAlt.status)
        assertEquals(listOf("e_alt1", "e_alt2"), resultAlt.pathEdges.map { it.id })
    }
}
