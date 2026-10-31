package com.example.bai1.data.repository

import com.example.bai1.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DisasterRepository {

    private val _currentUserRole = MutableStateFlow(UserRole.RESCUER)
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    fun setUserRole(role: UserRole) {
        _currentUserRole.value = role
    }

    // Emergency Banner Item
    val emergencyStormAlert = WarningItem(
        id = "storm_yagi",
        title = "Bão số 3 (YAGI)",
        subtitle = "Bão số 3 (YAGI) đang tiến vào đất liền. Dự kiến ảnh hưởng trong 12 giờ tới",
        category = DisasterCategory.STORM,
        severity = SeverityLevel.CRITICAL,
        updateTime = "10/09/2025 - 08:00",
        currentLocation = "Cách Đà Nẵng 150 km",
        movementDirection = "Tây Tây Bắc, tốc độ 20 km/h",
        intensity = "Cấp 13, giật cấp 15",
        affectedAreas = listOf("Đà Nẵng", "Quảng Nam", "Thừa Thiên Huế"),
        isEmergencyAlert = true
    )

    // Detailed Warning Items
    val warningList = listOf(
        emergencyStormAlert,
        WarningItem(
            id = "flood_quangnam",
            title = "Cảnh báo lũ lụt",
            subtitle = "Mực nước sông dâng cao, vượt báo động 3",
            category = DisasterCategory.FLOOD,
            severity = SeverityLevel.WARNING,
            updateTime = "10/09/2025 - 06:30",
            currentLocation = "Quảng Nam, Đà Nẵng",
            waterLevel = "Có thể vượt báo động 3",
            currentStatus = "Nhiều khu vực đã bị ngập sâu",
            affectedAreas = listOf("Huyện Đại Lộc", "Huyện Duy Xuyên", "Xã Hòa Tiến")
        ),
        WarningItem(
            id = "landslide_mountain",
            title = "Nguy cơ sạt lở đất",
            subtitle = "Tại khu vực vùng núi Quảng Nam & Thừa Thiên Huế",
            category = DisasterCategory.LANDSLIDE,
            severity = SeverityLevel.CRITICAL,
            updateTime = "10/09/2025 - 05:20",
            currentLocation = "Khu vực miền núi",
            currentStatus = "Đất đá có nguy cơ sụt lún khi mưa lớn kéo dài",
            affectedAreas = listOf("Nam Trà My", "Bắc Trà My", "A Lưới")
        ),
        WarningItem(
            id = "tropical_depression",
            title = "Áp thấp nhiệt đới",
            subtitle = "Có khả năng mạnh lên thành bão trong 24 giờ tới",
            category = DisasterCategory.STORM,
            severity = SeverityLevel.ALERT,
            updateTime = "10/09/2025 - 04:00",
            currentLocation = "Khu vực Giữa Biển Đông",
            intensity = "Cấp 7, giật cấp 9",
            affectedAreas = listOf("Vùng biển ngoài khơi Trung Bộ")
        ),
        WarningItem(
            id = "tidal_surge",
            title = "Cảnh báo triều cường",
            subtitle = "Tại khu vực ven biển Đà Nẵng - Hội An",
            category = DisasterCategory.FLOOD,
            severity = SeverityLevel.ALERT,
            updateTime = "09/09/2025 - 20:00",
            currentLocation = "Khu vực ven biển",
            waterLevel = "Mực nước đỉnh triều 1.8m",
            affectedAreas = listOf("Ven biển Sơn Trà", "Ngũ Hành Sơn", "Cửa Đại")
        ),
        WarningItem(
            id = "strong_winds",
            title = "Cảnh báo gió mạnh",
            subtitle = "Từ ngày 12/09 trên diện rộng ven biển",
            category = DisasterCategory.STORM,
            severity = SeverityLevel.INFO,
            updateTime = "09/09/2025 - 18:00",
            currentLocation = "Ven biển Trung Bộ",
            intensity = "Gió cấp 6-7, giật cấp 8",
            affectedAreas = listOf("Khu vực ven bờ")
        )
    )

    // Rescue Missions
    val rescueMissions = listOf(
        RescueMission(
            id = "mission_1",
            title = "Hỗ trợ di dời người dân vùng ngập xã Hòa Tiến, Đà Nẵng",
            time = "10/09/2025 - 12:00",
            location = "Hòa Tiến, Đà Nẵng",
            volunteersNeeded = 10,
            volunteersJoined = 7,
            priority = MissionPriority.EMERGENCY,
            status = MissionStatus.ONGOING,
            description = "Hỗ trợ di chuyển người dân đến khu vực an toàn, ưu tiên người già và trẻ em.",
            requirements = "Mang theo áo phao, trang thiết bị cá nhân, đèn pin chống nước."
        ),
        RescueMission(
            id = "mission_2",
            title = "Tiếp tế lương thực, nước uống huyện Đại Lộc, Quảng Nam",
            time = "10/09/2025 - 15:00",
            location = "Đại Lộc, Quảng Nam",
            volunteersNeeded = 5,
            volunteersJoined = 3,
            priority = MissionPriority.HIGH_PRIORITY,
            status = MissionStatus.ONGOING,
            description = "Vận chuyển túi an sinh và nước sạch tới các hộ dân bị cô lập do lũ dâng cao.",
            requirements = "Cần người có thể chèo xuồng/thuyền hoặc có xe tải gầm cao."
        ),
        RescueMission(
            id = "mission_3",
            title = "Gia cố kè chống sạt lở tại đèo Hải Vân",
            time = "11/09/2025 - 08:00",
            location = "Đèo Hải Vân, Đà Nẵng",
            volunteersNeeded = 15,
            volunteersJoined = 6,
            priority = MissionPriority.NORMAL,
            status = MissionStatus.UPCOMING,
            description = "Phối hợp cùng lực lượng công binh đặt bao cát và cọc tiêu cảnh báo sụt lún.",
            requirements = "Sức khỏe tốt, có găng tay bảo hộ và ủng cao su."
        ),
        RescueMission(
            id = "mission_4",
            title = "Sơ tán khẩn cấp 50 hộ dân tại Duy Xuyên",
            time = "09/09/2025 - 14:00",
            location = "Duy Xuyên, Quảng Nam",
            volunteersNeeded = 12,
            volunteersJoined = 12,
            priority = MissionPriority.EMERGENCY,
            status = MissionStatus.COMPLETED,
            description = "Đã hoàn thành di dời an toàn toàn bộ cư dân vùng trũng thấp trước khi đỉnh lũ đạt đỉnh.",
            requirements = "Đã hoàn thành thành công."
        )
    )

    // Rescue Team Chat Messages
    val chatMessages = listOf(
        ChatMessage(
            id = "c1",
            senderName = "Trần Hoàng",
            message = "Chúng ta tập trung tại UBND xã Hòa Tiến lúc 10h nhé!",
            timestamp = "08:12",
            isMine = false
        ),
        ChatMessage(
            id = "c2",
            senderName = "Tôi",
            message = "Tôi đã sẵn sàng, đang di chuyển đến điểm tập kết.",
            timestamp = "08:15",
            isMine = true
        ),
        ChatMessage(
            id = "c3",
            senderName = "Lê Minh",
            message = "Hiện tại nước đang lên nhanh, cần thêm xuồng cứu hộ và áo phao người lớn.",
            timestamp = "08:20",
            isMine = false,
            hasImageAttachment = true,
            imageCaption = "Hiện trường ngập úng tại ngã tư xã Hòa Tiến"
        )
    )

    // Notifications
    val notificationList = listOf(
        NotificationItem(
            id = "n1",
            title = "Bão số 3 (YAGI)",
            description = "Dự kiến đổ bộ trong 12 giờ tới tại khu vực Đà Nẵng - Huế.",
            timestamp = "08:00",
            category = NotificationCategory.WARNING,
            severity = SeverityLevel.CRITICAL
        ),
        NotificationItem(
            id = "n2",
            title = "Bạn được phân công nhiệm vụ",
            description = "Hỗ trợ di dời người dân xã Hòa Tiến, Đà Nẵng.",
            timestamp = "07:30",
            category = NotificationCategory.MISSION,
            severity = SeverityLevel.WARNING
        ),
        NotificationItem(
            id = "n3",
            title = "Cập nhật lũ lụt",
            description = "Mực nước sông Thu Bồn tiếp tục dâng cao, chạm mức báo động 3.",
            timestamp = "06:15",
            category = NotificationCategory.WARNING,
            severity = SeverityLevel.ALERT
        ),
        NotificationItem(
            id = "n4",
            title = "Tin nhắn mới từ nhóm cứu hộ",
            description = "Trần Hoàng: \"Tập trung lúc 10h tại UBND xã\"",
            timestamp = "Hôm qua",
            category = NotificationCategory.MISSION,
            severity = SeverityLevel.INFO
        ),
        NotificationItem(
            id = "n5",
            title = "Nhiệm vụ hoàn thành",
            description = "Đã hỗ trợ phân phát nhu yếu phẩm an toàn tại Đại Lộc.",
            timestamp = "Hôm qua",
            category = NotificationCategory.MISSION,
            severity = SeverityLevel.INFO,
            isUnread = false
        ),
        NotificationItem(
            id = "n6",
            title = "Cảnh báo sạt lở đất",
            description = "Nguy cơ cao tại huyện miền núi Nam Trà My và Bắc Trà My.",
            timestamp = "09/09",
            category = NotificationCategory.WARNING,
            severity = SeverityLevel.CRITICAL,
            isUnread = false
        )
    )

    // User Profile
    val userProfile = UserProfile(
        fullName = "Nguyễn Văn A",
        role = UserRole.RESCUER,
        isVerified = true,
        phoneNumber = "0775 109 883",
        email = "nguyenvana@gmail.com",
        activeArea = "Đà Nẵng",
        skills = listOf("Cứu hộ đường thủy", "Sơ cứu y tế", "Điều phối hậu cần"),
        completedMissionsCount = 12,
        certificatesCount = 3
    )
}
