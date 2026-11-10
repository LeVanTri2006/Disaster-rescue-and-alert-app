package com.example.bai1.rescue.routing.model

/**
 * Cạnh có hướng trên đồ thị đường giao thông nối từ fromNodeId đến toNodeId.
 * Đường hai chiều trong thực tế phải được biểu diễn bằng hai RoadEdge ngược chiều riêng biệt.
 * Đường một chiều chỉ có một cạnh theo hướng cho phép.
 *
 * @property id Định danh duy nhất của đoạn đường.
 * @property fromNodeId Nút xuất phát.
 * @property toNodeId Nút đích đến.
 * @property lengthMeters Chiều dài vật lý thực tế của đoạn đường (tính bằng mét).
 * @property geometry Danh sách các tọa độ chi tiết GeoPoint theo thứ tự từ fromNodeId đến toNodeId.
 * @property condition Điều kiện an toàn / thiên tai của đoạn đường (CLEAR, FLOODED, BLOCKED, UNKNOWN).
 * @property osmWayId ID của OSM Way tương ứng.
 * @property isWayForward Chiều di chuyển của cạnh trùng với chiều định nghĩa way trong OSM.
 * @property wayFlowPolicy Chính sách hướng lưu thông (FORWARD, REVERSE, BIDIRECTIONAL,...).
 * @property metadata Thông số vật lý, bề mặt, giới hạn khổ đường từ OSM.
 * @property compatibilityStatus Trạng thái tương thích với phương tiện cứu hộ (VERIFIED_COMPATIBLE, UNVERIFIED, BLOCKED).
 * @property isEligibleForStrictRouting Đủ điều kiện tham gia định tuyến nghiêm ngặt (Strict Routing).
 * @property blockedReason Lý do bị phong tỏa/không tương thích nếu có.
 */
data class RoadEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val lengthMeters: Double,
    val geometry: List<GeoPoint> = emptyList(),
    val condition: RoadCondition = RoadCondition.CLEAR,
    val osmWayId: Long = 0L,
    val isWayForward: Boolean = true,
    val wayFlowPolicy: WayFlowPolicy = WayFlowPolicy.UNKNOWN,
    val metadata: EdgeMetadata = EdgeMetadata(),
    val compatibilityStatus: EdgeCompatibilityStatus = EdgeCompatibilityStatus.UNVERIFIED,
    val isEligibleForStrictRouting: Boolean = false,
    val blockedReason: String? = null
) {
    /**
     * Kiểm tra tính hợp lệ cơ bản của dữ liệu cạnh.
     * Cạnh hợp lệ phải có node ID không rỗng, chiều dài là số hữu hạn và không âm.
     */
    val isValid: Boolean
        get() = fromNodeId.isNotBlank() &&
                toNodeId.isNotBlank() &&
                !lengthMeters.isNaN() &&
                !lengthMeters.isInfinite() &&
                lengthMeters >= 0.0

    /**
     * Tạo bản sao phòng thủ (defensive copy) bảo đảm các collection bên trong hoàn toàn bất biến.
     */
    fun toDefensive(): RoadEdge = copy(
        geometry = java.util.Collections.unmodifiableList(java.util.ArrayList(geometry)),
        metadata = metadata.toDefensive()
    )
}
