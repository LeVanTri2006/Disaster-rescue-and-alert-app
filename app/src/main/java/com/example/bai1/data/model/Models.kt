package com.example.bai1.data.model

enum class UserRole(val displayName: String) {
    USER("Người dùng"),
    RESCUER("Nhà hoạt động cứu hộ")
}

enum class DisasterCategory(val title: String) {
    STORM("Bão"),
    FLOOD("Lũ lụt"),
    LANDSLIDE("Sạt lở đất"),
    RESCUE("Nhiệm vụ cứu hộ")
}

enum class SeverityLevel(val label: String) {
    CRITICAL("NGUY HIỂM CAO"),
    WARNING("CẢNH BÁO"),
    ALERT("ĐANG DIỄN BIẾN"),
    INFO("THEO DÕI")
}

data class WarningItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: DisasterCategory,
    val severity: SeverityLevel,
    val updateTime: String,
    val currentLocation: String,
    val movementDirection: String = "",
    val intensity: String = "",
    val affectedAreas: List<String> = emptyList(),
    val waterLevel: String = "",
    val currentStatus: String = "",
    val isEmergencyAlert: Boolean = false
)

enum class MissionStatus(val label: String) {
    ONGOING("Đang diễn ra"),
    UPCOMING("Sắp tới"),
    COMPLETED("Đã hoàn thành")
}

enum class MissionPriority(val label: String) {
    EMERGENCY("Khẩn cấp"),
    HIGH_PRIORITY("Ưu tiên"),
    NORMAL("Bình thường")
}

data class RescueMission(
    val id: String,
    val title: String,
    val time: String,
    val location: String,
    val volunteersNeeded: Int,
    val volunteersJoined: Int,
    val priority: MissionPriority,
    val status: MissionStatus,
    val description: String,
    val requirements: String,
    val contactPhone: String = "112"
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val message: String,
    val timestamp: String,
    val isMine: Boolean,
    val hasImageAttachment: Boolean = false,
    val imageCaption: String = ""
)

enum class NotificationCategory(val title: String) {
    ALL("Tất cả"),
    WARNING("Cảnh báo"),
    MISSION("Nhiệm vụ"),
    SYSTEM("Hệ thống")
}

data class NotificationItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: String,
    val category: NotificationCategory,
    val severity: SeverityLevel = SeverityLevel.INFO,
    val isUnread: Boolean = true
)

data class VolunteerRegistrationForm(
    val fullName: String = "Nguyễn Văn A",
    val phoneNumber: String = "0775 109 883",
    val activeArea: String = "Đà Nẵng",
    val skills: List<String> = listOf("Cứu hộ đường thủy", "Sơ cứu y tế", "Vận chuyển hàng hóa"),
    val experience: String = "Có kinh nghiệm 3 năm lái xuồng cứu nạn và tham gia cứu hộ lũ lụt miền Trung.",
    val certificateUploaded: Boolean = true,
    val vehicleSupported: String = "Thuyền",
    val readyProvinces: List<String> = listOf("Đà Nẵng", "Quảng Nam", "Thừa Thiên Huế"),
    val committedToDeployment: Boolean = true
)

data class UserProfile(
    val fullName: String = "Nguyễn Văn A",
    val role: UserRole = UserRole.RESCUER,
    val isVerified: Boolean = true,
    val phoneNumber: String = "0775 109 883",
    val email: String = "nguyenvana@gmail.com",
    val activeArea: String = "Đà Nẵng",
    val skills: List<String> = listOf("Cứu hộ đường thủy", "Sơ cứu y tế", "Điều phối hậu cần"),
    val completedMissionsCount: Int = 12,
    val certificatesCount: Int = 3
)
