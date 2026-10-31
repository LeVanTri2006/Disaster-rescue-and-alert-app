package com.example.bai1.presentation.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.sos.domain.SosRescueInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Colors based on Reference ---
val PrimaryBlue = Color(0xFF1976D2)
val DarkBlue = Color(0xFF0D47A1)
val EmergencyRed = Color(0xFFE53935)
val EmergencyRedLight = Color(0xFFFFEBEE)
val WarningOrange = Color(0xFFFB8C00)
val SuccessGreen = Color(0xFF43A047)
val BgColor = Color(0xFFF5F8FC)
val TextColor = Color(0xFF1E293B)
val SecondaryColor = Color(0xFF64748B)
val LightBlueChip = Color(0xFFE3F2FD)
val MedicalBg = Color(0xFFFFF8E1) // Light yellowish
val PurpleColor = Color(0xFF8E24AA)
val DividerColor = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosDetailScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    viewModel: SosDetailViewModel = viewModel(
        factory = SosDetailViewModelFactory(SosAppDependencies.sosRepository, eventId)
    )
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val showNotImplementedSnackbar: () -> Unit = {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Chức năng đang được phát triển")
        }
    }

    val successState = state as? SosDetailState.Success
    LaunchedEffect(successState?.acceptSuccess, successState?.acceptError) {
        if (successState?.acceptSuccess == true) {
            snackbarHostState.showSnackbar("Nhận nhiệm vụ thành công!")
            onNavigateBack()
        } else if (successState?.acceptError != null) {
            val errorMsg = if (successState.acceptError.contains("409")) "Nhiệm vụ này đã được người khác nhận." else successState.acceptError
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearAcceptError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết SOS", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (state is SosDetailState.Success) {
                ActionBottomBar(
                    state = state as SosDetailState.Success,
                    onClick = showNotImplementedSnackbar,
                    onAccept = { viewModel.acceptMission() }
                )
            }
        },
        containerColor = BgColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val s = state) {
                is SosDetailState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryBlue)
                }
                is SosDetailState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = s.message, color = EmergencyRed, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                            Text("Quay lại", color = Color.White)
                        }
                    }
                }
                is SosDetailState.Success -> {
                    val sos = s.sos
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EmergencyStatusCard(sos)
                        TimeCard(sos.createdAt)
                        PeopleCard(sos)
                        ConditionCard(sos.condition)
                        NeedsCard(sos.neededItems)
                        DescriptionCard(sos.description)
                        LocationCard(sos.latitude, sos.longitude, showNotImplementedSnackbar)
                        ImageCard(sos.photoUri, sos.photos)
                        
                        // Extra bottom spacing to ensure content isn't cramped near the bottom bar
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun CardHeader(icon: ImageVector, title: String, iconTint: Color = PrimaryBlue, titleColor: Color = PrimaryBlue) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = titleColor
        )
    }
}

@Composable
fun EmergencyStatusCard(sos: SosRescueInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EmergencyRedLight),
        border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "SOS ĐANG HOẠT ĐỘNG",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = EmergencyRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Mức độ: ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = EmergencyRed
                    )
                    Surface(
                        color = EmergencyRed,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = if (sos.urgency.name.isNotBlank()) sos.urgency.name.uppercase() else "CHƯA RÕ",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Cần hỗ trợ khẩn cấp. Vui lòng liên hệ và đến hiện trường sớm nhất.",
                    fontSize = 12.sp,
                    color = SecondaryColor
                )
            }
        }
    }
}

@Composable
fun TimeCard(createdAt: Long) {
    val timeStr = if (createdAt > 0L) {
        SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale.getDefault()).format(Date(createdAt))
    } else "Không có thông tin"
    
    // Calculate hours ago roughly for UI presentation
    val hoursAgo = if (createdAt > 0L) {
        val diff = System.currentTimeMillis() - createdAt
        val hours = diff / (1000 * 60 * 60)
        if (hours > 0) "$hours giờ trước" else "Vừa xong"
    } else ""

    InfoCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                CardHeader(icon = Icons.Filled.Schedule, title = "Thời gian gửi", iconTint = PrimaryBlue)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = timeStr, fontSize = 16.sp, color = TextColor, fontWeight = FontWeight.Bold)
            }
            if (hoursAgo.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = SecondaryColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = hoursAgo, fontSize = 12.sp, color = SecondaryColor)
                }
            }
        }
    }
}

@Composable
fun PeopleCard(sos: SosRescueInfo) {
    InfoCard {
        CardHeader(icon = Icons.Filled.People, title = "Người cần hỗ trợ")
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PersonCountItem(Modifier.weight(1f), "Người lớn", Icons.Filled.Person, PrimaryBlue, sos.adults)
            PersonCountItem(Modifier.weight(1f), "Trẻ em", Icons.Filled.ChildCare, WarningOrange, sos.children)
            PersonCountItem(Modifier.weight(1f), "Cao tuổi", Icons.Filled.Elderly, PurpleColor, sos.elderly)
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tổng số người", fontSize = 15.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
            Text(
                "${sos.totalPeople}",
                fontSize = 20.sp,
                color = PrimaryBlue,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun PersonCountItem(modifier: Modifier, label: String, icon: ImageVector, color: Color, count: Int) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
        color = Color.White
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, fontSize = 11.sp, color = SecondaryColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(count.toString(), fontSize = 22.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ConditionCard(condition: String) {
    InfoCard(containerColor = MedicalBg) {
        CardHeader(icon = Icons.Filled.MedicalServices, title = "Tình trạng y tế", iconTint = EmergencyRed, titleColor = TextColor)
        Spacer(modifier = Modifier.height(8.dp))
        val displayCondition = if (condition.isNotBlank()) condition else "Không có thông tin"
        Text(text = displayCondition, fontSize = 15.sp, color = TextColor, modifier = Modifier.padding(start = 30.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeedsCard(neededItems: Set<String>) {
    InfoCard {
        CardHeader(icon = Icons.Filled.LocalHospital, title = "Nhu cầu hỗ trợ")
        Spacer(modifier = Modifier.height(12.dp))
        if (neededItems.isEmpty()) {
            Text(text = "Không có thông tin", fontSize = 14.sp, color = SecondaryColor, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 30.dp))
        } else {
            FlowRow(
                modifier = Modifier.padding(start = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                neededItems.filter { it.isNotBlank() }.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = LightBlueChip
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 13.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DescriptionCard(description: String) {
    InfoCard {
        CardHeader(icon = Icons.Filled.Description, title = "Mô tả thêm")
        Spacer(modifier = Modifier.height(8.dp))
        if (description.isNotBlank()) {
            Text(text = description, fontSize = 15.sp, color = TextColor, modifier = Modifier.padding(start = 30.dp))
        } else {
            Text(text = "Không có mô tả", fontSize = 14.sp, color = SecondaryColor, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 30.dp))
        }
    }
}

@Composable
fun LocationCard(latitude: Double?, longitude: Double?, onClickMap: () -> Unit) {
    InfoCard {
        CardHeader(icon = Icons.Filled.LocationOn, title = "Vị trí SOS")
        Spacer(modifier = Modifier.height(12.dp))
        if (latitude != null && longitude != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Vĩ độ", color = SecondaryColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$latitude", color = TextColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(DividerColor))
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Kinh độ", color = SecondaryColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$longitude", color = TextColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                onClick = onClickMap,
                modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                shape = RoundedCornerShape(8.dp),
                color = LightBlueChip
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xem trên bản đồ", color = PrimaryBlue, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Text(text = "Không có thông tin", fontSize = 14.sp, color = SecondaryColor, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 30.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ImageCard(photoUri: String?, photos: List<String>?) {
    val displayPhotos = if (!photos.isNullOrEmpty()) {
        photos
    } else if (!photoUri.isNullOrBlank()) {
        listOf(photoUri)
    } else {
        emptyList()
    }

    InfoCard {
        CardHeader(icon = Icons.Filled.Image, title = "Hình ảnh từ người gửi")
        Spacer(modifier = Modifier.height(12.dp))
        
        if (displayPhotos.isEmpty()) {
            Text(
                text = "Chưa có hình ảnh",
                fontSize = 14.sp,
                color = SecondaryColor,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 30.dp)
            )
        } else if (displayPhotos.size == 1) {
            SingleImage(displayPhotos[0])
        } else {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { displayPhotos.size })
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    SingleImage(displayPhotos[page])
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(displayPhotos.size) { index ->
                        val color = if (pagerState.currentPage == index) PrimaryBlue else DividerColor
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SingleImage(url: String) {
    coil.compose.SubcomposeAsyncImage(
        model = url,
        contentDescription = "Ảnh đính kèm SOS",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE2E8F0)),
        loading = {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        },
        error = {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = SecondaryColor, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Không thể tải hình ảnh",
                        fontSize = 12.sp,
                        color = SecondaryColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    )
}

@Composable
fun ActionBottomBar(state: SosDetailState.Success, onClick: () -> Unit, onAccept: () -> Unit) {
    Surface(
        color = Color.White,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            if (state.isRescuer) {
                if (state.rescueRequest == null) {
                    Text("Yêu cầu cứu hộ này chưa sẵn sàng để nhận.", color = SecondaryColor, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (state.rescueRequest.status == "PENDING") {
                    Button(
                        onClick = onAccept,
                        enabled = !state.isAccepting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (state.isAccepting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("NHẬN NHIỆM VỤ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (state.rescueRequest.status == "ASSIGNED") {
                    Text("Nhiệm vụ này đã có người nhận.", color = WarningOrange, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Text("Nhiệm vụ đang được thực hiện hoặc đã hoàn thành.", color = SuccessGreen, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CHỈ ĐƯỜNG", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue)
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NHẮN TIN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GỌI ĐIỆN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
