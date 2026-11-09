package com.example.bai1.demo

import com.google.gson.Gson
import com.example.bai1.rescue.routing.algorithm.UniformCostSearchRouter
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.model.*
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import java.io.ByteArrayInputStream

/** Test-only graph. Never manufactures qualification, CLEAR or a candidate route. */
object DemoGraph {
    val nodes = listOf(
        RoadNode("A", GeoPoint(16.0500,108.2000), isSynthetic=true),
        RoadNode("C", GeoPoint(16.0510,108.2010), isSynthetic=true),
        RoadNode("D", GeoPoint(16.0500,108.2010), isSynthetic=true),
        RoadNode("E", GeoPoint(16.0500,108.2020), isSynthetic=true),
        RoadNode("F", GeoPoint(16.0490,108.2005), isSynthetic=true),
        RoadNode("G", GeoPoint(16.0490,108.2020), isSynthetic=true),
        RoadNode("B", GeoPoint(16.0510,108.2030), isSynthetic=true),
        RoadNode("H", GeoPoint(16.0520,108.2030), isSynthetic=true)
    )
    val links = listOf("A" to "C", "C" to "B", "A" to "D", "D" to "E", "E" to "B", "A" to "F", "F" to "G", "G" to "B")
    fun json(scenario: String = "normal"): String {
        val router = UniformCostSearchRouter()
        val edges = links.map { (a,b) ->
            val from=nodes.first { it.id==a }; val to=nodes.first { it.id==b }
            mapOf("id" to a+b,"fromNodeId" to a,"toNodeId" to b,
                "lengthMeters" to if (scenario=="invalid" && a+b=="AC") -1.0 else router.distanceMeters(from.position,to.position),
                "geometry" to listOf(mapOf("lat" to from.lat,"lon" to from.lon),mapOf("lat" to to.lat,"lon" to to.lon)),
                "compatibilityStatus" to "UNVERIFIED","isEligibleForStrictRouting" to false)
        }
        val restrictions=if(scenario=="turn") listOf(mapOf("id" to "SYNTHETIC_NO_AC_CB","type" to "NO_LEFT_TURN","supportStatus" to "FULLY_SUPPORTED", "prohibitedTransitions" to listOf(mapOf("fromEdgeId" to "AC","toEdgeId" to "CB","viaNodeId" to "C")))) else emptyList()
        return Gson().toJson(mapOf("contractVersion" to "2.1.0","graphVersion" to "SYNTHETIC_TEST_DATA_E09_$scenario", "vehicleProfile" to "RESCUE_TRUCK_4X4", "boundingBoxType" to "FIXTURE", "canAttemptStrictRouting" to false,
            "nodes" to nodes.map { mapOf("id" to it.id,"lat" to it.lat,"lon" to it.lon,"isSynthetic" to true) },"edges" to edges,"turnRestrictions" to restrictions))
    }
    fun parse(scenario: String = "normal"): RoadGraph {
        val parsed=StreamingRoadGraphParser().parseInputStream(ByteArrayInputStream(json(scenario).toByteArray()))
        // Production base-graph parser initializes CLEAR and has no JSON hazard overlay contract.
        // This conservative TEST-ONLY overlay never claims live/verified hazard knowledge.
        return parsed.copy(directedEdges=parsed.directedEdges.map { edge -> edge.copy(
            condition=if(scenario=="blocked" && edge.id=="AC") RoadCondition.BLOCKED else RoadCondition.UNKNOWN
        ) })
    }
    fun analyze(graph: RoadGraph, start: String, goal: String): UcsRouteResult = UniformCostSearchRouter().findRoute(graph,start,goal)
    fun transition(graph: RoadGraph): String = TurnRestrictionIndex.build(graph).checkTransition("AC","CB","C").decision.name
}
