package com.example.bai1.rescue.routing.model

/**
 * Nút giao lộ hoặc điểm mốc trên mạng lưới đồ thị giao thông.
 *
 * @property id Định danh duy nhất của nút (ví dụ ID nút giao lộ).
 * @property position Tọa độ địa lý của nút (GeoPoint).
 * @property osmNodeId Định danh node gốc từ OSM (null nếu là node synthetic).
 * @property isSynthetic Đánh dấu node nội suy/tổng hợp.
 */
data class RoadNode(
    val id: String,
    val position: GeoPoint,
    val osmNodeId: Long? = null,
    val isSynthetic: Boolean = false
) {
    val lat: Double get() = position.latitude
    val lon: Double get() = position.longitude
}
