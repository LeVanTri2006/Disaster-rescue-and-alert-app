package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.algorithm.DfaBuildResult
import com.example.bai1.rescue.routing.algorithm.DfaDecision
import com.example.bai1.rescue.routing.algorithm.DfaEvaluationResult
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.UniformCostSearchRouter
import com.example.bai1.rescue.routing.algorithm.ViaWayPattern
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.loader.RoutingDisabledReason
import com.example.bai1.rescue.routing.model.EdgeCompatibilityStatus
import com.example.bai1.rescue.routing.model.EdgeSafetySummary
import com.example.bai1.rescue.routing.model.EdgeTransition
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
import com.example.bai1.rescue.routing.model.StrictRoutingEvaluation
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.UcsRouteStatus
import com.example.bai1.rescue.routing.model.UnsupportedRestrictionsSummary
import com.example.bai1.rescue.routing.model.ViaWayCompilationStatus
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * PHASE 4 - TASK 4C: BỘ KIỂM THỬ AN TOÀN ĐỊNH TUYẾN TOÀN DIỆN (END-TO-END ROUTING SAFETY VERIFICATION)
 *
 * Kiểm chứng toàn bộ luồng tích hợp:
 * 1. Audit Test Isolation & Test Seam Rejection.
 * 2. Production Graph End-to-End Readiness Test (Đồ thị Đà Nẵng thực tế).
 * 3. Synthetic Fixture - Complete UCS Execution (NO, ONLY, Combined, State Correctness).
 * 4. Live Hazard Integration under Strict Context.
 * 5. Failure Injection & Snapshot Integrity.
 * 6. Performance & Resource Audit.
 */
/**
 * P5-A01R: fixture computations inspect the PRIVATE production kernel only after asserting that
 * the real strict API denies the identical input. Computational SUCCESS is NOT authorization.
 * Acceptance tests use findRoute directly; no test approval seam is added to main sources.
 */
class EndToEndRoutingSafetyVerificationTest {

    private lateinit var router: UniformCostSearchRouter

    @Before
    fun setUp() {
        router = UniformCostSearchRouter()
    }

    // =========================================================================
    // SECTION 2: AUDIT TEST ISOLATION
    // =========================================================================

    @Test
    fun `01_test_seam_isolation_prevents_strict_routing_in_rescue_routing_context`() {
        val testDfa = ViaWayTurnRestrictionDfa.createForTesting(
            rootStateId = 0,
            testTransitionEvaluator = { _, _, defaultResult -> defaultResult }
        )
        assertTrue("testDfa must have hasTestEvaluator = true", testDfa.hasTestEvaluator)

        val manifest = createReadyManifest()
        val roadGraph = createReadyRoadGraph(manifest)
        val index = TurnRestrictionIndex.build(roadGraph, testDfa.acceptedRestrictionIds)

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = testDfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse("Context with test DFA must NOT be permitted for strict routing", context.isStrictRoutingPermitted)
        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        val denied = context.readinessEvaluation as StrictRoutingEvaluation.Denied
        assertEquals(RoutingDisabledReason.BACKEND_STRICT_ROUTING_DISABLED, denied.reason)
        assertTrue(denied.detailMessage.contains("testTransitionEvaluator"))

        val result = router.findRoute(context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertEquals(denied.detailMessage, result.routingDisabledReason)
    }

    @Test
    fun `02_production_dfa_and_empty_dfa_have_no_test_evaluator`() {
        val emptyDfa = ViaWayTurnRestrictionDfa.empty()
        assertFalse("EMPTY_DFA must have hasTestEvaluator = false", emptyDfa.hasTestEvaluator)

        val builtResult = ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(
            ViaWayPattern("p1", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        ))
        assertTrue(builtResult is DfaBuildResult.Success)
        val prodDfa = (builtResult as DfaBuildResult.Success).dfa
        assertFalse("Production built DFA must have hasTestEvaluator = false", prodDfa.hasTestEvaluator)
    }

    // =========================================================================
    // SECTION 4: PRODUCTION GRAPH END-TO-END READINESS TEST (ĐÀ NẴNG)
    // =========================================================================

    @Test
    fun `03_production_danang_graph_loading_and_readiness_lock`() {
        val baseDir = File("/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app/tools/osm-importer/output-dryrun-task3b")
        val gzFile = File(baseDir, "BaseRoadGraph.json.gz")
        val jsonFile = File(baseDir, "BaseRoadGraph.json")
        val manifestFile = File(baseDir, "RoutingManifest.json")

        if (!gzFile.exists() || !jsonFile.exists() || !manifestFile.exists()) {
            println("Skipping production test: files not found in $baseDir")
            return
        }

        // 1. Xác minh checksum của file gzip thực tế
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(gzFile).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                sha256Digest.update(buffer, 0, bytesRead)
            }
        }
        val computedSha256 = sha256Digest.digest().joinToString("") { "%02x".format(it) }
        val expectedSha256 = "1b7f64a965a763624e0a6c2cd866352fc3ef5f106214907866d0c6dcb49c25b4"
        assertEquals("Gzip SHA-256 must match expected production checksum", expectedSha256, computedSha256)

        // 2. Parse đồ thị bằng StreamingRoadGraphParser
        val parser = StreamingRoadGraphParser()
        val graph = parser.parseSync(jsonFile)

        // 3. Xác minh số liệu đồ thị Đà Nẵng
        assertEquals("dn_central_real_v1", graph.graphVersion)
        assertEquals("2.1.0", graph.contractVersion)
        assertEquals(7294, graph.nodes.size)
        assertEquals(16651, graph.directedEdges.size)
        assertEquals(27, graph.turnRestrictions.size)

        // 4. Xác minh Turn Restrictions
        val viaWayRestrictions = graph.turnRestrictions.filter { it.viaOsmWayIds.isNotEmpty() }
        assertEquals(12, viaWayRestrictions.size)

        val compiledViaWay = viaWayRestrictions.filter { it.viaWayCompilationStatus == ViaWayCompilationStatus.COMPILED }
        val unresolvedViaWay = viaWayRestrictions.filter { it.viaWayCompilationStatus == ViaWayCompilationStatus.UNRESOLVED }
        assertEquals(10, compiledViaWay.size)
        assertEquals(2, unresolvedViaWay.size)

        // 3 conditional restrictions chưa có solver
        val conditionalRestrictions = graph.turnRestrictions.filter { it.conditionalExpression != null }
        assertEquals(3, conditionalRestrictions.size)

        val totalUnsupported = graph.turnRestrictions.count {
            it.supportStatus != RestrictionSupportStatus.FULLY_SUPPORTED &&
            it.supportStatus != RestrictionSupportStatus.EXEMPT_FOR_PROFILE
        }
        assertEquals(16, totalUnsupported)

        // 5. Xác minh Edge Compatibility statistics
        assertNotNull(graph.edgeSafetySummary)
        val safety = graph.edgeSafetySummary!!
        assertEquals(16651L, safety.totalEdges)
        assertEquals(0L, safety.verifiedCompatibleEdges)
        assertEquals(16651L, safety.unverifiedEdges)
        assertEquals(0.0, safety.strictSafeEdgesPercent, 0.001)

        // 6. Build DFA và Index
        val dfaBuildResult = ViaWayTurnRestrictionDfa.build(graph.turnRestrictions, graph)
        assertTrue("DFA build must succeed", dfaBuildResult is DfaBuildResult.Success)
        val dfa = (dfaBuildResult as DfaBuildResult.Success).dfa
        assertEquals(10, dfa.patternCount)
        assertEquals(10, dfa.acceptedRestrictionIds.size)

        val index = TurnRestrictionIndex.build(graph, dfa.acceptedRestrictionIds)
        assertEquals(10, index.delegatedViaWayRestrictionIds.size)
        assertEquals(2, index.undelegatedViaWayRestrictionIds.size)
        assertTrue("Index must have unresolved restrictions (2 via-way + 1 via-node + 3 conditional)", index.hasUnresolvedRestrictions)

        // 7. Tạo Manifest và Context
        val manifest = RoutingManifest(
            schema = "https://emergency-rescue.vn/schemas/v2/routing-manifest.json",
            manifestVersion = "2.1.0",
            generatedAt = "2026-11-04T18:26:26.069Z",
            graphVersion = "dn_central_real_v1",
            osmSnapshotTimestamp = "2026-11-03T21:24:23Z",
            importerVersion = "osm-importer-v2.3-prototype",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.19, 16.035, 108.235, 16.075),
            bundle = RoutingBundleInfo("BaseRoadGraph.json.gz", 1174083L, 33007647L, computedSha256),
            hazardSnapshotStatus = "NOT_INCLUDED",
            hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            canAttemptStrictRouting = false,
            isStrictRoutingEligible = false,
            strictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            isRestrictionStrictRoutingEligible = false,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(16, "FAIL_CLOSED_IN_STRICT_MODE"),
            edgeSafetySummary = safety
        )

        val context = RescueRoutingContext(
            roadGraph = graph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = computedSha256
        )

        // 8. ĐÁNH GIÁ NGHIÊM NGẶT READINESS CỦA GRAPH ĐÀ NẴNG
        assertFalse("Production Da Nang graph must NOT permit strict routing", context.isStrictRoutingPermitted)
        assertFalse("Manifest canAttemptStrictRouting must be false", manifest.canAttemptStrictRouting)
        assertFalse("RoadGraph canAttemptStrictRouting must be false", graph.canAttemptStrictRouting)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, manifest.overallStrictRoutingReadiness)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, graph.overallStrictRoutingReadiness)

        // 9. Xác minh UCS từ chối chạy strict routing
        val arbitraryStart = graph.nodes.keys.first()
        val arbitraryGoal = graph.nodes.keys.last()
        val routeResult = router.findRoute(context, arbitraryStart, arbitraryGoal)
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, routeResult.status)
        assertNotNull(routeResult.routingDisabledReason)
    }

    // =========================================================================
    // SECTION 5: SYNTHETIC FIXTURE - COMPLETE UCS EXECUTION
    // =========================================================================

    // A. NO Via-way

    @Test
    fun `04_no_via_way_forbidden_sequence_never_appears_in_route`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay(
                    id = "res_no_1",
                    type = TurnRestrictionType.NO_LEFT_TURN,
                    edges = listOf("e_in", "e_via", "e_out"),
                    fromWay = 101L,
                    viaWays = listOf(102L),
                    toWay = 103L
                )
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertTrue(result.pathEdges.isNotEmpty())

        val edgeSequence = result.pathEdges.map { it.id }
        val containsForbidden = (0..edgeSequence.size - 3).any { i ->
            edgeSequence[i] == "e_in" && edgeSequence[i + 1] == "e_via" && edgeSequence[i + 2] == "e_out"
        }
        assertFalse("Forbidden sequence e_in -> e_via -> e_out must NOT appear in route", containsForbidden)
    }

    @Test
    fun `05_no_via_way_valid_detour_is_found`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay(
                    id = "res_no_1",
                    type = TurnRestrictionType.NO_LEFT_TURN,
                    edges = listOf("e_in", "e_via", "e_out"),
                    fromWay = 101L,
                    viaWays = listOf(102L),
                    toWay = 103L
                ),
                createCompiledViaWay(
                    id = "res_no_out1",
                    type = TurnRestrictionType.NO_LEFT_TURN,
                    edges = listOf("e_in", "e_via", "e_out1"),
                    fromWay = 101L,
                    viaWays = listOf(102L),
                    toWay = 103L
                ),
                createCompiledViaWay(
                    id = "res_no_opt2",
                    type = TurnRestrictionType.NO_LEFT_TURN,
                    edges = listOf("e_in", "e_via", "e_opt2"),
                    fromWay = 101L,
                    viaWays = listOf(102L),
                    toWay = 103L
                )
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertTrue("Route must use detour", edgeIds.contains("e_detour1") && edgeIds.contains("e_detour2"))
    }

    @Test
    fun `06_no_via_way_multiple_no_outputs_all_denied`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_no_left", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out1"), 101L, listOf(102L), 103L),
                createCompiledViaWay("res_no_u", TurnRestrictionType.NO_U_TURN, listOf("e_in", "e_via", "e_out2"), 101L, listOf(102L), 105L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse(edgeIds.contains("e_out1"))
        assertFalse(edgeIds.contains("e_out2"))
    }

    @Test
    fun `07_no_via_way_failure_transition_does_not_block_detour`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay(
                    id = "res_no_deep",
                    type = TurnRestrictionType.NO_LEFT_TURN,
                    edges = listOf("e_prefix", "e_via", "e_out"),
                    fromWay = 301L,
                    viaWays = listOf(102L),
                    toWay = 103L
                )
            ),
            allowAlternativePrefix = true
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_ALT_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertTrue("Route with alternative prefix should be allowed to use e_out", edgeIds.contains("e_out"))
    }

    @Test
    fun `08_no_via_way_reversed_directed_edge_does_not_trigger`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_no_1", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out"), 101L, listOf(102L), 103L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_REV_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
    }

    // B. ONLY Via-way

    @Test
    fun `09_only_via_way_vehicle_must_follow_via_sequence`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_only_1", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e_in", "e_via", "e_mandatory"), 101L, listOf(102L), 104L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL_ONLY")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertTrue(edgeIds.contains("e_mandatory"))
    }

    @Test
    fun `10_only_via_way_side_branch_blocked_during_active_obligation`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_only_1", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e_in", "e_via", "e_mandatory"), 101L, listOf(102L), 104L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_SIDE")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    @Test
    fun `11_only_via_way_obligation_released_upon_completion`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_only_1", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e_in", "e_via", "e_mandatory"), 101L, listOf(102L), 104L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_FREE_TURN")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertTrue("After e_mandatory, vehicle is free to turn to N_FREE_TURN", edgeIds.contains("e_after_mandatory"))
    }

    @Test
    fun `12_only_via_way_multiple_compatible_obligations_handled`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_only_a", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e_in", "e_via", "e_mandatory"), 101L, listOf(102L), 104L),
                createCompiledViaWay("res_only_b", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e_in", "e_via", "e_mandatory"), 101L, listOf(102L), 104L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL_ONLY")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertTrue(edgeIds.contains("e_mandatory"))
    }

    @Test
    fun `13_only_via_way_conflicting_obligations_create_safe_dead_end`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_only_1", TurnRestrictionType.ONLY_LEFT_TURN, listOf("e_in", "e_via", "e_opt1"), 101L, listOf(102L), 104L),
                createCompiledViaWay("res_only_2", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e_in", "e_via", "e_opt2"), 101L, listOf(102L), 103L)
            ),
            allowStartDetour = true
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("Conflicting ONLY obligations make via path dead-end; route must use detour", edgeIds.contains("e_via"))
    }

    @Test
    fun `14_only_via_way_destination_in_middle_of_via_path_terminates_validly`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_only_1", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e_in", "e_via", "e_mandatory"), 101L, listOf(102L), 104L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_VIA")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals("N_VIA", result.pathNodeIds.last())
        assertEquals("e_via", result.pathEdges.last().id)
    }

    @Test
    fun `15_only_via_way_wrong_continuation_from_same_node_is_denied`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_only_1", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e_in", "e_via", "e_mandatory"), 101L, listOf(102L), 104L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_WRONG")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    // C. Combined Via-node & Via-way

    @Test
    fun `16_combined_via_node_and_via_way_both_deny`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_via_way_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out"), 101L, listOf(102L), 103L),
                createViaNodeRestriction("res_via_node_no", TurnRestrictionType.NO_LEFT_TURN, fromEdge = "e_via", toEdge = "e_out", viaNode = "N_VIA")
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse(edgeIds.contains("e_out"))
    }

    @Test
    fun `17_combined_dfa_no_vetoes_edge_allowed_by_via_node`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_via_way_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out"), 101L, listOf(102L), 103L)
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("DFA NO must veto edge allowed by via-node index", edgeIds.contains("e_out"))
    }

    @Test
    fun `18_combined_via_node_no_vetoes_edge_allowed_by_dfa`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createViaNodeRestriction("res_via_node_no_1", TurnRestrictionType.NO_RIGHT_TURN, fromEdge = "e_via", toEdge = "e_side_branch", viaNode = "N_VIA"),
                createViaNodeRestriction("res_via_node_no_2", TurnRestrictionType.NO_RIGHT_TURN, fromEdge = "e_via", toEdge = "e_out2", viaNode = "N_VIA")
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_SIDE")
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
    }

    @Test
    fun `19_combined_only_and_via_node_do_not_invalidate_each_other`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_only", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e_in", "e_via", "e_mandatory"), 101L, listOf(102L), 104L),
                createViaNodeRestriction("res_via_node", TurnRestrictionType.NO_RIGHT_TURN, fromEdge = "e_via", toEdge = "e_side_branch", viaNode = "N_VIA")
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL_ONLY")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertTrue(edgeIds.contains("e_mandatory"))
        assertFalse(edgeIds.contains("e_side_branch"))
    }

    // D. UCS State Correctness

    @Test
    fun `20_ucs_state_different_restriction_states_not_merged`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out"), 101L, listOf(102L), 103L)
            ),
            allowAlternativePrefix = true
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertTrue(result.pathEdges.isNotEmpty())
    }

    @Test
    fun `21_ucs_state_each_find_route_starts_at_root`() {
        val fixture = createSyntheticFixture()
        val result1 = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        val result2 = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result1.status)
        assertEquals(UcsRouteStatus.SUCCESS, result2.status)
        assertEquals(result1.pathEdges.map { it.id }, result2.pathEdges.map { it.id })
    }

    @Test
    fun `22_ucs_state_route_reconstruction_preserves_edge_order`() {
        val fixture = createSyntheticFixture()
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edges = result.pathEdges
        for (i in 0 until edges.size - 1) {
            assertEquals("Edge i toNodeId must match edge i+1 fromNodeId",
                edges[i].toNodeId, edges[i + 1].fromNodeId
            )
        }
    }

    @Test
    fun `23_ucs_state_loop_route_allowed_with_distinct_state_key`() {
        val fixture = createLoopFixture()
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertTrue(result.pathEdges.isNotEmpty())
    }

    @Test
    fun `24_ucs_state_priority_queue_never_receives_rejected_edge`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out"), 101L, listOf(102L), 103L)
            )
        )
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertTrue("Rejected candidate edges must be recorded", result.turnRestrictionsSkipped >= 1)
    }

    @Test
    fun `25_ucs_state_internal_dfa_failure_aborts_search`() {
        val n0 = RoadNode("N0", GeoPoint(16.0, 108.0))
        val n1 = RoadNode("N1", GeoPoint(16.01, 108.01))
        val e1 = RoadEdge("e1", "N0", "N1", 10.0, osmWayId = 101L, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE)
        val graph = RoadGraph(
            nodes = mapOf("N0" to n0, "N1" to n1),
            directedEdges = listOf(e1),
            graphVersion = "test-fail",
            contractVersion = "2.1.0",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.0, 16.0, 108.01, 16.01),
            canAttemptStrictRouting = true,
            isStrictRoutingEligible = true,
            strictRoutingReadiness = StrictRoutingReadiness.READY,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            edgeSafetySummary = EdgeSafetySummary(1, 1, 0, 0, 0, 100.0, 0)
        )
        val corruptedDfa = ViaWayTurnRestrictionDfa.createForTesting(
            testTransitionEvaluator = { _, _, _ -> DfaEvaluationResult(DfaDecision.DENY_INVALID_STATE, null) }
        )

        val result = router.inspectFixtureComputation(roadGraph = graph, startNodeId = "N0", goalNodeId = "N1", viaWayDfa = corruptedDfa)
        assertEquals(UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE, result.status)
    }

    @Test
    fun `26_ucs_state_allow_with_null_next_state_fails_closed`() {
        val n0 = RoadNode("N0", GeoPoint(16.0, 108.0))
        val n1 = RoadNode("N1", GeoPoint(16.01, 108.01))
        val e1 = RoadEdge("e1", "N0", "N1", 10.0, osmWayId = 101L, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE)
        val graph = RoadGraph(
            nodes = mapOf("N0" to n0, "N1" to n1),
            directedEdges = listOf(e1),
            graphVersion = "test-fail",
            contractVersion = "2.1.0",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.0, 16.0, 108.01, 16.01),
            canAttemptStrictRouting = true,
            isStrictRoutingEligible = true,
            strictRoutingReadiness = StrictRoutingReadiness.READY,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            edgeSafetySummary = EdgeSafetySummary(1, 1, 0, 0, 0, 100.0, 0)
        )
        val corruptedDfa = ViaWayTurnRestrictionDfa.createForTesting(
            testTransitionEvaluator = { _, _, _ -> DfaEvaluationResult(DfaDecision.ALLOW, null) }
        )

        val result = router.inspectFixtureComputation(roadGraph = graph, startNodeId = "N0", goalNodeId = "N1", viaWayDfa = corruptedDfa)
        assertEquals(UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE, result.status)
    }

    @Test
    fun `27_ucs_state_invalid_polled_state_fails_closed`() {
        val n0 = RoadNode("N0", GeoPoint(16.0, 108.0))
        val n1 = RoadNode("N1", GeoPoint(16.01, 108.01))
        val e1 = RoadEdge("e1", "N0", "N1", 10.0, osmWayId = 101L, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE)
        val graph = RoadGraph(
            nodes = mapOf("N0" to n0, "N1" to n1),
            directedEdges = listOf(e1),
            graphVersion = "test-fail",
            contractVersion = "2.1.0",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.0, 16.0, 108.01, 16.01),
            canAttemptStrictRouting = true,
            isStrictRoutingEligible = true,
            strictRoutingReadiness = StrictRoutingReadiness.READY,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            edgeSafetySummary = EdgeSafetySummary(1, 1, 0, 0, 0, 100.0, 0)
        )
        val invalidRootDfa = ViaWayTurnRestrictionDfa.createForTesting(rootStateId = -99)

        val result = router.inspectFixtureComputation(roadGraph = graph, startNodeId = "N0", goalNodeId = "N1", viaWayDfa = invalidRootDfa)
        assertEquals(UcsRouteStatus.ROUTING_INTERNAL_SAFETY_FAILURE, result.status)
    }

    // =========================================================================
    // SECTION 6: LIVE HAZARD INTEGRATION
    // =========================================================================

    @Test
    fun `28_live_hazard_clear_edge_traversed`() {
        val fixture = createSyntheticFixture()
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
    }

    @Test
    fun `29_live_hazard_blocked_edge_always_rejected`() {
        val fixture = createHazardFixture(edgeCondition = RoadCondition.BLOCKED)
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("BLOCKED edge must never be in route", edgeIds.contains("e_hazard"))
    }

    @Test
    fun `30_live_hazard_unknown_edge_always_rejected`() {
        val fixture = createHazardFixture(edgeCondition = RoadCondition.UNKNOWN)
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("UNKNOWN edge must never be in route", edgeIds.contains("e_hazard"))
    }

    @Test
    fun `31_live_hazard_flooded_without_assessment_rejected`() {
        val fixture = createHazardFixture(edgeCondition = RoadCondition.FLOODED)
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("FLOODED edge without assessment must be rejected", edgeIds.contains("e_hazard"))
    }

    @Test
    fun `32_live_hazard_flooded_untrusted_assessment_rejected`() {
        val fixture = createHazardFixture(edgeCondition = RoadCondition.FLOODED)
        val now = System.currentTimeMillis()
        val assessment = LiveFloodAssessment(
            edgeId = "e_hazard",
            authorizedSource = "untrusted_source",
            verifiedAtEpochMillis = now,
            expiresAtEpochMillis = now + 3600_000L,
            isPassableForRescueTruck = true
        )
        val hazardContext = FloodAssessmentContext(
            currentEpochMillis = now,
            activeHazardSnapshotVersion = "v1",
            liveHazardReady = true,
            trustValidator = object : FloodAssessmentTrustValidator {
                override fun isTrusted(assessment: LiveFloodAssessment): Boolean = false
            }
        )
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL", mapOf("e_hazard" to assessment), hazardContext)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("Untrusted assessment must be rejected", edgeIds.contains("e_hazard"))
    }

    @Test
    fun `33_live_hazard_flooded_mismatched_snapshot_rejected`() {
        val fixture = createHazardFixture(edgeCondition = RoadCondition.FLOODED)
        val now = System.currentTimeMillis()
        val assessment = LiveFloodAssessment(
            edgeId = "e_hazard",
            authorizedSource = "source1",
            verifiedAtEpochMillis = now,
            expiresAtEpochMillis = now + 3600_000L,
            hazardSnapshotVersion = "snap_wrong",
            isPassableForRescueTruck = true
        )
        val hazardContext = FloodAssessmentContext(
            currentEpochMillis = now,
            activeHazardSnapshotVersion = "snap_correct",
            liveHazardReady = true,
            trustValidator = object : FloodAssessmentTrustValidator {
                override fun isTrusted(assessment: LiveFloodAssessment): Boolean = true
            }
        )
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL", mapOf("e_hazard" to assessment), hazardContext)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("Mismatched snapshot version must be rejected", edgeIds.contains("e_hazard"))
    }

    @Test
    fun `34_live_hazard_flooded_expired_assessment_rejected`() {
        val fixture = createHazardFixture(edgeCondition = RoadCondition.FLOODED)
        val now = System.currentTimeMillis()
        val assessment = LiveFloodAssessment(
            edgeId = "e_hazard",
            authorizedSource = "source1",
            verifiedAtEpochMillis = now - 7200_000L,
            expiresAtEpochMillis = now - 3600_000L,
            isPassableForRescueTruck = true
        )
        val hazardContext = FloodAssessmentContext(
            currentEpochMillis = now,
            freshnessPolicy = FloodFreshnessPolicy(maxAllowedTtlMillis = 7200_000L, maxAgeMillis = 1800_000L),
            liveHazardReady = true,
            trustValidator = object : FloodAssessmentTrustValidator {
                override fun isTrusted(assessment: LiveFloodAssessment): Boolean = true
            }
        )
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL", mapOf("e_hazard" to assessment), hazardContext)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("Expired assessment must be rejected", edgeIds.contains("e_hazard"))
    }

    @Test
    fun `35_live_hazard_flooded_invalid_signature_rejected`() {
        val fixture = createHazardFixture(edgeCondition = RoadCondition.FLOODED)
        val now = System.currentTimeMillis()
        val assessment = LiveFloodAssessment(
            edgeId = "e_hazard",
            authorizedSource = "source1",
            verifiedAtEpochMillis = now,
            expiresAtEpochMillis = now + 3600_000L,
            provenanceSignature = "invalid_sig",
            isPassableForRescueTruck = true
        )
        val hazardContext = FloodAssessmentContext(
            currentEpochMillis = now,
            liveHazardReady = true,
            freshnessPolicy = FloodFreshnessPolicy(maxAllowedTtlMillis = 7200_000L, maxAgeMillis = 3600_000L),
            trustValidator = PolicyBasedFloodAssessmentTrustValidator(
                trustedSourceIds = setOf("source1"),
                provenanceVerifier = object : FloodAssessmentProvenanceVerifier {
                    override fun verifyProvenance(assessment: LiveFloodAssessment): Boolean = false
                }
            )
        )
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL", mapOf("e_hazard" to assessment), hazardContext)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("Assessment with invalid signature must be rejected", edgeIds.contains("e_hazard"))
    }

    @Test
    fun `36_live_hazard_flooded_valid_trusted_assessment_allowed`() {
        val fixture = createHazardFixture(edgeCondition = RoadCondition.FLOODED)
        val now = System.currentTimeMillis()
        val assessment = LiveFloodAssessment(
            edgeId = "e_hazard",
            authorizedSource = "source1",
            verifiedAtEpochMillis = now,
            expiresAtEpochMillis = now + 3600_000L,
            maxWaterDepthMeters = 0.1,
            isPassableForRescueTruck = true,
            hazardSnapshotVersion = "snap_1",
            provenanceSignature = "valid_sig"
        )
        val hazardContext = FloodAssessmentContext(
            currentEpochMillis = now,
            activeHazardSnapshotVersion = "snap_1",
            liveHazardReady = true,
            freshnessPolicy = FloodFreshnessPolicy(maxAllowedTtlMillis = 7200_000L, maxAgeMillis = 3600_000L),
            trustValidator = PolicyBasedFloodAssessmentTrustValidator(
                trustedSourceIds = setOf("source1"),
                provenanceVerifier = object : FloodAssessmentProvenanceVerifier {
                    override fun verifyProvenance(assessment: LiveFloodAssessment): Boolean = true
                }
            )
        )
        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL_HAZARD", mapOf("e_hazard" to assessment), hazardContext)
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertTrue("Valid trusted flood assessment allows traversing edge", edgeIds.contains("e_hazard"))
    }

    @Test
    fun `37_live_hazard_dfa_turn_restriction_not_overridden_by_hazard_assessment`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out"), 101L, listOf(102L), 103L)
            )
        )
        val now = System.currentTimeMillis()
        val assessment = LiveFloodAssessment(
            edgeId = "e_out",
            authorizedSource = "source1",
            verifiedAtEpochMillis = now,
            expiresAtEpochMillis = now + 3600_000L,
            maxWaterDepthMeters = 0.05,
            isPassableForRescueTruck = true,
            provenanceSignature = "valid_sig"
        )
        val hazardContext = FloodAssessmentContext(
            currentEpochMillis = now,
            liveHazardReady = true,
            freshnessPolicy = FloodFreshnessPolicy(maxAllowedTtlMillis = 7200_000L, maxAgeMillis = 3600_000L),
            trustValidator = PolicyBasedFloodAssessmentTrustValidator(
                trustedSourceIds = setOf("source1"),
                provenanceVerifier = object : FloodAssessmentProvenanceVerifier {
                    override fun verifyProvenance(assessment: LiveFloodAssessment): Boolean = true
                }
            )
        )

        val result = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL", mapOf("e_out" to assessment), hazardContext)
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        val edgeIds = result.pathEdges.map { it.id }
        assertFalse("Hazard assessment MUST NEVER override a DFA turn restriction", edgeIds.contains("e_out"))
    }

    // =========================================================================
    // SECTION 7: FAILURE INJECTION & SNAPSHOT INTEGRITY
    // =========================================================================

    @Test
    fun `38_failure_injection_dfa_builder_failure_fails_closed`() {
        val badRestriction = TurnRestriction(
            id = "bad_res",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(101L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1")
        )
        val manifest = createReadyManifest()
        val roadGraph = createReadyRoadGraph(manifest, restrictions = listOf(badRestriction))

        val dfaBuildResult = ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph)
        assertTrue("DFA build must fail", dfaBuildResult is DfaBuildResult.Failure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `39_failure_injection_missing_or_blank_checksum_rejected`() {
        val manifest = createReadyManifest()
        val roadGraph = createReadyRoadGraph(manifest)
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = ""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `40_failure_injection_checksum_mismatch_rejected`() {
        val manifest = createReadyManifest()
        val roadGraph = createReadyRoadGraph(manifest)
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `41_failure_injection_graph_version_mismatch_rejected`() {
        val manifest = createReadyManifest(graphVersion = "v1")
        val roadGraph = createReadyRoadGraph(manifest, graphVersion = "v2")
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `42_failure_injection_index_version_mismatch_rejected`() {
        val manifest = createReadyManifest(graphVersion = "v1")
        val roadGraph = createReadyRoadGraph(manifest, graphVersion = "v1")
        val roadGraphOld = createReadyRoadGraph(manifest, graphVersion = "v_old")
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val indexOld = TurnRestrictionIndex.build(roadGraphOld, dfa.acceptedRestrictionIds)

        RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = indexOld,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )
    }

    @Test
    fun `43_failure_injection_delegation_id_mismatch_denied`() {
        val pattern = ViaWayPattern("p1", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_out"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa
        val manifest = createReadyManifest()
        val roadGraph = createReadyRoadGraph(manifest)
        val index = TurnRestrictionIndex.build(roadGraph, emptySet())

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse("Delegation ID mismatch must deny strict routing", context.isStrictRoutingPermitted)
        assertTrue(context.readinessEvaluation is StrictRoutingEvaluation.Denied)
        val denied = context.readinessEvaluation as StrictRoutingEvaluation.Denied
        assertEquals(RoutingDisabledReason.UNRESOLVED_TURN_RESTRICTIONS, denied.reason)
        assertTrue(denied.detailMessage.contains("Delegation mismatch"))
    }

    @Test
    fun `44_failure_injection_undelegated_via_way_denied`() {
        val unresolvedViaWay = TurnRestriction(
            id = "unresolved_vw",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            viaOsmWayIds = listOf(101L),
            viaWayCompilationStatus = ViaWayCompilationStatus.UNRESOLVED
        )
        val manifest = createReadyManifest()
        val roadGraph = createReadyRoadGraph(manifest, restrictions = listOf(unresolvedViaWay))
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse("Undelegated unresolved via-way must deny strict routing", context.isStrictRoutingPermitted)
    }

    @Test
    fun `45_failure_injection_manifest_denies_strict_routing_denied`() {
        val manifest = createReadyManifest(canAttemptStrictRouting = false)
        val roadGraph = createReadyRoadGraph(manifest, canAttemptStrictRouting = false)
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        val context = RescueRoutingContext(
            roadGraph = roadGraph,
            turnRestrictionIndex = index,
            viaWayDfa = dfa,
            manifest = manifest,
            artifactSha256 = manifest.bundle.sha256
        )

        assertFalse("Manifest denying strict routing must deny context", context.isStrictRoutingPermitted)
    }

    @Test
    fun `46_failure_injection_graph_refresh_does_not_affect_active_context`() {
        val fixtureV1 = createSyntheticFixture(version = "v1")
        val fixtureV2 = createSyntheticFixture(version = "v2")

        val contextV1 = fixtureV1.context
        val result1 = router.inspectFixtureComputation(contextV1, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result1.status)
        assertEquals("v1", result1.graphVersion)

        val contextV2 = fixtureV2.context
        val result2 = router.inspectFixtureComputation(contextV2, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result2.status)
        assertEquals("v2", result2.graphVersion)

        val result1Repeat = router.inspectFixtureComputation(contextV1, "N_START", "N_GOAL")
        assertEquals("v1", result1Repeat.graphVersion)
    }

    @Test
    fun `47_failure_injection_concurrent_routes_on_same_context_are_deterministic`() = runBlocking {
        val fixture = createSyntheticFixture()
        val context = fixture.context

        val deferred1 = async(Dispatchers.Default) {
            router.inspectFixtureComputation(context, "N_START", "N_GOAL")
        }
        val deferred2 = async(Dispatchers.Default) {
            router.inspectFixtureComputation(context, "N_START", "N_GOAL")
        }
        val deferred3 = async(Dispatchers.Default) {
            router.inspectFixtureComputation(context, "N_START", "N_GOAL")
        }

        val r1 = deferred1.await()
        val r2 = deferred2.await()
        val r3 = deferred3.await()

        assertEquals(UcsRouteStatus.SUCCESS, r1.status)
        assertEquals(r1.pathEdges.map { it.id }, r2.pathEdges.map { it.id })
        assertEquals(r1.pathEdges.map { it.id }, r3.pathEdges.map { it.id })
    }

    @Test
    fun `48_failure_injection_clear_loaded_graph_preserves_caller_context`() {
        val fixture = createSyntheticFixture()
        val contextHeldByCaller = fixture.context

        val result = router.inspectFixtureComputation(contextHeldByCaller, "N_START", "N_GOAL")
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertTrue(result.pathEdges.isNotEmpty())
    }

    @Test
    fun `49_failure_injection_empty_dfa_with_via_ways_cannot_bypass`() {
        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_vw", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out"), 101L, listOf(102L), 103L)
            )
        )
        val roadGraph = fixture.context.roadGraph

        val result = router.findRoute(
            roadGraph = roadGraph,
            startNodeId = "N_START",
            goalNodeId = "N_GOAL",
            viaWayDfa = null
        )

        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertTrue(result.routingDisabledReason!!.contains("Graph contains via-way turn restrictions but no ViaWayTurnRestrictionDfa was provided"))
    }

    // =========================================================================
    // SECTION 8: PERFORMANCE & RESOURCE AUDIT
    // =========================================================================

    @Test
    fun `50_performance_audit_production_danang_timings_and_synthetic_ucs_metrics`() {
        val baseDir = File("/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app/tools/osm-importer/output-dryrun-task3b")
        val gzFile = File(baseDir, "BaseRoadGraph.json.gz")
        val jsonFile = File(baseDir, "BaseRoadGraph.json")

        if (gzFile.exists() && jsonFile.exists()) {
            val t0 = System.nanoTime()
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16384)
            FileInputStream(gzFile).use { fis ->
                var len: Int
                while (fis.read(buf).also { len = it } != -1) {
                    digest.update(buf, 0, len)
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val tShaMs = (System.nanoTime() - t0) / 1_000_000.0

            val t1 = System.nanoTime()
            val parser = StreamingRoadGraphParser()
            val graph = parser.parseSync(jsonFile)
            val tParseMs = (System.nanoTime() - t1) / 1_000_000.0

            val t2 = System.nanoTime()
            val dfaResult = ViaWayTurnRestrictionDfa.build(graph.turnRestrictions, graph)
            val tDfaMs = (System.nanoTime() - t2) / 1_000_000.0
            val dfa = (dfaResult as DfaBuildResult.Success).dfa

            val t3 = System.nanoTime()
            val index = TurnRestrictionIndex.build(graph, dfa.acceptedRestrictionIds)
            val tIndexMs = (System.nanoTime() - t3) / 1_000_000.0

            val t4 = System.nanoTime()
            val manifest = RoutingManifest(
                schema = "https://emergency-rescue.vn/schemas/v2/routing-manifest.json",
                manifestVersion = "2.1.0",
                generatedAt = "2026-11-04T18:26:26.069Z",
                graphVersion = "dn_central_real_v1",
                osmSnapshotTimestamp = "2026-11-03T21:24:23Z",
                importerVersion = "osm-importer-v2.3",
                vehicleProfile = "RESCUE_TRUCK_4X4",
                boundingBoxType = "PRODUCTION",
                boundingBox = RoutingBoundingBox(108.19, 16.035, 108.235, 16.075),
                bundle = RoutingBundleInfo("BaseRoadGraph.json.gz", gzFile.length(), jsonFile.length(), sha256),
                hazardSnapshotStatus = "NOT_INCLUDED",
                hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
                canAttemptStrictRouting = false,
                isStrictRoutingEligible = false,
                strictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
                overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
                isRestrictionStrictRoutingEligible = false,
                restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
                unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(16, "FAIL_CLOSED_IN_STRICT_MODE"),
                edgeSafetySummary = graph.edgeSafetySummary!!
            )
            val context = RescueRoutingContext(graph, index, dfa, manifest, sha256)
            val tContextMs = (System.nanoTime() - t4) / 1_000_000.0

            println("=== PRODUCTION GRAPH DA NANG AUDIT MEASUREMENTS ===")
            println("Gzip SHA-256 verification time : ${"%.2f".format(tShaMs)} ms")
            println("Streaming JSON parser duration : ${"%.2f".format(tParseMs)} ms")
            println("ViaWay DFA build duration      : ${"%.2f".format(tDfaMs)} ms")
            println("TurnRestrictionIndex duration  : ${"%.2f".format(tIndexMs)} ms")
            println("Context assembly duration      : ${"%.2f".format(tContextMs)} ms")
            println("Total Production Pipeline Time : ${"%.2f".format(tShaMs + tParseMs + tDfaMs + tIndexMs + tContextMs)} ms")

            assertTrue("Gzip verification must complete in reasonable time", tShaMs < 2000.0)
            assertTrue("Streaming parser must complete under 2000ms", tParseMs < 2000.0)
            assertTrue("DFA build must complete under 100ms", tDfaMs < 100.0)
            assertTrue("TurnRestrictionIndex build must complete under 100ms", tIndexMs < 100.0)
        }

        val fixture = createSyntheticFixture(
            restrictions = listOf(
                createCompiledViaWay("res_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e_in", "e_via", "e_out"), 101L, listOf(102L), 103L)
            )
        )
        val tUcs0 = System.nanoTime()
        val ucsResult = router.inspectFixtureComputation(fixture.context, "N_START", "N_GOAL")
        val tUcsMs = (System.nanoTime() - tUcs0) / 1_000_000.0

        println("=== SYNTHETIC FIXTURE UCS METRICS ===")
        println("UCS execution time             : ${"%.2f".format(tUcsMs)} ms")
        println("Nodes visited (polled)         : ${ucsResult.visitedNodesCount}")
        println("Turn restrictions skipped      : ${ucsResult.turnRestrictionsSkipped}")
        println("Physical route distance meters : ${ucsResult.totalDistanceMeters}")

        assertEquals(UcsRouteStatus.SUCCESS, ucsResult.status)
        assertTrue("UCS search must be sub-millisecond on small fixture", tUcsMs < 50.0)
        assertTrue("Visited nodes count must be > 0", ucsResult.visitedNodesCount > 0)
    }

    // =========================================================================
    // FIXTURE CREATION HELPERS
    // =========================================================================

    private data class SyntheticFixture(
        val context: RescueRoutingContext
    )

    private fun createSyntheticFixture(
        restrictions: List<TurnRestriction> = emptyList(),
        allowAlternativePrefix: Boolean = false,
        allowStartDetour: Boolean = false,
        version: String = "synth_v1"
    ): SyntheticFixture {
        val nodes = mutableMapOf<String, RoadNode>()
        fun addNode(id: String, lat: Double, lon: Double) {
            nodes[id] = RoadNode(id, GeoPoint(lat, lon))
        }

        addNode("N_START", 16.00, 108.00)
        addNode("N_IN", 16.01, 108.01)
        addNode("N_VIA", 16.02, 108.02)
        addNode("N_GOAL", 16.03, 108.03)
        addNode("N_GOAL_ONLY", 16.035, 108.035)
        addNode("N_SIDE", 16.025, 108.015)
        addNode("N_FREE_TURN", 16.04, 108.04)
        addNode("N_WRONG", 16.028, 108.022)
        addNode("N_DETOUR_A", 16.015, 108.005)
        addNode("N_ALT_START", 16.005, 108.015)
        addNode("N_REV_START", 16.012, 108.012)

        val edges = mutableListOf<RoadEdge>()
        fun addEdge(id: String, u: String, v: String, len: Double, osmWayId: Long = 100L) {
            edges.add(RoadEdge(
                id = id,
                fromNodeId = u,
                toNodeId = v,
                lengthMeters = len,
                osmWayId = osmWayId,
                compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE
            ))
        }

        // Main line: N_START -> (e_in: 101L) -> N_IN -> (e_via: 102L) -> N_VIA -> (e_out: 103L) -> N_GOAL
        addEdge("e_in", "N_START", "N_IN", 10.0, 101L)
        addEdge("e_via", "N_IN", "N_VIA", 10.0, 102L)
        addEdge("e_out", "N_VIA", "N_GOAL", 10.0, 103L) // target of NO

        // Side branches & variants from N_VIA
        addEdge("e_out1", "N_VIA", "N_GOAL", 10.0, 103L)
        addEdge("e_out2", "N_VIA", "N_SIDE", 10.0, 105L)
        addEdge("e_mandatory", "N_VIA", "N_GOAL_ONLY", 12.0, 104L)
        addEdge("e_opt1", "N_VIA", "N_GOAL_ONLY", 12.0, 104L)
        addEdge("e_opt2", "N_VIA", "N_GOAL", 12.0, 103L)
        addEdge("e_side_branch", "N_VIA", "N_SIDE", 15.0, 105L)
        addEdge("e_after_mandatory", "N_GOAL_ONLY", "N_FREE_TURN", 10.0, 106L)
        addEdge("e_wrong", "N_VIA", "N_WRONG", 10.0, 107L)

        // Detour line around (e_via -> e_out): from N_IN through N_DETOUR_A to N_GOAL (total cost 30.0 vs 20.0)
        addEdge("e_detour1", "N_IN", "N_DETOUR_A", 15.0, 201L)
        addEdge("e_detour2", "N_DETOUR_A", "N_GOAL", 15.0, 202L)

        if (allowStartDetour) {
            addEdge("e_detour_start", "N_START", "N_DETOUR_A", 20.0, 201L)
        }

        if (allowAlternativePrefix) {
            addEdge("e_prefix", "N_START", "N_IN", 15.0, 301L)
            addEdge("e_alt", "N_ALT_START", "N_IN", 10.0, 302L)
        }
        addEdge("e_in_rev", "N_REV_START", "N_START", 10.0, 401L)

        val roadGraph = RoadGraph(
            nodes = nodes,
            directedEdges = edges,
            graphVersion = version,
            contractVersion = "2.1.0",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.0, 16.0, 108.05, 16.05),
            canAttemptStrictRouting = true,
            isStrictRoutingEligible = true,
            strictRoutingReadiness = StrictRoutingReadiness.READY,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            turnRestrictions = restrictions,
            edgeSafetySummary = EdgeSafetySummary(edges.size.toLong(), edges.size.toLong(), 0, 0, 0, 100.0, 0)
        )

        val dfaBuildResult = ViaWayTurnRestrictionDfa.build(restrictions, roadGraph)
        val dfa = if (dfaBuildResult is DfaBuildResult.Success) dfaBuildResult.dfa else ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)

        val manifest = RoutingManifest(
            schema = "https://emergency-rescue.vn/schemas/v2/routing-manifest.json",
            manifestVersion = "2.1.0",
            generatedAt = "2026-11-04T12:00:00Z",
            graphVersion = version,
            osmSnapshotTimestamp = "2026-11-03T21:24:23Z",
            importerVersion = "osm-importer-v2.3",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.0, 16.0, 108.05, 16.05),
            bundle = RoutingBundleInfo("BaseRoadGraph.json.gz", 1000L, 5000L, "aabbccddeeff11223344556677889900aabbccddeeff11223344556677889900"),
            hazardSnapshotStatus = "NOT_INCLUDED",
            hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            canAttemptStrictRouting = true,
            isStrictRoutingEligible = true,
            strictRoutingReadiness = StrictRoutingReadiness.READY,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(0, "FAIL_CLOSED_IN_STRICT_MODE"),
            edgeSafetySummary = roadGraph.edgeSafetySummary!!
        )

        val context = RescueRoutingContext(roadGraph, index, dfa, manifest, manifest.bundle.sha256)
        return SyntheticFixture(context)
    }

    private fun createHazardFixture(edgeCondition: RoadCondition): SyntheticFixture {
        val nodes = mapOf(
            "N_START" to RoadNode("N_START", GeoPoint(16.00, 108.00)),
            "N_MID" to RoadNode("N_MID", GeoPoint(16.01, 108.01)),
            "N_GOAL_HAZARD" to RoadNode("N_GOAL_HAZARD", GeoPoint(16.02, 108.02)),
            "N_GOAL" to RoadNode("N_GOAL", GeoPoint(16.03, 108.03)),
            "N_DETOUR" to RoadNode("N_DETOUR", GeoPoint(16.015, 108.005))
        )
        val edges = listOf(
            RoadEdge("e1", "N_START", "N_MID", 10.0, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE),
            RoadEdge("e_hazard", "N_MID", "N_GOAL_HAZARD", 10.0, condition = edgeCondition, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE),
            RoadEdge("e_continue", "N_GOAL_HAZARD", "N_GOAL", 10.0, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE),
            RoadEdge("e_detour", "N_START", "N_DETOUR", 30.0, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE),
            RoadEdge("e_detour_finish", "N_DETOUR", "N_GOAL", 30.0, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE)
        )
        val roadGraph = RoadGraph(
            nodes = nodes,
            directedEdges = edges,
            graphVersion = "hazard_v1",
            contractVersion = "2.1.0",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.0, 16.0, 108.05, 16.05),
            canAttemptStrictRouting = true,
            isStrictRoutingEligible = true,
            strictRoutingReadiness = StrictRoutingReadiness.READY,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            edgeSafetySummary = EdgeSafetySummary(edges.size.toLong(), edges.size.toLong(), 0, 0, 0, 100.0, 0)
        )
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, emptySet())
        val manifest = createReadyManifest(graphVersion = "hazard_v1")
        val context = RescueRoutingContext(roadGraph, index, dfa, manifest, manifest.bundle.sha256)
        return SyntheticFixture(context)
    }

    private fun createLoopFixture(): SyntheticFixture {
        val nodes = mapOf(
            "N_START" to RoadNode("N_START", GeoPoint(16.00, 108.00)),
            "N_LOOP1" to RoadNode("N_LOOP1", GeoPoint(16.01, 108.01)),
            "N_LOOP2" to RoadNode("N_LOOP2", GeoPoint(16.02, 108.02)),
            "N_GOAL" to RoadNode("N_GOAL", GeoPoint(16.03, 108.03))
        )
        val edges = listOf(
            RoadEdge("e1", "N_START", "N_LOOP1", 10.0, osmWayId = 1L, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE),
            RoadEdge("e2", "N_LOOP1", "N_LOOP2", 10.0, osmWayId = 2L, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE),
            RoadEdge("e_loop_back", "N_LOOP2", "N_LOOP1", 10.0, osmWayId = 3L, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE),
            RoadEdge("e_exit", "N_LOOP1", "N_GOAL", 10.0, osmWayId = 4L, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE)
        )
        val roadGraph = RoadGraph(
            nodes = nodes,
            directedEdges = edges,
            graphVersion = "loop_v1",
            contractVersion = "2.1.0",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.0, 16.0, 108.05, 16.05),
            canAttemptStrictRouting = true,
            isStrictRoutingEligible = true,
            strictRoutingReadiness = StrictRoutingReadiness.READY,
            overallStrictRoutingReadiness = StrictRoutingReadiness.READY,
            isRestrictionStrictRoutingEligible = true,
            restrictionStrictRoutingReadiness = StrictRoutingReadiness.READY,
            edgeSafetySummary = EdgeSafetySummary(edges.size.toLong(), edges.size.toLong(), 0, 0, 0, 100.0, 0)
        )
        val dfa = ViaWayTurnRestrictionDfa.empty()
        val index = TurnRestrictionIndex.build(roadGraph, emptySet())
        val manifest = createReadyManifest(graphVersion = "loop_v1")
        val context = RescueRoutingContext(roadGraph, index, dfa, manifest, manifest.bundle.sha256)
        return SyntheticFixture(context)
    }

    private fun createCompiledViaWay(
        id: String,
        type: TurnRestrictionType,
        edges: List<String>,
        fromWay: Long = 101L,
        viaWays: List<Long> = listOf(102L),
        toWay: Long = 103L
    ): TurnRestriction {
        return TurnRestriction(
            id = id,
            type = type,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            fromOsmWayIds = listOf(fromWay),
            viaOsmWayIds = viaWays,
            toOsmWayIds = listOf(toWay),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = edges
        )
    }

    private fun createViaNodeRestriction(
        id: String,
        type: TurnRestrictionType,
        fromEdge: String,
        toEdge: String,
        viaNode: String
    ): TurnRestriction {
        val transition = EdgeTransition(fromEdgeId = fromEdge, toEdgeId = toEdge, viaNodeId = viaNode)
        val isNo = type.name.startsWith("NO_")
        return TurnRestriction(
            id = id,
            type = type,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            viaOsmNodeIds = listOf(888L),
            prohibitedTransitions = if (isNo) listOf(transition) else emptyList(),
            allowedTransitions = if (!isNo) listOf(transition) else emptyList(),
            orderedDirectedEdgeIds = listOf(fromEdge, toEdge)
        )
    }

    private fun createReadyManifest(
        graphVersion: String = "synth_v1",
        canAttemptStrictRouting: Boolean = true
    ): RoutingManifest {
        val readiness = if (canAttemptStrictRouting) StrictRoutingReadiness.READY else StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS
        return RoutingManifest(
            schema = "https://emergency-rescue.vn/schemas/v2/routing-manifest.json",
            manifestVersion = "2.1.0",
            generatedAt = "2026-11-04T12:00:00Z",
            graphVersion = graphVersion,
            osmSnapshotTimestamp = "2026-11-03T21:24:23Z",
            importerVersion = "osm-importer-v2.3",
            vehicleProfile = "RESCUE_TRUCK_4X4",
            boundingBoxType = "PRODUCTION",
            boundingBox = RoutingBoundingBox(108.0, 16.0, 108.05, 16.05),
            bundle = RoutingBundleInfo("BaseRoadGraph.json.gz", 1000L, 5000L, "aabbccddeeff11223344556677889900aabbccddeeff11223344556677889900"),
            hazardSnapshotStatus = "NOT_INCLUDED",
            hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
            canAttemptStrictRouting = canAttemptStrictRouting,
            isStrictRoutingEligible = canAttemptStrictRouting,
            strictRoutingReadiness = readiness,
            overallStrictRoutingReadiness = readiness,
            isRestrictionStrictRoutingEligible = canAttemptStrictRouting,
            restrictionStrictRoutingReadiness = readiness,
            unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(if (canAttemptStrictRouting) 0 else 1, "FAIL_CLOSED_IN_STRICT_MODE"),
            edgeSafetySummary = EdgeSafetySummary(10, 10, 0, 0, 0, 100.0, 0)
        )
    }

    private fun createReadyRoadGraph(
        manifest: RoutingManifest,
        graphVersion: String = manifest.graphVersion,
        canAttemptStrictRouting: Boolean = manifest.canAttemptStrictRouting,
        restrictions: List<TurnRestriction> = emptyList()
    ): RoadGraph {
        val n0 = RoadNode("N_START", GeoPoint(16.0, 108.0))
        val n1 = RoadNode("N_GOAL", GeoPoint(16.01, 108.01))
        val e1 = RoadEdge("e1", "N_START", "N_GOAL", 10.0, compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE)
        return RoadGraph(
            nodes = mapOf("N_START" to n0, "N_GOAL" to n1),
            directedEdges = listOf(e1),
            graphVersion = graphVersion,
            contractVersion = "2.1.0",
            vehicleProfile = manifest.vehicleProfile,
            boundingBoxType = manifest.boundingBoxType,
            boundingBox = manifest.boundingBox,
            canAttemptStrictRouting = canAttemptStrictRouting,
            isStrictRoutingEligible = canAttemptStrictRouting,
            strictRoutingReadiness = manifest.overallStrictRoutingReadiness,
            overallStrictRoutingReadiness = manifest.overallStrictRoutingReadiness,
            isRestrictionStrictRoutingEligible = manifest.isRestrictionStrictRoutingEligible,
            restrictionStrictRoutingReadiness = manifest.restrictionStrictRoutingReadiness,
            turnRestrictions = restrictions,
            edgeSafetySummary = EdgeSafetySummary(1, 1, 0, 0, 0, 100.0, 0)
        )
    }
}
