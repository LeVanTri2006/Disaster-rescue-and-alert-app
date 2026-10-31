package com.example.bai1.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bai1.data.model.UserRole
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.AppBottomNavigation
import com.example.bai1.ui.theme.*
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.rescue.network.RescueMissionApiClient
import com.example.bai1.rescue.repository.DefaultRescueMissionRepository
import com.example.bai1.rescue.network.OkHttpRescueMissionApiClient
import com.example.bai1.sos.network.SharedPreferencesAccessTokenProvider
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisasterMapScreen(
    viewModel: DisasterMapViewModel = viewModel(
        factory = DisasterMapViewModelFactory(
            SosAppDependencies.sosRepository,
            com.example.bai1.rescue.RescueMissionAppDependencies.rescueMissionRepository
        )
    ),
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSosDetail: (String) -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    val userRole by DisasterRepository.currentUserRole.collectAsState()
    val markers by viewModel.markers.collectAsState()
    
    // Fetch nearby requests when map opens. For Da Nang: 16.0544, 108.2022
    LaunchedEffect(Unit) {
        viewModel.loadNearbyRequests(16.0544, 108.2022)
    }

    var selectedMarker by remember { mutableStateOf<MapRescueMarker?>(null) }
    val daNang = LatLng(16.0544, 108.2022)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(daNang, 12f)
    }

    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "map",
                userRole = userRole,
                onNavigate = onBottomNavNavigate
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(myLocationButtonEnabled = true),
                properties = MapProperties(isMyLocationEnabled = false) // True requires location permissions
            ) {
                markers.forEach { marker ->
                    val pos = LatLng(marker.latitude, marker.longitude)
                    val iconColor = if (marker.type == MarkerType.SOS) BitmapDescriptorFactory.HUE_RED else BitmapDescriptorFactory.HUE_ORANGE
                    
                    Marker(
                        state = MarkerState(position = pos),
                        title = marker.title,
                        snippet = marker.snippet,
                        icon = BitmapDescriptorFactory.defaultMarker(iconColor),
                        onClick = {
                            selectedMarker = marker
                            false // return false so info window shows and camera moves
                        }
                    )
                }
            }

            // Overlay top bar
            Surface(
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Bản đồ cứu hộ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Hiển thị ${markers.size} tín hiệu khẩn cấp",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }

            // Overlay Bottom Sheet for Selected Marker
            selectedMarker?.let { marker ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tintColor = if (marker.type == MarkerType.SOS) EmergencyRed else PrimaryBlue
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = tintColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "MỨC ĐỘ KHẨN CẤP: ${marker.urgency}",
                                fontWeight = FontWeight.Bold,
                                color = tintColor,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (marker.type == MarkerType.SOS && marker.createdAt > 0L) {
                            val timeStr = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(marker.createdAt))
                            Text("Thời gian: $timeStr", fontSize = 14.sp, color = TextPrimary)
                        }
                        
                        Text("Người cần cứu: ${marker.totalPeople}", fontSize = 14.sp, color = TextPrimary)
                        
                        if (marker.condition.isNotBlank()) {
                            Text("Tình trạng: ${marker.condition}", fontSize = 14.sp, color = TextPrimary)
                        }
                        if (marker.neededItems.isNotEmpty()) {
                            Text("Cần hỗ trợ: ${marker.neededItems.joinToString()}", fontSize = 14.sp, color = TextPrimary)
                        }
                        if (marker.description.isNotBlank()) {
                            Text("Mô tả: ${marker.description}", fontSize = 14.sp, color = TextPrimary)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Chi hien thi nut Xem Chi Tiet
                        if (marker.eventId != null) {
                            Button(
                                onClick = { onNavigateToSosDetail(marker.eventId) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("XEM CHI TIẾT", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        TextButton(
                            onClick = { selectedMarker = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Đóng")
                        }
                    }
                }
            }
}
    }
}
