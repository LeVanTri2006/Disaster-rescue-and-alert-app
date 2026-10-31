package com.example.bai1.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.data.model.UserRole
import com.example.bai1.ui.theme.EmergencyRed
import com.example.bai1.ui.theme.PrimaryBlue
import com.example.bai1.ui.theme.TextMuted
import com.example.bai1.ui.theme.TextSecondary

sealed class NavItem(
    val route: String,
    val title: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
) {
    object Home : NavItem("home", "Trang chủ", Icons.Filled.Home, Icons.Outlined.Home)
    object Map : NavItem("map", "Bản đồ", Icons.Filled.Map, Icons.Outlined.Map)
    object SOS : NavItem("sos", "SOS", Icons.Filled.Warning, Icons.Outlined.Warning)
    object Mission : NavItem("mission_list", "Nhiệm vụ", Icons.Filled.Groups, Icons.Outlined.Groups)
    object Notification : NavItem("notification", "Thông báo", Icons.Filled.Notifications, Icons.Outlined.Notifications)
    object Profile : NavItem("profile", "Cá nhân", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun AppBottomNavigation(
    currentRoute: String,
    userRole: UserRole = UserRole.RESCUER,
    onNavigate: (String) -> Unit
) {
    val fourthTab = if (userRole == UserRole.USER) NavItem.Notification else NavItem.Mission

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Trang chủ
            BottomNavItemView(
                item = NavItem.Home,
                isSelected = currentRoute == NavItem.Home.route,
                onClick = { onNavigate(NavItem.Home.route) },
                modifier = Modifier.weight(1f)
            )

            // Tab 2: Bản đồ
            BottomNavItemView(
                item = NavItem.Map,
                isSelected = currentRoute == NavItem.Map.route,
                onClick = { onNavigate(NavItem.Map.route) },
                modifier = Modifier.weight(1f)
            )

            // Tab 3: SOS (Prominent Elevated Red Button in Center)
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(4.dp) // Creates the white border effect
                        .clip(CircleShape)
                        .background(EmergencyRed)
                        .clickable { onNavigate(NavItem.SOS.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Tab 4: Nhiệm vụ (Cứu hộ) hoặc Thông báo (Người dùng)
            BottomNavItemView(
                item = fourthTab,
                isSelected = currentRoute == fourthTab.route,
                onClick = { onNavigate(fourthTab.route) },
                modifier = Modifier.weight(1f)
            )

            // Tab 5: Cá nhân
            BottomNavItemView(
                item = NavItem.Profile,
                isSelected = currentRoute == NavItem.Profile.route,
                onClick = { onNavigate(NavItem.Profile.route) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavItemView(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.iconSelected else item.iconUnselected,
            contentDescription = item.title,
            tint = if (isSelected) PrimaryBlue else TextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) PrimaryBlue else TextSecondary,
            maxLines = 1
        )
    }
}
