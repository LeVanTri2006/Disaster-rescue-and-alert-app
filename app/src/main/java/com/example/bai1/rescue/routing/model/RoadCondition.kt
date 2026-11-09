package com.example.bai1.rescue.routing.model

/**
 * Trạng thái điều kiện giao thông / thiên tai của đoạn đường (RoadEdge).
 *
 * - CLEAR: Đoạn đường thông thoáng, an toàn. Hệ số rủi ro = 1.0.
 * - FLOODED: Đoạn đường ngập nước. Hệ số rủi ro cao = 50.0 (UCS sẽ ưu tiên tìm đường vòng nếu đường vòng có chi phí thấp hơn).
 * - BLOCKED: Đoạn đường bị phong tỏa, sạt lở hoặc tắc nghẽn hoàn toàn. Không được phép lưu thông qua.
 * - UNKNOWN: Trạng thái chưa được xác minh.
 *   POLICY PHASE 1: Không tự động coi là an toàn. Mặc định loại khỏi tuyến đường cứu hộ an toàn.
 */
enum class RoadCondition {
    CLEAR,
    FLOODED,
    BLOCKED,
    UNKNOWN
}
