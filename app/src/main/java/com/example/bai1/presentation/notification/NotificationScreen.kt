package com.example.bai1.presentation.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.AppBottomNavigation
import com.example.bai1.presentation.components.AppTopBar
import com.example.bai1.ui.theme.*

@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    val userRole by DisasterRepository.currentUserRole.collectAsState()
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val categories = listOf("Tất cả", "Cảnh báo", "Nhiệm vụ", "Hệ thống")

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Thông báo",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "notification",
                userRole = userRole,
                onNavigate = onBottomNavNavigate
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedCategoryIndex,
                containerColor = Color.White,
                contentColor = PrimaryBlue,
                divider = { HorizontalDivider(color = BorderColor) }
            ) {
                categories.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selectedCategoryIndex == index) PrimaryBlue else TextSecondary
                            )
                        }
                    )
                }
            }

            // Mock Notification items for UI exact match
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    NotificationItemUI(
                        icon = Icons.Default.Warning,
                        iconColor = EmergencyRed,
                        title = "Yêu cầu cứu hộ khẩn cấp",
                        titleColor = EmergencyRed,
                        subtitle = "Tại Hòa Tiến, Đà Nẵng",
                        time = "5 phút trước"
                    )
                }
                item {
                    NotificationItemUI(
                        icon = Icons.Default.AssignmentInd,
                        iconColor = PrimaryBlue,
                        title = "Bạn được phân công nhiệm vụ",
                        titleColor = TextPrimary,
                        subtitle = "Cứu hộ tại Hòa Tiến",
                        time = "10 phút trước"
                    )
                }
                item {
                    NotificationItemUI(
                        icon = Icons.Default.Sos,
                        iconColor = WarningOrange,
                        title = "SOS gần bạn",
                        titleColor = WarningOrange,
                        subtitle = "Có tín hiệu SOS cách 3.1 km",
                        time = "15 phút trước"
                    )
                }
                item {
                    NotificationItemUI(
                        icon = Icons.Default.Groups,
                        iconColor = PrimaryBlue,
                        title = "Tin nhắn nhóm",
                        titleColor = TextPrimary,
                        subtitle = "Đội cứu hộ Đà Nẵng có tin nhắn mới",
                        time = "20 phút trước"
                    )
                }
                item {
                    NotificationItemUI(
                        icon = Icons.Default.CheckCircleOutline,
                        iconColor = SuccessGreen,
                        title = "Cập nhật nhiệm vụ",
                        titleColor = TextPrimary,
                        subtitle = "Nhiệm vụ đã hoàn thành",
                        time = "1 giờ trước"
                    )
                }
                item {
                    NotificationItemUI(
                        icon = Icons.Default.WarningAmber,
                        iconColor = WarningOrange,
                        title = "Cảnh báo lũ lụt",
                        titleColor = WarningOrange,
                        subtitle = "Mực nước sông đang dâng cao",
                        time = "2 giờ trước"
                    )
                }
                item {
                    NotificationItemUI(
                        icon = Icons.Default.SystemUpdate,
                        iconColor = Color(0xFF455A64), // Dark Blue-Gray
                        title = "Thông báo hệ thống",
                        titleColor = TextPrimary,
                        subtitle = "Cập nhật phiên bản mới",
                        time = "1 ngày trước"
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationItemUI(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    titleColor: Color,
    subtitle: String,
    time: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Subtle shadow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = time,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}
