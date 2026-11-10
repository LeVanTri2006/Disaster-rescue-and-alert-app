package com.example.bai1.presentation.map

import com.example.bai1.sos.domain.SosRescueInfo
import com.google.android.gms.maps.model.LatLng

/**
 * Shared state để truyền tín hiệu điều hướng từ SosDetailScreen hoặc MissionDetailScreen
 * sang DisasterMapScreen để tự động tính toán thuật toán UCS và vẽ đường đi,
 * đồng thời quản lý trạng thái theo dõi thời gian thực phía người dân (Citizen tracking mode).
 */
object DisasterMapSharedState {
    @Volatile
    var targetEventId: String? = null

    @Volatile
    var targetDestination: TargetDestination? = null

    @Volatile
    var lastKnownRescuerLocation: LatLng? = null

    @Volatile
    var lastKnownVictimLocation: LatLng? = null

    @Volatile
    var citizenTrackingEventId: String? = null

    @Volatile
    var citizenActiveSos: SosRescueInfo? = null

    @Volatile
    var isCitizenTrackingMode: Boolean = false

    data class TargetDestination(
        val latitude: Double,
        val longitude: Double,
        val title: String = "Vị trí cứu hộ",
        val urgency: String = "KHẨN CẤP",
        val eventId: String? = null,
        val requestId: String? = null
    )
}

