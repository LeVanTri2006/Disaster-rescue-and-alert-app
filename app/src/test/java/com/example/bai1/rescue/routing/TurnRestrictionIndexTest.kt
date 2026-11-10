package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.algorithm.DefaultTurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.TransitionDecision
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.model.EdgeTransition
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoadCondition
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.ViaWayCompilationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TurnRestrictionIndexTest {

    private lateinit var nodeA: RoadNode
    private lateinit var nodeV: RoadNode
    private lateinit var nodeB: RoadNode
    private lateinit var nodeC: RoadNode
    private lateinit var nodeD: RoadNode

    private lateinit var edgeAV: RoadEdge
    private lateinit var edgeVB: RoadEdge
    private lateinit var edgeVC: RoadEdge
    private lateinit var edgeVD: RoadEdge

    @Before
    fun setUp() {
        nodeA = RoadNode(id = "node_A", position = GeoPoint(16.0, 107.99), osmNodeId = 1001L)
        nodeV = RoadNode(id = "node_V", position = GeoPoint(16.0, 108.0), osmNodeId = 2000L)
        nodeB = RoadNode(id = "node_B", position = GeoPoint(16.01, 108.0), osmNodeId = 1002L)
        nodeC = RoadNode(id = "node_C", position = GeoPoint(16.0, 108.01), osmNodeId = 1003L)
        nodeD = RoadNode(id = "node_D", position = GeoPoint(15.99, 108.0), osmNodeId = 1004L)

        edgeAV = RoadEdge(
            id = "e_AV",
            osmWayId = 501L,
            fromNodeId = "node_A",
            toNodeId = "node_V",
            lengthMeters = 100.0,
            condition = RoadCondition.CLEAR
        )
        edgeVB = RoadEdge(
            id = "e_VB",
            osmWayId = 502L,
            fromNodeId = "node_V",
            toNodeId = "node_B",
            lengthMeters = 100.0,
            condition = RoadCondition.CLEAR
        )
        edgeVC = RoadEdge(
            id = "e_VC",
            osmWayId = 503L,
            fromNodeId = "node_V",
            toNodeId = "node_C",
            lengthMeters = 100.0,
            condition = RoadCondition.CLEAR
        )
        edgeVD = RoadEdge(
            id = "e_VD",
            osmWayId = 504L,
            fromNodeId = "node_V",
            toNodeId = "node_D",
            lengthMeters = 100.0,
            condition = RoadCondition.CLEAR
        )
    }

    private fun createGraph(restrictions: List<TurnRestriction>): RoadGraph {
        return RoadGraph(
            nodes = listOf(nodeA, nodeV, nodeB, nodeC, nodeD).associateBy { it.id },
            directedEdges = listOf(edgeAV, edgeVB, edgeVC, edgeVD),
            graphVersion = "test_v1",
            turnRestrictions = restrictions
        )
    }

    // 1. Không có restriction: transition thông thường được phép
    @Test
    fun `test 1 - testNoRestrictions_allowsTransition`() {
        val graph = createGraph(emptyList())
        val index = TurnRestrictionIndex.build(graph)

        val result = index.checkTransition("e_AV", "e_VB", "node_V")
        assertTrue(result.isAllowed)
        assertEquals(TransitionDecision.ALLOW, result.decision)
        assertTrue(index.isStrictRoutingCompatible)
        assertFalse(index.hasUnresolvedRestrictions)
        assertEquals(0, index.totalRestrictionsCount)
    }

    // 2. NO_LEFT_TURN: transition cụ thể bị cấm
    @Test
    fun `test 2 - testNoLeftTurn_prohibitsSpecificTransition`() {
        val noLeftTurn = TurnRestriction(
            id = "restr_no_left",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(noLeftTurn)))

        val leftResult = index.checkTransition("e_AV", "e_VB", "node_V")
        assertFalse(leftResult.isAllowed)
        assertEquals(TransitionDecision.DENY_PROHIBITED, leftResult.decision)
        assertEquals("restr_no_left", leftResult.restrictionId)
        assertEquals(TurnRestrictionType.NO_LEFT_TURN, leftResult.restrictionType)

        // Hướng đi thẳng sang C hoặc rẽ phải sang D vẫn được phép
        val straightResult = index.checkTransition("e_AV", "e_VC", "node_V")
        assertTrue(straightResult.isAllowed)
        assertEquals(TransitionDecision.ALLOW, straightResult.decision)
    }

    // 3. NO_RIGHT_TURN: cấm rẽ phải
    @Test
    fun `test 3 - testNoRightTurn_prohibitsSpecificTransition`() {
        val noRightTurn = TurnRestriction(
            id = "restr_no_right",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(noRightTurn)))

        val resultDenied = index.checkTransition("e_AV", "e_VD", "node_V")
        assertEquals(TransitionDecision.DENY_PROHIBITED, resultDenied.decision)
        assertEquals(TurnRestrictionType.NO_RIGHT_TURN, resultDenied.restrictionType)

        val resultAllowed = index.checkTransition("e_AV", "e_VB", "node_V")
        assertEquals(TransitionDecision.ALLOW, resultAllowed.decision)
    }

    // 4. NO_U_TURN: cấm quay đầu
    @Test
    fun `test 4 - testNoUTurn_prohibitsSpecificTransition`() {
        val edgeVA = RoadEdge(
            id = "e_VA",
            osmWayId = 501L,
            fromNodeId = "node_V",
            toNodeId = "node_A",
            lengthMeters = 100.0,
            condition = RoadCondition.CLEAR
        )
        val graph = RoadGraph(
            nodes = listOf(nodeA, nodeV, nodeB, nodeC, nodeD).associateBy { it.id },
            directedEdges = listOf(edgeAV, edgeVA, edgeVB, edgeVC, edgeVD),
            graphVersion = "test_v1",
            turnRestrictions = listOf(
                TurnRestriction(
                    id = "restr_no_u_turn",
                    type = TurnRestrictionType.NO_U_TURN,
                    supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
                    prohibitedTransitions = listOf(
                        EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VA", viaNodeId = "node_V")
                    )
                )
            )
        )
        val index = TurnRestrictionIndex.build(graph)

        val uTurnResult = index.checkTransition("e_AV", "e_VA", "node_V")
        assertEquals(TransitionDecision.DENY_PROHIBITED, uTurnResult.decision)
        assertEquals(TurnRestrictionType.NO_U_TURN, uTurnResult.restrictionType)
    }

    // 5. ONLY_STRAIGHT_ON: chỉ được đi theo transition cho phép
    @Test
    fun `test 5 - testOnlyStraightOn_allowsOnlySpecifiedTransition`() {
        val onlyStraight = TurnRestriction(
            id = "restr_only_straight",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V")
            ),
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(onlyStraight)))

        // Đi thẳng sang C được phép
        val straightResult = index.checkTransition("e_AV", "e_VC", "node_V")
        assertEquals(TransitionDecision.ALLOW, straightResult.decision)

        // Rẽ trái sang B bị từ chối
        val leftResult = index.checkTransition("e_AV", "e_VB", "node_V")
        assertFalse(leftResult.isAllowed)

        // Rẽ phải sang D bị từ chối
        val rightResult = index.checkTransition("e_AV", "e_VD", "node_V")
        assertFalse(rightResult.isAllowed)
    }

    // 6. ONLY_LEFT_TURN: chỉ cho phép rẽ trái, từ chối mọi hướng khác
    @Test
    fun `test 6 - testOnlyLeftTurn_prohibitsOtherOutgoingEdges`() {
        val onlyLeft = TurnRestriction(
            id = "restr_only_left",
            type = TurnRestrictionType.ONLY_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            ),
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(onlyLeft)))

        assertEquals(TransitionDecision.ALLOW, index.checkTransition("e_AV", "e_VB", "node_V").decision)
        assertEquals(TransitionDecision.DENY_PROHIBITED, index.checkTransition("e_AV", "e_VC", "node_V").decision)
        assertEquals(TransitionDecision.DENY_PROHIBITED, index.checkTransition("e_AV", "e_VD", "node_V").decision)
    }

    // 7. Nhiều restriction cùng ảnh hưởng đến một incoming edge
    @Test
    fun `test 7 - testMultipleRestrictionsOnSameIncomingEdge`() {
        val noLeft = TurnRestriction(
            id = "restr_1",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val noRight = TurnRestriction(
            id = "restr_2",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(noLeft, noRight)))

        // Cả rẽ trái và rẽ phải đều bị cấm
        assertEquals(TransitionDecision.DENY_PROHIBITED, index.checkTransition("e_AV", "e_VB", "node_V").decision)
        assertEquals(TransitionDecision.DENY_PROHIBITED, index.checkTransition("e_AV", "e_VD", "node_V").decision)

        // Chỉ đi thẳng sang C là được phép
        assertEquals(TransitionDecision.ALLOW, index.checkTransition("e_AV", "e_VC", "node_V").decision)

        val prohibitedList = index.getProhibitedTransitionsForIncoming("e_AV", "node_V")
        assertEquals(setOf("e_VB", "e_VD"), prohibitedList)
    }

    // 8. Nhiều from/to ways (Ví dụ NO_ENTRY từ nhiều hướng)
    @Test
    fun `test 8 - testMultipleFromToWays`() {
        val edgeA2V = RoadEdge(
            id = "e_A2V",
            osmWayId = 505L,
            fromNodeId = "node_A",
            toNodeId = "node_V",
            lengthMeters = 100.0,
            condition = RoadCondition.CLEAR
        )
        val graph = RoadGraph(
            nodes = listOf(nodeA, nodeV, nodeB, nodeC, nodeD).associateBy { it.id },
            directedEdges = listOf(edgeAV, edgeA2V, edgeVB, edgeVC, edgeVD),
            graphVersion = "test_v1",
            turnRestrictions = listOf(
                TurnRestriction(
                    id = "restr_no_entry",
                    type = TurnRestrictionType.NO_ENTRY,
                    supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
                    prohibitedTransitions = listOf(
                        EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V"),
                        EdgeTransition(fromEdgeId = "e_A2V", toEdgeId = "e_VB", viaNodeId = "node_V")
                    )
                )
            )
        )
        val index = TurnRestrictionIndex.build(graph)

        assertEquals(TransitionDecision.DENY_PROHIBITED, index.checkTransition("e_AV", "e_VB", "node_V").decision)
        assertEquals(TransitionDecision.DENY_PROHIBITED, index.checkTransition("e_A2V", "e_VB", "node_V").decision)
        assertEquals(TransitionDecision.ALLOW, index.checkTransition("e_AV", "e_VC", "node_V").decision)
    }

    // 9. Incoming edge không khớp restriction -> Cho phép
    @Test
    fun `test 9 - testIncomingEdgeMismatch_allowsTransition`() {
        val noLeft = TurnRestriction(
            id = "restr_no_left",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_OTHER", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(noLeft)))

        // Đến từ e_AV không bị cấm
        assertEquals(TransitionDecision.ALLOW, index.checkTransition("e_AV", "e_VB", "node_V").decision)
    }

    // 10. Via node không khớp restriction -> Cho phép
    @Test
    fun `test 10 - testViaNodeMismatch_allowsTransition`() {
        val noLeft = TurnRestriction(
            id = "restr_no_left",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_OTHER")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(noLeft)))

        assertEquals(TransitionDecision.ALLOW, index.checkTransition("e_AV", "e_VB", "node_V").decision)
    }

    // 11. FAIL_CLOSED_UNRESOLVED được giữ nguyên và đánh dấu strict incompatible
    @Test
    fun `test 11 - testFailClosedUnresolved_isRecordedAndMarksStrictIncompatible`() {
        val unresolved = TurnRestriction(
            id = "restr_unresolved_1",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            fromOsmWayIds = listOf(501L),
            viaOsmNodeIds = listOf(2000L),
            toOsmWayIds = listOf(502L),
            validationError = "Topological disconnect"
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(unresolved)))

        assertTrue(index.hasUnresolvedRestrictions)
        assertFalse(index.isStrictRoutingCompatible)
        assertTrue(index.unresolvedRestrictionIds.contains("restr_unresolved_1"))

        // Kiểm tra chuyển tiếp chạm vào restriction chưa giải quyết -> Trả về UNRESOLVED (fail-closed)
        val result = index.checkTransition("e_AV", "e_VB", "node_V")
        assertEquals(TransitionDecision.UNRESOLVED, result.decision)
        assertEquals("restr_unresolved_1", result.restrictionId)
    }

    // 12. UNVERIFIED_WARNING được ghi nhận
    @Test
    fun `test 12 - testUnverifiedWarning_isRecorded`() {
        val warning = TurnRestriction(
            id = "restr_warning_1",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            supportStatus = RestrictionSupportStatus.UNVERIFIED_WARNING,
            conditionalExpression = "no_right_turn @ (06:00-09:00)"
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(warning)))

        assertTrue(index.unverifiedRestrictionIds.contains("restr_warning_1"))
        assertTrue(index.hasUnresolvedRestrictions)
        assertFalse(index.isStrictRoutingCompatible)
    }

    // 13. Conditional unresolved không được tự động cho phép
    @Test
    fun `test 13 - testConditionalUnresolved_notAutomaticallyAllowed`() {
        val conditional = TurnRestriction(
            id = "restr_cond_1",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            conditionalExpression = "no_left_turn @ (06:30-08:00,16:00-19:00)",
            fromOsmWayIds = listOf(501L),
            viaOsmNodeIds = listOf(2000L),
            toOsmWayIds = listOf(502L)
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(conditional)))

        assertTrue(index.conditionalRestrictionIds.contains("restr_cond_1"))
        assertFalse(index.isStrictRoutingCompatible)

        val result = index.checkTransition("e_AV", "e_VB", "node_V")
        assertEquals(TransitionDecision.UNRESOLVED, result.decision)
    }

    // 14. Via-way unresolved làm strict routing không sẵn sàng
    @Test
    fun `test 14 - testViaWayUnresolved_marksStrictRoutingIncompatible`() {
        val viaWay = TurnRestriction(
            id = "restr_via_way_1",
            type = TurnRestrictionType.NO_U_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            fromOsmWayIds = listOf(501L),
            viaOsmWayIds = listOf(99999L),
            toOsmWayIds = listOf(502L),
            validationError = "Via-way restrictions require multi-hop state machine not yet implemented"
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(viaWay)))

        assertTrue(index.viaWayRestrictionIds.contains("restr_via_way_1"))
        assertEquals(1, index.viaWayRestrictions.size)
        assertEquals("restr_via_way_1", index.viaWayRestrictions[0].id)
        assertFalse(index.isStrictRoutingCompatible)
    }

    // 15. Exemption chỉ áp dụng khi có xác nhận rõ ràng
    @Test
    fun `test 15 - testExemption_appliesOnlyWhenConfirmed`() {
        // Trường hợp A: Đã được xác nhận miễn trừ cho hồ sơ xe cứu hộ
        val exemptRestriction = TurnRestriction(
            id = "restr_exempt",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.EXEMPT_FOR_PROFILE,
            isExemptForProfile = true,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val indexExempt = TurnRestrictionIndex.build(createGraph(listOf(exemptRestriction)))
        assertTrue(indexExempt.exemptRestrictionIds.contains("restr_exempt"))
        assertEquals(TransitionDecision.ALLOW, indexExempt.checkTransition("e_AV", "e_VB", "node_V").decision)

        // Trường hợp B: Không được miễn trừ (isExemptForProfile = false) -> Phải áp dụng cấm rẽ
        val nonExemptRestriction = TurnRestriction(
            id = "restr_non_exempt",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            isExemptForProfile = false,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val indexNonExempt = TurnRestrictionIndex.build(createGraph(listOf(nonExemptRestriction)))
        assertEquals(TransitionDecision.DENY_PROHIBITED, indexNonExempt.checkTransition("e_AV", "e_VB", "node_V").decision)
    }

    // 16. NO và ONLY xung đột
    @Test
    fun `test 16 - testConflictBetweenNoAndOnly_detectedAndMarksStrictIncompatible`() {
        val onlyStraight = TurnRestriction(
            id = "restr_only",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V")
            )
        )
        val noStraight = TurnRestriction(
            id = "restr_no",
            type = TurnRestrictionType.NO_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(onlyStraight, noStraight)))

        assertTrue("Index must detect conflict", index.hasConflicts)
        assertEquals(1, index.conflicts.size)
        assertFalse("Strict routing must not be compatible when conflicts exist", index.isStrictRoutingCompatible)

        val checkResult = index.checkTransition("e_AV", "e_VC", "node_V")
        assertEquals(TransitionDecision.UNRESOLVED, checkResult.decision)
    }

    // 17. Transition lookup không sửa đổi graph gốc
    @Test
    fun `test 17 - testTransitionLookup_doesNotMutateGraph`() {
        val noLeft = TurnRestriction(
            id = "restr_no_left",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val graph = createGraph(listOf(noLeft))
        val index = TurnRestrictionIndex.build(graph)

        val edgesBefore = graph.directedEdges.toList()
        val nodesBefore = graph.nodes.toMap()

        index.checkTransition("e_AV", "e_VB", "node_V")
        index.checkTransition("e_AV", "e_VC", "node_V")

        assertEquals(edgesBefore, graph.directedEdges)
        assertEquals(nodesBefore, graph.nodes)
        assertEquals(1, graph.turnRestrictions.size)
    }

    // 18. Empty hoặc malformed transition được xử lý fail-closed
    @Test
    fun `test 18 - testEmptyOrMalformedTransition_handledFailClosed`() {
        // FULLY_SUPPORTED nhưng không có bất kỳ prohibitedTransitions hay allowedTransitions nào
        val malformed = TurnRestriction(
            id = "restr_malformed",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = emptyList(),
            allowedTransitions = emptyList()
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(malformed)))

        assertTrue(index.unresolvedRestrictionIds.contains("restr_malformed"))
        assertTrue(index.hasUnresolvedRestrictions)
        assertFalse(index.isStrictRoutingCompatible)
    }

    // 19. Start node không có incoming edge: trả về ALLOW
    @Test
    fun `test 19 - testStartNodeWithoutIncomingEdge_returnsAllowed`() {
        val noLeft = TurnRestriction(
            id = "restr_no_left",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(noLeft)))

        val startResult = index.checkTransition(
            incomingEdgeId = null,
            outgoingEdgeId = "e_VB",
            viaNodeId = "node_V"
        )
        assertTrue(startResult.isAllowed)
        assertEquals(TransitionDecision.ALLOW, startResult.decision)
    }

    // 20. Kiểm tra helper methods
    @Test
    fun `test 20 - testHelperMethods_getProhibitedAndAllowedSets`() {
        val onlyStraight = TurnRestriction(
            id = "restr_only",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V")
            ),
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(onlyStraight)))

        assertTrue(index.hasRestrictionsAtNode("node_V"))
        assertFalse(index.hasRestrictionsAtNode("node_A"))

        val allowedSet = index.getAllowedTransitionsForIncoming("e_AV", "node_V")
        assertNotNull(allowedSet)
        assertEquals(setOf("e_VC"), allowedSet)

        val prohibitedSet = index.getProhibitedTransitionsForIncoming("e_AV", "node_V")
        assertEquals(setOf("e_VB"), prohibitedSet)

        assertNull(index.getAllowedTransitionsForIncoming("e_AV", "node_A"))
    }

    // 21. Hai ONLY restrictions độc lập cùng áp dụng cho một incoming edge phải được kết hợp bằng INTERSECTION
    @Test
    fun `test 21 - two independent ONLY restrictions combined by INTERSECTION`() {
        // ONLY A: cho phép {e_VC (thẳng), e_VD (phải)}
        val onlyA = TurnRestriction(
            id = "restr_only_A",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        // ONLY B: cho phép {e_VC (thẳng), e_VB (trái)}
        val onlyB = TurnRestriction(
            id = "restr_only_B",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(onlyA, onlyB)))

        // Giao của {e_VC, e_VD} và {e_VC, e_VB} là {e_VC}
        val allowedSet = index.getAllowedTransitionsForIncoming("e_AV", "node_V")
        assertNotNull(allowedSet)
        assertEquals(setOf("e_VC"), allowedSet)

        // e_VC nằm trong phần giao -> ALLOW
        assertEquals(TransitionDecision.ALLOW, index.checkTransition("e_AV", "e_VC", "node_V").decision)

        // e_VD và e_VB không nằm trong phần giao -> DENY_NOT_IN_ALLOWED_SET
        assertEquals(TransitionDecision.DENY_NOT_IN_ALLOWED_SET, index.checkTransition("e_AV", "e_VD", "node_V").decision)
        assertEquals(TransitionDecision.DENY_NOT_IN_ALLOWED_SET, index.checkTransition("e_AV", "e_VB", "node_V").decision)
    }

    // 22. Hai ONLY restrictions có intersection rỗng -> ghi nhận conflict và fail-closed
    @Test
    fun `test 22 - two ONLY restrictions with empty intersection creates conflict and fails closed`() {
        val onlyLeft = TurnRestriction(
            id = "restr_only_left",
            type = TurnRestrictionType.ONLY_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val onlyRight = TurnRestriction(
            id = "restr_only_right",
            type = TurnRestrictionType.ONLY_RIGHT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(onlyLeft, onlyRight)))

        assertTrue("Intersection rỗng phải tạo ra conflict", index.hasConflicts)
        assertFalse("Strict routing phải bị khóa khi có conflict rỗng", index.isRestrictionIndexCompatible)
        assertTrue(index.conflicts.any { it.reason.contains("empty intersection") })

        // Mọi ngã rẽ đều bị từ chối chuyển tiếp do conflict không thể dung hòa
        assertEquals(TransitionDecision.UNRESOLVED, index.checkTransition("e_AV", "e_VB", "node_V").decision)
        assertEquals(TransitionDecision.UNRESOLVED, index.checkTransition("e_AV", "e_VD", "node_V").decision)
    }

    // 23. ONLY restriction kết hợp với NO restriction
    @Test
    fun `test 23 - ONLY restriction combined with NO restriction on same incoming edge`() {
        // ONLY cho phép {e_VC (thẳng), e_VD (phải)}
        val onlyStraightOrRight = TurnRestriction(
            id = "restr_only_straight_or_right",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        // NO cấm rẽ phải {e_VD}
        val noRight = TurnRestriction(
            id = "restr_no_right",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(onlyStraightOrRight, noRight)))

        // Sau khi trừ NO, tập cho phép hợp lệ duy nhất là {e_VC}
        val allowedSet = index.getAllowedTransitionsForIncoming("e_AV", "node_V")
        assertNotNull(allowedSet)
        assertEquals(setOf("e_VC"), allowedSet)

        assertEquals(TransitionDecision.ALLOW, index.checkTransition("e_AV", "e_VC", "node_V").decision)
        // e_VD bị cấm bởi NO
        assertEquals(TransitionDecision.DENY_PROHIBITED, index.checkTransition("e_AV", "e_VD", "node_V").decision)
        // e_VB không nằm trong ONLY ban đầu
        assertEquals(TransitionDecision.DENY_NOT_IN_ALLOWED_SET, index.checkTransition("e_AV", "e_VB", "node_V").decision)
    }

    // 24. Nhiều ONLY và NO cùng tác động một incoming edge
    @Test
    fun `test 24 - multiple ONLY and NO on same incoming edge`() {
        val only1 = TurnRestriction(
            id = "restr_only_1",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val only2 = TurnRestriction(
            id = "restr_only_2",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val noRight = TurnRestriction(
            id = "restr_no_right",
            type = TurnRestrictionType.NO_RIGHT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(only1, only2, noRight)))

        // Intersection (only1, only2) = {e_VC, e_VD}
        // Trừ noRight ({e_VD}) = {e_VC}
        val allowedSet = index.getAllowedTransitionsForIncoming("e_AV", "node_V")
        assertEquals(setOf("e_VC"), allowedSet)

        assertEquals(TransitionDecision.ALLOW, index.checkTransition("e_AV", "e_VC", "node_V").decision)
        assertEquals(TransitionDecision.DENY_PROHIBITED, index.checkTransition("e_AV", "e_VD", "node_V").decision)
        assertEquals(TransitionDecision.DENY_NOT_IN_ALLOWED_SET, index.checkTransition("e_AV", "e_VB", "node_V").decision)
    }

    // 25. Thứ tự nhập restriction không làm thay đổi kết quả (Giao hoán của Intersection)
    @Test
    fun `test 25 - order of ONLY restrictions does not alter intersection result`() {
        val onlyA = TurnRestriction(
            id = "restr_A",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VD", viaNodeId = "node_V")
            )
        )
        val onlyB = TurnRestriction(
            id = "restr_B",
            type = TurnRestrictionType.ONLY_STRAIGHT_ON,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VC", viaNodeId = "node_V"),
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )

        // Thứ tự 1: [A, B]
        val index1 = TurnRestrictionIndex.build(createGraph(listOf(onlyA, onlyB)))
        // Thứ tự 2: [B, A]
        val index2 = TurnRestrictionIndex.build(createGraph(listOf(onlyB, onlyA)))

        assertEquals(
            index1.getAllowedTransitionsForIncoming("e_AV", "node_V"),
            index2.getAllowedTransitionsForIncoming("e_AV", "node_V")
        )
        assertEquals(setOf("e_VC"), index1.getAllowedTransitionsForIncoming("e_AV", "node_V"))

        assertEquals(
            index1.checkTransition("e_AV", "e_VC", "node_V").decision,
            index2.checkTransition("e_AV", "e_VC", "node_V").decision
        )
        assertEquals(
            index1.checkTransition("e_AV", "e_VD", "node_V").decision,
            index2.checkTransition("e_AV", "e_VD", "node_V").decision
        )
    }

    // 26. Hai trường exemption mâu thuẫn -> Xử lý conservative và báo conflict
    @Test
    fun `test 26 - contradictory exemption indicators creates conflict and fails closed`() {
        // isExemptForProfile = true nhưng supportStatus lại là FAIL_CLOSED_UNRESOLVED
        val contradictoryRestriction = TurnRestriction(
            id = "restr_contradictory_exempt",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            isExemptForProfile = true,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(contradictoryRestriction)))

        assertTrue("Phải phát hiện conflict khi trường exemption mâu thuẫn", index.hasConflicts)
        assertFalse("Strict routing không được phép khi có mâu thuẫn exemption", index.isRestrictionIndexCompatible)
        assertFalse("Không được miễn trừ khi có mâu thuẫn", index.exemptRestrictionIds.contains("restr_contradictory_exempt"))

        // Chuyển tiếp phải bị coi là UNRESOLVED
        assertEquals(TransitionDecision.UNRESOLVED, index.checkTransition("e_AV", "e_VB", "node_V").decision)
    }

    // 27. Phân biệt readiness giữa Restriction Index và toàn bộ Graph
    @Test
    fun `test 27 - index compatibility is distinct from overall graph strict routing readiness`() {
        // Một đồ thị không có turn restriction -> Index hoàn toàn compatible
        val graph = RoadGraph(
            nodes = listOf(nodeA, nodeV, nodeB, nodeC, nodeD).associateBy { it.id },
            directedEdges = listOf(edgeAV, edgeVB, edgeVC, edgeVD),
            graphVersion = "test_v1",
            canAttemptStrictRouting = false,
            turnRestrictions = emptyList()
        )
        val index = TurnRestrictionIndex.build(graph)

        // Phân hệ restriction index sẵn sàng 100%
        assertTrue(index.isRestrictionIndexCompatible)
        assertTrue(index.isStrictRoutingCompatible)

        // Nhưng graph có thể chưa sẵn sàng strict routing (do cạnh unverified hoặc hazard)
        assertFalse(graph.canAttemptStrictRouting)
    }

    // 28. Thứ tự ưu tiên: Unresolved restriction không bị che khuất bởi allowed set của ONLY
    @Test
    fun `test 28 - decision priority guarantees unresolved restriction is not masked by ONLY`() {
        // ONLY cho phép e_VB
        val onlyLeft = TurnRestriction(
            id = "restr_only",
            type = TurnRestrictionType.ONLY_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
            allowedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        // Nhưng đồng thời có một restriction khác chưa giải quyết trên chính chuyển tiếp e_AV -> e_VB
        val unresolved = TurnRestriction(
            id = "restr_unresolved",
            type = TurnRestrictionType.NO_LEFT_TURN,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED,
            prohibitedTransitions = listOf(
                EdgeTransition(fromEdgeId = "e_AV", toEdgeId = "e_VB", viaNodeId = "node_V")
            )
        )
        val index = TurnRestrictionIndex.build(createGraph(listOf(onlyLeft, unresolved)))

        // Chuyển tiếp e_AV -> e_VB phải trả về UNRESOLVED (fail-closed), tuyệt đối không được trả về ALLOW
        val result = index.checkTransition("e_AV", "e_VB", "node_V")
        assertEquals(TransitionDecision.UNRESOLVED, result.decision)
    }

    @Test
    fun `test 29 - delegated via-way restriction is tracked in delegatedViaWayRestrictionIds and does not cause unresolved status`() {
        val compiledViaWay = TurnRestriction(
            id = "via_way_compiled",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromOsmWayIds = listOf(101L),
            viaOsmWayIds = listOf(102L),
            toOsmWayIds = listOf(103L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e_from", "e_via", "e_to"),
            supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED
        )
        val graph = createGraph(listOf(compiledViaWay))
        val index = TurnRestrictionIndex.build(graph, delegatedViaWayRestrictionIds = setOf("via_way_compiled"))

        assertTrue("via_way_compiled must be in delegatedViaWayRestrictionIds", index.delegatedViaWayRestrictionIds.contains("via_way_compiled"))
        assertTrue("undelegatedViaWayRestrictionIds must be empty", index.undelegatedViaWayRestrictionIds.isEmpty())
        assertTrue("unresolvedRestrictionIds must not contain delegated via-way", index.unresolvedRestrictionIds.isEmpty())
        assertFalse("hasUnresolvedRestrictions must be false when via-way is delegated", index.hasUnresolvedRestrictions)
        assertTrue("isRestrictionIndexCompatible must be true when via-way is delegated", index.isRestrictionIndexCompatible)
        assertEquals(1, index.supportedRestrictionsCount)
    }

    @Test
    fun `test 30 - undelegated via-way restriction remains in undelegatedViaWayRestrictionIds and unresolvedRestrictionIds`() {
        val uncompiledViaWay = TurnRestriction(
            id = "via_way_unresolved",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromOsmWayIds = listOf(101L),
            viaOsmWayIds = listOf(102L),
            toOsmWayIds = listOf(103L),
            viaWayCompilationStatus = ViaWayCompilationStatus.UNRESOLVED,
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
        )
        val graph = createGraph(listOf(uncompiledViaWay))
        val index = TurnRestrictionIndex.build(graph, delegatedViaWayRestrictionIds = emptySet())

        assertFalse("via_way_unresolved must not be in delegatedViaWayRestrictionIds", index.delegatedViaWayRestrictionIds.contains("via_way_unresolved"))
        assertTrue("via_way_unresolved must be in undelegatedViaWayRestrictionIds", index.undelegatedViaWayRestrictionIds.contains("via_way_unresolved"))
        assertTrue("via_way_unresolved must be in unresolvedRestrictionIds", index.unresolvedRestrictionIds.contains("via_way_unresolved"))
        assertTrue("hasUnresolvedRestrictions must be true for undelegated via-way", index.hasUnresolvedRestrictions)
        assertFalse("isRestrictionIndexCompatible must be false for undelegated via-way", index.isRestrictionIndexCompatible)
    }

    @Test
    fun `test 31 - mixed via-node and via-way relation is never delegated and remains unresolved`() {
        val mixedRestriction = TurnRestriction(
            id = "rel_mixed",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromOsmWayIds = listOf(101L),
            viaOsmNodeIds = listOf(5001L),
            viaOsmWayIds = listOf(102L),
            toOsmWayIds = listOf(103L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e_from", "e_via", "e_to"),
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
        )
        val graph = createGraph(listOf(mixedRestriction))
        val index = TurnRestrictionIndex.build(graph, delegatedViaWayRestrictionIds = setOf("rel_mixed"))

        assertFalse("Mixed via relation must NOT be in delegatedViaWayRestrictionIds", index.delegatedViaWayRestrictionIds.contains("rel_mixed"))
        assertTrue("Mixed via relation must be in undelegatedViaWayRestrictionIds", index.undelegatedViaWayRestrictionIds.contains("rel_mixed"))
        assertTrue("Mixed via relation must be in unresolvedRestrictionIds", index.unresolvedRestrictionIds.contains("rel_mixed"))
        assertTrue("hasUnresolvedRestrictions must be true for mixed relation", index.hasUnresolvedRestrictions)
        assertFalse("isRestrictionIndexCompatible must be false for mixed relation", index.isRestrictionIndexCompatible)
    }
}
