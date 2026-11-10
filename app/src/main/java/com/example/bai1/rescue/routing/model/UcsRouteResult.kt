package com.example.bai1.rescue.routing.model

/**
 * Trạng thái kết quả định tuyến cứu hộ.
 */
enum class UcsRouteStatus {
    /** Tìm thấy tuyến đường tối ưu thành công. */
    SUCCESS,
    /** Không tìm thấy tuyến đường hợp lệ nối giữa hai điểm. */
    NO_PATH,
    /** Đầu vào không hợp lệ (ví dụ: nút xuất phát hoặc đích không tồn tại trong đồ thị). */
    INVALID_INPUT,
    /** Đồ thị chưa sẵn sàng hoặc rỗng. */
    GRAPH_UNAVAILABLE,
    /** Đồ thị chưa đáp ứng đầy đủ tiêu chuẩn an toàn định tuyến nghiêm ngặt (Fail-closed Safety Gate). */
    STRICT_ROUTING_DISABLED,
    /** Lỗi an toàn nội bộ trong quá trình định tuyến (DFA state corruption, invalid state ID, etc.). */
    ROUTING_INTERNAL_SAFETY_FAILURE
}

/**
 * Kết quả tính toán tuyến đường bằng thuật toán Uniform Cost Search (UCS).
 *
 * @property status Trạng thái thực thi thuật toán (SUCCESS, NO_PATH, INVALID_INPUT, GRAPH_UNAVAILABLE, STRICT_ROUTING_DISABLED).
 * @property pathNodeIds Danh sách ID các nút đi qua từ điểm xuất phát đến đích.
 * @property pathEdges Danh sách các cạnh (RoadEdge) cấu thành nên tuyến đường đã chọn.
 * @property pathGeometry Chuỗi tọa độ GeoPoint liên tục theo đúng chiều di chuyển phục vụ vẽ polyline.
 * @property totalCost Tổng chi phí tính toán theo hàm mục tiêu g(n) của UCS (bao gồm trọng số rủi ro thiên tai).
 * @property totalDistanceMeters Tổng chiều dài khoảng cách vật lý thực tế của tuyến đường (tính bằng mét).
 * @property visitedNodesCount Số lượng nút đã được thuật toán lấy ra (poll) và duyệt từ PriorityQueue.
 * @property blockedEdgesSkipped Số cạnh bị phong tỏa (BLOCKED) mà thuật toán đã bỏ qua trong quá trình tìm kiếm.
 * @property floodedEdgesOnRoute Số cạnh ngập lụt (FLOODED) nằm trên tuyến đường cuối cùng được chọn.
 * @property graphVersion Phiên bản dữ liệu đồ thị được sử dụng khi tính toán.
 * @property turnRestrictionsSkipped Số chuyển tiếp bị hạn chế rẽ (turn restrictions) đã bỏ qua trong quá trình tìm kiếm.
 * @property routingDisabledReason Lý do kỹ thuật giải thích vì sao strict routing bị từ chối nếu có.
 */
data class UcsRouteResult(
    val status: UcsRouteStatus,
    val pathNodeIds: List<String> = emptyList(),
    val pathEdges: List<RoadEdge> = emptyList(),
    val pathGeometry: List<GeoPoint> = emptyList(),
    val totalCost: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    val visitedNodesCount: Int = 0,
    val blockedEdgesSkipped: Int = 0,
    val floodedEdgesOnRoute: Int = 0,
    val graphVersion: String = "",
    val turnRestrictionsSkipped: Int = 0,
    val routingDisabledReason: String? = null
)
