package com.example.bai1.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bai1.data.model.UserRole
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.AppBottomNavigation
import com.example.bai1.presentation.location.AndroidLocationProvider
import com.example.bai1.presentation.location.LocationResult
import com.example.bai1.ui.theme.*
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.rescue.network.RescueMissionApiClient
import com.example.bai1.rescue.repository.DefaultRescueMissionRepository
import com.example.bai1.rescue.network.OkHttpRescueMissionApiClient
import com.example.bai1.sos.network.SharedPreferencesAccessTokenProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val locationProvider = remember { AndroidLocationProvider(context.applicationContext) }
    val userRole by DisasterRepository.currentUserRole.collectAsState()
    val markers by viewModel.markers.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val daNang = LatLng(16.0544, 108.2022)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(daNang, 12f)
    }
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            locationProvider.getCurrentLocation { result ->
                if (result is LocationResult.Success) {
                    val target = LatLng(result.latitude, result.longitude)
                    myLocation = target
                    coroutineScope.launch {
                        try {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(target, 16f),
                                durationMs = 800
                            )
                        } catch (_: Exception) {
                            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target, 16f))
                        }
                    }
                }
            }
        }
    }

    fun focusOnMyLocation() {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        locationProvider.getCurrentLocation { result ->
            if (result is LocationResult.Success) {
                val target = LatLng(result.latitude, result.longitude)
                myLocation = target
                coroutineScope.launch {
                    try {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(target, 16f),
                            durationMs = 800
                        )
                    } catch (_: Exception) {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target, 16f))
                    }
                }
            }
        }
    }
    
    // Fetch nearby requests when map opens. For Da Nang: 16.0544, 108.2022
    LaunchedEffect(Unit) {
        viewModel.loadNearbyRequests(16.0544, 108.2022)
    }

    var selectedMarker by remember { mutableStateOf<MapRescueMarker?>(null) }


    var isMapLoaded by remember { mutableStateOf(false) }
    var hasAutoCentered by remember { mutableStateOf(false) }

    fun focusOnMarkers() {
        if (markers.isEmpty()) return
        coroutineScope.launch {
            try {
                if (markers.size == 1) {
                    val first = markers.first()
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 14f),
                        durationMs = 1000
                    )
                } else {
                    val builder = LatLngBounds.builder()
                    markers.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(builder.build(), 120),
                        durationMs = 1000
                    )
                }
            } catch (e: Exception) {
                val first = markers.first()
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 14f)
                )
            }
        }
    }

    LaunchedEffect(markers, isMapLoaded) {
        if (markers.isNotEmpty() && isMapLoaded && !hasAutoCentered) {
            hasAutoCentered = true
            focusOnMarkers()
        }
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
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = true
                ),
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                onMapLoaded = {
                    isMapLoaded = true
                }
            ) {
                markers.forEach { marker ->
                    val pos = LatLng(marker.latitude, marker.longitude)
                    val iconHue = when (marker.urgency.trim().uppercase()) {
                        "CRITICAL", "NGUY CẤP", "ĐẶC BIỆT NGUY CẤP", "ĐẶC BIỆT KHẨN CẤP" -> BitmapDescriptorFactory.HUE_RED
                        "HIGH", "CAO", "KHẨN CẤP" -> BitmapDescriptorFactory.HUE_RED
                        "MEDIUM", "TRUNG BÌNH" -> BitmapDescriptorFactory.HUE_ORANGE
                        "LOW", "THẤP" -> BitmapDescriptorFactory.HUE_AZURE
                        else -> if (marker.type == MarkerType.SOS) BitmapDescriptorFactory.HUE_RED else BitmapDescriptorFactory.HUE_ORANGE
                    }
                    
                    Marker(
                        state = MarkerState(position = pos),
                        title = marker.title,
                        snippet = marker.snippet,
                        icon = BitmapDescriptorFactory.defaultMarker(iconHue),
                        onClick = {
                            selectedMarker = marker
                            false // return false so info window shows and camera moves
                        }
                    )
                }
            }

            // Overlay top bar
            Surface(
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = markers.isNotEmpty()) { focusOnMarkers() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Bản đồ cứu hộ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Hiển thị ${markers.size} tín hiệu khẩn cấp" + if (markers.isNotEmpty()) " (Chạm để xem vị trí)" else "",
                        fontSize = 14.sp,
                        color = if (markers.isNotEmpty()) EmergencyRed else TextSecondary,
                        fontWeight = if (markers.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            // Vị trí của tôi (Dành cho cả người cứu hộ và người dùng)
            SmallFloatingActionButton(
                onClick = { focusOnMyLocation() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (selectedMarker != null) 290.dp else 112.dp, end = 16.dp),
                containerColor = Color.White,
                contentColor = PrimaryBlue,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Vị trí của tôi",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Overlay Bottom Sheet for Selected Marker
            selectedMarker?.let { marker ->
                val urgencyColor = when (marker.urgency.trim().uppercase()) {
                    "CRITICAL", "NGUY CẤP", "ĐẶC BIỆT NGUY CẤP", "ĐẶC BIỆT KHẨN CẤP" -> EmergencyRed
                    "HIGH", "CAO", "KHẨN CẤP" -> EmergencyRed
                    "MEDIUM", "TRUNG BÌNH" -> WarningOrange
                    "LOW", "THẤP" -> PrimaryBlue
                    else -> EmergencyRed
                }

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
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = urgencyColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "MỨC ĐỘ KHẨN CẤP: ",
                                fontWeight = FontWeight.Bold,
                                color = urgencyColor,
                                fontSize = 15.sp
                            )
                            Surface(
                                color = urgencyColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = marker.urgency,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (marker.createdAt > 0L) {
                            val timeStr = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(marker.createdAt))
                            Text("Thời gian: $timeStr", fontSize = 14.sp, color = TextPrimary)
                        }
                        
                        Text("Người cần cứu: ${marker.totalPeople}", fontSize = 14.sp, color = TextPrimary)
                        
                        if (marker.condition.isNotBlank()) {
                            Text("Tình trạng: ${marker.condition}", fontSize = 14.sp, color = TextPrimary)
                        }
                        if (marker.neededItems.isNotEmpty()) {
                            Text("Cần hỗ trợ: ${marker.neededItems.joinToString(", ")}", fontSize = 14.sp, color = TextPrimary)
                        }
                        if (marker.description.isNotBlank()) {
                            Text("Mô tả: ${marker.description}", fontSize = 14.sp, color = TextPrimary)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                if (marker.eventId != null) {
                                    onNavigateToSosDetail(marker.eventId)
                                } else if (marker.requestId != null) {
                                    onNavigateToDetail(marker.requestId)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("XEM CHI TIẾT", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
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
