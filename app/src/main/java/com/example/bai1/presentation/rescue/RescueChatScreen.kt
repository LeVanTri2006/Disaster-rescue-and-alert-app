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
fun RescueChatScreen(
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
                                painter = painterResource(id = R.drawable.avatar), // placeholder
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                            // Online dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                        Column {
                            Text("Gia đình chị Lan", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Hoạt động 5 phút trước", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    Row {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Phone, contentDescription = "Gọi", tint = PrimaryBlue)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = PrimaryBlue)
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
                        Icon(Icons.Default.Mic, contentDescription = "Mic/Send", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Divider
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE2E8F0))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Hôm nay", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Msg 1 (Receiver text)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Image(
                        painter = painterResource(id = R.drawable.avatar),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Chào bạn, gia đình mình đang mắc kẹt ở tầng 2, nước lên nhanh quá!",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Msg 2 (Receiver image)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Spacer(modifier = Modifier.width(32.dp)) // empty avatar space
                    Image(
                        painter = painterResource(id = R.drawable.news_flood),
                        contentDescription = "Ảnh hiện trường",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }

            // Msg 3 (Sender text)
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 16.dp))
                            .background(PrimaryBlue)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Bạn giữ bình tĩnh, đội cứu hộ đang cách bạn 2km.",
                            fontSize = 14.sp,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Msg 4 (Sender location)
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 16.dp))
                            .background(PrimaryBlue)
                            .padding(4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.news_storm), // mock map image
                            contentDescription = "Bản đồ",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Text(
                            text = "Đội cứu hộ A đã chia sẻ vị trí",
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.padding(8.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
