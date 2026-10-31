package com.example.bai1.presentation.rescue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bai1.R
import com.example.bai1.rescue.network.MissionStatus
import com.example.bai1.rescue.presentation.RescueMissionViewModel

import com.example.bai1.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(
    missionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit,
    viewModel: RescueMissionViewModel = viewModel(factory = com.example.bai1.rescue.presentation.RescueMissionViewModelFactory())
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tổng quan", "Đội tham gia", "Cập nhật")
    
    val selectedMission by viewModel.selectedMission.collectAsState()
    val isCompleting by viewModel.isCompleting.collectAsState()
    val missionActionError by viewModel.missionActionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(missionActionError) {
        if (missionActionError != null) {
            snackbarHostState.showSnackbar(missionActionError!!)
            viewModel.clearMissionActionError()
        }
    }
    
    LaunchedEffect(missionId) {
        viewModel.loadMissionDetail(missionId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Chi tiết nhiệm vụ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().shadow(elevation = 16.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedMission?.status != MissionStatus.COMPLETED.name) {
                        Button(
                            onClick = { viewModel.completeMission(missionId) },
                            enabled = !isCompleting,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isCompleting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Hoàn thành nhiệm vụ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = "Đã hoàn thành",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        containerColor = Color.White
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isCompleted = selectedMission?.status == MissionStatus.COMPLETED.name
                        val statusBg = if (isCompleted) SuccessGreen else EmergencyRed
                        val statusText = if (isCompleted) "Đã hoàn thành" else "Đang thực hiện"
                        
                        Surface(
                            color = statusBg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Circle, contentDescription = null, tint = Color.White, modifier = Modifier.size(6.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(statusText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Surface(
                            color = WarningOrangeLight,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val urgencyStr = selectedMission?.request?.urgency ?: "HIGH"
                            val urgencyText = if (urgencyStr == "CRITICAL") "Đặc biệt khẩn" else "Ưu tiên cao"
                            Text(urgencyText, color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = selectedMission?.request?.description ?: "Cứu hộ khẩn cấp",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val lat = selectedMission?.request?.latitude
                        val lon = selectedMission?.request?.longitude
                        val locText = if (lat != null && lon != null) "Lat: $lat, Lng: $lon" else "Không rõ vị trí"
                        Text(locText, fontSize = 14.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val timeRaw = selectedMission?.acceptedAt ?: selectedMission?.request?.createdAt ?: "Vừa xong"
                        val timeStr = if (timeRaw.length >= 16) timeRaw.substring(0, 16).replace("T", " ") else timeRaw
                        Text(timeStr, fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }

            // TabRow
            item {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.White,
                    contentColor = PrimaryBlue,
                    divider = { HorizontalDivider(color = BorderColor) }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (selectedTabIndex == index) PrimaryBlue else TextSecondary
                                )
                            }
                        )
                    }
                }
            }

            // Tab Content
            if (selectedTabIndex == 0) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Map Snippet
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.news_storm), // Placeholder map
                                contentDescription = "Map route",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Button(
                                onClick = {
                                    val lat = selectedMission?.request?.latitude
                                    val lng = selectedMission?.request?.longitude
                                    if (lat != null && lng != null) {
                                        val uri = Uri.parse("google.navigation:q=$lat,$lng")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                        mapIntent.setPackage("com.google.android.apps.maps")
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (e: Exception) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text("Chỉ đường", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Mục tiêu
                        Text("Thông tin nhiệm vụ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        BulletList(
                            items = listOf(
                                "Mã nhiệm vụ: ${selectedMission?.id ?: ""}",
                                "Số người cần cứu: ${selectedMission?.request?.peopleCount ?: 1}",
                                "Tình trạng: ${selectedMission?.status ?: ""}"
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0987654321")) // Mock phone
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                                border = BorderStroke(1.dp, PrimaryBlue)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gọi", fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = onNavigateToChat,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                                border = BorderStroke(1.dp, PrimaryBlue)
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chat nhóm", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•", fontSize = 16.sp, color = TextPrimary, modifier = Modifier.padding(end = 8.dp))
                Text(text = item, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
            }
        }
    }
}
