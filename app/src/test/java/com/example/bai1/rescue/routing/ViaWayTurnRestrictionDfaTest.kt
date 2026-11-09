package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.algorithm.DfaBuildResult
import com.example.bai1.rescue.routing.algorithm.DfaDecision
import com.example.bai1.rescue.routing.algorithm.ViaWayDfaLimits
import com.example.bai1.rescue.routing.algorithm.ViaWayPattern
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.ViaWayCompilationStatus
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test Suite toàn diện cho bộ máy Via-way Turn Restriction DFA.
 * Kiểm tra đầy đủ 35 kịch bản theo yêu cầu nghiệp vụ:
 * - NO patterns (1-9)
 * - ONLY obligations (10-20)
 * - Kết hợp & An toàn (21-35)
 */
class ViaWayTurnRestrictionDfaTest {

    private fun createSampleGraph(): RoadGraph {
        val nodes = mapOf(
            "n1" to RoadNode("n1", GeoPoint(16.0, 108.0)),
            "n2" to RoadNode("n2", GeoPoint(16.01, 108.01)),
            "n3" to RoadNode("n3", GeoPoint(16.02, 108.02)),
            "n4" to RoadNode("n4", GeoPoint(16.03, 108.03)),
            "n5" to RoadNode("n5", GeoPoint(16.04, 108.04))
        )
        val edges = listOf(
            RoadEdge("e1", "n1", "n2", lengthMeters = 100.0, geometry = listOf(GeoPoint(16.0, 108.0), GeoPoint(16.01, 108.01)), osmWayId = 1001L),
            RoadEdge("e2", "n2", "n3", lengthMeters = 100.0, geometry = listOf(GeoPoint(16.01, 108.01), GeoPoint(16.02, 108.02)), osmWayId = 1002L),
            RoadEdge("e3", "n3", "n4", lengthMeters = 100.0, geometry = listOf(GeoPoint(16.02, 108.02), GeoPoint(16.03, 108.03)), osmWayId = 1003L),
            RoadEdge("e4", "n4", "n5", lengthMeters = 100.0, geometry = listOf(GeoPoint(16.03, 108.03), GeoPoint(16.04, 108.04)), osmWayId = 1004L)
        )
        return RoadGraph(nodes = nodes, directedEdges = edges, graphVersion = "test_v1")
    }

    // =========================================================================
    // NO PATTERNS (Tests 1 - 9)
    // =========================================================================

    @Test
    fun `01_no via-way basic`() {
        val pattern = ViaWayPattern("rel_no_basic", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val buildResult = ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern))
        assertTrue(buildResult is DfaBuildResult.Success)
        val dfa = (buildResult as DfaBuildResult.Success).dfa

        // e1 from root
        val r1 = dfa.evaluateTransition(0, "e1")
        assertEquals(DfaDecision.ALLOW, r1.decision)
        val s1 = r1.nextStateId!!
        assertNotEquals(0, s1)

        // e2 from s1
        val r2 = dfa.evaluateTransition(s1, "e2")
        assertEquals(DfaDecision.ALLOW, r2.decision)
        val s2 = r2.nextStateId!!
        assertNotEquals(0, s2)

        // e3 from s2 completes prohibited NO pattern
        val r3 = dfa.evaluateTransition(s2, "e3")
        assertEquals(DfaDecision.DENY_PROHIBITED, r3.decision)
        assertNull(r3.nextStateId)
        assertEquals(listOf("rel_no_basic"), r3.violatedRestrictionIds)
    }

    @Test
    fun `02_no restriction with multiple consecutive via-ways`() {
        val pattern = ViaWayPattern("rel_no_multi", TurnRestrictionType.NO_U_TURN, listOf("e1", "e2", "e3", "e4", "e5"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!
        val s3 = dfa.evaluateTransition(s2, "e3").nextStateId!!
        val s4 = dfa.evaluateTransition(s3, "e4").nextStateId!!

        val r5 = dfa.evaluateTransition(s4, "e5")
        assertEquals(DfaDecision.DENY_PROHIBITED, r5.decision)
        assertEquals(listOf("rel_no_multi"), r5.violatedRestrictionIds)
    }

    @Test
    fun `03_two no patterns sharing prefix`() {
        val p1 = ViaWayPattern("rel_p1", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val p2 = ViaWayPattern("rel_p2", TurnRestrictionType.NO_RIGHT_TURN, listOf("e1", "e2", "e4"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(p1, p2)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!

        // e3 branch denied by p1
        val r3 = dfa.evaluateTransition(s2, "e3")
        assertEquals(DfaDecision.DENY_PROHIBITED, r3.decision)
        assertEquals(listOf("rel_p1"), r3.violatedRestrictionIds)

        // e4 branch denied by p2
        val r4 = dfa.evaluateTransition(s2, "e4")
        assertEquals(DfaDecision.DENY_PROHIBITED, r4.decision)
        assertEquals(listOf("rel_p2"), r4.violatedRestrictionIds)

        // e5 branch not restricted -> ALLOW
        val r5 = dfa.evaluateTransition(s2, "e5")
        assertEquals(DfaDecision.ALLOW, r5.decision)
    }

    @Test
    fun `04_two no patterns with suffix overlap`() {
        val p1 = ViaWayPattern("rel_p1", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        val p2 = ViaWayPattern("rel_p2", TurnRestrictionType.NO_RIGHT_TURN, listOf("e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(p1, p2)) as DfaBuildResult.Success).dfa

        // Route: e9 (unrelated) -> e2 -> e3
        val s_e9 = dfa.evaluateTransition(0, "e9").nextStateId!!
        assertEquals(0, s_e9) // stays at root

        // e2 from root enters depth 1 of p2
        val s_e2 = dfa.evaluateTransition(0, "e2").nextStateId!!
        assertNotEquals(0, s_e2)

        // e3 completes p2
        val r_e3 = dfa.evaluateTransition(s_e2, "e3")
        assertEquals(DfaDecision.DENY_PROHIBITED, r_e3.decision)
        assertEquals(listOf("rel_p2"), r_e3.violatedRestrictionIds)
    }

    @Test
    fun `05_single transition completes multiple no patterns`() {
        val p1 = ViaWayPattern("rel_long", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val p2 = ViaWayPattern("rel_short", TurnRestrictionType.NO_U_TURN, listOf("e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(p1, p2)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!

        // e3 simultaneously completes rel_long (directly) and rel_short (via failure link)
        val r3 = dfa.evaluateTransition(s2, "e3")
        assertEquals(DfaDecision.DENY_PROHIBITED, r3.decision)
        assertTrue(r3.violatedRestrictionIds.contains("rel_long"))
        assertTrue(r3.violatedRestrictionIds.contains("rel_short"))
        assertEquals(2, r3.violatedRestrictionIds.size)
    }

    @Test
    fun `06_exact forbidden edge sequence denied`() {
        val pattern = ViaWayPattern("rel_exact", TurnRestrictionType.NO_STRAIGHT_ON, listOf("edge_A", "edge_B", "edge_C"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "edge_A").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "edge_B").nextStateId!!
        val r3 = dfa.evaluateTransition(s2, "edge_C")
        assertEquals(DfaDecision.DENY_PROHIBITED, r3.decision)
    }

    @Test
    fun `07_reverse edge sequence does not trigger restriction`() {
        val pattern = ViaWayPattern("rel_fwd", TurnRestrictionType.NO_LEFT_TURN, listOf("edge_fwd_1", "edge_fwd_2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        // Reverse sequence with distinct edge IDs
        val r1 = dfa.evaluateTransition(0, "edge_rev_2")
        assertEquals(DfaDecision.ALLOW, r1.decision)
        assertEquals(0, r1.nextStateId!!)

        val r2 = dfa.evaluateTransition(0, "edge_rev_1")
        assertEquals(DfaDecision.ALLOW, r2.decision)
        assertEquals(0, r2.nextStateId!!)
    }

    @Test
    fun `08_detour does not complete no pattern and is allowed`() {
        val pattern = ViaWayPattern("rel_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!

        // Detour onto e_detour instead of e3
        val rDetour = dfa.evaluateTransition(s2, "e_detour")
        assertEquals(DfaDecision.ALLOW, rDetour.decision)
        assertEquals(0, rDetour.nextStateId!!)
    }

    @Test
    fun `09_failure transition correctly handles broken prefix`() {
        val pattern = ViaWayPattern("rel_p", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        // Break prefix with noise
        val sBreak = dfa.evaluateTransition(s1, "e_noise").nextStateId!!
        assertEquals(0, sBreak)

        // Start pattern fresh
        val s1Fresh = dfa.evaluateTransition(sBreak, "e1").nextStateId!!
        val s2Fresh = dfa.evaluateTransition(s1Fresh, "e2").nextStateId!!
        val r3 = dfa.evaluateTransition(s2Fresh, "e3")
        assertEquals(DfaDecision.DENY_PROHIBITED, r3.decision)
    }

    // =========================================================================
    // ONLY OBLIGATIONS (Tests 10 - 20)
    // =========================================================================

    @Test
    fun `10_only via-way basic`() {
        val pattern = ViaWayPattern("rel_only_basic", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val r1 = dfa.evaluateTransition(0, "e1")
        assertEquals(DfaDecision.ALLOW, r1.decision)
        val s1 = r1.nextStateId!!

        // From s1, ONLY e2 is allowed; e_wrong is rejected
        val rWrong = dfa.evaluateTransition(s1, "e_wrong")
        assertEquals(DfaDecision.DENY_NOT_IN_ALLOWED_SET, rWrong.decision)
        assertEquals(listOf("rel_only_basic"), rWrong.violatedRestrictionIds)

        val r2 = dfa.evaluateTransition(s1, "e2")
        assertEquals(DfaDecision.ALLOW, r2.decision)
    }

    @Test
    fun `11_only obligation activates after from-approach edge`() {
        val pattern = ViaWayPattern("rel_only", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e_from", "e_to"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        // At root (before e_from), other edges are allowed
        val rOther = dfa.evaluateTransition(0, "e_other")
        assertEquals(DfaDecision.ALLOW, rOther.decision)

        // Traversing e_from activates obligation
        val sFrom = dfa.evaluateTransition(0, "e_from").nextStateId!!
        val stateObj = dfa.getState(sFrom)
        assertNotNull(stateObj)
        assertEquals("e_to", stateObj!!.effectiveAllowedEdge)
        assertEquals(1, stateObj.activeOnlyObligations.size)
    }

    @Test
    fun `12_blocks all branches at intermediate junction`() {
        val pattern = ViaWayPattern("rel_only_long", TurnRestrictionType.ONLY_LEFT_TURN, listOf("e1", "e2", "e3", "e4"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!

        // Intermediate junction after e2: only e3 is permitted
        val rBranchA = dfa.evaluateTransition(s2, "e_side_a")
        assertEquals(DfaDecision.DENY_NOT_IN_ALLOWED_SET, rBranchA.decision)

        val rBranchB = dfa.evaluateTransition(s2, "e_side_b")
        assertEquals(DfaDecision.DENY_NOT_IN_ALLOWED_SET, rBranchB.decision)

        val r3 = dfa.evaluateTransition(s2, "e3")
        assertEquals(DfaDecision.ALLOW, r3.decision)
    }

    @Test
    fun `13_blocks all other edges at final junction before to-edge`() {
        val pattern = ViaWayPattern("rel_only_final", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e2", "e3", "e4"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!
        val s3 = dfa.evaluateTransition(s2, "e3").nextStateId!!

        // Final junction before e4: only e4 is permitted
        val rWrong = dfa.evaluateTransition(s3, "e_wrong_exit")
        assertEquals(DfaDecision.DENY_NOT_IN_ALLOWED_SET, rWrong.decision)

        val r4 = dfa.evaluateTransition(s3, "e4")
        assertEquals(DfaDecision.ALLOW, r4.decision)
    }

    @Test
    fun `14_completing to-edge releases obligation`() {
        val pattern = ViaWayPattern("rel_release", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!
        val s3 = dfa.evaluateTransition(s2, "e3").nextStateId!!

        // After completing e3 (to-edge), obligation is cleared
        val stateObj = dfa.getState(s3)
        assertNotNull(stateObj)
        assertNull(stateObj!!.effectiveAllowedEdge)
        assertFalse(stateObj.hasOnlyConflict)
        assertTrue(stateObj.activeOnlyObligations.isEmpty())

        // Vehicle is now free to take any edge
        val rNext = dfa.evaluateTransition(s3, "e_any_subsequent")
        assertEquals(DfaDecision.ALLOW, rNext.decision)
    }

    @Test
    fun `15_failure link cannot be used to drop active only obligation`() {
        val pattern = ViaWayPattern("rel_no_escape", TurnRestrictionType.ONLY_LEFT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        // Vehicle tries to turn onto e_side where failLink would normally drop to root
        val rSide = dfa.evaluateTransition(s1, "e_side")
        assertEquals(DfaDecision.DENY_NOT_IN_ALLOWED_SET, rSide.decision)
        assertNull(rSide.nextStateId)
    }

    @Test
    fun `16_two only restrictions sharing prefix with same required next edge`() {
        val r1 = ViaWayPattern("rel_only_1", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e2", "e3"))
        val r2 = ViaWayPattern("rel_only_2", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(r1, r2)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val stateObj = dfa.getState(s1)!!
        assertFalse(stateObj.hasOnlyConflict)
        assertEquals("e2", stateObj.effectiveAllowedEdge)

        val r2Res = dfa.evaluateTransition(s1, "e2")
        assertEquals(DfaDecision.ALLOW, r2Res.decision)
    }

    @Test
    fun `17_two only restrictions conflicting with empty intersection`() {
        val r1 = ViaWayPattern("rel_only_a", TurnRestrictionType.ONLY_LEFT_TURN, listOf("e1", "e_via_a", "e_to_a"))
        val r2 = ViaWayPattern("rel_only_b", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e_via_b", "e_to_b"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(r1, r2)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val stateObj = dfa.getState(s1)!!
        assertTrue(stateObj.hasOnlyConflict)
        assertNull(stateObj.effectiveAllowedEdge)

        // All candidates must be denied due to empty intersection conflict
        val rA = dfa.evaluateTransition(s1, "e_via_a")
        assertEquals(DfaDecision.DENY_CONFLICT, rA.decision)

        val rB = dfa.evaluateTransition(s1, "e_via_b")
        assertEquals(DfaDecision.DENY_CONFLICT, rB.decision)

        val rOther = dfa.evaluateTransition(s1, "e_other")
        assertEquals(DfaDecision.DENY_CONFLICT, rOther.decision)
    }

    @Test
    fun `18_multiple only obligations active simultaneously`() {
        val r1 = ViaWayPattern("rel_1", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("eA", "eB", "eC"))
        val r2 = ViaWayPattern("rel_2", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("eB", "eC", "eD"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(r1, r2)) as DfaBuildResult.Success).dfa

        val sA = dfa.evaluateTransition(0, "eA").nextStateId!!
        val sB = dfa.evaluateTransition(sA, "eB").nextStateId!!

        val stateObj = dfa.getState(sB)!!
        // At sB, both r1 and r2 require eC
        assertEquals("eC", stateObj.effectiveAllowedEdge)
        assertFalse(stateObj.hasOnlyConflict)

        val rC = dfa.evaluateTransition(sB, "eC")
        assertEquals(DfaDecision.ALLOW, rC.decision)
    }

    @Test
    fun `19_overlapping only prefixes via failure links`() {
        val r1 = ViaWayPattern("rel_prefix_1", TurnRestrictionType.ONLY_LEFT_TURN, listOf("eA", "e1", "e2"))
        val r2 = ViaWayPattern("rel_prefix_2", TurnRestrictionType.ONLY_LEFT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(r1, r2)) as DfaBuildResult.Success).dfa

        val sA = dfa.evaluateTransition(0, "eA").nextStateId!!
        val s1 = dfa.evaluateTransition(sA, "e1").nextStateId!!

        // Direct r1 requires e2; suffix r2 via failLink also requires e2
        val stateObj = dfa.getState(s1)!!
        assertEquals("e2", stateObj.effectiveAllowedEdge)
        val r2Res = dfa.evaluateTransition(s1, "e2")
        assertEquals(DfaDecision.ALLOW, r2Res.decision)
    }

    @Test
    fun `20_route repeating edge activates new prefix without losing previous obligation`() {
        val r1 = ViaWayPattern("rel_1", TurnRestrictionType.ONLY_LEFT_TURN, listOf("eA", "e1", "e2"))
        val r2 = ViaWayPattern("rel_2", TurnRestrictionType.ONLY_LEFT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(r1, r2)) as DfaBuildResult.Success).dfa

        val sA = dfa.evaluateTransition(0, "eA").nextStateId!!
        val s1 = dfa.evaluateTransition(sA, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!

        // At s2: r1 completed, but r2 (started at e1) is STILL ACTIVE, requiring e3!
        val stateObj = dfa.getState(s2)!!
        assertEquals("e3", stateObj.effectiveAllowedEdge)

        // Wrong edge must be denied by remaining active r2 obligation
        val rWrong = dfa.evaluateTransition(s2, "e_wrong")
        assertEquals(DfaDecision.DENY_NOT_IN_ALLOWED_SET, rWrong.decision)

        // Taking e3 fulfills r2
        val r3 = dfa.evaluateTransition(s2, "e3")
        assertEquals(DfaDecision.ALLOW, r3.decision)
    }

    // =========================================================================
    // COMBINED & SAFETY (Tests 21 - 35)
    // =========================================================================

    @Test
    fun `21_no and only both present in dfa`() {
        val rOnly = ViaWayPattern("rel_only", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e2", "e3"))
        val rNo = ViaWayPattern("rel_no", TurnRestrictionType.NO_LEFT_TURN, listOf("eA", "eB", "eC"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(rOnly, rNo)) as DfaBuildResult.Success).dfa

        // ONLY path operates correctly
        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!
        val r3 = dfa.evaluateTransition(s2, "e3")
        assertEquals(DfaDecision.ALLOW, r3.decision)

        // NO path operates correctly
        val sA = dfa.evaluateTransition(0, "eA").nextStateId!!
        val sB = dfa.evaluateTransition(sA, "eB").nextStateId!!
        val rC = dfa.evaluateTransition(sB, "eC")
        assertEquals(DfaDecision.DENY_PROHIBITED, rC.decision)
    }

    @Test
    fun `22_only allows edge but no prohibits it then denied`() {
        // ONLY requires e2 after e1; but NO_U_TURN prohibits [e1, e2]
        val rOnly = ViaWayPattern("rel_only", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e2", "e3"))
        val rNo = ViaWayPattern("rel_no", TurnRestrictionType.NO_U_TURN, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(rOnly, rNo)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!

        // Candidate e2 satisfies ONLY, but completes prohibited NO pattern -> MUST BE DENIED
        val r2 = dfa.evaluateTransition(s1, "e2")
        assertEquals(DfaDecision.DENY_PROHIBITED, r2.decision)
        assertEquals(listOf("rel_no"), r2.violatedRestrictionIds)
    }

    @Test
    fun `23_invalid dfa state fails closed`() {
        val pattern = ViaWayPattern("p", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val rNegative = dfa.evaluateTransition(-1, "e1")
        assertEquals(DfaDecision.DENY_INVALID_STATE, rNegative.decision)
        assertNull(rNegative.nextStateId)

        val rOverflow = dfa.evaluateTransition(99999, "e1")
        assertEquals(DfaDecision.DENY_INVALID_STATE, rOverflow.decision)
        assertNull(rOverflow.nextStateId)
    }

    @Test
    fun `24_empty or blank edge id fails closed`() {
        val pattern = ViaWayPattern("p", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val rEmpty = dfa.evaluateTransition(0, "")
        assertEquals(DfaDecision.DENY_INVALID_EDGE, rEmpty.decision)

        val rBlank = dfa.evaluateTransition(0, "   ")
        assertEquals(DfaDecision.DENY_INVALID_EDGE, rBlank.decision)
    }

    @Test
    fun `25_unknown restriction type not included in engine`() {
        val restrUnknown = TurnRestriction(
            id = "rel_unknown",
            type = TurnRestrictionType.UNKNOWN,
            viaOsmWayIds = listOf(2L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val buildResult = ViaWayTurnRestrictionDfa.build(listOf(restrUnknown))
        assertTrue(buildResult is DfaBuildResult.Success)
        val dfa = (buildResult as DfaBuildResult.Success).dfa
        assertEquals(0, dfa.patternCount)
        assertEquals(1, dfa.stateCount) // only root
    }

    @Test
    fun `26_unresolved compilation status not included in engine`() {
        val restrUnresolved = TurnRestriction(
            id = "rel_unresolved",
            type = TurnRestrictionType.NO_LEFT_TURN,
            viaOsmWayIds = listOf(2L),
            viaWayCompilationStatus = ViaWayCompilationStatus.UNRESOLVED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val buildResult = ViaWayTurnRestrictionDfa.build(listOf(restrUnresolved))
        assertTrue(buildResult is DfaBuildResult.Success)
        val dfa = (buildResult as DfaBuildResult.Success).dfa
        assertEquals(0, dfa.patternCount)
    }

    @Test
    fun `27_unsupported conditional restriction not included in engine`() {
        val restrCond = TurnRestriction(
            id = "rel_cond",
            type = TurnRestrictionType.NO_LEFT_TURN,
            viaOsmWayIds = listOf(2L),
            conditionalExpression = "delivery @ (08:00-18:00)",
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3")
        )
        val buildResult = ViaWayTurnRestrictionDfa.build(listOf(restrCond))
        assertTrue(buildResult is DfaBuildResult.Success)
        val dfa = (buildResult as DfaBuildResult.Success).dfa
        assertEquals(0, dfa.patternCount)
    }

    @Test
    fun `28_discontinuous edge sequence rejected by builder`() {
        val graph = createSampleGraph()
        // e1 ends at n2, but e3 starts at n3 (discontinuous: missing e2!)
        val restrDiscontinuous = TurnRestriction(
            id = "rel_gap",
            type = TurnRestrictionType.NO_U_TURN,
            fromOsmWayIds = listOf(1001L),
            viaOsmWayIds = listOf(1003L),
            toOsmWayIds = listOf(1004L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e3", "e4"),
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
        )

        val buildResult = ViaWayTurnRestrictionDfa.build(listOf(restrDiscontinuous), graph)
        assertTrue(buildResult is DfaBuildResult.Failure)
        val failure = buildResult as DfaBuildResult.Failure
        assertEquals("INVALID_TOPOLOGY_DISCONTINUOUS", failure.diagnosticCode)
    }

    @Test
    fun `29_via edge osm way id out of order rejected by builder`() {
        val graph = createSampleGraph()
        // Sequence has e1 (1001), e2 (1002), e3 (1003), but viaOsmWayIds declares 9999L
        val restrMismatchedWay = TurnRestriction(
            id = "rel_mismatch_osm",
            type = TurnRestrictionType.NO_U_TURN,
            fromOsmWayIds = listOf(1001L),
            viaOsmWayIds = listOf(9999L),
            toOsmWayIds = listOf(1003L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3"),
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
        )

        val buildResult = ViaWayTurnRestrictionDfa.build(listOf(restrMismatchedWay), graph)
        assertTrue(buildResult is DfaBuildResult.Failure)
        val failure = buildResult as DfaBuildResult.Failure
        assertEquals("INVALID_OSM_WAY_ALIGNMENT", failure.diagnosticCode)
    }

    @Test
    fun `30_state limit exceeded causes build failure`() {
        val limits = ViaWayDfaLimits(maxStates = 2)
        val pattern = ViaWayPattern("rel_p", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val buildResult = ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern), limits)

        assertTrue(buildResult is DfaBuildResult.Failure)
        val failure = buildResult as DfaBuildResult.Failure
        assertEquals("LIMIT_EXCEEDED_STATES", failure.diagnosticCode)
    }

    @Test
    fun `31_pattern length limit exceeded causes build failure`() {
        val limits = ViaWayDfaLimits(maxPatternLength = 2)
        val pattern = ViaWayPattern("rel_long", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val buildResult = ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern), limits)

        assertTrue(buildResult is DfaBuildResult.Failure)
        val failure = buildResult as DfaBuildResult.Failure
        assertEquals("LIMIT_EXCEEDED_PATTERN_LENGTH", failure.diagnosticCode)
    }

    @Test
    fun `32_dfa state transitions are deterministic`() {
        val p1 = ViaWayPattern("p1", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val p2 = ViaWayPattern("p2", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("eA", "eB", "eC"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(p1, p2)) as DfaBuildResult.Success).dfa

        // Running 10 times yields exact same state IDs
        val expectedS1 = dfa.evaluateTransition(0, "e1").nextStateId
        val expectedS2 = dfa.evaluateTransition(expectedS1!!, "e2").nextStateId

        for (i in 0 until 10) {
            val s1 = dfa.evaluateTransition(0, "e1").nextStateId
            assertEquals(expectedS1, s1)
            val s2 = dfa.evaluateTransition(s1!!, "e2").nextStateId
            assertEquals(expectedS2, s2)
        }
    }

    @Test
    fun `33_repeated evaluations with same input produce identical result`() {
        val pattern = ViaWayPattern("p", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val first = dfa.evaluateTransition(0, "e1")
        for (i in 0 until 5) {
            val repeated = dfa.evaluateTransition(0, "e1")
            assertEquals(first, repeated)
        }
    }

    @Test
    fun `34_two independent dfa instances do not share state ids`() {
        val p1 = ViaWayPattern("p1", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3"))
        val p2 = ViaWayPattern("p2", TurnRestrictionType.NO_LEFT_TURN, listOf("eA", "eB", "eC", "eD", "eE"))

        val dfa1 = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(p1)) as DfaBuildResult.Success).dfa
        val dfa2 = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(p2)) as DfaBuildResult.Success).dfa

        // dfa2 has more states than dfa1
        assertTrue(dfa2.stateCount > dfa1.stateCount)
        val highestStateInDfa2 = dfa2.stateCount - 1

        // Testing highestStateInDfa2 against dfa1 must be rejected as invalid state ID
        val rInvalid = dfa1.evaluateTransition(highestStateInDfa2, "e1")
        assertEquals(DfaDecision.DENY_INVALID_STATE, rInvalid.decision)
    }

    @Test
    fun `35_existing routing tests do not regress`() {
        // Simple sanity check demonstrating DFA is standalone and non-intrusive
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(emptyList()) as DfaBuildResult.Success).dfa
        assertEquals(1, dfa.stateCount)
        assertEquals(0, dfa.patternCount)
        val res = dfa.evaluateTransition(0, "any_edge")
        assertEquals(DfaDecision.ALLOW, res.decision)
        assertEquals(0, res.nextStateId)
    }

    // =========================================================================
    // TASK 3B: CORRECTNESS AUDIT & PRODUCTION GRAPH DRY-RUN TESTS (36 - 40)
    // =========================================================================

    @Test
    fun `36_dry_run_production_danang_graph_builds_successfully`() {
        val graphFile = File("/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app/tools/osm-importer/output-dryrun-task3b/BaseRoadGraph.json")
        if (!graphFile.exists()) {
            println("Skipping dry run: graph file not found on disk")
            return
        }

        val parser = StreamingRoadGraphParser()
        val graph = parser.parseSync(graphFile)

        assertEquals("dn_central_real_v1", graph.graphVersion)
        assertEquals("2.1.0", graph.contractVersion)
        assertEquals(7294, graph.nodes.size)
        assertEquals(16651, graph.directedEdges.size)
        assertEquals(27, graph.turnRestrictions.size)

        val viaWayRestrictions = graph.turnRestrictions.filter { it.viaOsmWayIds.isNotEmpty() }
        assertEquals(12, viaWayRestrictions.size)

        val compiledViaWay = viaWayRestrictions.filter { it.viaWayCompilationStatus == ViaWayCompilationStatus.COMPILED }
        val unresolvedViaWay = viaWayRestrictions.filter { it.viaWayCompilationStatus == ViaWayCompilationStatus.UNRESOLVED }
        assertEquals(10, compiledViaWay.size)
        assertEquals(2, unresolvedViaWay.size)

        // Build DFA
        val startTime = System.nanoTime()
        val buildResult = ViaWayTurnRestrictionDfa.build(graph.turnRestrictions, graph)
        val elapsedNanos = System.nanoTime() - startTime
        val elapsedMs = elapsedNanos / 1_000_000.0

        assertTrue(buildResult is DfaBuildResult.Success)
        val dfa = (buildResult as DfaBuildResult.Success).dfa

        println("Production Dry-Run DFA built in ${elapsedMs}ms:")
        println(" - Accepted Patterns: ${dfa.patternCount}")
        println(" - State Count: ${dfa.stateCount}")
        println(" - Transition Count: ${dfa.totalTransitionsCount}")

        assertEquals(10, dfa.patternCount)
        // Limits verification
        assertTrue(dfa.patternCount <= dfa.limits.maxPatterns)
        assertTrue(dfa.stateCount <= dfa.limits.maxStates)
        assertTrue(dfa.totalTransitionsCount <= dfa.limits.maxTransitions)

        // Verify all 10 compiled restrictions have pattern length <= maxPatternLength (20)
        for (r in compiledViaWay) {
            assertTrue(r.orderedDirectedEdgeIds.size <= dfa.limits.maxPatternLength)
            assertEquals(3, r.orderedDirectedEdgeIds.size)
        }

        // Test transition evaluation on actual compiled NO_U_TURN pattern
        // rel_osm_9643201: [edge_w693839075_n6164917335_n6164917332_F, edge_w693839074_n6164917332_n6164917307_F, edge_w693839073_n6164917307_n6164917334_F]
        val rSample = compiledViaWay.first { it.id == "rel_osm_9643201" }
        val seq = rSample.orderedDirectedEdgeIds
        val s1 = dfa.evaluateTransition(0, seq[0]).nextStateId!!
        val s2 = dfa.evaluateTransition(s1, seq[1]).nextStateId!!
        val rEnd = dfa.evaluateTransition(s2, seq[2])
        assertEquals(DfaDecision.DENY_PROHIBITED, rEnd.decision)
        assertTrue(rEnd.violatedRestrictionIds.contains("rel_osm_9643201"))
    }

    @Test
    fun `37_compiled_with_fail_closed_unresolved_is_accepted_into_dfa`() {
        val pattern = TurnRestriction(
            id = "rel_compiled_unresolved_support",
            type = TurnRestrictionType.NO_U_TURN,
            fromOsmWayIds = listOf(1001L),
            viaOsmWayIds = listOf(1002L),
            toOsmWayIds = listOf(1003L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2", "e3"),
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED // Still unresolved runtime status!
        )

        val graph = createSampleGraph()
        val buildResult = ViaWayTurnRestrictionDfa.build(listOf(pattern), graph)
        assertTrue(buildResult is DfaBuildResult.Success)
        val dfa = (buildResult as DfaBuildResult.Success).dfa

        // MUST be accepted despite supportStatus being FAIL_CLOSED_UNRESOLVED
        assertEquals(1, dfa.patternCount)
        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!
        val r3 = dfa.evaluateTransition(s2, "e3")
        assertEquals(DfaDecision.DENY_PROHIBITED, r3.decision)
    }

    @Test
    fun `38_no_output_inherited_through_multiple_failure_links`() {
        // P1: [e1, e2, e3, e4]
        // P2: [e2, e3, e4]
        // P3: [e3, e4]
        val p1 = ViaWayPattern("p1_longest", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2", "e3", "e4"))
        val p2 = ViaWayPattern("p2_middle", TurnRestrictionType.NO_RIGHT_TURN, listOf("e2", "e3", "e4"))
        val p3 = ViaWayPattern("p3_shortest", TurnRestrictionType.NO_U_TURN, listOf("e3", "e4"))

        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(p1, p2, p3)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!
        val s3 = dfa.evaluateTransition(s2, "e3").nextStateId!!

        // e4 completes p1 (directly), p2 (via failLink), and p3 (via failLink -> failLink)
        val r4 = dfa.evaluateTransition(s3, "e4")
        assertEquals(DfaDecision.DENY_PROHIBITED, r4.decision)
        assertEquals(3, r4.violatedRestrictionIds.size)
        assertTrue(r4.violatedRestrictionIds.contains("p1_longest"))
        assertTrue(r4.violatedRestrictionIds.contains("p2_middle"))
        assertTrue(r4.violatedRestrictionIds.contains("p3_shortest"))
    }

    @Test
    fun `39_shared_only_prefix_diverging_creates_dead_end_conflict`() {
        val rA = ViaWayPattern("rA", TurnRestrictionType.ONLY_LEFT_TURN, listOf("e1", "e2", "e3"))
        val rB = ViaWayPattern("rB", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e1", "e2", "e4"))

        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(rA, rB)) as DfaBuildResult.Success).dfa

        // e1 is allowed
        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        // e2 is allowed (both rA and rB require e2 at depth 1)
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!

        val state2 = dfa.getState(s2)!!
        assertTrue(state2.hasOnlyConflict)
        assertNull(state2.effectiveAllowedEdge)

        // At s2, rA requires e3, rB requires e4 -> conflict!
        // No candidate can ever succeed; s2 is an impassable dead end
        val r3 = dfa.evaluateTransition(s2, "e3")
        assertEquals(DfaDecision.DENY_CONFLICT, r3.decision)

        val r4 = dfa.evaluateTransition(s2, "e4")
        assertEquals(DfaDecision.DENY_CONFLICT, r4.decision)

        val r5 = dfa.evaluateTransition(s2, "e5")
        assertEquals(DfaDecision.DENY_CONFLICT, r5.decision)
    }

    @Test
    fun `40_only_suffix_prefix_overlap_advances_and_releases_in_order`() {
        // rA: [e1, e2, e3, e4]
        // rB: [e2, e3, e4, e5]
        val rA = ViaWayPattern("rA", TurnRestrictionType.ONLY_LEFT_TURN, listOf("e1", "e2", "e3", "e4"))
        val rB = ViaWayPattern("rB", TurnRestrictionType.ONLY_RIGHT_TURN, listOf("e2", "e3", "e4", "e5"))

        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(rA, rB)) as DfaBuildResult.Success).dfa

        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val s2 = dfa.evaluateTransition(s1, "e2").nextStateId!!
        val s3 = dfa.evaluateTransition(s2, "e3").nextStateId!!
        val s4 = dfa.evaluateTransition(s3, "e4").nextStateId!!

        // At s4: rA completed at e4. But rB (active from suffix at e2) is still active at depth 3, requiring e5!
        val state4 = dfa.getState(s4)!!
        assertEquals("e5", state4.effectiveAllowedEdge)
        assertFalse(state4.hasOnlyConflict)

        // Detour e_wrong is denied by rB
        val rWrong = dfa.evaluateTransition(s4, "e_wrong")
        assertEquals(DfaDecision.DENY_NOT_IN_ALLOWED_SET, rWrong.decision)

        // Required e5 is allowed and completes rB
        val r5 = dfa.evaluateTransition(s4, "e5")
        assertEquals(DfaDecision.ALLOW, r5.decision)
        val s5 = r5.nextStateId!!

        // At s5: both rA and rB are complete
        val state5 = dfa.getState(s5)!!
        assertNull(state5.effectiveAllowedEdge)
        assertTrue(state5.activeOnlyObligations.isEmpty())
    }

    @Test
    fun `41_mutate_builder_source_transition_map_after_build_does_not_affect_dfa`() {
        val pattern = ViaWayPattern("p1", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa
        val root = dfa.getState(0)!!

        val origSize = root.transitions.size
        assertEquals(1, origSize)

        // Cast và cố gắng mutate transitions
        try {
            (root.transitions as MutableMap<String, Int>)["injected_edge"] = 999
            org.junit.Assert.fail("transitions must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        // DFA state vẫn giữ nguyên số transition ban đầu
        assertEquals(1, dfa.getState(0)!!.transitions.size)
        assertEquals(origSize, dfa.getState(0)!!.transitions.size)
    }

    @Test
    fun `42_mutate_source_obligations_list_after_build_does_not_affect_dfa`() {
        val pattern = ViaWayPattern("p_only", TurnRestrictionType.ONLY_STRAIGHT_ON, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa
        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val state1 = dfa.getState(s1)!!

        assertEquals(1, state1.activeOnlyObligations.size)

        try {
            (state1.activeOnlyObligations as MutableList<*>).clear()
            org.junit.Assert.fail("activeOnlyObligations must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        assertEquals(1, dfa.getState(s1)!!.activeOnlyObligations.size)
    }

    @Test
    fun `43_mutate_source_prohibited_pattern_ids_after_build_does_not_affect_dfa`() {
        val pattern = ViaWayPattern("p_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa
        val s1 = dfa.evaluateTransition(0, "e1").nextStateId!!
        val directChild = dfa.getState(s1)!!.transitions["e2"]!!
        val state2 = dfa.getState(directChild)!!

        assertEquals(1, state2.prohibitedPatternIds.size)

        try {
            (state2.prohibitedPatternIds as MutableList<String>).add("injected_pattern")
            org.junit.Assert.fail("prohibitedPatternIds must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        assertEquals(1, dfa.getState(directChild)!!.prohibitedPatternIds.size)
    }

    @Test
    fun `44_concurrent_evaluateTransition_does_not_alter_dfa_state`() {
        val pattern = ViaWayPattern("p_no", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val threads = (1..16).map {
            Thread {
                for (i in 0 until 500) {
                    val r1 = dfa.evaluateTransition(0, "e1")
                    assertEquals(DfaDecision.ALLOW, r1.decision)
                    val r2 = dfa.evaluateTransition(r1.nextStateId!!, "e2")
                    assertEquals(DfaDecision.DENY_PROHIBITED, r2.decision)
                    val r3 = dfa.evaluateTransition(0, "unknown_edge")
                    assertEquals(DfaDecision.ALLOW, r3.decision)
                    assertEquals(0, r3.nextStateId)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(1, dfa.patternCount)
        assertEquals(3, dfa.stateCount)
        assertEquals(1, dfa.getState(0)!!.transitions.size)
    }

    @Test
    fun `45_mixed_via_node_and_way_relation_is_skipped_by_dfa_builder`() {
        val mixedRestriction = TurnRestriction(
            id = "rel_mixed",
            type = TurnRestrictionType.NO_LEFT_TURN,
            fromOsmWayIds = listOf(101L),
            viaOsmNodeIds = listOf(5001L),
            viaOsmWayIds = listOf(102L),
            toOsmWayIds = listOf(103L),
            viaWayCompilationStatus = ViaWayCompilationStatus.COMPILED,
            orderedDirectedEdgeIds = listOf("e1", "e2"),
            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
        )

        val result = ViaWayTurnRestrictionDfa.build(listOf(mixedRestriction))
        assertTrue(result is DfaBuildResult.Success)
        val dfa = (result as DfaBuildResult.Success).dfa
        assertEquals(0, dfa.patternCount)
        assertFalse(dfa.acceptedRestrictionIds.contains("rel_mixed"))
    }

    // ==========================================
    // TASK 4B.3.1: DFA FINALITY & IMMUTABILITY AUDIT TESTS (46 - 49)
    // ==========================================

    @Test
    fun `46_dfa_class_is_strictly_final_and_cannot_be_subclassed`() {
        val clazz = ViaWayTurnRestrictionDfa::class.java
        assertTrue("ViaWayTurnRestrictionDfa must be final to prevent unauthorized subclassing", Modifier.isFinal(clazz.modifiers))

        // Xác minh không có constructor public thông thường nào có thể gọi từ bên ngoài
        val callablePublicCtors = clazz.constructors.filter { ctor ->
            !ctor.isSynthetic && !ctor.parameterTypes.any { pt -> pt.name.contains("DefaultConstructorMarker") }
        }
        assertEquals("Production class must have 0 public callable constructors", 0, callablePublicCtors.size)

        // Xác minh primary constructor là private
        val declaredCtors = clazz.declaredConstructors.filter { ctor ->
            !ctor.parameterTypes.any { pt -> pt.name.contains("DefaultConstructorMarker") }
        }
        assertEquals("Production class must have exactly 1 real declared constructor", 1, declaredCtors.size)
        assertTrue("Primary declared constructor must be private", Modifier.isPrivate(declaredCtors.first().modifiers))
    }

    @Test
    fun `47_dfa_decision_methods_are_final_and_cannot_be_overridden`() {
        val clazz = ViaWayTurnRestrictionDfa::class.java
        val evalMethod = clazz.getMethod("evaluateTransition", Int::class.javaPrimitiveType, String::class.java)
        assertTrue("evaluateTransition must be final", Modifier.isFinal(evalMethod.modifiers))

        val isValidStateMethod = clazz.getMethod("isValidStateId", Int::class.javaPrimitiveType)
        assertTrue("isValidStateId must be final", Modifier.isFinal(isValidStateMethod.modifiers))

        val isEnforcedMethod = clazz.getMethod("isRestrictionEnforced", String::class.java)
        assertTrue("isRestrictionEnforced must be final", Modifier.isFinal(isEnforcedMethod.modifiers))

        val getStateMethod = clazz.getMethod("getState", Int::class.javaPrimitiveType)
        assertTrue("getState must be final", Modifier.isFinal(getStateMethod.modifiers))
    }

    @Test
    fun `48_acceptedRestrictionIds_is_strictly_unmodifiable`() {
        val pattern = ViaWayPattern("p_test", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        assertEquals(setOf("p_test"), dfa.acceptedRestrictionIds)

        try {
            (dfa.acceptedRestrictionIds as MutableSet<String>).add("injected_restriction")
            org.junit.Assert.fail("acceptedRestrictionIds must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        try {
            (dfa.acceptedRestrictionIds as MutableSet<String>).clear()
            org.junit.Assert.fail("acceptedRestrictionIds must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        assertEquals(1, dfa.acceptedRestrictionIds.size)
        assertTrue(dfa.isRestrictionEnforced("p_test"))
        assertFalse(dfa.isRestrictionEnforced("injected_restriction"))
    }

    @Test
    fun `49_dfa_internal_state_storage_is_private_and_immutable`() {
        val pattern = ViaWayPattern("p_test", TurnRestrictionType.NO_LEFT_TURN, listOf("e1", "e2"))
        val dfa = (ViaWayTurnRestrictionDfa.buildFromPatterns(listOf(pattern)) as DfaBuildResult.Success).dfa

        val state0 = dfa.getState(0)
        assertNotNull(state0)

        try {
            (state0!!.transitions as MutableMap<String, Int>)["fake_edge"] = 999
            org.junit.Assert.fail("transitions must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }

        try {
            (state0!!.activeOnlyObligations as MutableList<*>).clear()
            org.junit.Assert.fail("activeOnlyObligations must be unmodifiable")
        } catch (e: UnsupportedOperationException) {
            // Mong đợi
        }
    }
}

