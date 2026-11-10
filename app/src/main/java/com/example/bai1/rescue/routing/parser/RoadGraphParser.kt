package com.example.bai1.rescue.routing.parser

import com.example.bai1.rescue.routing.model.EdgeCompatibilityStatus
import com.example.bai1.rescue.routing.model.EdgeMetadata
import com.example.bai1.rescue.routing.model.EdgeSafetySummary
import com.example.bai1.rescue.routing.model.EdgeTransition
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.OsmRelationMember
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoadCondition
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.RoutingBoundingBox
import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.ViaWayCompilationStatus
import com.example.bai1.rescue.routing.model.WayFlowPolicy
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Collections

sealed class GraphParseException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ContractVersionMismatchException(message: String) : GraphParseException(message)
    class GraphVersionMismatchException(message: String) : GraphParseException(message)
    class VehicleProfileMismatchException(message: String) : GraphParseException(message)
    class MissingRequiredFieldException(message: String) : GraphParseException(message)
    class DuplicateIdException(message: String) : GraphParseException(message)
    class DanglingEdgeReferenceException(message: String) : GraphParseException(message)
    class InvalidCoordinateException(message: String) : GraphParseException(message)
    class InvalidGeometryException(message: String) : GraphParseException(message)
    class InvalidEdgeLengthException(message: String) : GraphParseException(message)
    class MalformedJsonException(message: String, cause: Throwable? = null) : GraphParseException(message, cause)
}

interface RoadGraphParser {
    /**
     * Phân tích cú pháp file BaseRoadGraph.json theo streaming không tải toàn bộ vào RAM.
     * Xác thực tính toàn vẹn và hợp lệ của đồ thị đối chiếu với manifest kỳ vọng.
     */
    suspend fun parse(
        file: File,
        expectedManifest: RoutingManifest? = null
    ): RoadGraph

    fun parseSync(
        file: File,
        expectedManifest: RoutingManifest? = null
    ): RoadGraph
}

class StreamingRoadGraphParser : RoadGraphParser {

    companion object {
        const val EXPECTED_CONTRACT_VERSION = "2.1.0"
        private const val BUFFER_SIZE = 65536 // 64KB read buffer
    }

    override suspend fun parse(
        file: File,
        expectedManifest: RoutingManifest?
    ): RoadGraph = withContext(Dispatchers.IO) {
        parseSync(file, expectedManifest)
    }

    override fun parseSync(
        file: File,
        expectedManifest: RoutingManifest?
    ): RoadGraph {
        if (!file.exists()) {
            throw FileNotFoundException("Base road graph file not found: ${file.path}")
        }

        FileInputStream(file).use { fis ->
            return parseInputStream(fis, expectedManifest)
        }
    }

    fun parseInputStream(
        inputStream: InputStream,
        expectedManifest: RoutingManifest? = null
    ): RoadGraph {
        InputStreamReader(inputStream, Charsets.UTF_8).use { isr ->
            BufferedReader(isr, BUFFER_SIZE).use { br ->
                JsonReader(br).use { reader ->
                    return parseFromReader(reader, expectedManifest)
                }
            }
        }
    }

    @Throws(GraphParseException::class)
    fun parseFromReader(
        reader: JsonReader,
        expectedManifest: RoutingManifest? = null
    ): RoadGraph {
        var schema: String? = null
        var contractVersion: String? = null
        var graphVersion: String? = null
        var vehicleProfile: String? = null
        var boundingBoxType: String? = null
        var boundingBox: RoutingBoundingBox? = null
        var isRestrictionStrictRoutingEligible = false
        var restrictionStrictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS
        var canAttemptStrictRouting = false
        var isStrictRoutingEligible = false
        var strictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS
        var overallStrictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS
        var edgeSafetySummary: EdgeSafetySummary? = null
        var osmSnapshotTimestamp: String? = null
        var generatedAt: String? = null

        val nodesMap = LinkedHashMap<String, RoadNode>()
        val edgesList = ArrayList<RoadEdge>()
        val edgeIdsSet = HashSet<String>()
        val turnRestrictionsList = ArrayList<TurnRestriction>()

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                val fieldName = reader.nextName()
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    continue
                }

                when (fieldName) {
                    "${'$'}schema" -> schema = reader.nextString()
                    "contractVersion" -> contractVersion = reader.nextString()
                    "graphVersion" -> graphVersion = reader.nextString()
                    "vehicleProfile" -> vehicleProfile = reader.nextString()
                    "boundingBoxType" -> boundingBoxType = reader.nextString()
                    "boundingBox" -> boundingBox = parseBoundingBox(reader)
                    "isRestrictionStrictRoutingEligible" -> isRestrictionStrictRoutingEligible = reader.nextBoolean()
                    "restrictionStrictRoutingReadiness" -> restrictionStrictRoutingReadiness = StrictRoutingReadiness.fromString(reader.nextString())
                    "canAttemptStrictRouting" -> canAttemptStrictRouting = reader.nextBoolean()
                    "isStrictRoutingEligible" -> isStrictRoutingEligible = reader.nextBoolean()
                    "strictRoutingReadiness" -> strictRoutingReadiness = StrictRoutingReadiness.fromString(reader.nextString())
                    "overallStrictRoutingReadiness" -> overallStrictRoutingReadiness = StrictRoutingReadiness.fromString(reader.nextString())
                    "edgeSafetySummary" -> edgeSafetySummary = parseEdgeSafetySummary(reader)
                    "osmSnapshotTimestamp" -> osmSnapshotTimestamp = reader.nextString()
                    "generatedAt" -> generatedAt = reader.nextString()
                    "nodes" -> parseNodesArray(reader, nodesMap)
                    "edges" -> parseEdgesArray(reader, edgesList, edgeIdsSet)
                    "turnRestrictions" -> parseTurnRestrictionsArray(reader, turnRestrictionsList)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw GraphParseException.MalformedJsonException("Trailing data after graph document")
            }
        } catch (e: GraphParseException) {
            throw e
        } catch (e: Exception) {
            throw GraphParseException.MalformedJsonException("Malformed JSON syntax in base road graph: ${e.message}", e)
        }

        // ================= VALIDATION BẮT BUỘC =================

        // 1. Kiểm tra contractVersion
        if (contractVersion == null) {
            throw GraphParseException.MissingRequiredFieldException("Missing required root field: contractVersion")
        }
        if (contractVersion != EXPECTED_CONTRACT_VERSION) {
            throw GraphParseException.ContractVersionMismatchException(
                "Invalid contractVersion: expected $EXPECTED_CONTRACT_VERSION, got $contractVersion"
            )
        }

        // 2. Kiểm tra graphVersion
        if (graphVersion.isNullOrBlank()) {
            throw GraphParseException.MissingRequiredFieldException("Missing required root field: graphVersion")
        }
        if (expectedManifest != null && graphVersion != expectedManifest.graphVersion) {
            throw GraphParseException.GraphVersionMismatchException(
                "Graph version mismatch: expected '${expectedManifest.graphVersion}', got '$graphVersion'"
            )
        }

        // 3. Kiểm tra vehicleProfile
        if (vehicleProfile.isNullOrBlank()) {
            throw GraphParseException.MissingRequiredFieldException("Missing required root field: vehicleProfile")
        }
        if (expectedManifest != null && vehicleProfile != expectedManifest.vehicleProfile) {
            throw GraphParseException.VehicleProfileMismatchException(
                "Vehicle profile mismatch: expected '${expectedManifest.vehicleProfile}', got '$vehicleProfile'"
            )
        }

        // 4. Kiểm tra nodes và edges không được rỗng
        if (nodesMap.isEmpty()) {
            throw GraphParseException.MissingRequiredFieldException("Graph contains no nodes")
        }
        if (edgesList.isEmpty()) {
            throw GraphParseException.MissingRequiredFieldException("Graph contains no edges")
        }

        // 5. Kiểm tra tính toàn vẹn tham chiếu (Dangling reference check):
        // Mọi fromNodeId và toNodeId của từng cạnh phải tham chiếu tới node tồn tại trong nodesMap
        for (edge in edgesList) {
            if (!nodesMap.containsKey(edge.fromNodeId)) {
                throw GraphParseException.DanglingEdgeReferenceException(
                    "Edge '${edge.id}' references non-existent fromNodeId: '${edge.fromNodeId}'"
                )
            }
            if (!nodesMap.containsKey(edge.toNodeId)) {
                throw GraphParseException.DanglingEdgeReferenceException(
                    "Edge '${edge.id}' references non-existent toNodeId: '${edge.toNodeId}'"
                )
            }
        }

        // 6. Xác minh tham chiếu cạnh và tính liên tục topology của compiled via-way restrictions
        val hasCompiledViaWay = turnRestrictionsList.any { it.viaWayCompilationStatus == ViaWayCompilationStatus.COMPILED }
        val edgesById by lazy { edgesList.associateBy { it.id } }
        val validatedTurnRestrictions = ArrayList<TurnRestriction>(turnRestrictionsList.size)

        for (restriction in turnRestrictionsList) {
            if (restriction.viaWayCompilationStatus == ViaWayCompilationStatus.COMPILED) {
                var validationFailedReason: String? = null

                // A. Xác minh tất cả edge IDs đều tồn tại trong đồ thị
                for (edgeId in restriction.orderedDirectedEdgeIds) {
                    if (!edgesById.containsKey(edgeId)) {
                        validationFailedReason = "Referenced edge '$edgeId' does not exist in road graph"
                        break
                    }
                }

                // B. Xác minh chuỗi liên tục: previous.toNodeId == next.fromNodeId
                if (validationFailedReason == null) {
                    val edges = restriction.orderedDirectedEdgeIds.map { edgesById[it]!! }
                    for (i in 0 until edges.size - 1) {
                        val currentEdge = edges[i]
                        val nextEdge = edges[i + 1]
                        if (currentEdge.toNodeId != nextEdge.fromNodeId) {
                            validationFailedReason = "Discontinuous edge sequence: edge '${currentEdge.id}' toNodeId '${currentEdge.toNodeId}' != next edge '${nextEdge.id}' fromNodeId '${nextEdge.fromNodeId}'"
                            break
                        }
                    }

                    // C. Xác minh OSM way ID tương ứng với from / to / via member ways
                    if (validationFailedReason == null && edges.isNotEmpty()) {
                        val collapsedWayIds = ArrayList<Long>()
                        for (edge in edges) {
                            if (collapsedWayIds.isEmpty() || collapsedWayIds.last() != edge.osmWayId) {
                                collapsedWayIds.add(edge.osmWayId)
                            }
                        }

                        val firstWayId = collapsedWayIds.first()
                        val lastWayId = collapsedWayIds.last()

                        if (restriction.fromOsmWayIds.isNotEmpty() && !restriction.fromOsmWayIds.contains(firstWayId)) {
                            validationFailedReason = "From-approach edge '${edges.first().id}' osmWayId $firstWayId not in fromOsmWayIds ${restriction.fromOsmWayIds}"
                        } else if (restriction.toOsmWayIds.isNotEmpty() && !restriction.toOsmWayIds.contains(lastWayId)) {
                            validationFailedReason = "To-departure edge '${edges.last().id}' osmWayId $lastWayId not in toOsmWayIds ${restriction.toOsmWayIds}"
                        } else if (restriction.viaOsmWayIds.isNotEmpty()) {
                            val intermediateWayIds = if (collapsedWayIds.size >= 2) {
                                collapsedWayIds.subList(1, collapsedWayIds.size - 1)
                            } else {
                                emptyList()
                            }
                            if (intermediateWayIds != restriction.viaOsmWayIds) {
                                validationFailedReason = "Via-path edges osmWayId sequence $intermediateWayIds does not match expected viaOsmWayIds ${restriction.viaOsmWayIds}"
                            }
                        }
                    }
                }

                if (validationFailedReason != null) {
                    // Hạ về UNRESOLVED fail-closed với chẩn đoán phù hợp
                    validatedTurnRestrictions.add(
                        restriction.copy(
                            viaWayCompilationStatus = ViaWayCompilationStatus.UNRESOLVED,
                            orderedDirectedEdgeIds = emptyList(),
                            viaWayCompilationError = validationFailedReason,
                            supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
                        )
                    )
                } else {
                    validatedTurnRestrictions.add(restriction)
                }
            } else {
                validatedTurnRestrictions.add(restriction)
            }
        }

        return RoadGraph(
            nodes = nodesMap,
            directedEdges = edgesList,
            graphVersion = graphVersion,
            schema = schema,
            contractVersion = contractVersion,
            vehicleProfile = vehicleProfile,
            boundingBoxType = boundingBoxType,
            boundingBox = boundingBox,
            isRestrictionStrictRoutingEligible = isRestrictionStrictRoutingEligible,
            restrictionStrictRoutingReadiness = restrictionStrictRoutingReadiness,
            canAttemptStrictRouting = canAttemptStrictRouting,
            isStrictRoutingEligible = isStrictRoutingEligible,
            strictRoutingReadiness = strictRoutingReadiness,
            overallStrictRoutingReadiness = overallStrictRoutingReadiness,
            edgeSafetySummary = edgeSafetySummary,
            osmSnapshotTimestamp = osmSnapshotTimestamp,
            generatedAt = generatedAt,
            turnRestrictions = validatedTurnRestrictions
        )
    }

    private fun parseNodesArray(
        reader: JsonReader,
        nodesMap: MutableMap<String, RoadNode>
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            val node = parseNode(reader)
            if (nodesMap.put(node.id, node) != null) {
                throw GraphParseException.DuplicateIdException("Duplicate node ID detected: '${node.id}'")
            }
        }
        reader.endArray()
    }

    private fun parseNode(reader: JsonReader): RoadNode {
        var id: String? = null
        var osmNodeId: Long? = null
        var lat: Double? = null
        var lon: Double? = null
        var isSynthetic = false

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            when (name) {
                "id" -> id = reader.nextString()
                "osmNodeId" -> osmNodeId = reader.nextLong()
                "lat" -> lat = reader.nextDouble()
                "lon" -> lon = reader.nextDouble()
                "isSynthetic" -> isSynthetic = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (id.isNullOrBlank()) {
            throw GraphParseException.MissingRequiredFieldException("Node has missing or blank ID")
        }
        if (lat == null || lon == null) {
            throw GraphParseException.MissingRequiredFieldException("Node '$id' missing latitude or longitude")
        }
        validateCoordinates(lat, lon, "Node '$id'")

        return RoadNode(
            id = id,
            position = GeoPoint(latitude = lat, longitude = lon),
            osmNodeId = osmNodeId,
            isSynthetic = isSynthetic
        )
    }

    private fun parseEdgesArray(
        reader: JsonReader,
        edgesList: MutableList<RoadEdge>,
        edgeIdsSet: MutableSet<String>
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            val edge = parseEdge(reader)
            if (!edgeIdsSet.add(edge.id)) {
                throw GraphParseException.DuplicateIdException("Duplicate edge ID detected: '${edge.id}'")
            }
            edgesList.add(edge)
        }
        reader.endArray()
    }

    private fun parseEdge(reader: JsonReader): RoadEdge {
        var id: String? = null
        var osmWayId: Long = 0L
        var fromNodeId: String? = null
        var toNodeId: String? = null
        var isWayForward = true
        var lengthMeters: Double? = null
        var geometry: List<GeoPoint> = emptyList()
        var wayFlowPolicy = WayFlowPolicy.UNKNOWN
        var metadata = EdgeMetadata()
        var compatibilityStatus = EdgeCompatibilityStatus.UNVERIFIED
        var isEligibleForStrictRouting = false
        var blockedReason: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            when (name) {
                "id" -> id = reader.nextString()
                "osmWayId" -> osmWayId = reader.nextLong()
                "fromNodeId" -> fromNodeId = reader.nextString()
                "toNodeId" -> toNodeId = reader.nextString()
                "isWayForward" -> isWayForward = reader.nextBoolean()
                "lengthMeters" -> lengthMeters = reader.nextDouble()
                "geometry" -> geometry = parseGeometryArray(reader, id)
                "wayFlowPolicy" -> wayFlowPolicy = WayFlowPolicy.fromString(reader.nextString())
                "metadata" -> metadata = parseEdgeMetadata(reader)
                "compatibilityStatus" -> compatibilityStatus = EdgeCompatibilityStatus.fromString(reader.nextString())
                "isEligibleForStrictRouting" -> isEligibleForStrictRouting = reader.nextBoolean()
                "blockedReason" -> blockedReason = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (id.isNullOrBlank()) {
            throw GraphParseException.MissingRequiredFieldException("Edge has missing or blank ID")
        }
        if (fromNodeId.isNullOrBlank()) {
            throw GraphParseException.MissingRequiredFieldException("Edge '$id' has missing or blank fromNodeId")
        }
        if (toNodeId.isNullOrBlank()) {
            throw GraphParseException.MissingRequiredFieldException("Edge '$id' has missing or blank toNodeId")
        }
        if (lengthMeters == null || lengthMeters.isNaN() || lengthMeters.isInfinite() || lengthMeters < 0.0) {
            throw GraphParseException.InvalidEdgeLengthException("Edge '$id' has invalid lengthMeters: $lengthMeters")
        }
        if (geometry.size < 2) {
            throw GraphParseException.InvalidGeometryException("Edge '$id' geometry must contain at least 2 points, found: ${geometry.size}")
        }

        return RoadEdge(
            id = id,
            fromNodeId = fromNodeId,
            toNodeId = toNodeId,
            lengthMeters = lengthMeters,
            geometry = geometry,
            condition = RoadCondition.CLEAR, // Ban đầu là CLEAR, điều kiện thiên tai động sẽ cập nhật sau
            osmWayId = osmWayId,
            isWayForward = isWayForward,
            wayFlowPolicy = wayFlowPolicy,
            metadata = metadata,
            compatibilityStatus = compatibilityStatus,
            isEligibleForStrictRouting = isEligibleForStrictRouting,
            blockedReason = blockedReason
        )
    }

    private fun parseGeometryArray(reader: JsonReader, edgeId: String?): List<GeoPoint> {
        val points = ArrayList<GeoPoint>()
        reader.beginArray()
        while (reader.hasNext()) {
            var lat: Double? = null
            var lon: Double? = null

            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    continue
                }
                when (name) {
                    "lat" -> lat = reader.nextDouble()
                    "lon" -> lon = reader.nextDouble()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (lat == null || lon == null) {
                throw GraphParseException.InvalidGeometryException("Geometry point in edge '${edgeId.orEmpty()}' is missing lat or lon")
            }
            validateCoordinates(lat, lon, "Geometry point in edge '${edgeId.orEmpty()}'")
            points.add(GeoPoint(latitude = lat, longitude = lon))
        }
        reader.endArray()
        return points
    }

    private fun parseEdgeMetadata(reader: JsonReader): EdgeMetadata {
        var name = ""
        var highwayType = ""
        var surface = ""
        var smoothness = ""
        var trackGrade: String? = null
        var surfaceSuitability: String? = null
        var widthMeters: Double? = null
        var effectiveWidthMeters: Double? = null
        var maxWidthMeters: Double? = null
        var maxPhysicalWidthMeters: Double? = null
        var maxHeightMeters: Double? = null
        var maxPhysicalHeightMeters: Double? = null
        var maxWeightTons: Double? = null
        var maxWeightRatingTons: Double? = null
        var maxAxleLoadTons: Double? = null
        var maxSpeedKmH: Double? = null
        var bridge = false
        var tunnel = false
        var layer = 0
        val rawTags = HashMap<String, String>()
        var isDimensionUnverified = false
        var isPhysicalClearanceUnverified = false
        val unverifiedPhysicalDimensions = ArrayList<String>()
        val unverifiedLegalRestrictions = ArrayList<String>()
        val tagConflicts = ArrayList<String>()
        val tagWarnings = ArrayList<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            when (key) {
                "name" -> name = reader.nextString()
                "highwayType" -> highwayType = reader.nextString()
                "surface" -> surface = reader.nextString()
                "smoothness" -> smoothness = reader.nextString()
                "trackGrade" -> trackGrade = reader.nextString()
                "surfaceSuitability" -> surfaceSuitability = reader.nextString()
                "widthMeters" -> widthMeters = reader.nextDouble()
                "effectiveWidthMeters" -> effectiveWidthMeters = reader.nextDouble()
                "maxWidthMeters" -> maxWidthMeters = reader.nextDouble()
                "maxPhysicalWidthMeters" -> maxPhysicalWidthMeters = reader.nextDouble()
                "maxHeightMeters" -> maxHeightMeters = reader.nextDouble()
                "maxPhysicalHeightMeters" -> maxPhysicalHeightMeters = reader.nextDouble()
                "maxWeightTons" -> maxWeightTons = reader.nextDouble()
                "maxWeightRatingTons" -> maxWeightRatingTons = reader.nextDouble()
                "maxAxleLoadTons" -> maxAxleLoadTons = reader.nextDouble()
                "maxSpeedKmH" -> maxSpeedKmH = reader.nextDouble()
                "bridge" -> bridge = reader.nextBoolean()
                "tunnel" -> tunnel = reader.nextBoolean()
                "layer" -> layer = reader.nextInt()
                "rawTags" -> parseStringMap(reader, rawTags)
                "isDimensionUnverified" -> isDimensionUnverified = reader.nextBoolean()
                "isPhysicalClearanceUnverified" -> isPhysicalClearanceUnverified = reader.nextBoolean()
                "unverifiedPhysicalDimensions" -> parseStringList(reader, unverifiedPhysicalDimensions)
                "unverifiedLegalRestrictions" -> parseStringList(reader, unverifiedLegalRestrictions)
                "tagConflicts" -> parseStringList(reader, tagConflicts)
                "tagWarnings" -> parseStringList(reader, tagWarnings)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return EdgeMetadata(
            name = name,
            highwayType = highwayType,
            surface = surface,
            smoothness = smoothness,
            trackGrade = trackGrade,
            surfaceSuitability = surfaceSuitability,
            widthMeters = widthMeters,
            effectiveWidthMeters = effectiveWidthMeters,
            maxWidthMeters = maxWidthMeters,
            maxPhysicalWidthMeters = maxPhysicalWidthMeters,
            maxHeightMeters = maxHeightMeters,
            maxPhysicalHeightMeters = maxPhysicalHeightMeters,
            maxWeightTons = maxWeightTons,
            maxWeightRatingTons = maxWeightRatingTons,
            maxAxleLoadTons = maxAxleLoadTons,
            maxSpeedKmH = maxSpeedKmH,
            bridge = bridge,
            tunnel = tunnel,
            layer = layer,
            rawTags = rawTags,
            isDimensionUnverified = isDimensionUnverified,
            isPhysicalClearanceUnverified = isPhysicalClearanceUnverified,
            unverifiedPhysicalDimensions = unverifiedPhysicalDimensions,
            unverifiedLegalRestrictions = unverifiedLegalRestrictions,
            tagConflicts = tagConflicts,
            tagWarnings = tagWarnings
        )
    }

    private fun parseTurnRestrictionsArray(
        reader: JsonReader,
        turnRestrictionsList: MutableList<TurnRestriction>
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            turnRestrictionsList.add(parseTurnRestriction(reader))
        }
        reader.endArray()
    }

    private fun parseTurnRestriction(reader: JsonReader): TurnRestriction {
        var id = ""
        var type = TurnRestrictionType.UNKNOWN
        val members = ArrayList<OsmRelationMember>()
        val fromOsmWayIds = ArrayList<Long>()
        val viaOsmNodeIds = ArrayList<Long>()
        val viaOsmWayIds = ArrayList<Long>()
        val toOsmWayIds = ArrayList<Long>()
        var restrictionMode: String? = null
        val exceptModes = ArrayList<String>()
        var conditionalExpression: String? = null
        var supportStatus = RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
        var isExemptForProfile = false
        var validationError: String? = null
        val prohibitedTransitions = ArrayList<EdgeTransition>()
        val allowedTransitions = ArrayList<EdgeTransition>()

        var rawCompilationStatusStr: String? = null
        var hasCompilationStatusField = false
        val orderedDirectedEdgeIds = ArrayList<String>()
        var hasOrderedDirectedEdgeIdsField = false
        var viaWayCompilationError: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            when (key) {
                "id" -> id = reader.nextString()
                "type" -> type = TurnRestrictionType.fromString(reader.nextString())
                "members" -> parseRelationMembers(reader, members)
                "fromOsmWayIds" -> parseLongList(reader, fromOsmWayIds)
                "viaOsmNodeIds" -> parseLongList(reader, viaOsmNodeIds)
                "viaOsmWayIds" -> parseLongList(reader, viaOsmWayIds)
                "toOsmWayIds" -> parseLongList(reader, toOsmWayIds)
                "restrictionMode" -> restrictionMode = reader.nextString()
                "exceptModes" -> parseStringList(reader, exceptModes)
                "conditionalExpression" -> conditionalExpression = reader.nextString()
                "supportStatus" -> supportStatus = RestrictionSupportStatus.fromString(reader.nextString())
                "isExemptForProfile" -> isExemptForProfile = reader.nextBoolean()
                "validationError" -> validationError = reader.nextString()
                "prohibitedTransitions" -> parseEdgeTransitions(reader, prohibitedTransitions)
                "allowedTransitions" -> parseEdgeTransitions(reader, allowedTransitions)
                "viaWayCompilationStatus" -> {
                    hasCompilationStatusField = true
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        rawCompilationStatusStr = null
                    } else {
                        rawCompilationStatusStr = reader.nextString()
                    }
                }
                "orderedDirectedEdgeIds" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        hasOrderedDirectedEdgeIdsField = true
                        parseStringList(reader, orderedDirectedEdgeIds)
                    }
                }
                "viaWayCompilationError" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        viaWayCompilationError = null
                    } else {
                        viaWayCompilationError = reader.nextString()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val effectiveCompilationStatus: ViaWayCompilationStatus
        val effectiveOrderedEdgeIds: List<String>
        val effectiveCompilationError: String?

        if (viaOsmWayIds.isEmpty()) {
            effectiveCompilationStatus = ViaWayCompilationStatus.NOT_APPLICABLE
            effectiveOrderedEdgeIds = emptyList()
            effectiveCompilationError = viaWayCompilationError
        } else {
            // Đối với via-way restrictions:
            if (!hasCompilationStatusField || rawCompilationStatusStr.isNullOrBlank()) {
                // Graph artifact cũ không có 3 trường mới -> giữ unresolved fail-closed
                effectiveCompilationStatus = ViaWayCompilationStatus.UNRESOLVED
                effectiveOrderedEdgeIds = emptyList()
                effectiveCompilationError = viaWayCompilationError ?: "Legacy graph artifact: missing via-way compilation fields"
            } else {
                when (rawCompilationStatusStr.trim()) {
                    "COMPILED" -> {
                        // Chỉ chấp nhận COMPILED khi sequence tồn tại, không rỗng, và ID hợp lệ
                        val isValidSequence = hasOrderedDirectedEdgeIdsField &&
                                orderedDirectedEdgeIds.isNotEmpty() &&
                                orderedDirectedEdgeIds.all { it.isNotBlank() && it.startsWith("edge_") }

                        if (isValidSequence) {
                            effectiveCompilationStatus = ViaWayCompilationStatus.COMPILED
                            effectiveOrderedEdgeIds = orderedDirectedEdgeIds
                            effectiveCompilationError = viaWayCompilationError
                        } else {
                            // Hạ về unresolved khi sequence rỗng hoặc ID không hợp lệ
                            effectiveCompilationStatus = ViaWayCompilationStatus.UNRESOLVED
                            effectiveOrderedEdgeIds = emptyList()
                            effectiveCompilationError = viaWayCompilationError ?: "Demoted from COMPILED: invalid or empty orderedDirectedEdgeIds sequence"
                        }
                    }
                    "UNRESOLVED" -> {
                        effectiveCompilationStatus = ViaWayCompilationStatus.UNRESOLVED
                        effectiveOrderedEdgeIds = orderedDirectedEdgeIds
                        effectiveCompilationError = viaWayCompilationError ?: "Via-way restriction compilation unresolved"
                    }
                    "NOT_APPLICABLE" -> {
                        effectiveCompilationStatus = ViaWayCompilationStatus.NOT_APPLICABLE
                        effectiveOrderedEdgeIds = emptyList()
                        effectiveCompilationError = viaWayCompilationError
                    }
                    else -> {
                        // Status không nhận diện được -> fail-closed về UNRESOLVED
                        effectiveCompilationStatus = ViaWayCompilationStatus.UNRESOLVED
                        effectiveOrderedEdgeIds = emptyList()
                        effectiveCompilationError = viaWayCompilationError ?: "Unrecognized viaWayCompilationStatus '$rawCompilationStatusStr', failing closed to UNRESOLVED"
                    }
                }
            }
        }

        // Bất biến an toàn: Via-way restriction luôn bắt buộc supportStatus = FAIL_CLOSED_UNRESOLVED
        val effectiveSupportStatus = if (viaOsmWayIds.isNotEmpty()) {
            RestrictionSupportStatus.FAIL_CLOSED_UNRESOLVED
        } else {
            supportStatus
        }

        return TurnRestriction(
            id = id,
            type = type,
            members = members,
            fromOsmWayIds = fromOsmWayIds,
            viaOsmNodeIds = viaOsmNodeIds,
            viaOsmWayIds = viaOsmWayIds,
            toOsmWayIds = toOsmWayIds,
            restrictionMode = restrictionMode,
            exceptModes = exceptModes,
            conditionalExpression = conditionalExpression,
            supportStatus = effectiveSupportStatus,
            isExemptForProfile = isExemptForProfile,
            validationError = validationError,
            prohibitedTransitions = prohibitedTransitions,
            allowedTransitions = allowedTransitions,
            viaWayCompilationStatus = effectiveCompilationStatus,
            orderedDirectedEdgeIds = effectiveOrderedEdgeIds,
            viaWayCompilationError = effectiveCompilationError
        )
    }

    private fun parseRelationMembers(reader: JsonReader, members: MutableList<OsmRelationMember>) {
        reader.beginArray()
        while (reader.hasNext()) {
            var type = ""
            var ref = 0L
            var role = ""
            var order = 0

            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    continue
                }
                when (key) {
                    "type" -> type = reader.nextString()
                    "ref" -> ref = reader.nextLong()
                    "role" -> role = reader.nextString()
                    "order" -> order = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            members.add(OsmRelationMember(type = type, ref = ref, role = role, order = order))
        }
        reader.endArray()
    }

    private fun parseEdgeTransitions(reader: JsonReader, transitions: MutableList<EdgeTransition>) {
        reader.beginArray()
        while (reader.hasNext()) {
            var fromEdgeId = ""
            var toEdgeId = ""
            var viaNodeId = ""

            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    continue
                }
                when (key) {
                    "fromEdgeId" -> fromEdgeId = reader.nextString()
                    "toEdgeId" -> toEdgeId = reader.nextString()
                    "viaNodeId" -> viaNodeId = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            transitions.add(EdgeTransition(fromEdgeId = fromEdgeId, toEdgeId = toEdgeId, viaNodeId = viaNodeId))
        }
        reader.endArray()
    }

    private fun parseBoundingBox(reader: JsonReader): RoutingBoundingBox {
        var minLon = 0.0
        var minLat = 0.0
        var maxLon = 0.0
        var maxLat = 0.0
        var bufferedMeters: Double? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            when (key) {
                "minLon" -> minLon = reader.nextDouble()
                "minLat" -> minLat = reader.nextDouble()
                "maxLon" -> maxLon = reader.nextDouble()
                "maxLat" -> maxLat = reader.nextDouble()
                "bufferedMeters" -> bufferedMeters = reader.nextDouble()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return RoutingBoundingBox(
            minLon = minLon,
            minLat = minLat,
            maxLon = maxLon,
            maxLat = maxLat,
            bufferedMeters = bufferedMeters
        )
    }

    private fun parseEdgeSafetySummary(reader: JsonReader): EdgeSafetySummary {
        var totalEdges = 0L
        var verifiedCompatibleEdges = 0L
        var unverifiedEdges = 0L
        var blockedEdges = 0L
        var conflictedEdges = 0L
        var strictSafeEdgesPercent = 0.0
        var blockedWays = 0L
        var excludedBlockedWays: Long? = null
        var retainedBlockedWays: Long? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            when (key) {
                "totalEdges" -> totalEdges = reader.nextLong()
                "verifiedCompatibleEdges" -> verifiedCompatibleEdges = reader.nextLong()
                "unverifiedEdges" -> unverifiedEdges = reader.nextLong()
                "blockedEdges" -> blockedEdges = reader.nextLong()
                "conflictedEdges" -> conflictedEdges = reader.nextLong()
                "strictSafeEdgesPercent" -> strictSafeEdgesPercent = reader.nextDouble()
                "blockedWays" -> blockedWays = reader.nextLong()
                "excludedBlockedWays" -> excludedBlockedWays = reader.nextLong()
                "retainedBlockedWays" -> retainedBlockedWays = reader.nextLong()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return EdgeSafetySummary(
            totalEdges = totalEdges,
            verifiedCompatibleEdges = verifiedCompatibleEdges,
            unverifiedEdges = unverifiedEdges,
            blockedEdges = blockedEdges,
            conflictedEdges = conflictedEdges,
            strictSafeEdgesPercent = strictSafeEdgesPercent,
            blockedWays = blockedWays,
            excludedBlockedWays = excludedBlockedWays,
            retainedBlockedWays = retainedBlockedWays
        )
    }

    private fun parseStringList(reader: JsonReader, target: MutableList<String>) {
        reader.beginArray()
        while (reader.hasNext()) {
            target.add(reader.nextString())
        }
        reader.endArray()
    }

    private fun parseLongList(reader: JsonReader, target: MutableList<Long>) {
        reader.beginArray()
        while (reader.hasNext()) {
            target.add(reader.nextLong())
        }
        reader.endArray()
    }

    private fun parseStringMap(reader: JsonReader, target: MutableMap<String, String>) {
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            target[key] = if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                ""
            } else {
                reader.nextString()
            }
        }
        reader.endObject()
    }

    private fun validateCoordinates(lat: Double, lon: Double, context: String) {
        if (lat.isNaN() || lat.isInfinite() || lat < -90.0 || lat > 90.0) {
            throw GraphParseException.InvalidCoordinateException(
                "$context has invalid WGS84 latitude: $lat (must be in range [-90.0, 90.0])"
            )
        }
        if (lon.isNaN() || lon.isInfinite() || lon < -180.0 || lon > 180.0) {
            throw GraphParseException.InvalidCoordinateException(
                "$context has invalid WGS84 longitude: $lon (must be in range [-180.0, 180.0])"
            )
        }
    }
}

