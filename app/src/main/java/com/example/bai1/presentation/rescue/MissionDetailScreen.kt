package com.example.bai1.presentation.rescue

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bai1.rescue.network.MissionStatus
import com.example.bai1.rescue.presentation.RescueMissionViewModel
import com.example.bai1.ui.theme.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(
    missionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit,
    viewModel: RescueMissionViewModel = viewModel(factory = com.example.bai1.rescue.presentation.RescueMissionViewModelFactory())
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tổng quan", "Đội tham gia", "Cập nhật")

    val selectedMission by viewModel.selectedMission.collectAsState()
    val isCompleting by viewModel.isCompleting.collectAsState()
    val missionActionError by viewModel.missionActionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCompleteDialog by remember { mutableStateOf(false) }
    var completeNotes by remember { mutableStateOf("") }
    var showEmergencyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(missionActionError) {
        if (missionActionError != null) {
            snackbarHostState.showSnackbar(missionActionError!!)
            viewModel.clearMissionActionError()
        }
    }

    LaunchedEffect(missionId) {
        viewModel.loadMissionDetail(missionId)
    }

    val currentStatus = selectedMission?.status ?: ""
    val isCompleted = currentStatus == MissionStatus.COMPLETED.name

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Chi tiết nhiệm vụ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(missionId))
                                    Toast.makeText(context, "Đã sao chép mã UUID: $missionId", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val shortId = if (missionId.length >= 8) "#${missionId.take(8).uppercase()}" else "#$missionId"
                            Text(
                                text = shortId,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryBlue
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 16.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    if (selectedMission == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryBlue
                            )
                        }
                    } else if (isCompleted) {
                        // Completed Banner
                        Surface(
                            color = SuccessGreenLight,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Nhiệm vụ đã hoàn thành xuất sắc",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = SuccessGreenDark
                                    )
                                    val completedTime = selectedMission?.completedAt
                                    if (!completedTime.isNullOrBlank()) {
                                        Text(
                                            text = "Hoàn tất lúc: ${formatMissionDate(completedTime)}",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Swipe-to-Confirm Button (Chống bấm nhầm khi đang cứu nạn)
                        val (swipeText, swipeColor, nextStatus) = when (currentStatus) {
                            MissionStatus.ASSIGNED.name -> Triple(
                                "Vuốt để bắt đầu di chuyển  ❯❯❯",
                                PrimaryBlue,
                                MissionStatus.IN_ROUTE
                            )
                            MissionStatus.IN_ROUTE.name -> Triple(
                                "Vuốt xác nhận đến hiện trường  ❯❯❯",
                                Color(0xFF0284C7),
                                MissionStatus.ARRIVED
                            )
                            MissionStatus.ARRIVED.name -> Triple(
                                "Vuốt để bắt đầu cứu hộ  ❯❯❯",
                                WarningOrange,
                                MissionStatus.IN_PROGRESS
                            )
                            MissionStatus.IN_PROGRESS.name -> Triple(
                                "Vuốt để hoàn thành nhiệm vụ  ❯❯❯",
                                SuccessGreen,
                                MissionStatus.COMPLETED
                            )
                            else -> Triple(
                                "Vuốt cập nhật tiến độ  ❯❯❯",
                                PrimaryBlue,
                                null
                            )
                        }

                        SwipeToConfirmButton(
                            text = swipeText,
                            actionColor = swipeColor,
                            isLoading = isCompleting,
                            onConfirmed = {
                                if (currentStatus == MissionStatus.IN_PROGRESS.name) {
                                    // Open confirmation dialog before final completion
                                    showCompleteDialog = true
                                } else if (nextStatus != null) {
                                    viewModel.updateMissionStatus(missionId, nextStatus)
                                }
                            }
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Header: Status, Urgency, Title, Location, Time
            item {
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusBg = getMissionStatusBg(currentStatus)
                            val statusLabel = getMissionStatusLabel(currentStatus)

                            Surface(
                                color = statusBg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Circle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(6.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = statusLabel,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val urgencyStr = selectedMission?.request?.urgency ?: "HIGH"
                            val (urgencyText, urgencyColor, urgencyBg) = when (urgencyStr.uppercase()) {
                                "CRITICAL" -> Triple("Đặc biệt nguy cấp", EmergencyRed, EmergencyRedLight)
                                "HIGH" -> Triple("Mức độ: Cao", EmergencyRed, EmergencyRedLight)
                                "MEDIUM" -> Triple("Mức độ: Trung bình", WarningOrange, WarningOrangeLight)
                                "LOW" -> Triple("Mức độ: Thấp", PrimaryBlue, PrimaryLightBlue)
                                else -> Triple("Bình thường", PrimaryBlue, PrimaryLightBlue)
                            }
                            Surface(
                                color = urgencyBg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = urgencyText,
                                    color = urgencyColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = selectedMission?.request?.description ?: "Yêu cầu cứu hộ khẩn cấp",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Location row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val lat = selectedMission?.request?.latitude
                            val lon = selectedMission?.request?.longitude
                            val locText = if (lat != null && lon != null) {
                                String.format("%.4f, %.4f", lat, lon)
                            } else {
                                "Chưa xác định tọa độ"
                            }
                            Text(
                                text = "Vị trí hiện trường: $locText",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Time row (Đã khắc phục lỗi tràn chữ)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val timeRaw = selectedMission?.acceptedAt ?: selectedMission?.request?.createdAt
                            val timeStr = formatMissionDate(timeRaw)
                            Text(
                                text = "Tiếp nhận: $timeStr",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Thanh tiến độ nhiệm vụ (5 Bước chuẩn nghiệp vụ)
            item {
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = "Tiến độ thực hiện nhiệm vụ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MissionProgressStepper(currentStatus = currentStatus)
                    }
                }
            }

            // Tab Navigation
            item {
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.White,
                        contentColor = PrimaryBlue,
                        divider = { HorizontalDivider(color = Color(0xFFE2E8F0)) }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = if (selectedTabIndex == index) PrimaryBlue else TextSecondary
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Tab 1: Tổng quan (Overview)
            if (selectedTabIndex == 0) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Bản đồ hiện trường thực tế
                        RealMapCard(
                            latitude = selectedMission?.request?.latitude,
                            longitude = selectedMission?.request?.longitude,
                            urgency = selectedMission?.request?.urgency,
                            description = selectedMission?.request?.description
                        )

                        // 2. Thông tin người cần cứu hộ
                        VictimInfoCard(
                            request = selectedMission?.request,
                            onCall = { phone ->
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            }
                        )

                        // 3. Quick Action Buttons: Gọi & Chat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val requesterPhone = selectedMission?.request?.requesterPhone?.takeIf { it.isNotBlank() }
                            Button(
                                onClick = {
                                    if (requesterPhone != null) {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$requesterPhone"))
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context, "Người dùng chưa cập nhật số điện thoại", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF22C55E),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gọi điện", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Button(
                                onClick = onNavigateToChat,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chat nhóm", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Tab 2: Đội tham gia (Team / Rescuer)
            if (selectedTabIndex == 1) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TeamInfoCard(
                            mission = selectedMission,
                            onCallRescuer = { phone ->
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            },
                            onNavigateToChat = onNavigateToChat,
                            onEmergencyAlert = { showEmergencyDialog = true }
                        )
                    }
                }
            }

            // Tab 3: Cập nhật & Nhật ký (Logs & Timeline)
            if (selectedTabIndex == 2) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ActivityTimelineCard(mission = selectedMission)
                    }
                }
            }
        }
    }

    // Dialog xác nhận hoàn thành cứu hộ
    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hoàn thành nhiệm vụ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Bạn có chắc chắn đã hoàn tất việc cứu hộ nạn nhân an toàn?",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    OutlinedTextField(
                        value = completeNotes,
                        onValueChange = { completeNotes = it },
                        label = { Text("Ghi chú kết quả cứu hộ (nếu có)") },
                        placeholder = { Text("Ví dụ: Đã đưa 2 người an toàn về trạm y tế...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompleteDialog = false
                        viewModel.completeMission(missionId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Xác nhận hoàn tất", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }

    // Dialog Yêu cầu hỗ trợ khẩn cấp / Báo sự cố
    if (showEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hỗ trợ khẩn cấp", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EmergencyRed)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Nếu bạn gặp sự cố nguy hiểm tại hiện trường hoặc cần thêm phương tiện/lực lượng chi viện khẩn cấp, vui lòng liên hệ ngay hotline điều phối:",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gọi 112 - Tìm kiếm cứu nạn thiên tai", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:114")))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                        border = BorderStroke(1.dp, PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gọi 114 - Cứu hộ cứu nạn chuyên nghiệp", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEmergencyDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}

/**
 * Thanh tiến độ 5 bước chuẩn hóa theo quy trình cứu hộ
 */
@Composable
fun MissionProgressStepper(
    currentStatus: String,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "Tiếp nhận",
        "Di chuyển",
        "Đến nơi",
        "Cứu hộ",
        "Hoàn thành"
    )

    val currentIndex = when (currentStatus) {
        MissionStatus.ASSIGNED.name -> 0
        MissionStatus.IN_ROUTE.name -> 1
        MissionStatus.ARRIVED.name -> 2
        MissionStatus.IN_PROGRESS.name -> 3
        MissionStatus.COMPLETED.name -> 4
        else -> 1
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { index, stepName ->
            val isPassed = index < currentIndex
            val isCurrent = index == currentIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left connecting line
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (index == 0) Color.Transparent
                                else if (index <= currentIndex) SuccessGreen
                                else Color(0xFFE2E8F0)
                            )
                    )

                    // Step indicator circle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .shadow(if (isCurrent) 4.dp else 0.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isPassed -> SuccessGreen
                                    isCurrent -> PrimaryBlue
                                    else -> Color(0xFFF1F5F9)
                                }
                            )
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = when {
                                    isPassed -> SuccessGreen
                                    isCurrent -> PrimaryBlue
                                    else -> Color(0xFFCBD5E1)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPassed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }

                    // Right connecting line
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (index == steps.lastIndex) Color.Transparent
                                else if (index < currentIndex) SuccessGreen
                                else Color(0xFFE2E8F0)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stepName,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isCurrent -> PrimaryBlue
                        isPassed -> SuccessGreenDark
                        else -> TextSecondary
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Nút vuốt trượt mũi tên (Swipe-to-Confirm Slider) chống bấm nhầm
 */
@Composable
fun SwipeToConfirmButton(
    text: String,
    actionColor: Color,
    isLoading: Boolean,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 48.dp
    val density = LocalDensity.current
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val thumbPaddingPx = with(density) { 4.dp.toPx() }

    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val maxDragPx = (trackWidthPx - thumbSizePx - (thumbPaddingPx * 2)).coerceAtLeast(1f)
    val progress = (offsetX.value / maxDragPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFF1F5F9))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(28.dp))
            .onSizeChanged { size ->
                trackWidthPx = size.width.toFloat()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track fill based on drag progress
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { (offsetX.value + thumbSizePx + thumbPaddingPx * 2).toDp() })
                .clip(RoundedCornerShape(28.dp))
                .background(actionColor.copy(alpha = 0.18f))
        )

        // Center Hint Text (giữ độ rõ để nhìn xuyên qua nút trượt trong suốt)
        Text(
            text = text,
            color = actionColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
                .alpha(0.88f),
            textAlign = TextAlign.Center
        )

        // Draggable Thumb with Arrow Icon (nút kéo màu hơi trong suốt nhìn thấy chữ bên dưới)
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumbSizeDp)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(actionColor.copy(alpha = 0.42f))
                .border(2.dp, actionColor.copy(alpha = 0.85f), CircleShape)
                .pointerInput(isLoading, maxDragPx) {
                    if (isLoading) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value >= maxDragPx * 0.75f) {
                                    // Snap to end and trigger action
                                    offsetX.animateTo(maxDragPx, tween(150))
                                    onConfirmed()
                                    // Smoothly return after triggering
                                    offsetX.animateTo(0f, tween(300))
                                } else {
                                    // Snap back to start
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = actionColor,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Swipe arrow",
                    tint = actionColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Bản đồ hiện trường thực tế có Marker nạn nhân & Nút chỉ đường
 */
@Composable
fun RealMapCard(
    latitude: Double?,
    longitude: Double?,
    urgency: String?,
    description: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val targetLatLng = remember(latitude, longitude) {
        if (latitude != null && longitude != null) {
            LatLng(latitude, longitude)
        } else {
            LatLng(16.0544, 108.2022) // Default Da Nang
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetLatLng, 15f)
    }

    LaunchedEffect(targetLatLng) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(targetLatLng, 15f)
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bản đồ hiện trường cứu hộ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Surface(
                    color = PrimaryLightBlue,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "GPS vệ tinh",
                        color = PrimaryBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                if (latitude != null && longitude != null) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            mapToolbarEnabled = false,
                            myLocationButtonEnabled = false
                        )
                    ) {
                        Marker(
                            state = MarkerState(position = targetLatLng),
                            title = "Vị trí người gặp nạn",
                            snippet = description ?: "Cứu hộ khẩn cấp",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Không có dữ liệu GPS tọa độ", color = TextSecondary, fontSize = 13.sp)
                    }
                }

                // Nút chỉ đường nhanh qua Google Maps
                Button(
                    onClick = {
                        if (latitude != null && longitude != null) {
                            val uri = Uri.parse("google.navigation:q=$latitude,$longitude")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        } else {
                            Toast.makeText(context, "Chưa có tọa độ nạn nhân để chỉ đường", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chỉ đường", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Thông tin chi tiết người gặp nạn
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VictimInfoCard(
    request: com.example.bai1.rescue.network.RescueMissionRequestInfo?,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Thông tin người cần cứu hộ",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Requester Contact Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PrimaryLightBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                val requesterName = request?.requesterName?.takeIf { it.isNotBlank() } ?: "Người cần cứu hộ"
                val phone = request?.requesterPhone?.takeIf { it.isNotBlank() }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = requesterName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (phone != null) "SĐT: $phone" else "SĐT: Chưa cập nhật",
                        fontSize = 13.sp,
                        color = if (phone != null) TextSecondary else Color.Gray
                    )
                }

                val cardContext = LocalContext.current
                IconButton(
                    onClick = { 
                        if (phone != null) {
                            onCall(phone) 
                        } else {
                            Toast.makeText(cardContext, "Người dùng chưa cập nhật số điện thoại", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (phone != null) Color(0xFF22C55E) else Color(0xFFCBD5E1)
                    )
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Gọi ngay",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Phân loại nhân khẩu học (Người già, trẻ em, tổng số người)
            Text(
                text = "Đối tượng & Số lượng cần hỗ trợ:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DemographicChip(
                    label = "Tổng: ${request?.peopleCount ?: 1} người",
                    icon = Icons.Default.Groups,
                    color = PrimaryBlue,
                    bgColor = PrimaryLightBlue
                )

                val elderly = request?.elderly ?: 0
                if (elderly > 0) {
                    DemographicChip(
                        label = "Người già: $elderly",
                        icon = Icons.Default.Elderly,
                        color = WarningOrange,
                        bgColor = WarningOrangeLight
                    )
                }

                val children = request?.children ?: 0
                if (children > 0) {
                    DemographicChip(
                        label = "Trẻ em: $children",
                        icon = Icons.Default.ChildCare,
                        color = Color(0xFF8E24AA),
                        bgColor = Color(0xFFF3E5F5)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mô tả tình huống (Tình trạng hiện tại, Vật dụng cần thiết, Mô tả thêm)
            Text(
                text = "Mô tả tình huống:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Tình trạng hiện tại
                    val currentCondition = request?.emergencyType?.takeIf { it.isNotBlank() } ?: "Cần cứu hộ khẩn cấp"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(EmergencyRedLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tình trạng hiện tại:",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentCondition,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRedDark
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                    // 2. Vật dụng cần thiết
                    val neededItems = request?.neededItems.orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PrimaryLightBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vật dụng cần thiết:",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (neededItems.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    neededItems.forEach { item ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = PrimaryLightBlue,
                                            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = item,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PrimaryDarkBlue,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Không có yêu cầu vật dụng đặc biệt",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                    // 3. Mô tả thêm (nếu có)
                    val extraDescription = request?.description?.trim().takeIf { !it.isNullOrBlank() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mô tả thêm (nếu có):",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = extraDescription ?: "Không có mô tả thêm",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (extraDescription != null) TextPrimary else Color.Gray,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            // Ảnh hiện trường (nếu có)
            if (!request?.photoUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Hình ảnh hiện trường do nạn nhân gửi:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                AsyncImage(
                    model = request?.photoUrl,
                    contentDescription = "Ảnh hiện trường",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
fun DemographicChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    bgColor: Color
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Tab 2: Thông tin Đội tham gia & Cứu hộ viên
 */
@Composable
fun TeamInfoCard(
    mission: com.example.bai1.rescue.network.RescueMissionResponse?,
    onCallRescuer: (String) -> Unit,
    onNavigateToChat: () -> Unit,
    onEmergencyAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Lực lượng cứu hộ phụ trách",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Rescuer info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mission?.rescuerName ?: "Cứu hộ viên tiếp nhận",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Vai trò: Trưởng nhóm cứu hộ hiện trường",
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                    val phone = mission?.rescuerPhone ?: "Hotline điều phối"
                    Text(
                        text = "Liên hệ: $phone",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions: Chat & Báo sự cố
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToChat,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    border = BorderStroke(1.dp, PrimaryBlue)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chat nội bộ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onEmergencyAlert,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Báo sự cố", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Tab 3: Nhật ký hoạt động & Dòng thời gian
 */
@Composable
fun ActivityTimelineCard(
    mission: com.example.bai1.rescue.network.RescueMissionResponse?,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Nhật ký hoạt động & Mốc thời gian",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            val timelineEvents = listOf(
                TimelineItem(
                    title = "Nhận nhiệm vụ",
                    time = formatMissionDate(mission?.acceptedAt ?: mission?.request?.createdAt),
                    description = "Cứu hộ viên đã tiếp nhận yêu cầu từ trung tâm điều phối",
                    isDone = true
                ),
                TimelineItem(
                    title = "Bắt đầu di chuyển",
                    time = formatMissionDate(mission?.acceptedAt),
                    description = "Đội cứu hộ xuất phát di chuyển đến tọa độ hiện trường",
                    isDone = mission?.status != MissionStatus.ASSIGNED.name
                ),
                TimelineItem(
                    title = "Đã đến hiện trường",
                    time = if (mission?.status in listOf(MissionStatus.ARRIVED.name, MissionStatus.IN_PROGRESS.name, MissionStatus.COMPLETED.name)) "Đã xác nhận" else "Chờ xác nhận",
                    description = "Tiếp cận khu vực xảy ra thiên tai và xác định nạn nhân",
                    isDone = mission?.status in listOf(MissionStatus.ARRIVED.name, MissionStatus.IN_PROGRESS.name, MissionStatus.COMPLETED.name)
                ),
                TimelineItem(
                    title = "Bắt đầu cứu hộ",
                    time = formatMissionDate(mission?.startedAt),
                    description = "Triển khai phương án ứng cứu và chăm sóc ban đầu",
                    isDone = mission?.status in listOf(MissionStatus.IN_PROGRESS.name, MissionStatus.COMPLETED.name)
                ),
                TimelineItem(
                    title = "Hoàn thành nhiệm vụ",
                    time = formatMissionDate(mission?.completedAt),
                    description = "Bàn giao nạn nhân an toàn cho cơ quan y tế / sơ tán",
                    isDone = mission?.status == MissionStatus.COMPLETED.name
                )
            )

            timelineEvents.forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (item.isDone) SuccessGreen else Color(0xFFCBD5E1)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.isDone) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        if (index < timelineEvents.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(48.dp)
                                    .background(if (item.isDone) SuccessGreen else Color(0xFFE2E8F0))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (item.isDone) TextPrimary else TextSecondary
                            )
                            Text(
                                text = item.time,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

data class TimelineItem(
    val title: String,
    val time: String,
    val description: String,
    val isDone: Boolean
)

fun formatMissionDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "--"
    return try {
        if (raw.contains("T")) {
            val parts = raw.split("T")
            val datePart = parts[0]
            val timePart = parts[1].substringBefore(".").take(5)
            "$datePart $timePart"
        } else {
            raw.take(16)
        }
    } catch (_: Exception) {
        raw
    }
}

fun getMissionStatusLabel(status: String): String {
    return when (status) {
        MissionStatus.ASSIGNED.name -> "Đã tiếp nhận"
        MissionStatus.IN_ROUTE.name -> "Đang di chuyển"
        MissionStatus.ARRIVED.name -> "Đã đến hiện trường"
        MissionStatus.IN_PROGRESS.name -> "Đang cứu hộ"
        MissionStatus.COMPLETED.name -> "Đã hoàn thành"
        MissionStatus.CANCELLED.name -> "Đã hủy"
        MissionStatus.FAILED.name -> "Thất bại"
        else -> status
    }
}

fun getMissionStatusBg(status: String): Color {
    return when (status) {
        MissionStatus.ASSIGNED.name -> PrimaryBlue
        MissionStatus.IN_ROUTE.name -> Color(0xFF0284C7)
        MissionStatus.ARRIVED.name -> WarningOrange
        MissionStatus.IN_PROGRESS.name -> Color(0xFFEA580C)
        MissionStatus.COMPLETED.name -> SuccessGreen
        MissionStatus.CANCELLED.name -> TextSecondary
        MissionStatus.FAILED.name -> EmergencyRed
        else -> PrimaryBlue
    }
}
