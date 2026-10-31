package com.example.bai1.presentation.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.AppBottomNavigation
import com.example.bai1.presentation.location.CurrentLocationViewModel
import com.example.bai1.sos.presentation.LocationUiState
import com.example.bai1.ui.theme.EmergencyRed
import com.example.bai1.ui.theme.TextPrimary
import com.example.bai1.ui.theme.TextSecondary
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

@Composable
fun SOSScreen(
    onNavigateBack: () -> Unit,
    onBottomNavNavigate: (String) -> Unit,
    onHoldSosSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationViewModel: CurrentLocationViewModel = viewModel(
        factory = CurrentLocationViewModel.factory(context.applicationContext)
    )
    val locationState by locationViewModel.state.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            locationViewModel.refresh()
        } else {
            locationViewModel.permissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            locationViewModel.refresh()
        } else {
            locationPermissionLauncher.launch(locationPermissions)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasLocationPermission(context)) {
                locationViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isPressing by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isSosSent by remember { mutableStateOf(false) }
    
    val userRole by DisasterRepository.currentUserRole.collectAsState()

    // Pulsating animation for the SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "SOSPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Handle press-and-hold progress
    LaunchedEffect(isPressing, isSosSent) {
        if (isPressing && !isSosSent) {
            while (isPressing && holdProgress < 1f) {
                delay(30)
                holdProgress += 0.03f
            }
            if (holdProgress >= 1f) {
                isPressing = false
                holdProgress = 0f
                onHoldSosSuccess()
            }
        } else if (!isPressing && !isSosSent) {
            holdProgress = 0f
        }
    }

    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "sos",
                userRole = userRole,
                onNavigate = onBottomNavNavigate
            )
        },
        containerColor = EmergencyRed
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header with Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onNavigateBack() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title and Subtitle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SOS KHẨN CẤP",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Nhấn và giữ để gửi tín hiệu\nđến trung tâm cứu hộ và đội ngũ gần bạn",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // SOS Button (Pulse + Hold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Ring
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(if (isPressing) 1.15f else pulseScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Mid Pulse Ring
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )

                // Hold Progress Indicator Circle
                if (isPressing && !isSosSent) {
                    CircularProgressIndicator(
                        progress = { holdProgress },
                        modifier = Modifier.size(150.dp),
                        color = Color.White,
                        strokeWidth = 6.dp,
                    )
                }

                // Inner Main SOS Button
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .pointerInput(isSosSent) {
                            if (!isSosSent) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    isPressing = true
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.pressed })
                                    isPressing = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSosSent) "ĐÃ GỬI" else "SOS",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = EmergencyRed,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Location Card & Call Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Location Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header: location is fetched automatically when this screen opens/resumes.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF0F0)) // Very light red background
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (locationState) {
                                    LocationUiState.Loading -> "Đang xác định vị trí chính xác..."
                                    is LocationUiState.Success -> "Vị trí đã được cập nhật tự động"
                                    is LocationUiState.Error -> "Chưa xác định được vị trí"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                        
                        HorizontalDivider(color = Color(0xFFEEEEEE))

                        // Content
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF5F7FA)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = com.example.bai1.ui.theme.PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                when (val state = locationState) {
                                    LocationUiState.Loading -> Text(
                                        text = "Đang lấy vị trí GPS/mạng...",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        lineHeight = 22.sp
                                    )
                                    is LocationUiState.Success -> {
                                        Text(
                                            text = formatCoordinates(state.latitude, state.longitude),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Độ chính xác ±${state.accuracyMeters.formatOneDecimal()} m",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    is LocationUiState.Error -> {
                                        Text(
                                            text = state.message,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = EmergencyRed,
                                            lineHeight = 20.sp
                                        )
                                        Text(
                                            text = "Hãy bật quyền vị trí chính xác rồi thử lại.",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                        if (locationState is LocationUiState.Error) {
                            OutlinedButton(
                                onClick = {
                                    if (hasLocationPermission(context)) locationViewModel.refresh()
                                    else {
                                        locationPermissionLauncher.launch(locationPermissions)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(horizontal = 16.dp, vertical = 0.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thử lại", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Call 112 Emergency Button
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = EmergencyRed
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Gọi 112",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Gọi khẩn cấp 112",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp)) // some padding before bottom bar
            }
        }
    }
}

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun formatCoordinates(latitude: Double, longitude: Double): String =
    "${"%.5f".format(java.util.Locale.US, latitude)}° N, ${"%.5f".format(java.util.Locale.US, longitude)}° E"

private fun Float.formatOneDecimal(): String = "%.1f".format(java.util.Locale.US, this)
