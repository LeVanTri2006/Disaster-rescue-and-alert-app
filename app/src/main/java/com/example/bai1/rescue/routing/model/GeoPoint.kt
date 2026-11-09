package com.example.bai1.rescue.routing.model

/**
 * Tọa độ địa lý (WGS84).
 * Mô hình thuần domain - hoàn toàn độc lập, không phụ thuộc vào Google Maps SDK (LatLng).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    val isValid: Boolean
        get() = !latitude.isNaN() && !latitude.isInfinite() &&
                !longitude.isNaN() && !longitude.isInfinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
}
