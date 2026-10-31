package com.example.bai1.presentation.rescue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.R
import com.example.bai1.ui.theme.*

@Composable
fun GroupChatScreen(
    onNavigateBack: () -> Unit
) {
    var inputMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.size(40.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.news_storm), // mock group avatar
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        }
                        Column {
                            Text("Đội cứu hộ Đà Nẵng", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("128 thành viên", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    Row {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Phone, contentDescription = "Gọi", tint = PrimaryBlue)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = PrimaryBlue)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Đính kèm", tint = TextSecondary)
                    }

                    OutlinedTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        placeholder = { Text("Nhập tin nhắn...", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = BackgroundLight,
                            unfocusedContainerColor = BackgroundLight
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pinned Alert Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmergencyRedLight)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = EmergencyRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Yêu cầu mới: Hòa Tiến",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmergencyRed
                        )
                        Text(
                            text = "6 người cần cứu hộ - Cách 2.3 km",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Msg 1 (Receiver text - Tran Van B)
                item {
                    GroupChatMessage(
                        senderName = "Trần Văn B",
                        avatarRes = R.drawable.avatar,
                        message = "Tôi đang di chuyển đến khu vực.",
                        time = "08:20",
                        isMine = false
                    )
                }

                // Msg 2 (Receiver text - Le Minh)
                item {
                    GroupChatMessage(
                        senderName = "Lê Minh",
                        avatarRes = R.drawable.news_flood,
                        message = "Tôi cũng xuất phát, 10 phút nữa sẽ tới.",
                        time = "08:22",
                        isMine = false
                    )
                }

                // Msg 3 (Image from Le Minh)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Spacer(modifier = Modifier.width(32.dp)) // empty avatar space
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.news_flood),
                                contentDescription = "Ảnh hiện trường",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "08:25",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            )
                        }
                    }
                }

                // Msg 4 (Receiver text - Nguyen Thi Lan)
                item {
                    GroupChatMessage(
                        senderName = "Nguyễn Thị Lan",
                        avatarRes = R.drawable.avatar,
                        message = "Khu vực ngập sâu, cần thêm thuyền.",
                        time = "08:26",
                        isMine = false
                    )
                }

                // Msg 5 (Location map)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Spacer(modifier = Modifier.width(32.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Image(
                                    painter = painterResource(id = R.drawable.news_storm), // map snippet
                                    contentDescription = "Map",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Column {
                                    Text("Vị trí hiện tại", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("Hòa Tiến, Đà Nẵng", fontSize = 12.sp, color = TextSecondary)
                                    Text("08:26", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChatMessage(
    senderName: String,
    avatarRes: Int,
    message: String,
    time: String,
    isMine: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isMine) {
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (!isMine) {
                Text(
                    text = senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isMine) 16.dp else 4.dp,
                            topEnd = if (isMine) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(if (isMine) PrimaryBlue else Color.White)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = if (isMine) Color.White else TextPrimary,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = time,
                        fontSize = 10.sp,
                        color = if (isMine) Color.White.copy(alpha = 0.7f) else TextMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
