package com.example.bai1.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAboutApp: () -> Unit,
    onLogout: () -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var locationEnabled by remember { mutableStateOf(true) }
    var readyStatusEnabled by remember { mutableStateOf(true) }
    
    val userRole by DisasterRepository.currentUserRole.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Cài đặt",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "profile", // Keep profile highlighted
                userRole = userRole,
                onNavigate = onBottomNavNavigate
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingArrowItem(icon = Icons.Default.PersonOutline, title = "Tài khoản") {}
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingSwitchItem(icon = Icons.Default.NotificationsNone, title = "Thông báo", checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingSwitchItem(icon = Icons.Default.LocationOn, title = "Quyền truy cập vị trí", checked = locationEnabled, onCheckedChange = { locationEnabled = it })
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingSwitchItem(icon = Icons.Default.CheckCircle, iconColor = SuccessGreen, title = "Trạng thái sẵn sàng cứu hộ", checked = readyStatusEnabled, onCheckedChange = { readyStatusEnabled = it })
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingArrowItem(icon = Icons.Default.Phone, title = "Số liên hệ khẩn cấp") {}
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingValueItem(icon = Icons.Default.Language, title = "Ngôn ngữ", value = "Tiếng Việt") {}
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingValueItem(icon = Icons.Default.LightMode, title = "Giao diện", value = "Sáng") {}
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingValueItem(icon = Icons.Default.Straighten, title = "Đơn vị đo lường", value = "Hệ mét (km)") {}
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingArrowItem(icon = Icons.Default.LocationOn, title = "Vùng hoạt động") {}
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 52.dp))
                
                SettingValueOnlyItem(icon = Icons.Default.Info, title = "Về ứng dụng", value = "Phiên bản 1.0.0", onClick = onNavigateToAboutApp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DividerColor),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Đăng xuất",
                        tint = EmergencyRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đăng xuất",
                        color = EmergencyRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingSwitchItem(
    icon: ImageVector,
    iconColor: Color = PrimaryBlue,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.LightGray,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingArrowItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
private fun SettingValueItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, fontSize = 14.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
private fun SettingValueOnlyItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        Text(text = value, fontSize = 14.sp, color = TextSecondary)
    }
}
