package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.algorithm.*
import com.example.bai1.rescue.routing.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import java.lang.reflect.InvocationTargetException

/**
 * Unit inspection of the existing PRIVATE mathematical kernel, compiled only in src/test.
 * This is not a consumer, verifier, permission provider or alternative production entry point.
 * Before inspecting computation, the identical request MUST be refused by the real strict API.
 * Never use computational SUCCESS as safety evidence. P5A01ConsumerAuditTest never uses this helper.
 * Reflection avoids adding a TEST_ONLY unlock switch or callable search seam to production code.
 */
internal fun UniformCostSearchRouter.inspectFixtureComputation(
    roadGraph: RoadGraph?, startNodeId: String, goalNodeId: String,
    turnRestrictionIndex: TurnRestrictionIndex? = null,
    liveFloodAssessments: Map<String, LiveFloodAssessment> = emptyMap(),
    floodAssessmentContext: FloodAssessmentContext? = null,
    viaWayDfa: ViaWayTurnRestrictionDfa? = null
): UcsRouteResult {
    val denied = findRoute(roadGraph, startNodeId, goalNodeId, turnRestrictionIndex,
        liveFloodAssessments, floodAssessmentContext, viaWayDfa)
    assertEquals("Strict API must deny before computational inspection", UcsRouteStatus.STRICT_ROUTING_DISABLED, denied.status)
    val graph = requireNotNull(roadGraph)
    val dfa = viaWayDfa ?: ViaWayTurnRestrictionDfa.empty()
    return inspectPrivateKernel(graph, startNodeId, goalNodeId,
        turnRestrictionIndex ?: TurnRestrictionIndex.build(graph, dfa.acceptedRestrictionIds),
        dfa, liveFloodAssessments, floodAssessmentContext)
}

internal fun UniformCostSearchRouter.inspectFixtureComputation(
    context: RescueRoutingContext, startNodeId: String, goalNodeId: String,
    liveFloodAssessments: Map<String, LiveFloodAssessment> = emptyMap(),
    floodAssessmentContext: FloodAssessmentContext? = null
): UcsRouteResult {
    assertFalse("Context is never a production credential", context.isStrictRoutingPermitted)
    assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED,
        findRoute(context, startNodeId, goalNodeId, liveFloodAssessments, floodAssessmentContext).status)
    return inspectPrivateKernel(context.roadGraph, startNodeId, goalNodeId, context.turnRestrictionIndex,
        context.viaWayDfa, liveFloodAssessments, floodAssessmentContext)
}

internal fun UniformCostSearchRouter.inspectFixtureComputation(
    roadGraph: RoadGraph?, startNodeId: String, goalNodeId: String,
    turnRestrictionIndex: TurnRestrictionIndex? = null,
    liveFloodAssessments: Map<String, LiveFloodAssessment> = emptyMap(), currentEpochMillis: Long,
    requiredHazardSnapshotVersion: String? = null, liveHazardReady: Boolean = false,
    trustValidator: FloodAssessmentTrustValidator = FailClosedFloodAssessmentTrustValidator,
    freshnessPolicy: FloodFreshnessPolicy? = null, viaWayDfa: ViaWayTurnRestrictionDfa? = null
): UcsRouteResult {
    assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, findRoute(roadGraph, startNodeId, goalNodeId,
        turnRestrictionIndex, liveFloodAssessments, currentEpochMillis, requiredHazardSnapshotVersion,
        liveHazardReady, trustValidator, freshnessPolicy, viaWayDfa).status)
    return inspectFixtureComputation(roadGraph, startNodeId, goalNodeId, turnRestrictionIndex, liveFloodAssessments,
        FloodAssessmentContext(liveHazardReady, requiredHazardSnapshotVersion, freshnessPolicy, trustValidator, currentEpochMillis), viaWayDfa)
}

internal fun UniformCostSearchRouter.inspectFixtureComputation(
    context: RescueRoutingContext, startNodeId: String, goalNodeId: String,
    liveFloodAssessments: Map<String, LiveFloodAssessment> = emptyMap(), currentEpochMillis: Long,
    requiredHazardSnapshotVersion: String? = null, liveHazardReady: Boolean = false,
    trustValidator: FloodAssessmentTrustValidator = FailClosedFloodAssessmentTrustValidator,
    freshnessPolicy: FloodFreshnessPolicy? = null
): UcsRouteResult {
    assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, findRoute(context, startNodeId, goalNodeId,
        liveFloodAssessments, currentEpochMillis, requiredHazardSnapshotVersion, liveHazardReady, trustValidator, freshnessPolicy).status)
    return inspectFixtureComputation(context, startNodeId, goalNodeId, liveFloodAssessments,
        FloodAssessmentContext(liveHazardReady, requiredHazardSnapshotVersion, freshnessPolicy, trustValidator, currentEpochMillis))
}

private fun UniformCostSearchRouter.inspectPrivateKernel(
    graph: RoadGraph, start: String, goal: String, index: TurnRestrictionIndex,
    dfa: ViaWayTurnRestrictionDfa, assessments: Map<String, LiveFloodAssessment>, hazard: FloodAssessmentContext?
): UcsRouteResult {
    val method = UniformCostSearchRouter::class.java.getDeclaredMethod("executeSearch",
        RoadGraph::class.java, String::class.java, String::class.java, TurnRestrictionIndex::class.java,
        ViaWayTurnRestrictionDfa::class.java, Map::class.java, FloodAssessmentContext::class.java)
    method.isAccessible = true
    return try {
        method.invoke(this, graph, start, goal, index, dfa, assessments, hazard) as UcsRouteResult
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
