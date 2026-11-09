package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.model.EdgeCompatibilityStatus
import com.example.bai1.rescue.routing.model.EdgeSafetySummary
import com.example.bai1.rescue.routing.model.GraphDownloadInfo
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoutingBundleInfo
import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.UnsupportedRestrictionsSummary
import com.example.bai1.rescue.routing.model.ViaWayCompilationStatus
import com.example.bai1.rescue.routing.model.WayFlowPolicy
import com.example.bai1.rescue.routing.parser.GraphParseException
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class RoadGraphParserTest {

    private val parser = StreamingRoadGraphParser()

    private fun createValidGraphJson(
        contractVersion: String = "2.1.0",
        graphVersion: String = "dn_central_real_v1",
        vehicleProfile: String = "RESCUE_TRUCK_4X4",
        canAttemptStrictRouting: Boolean = false,
        nodesJson: String = """
            [
              {"id": "node_1", "osmNodeId": 101, "lat": 16.0500, "lon": 108.2000, "isSynthetic": false},
              {"id": "node_2", "osmNodeId": 102, "lat": 16.0550, "lon": 108.2050, "isSynthetic": false}
            ]
        """.trimIndent(),
        edgesJson: String = """
            [
              {
                "id": "edge_1_2",
                "osmWayId": 5001,
                "fromNodeId": "node_1",
                "toNodeId": "node_2",
                "isWayForward": true,
                "lengthMeters": 650.5,
                "geometry": [
                  {"lat": 16.0500, "lon": 108.2000},
                  {"lat": 16.0525, "lon": 108.2025},
                  {"lat": 16.0550, "lon": 108.2050}
                ],
                "wayFlowPolicy": "BIDIRECTIONAL",
                "metadata": {
                  "name": "Đường Lê Duẩn",
                  "highwayType": "primary",
                  "surface": "asphalt",
                  "smoothness": "good",
                  "widthMeters": 14.0,
                  "bridge": false,
                  "tunnel": false,
                  "layer": 0,
                  "rawTags": {"highway": "primary", "name": "Đường Lê Duẩn"},
                  "isDimensionUnverified": true,
                  "isPhysicalClearanceUnverified": true,
                  "unverifiedPhysicalDimensions": ["width"],
                  "unverifiedLegalRestrictions": ["maxweight"],
                  "tagConflicts": [],
                  "tagWarnings": []
                },
                "compatibilityStatus": "UNVERIFIED",
                "isEligibleForStrictRouting": false,
                "blockedReason": null
              }
            ]
        """.trimIndent(),
        turnRestrictionsJson: String = """
            [
              {
                "id": "rel_9001",
                "type": "NO_LEFT_TURN",
                "members": [
                  {"type": "way", "ref": 5001, "role": "from", "order": 0},
                  {"type": "node", "ref": 102, "role": "via", "order": 1},
                  {"type": "way", "ref": 5002, "role": "to", "order": 2}
                ],
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [102],
                "viaOsmWayIds": [],
                "toOsmWayIds": [5002],
                "restrictionMode": null,
                "exceptModes": ["bicycle"],
                "conditionalExpression": null,
                "supportStatus": "FULLY_SUPPORTED",
                "isExemptForProfile": false,
                "prohibitedTransitions": [
                  {"fromEdgeId": "edge_1_2", "toEdgeId": "edge_2_3", "viaNodeId": "node_2"}
                ],
                "allowedTransitions": []
              }
            ]
        """.trimIndent()
    ): String = """
    {
      "${'$'}schema": "https://emergency-rescue.vn/schemas/v2/base-road-graph.json",
      "contractVersion": "$contractVersion",
      "graphVersion": "$graphVersion",
      "vehicleProfile": "$vehicleProfile",
      "boundingBoxType": "PRODUCTION",
      "boundingBox": {
        "minLon": 108.1900,
        "minLat": 16.0350,
        "maxLon": 108.2350,
        "maxLat": 16.0750,
        "bufferedMeters": 500.0
      },
      "isRestrictionStrictRoutingEligible": false,
      "restrictionStrictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS",
      "canAttemptStrictRouting": $canAttemptStrictRouting,
      "isStrictRoutingEligible": false,
      "strictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS",
      "overallStrictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS",
      "edgeSafetySummary": {
        "totalEdges": 1,
        "verifiedCompatibleEdges": 0,
        "unverifiedEdges": 1,
        "blockedEdges": 0,
        "conflictedEdges": 0,
        "strictSafeEdgesPercent": 0.0,
        "blockedWays": 0,
        "excludedBlockedWays": 0,
        "retainedBlockedWays": 0
      },
      "osmSnapshotTimestamp": "2026-11-03T21:24:23Z",
      "generatedAt": "2026-11-04T12:24:50.392Z",
      "nodes": $nodesJson,
      "edges": $edgesJson,
      "turnRestrictions": $turnRestrictionsJson
    }
    """.trimIndent()

    private fun createSampleManifest(
        graphVersion: String = "dn_central_real_v1",
        vehicleProfile: String = "RESCUE_TRUCK_4X4"
    ): RoutingManifest = RoutingManifest(
        manifestVersion = "2.1.0",
        generatedAt = "2026-11-04T12:24:50.392Z",
        graphVersion = graphVersion,
        osmSnapshotTimestamp = "2026-11-03T21:24:23Z",
        importerVersion = "osm-importer-v2.3",
        vehicleProfile = vehicleProfile,
        boundingBoxType = "PRODUCTION",
        bundle = RoutingBundleInfo(
            fileName = "BaseRoadGraph.json.gz",
            compressedSizeBytes = 1173443L,
            uncompressedSizeBytes = 33002111L,
            sha256 = "9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b"
        ),
        hazardSnapshotStatus = "NOT_INCLUDED",
        hazardAssessmentPolicy = "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
        isRestrictionStrictRoutingEligible = false,
        restrictionStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
        unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(totalCount = 16),
        canAttemptStrictRouting = false,
        overallStrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
        edgeSafetySummary = EdgeSafetySummary(
            totalEdges = 16651L,
            verifiedCompatibleEdges = 0L,
            unverifiedEdges = 16651L,
            blockedEdges = 0L,
            conflictedEdges = 0L,
            strictSafeEdgesPercent = 0.0,
            blockedWays = 1425L
        )
    )

    private fun parseString(json: String, expectedManifest: RoutingManifest? = null) =
        parser.parseInputStream(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)), expectedManifest)

    @Test
    fun `1_parses valid graph completely preserving root metadata`() {
        val json = createValidGraphJson()
        val graph = parseString(json)

        assertEquals("2.1.0", graph.contractVersion)
        assertEquals("dn_central_real_v1", graph.graphVersion)
        assertEquals("RESCUE_TRUCK_4X4", graph.vehicleProfile)
        assertEquals("PRODUCTION", graph.boundingBoxType)
        assertNotNull(graph.boundingBox)
        assertEquals(108.1900, graph.boundingBox!!.minLon, 0.0001)
        assertEquals(16.0350, graph.boundingBox!!.minLat, 0.0001)
        assertEquals(500.0, graph.boundingBox!!.bufferedMeters!!, 0.0001)
        assertEquals("2026-11-03T21:24:23Z", graph.osmSnapshotTimestamp)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, graph.overallStrictRoutingReadiness)
        assertFalse(graph.canAttemptStrictRouting)
        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.directedEdges.size)
        assertEquals(1, graph.turnRestrictions.size)
    }

    @Test
    fun `2_parses multiple nodes with coordinates and osm metadata`() {
        val nodesJson = """
            [
              {"id": "node_A", "osmNodeId": 111, "lat": 16.01, "lon": 108.21, "isSynthetic": false},
              {"id": "node_B", "osmNodeId": 222, "lat": 16.02, "lon": 108.22, "isSynthetic": true},
              {"id": "node_C", "osmNodeId": null, "lat": 16.03, "lon": 108.23, "isSynthetic": false}
            ]
        """.trimIndent()
        val edgesJson = """
            [
              {"id": "e1", "osmWayId": 1, "fromNodeId": "node_A", "toNodeId": "node_B", "isWayForward": true, "lengthMeters": 100.0, "geometry": [{"lat": 16.01, "lon": 108.21}, {"lat": 16.02, "lon": 108.22}]},
              {"id": "e2", "osmWayId": 2, "fromNodeId": "node_B", "toNodeId": "node_C", "isWayForward": true, "lengthMeters": 120.0, "geometry": [{"lat": 16.02, "lon": 108.22}, {"lat": 16.03, "lon": 108.23}]}
            ]
        """.trimIndent()

        val json = createValidGraphJson(nodesJson = nodesJson, edgesJson = edgesJson)
        val graph = parseString(json)

        assertEquals(3, graph.nodes.size)
        val nodeA = graph.getNode("node_A")
        assertNotNull(nodeA)
        assertEquals(111L, nodeA!!.osmNodeId)
        assertEquals(16.01, nodeA.lat, 0.0001)
        assertEquals(108.21, nodeA.lon, 0.0001)
        assertFalse(nodeA.isSynthetic)

        val nodeB = graph.getNode("node_B")
        assertNotNull(nodeB)
        assertTrue(nodeB!!.isSynthetic)

        val nodeC = graph.getNode("node_C")
        assertNotNull(nodeC)
        assertNull(nodeC!!.osmNodeId)
    }

    @Test
    fun `3_parses multiple directed edges correctly`() {
        val nodesJson = """
            [
              {"id": "nA", "lat": 16.0, "lon": 108.0},
              {"id": "nB", "lat": 16.1, "lon": 108.1}
            ]
        """.trimIndent()
        // Two opposite directed edges representing a bidirectional street
        val edgesJson = """
            [
              {"id": "e_forward", "osmWayId": 10, "fromNodeId": "nA", "toNodeId": "nB", "isWayForward": true, "lengthMeters": 1500.0, "geometry": [{"lat": 16.0, "lon": 108.0}, {"lat": 16.1, "lon": 108.1}], "wayFlowPolicy": "BIDIRECTIONAL"},
              {"id": "e_backward", "osmWayId": 10, "fromNodeId": "nB", "toNodeId": "nA", "isWayForward": false, "lengthMeters": 1500.0, "geometry": [{"lat": 16.1, "lon": 108.1}, {"lat": 16.0, "lon": 108.0}], "wayFlowPolicy": "BIDIRECTIONAL"}
            ]
        """.trimIndent()

        val json = createValidGraphJson(nodesJson = nodesJson, edgesJson = edgesJson)
        val graph = parseString(json)

        assertEquals(2, graph.directedEdges.size)
        val outgoingA = graph.getOutgoingEdges("nA")
        assertEquals(1, outgoingA.size)
        assertEquals("e_forward", outgoingA[0].id)
        assertTrue(outgoingA[0].isWayForward)

        val outgoingB = graph.getOutgoingEdges("nB")
        assertEquals(1, outgoingB.size)
        assertEquals("e_backward", outgoingB[0].id)
        assertFalse(outgoingB[0].isWayForward)
    }

    @Test
    fun `4_parses edge geometry with multiple intermediate waypoints`() {
        val json = createValidGraphJson()
        val graph = parseString(json)

        val edge = graph.directedEdges.first()
        assertEquals(3, edge.geometry.size)
        assertEquals(16.0500, edge.geometry[0].latitude, 0.0001)
        assertEquals(108.2000, edge.geometry[0].longitude, 0.0001)
        assertEquals(16.0525, edge.geometry[1].latitude, 0.0001)
        assertEquals(108.2025, edge.geometry[1].longitude, 0.0001)
        assertEquals(16.0550, edge.geometry[2].latitude, 0.0001)
        assertEquals(108.2050, edge.geometry[2].longitude, 0.0001)
    }

    @Test
    fun `5_parses turn restrictions completely`() {
        val json = createValidGraphJson()
        val graph = parseString(json)

        assertEquals(1, graph.turnRestrictions.size)
        val restriction = graph.turnRestrictions.first()
        assertEquals("rel_9001", restriction.id)
        assertEquals(TurnRestrictionType.NO_LEFT_TURN, restriction.type)
        assertEquals(3, restriction.members.size)
        assertEquals("way", restriction.members[0].type)
        assertEquals("from", restriction.members[0].role)
        assertEquals(listOf(5001L), restriction.fromOsmWayIds)
        assertEquals(listOf(102L), restriction.viaOsmNodeIds)
        assertEquals(listOf(5002L), restriction.toOsmWayIds)
        assertEquals(listOf("bicycle"), restriction.exceptModes)
        assertEquals(RestrictionSupportStatus.FULLY_SUPPORTED, restriction.supportStatus)
        assertEquals(1, restriction.prohibitedTransitions.size)
        assertEquals("edge_1_2", restriction.prohibitedTransitions[0].fromEdgeId)
        assertEquals("edge_2_3", restriction.prohibitedTransitions[0].toEdgeId)
        assertEquals("node_2", restriction.prohibitedTransitions[0].viaNodeId)
    }

    @Test
    fun `6_preserves unsupported turn restrictions for fail-closed handling`() {
        val unsupportedRestrictionsJson = """
            [
              {
                "id": "rel_conditional_unresolved",
                "type": "NO_LEFT_TURN",
                "members": [],
                "fromOsmWayIds": [7001],
                "viaOsmNodeIds": [201],
                "viaOsmWayIds": [],
                "toOsmWayIds": [7002],
                "conditionalExpression": "no_left_turn @ (06:30-08:30)",
                "supportStatus": "FAIL_CLOSED_UNRESOLVED",
                "isExemptForProfile": false
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(turnRestrictionsJson = unsupportedRestrictionsJson)
        val graph = parseString(json)

        val restriction = graph.turnRestrictions.first()
        assertEquals("rel_conditional_unresolved", restriction.id)
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, restriction.supportStatus)
        assertEquals("no_left_turn @ (06:30-08:30)", restriction.conditionalExpression)
        assertFalse("Must preserve fail-closed state", restriction.isExemptForProfile)
    }

    @Test
    fun `7_preserves edge compatibility status without mapping to clear or unknown`() {
        val nodesJson = """
            [
              {"id": "n1", "lat": 16.0, "lon": 108.0},
              {"id": "n2", "lat": 16.1, "lon": 108.1},
              {"id": "n3", "lat": 16.2, "lon": 108.2},
              {"id": "n4", "lat": 16.3, "lon": 108.3}
            ]
        """.trimIndent()
        val edgesJson = """
            [
              {"id": "e_unverified", "fromNodeId": "n1", "toNodeId": "n2", "lengthMeters": 100.0, "geometry": [{"lat": 16.0, "lon": 108.0}, {"lat": 16.1, "lon": 108.1}], "compatibilityStatus": "UNVERIFIED"},
              {"id": "e_verified", "fromNodeId": "n2", "toNodeId": "n3", "lengthMeters": 100.0, "geometry": [{"lat": 16.1, "lon": 108.1}, {"lat": 16.2, "lon": 108.2}], "compatibilityStatus": "VERIFIED_COMPATIBLE"},
              {"id": "e_blocked", "fromNodeId": "n3", "toNodeId": "n4", "lengthMeters": 100.0, "geometry": [{"lat": 16.2, "lon": 108.2}, {"lat": 16.3, "lon": 108.3}], "compatibilityStatus": "BLOCKED", "blockedReason": "Width 1.8m < vehicle 2.5m"}
            ]
        """.trimIndent()

        val json = createValidGraphJson(nodesJson = nodesJson, edgesJson = edgesJson)
        val graph = parseString(json)

        val eUnverified = graph.directedEdges.first { it.id == "e_unverified" }
        assertEquals(EdgeCompatibilityStatus.UNVERIFIED, eUnverified.compatibilityStatus)
        assertFalse(eUnverified.isEligibleForStrictRouting)

        val eVerified = graph.directedEdges.first { it.id == "e_verified" }
        assertEquals(EdgeCompatibilityStatus.VERIFIED_COMPATIBLE, eVerified.compatibilityStatus)

        val eBlocked = graph.directedEdges.first { it.id == "e_blocked" }
        assertEquals(EdgeCompatibilityStatus.BLOCKED, eBlocked.compatibilityStatus)
        assertEquals("Width 1.8m < vehicle 2.5m", eBlocked.blockedReason)
    }

    @Test
    fun `8_preserves graph readiness flags exactly`() {
        val json = createValidGraphJson(canAttemptStrictRouting = false)
        val graph = parseString(json)

        assertFalse(graph.canAttemptStrictRouting)
        assertFalse(graph.isRestrictionStrictRoutingEligible)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, graph.restrictionStrictRoutingReadiness)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, graph.overallStrictRoutingReadiness)
    }

    @Test(expected = GraphParseException.GraphVersionMismatchException::class)
    fun `9_rejects graph version mismatch against expected manifest`() {
        val json = createValidGraphJson(graphVersion = "dn_central_v2_wrong")
        val expectedManifest = createSampleManifest(graphVersion = "dn_central_real_v1")

        parseString(json, expectedManifest)
    }

    @Test(expected = GraphParseException.ContractVersionMismatchException::class)
    fun `10_rejects unsupported contract version`() {
        val json = createValidGraphJson(contractVersion = "1.0.0")
        parseString(json)
    }

    @Test(expected = GraphParseException.DuplicateIdException::class)
    fun `11_rejects duplicate node ID`() {
        val duplicateNodesJson = """
            [
              {"id": "node_dup", "lat": 16.0500, "lon": 108.2000},
              {"id": "node_dup", "lat": 16.0550, "lon": 108.2050}
            ]
        """.trimIndent()
        val json = createValidGraphJson(nodesJson = duplicateNodesJson)
        parseString(json)
    }

    @Test(expected = GraphParseException.DuplicateIdException::class)
    fun `12_rejects duplicate edge ID`() {
        val duplicateEdgesJson = """
            [
              {"id": "edge_dup", "fromNodeId": "node_1", "toNodeId": "node_2", "lengthMeters": 100.0, "geometry": [{"lat": 16.05, "lon": 108.20}, {"lat": 16.055, "lon": 108.205}]},
              {"id": "edge_dup", "fromNodeId": "node_2", "toNodeId": "node_1", "lengthMeters": 100.0, "geometry": [{"lat": 16.055, "lon": 108.205}, {"lat": 16.05, "lon": 108.20}]}
            ]
        """.trimIndent()
        val json = createValidGraphJson(edgesJson = duplicateEdgesJson)
        parseString(json)
    }

    @Test(expected = GraphParseException.DanglingEdgeReferenceException::class)
    fun `13_rejects edge referencing non-existent node`() {
        val danglingEdgeJson = """
            [
              {"id": "edge_dangling", "fromNodeId": "node_1", "toNodeId": "ghost_node", "lengthMeters": 100.0, "geometry": [{"lat": 16.05, "lon": 108.20}, {"lat": 16.055, "lon": 108.205}]}
            ]
        """.trimIndent()
        val json = createValidGraphJson(edgesJson = danglingEdgeJson)
        parseString(json)
    }

    @Test(expected = GraphParseException.InvalidCoordinateException::class)
    fun `14_rejects out of range WGS84 coordinates in nodes`() {
        val badCoordNodesJson = """
            [
              {"id": "node_1", "lat": 95.0, "lon": 108.2000},
              {"id": "node_2", "lat": 16.0550, "lon": 108.2050}
            ]
        """.trimIndent()
        val json = createValidGraphJson(nodesJson = badCoordNodesJson)
        parseString(json)
    }

    @Test(expected = GraphParseException.InvalidGeometryException::class)
    fun `15_rejects geometry with fewer than two points`() {
        val singlePointGeomEdge = """
            [
              {"id": "edge_single", "fromNodeId": "node_1", "toNodeId": "node_2", "lengthMeters": 100.0, "geometry": [{"lat": 16.05, "lon": 108.20}]}
            ]
        """.trimIndent()
        val json = createValidGraphJson(edgesJson = singlePointGeomEdge)
        parseString(json)
    }

    @Test(expected = GraphParseException.MissingRequiredFieldException::class)
    fun `16_rejects JSON missing required nodes array`() {
        val missingNodesJson = """
        {
          "contractVersion": "2.1.0",
          "graphVersion": "dn_central_real_v1",
          "vehicleProfile": "RESCUE_TRUCK_4X4",
          "edges": []
        }
        """.trimIndent()
        parseString(missingNodesJson)
    }

    @Test(expected = GraphParseException.MalformedJsonException::class)
    fun `17_rejects malformed and truncated JSON syntax`() {
        val truncatedJson = "{\"contractVersion\": \"2.1.0\", \"graphVersion\": "
        parseString(truncatedJson)
    }

    @Test
    fun `18_unverified metadata graph is preserved and not altered into strict-ready`() {
        val json = createValidGraphJson(canAttemptStrictRouting = false)
        val graph = parseString(json)

        // Safety guarantee check: An unverified graph must remain NOT ready for strict routing
        assertFalse("strictRoutingReady must be false when edges/restrictions are unverified", graph.canAttemptStrictRouting)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, graph.overallStrictRoutingReadiness)
        val edge = graph.directedEdges.first()
        assertEquals(EdgeCompatibilityStatus.UNVERIFIED, edge.compatibilityStatus)
        assertFalse(edge.isEligibleForStrictRouting)
    }

    @Test
    fun `19_parse actual 33MB production Da Nang graph file if present on disk`() {
        val prodFile = File("/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app/tools/osm-importer/output-danang-261103-task97/BaseRoadGraph.json")
        if (!prodFile.exists()) {
            println("Skipping production file parse test (file not found on disk)")
            return
        }

        val expectedManifest = createSampleManifest(
            graphVersion = "dn_central_real_v1",
            vehicleProfile = "RESCUE_TRUCK_4X4"
        )

        val startTime = System.currentTimeMillis()
        val graph = parser.parseSync(prodFile, expectedManifest)
        val durationMs = System.currentTimeMillis() - startTime

        println("Parsed 33MB production graph in ${durationMs}ms")
        assertEquals("dn_central_real_v1", graph.graphVersion)
        assertEquals("2.1.0", graph.contractVersion)
        assertEquals("RESCUE_TRUCK_4X4", graph.vehicleProfile)
        assertEquals(7294, graph.nodes.size)
        assertEquals(16651, graph.directedEdges.size)
        assertEquals(27, graph.turnRestrictions.size)
        assertFalse(graph.canAttemptStrictRouting)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, graph.overallStrictRoutingReadiness)
    }

    // =========================================================================
    // PHASE 4 TASK 2B: VIA-WAY GRAPH CONTRACT & PARSER INTEGRATION TESTS
    // =========================================================================

    private fun create3EdgeNodesJson(): String = """
        [
          {"id": "node_1", "osmNodeId": 101, "lat": 16.0500, "lon": 108.2000, "isSynthetic": false},
          {"id": "node_2", "osmNodeId": 102, "lat": 16.0550, "lon": 108.2050, "isSynthetic": false},
          {"id": "node_3", "osmNodeId": 103, "lat": 16.0600, "lon": 108.2100, "isSynthetic": false},
          {"id": "node_4", "osmNodeId": 104, "lat": 16.0650, "lon": 108.2150, "isSynthetic": false}
        ]
    """.trimIndent()

    private fun create3EdgesJson(): String = """
        [
          {
            "id": "edge_1_2",
            "osmWayId": 5001,
            "fromNodeId": "node_1",
            "toNodeId": "node_2",
            "isWayForward": true,
            "lengthMeters": 100.0,
            "geometry": [{"lat": 16.0500, "lon": 108.2000}, {"lat": 16.0550, "lon": 108.2050}],
            "wayFlowPolicy": "FORWARD",
            "metadata": {"name": "From Way", "highwayType": "primary", "surface": "asphalt", "smoothness": "good", "bridge": false, "tunnel": false, "layer": 0, "rawTags": {}},
            "compatibilityStatus": "VERIFIED_COMPATIBLE",
            "isEligibleForStrictRouting": true
          },
          {
            "id": "edge_2_3",
            "osmWayId": 5002,
            "fromNodeId": "node_2",
            "toNodeId": "node_3",
            "isWayForward": true,
            "lengthMeters": 100.0,
            "geometry": [{"lat": 16.0550, "lon": 108.2050}, {"lat": 16.0600, "lon": 108.2100}],
            "wayFlowPolicy": "FORWARD",
            "metadata": {"name": "Via Way", "highwayType": "primary", "surface": "asphalt", "smoothness": "good", "bridge": false, "tunnel": false, "layer": 0, "rawTags": {}},
            "compatibilityStatus": "VERIFIED_COMPATIBLE",
            "isEligibleForStrictRouting": true
          },
          {
            "id": "edge_3_4",
            "osmWayId": 5003,
            "fromNodeId": "node_3",
            "toNodeId": "node_4",
            "isWayForward": true,
            "lengthMeters": 100.0,
            "geometry": [{"lat": 16.0600, "lon": 108.2100}, {"lat": 16.0650, "lon": 108.2150}],
            "wayFlowPolicy": "FORWARD",
            "metadata": {"name": "To Way", "highwayType": "primary", "surface": "asphalt", "smoothness": "good", "bridge": false, "tunnel": false, "layer": 0, "rawTags": {}},
            "compatibilityStatus": "VERIFIED_COMPATIBLE",
            "isEligibleForStrictRouting": true
          }
        ]
    """.trimIndent()

    @Test
    fun `20_parse valid COMPILED restriction`() {
        val restrJson = """
            [
              {
                "id": "rel_via_compiled",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "viaWayCompilationError": null,
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.COMPILED, restr.viaWayCompilationStatus)
        assertEquals(listOf("edge_1_2", "edge_2_3", "edge_3_4"), restr.orderedDirectedEdgeIds)
        assertNull(restr.viaWayCompilationError)
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, restr.supportStatus)
    }

    @Test
    fun `21_parse valid UNRESOLVED restriction`() {
        val restrJson = """
            [
              {
                "id": "rel_via_unresolved",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "UNRESOLVED",
                "orderedDirectedEdgeIds": [],
                "viaWayCompilationError": "Discontinuous topology: consecutive via-ways share no junction",
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertEquals("Discontinuous topology: consecutive via-ways share no junction", restr.viaWayCompilationError)
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, restr.supportStatus)
    }

    @Test
    fun `22_preserve ordered edge IDs`() {
        val restrJson = """
            [
              {
                "id": "rel_order_check",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val seq = graph.turnRestrictions.first().orderedDirectedEdgeIds

        assertEquals("edge_1_2", seq[0])
        assertEquals("edge_2_3", seq[1])
        assertEquals("edge_3_4", seq[2])
    }

    @Test
    fun `23_parse legacy graph without new fields`() {
        // Legacy graph: has viaOsmWayIds, but omits viaWayCompilationStatus, orderedDirectedEdgeIds, viaWayCompilationError
        val restrJson = """
            [
              {
                "id": "rel_legacy_via",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertNotNull(restr.viaWayCompilationError)
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, restr.supportStatus)
    }

    @Test
    fun `24_missing compilation status does not imply COMPILED`() {
        val restrJson = """
            [
              {
                "id": "rel_missing_status",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        // Missing viaWayCompilationStatus MUST NOT be assumed COMPILED
        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
    }

    @Test
    fun `25_COMPILED with empty sequence becomes unresolved`() {
        val restrJson = """
            [
              {
                "id": "rel_empty_seq",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": [],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertTrue(restr.viaWayCompilationError!!.contains("Demoted from COMPILED"))
    }

    @Test
    fun `26_unknown compilation status becomes unresolved`() {
        val restrJson = """
            [
              {
                "id": "rel_unknown_status",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "FUTURE_EXPERIMENTAL_STATUS",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertTrue(restr.viaWayCompilationError!!.contains("Unrecognized viaWayCompilationStatus"))
    }

    @Test
    fun `27_null or malformed edge sequence becomes unresolved`() {
        val restrJson = """
            [
              {
                "id": "rel_malformed_seq",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "invalid_edge_format", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertTrue(restr.viaWayCompilationError!!.contains("Demoted from COMPILED"))
    }

    @Test
    fun `28_missing diagnostic handled safely`() {
        val restrJson = """
            [
              {
                "id": "rel_no_diag",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "UNRESOLVED",
                "orderedDirectedEdgeIds": [],
                "viaWayCompilationError": null,
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertNotNull(restr.viaWayCompilationError)
    }

    @Test
    fun `29_existing via-node restrictions remain compatible`() {
        val restrJson = """
            [
              {
                "id": "rel_via_node",
                "type": "NO_LEFT_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [102],
                "viaOsmWayIds": [],
                "toOsmWayIds": [5002],
                "supportStatus": "FULLY_SUPPORTED",
                "prohibitedTransitions": [
                  {"fromEdgeId": "edge_1_2", "toEdgeId": "edge_2_3", "viaNodeId": "node_2"}
                ]
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.NOT_APPLICABLE, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertEquals(RestrictionSupportStatus.FULLY_SUPPORTED, restr.supportStatus)
    }

    @Test
    fun `30_streaming parser regression with graph containing the new fields`() {
        // Multi-restriction graph containing both via-node and compiled via-way
        val restrJson = """
            [
              {
                "id": "rel_node_1",
                "type": "NO_LEFT_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [102],
                "viaOsmWayIds": [],
                "toOsmWayIds": [5002],
                "supportStatus": "FULLY_SUPPORTED",
                "prohibitedTransitions": [
                  {"fromEdgeId": "edge_1_2", "toEdgeId": "edge_2_3", "viaNodeId": "node_2"}
                ]
              },
              {
                "id": "rel_way_2",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        assertEquals(2, graph.turnRestrictions.size)

        val r1 = graph.turnRestrictions[0]
        assertEquals(ViaWayCompilationStatus.NOT_APPLICABLE, r1.viaWayCompilationStatus)
        assertEquals(RestrictionSupportStatus.FULLY_SUPPORTED, r1.supportStatus)

        val r2 = graph.turnRestrictions[1]
        assertEquals(ViaWayCompilationStatus.COMPILED, r2.viaWayCompilationStatus)
        assertEquals(3, r2.orderedDirectedEdgeIds.size)
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, r2.supportStatus)
    }

    @Test
    fun `31_safety_compiled via-way sequence does not bypass TurnRestrictionIndex`() {
        val restrJson = """
            [
              {
                "id": "rel_way_safety",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val index = TurnRestrictionIndex.build(graph)

        // Index MUST remain incompatible and unresolved!
        assertTrue(index.hasUnresolvedRestrictions)
        assertFalse(index.isRestrictionIndexCompatible)
        assertFalse(index.isStrictRoutingCompatible)
        assertTrue(index.viaWayRestrictions.isNotEmpty())
    }

    @Test
    fun `32_safety_strict routing remains disabled while via-way DFA is not implemented`() {
        val restrJson = """
            [
              {
                "id": "rel_way_safety_2",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson,
            canAttemptStrictRouting = false
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        assertFalse(graph.canAttemptStrictRouting)
        assertFalse(graph.isStrictRoutingEligible)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, graph.restrictionStrictRoutingReadiness)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, graph.overallStrictRoutingReadiness)
    }

    @Test
    fun `33_safety_invalid edge reference fails closed`() {
        // Restriction references edge_9_9 which does NOT exist in graph
        val restrJson = """
            [
              {
                "id": "rel_dangling_edge",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_non_existent", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        // MUST be demoted to UNRESOLVED due to non-existent edge reference
        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertTrue(restr.viaWayCompilationError!!.contains("does not exist in road graph"))
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, restr.supportStatus)
    }

    @Test
    fun `34_safety_discontinuous compiled sequence fails closed`() {
        // Sequence has gap: edge_1_2 ends at node_2, but edge_3_4 starts at node_3 (missing edge_2_3!)
        val restrJson = """
            [
              {
                "id": "rel_gap_seq",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        // MUST be demoted to UNRESOLVED due to topological discontinuity
        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertTrue(restr.viaWayCompilationError!!.contains("Discontinuous edge sequence"))
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, restr.supportStatus)
    }

    @Test
    fun `35_safety_foreign via edge osmWayId fails closed`() {
        // Expected viaOsmWayIds is [9999], but actual via edge edge_2_3 has osmWayId 5002
        val restrJson = """
            [
              {
                "id": "rel_mismatched_via_way",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [9999],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        // MUST be demoted to UNRESOLVED due to mismatched via way sequence
        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertTrue(restr.viaWayCompilationError!!.contains("does not match expected viaOsmWayIds"))
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, restr.supportStatus)
    }

    @Test
    fun `36_safety_via osmWayId sequence mismatch fails closed`() {
        // Expected viaOsmWayIds has 2 via ways [5002, 5004], but sequence only has 1 via way [5002]
        val restrJson = """
            [
              {
                "id": "rel_missing_via_way",
                "type": "NO_U_TURN",
                "fromOsmWayIds": [5001],
                "viaOsmNodeIds": [],
                "viaOsmWayIds": [5002, 5004],
                "toOsmWayIds": [5003],
                "viaWayCompilationStatus": "COMPILED",
                "orderedDirectedEdgeIds": ["edge_1_2", "edge_2_3", "edge_3_4"],
                "supportStatus": "FAIL_CLOSED_UNRESOLVED"
              }
            ]
        """.trimIndent()

        val json = createValidGraphJson(
            nodesJson = create3EdgeNodesJson(),
            edgesJson = create3EdgesJson(),
            turnRestrictionsJson = restrJson
        )

        val graph = parser.parseInputStream(ByteArrayInputStream(json.toByteArray()))
        val restr = graph.turnRestrictions.first()

        assertEquals(ViaWayCompilationStatus.UNRESOLVED, restr.viaWayCompilationStatus)
        assertTrue(restr.orderedDirectedEdgeIds.isEmpty())
        assertTrue(restr.viaWayCompilationError!!.contains("does not match expected viaOsmWayIds"))
        assertEquals(RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED, restr.supportStatus)
    }
}

