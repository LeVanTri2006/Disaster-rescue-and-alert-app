package com.example.bai1.presentation.rescue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.data.model.UserRole
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.AppBottomNavigation
import com.example.bai1.presentation.components.AppTopBar
import com.example.bai1.rescue.network.MissionStatus
import com.example.bai1.rescue.network.RescueMissionResponse
import com.example.bai1.rescue.presentation.RescueMissionViewModel
import com.example.bai1.ui.theme.*

@Composable
fun RescueMissionListScreen(
    viewModel: RescueMissionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.bai1.rescue.presentation.RescueMissionViewModelFactory()
    ),
    locationViewModel: com.example.bai1.presentation.location.CurrentLocationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.bai1.presentation.location.CurrentLocationViewModelFactory(androidx.compose.ui.platform.LocalContext.current)
    ),
    onNavigateToDetail: (String) -> Unit,
    onNavigateToRequestDetail: (String) -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    val userRole by DisasterRepository.currentUserRole.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    val missions by viewModel.missions.collectAsState()
    val nearbyRequests by viewModel.nearbyRequests.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val locationState by locationViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadMissions()
        locationViewModel.refresh()
    }
    
    LaunchedEffect(locationState) {
        if (locationState is com.example.bai1.sos.presentation.LocationUiState.Success) {
            val loc = locationState as com.example.bai1.sos.presentation.LocationUiState.Success
            viewModel.loadNearbyRequests(loc.latitude, loc.longitude, 50000)
        } else if (locationState is com.example.bai1.sos.presentation.LocationUiState.Error) {
            // Fallback for demo if location fails
            viewModel.loadNearbyRequests(21.0277644, 105.8341598, 50000)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadMissions()
                if (locationState is com.example.bai1.sos.presentation.LocationUiState.Success) {
                    val loc = locationState as com.example.bai1.sos.presentation.LocationUiState.Success
                    viewModel.loadNearbyRequests(loc.latitude, loc.longitude, 50000)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val processingMissions = missions.filter { it.status == MissionStatus.IN_PROGRESS.name || it.status == MissionStatus.IN_ROUTE.name || it.status == MissionStatus.ASSIGNED.name }
    val completedMissions = missions.filter { it.status == MissionStatus.COMPLETED.name || it.status == MissionStatus.CANCELLED.name || it.status == MissionStatus.FAILED.name }

    val tabs = listOf("Gần đây (${nearbyRequests.size})", "Nhiệm vụ của tôi (${processingMissions.size})", "Hoàn thành (${completedMissions.size})")

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Nhiệm vụ cứu hộ",
                showBackButton = true, // Design has a back button, but it's a bottom nav tab. Let's include it.
                onBackClick = { onBottomNavNavigate("home") } // Back to home
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "mission_list",
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
        ) {
            // TabRow
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

            // Missions List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (selectedTabIndex == 0) {
                        items(nearbyRequests.size) { index ->
                            val req = nearbyRequests[index]
                            
                            val distanceText = req.distance?.let { d ->
                                if (d < 1000) "Cách ~${d.toInt()}m"
                                else "Cách ~${String.format(java.util.Locale.US, "%.1f", d / 1000)}km"
                            } ?: "Khoảng cách không rõ"
                            
                            val timeText = req.createdAt?.let {
                                try {
                                    // Parse standard ISO-8601 string to something shorter if possible, or just use raw
                                    // Given it's a timestamp, just take the first part or default to "Vừa xong"
                                    if (it.length >= 16) it.substring(0, 16).replace("T", " ") else it
                                } catch (e: Exception) { "Vừa xong" }
                            } ?: "Vừa xong"

                            val (urgencyLabel, urgencyColor) = when (req.urgency.uppercase()) {
                                "CRITICAL" -> "Nguy cấp" to EmergencyRed
                                "HIGH" -> "Khẩn cấp" to EmergencyRed
                                "MEDIUM" -> "Trung bình" to WarningOrange
                                "LOW" -> "Thấp" to PrimaryBlue
                                else -> "Cần cứu hộ" to EmergencyRed
                            }

                            MissionCard(
                                statusText = urgencyLabel,
                                statusColor = urgencyColor,
                                title = req.description ?: "Yêu cầu cứu hộ",
                                location = if (req.latitude != null) "Lat: ${req.latitude}, Lng: ${req.longitude}" else "Không rõ vị trí",
                                people = "${req.peopleCount} người",
                                time = timeText,
                                distance = distanceText,
                                buttonText = "Chi tiết",
                                onButtonClick = { req.sosEventId?.let { onNavigateToRequestDetail(it) } }
                            )
                        }
                    } else {
                        val currentList = if (selectedTabIndex == 1) processingMissions else completedMissions
                        items(currentList.size) { index ->
                            val mission = currentList[index]
                        
                        val statusText = when (mission.status) {
                            MissionStatus.ASSIGNED.name -> "Sắp tới"
                            MissionStatus.IN_ROUTE.name -> "Đang di chuyển"
                            MissionStatus.IN_PROGRESS.name -> "Đang thực hiện"
                            MissionStatus.COMPLETED.name -> "Đã hoàn thành"
                            MissionStatus.CANCELLED.name -> "Đã hủy"
                            else -> "Thất bại"
                        }
                        
                        val statusColor = when (mission.status) {
                            MissionStatus.ASSIGNED.name -> WarningOrange
                            MissionStatus.IN_ROUTE.name -> PrimaryBlue
                            MissionStatus.IN_PROGRESS.name -> EmergencyRed
                            MissionStatus.COMPLETED.name -> SuccessGreen
                            else -> TextSecondary
                        }
                        
                        val peopleText = "${mission.request?.peopleCount ?: 0} người"
                        // Try to extract some fake values if missing from request
                        val title = mission.request?.description ?: "Nhiệm vụ cứu hộ"
                        val location = if (mission.request?.latitude != null) {
                            "Lat: ${mission.request.latitude}, Lng: ${mission.request.longitude}"
                        } else {
                            "Không rõ vị trí"
                        }
                        
                        MissionCard(
                            statusText = statusText,
                            statusColor = statusColor,
                            title = title,
                            location = location,
                            people = peopleText,
                            time = mission.startedAt ?: mission.acceptedAt ?: "Chưa xác định",
                            distance = "Không xác định", // Mocking distance for now
                            buttonText = "Chi tiết",
                            onButtonClick = { onNavigateToDetail(mission.id) }
                        )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissionCard(
    statusText: String,
    statusColor: Color,
    title: String,
    location: String,
    people: String,
    time: String,
    distance: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Using border instead of elevation for flat design
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left Color Bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Status Badge
                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Info Rows
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(location, fontSize = 13.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Home, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(people, fontSize = 13.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(time, fontSize = 13.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(distance, fontSize = 13.sp, color = TextSecondary)
                    }

                    Button(
                        onClick = onButtonClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(buttonText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
