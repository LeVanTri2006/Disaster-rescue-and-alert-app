package com.example.bai1.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.bai1.presentation.location.LocationResult
import com.example.bai1.ui.theme.*
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

@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class)
@Composable
fun DisasterMapScreen(
    viewModel: DisasterMapViewModel,
    locationProvider: com.example.bai1.presentation.location.LocationProvider,
    requestDeviceLocation: Boolean = true,
    onMapReady: (com.google.android.gms.maps.GoogleMap) -> Unit = {},
    onTilesLoaded: () -> Unit = {},
    onMarkerSelected: (MapRescueMarker, com.google.android.gms.maps.model.Marker) -> Unit = { _, _ -> },
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSosDetail: (String) -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val userRole by DisasterRepository.currentUserRole.collectAsState()
    val markers by viewModel.markers.collectAsState()
    val routeState by viewModel.routeState.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            requestDeviceLocation && (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED)
        )
    }

    val daNang = LatLng(16.0544, 108.2022)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(daNang, 12f)
    }
    var myLocation by remember { mutableStateOf(DisasterMapSharedState.lastKnownRescuerLocation) }
    val coroutineScope = rememberCoroutineScope()

    // Trạng thái dẫn đường thời gian thực (Navigation Mode)
    var isNavigating by remember { mutableStateOf(false) }
    var currentBearing by remember { mutableStateOf(0f) }
    var vehicleType by remember { mutableStateOf(NavigationVehicleType.CAR) }
    var isCameraFollowEnabled by remember { mutableStateOf(true) }
    var remainingDistanceMeters by remember { mutableStateOf<Double?>(null) }
    var estimatedTimeMinutes by remember { mutableStateOf<Int?>(null) }
    var isSimulatingMovement by remember { mutableStateOf(false) }

    LaunchedEffect(routeState) {
        if (routeState == null) {
            isNavigating = false
            isSimulatingMovement = false
            remainingDistanceMeters = null
            estimatedTimeMinutes = null
        } else {
            val dist = routeState?.totalDistanceMeters ?: 0.0
            remainingDistanceMeters = dist
            estimatedTimeMinutes = (((dist / 1000.0) / 30.0) * 60).toInt().coerceAtLeast(1)
        }
    }

    // Lắng nghe cập nhật GPS liên tục thời gian thực khi đang ở chế độ dẫn đường
    DisposableEffect(isNavigating) {
        if (!isNavigating) return@DisposableEffect onDispose {}

        val cancelUpdates = locationProvider.requestLocationUpdates(intervalMs = 1500L) { result ->
            if (result is LocationResult.Success) {
                val newLoc = LatLng(result.latitude, result.longitude)
                val prevLoc = myLocation

                if (prevLoc != null && (prevLoc.latitude != newLoc.latitude || prevLoc.longitude != newLoc.longitude)) {
                    val b = if (result.bearing > 0f) result.bearing else computeBearing(prevLoc, newLoc)
                    if (b > 0f) currentBearing = b
                }
                myLocation = newLoc
                DisasterMapSharedState.lastKnownRescuerLocation = newLoc

                routeState?.polylinePoints?.let { pts ->
                    if (pts.isNotEmpty()) {
                        val rem = calculateRemainingDistance(newLoc, pts)
                        remainingDistanceMeters = rem
                        val speedKmh = if (result.speedMps > 2f) (result.speedMps * 3.6).toDouble() else 30.0
                        estimatedTimeMinutes = (((rem / 1000.0) / speedKmh) * 60.0).toInt().coerceAtLeast(1)
                    }
                }

                if (isCameraFollowEnabled) {
                    coroutineScope.launch {
                        try {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(newLoc)
                                        .zoom(17.5f)
                                        .tilt(38f)
                                        .bearing(currentBearing)
                                        .build()
                                ),
                                durationMs = 600
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        onDispose {
            cancelUpdates()
        }
    }

    // Chế độ chạy thử mô phỏng di chuyển (tiện lợi khi test trong phòng)
    LaunchedEffect(isSimulatingMovement) {
        if (!isSimulatingMovement) return@LaunchedEffect
        val pts = routeState?.polylinePoints ?: return@LaunchedEffect
        for (i in 0 until pts.size) {
            if (!isSimulatingMovement || !isNavigating) break
            val pt = pts[i]
            val prev = myLocation
            if (prev != null) {
                val b = computeBearing(prev, pt)
                if (b > 0f) currentBearing = b
            }
            myLocation = pt
            DisasterMapSharedState.lastKnownRescuerLocation = pt
            remainingDistanceMeters = calculateRemainingDistance(pt, pts)
            estimatedTimeMinutes = (((remainingDistanceMeters ?: 0.0) / 1000.0) / 30.0 * 60).toInt().coerceAtLeast(1)

            if (isCameraFollowEnabled) {
                try {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(pt)
                                .zoom(17.5f)
                                .tilt(38f)
                                .bearing(currentBearing)
                                .build()
                        ),
                        durationMs = 600
                    )
                } catch (_: Exception) {}
            }
            kotlinx.coroutines.delay(800)
        }
        isSimulatingMovement = false
    }

    // ── CHẾ ĐỘ THEO DÕI CỨU HỘ CHỈ DÀNH CHO BẢN ĐỒ NGƯỜI DÂN (CITIZEN USER) ──
    // Cứu hộ viên (RESCUER) tuyệt đối không bật chế độ này mà chỉ theo dõi tín hiệu SOS cập nhật thời gian thực
    val isCitizenUser = (userRole == UserRole.USER)
    var isCitizenTrackingMode by remember(userRole) {
        mutableStateOf(isCitizenUser && (DisasterMapSharedState.isCitizenTrackingMode || DisasterMapSharedState.citizenTrackingEventId != null))
    }

    LaunchedEffect(userRole) {
        if (userRole == UserRole.RESCUER) {
            isCitizenTrackingMode = false
            DisasterMapSharedState.isCitizenTrackingMode = false
        }
    }

    var citizenTrackingSosId by remember {
        mutableStateOf(DisasterMapSharedState.citizenTrackingEventId)
    }
    var liveVictimLocation by remember {
        mutableStateOf(DisasterMapSharedState.lastKnownVictimLocation ?: myLocation)
    }
    var liveRescuerLocation by remember {
        mutableStateOf<LatLng?>(null)
    }
    var citizenProgressStep by remember { mutableIntStateOf(1) } // 0: Tiếp nhận, 1: Di chuyển, 2: Đến nơi, 3: Cứu hộ, 4: Hoàn thành
    var rescuerBearing by remember { mutableFloatStateOf(0f) }
    var isCitizenSimulatingMovement by remember { mutableStateOf(true) }
    var citizenRemainingDist by remember { mutableStateOf<Double?>(null) }
    var citizenEtaMinutes by remember { mutableStateOf<Int?>(null) }

    // Tự động khởi tạo hoặc đồng bộ vị trí người gửi tín hiệu và người cứu hộ khi ở chế độ Citizen Tracking
    LaunchedEffect(isCitizenTrackingMode, markers, myLocation) {
        if (!isCitizenTrackingMode) return@LaunchedEffect

        // 1. Xác định vị trí người gửi SOS (Victim Location)
        var victim = liveVictimLocation ?: DisasterMapSharedState.lastKnownVictimLocation
        if (victim == null && citizenTrackingSosId != null) {
            val matched = markers.find { it.eventId == citizenTrackingSosId || it.requestId == citizenTrackingSosId }
            if (matched != null) {
                victim = LatLng(matched.latitude, matched.longitude)
            }
        }
        if (victim == null && markers.isNotEmpty()) {
            val firstSos = markers.firstOrNull { it.type == MarkerType.SOS } ?: markers.first()
            victim = LatLng(firstSos.latitude, firstSos.longitude)
            citizenTrackingSosId = firstSos.eventId ?: firstSos.requestId
        }
        if (victim == null) {
            victim = myLocation ?: LatLng(16.0544, 108.2022)
        }
        liveVictimLocation = victim
        DisasterMapSharedState.lastKnownVictimLocation = victim

        // 2. Xác định vị trí đội cứu hộ (Rescuer Location)
        if (liveRescuerLocation == null) {
            val cachedRescuer = DisasterMapSharedState.lastKnownRescuerLocation
            val rescuer = if (cachedRescuer != null && computeDistanceMeters(cachedRescuer, victim) > 150.0) {
                cachedRescuer
            } else {
                LatLng(16.0685, 108.2150)
            }
            liveRescuerLocation = rescuer
            DisasterMapSharedState.lastKnownRescuerLocation = rescuer

            viewModel.computeRouteBetweenPoints(
                startPoint = rescuer,
                destinationPoint = victim,
                isSimulatedRescuerLocation = false
            )
        }
    }

    // Lắng nghe cập nhật GPS liên tục thời gian thực cho vị trí người gửi tín hiệu (Victim GPS)
    DisposableEffect(isCitizenTrackingMode) {
        if (!isCitizenTrackingMode) return@DisposableEffect onDispose {}

        val cancelVictimGps = locationProvider.requestLocationUpdates(intervalMs = 1500L) { res ->
            if (res is LocationResult.Success) {
                val newLoc = LatLng(res.latitude, res.longitude)
                liveVictimLocation = newLoc
                DisasterMapSharedState.lastKnownVictimLocation = newLoc
            }
        }
        onDispose {
            cancelVictimGps()
        }
    }

    // Mô phỏng di chuyển xe cứu hộ thời gian thực theo tuyến đường UCS về phía người gửi SOS
    LaunchedEffect(isCitizenTrackingMode, isCitizenSimulatingMovement, routeState?.polylinePoints) {
        if (!isCitizenTrackingMode || !isCitizenSimulatingMovement) return@LaunchedEffect
        val pts = routeState?.polylinePoints ?: return@LaunchedEffect
        if (pts.size < 2) return@LaunchedEffect

        citizenProgressStep = 1
        for (i in 0 until pts.size) {
            if (!isCitizenSimulatingMovement || !isCitizenTrackingMode) break
            val pt = pts[i]
            val prev = liveRescuerLocation
            if (prev != null) {
                val b = computeBearing(prev, pt)
                if (b > 0f) rescuerBearing = b
            }
            liveRescuerLocation = pt
            DisasterMapSharedState.lastKnownRescuerLocation = pt

            val rem = calculateRemainingDistance(pt, pts)
            citizenRemainingDist = rem
            citizenEtaMinutes = (((rem / 1000.0) / 30.0) * 60.0).toInt().coerceAtLeast(1)

            if (rem <= 35.0 || i == pts.size - 1) {
                citizenProgressStep = 2 // Đến nơi
                kotlinx.coroutines.delay(2500)
                citizenProgressStep = 3 // Cứu hộ
                kotlinx.coroutines.delay(3000)
                citizenProgressStep = 4 // Hoàn thành
                break
            }
            kotlinx.coroutines.delay(800)
        }
    }

    // Tự động căn khung hình chứa cả người gửi tín hiệu và người cứu hộ khi nạp xong tuyến đường
    LaunchedEffect(isCitizenTrackingMode, routeState?.polylinePoints) {
        if (!isCitizenTrackingMode) return@LaunchedEffect
        val pts = routeState?.polylinePoints
        if (!pts.isNullOrEmpty()) {
            try {
                val builder = LatLngBounds.builder()
                pts.forEach { builder.include(it) }
                liveVictimLocation?.let { builder.include(it) }
                liveRescuerLocation?.let { builder.include(it) }
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 150),
                    durationMs = 900
                )
            } catch (_: Exception) {}
        }
    }

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
                    DisasterMapSharedState.lastKnownRescuerLocation = target
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

    // Tự động yêu cầu vị trí cứu hộ viên khi mở màn hình nếu đã có quyền vị trí
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val cached = DisasterMapSharedState.lastKnownRescuerLocation
            if (cached != null && myLocation == null) {
                myLocation = cached
            }
            locationProvider.getCurrentLocation { result ->
                if (result is LocationResult.Success) {
                    val loc = LatLng(result.latitude, result.longitude)
                    myLocation = loc
                    DisasterMapSharedState.lastKnownRescuerLocation = loc
                }
            }
        }
    }

    fun focusOnMyLocation() {
        if (requestDeviceLocation && !hasLocationPermission) {
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
                DisasterMapSharedState.lastKnownRescuerLocation = target
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
    val refreshError by viewModel.refreshError.collectAsState()
    LaunchedEffect(markers) { selectedMarker = selectedMarker?.takeIf { selected -> markers.any { it.eventId == selected.eventId && it.requestId == selected.requestId } } }

    // Giám sát thời gian thực phía Cứu hộ viên: Nếu người gửi SOS thay đổi vị trí (GPS nạn nhân di chuyển),
    // bản đồ cứu hộ viên tự động cập nhật marker và tính lại tuyến đường đến vị trí mới nhất của nạn nhân
    LaunchedEffect(markers) {
        if (isCitizenTrackingMode) return@LaunchedEffect
        val currentRoute = routeState ?: return@LaunchedEffect
        val destPt = currentRoute.destinationPoint ?: return@LaunchedEffect
        val currentMarker = selectedMarker ?: return@LaunchedEffect
        val targetId = currentMarker.eventId ?: currentMarker.requestId ?: return@LaunchedEffect

        val latest = markers.find { it.eventId == targetId || it.requestId == targetId }
        if (latest != null) {
            val newDest = LatLng(latest.latitude, latest.longitude)
            if (computeDistanceMeters(destPt, newDest) > 10.0) {
                selectedMarker = latest
                val rescuerLoc = myLocation ?: DisasterMapSharedState.lastKnownRescuerLocation
                if (rescuerLoc != null) {
                    viewModel.computeRouteToMarker(
                        marker = latest,
                        rescuerLocation = rescuerLoc,
                        isSimulatedRescuerLocation = false
                    )
                }
            }
        }
    }


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

    LaunchedEffect(routeState?.polylinePoints) {
        val points = routeState?.polylinePoints
        if (!points.isNullOrEmpty() && !isNavigating) {
            try {
                val builder = LatLngBounds.builder()
                points.forEach { builder.include(it) }
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 140),
                    durationMs = 800
                )
            } catch (_: Exception) {
                val first = points.first()
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(first, 15f))
            }
        }
    }

    // Tự động nhận diện tín hiệu điều hướng từ SosDetailScreen hoặc MissionDetailScreen (CHỈ ĐƯỜNG)
    LaunchedEffect(markers, myLocation) {
        val dest = DisasterMapSharedState.targetDestination
        val targetId = DisasterMapSharedState.targetEventId

        if (dest != null) {
            DisasterMapSharedState.targetDestination = null
            selectedMarker = null
            val marker = MapRescueMarker(
                type = MarkerType.SOS,
                eventId = dest.eventId,
                requestId = dest.requestId,
                latitude = dest.latitude,
                longitude = dest.longitude,
                title = dest.title,
                snippet = dest.title,
                urgency = dest.urgency
            )
            val rescuerLoc = myLocation ?: DisasterMapSharedState.lastKnownRescuerLocation
            if (rescuerLoc != null && rescuerLoc.latitude != 0.0 && rescuerLoc.longitude != 0.0) {
                myLocation = rescuerLoc
                viewModel.computeRouteToMarker(
                    marker = marker,
                    rescuerLocation = rescuerLoc,
                    isSimulatedRescuerLocation = false
                )
            } else {
                locationProvider.getCurrentLocation { res ->
                    if (res is LocationResult.Success) {
                        val loc = LatLng(res.latitude, res.longitude)
                        myLocation = loc
                        DisasterMapSharedState.lastKnownRescuerLocation = loc
                        viewModel.computeRouteToMarker(
                            marker = marker,
                            rescuerLocation = loc,
                            isSimulatedRescuerLocation = false
                        )
                    }
                }
            }
        } else if (targetId != null && markers.isNotEmpty()) {
            val matching = markers.find { it.eventId == targetId || it.requestId == targetId }
            if (matching != null) {
                DisasterMapSharedState.targetEventId = null
                selectedMarker = null
                val rescuerLoc = myLocation ?: DisasterMapSharedState.lastKnownRescuerLocation
                if (rescuerLoc != null && rescuerLoc.latitude != 0.0 && rescuerLoc.longitude != 0.0) {
                    myLocation = rescuerLoc
                    viewModel.computeRouteToMarker(
                        marker = matching,
                        rescuerLocation = rescuerLoc,
                        isSimulatedRescuerLocation = false
                    )
                } else {
                    locationProvider.getCurrentLocation { res ->
                        if (res is LocationResult.Success) {
                            val loc = LatLng(res.latitude, res.longitude)
                            myLocation = loc
                            DisasterMapSharedState.lastKnownRescuerLocation = loc
                            viewModel.computeRouteToMarker(
                                marker = matching,
                                rescuerLocation = loc,
                                isSimulatedRescuerLocation = false
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (!isNavigating && !isCitizenTrackingMode) {
                AppBottomNavigation(
                    currentRoute = "map",
                    userRole = userRole,
                    onNavigate = onBottomNavNavigate
                )
            }
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
                    zoomControlsEnabled = !isNavigating && !isCitizenTrackingMode,
                    mapToolbarEnabled = false
                ),
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission && !isNavigating && !isCitizenTrackingMode),
                onMapLoaded = {
                    isMapLoaded = true
                    onTilesLoaded()
                }
            ) {
                MapEffect(Unit) { map -> onMapReady(map) }

                // 1. NỘI DUNG BẢN ĐỒ KHI Ở CHẾ ĐỘ NGƯỜI DÂN THEO DÕI CỨU HỘ (CITIZEN TRACKING)
                if (isCitizenTrackingMode) {
                    // Marker vị trí người gửi SOS (Cập nhật thời gian thực theo GPS)
                    liveVictimLocation?.let { pos ->
                        val victimDescriptor = remember { NavigationMarkerHelper.getVictimDescriptor(context) }
                        Marker(
                            state = MarkerState(position = pos),
                            icon = victimDescriptor,
                            anchor = Offset(0.5f, 0.5f),
                            title = "Vị trí của bạn (Tín hiệu SOS)",
                            snippet = "GPS trực tiếp • Đang gửi tín hiệu khẩn cấp"
                        )
                    }

                    // Marker xe cứu hộ đang di chuyển về phía người gửi SOS (Thời gian thực)
                    liveRescuerLocation?.let { pos ->
                        val vehicleDescriptor = remember(vehicleType) {
                            NavigationMarkerHelper.getVehicleDescriptor(context, vehicleType)
                        }
                        Marker(
                            state = MarkerState(position = pos),
                            icon = vehicleDescriptor,
                            rotation = rescuerBearing,
                            flat = true,
                            anchor = Offset(0.5f, 0.5f),
                            title = "Đội cứu hộ đang đến",
                            snippet = "Cách bạn: ${String.format(Locale.US, "%.1f km", (citizenRemainingDist ?: routeState?.totalDistanceMeters ?: 0.0) / 1000.0)} • Dự kiến: ${citizenEtaMinutes ?: 1} phút"
                        )
                    }

                    // Tuyến đường UCS nối từ người cứu hộ đến vị trí người gửi SOS
                    routeState?.let { route ->
                        if (route.polylinePoints.isNotEmpty()) {
                            Polyline(
                                points = route.polylinePoints,
                                color = Color(0xFF00A8FF),
                                width = 16f,
                                geodesic = true
                            )
                        }
                    }
                } else {
                    // 2. NỘI DUNG BẢN ĐỒ KHI Ở CHẾ ĐỘ CỨU HỘ VIÊN
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
                            onClick = { nativeMarker ->
                                onMarkerSelected(marker, nativeMarker)
                                selectedMarker = marker
                                false // return false so info window shows and camera moves
                            }
                        )
                    }

                    // Render UCS Polyline if available
                    routeState?.let { route ->
                        if (route.polylinePoints.isNotEmpty()) {
                            Polyline(
                                points = route.polylinePoints,
                                color = if (isNavigating) Color(0xFF00A8FF) else PrimaryBlue,
                                width = if (isNavigating) 16f else 14f,
                                geodesic = true
                            )
                        }
                    }

                    // Render biểu tượng xe ô tô / xe máy / mũi tên điều hướng Google Maps theo GPS thời gian thực
                    if (isNavigating && myLocation != null) {
                        val vehicleDescriptor = remember(vehicleType) {
                            NavigationMarkerHelper.getVehicleDescriptor(context, vehicleType)
                        }
                        Marker(
                            state = MarkerState(position = myLocation!!),
                            icon = vehicleDescriptor,
                            rotation = currentBearing,
                            flat = true,
                            anchor = Offset(0.5f, 0.5f),
                            title = "Đội cứu hộ đang di chuyển"
                        )
                    }
                }
            }

            // Overlay top bar khi ở chế độ Cứu hộ viên bình thường
            if (!isNavigating && !isCitizenTrackingMode) {
                Surface(
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = markers.isNotEmpty()) { focusOnMarkers() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            refreshError?.let { Text(it, color = EmergencyRed) }
                            Text(
                                "Bản đồ cứu hộ",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Hiển thị ${markers.size} tín hiệu khẩn cấp" + if (markers.isNotEmpty()) " (Chạm để xem)" else "",
                                fontSize = 14.sp,
                                color = if (markers.isNotEmpty()) EmergencyRed else TextSecondary,
                                fontWeight = if (markers.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        // Huy hiệu giám sát tín hiệu thời gian thực dành cho Cứu hộ viên (không có nút Theo dõi SOS)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tín hiệu trực tiếp",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Vòng quay loading ở GIỮA màn hình khi đang tính toán lộ trình
            if (routeState?.isLoading == true) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 10.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(46.dp),
                            color = PrimaryBlue,
                            trackColor = PrimaryBlue.copy(alpha = 0.15f),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Đang tìm lộ trình cứu hộ...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Thông báo lỗi nếu không tìm được đường hoặc ngoài phạm vi
            routeState?.errorMessage?.let { errorMsg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 88.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(10.dp),
                    color = EmergencyRed.copy(alpha = 0.95f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMsg,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.clearRoute() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ── HUD THEO DÕI CỨU HỘ PHÍA NGƯỜI DÂN (CITIZEN RESCUE TRACKING HUD) ──
            if (isCitizenTrackingMode) {
                // Top Progress Stepper HUD (5 bước tiến độ cứu hộ)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.96f),
                    shadowElevation = 10.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "THEO DÕI TIẾN ĐỘ CỨU HỘ",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF16A34A).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF22C55E))
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "TRỰC TIẾP",
                                            color = Color(0xFF4ADE80),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        isCitizenTrackingMode = false
                                        DisasterMapSharedState.isCitizenTrackingMode = false
                                        viewModel.clearRoute()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Đóng theo dõi",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        CitizenRescueProgressStepper(currentStep = citizenProgressStep)

                        Spacer(modifier = Modifier.height(10.dp))

                        val stepMsg = when (citizenProgressStep) {
                            0 -> "Đã tiếp nhận yêu cầu SOS • Đang điều động đội cứu trợ"
                            1 -> "Đội cứu hộ đang di chuyển đến vị trí của bạn"
                            2 -> "Đội cứu hộ đã tiếp cận hiện trường (< 35m)!"
                            3 -> "Đang triển khai các biện pháp cứu trợ an toàn"
                            4 -> "Hoàn thành cứu hộ thành công! Hãy giữ an toàn."
                            else -> "Đội cứu hộ đang trên đường tới bạn"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (citizenProgressStep) {
                                4 -> Color(0xFF14532D)
                                2, 3 -> Color(0xFF064E3B)
                                else -> Color(0xFF1E293B)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stepMsg,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Bottom ETA & Rescuer Action HUD
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp, start = 12.dp, end = 12.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.96f),
                    shadowElevation = 10.dp
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Hàng 1: Thời gian dự kiến (ETA), Khoảng cách còn lại và Nút Mô phỏng / Dừng
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${citizenEtaMinutes ?: 1} phút",
                                        color = Color(0xFF22C55E),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = String.format(Locale.US, "• %.1f km", (citizenRemainingDist ?: routeState?.totalDistanceMeters ?: 0.0) / 1000.0),
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Đang di chuyển đến vị trí của bạn",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Nút Dừng / Mô phỏng di chuyển (Dành cho việc kiểm thử)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCitizenSimulatingMovement) Color(0xFFDC2626) else Color(0xFF16A34A),
                                modifier = Modifier.clickable {
                                    isCitizenSimulatingMovement = !isCitizenSimulatingMovement
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isCitizenSimulatingMovement) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isCitizenSimulatingMovement) "Dừng" else "Mô phỏng",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Hàng 2: 3 nút chuyển góc nhìn Camera (Vị trí tôi, Xe cứu hộ, Toàn cảnh)
                        // Bố trí 3 nút dạng chip gọn gàng, không bị bóp nghẹt, chữ không bao giờ xuống dòng
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        liveVictimLocation?.let { loc ->
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(loc, 17f),
                                                durationMs = 600
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Vị trí tôi",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        liveRescuerLocation?.let { loc ->
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(loc, 17f),
                                                durationMs = 600
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Xe cứu hộ",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val b = LatLngBounds.builder()
                                            liveVictimLocation?.let { b.include(it) }
                                            liveRescuerLocation?.let { b.include(it) }
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngBounds(b.build(), 150),
                                                durationMs = 600
                                            )
                                        } catch (_: Exception) {}
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.NearMe,
                                        contentDescription = null,
                                        tint = Color(0xFFA855F7),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Toàn cảnh",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Hàng 3: Nút gọi khẩn cấp 112 toàn chiều rộng nổi bật, dễ bấm
                        Surface(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                        data = android.net.Uri.parse("tel:112")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = EmergencyRed,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "GỌI KHẨN CẤP TỔNG ĐÀI 112",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // 1. THANH ĐIỀU KHIỂN KHI Ở CHẾ ĐỘ CỨU HỘ VIÊN (KHÔNG DẪN ĐƯỜNG, KHÔNG CITIZEN TRACKING)
            if (!isNavigating && !isCitizenTrackingMode) {
                routeState?.let { route ->
                    if (route.polylinePoints.isNotEmpty() && route.errorMessage == null && !route.isLoading) {
                        val distKm = (route.totalDistanceMeters ?: 0.0) / 1000.0
                        Column(
                            modifier = Modifier
                                .padding(top = 88.dp, end = 16.dp)
                                .align(Alignment.TopEnd),
                            horizontalAlignment = Alignment.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.96f),
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Directions,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Lộ trình: ${String.format(Locale.US, "%.1f km", distKm)}",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    route.routeResult?.let { res ->
                                        Text(
                                            text = " (${res.pathEdges.size} đoạn)",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Surface(
                                        color = Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.clickable { viewModel.clearRoute() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Đóng lộ trình",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "Đóng",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Nút BẮT ĐẦU DI CHUYỂN
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xFF0F9D58),
                                shadowElevation = 8.dp,
                                modifier = Modifier.clickable {
                                    isNavigating = true
                                    isCameraFollowEnabled = true
                                    val pts = route.polylinePoints
                                    if (pts.size >= 2 && myLocation != null) {
                                        currentBearing = computeBearing(myLocation!!, pts[1])
                                    }
                                    coroutineScope.launch {
                                        myLocation?.let { loc ->
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newCameraPosition(
                                                    CameraPosition.Builder()
                                                        .target(loc)
                                                        .zoom(17.5f)
                                                        .tilt(38f)
                                                        .bearing(currentBearing)
                                                        .build()
                                                ),
                                                durationMs = 800
                                            )
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "BẮT ĐẦU DI CHUYỂN",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Vị trí của tôi (Dành cho cả người cứu hộ và người dùng)
                SmallFloatingActionButton(
                    onClick = { focusOnMyLocation() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (selectedMarker != null) 340.dp else 112.dp, end = 16.dp),
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
                                    val currentLoc = myLocation ?: DisasterMapSharedState.lastKnownRescuerLocation
                                    selectedMarker = null
                                    if (currentLoc != null && currentLoc.latitude != 0.0 && currentLoc.longitude != 0.0) {
                                        myLocation = currentLoc
                                        viewModel.computeRouteToMarker(
                                            marker = marker,
                                            rescuerLocation = currentLoc,
                                            isSimulatedRescuerLocation = false
                                        )
                                    } else {
                                        locationProvider.getCurrentLocation { res ->
                                            if (res is LocationResult.Success) {
                                                val loc = LatLng(res.latitude, res.longitude)
                                                myLocation = loc
                                                DisasterMapSharedState.lastKnownRescuerLocation = loc
                                                viewModel.computeRouteToMarker(
                                                    marker = marker,
                                                    rescuerLocation = loc,
                                                    isSimulatedRescuerLocation = false
                                                )
                                            } else {
                                                focusOnMyLocation()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TÌM ĐƯỜNG ĐẾN SOS (UCS)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

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

            // 2. GIAO DIỆN DẪN ĐƯỜNG THỜI GIAN THỰC (GOOGLE MAPS NAVIGATION HUD)
            if (isNavigating) {
                // Banner chỉ hướng trên đỉnh (Google Maps style)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF064E3B).copy(alpha = 0.96f),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Về hướng ${selectedMarker?.title ?: "khu vực cần cứu hộ"}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tuyến UCS tối ưu • ${String.format(Locale.US, "%.1f km", (remainingDistanceMeters ?: routeState?.totalDistanceMeters ?: 0.0) / 1000.0)} còn lại",
                                color = Color(0xFFA7F3D0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Chọn loại phương tiện (Ô tô / Xe máy / Mũi tên)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.clickable {
                                vehicleType = when (vehicleType) {
                                    NavigationVehicleType.CAR -> NavigationVehicleType.MOTORCYCLE
                                    NavigationVehicleType.MOTORCYCLE -> NavigationVehicleType.ARROW
                                    NavigationVehicleType.ARROW -> NavigationVehicleType.CAR
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (vehicleType) {
                                        NavigationVehicleType.CAR -> "🚗 Ô tô"
                                        NavigationVehicleType.MOTORCYCLE -> "🏍️ Xe máy"
                                        NavigationVehicleType.ARROW -> "⬆️ Mũi tên"
                                    },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Nút "Về giữa" (Recenter camera on vehicle)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 110.dp, start = 16.dp)
                        .clickable {
                            isCameraFollowEnabled = true
                            coroutineScope.launch {
                                myLocation?.let { loc ->
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(loc)
                                                .zoom(17.5f)
                                                .tilt(38f)
                                                .bearing(currentBearing)
                                                .build()
                                        ),
                                        durationMs = 600
                                    )
                                }
                            }
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NearMe,
                            contentDescription = "Về giữa",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Về giữa",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Thanh điều hướng bên dưới (Google Maps bottom navigation bar)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.96f),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nút X dừng điều hướng
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF334155),
                            modifier = Modifier
                                .size(42.dp)
                                .clickable {
                                    isNavigating = false
                                    isSimulatingMovement = false
                                    routeState?.polylinePoints?.let { pts ->
                                        if (pts.isNotEmpty()) {
                                            coroutineScope.launch {
                                                try {
                                                    val builder = LatLngBounds.builder()
                                                    pts.forEach { builder.include(it) }
                                                    cameraPositionState.animate(
                                                        CameraUpdateFactory.newLatLngBounds(builder.build(), 120),
                                                        durationMs = 800
                                                    )
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dừng dẫn đường",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${estimatedTimeMinutes ?: 1} phút",
                                    color = Color(0xFF22C55E),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format(Locale.US, "• %.1f km", (remainingDistanceMeters ?: routeState?.totalDistanceMeters ?: 0.0) / 1000.0),
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isSimulatingMovement) "Đang chạy mô phỏng di chuyển..." else "GPS thời gian thực • Xe đang di chuyển",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }

                        // Nút chạy thử mô phỏng (tiện lợi khi test trong phòng)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSimulatingMovement) EmergencyRed else Color(0xFF1E293B),
                            modifier = Modifier.clickable {
                                isSimulatingMovement = !isSimulatingMovement
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isSimulatingMovement) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSimulatingMovement) "Dừng" else "Chạy thử",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun computeBearing(from: LatLng, to: LatLng): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lon1 = Math.toRadians(from.longitude)
    val lat2 = Math.toRadians(to.latitude)
    val lon2 = Math.toRadians(to.longitude)
    val dLon = lon2 - lon1
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    val brng = Math.toDegrees(Math.atan2(y, x)).toFloat()
    return (brng + 360f) % 360f
}

private fun calculateRemainingDistance(currentLoc: LatLng, polyline: List<LatLng>): Double {
    if (polyline.isEmpty()) return 0.0
    var closestIdx = 0
    var minDist = Double.MAX_VALUE
    for (i in polyline.indices) {
        val d = computeDistanceMeters(currentLoc, polyline[i])
        if (d < minDist) {
            minDist = d
            closestIdx = i
        }
    }
    var totalDist = minDist
    for (i in closestIdx until polyline.size - 1) {
        totalDist += computeDistanceMeters(polyline[i], polyline[i + 1])
    }
    return totalDist
}

private fun computeDistanceMeters(p1: LatLng, p2: LatLng): Double {
    val lat1Rad = Math.toRadians(p1.latitude)
    val lat2Rad = Math.toRadians(p2.latitude)
    val dLat = Math.toRadians(p2.latitude - p1.latitude)
    val dLon = Math.toRadians(p2.longitude - p1.longitude)
    val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
            Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0) * Math.cos(lat1Rad) * Math.cos(lat2Rad)
    val c = 2.0 * Math.atan2(Math.sqrt(a.coerceIn(0.0, 1.0)), Math.sqrt((1.0 - a).coerceAtLeast(0.0)))
    return 6371000.0 * c
}

@Composable
fun CitizenRescueProgressStepper(
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val steps = listOf("Tiếp nhận", "Di chuyển", "Đến nơi", "Cứu hộ", "Hoàn thành")
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { index, name ->
            val isPassed = index < currentStep
            val isCurrent = index == currentStep

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (index == 0) Color.Transparent
                                else if (index <= currentStep) Color(0xFF22C55E)
                                else Color(0xFF475569)
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isPassed -> Color(0xFF22C55E)
                                    isCurrent -> PrimaryBlue
                                    else -> Color(0xFF334155)
                                }
                            )
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = when {
                                    isPassed -> Color(0xFF22C55E)
                                    isCurrent -> Color.White
                                    else -> Color(0xFF64748B)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPassed) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (index == steps.size - 1) Color.Transparent
                                else if (index < currentStep) Color(0xFF22C55E)
                                else Color(0xFF475569)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = name,
                    fontSize = 10.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isPassed -> Color(0xFF22C55E)
                        isCurrent -> Color.White
                        else -> Color(0xFF94A3B8)
                    },
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
