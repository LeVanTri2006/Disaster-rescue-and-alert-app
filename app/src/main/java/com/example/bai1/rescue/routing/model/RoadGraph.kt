package com.example.bai1.rescue.routing.model

import java.util.Collections
import java.util.LinkedHashMap
import java.util.ArrayList

/**
 * Đồ thị mạng lưới đường giao thông có hướng phục vụ thuật toán định tuyến cứu hộ.
 * Bảo toàn đầy đủ metadata đồ thị từ OSM Importer và BaseRoadGraph JSON (contractVersion 2.1.0).
 *
 * @property nodes Bảng tra cứu các nút RoadNode theo ID.
 * @property directedEdges Danh sách các cạnh có hướng RoadEdge.
 * @property graphVersion Phiên bản dữ liệu đồ thị (dùng để theo dõi cập nhật bản đồ).
 * @property schema Định danh $schema JSON.
 * @property contractVersion Phiên bản hợp đồng định dạng đồ thị (ví dụ "2.1.0").
 * @property vehicleProfile Hồ sơ phương tiện (ví dụ "RESCUE_TRUCK_4X4").
 * @property boundingBoxType Loại vùng bao ("PRODUCTION" hoặc "FIXTURE").
 * @property boundingBox Tọa độ hộp bao địa lý của đồ thị.
 * @property isRestrictionStrictRoutingEligible Đồ thị đủ điều kiện hạn chế rẽ cho strict routing.
 * @property restrictionStrictRoutingReadiness Trạng thái sẵn sàng của hạn chế rẽ.
 * @property canAttemptStrictRouting Cho phép thử định tuyến nghiêm ngặt.
 * @property isStrictRoutingEligible Alias tương thích cho canAttemptStrictRouting.
 * @property strictRoutingReadiness Alias tương thích cho overallStrictRoutingReadiness.
 * @property overallStrictRoutingReadiness Trạng thái sẵn sàng tổng thể nghiêm ngặt.
 * @property edgeSafetySummary Tóm tắt độ an toàn và tương thích của tập cạnh.
 * @property osmSnapshotTimestamp Thời điểm snapshot dữ liệu OpenStreetMap.
 * @property generatedAt Thời điểm khởi tạo đồ thị.
 * @property turnRestrictions Danh sách đầy đủ các quan hệ hạn chế rẽ (Turn Restrictions).
 */
class RoadGraph(
    nodes: Map<String, RoadNode>,
    directedEdges: List<RoadEdge>,
    val graphVersion: String = "1.0",
    val schema: String? = null,
    val contractVersion: String = "2.1.0",
    val vehicleProfile: String = "RESCUE_TRUCK_4X4",
    val boundingBoxType: String? = null,
    val boundingBox: RoutingBoundingBox? = null,
    val isRestrictionStrictRoutingEligible: Boolean = false,
    val restrictionStrictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
    val canAttemptStrictRouting: Boolean = false,
    val isStrictRoutingEligible: Boolean = false,
    val strictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
    val overallStrictRoutingReadiness: StrictRoutingReadiness = StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS,
    val edgeSafetySummary: EdgeSafetySummary? = null,
    val osmSnapshotTimestamp: String? = null,
    val generatedAt: String? = null,
    turnRestrictions: List<TurnRestriction> = emptyList()
) {
    val nodes: Map<String, RoadNode> = Collections.unmodifiableMap(LinkedHashMap(nodes))
    val directedEdges: List<RoadEdge> = Collections.unmodifiableList(ArrayList(directedEdges.map { it.toDefensive() }))
    val turnRestrictions: List<TurnRestriction> = Collections.unmodifiableList(ArrayList(turnRestrictions.map { it.toDefensive() }))

    /**
     * Constructor thuận tiện nhận danh sách RoadNode.
     */
    constructor(
        nodesList: List<RoadNode>,
        directedEdges: List<RoadEdge>,
        graphVersion: String = "1.0",
        turnRestrictions: List<TurnRestriction> = emptyList()
    ) : this(
        nodes = nodesList.associateBy { it.id },
        directedEdges = directedEdges,
        graphVersion = graphVersion,
        turnRestrictions = turnRestrictions
    )

    /**
     * Map ánh xạ từ fromNodeId tới danh sách các cạnh đi ra (outgoing edges) để tối ưu tra cứu kề.
     */
    val outgoingEdgesByNodeId: Map<String, List<RoadEdge>> by lazy {
        val grouped = HashMap<String, MutableList<RoadEdge>>(nodes.size)
        for (edge in directedEdges) {
            grouped.getOrPut(edge.fromNodeId) { ArrayList(2) }.add(edge)
        }
        Collections.unmodifiableMap(grouped)
    }

    /**
     * Lấy các cạnh đi ra từ một nút.
     */
    fun getOutgoingEdges(nodeId: String): List<RoadEdge> {
        return outgoingEdgesByNodeId[nodeId] ?: emptyList()
    }

    /**
     * Lấy thông tin nút theo ID.
     */
    fun getNode(nodeId: String): RoadNode? = nodes[nodeId]

    /**
     * Kiểm tra xem một nút có tồn tại trong đồ thị hay không.
     */
    fun hasNode(nodeId: String): Boolean = nodes.containsKey(nodeId)

    fun copy(
        nodes: Map<String, RoadNode> = this.nodes,
        directedEdges: List<RoadEdge> = this.directedEdges,
        graphVersion: String = this.graphVersion,
        schema: String? = this.schema,
        contractVersion: String = this.contractVersion,
        vehicleProfile: String = this.vehicleProfile,
        boundingBoxType: String? = this.boundingBoxType,
        boundingBox: RoutingBoundingBox? = this.boundingBox,
        isRestrictionStrictRoutingEligible: Boolean = this.isRestrictionStrictRoutingEligible,
        restrictionStrictRoutingReadiness: StrictRoutingReadiness = this.restrictionStrictRoutingReadiness,
        canAttemptStrictRouting: Boolean = this.canAttemptStrictRouting,
        isStrictRoutingEligible: Boolean = this.isStrictRoutingEligible,
        strictRoutingReadiness: StrictRoutingReadiness = this.strictRoutingReadiness,
        overallStrictRoutingReadiness: StrictRoutingReadiness = this.overallStrictRoutingReadiness,
        edgeSafetySummary: EdgeSafetySummary? = this.edgeSafetySummary,
        osmSnapshotTimestamp: String? = this.osmSnapshotTimestamp,
        generatedAt: String? = this.generatedAt,
        turnRestrictions: List<TurnRestriction> = this.turnRestrictions
    ): RoadGraph = RoadGraph(
        nodes = nodes,
        directedEdges = directedEdges,
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
        turnRestrictions = turnRestrictions
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RoadGraph
        if (graphVersion != other.graphVersion) return false
        if (contractVersion != other.contractVersion) return false
        if (vehicleProfile != other.vehicleProfile) return false
        if (nodes != other.nodes) return false
        if (directedEdges != other.directedEdges) return false
        if (turnRestrictions != other.turnRestrictions) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nodes.hashCode()
        result = 31 * result + directedEdges.hashCode()
        result = 31 * result + graphVersion.hashCode()
        return result
    }

    override fun toString(): String {
        return "RoadGraph(graphVersion='$graphVersion', nodes=${nodes.size}, directedEdges=${directedEdges.size}, turnRestrictions=${turnRestrictions.size})"
    }
}
