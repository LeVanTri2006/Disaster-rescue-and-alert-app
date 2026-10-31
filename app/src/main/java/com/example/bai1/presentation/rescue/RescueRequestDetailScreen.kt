package com.example.bai1.presentation.rescue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AccessTime
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
import com.example.bai1.rescue.presentation.RescueMissionViewModel
import com.example.bai1.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescueRequestDetailScreen(
    requestId: String,
    viewModel: RescueMissionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.bai1.rescue.presentation.RescueMissionViewModelFactory()
    ),
    onNavigateBack: () -> Unit,
    onNavigateToMissionList: () -> Unit
) {
    val request by viewModel.selectedRequest.collectAsState()
    val error by viewModel.missionActionError.collectAsState()

    LaunchedEffect(requestId) {
        viewModel.loadRequestDetail(requestId)
    }

    LaunchedEffect(error) {
        if (error != null) {
            // Wait, Toast in compose needs context, we'll just show it in UI for simplicity
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết yêu cầu cứu hộ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (error != null) {
                        Text(text = "Lỗi: $error", color = EmergencyRed, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Button(
                        onClick = { 
                            viewModel.acceptRequest(requestId)
                            // Ideally observe success state, but we navigate after clicking for now
                            onNavigateToMissionList()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("NHẬN NHIỆM VỤ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    ) { paddingValues ->
        if (request == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundLight)
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                // Header
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmergencyRedLight)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            request?.urgency ?: "Khẩn cấp",
                            color = EmergencyRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp).offset(y = 2.dp))
                        Text(
                            text = if (request?.latitude != null) "Lat: ${request?.latitude}, Lng: ${request?.longitude}" else "Không có vị trí",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Detail List
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val distanceText = request?.distance?.let { d ->
                        if (d < 1000) "~${d.toInt()}m"
                        else "~${String.format(java.util.Locale.US, "%.1f", d / 1000)}km"
                    } ?: "Không rõ"
                    
                    val timeText = request?.createdAt?.let {
                        try {
                            if (it.length >= 16) it.substring(0, 16).replace("T", " ") else it
                        } catch (e: Exception) { "Vừa xong" }
                    } ?: "Vừa xong"
                    
                    DetailRow(icon = Icons.Default.AccessTime, label = "Thời gian", value = timeText)
                    DetailRow(icon = Icons.Default.LocationOn, label = "Khoảng cách tới bạn", value = distanceText)
                    DetailRow(icon = Icons.Default.Groups, label = "Số người cần cứu", value = "${request?.peopleCount ?: 0} người")
                    DetailRow(icon = Icons.Default.Warning, label = "Tình trạng", value = request?.status ?: "")
                    DetailRow(icon = Icons.Default.MedicalServices, label = "Nhu cầu / Mô tả", value = request?.description ?: "Không có mô tả")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Hình ảnh đính kèm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.LightGray.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Không có hình ảnh", fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PrimaryLightBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(text = label, fontSize = 12.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}
