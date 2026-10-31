package com.example.bai1.presentation.rescue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.R
import com.example.bai1.data.model.UserRole
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.AppBottomNavigation
import com.example.bai1.ui.theme.*

@Composable
fun GroupListScreen(
    onNavigateToChat: () -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    val userRole by DisasterRepository.currentUserRole.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(1) } // Default to "Nhóm của tôi"
    val tabs = listOf("Tất cả", "Nhóm của tôi", "Gợi ý")

    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "group_list",
                userRole = userRole,
                onNavigate = onBottomNavNavigate
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Create group */ },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tạo nhóm mới", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
            Text(
                text = "Hội nhóm cứu hộ",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm nhóm...", color = TextSecondary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = TextSecondary)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(2.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.FilterList, contentDescription = "Bộ lọc", tint = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = PrimaryBlue,
                edgePadding = 20.dp,
                indicator = { },
                divider = { }
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) PrimaryBlue else Color.White)
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List
            LazyColumn(
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item {
                    GroupItem(
                        name = "Biệt đội cứu hộ khẩn cấp",
                        members = "120 thành viên",
                        lastMessage = "Trưởng nhóm: Chúng ta sẽ xuất phát lúc 8h",
                        unreadCount = 3,
                        avatarRes = R.drawable.avatar,
                        onClick = onNavigateToChat
                    )
                }
                item {
                    GroupItem(
                        name = "Team cano cứu trợ ĐN",
                        members = "45 thành viên",
                        lastMessage = "Tuấn: Cần thêm 2 phao cứu sinh!",
                        unreadCount = 1,
                        avatarRes = R.drawable.news_flood,
                        onClick = onNavigateToChat
                    )
                }
                item {
                    GroupItem(
                        name = "Y tế tình nguyện",
                        members = "30 thành viên",
                        lastMessage = "Bác sĩ Lan: Đã chuẩn bị đủ túi sơ cứu",
                        unreadCount = 0,
                        avatarRes = R.drawable.news_storm,
                        onClick = onNavigateToChat
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupItem(
    name: String,
    members: String,
    lastMessage: String,
    unreadCount: Int,
    avatarRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(id = avatarRes),
            contentDescription = "Group Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "10:24", // Mock time
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = members,
                fontSize = 13.sp,
                color = PrimaryBlue,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = lastMessage,
                fontSize = 13.sp,
                color = if (unreadCount > 0) TextPrimary else TextSecondary,
                fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
