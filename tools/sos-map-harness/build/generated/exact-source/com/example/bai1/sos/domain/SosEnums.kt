package com.example.bai1.sos.domain

/**
 * How an SOS event is (or will be) transported to the rescue network.
 *
 * IMPORTANT: These are architectural states only.
 * NO real networking is implemented in Phase 7A.
 *
 * Future phases:
 *   ONLINE  → REST (NestJS) / Firebase Realtime Database   (Phase 7C / 7D)
 *   MESH    → Nearby Connections peer-to-peer              (Phase 7E)
 *   RELAY   → Mesh node → Internet gateway → Server        (Phase 7F)
 */
enum class SosTransportMode {
    /** Device has internet and SOS was (or will be) sent via REST/Firebase. */
    ONLINE,

    /** No internet; SOS is queued for Nearby Connections mesh relay. */
    MESH,

    /** SOS reached a mesh relay node that has internet access. */
    RELAY,

    /** Transport mode has not been determined yet. */
    UNKNOWN
}

/** Urgency level the user self-reports on the SOS form. */
enum class SosUrgency(val label: String) {
    CRITICAL("Nguy hiểm tính mạng"),
    HIGH("Cần cứu trợ ngay"),
    MEDIUM("Cần hỗ trợ"),
    LOW("Theo dõi");

    val vietnameseName: String
        get() = when (this) {
            CRITICAL -> "NGUY CẤP"
            HIGH -> "CAO"
            MEDIUM -> "TRUNG BÌNH"
            LOW -> "THẤP"
        }

    val displayTitle: String
        get() = when (this) {
            CRITICAL -> "Nguy cấp"
            HIGH -> "Cao"
            MEDIUM -> "Trung bình"
            LOW -> "Thấp"
        }

    companion object {
        fun toVietnamese(value: String?): String {
            return when (value?.trim()?.uppercase()) {
                "CRITICAL" -> "NGUY CẤP"
                "HIGH" -> "CAO"
                "MEDIUM" -> "TRUNG BÌNH"
                "LOW" -> "THẤP"
                else -> value?.ifBlank { "CHƯA RÕ" } ?: "CHƯA RÕ"
            }
        }

        fun toDisplayTitle(value: String?): String {
            return when (value?.trim()?.uppercase()) {
                "CRITICAL" -> "Nguy cấp"
                "HIGH" -> "Cao"
                "MEDIUM" -> "Trung bình"
                "LOW" -> "Thấp"
                else -> value?.ifBlank { "Chưa rõ" } ?: "Chưa rõ"
            }
        }
    }
}

/** Lifecycle status of an SOS event. */
enum class SosStatus(val label: String) {
    PENDING("Đang chờ"),
    SUBMITTED("Đã gửi"),
    QUEUED("Đã lưu, chờ kết nối"),
    FAILED("Gửi thất bại"),
    ACTIVE("Đang hoạt động"),
    RESOLVED("Đã giải quyết"),
    FALSE_ALARM("Báo động giả")
}
