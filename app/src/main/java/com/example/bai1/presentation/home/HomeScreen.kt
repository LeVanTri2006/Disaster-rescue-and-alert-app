package com.example.bai1.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.R
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.data.model.UserRole
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.*
import com.example.bai1.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToWarningDetail: (String) -> Unit,
    onNavigateToCategory: (DisasterCategory) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMap: () -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    val userRole by DisasterRepository.currentUserRole.collectAsState()

    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "home",
                userRole = userRole,
                onNavigate = onBottomNavNavigate
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        if (userRole == UserRole.RESCUER) {
            VolunteerHomeScreenContent(
                innerPadding = innerPadding,
                onNavigateToMission = { onNavigateToWarningDetail(it) }, // Mock navigation
                onNavigateToGroups = { onBottomNavNavigate("group_list") } // To be added later
            )
        } else {
            NormalUserHomeScreenContent(
                innerPadding = innerPadding,
                onNavigateToWarningDetail = onNavigateToWarningDetail,
                onNavigateToCategory = onNavigateToCategory,
                onNavigateToNotifications = onNavigateToNotifications
            )
        }
    }
}

@Composable
private fun VolunteerHomeScreenContent(
    innerPadding: PaddingValues,
    onNavigateToMission: (String) -> Unit,
    onNavigateToGroups: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ─── 1. HEADER ─────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(48.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.avatar),
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                        // Online green dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                                .border(2.dp, BackgroundLight, CircleShape)
                        )
                    }

                    Column {
                        Text(
                            text = "Xin chào,",
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = DisasterRepository.userProfile.fullName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Nhà hoạt động cứu hộ",
                            fontSize = 12.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ─── STATUS BADGE ───────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .padding(horizontal = 72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SuccessGreenLight)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Đang sẵn sàng",
                    fontSize = 12.sp,
                    color = SuccessGreenDark,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ─── 2. BIG RED EMERGENCY BANNER ───────────────────────────────
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { onNavigateToMission("emergency_1") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EmergencyRed)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "3 yêu cầu cứu hộ khẩn cấp",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Có 1 yêu cầu mới tại Đà Nẵng\nCách bạn 2.5 km",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 18.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ─── 3. METRIC CARDS ───────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Timer,
                    iconTint = PrimaryBlue,
                    iconBg = PrimaryLightBlue,
                    value = "5",
                    label = "Nhiệm vụ\nđang diễn ra"
                )
                // Card 2
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.HealthAndSafety,
                    iconTint = SuccessGreen,
                    iconBg = SuccessGreenLight,
                    value = "12",
                    label = "Đã cứu trợ\nthành công"
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ─── 4. YÊU CẦU CỨU HỘ GẦN BẠN ─────────────────────────────────
        item {
            SectionHeader(title = "Yêu cầu cứu hộ gần bạn", actionText = "Xem tất cả >", onActionClick = {})
            Spacer(modifier = Modifier.height(16.dp))
            
            // Task Card 1 (Red)
            TaskCard(
                title = "Cần cứu hộ khẩn cấp",
                location = "Hòa Tiến, Đà Nẵng",
                details = "2.3 km - 6 người",
                alertColor = EmergencyRed,
                alertBgColor = EmergencyRedLight,
                badgeText = "Khẩn cấp",
                timeAgo = "5 phút trước",
                icon = Icons.Default.WarningAmber
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Task Card 2 (Orange)
            TaskCard(
                title = "Cần di tản",
                location = "Điện Bàn, Quảng Nam",
                details = "12.1 km - 20 người",
                alertColor = WarningOrange,
                alertBgColor = WarningOrangeLight,
                badgeText = "Cao",
                timeAgo = "15 phút trước",
                icon = Icons.Default.ErrorOutline
            )
            
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ─── 5. CHỨC NĂNG NHANH ────────────────────────────────────────
        item {
            SectionHeader(title = "Chức năng nhanh")
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionItem("Nhiệm vụ", Icons.Default.Assignment, PrimaryBlue, PrimaryLightBlue)
                QuickActionItem("Bản đồ", Icons.Default.Map, PrimaryBlue, PrimaryLightBlue)
                QuickActionItem("Hội nhóm", Icons.Default.Groups, PrimaryBlue, PrimaryLightBlue, onClick = onNavigateToGroups)
                QuickActionItem("Tin nhắn", Icons.Default.ChatBubble, PrimaryBlue, PrimaryLightBlue)
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    value: String,
    label: String
) {
    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Text(text = label, fontSize = 11.sp, color = TextSecondary, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionText: String? = null, onActionClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (actionText != null) {
            Text(
                text = actionText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryBlue,
                modifier = Modifier.clickable(onClick = onActionClick)
            )
        }
    }
}



@Composable
private fun QuickActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
        }
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable
private fun NormalUserHomeScreenContent(
    innerPadding: PaddingValues,
    onNavigateToWarningDetail: (String) -> Unit,
    onNavigateToCategory: (DisasterCategory) -> Unit,
    onNavigateToNotifications: () -> Unit
) {
    val emergencyItem = DisasterRepository.emergencyStormAlert
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ─── 1. HEADER ─────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.avatar),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                    Column {
                        Text(
                            text = "Xin chào,",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = DisasterRepository.userProfile.fullName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onNavigateToNotifications),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Thông báo",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed)
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                    )
                }
            }
        }
        item {
            EmergencyAlertCard(
                warningItem = emergencyItem,
                onClick = { onNavigateToWarningDetail(emergencyItem.id) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
            DisasterCategoryGrid(
                onCategoryClick = onNavigateToCategory,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tin tức mới nhất",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onNavigateToCategory(DisasterCategory.STORM) }
                ) {
                    Text(
                        text = "Xem tất cả",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NewsCard(
                title = "Bão số 3 mạnh lên, giật cấp 15",
                timeAgo = "2 giờ trước",
                imageRes = R.drawable.news_storm,
                onClick = { onNavigateToWarningDetail(emergencyItem.id) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            NewsCard(
                title = "Mực nước sông Thu Bồn dâng nhanh gây ngập diện rộng",
                timeAgo = "4 giờ trước",
                imageRes = R.drawable.news_flood,
                onClick = { onNavigateToWarningDetail("flood_quangnam") },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
