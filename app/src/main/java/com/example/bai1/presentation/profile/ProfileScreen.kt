package com.example.bai1.presentation.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.R
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.AppBottomNavigation
import com.example.bai1.presentation.components.AppTopBar
import com.example.bai1.ui.theme.*
import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.auth.session.AuthState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToVolunteerRegister: () -> Unit,
    onLogout: () -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    val userRole by DisasterRepository.currentUserRole.collectAsState()
    val authState by AuthAppDependencies.repository.state.collectAsState()
    val authUser = (authState as? AuthState.Authenticated)?.user

    val isRescuer = authUser?.role == "RESCUER" || userRole.name == "RESCUER"
    val isPending = authUser?.verificationStatus == "PENDING"

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Hồ sơ cá nhân",
                showBackButton = true,
                onBackClick = { onBottomNavNavigate("home") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = PrimaryBlue)
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "profile",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.avatar), // Mock avatar
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Verified Badge on Avatar
                if (!isPending) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = authUser?.fullName ?: "Người dùng",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (!isPending) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isRescuer) "Nhà hoạt động cứu hộ" else "Người dùng",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isRescuer && isPending) {
                // Pending Badge
                Surface(
                    color = WarningOrangeLight,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = WarningOrangeDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đăng ký hoạt động cứu hộ đang chờ xác minh", color = WarningOrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isRescuer) {
                // Ready Badge
                Surface(
                    color = SuccessGreenLight,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreenDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đang sẵn sàng", color = SuccessGreenDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Stats Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = "25", label = "Nhiệm vụ")
                    HorizontalDivider(modifier = Modifier.height(40.dp).width(1.dp), color = BorderColor)
                    StatItem(value = "156", label = "Giờ tình nguyện")
                    HorizontalDivider(modifier = Modifier.height(40.dp).width(1.dp), color = BorderColor)
                    StatItem(value = "5.0", label = "Đánh giá")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Kỹ năng cứu hộ
            SectionHeader(title = "Kỹ năng cứu hộ")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkillChip("Lái thuyền")
                SkillChip("Sơ cứu y tế")
                SkillChip("Tìm kiếm cứu nạn")
                SkillChip("Hỗ trợ di tản")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Phương tiện / Trang thiết bị
            SectionHeader(title = "Phương tiện / Trang thiết bị")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EquipmentItem(icon = Icons.Default.DirectionsBoat, label = "Thuyền")
                EquipmentItem(icon = Icons.Default.DirectionsCar, label = "Xe bán tải")
                EquipmentItem(icon = Icons.Default.DryCleaning, label = "Áo phao")
                EquipmentItem(icon = Icons.Default.MedicalServices, label = "Bộ sơ cứu")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Khu vực hoạt động
            SectionHeader(title = "Khu vực hoạt động")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(BackgroundLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Đà Nẵng, Quảng Nam, Thừa Thiên Huế",
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Giấy chứng nhận
            SectionHeader(title = "Giấy chứng nhận")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Chứng chỉ sơ cứu y tế", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(SuccessGreenLight).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreenDark, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đã xác thực", fontSize = 11.sp, color = SuccessGreenDark, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(32.dp)) // padding for bottom nav
        }
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )
}

@Composable
fun SkillChip(title: String) {
    Surface(
        color = PrimaryLightBlue,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = title,
            color = PrimaryBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun EquipmentItem(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, PrimaryLightBlue, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
    }
}
