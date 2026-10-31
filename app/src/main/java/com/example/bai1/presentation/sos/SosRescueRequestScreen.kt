package com.example.bai1.presentation.sos

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.presentation.LocationUiState
import com.example.bai1.sos.presentation.SosUiState
import com.example.bai1.ui.theme.EmergencyRed
import com.example.bai1.ui.theme.PrimaryBlue
import com.example.bai1.ui.theme.SuccessGreen
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosRescueRequestScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit,
    viewModelOverride: SosRescueRequestViewModel? = null
) {
    val context = LocalContext.current
    val viewModel = viewModelOverride ?: composeViewModel(factory = SosRescueRequestViewModel.factory(context.applicationContext))
    val formData by viewModel.formData.collectAsState()
    val selectedConditions by viewModel.selectedConditions.collectAsState()
    val uiState  by viewModel.uiState.collectAsState()
    var locationPermissionRequested by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.refreshLocation()
        } else {
            viewModel.setLocationPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) viewModel.refreshLocation()
        else {
            locationPermissionRequested = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Route to success / queued screen
    when (val state = uiState) {
        is SosUiState.Submitted -> {
            SosSuccessState(
                eventId = state.result.eventId,
                transportMode = state.result.transportMode,
                message = state.result.message,
                totalPeople = formData.totalPeople,
                condition = formData.condition,
                latitude = formData.latitude,
                longitude = formData.longitude,
                onNavigateToMap = onNavigateToMap
            )
            return
        }
        is SosUiState.Queued -> {
            SosQueuedState(
                eventId = state.result.eventId,
                message = state.result.message,
                onNavigateToMap = onNavigateToMap
            )
            return
        }
        else -> { /* Continue to form */ }
    }

    val isSubmitting = uiState is SosUiState.Submitting
    val validationError = (uiState as? SosUiState.Editing)?.validationError
        ?: (uiState as? SosUiState.Error)?.message
    val currentLatitude = formData.latitude
    val currentLongitude = formData.longitude
    val locationState = (uiState as? SosUiState.Editing)?.location
        ?: if (currentLatitude != null && currentLongitude != null) {
            LocationUiState.Success(
                latitude = currentLatitude,
                longitude = currentLongitude,
                accuracyMeters = formData.locationAccuracy ?: 0f,
                timestamp = formData.locationTimestamp ?: 0L
            )
        } else {
            LocationUiState.Loading
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FC))
    ) {
        // Red top header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = EmergencyRed,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .padding(top = 48.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Trở lại", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tạo yêu cầu cứu hộ",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Điền thông tin chi tiết để được hỗ trợ nhanh nhất",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = "SOS",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).padding(bottom = 2.dp)
                    )
                    Text(
                        text = "SOS",
                        color = EmergencyRed,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Form content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Validation / error banner
            if (validationError != null) {
                Text(
                    text = validationError,
                    color = EmergencyRed,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // 1. Location
            FormSectionCard(icon = Icons.Default.LocationOn, title = "Vị trí hiện tại") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        when (val state = locationState) {
                            LocationUiState.Loading -> {
                                Text("Đang lấy vị trí...", fontSize = 12.sp, color = Color(0xFFF59E0B))
                            }
                            is LocationUiState.Success -> {
                                Text("🟢 Đã xác định vị trí", fontSize = 12.sp, color = SuccessGreen)
                                Text(
                                    formatCoordinates(state.latitude, state.longitude),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    "Độ chính xác: ±${state.accuracyMeters.formatOneDecimal()} m",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            is LocationUiState.Error -> {
                                Text("🟠 Không xác định được vị trí", fontSize = 12.sp, color = Color(0xFFF59E0B))
                                Text(state.message, fontSize = 12.sp, color = EmergencyRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (hasLocationPermission(context)) viewModel.refreshLocation()
                            else if (locationPermissionRequested && isPermissionPermanentlyDenied(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                                openAppSettings(context)
                            }
                            else locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            locationPermissionRequested = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, EmergencyRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cập nhật vị trí", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 2. People Count
            FormSectionCard(
                icon = Icons.Default.Group,
                title = "Số lượng người cần cứu",
                isRequired = true
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        PersonCounter(
                            icon = Icons.Default.Person,
                            title = "Người lớn",
                            subtitle = null,
                            count = formData.adults,
                            onDecrease = { if (formData.adults > 0) viewModel.updateAdultsCount(formData.adults - 1) },
                            onIncrease = { viewModel.updateAdultsCount(formData.adults + 1) },
                            modifier = Modifier.weight(1f)
                        )
                        HorizontalDivider(
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.width(1.dp).height(60.dp).padding(horizontal = 4.dp)
                        )
                        PersonCounter(
                            icon = Icons.Default.Face,
                            title = "Trẻ nhỏ",
                            subtitle = "(dưới 16 tuổi)",
                            count = formData.children,
                            onDecrease = { if (formData.children > 0) viewModel.updateChildrenCount(formData.children - 1) },
                            onIncrease = { viewModel.updateChildrenCount(formData.children + 1) },
                            modifier = Modifier.weight(1f)
                        )
                        HorizontalDivider(
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.width(1.dp).height(60.dp).padding(horizontal = 4.dp)
                        )
                        PersonCounter(
                            icon = Icons.Default.ElderlyWoman,
                            title = "Người cao tuổi",
                            subtitle = "(trên 65 tuổi)",
                            count = formData.elderly,
                            onDecrease = { if (formData.elderly > 0) viewModel.updateEldersCount(formData.elderly - 1) },
                            onIncrease = { viewModel.updateEldersCount(formData.elderly + 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tổng số người:", fontSize = 14.sp, color = Color(0xFF475569))
                        Text(
                            "${formData.totalPeople} người",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmergencyRed
                        )
                    }
                }
            }

            // 3. Needed Items
            val neededItemsList = listOf("Thực phẩm", "Nước uống", "Thuốc men", "Áo phao", "Thuyền", "Chăn màn", "Điện thoại sạc", "Khác")
            FormSectionCard(icon = Icons.Default.Inventory2, title = "Vật phẩm cần thiết") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    neededItemsList.forEach { item ->
                        val isSelected = formData.neededItems.contains(item)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleNeededItem(item) },
                            label = { Text(item, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmergencyRed.copy(alpha = 0.12f),
                                selectedLabelColor = EmergencyRed
                            )
                        )
                    }
                }
            }

            // 4. Condition
            val conditions = listOf(
                "Nguy hiểm tính mạng",
                "Bất tỉnh",
                "Khó thở",
                "Chảy máu nhiều",
                "Bị thương nặng",
                "Bị thương nhẹ",
                "Kẹt trong nhà",
                "Thiếu thức ăn/nước",
                "Mất liên lạc",
                "An toàn, chỉ cần hỗ trợ"
            )
            FormSectionCard(
                icon = Icons.Default.MedicalServices,
                title = "Tình trạng hiện tại",
                isRequired = true
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    conditions.forEach { cond ->
                        val isSelected = selectedConditions.contains(cond)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) EmergencyRed.copy(alpha = 0.08f) else Color(0xFFF8FAFC))
                                .border(
                                    1.dp,
                                    if (isSelected) EmergencyRed else Color(0xFFE2E8F0),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.toggleCondition(cond) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleCondition(cond) },
                                colors = CheckboxDefaults.colors(checkedColor = EmergencyRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                cond,
                                fontSize = 14.sp,
                                color = if (isSelected) EmergencyRed else Color(0xFF1E293B),
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 5. Description
            FormSectionCard(icon = Icons.Default.Description, title = "Mô tả thêm") {
                OutlinedTextField(
                    value = formData.description,
                    onValueChange = { viewModel.updateDescription(it) },
                    placeholder = { Text("Mô tả tình trạng cụ thể, địa chỉ chính xác, yêu cầu đặc biệt...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4
                )
            }

            // 6. Photo
            FormSectionCard(icon = Icons.Default.CameraAlt, title = "Hình ảnh") {
                PhotoSelector(
                    photoUris = formData.photoUris,
                    onAddPhoto = { viewModel.addPhoto(it) },
                    onRemovePhoto = { viewModel.removePhoto(it) },
                    onAddPhotos = { viewModel.addPhotos(it) }
                )
            }

            // 7. Submit Button
            Button(
                onClick = { viewModel.submitRequest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Đang gửi...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GỬI YÊU CẦU CỨU HỘ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUCCESS STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SosSuccessState(
    eventId: String,
    transportMode: SosTransportMode,
    message: String,
    totalPeople: Int,
    condition: String,
    latitude: Double?,
    longitude: Double?,
    onNavigateToMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FC))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SuccessGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = message, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Phương thức: ${when (transportMode) {
                SosTransportMode.ONLINE -> "Internet"
                SosTransportMode.MESH   -> "Mesh"
                SosTransportMode.RELAY  -> "Relay"
                SosTransportMode.UNKNOWN -> "Không xác định"
            }}",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Số người: $totalPeople", fontSize = 16.sp)
                Text("Tình trạng: $condition", fontSize = 16.sp, color = EmergencyRed, fontWeight = FontWeight.Medium)
                if (latitude != null) {
                    Text("Vị trí: $latitude, $longitude", fontSize = 14.sp, color = Color.Gray)
                }
                Text("Mã sự kiện: ${eventId.take(8)}...", fontSize = 12.sp, color = Color.LightGray)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNavigateToMap,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("XEM TRÊN BẢN ĐỒ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUEUED STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SosQueuedState(
    eventId: String,
    message: String,
    onNavigateToMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FC))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = message, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Yêu cầu sẽ được gửi ngay khi có kết nối mạng.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Mã sự kiện: ${eventId.take(8)}...", fontSize = 12.sp, color = Color.LightGray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNavigateToMap,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("XEM TRÊN BẢN ĐỒ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE FORM COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FormSectionCard(
    icon: ImageVector,
    title: String,
    isRequired: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(EmergencyRed.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title + if (isRequired) " *" else "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun PhotoPreviewItem(uri: Uri, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var previewBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        previewBitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE2E8F0))
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap!!.asImageBitmap(),
                contentDescription = "Ảnh SOS",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray,
                modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Xoá ảnh", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun PhotoSelector(photoUris: List<Uri>, onAddPhoto: (Uri) -> Unit, onRemovePhoto: (Uri) -> Unit, onAddPhotos: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var cameraPermissionRequested by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            photoError = null
            onAddPhotos(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = tempCameraUri
        if (success && uri != null) {
            photoError = null
            onAddPhoto(uri)
        } else {
            uri?.let { deleteCachedPhoto(context, it) }
            photoError = "Chụp ảnh đã bị hủy."
        }
        tempCameraUri = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraCapture(
                context = context,
                launcher = cameraLauncher,
                onUriCreated = { uri -> tempCameraUri = uri },
                onUnavailable = { photoError = "Không tìm thấy ứng dụng camera trên thiết bị." }
            )
        } else {
            photoError = if (cameraPermissionRequested && isPermissionPermanentlyDenied(context, Manifest.permission.CAMERA)) {
                "Quyền camera đã bị từ chối. Hãy cấp lại trong Cài đặt."
            } else {
                "Cần quyền camera để chụp ảnh."
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            photoUris.forEach { uri ->
                PhotoPreviewItem(
                    uri = uri,
                    onRemove = { onRemovePhoto(uri) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (photoUris.size < 3) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .clickable {
                            if (photoUris.size >= 3) {
                                photoError = "Chỉ được chọn tối đa 3 ảnh"
                            } else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    launchCameraCapture(
                                        context = context,
                                        launcher = cameraLauncher,
                                        onUriCreated = { uri -> tempCameraUri = uri },
                                        onUnavailable = { photoError = "Không tìm thấy ứng dụng camera trên thiết bị." }
                                    )
                                } else if (cameraPermissionRequested && isPermissionPermanentlyDenied(context, Manifest.permission.CAMERA)) {
                                    openAppSettings(context)
                                } else {
                                    cameraPermissionRequested = true
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Chụp ảnh", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .clickable {
                            if (photoUris.size >= 3) {
                                photoError = "Chỉ được chọn tối đa 3 ảnh"
                            } else {
                                galleryLauncher.launch("image/*")
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Thư viện", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                }
            }
        }
        if (photoError != null) {
            Text(photoError!!, color = EmergencyRed, fontSize = 12.sp)
            if (photoError!!.contains("Cài đặt")) {
                TextButton(onClick = { openAppSettings(context) }) {
                    Text("Mở Cài đặt")
                }
            }
        }
    }
}

private fun launchCameraCapture(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Uri>,
    onUriCreated: (Uri) -> Unit,
    onUnavailable: () -> Unit
) {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
        onUnavailable()
        return
    }
    try {
        val cameraDirectory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("camera_photo_", ".jpg", cameraDirectory)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        onUriCreated(uri)
        launcher.launch(uri)
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
    } catch (_: IllegalArgumentException) {
        onUnavailable()
    } catch (_: SecurityException) {
        onUnavailable()
    }
}

private fun deleteCachedPhoto(context: android.content.Context, uri: Uri) {
    runCatching {
        context.contentResolver.delete(uri, null, null)
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun isPermissionPermanentlyDenied(context: android.content.Context, permission: String): Boolean {
    val activity = context as? Activity ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

private fun openAppSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

private fun formatCoordinates(latitude: Double, longitude: Double): String {
    val latitudeDirection = if (latitude >= 0) "N" else "S"
    val longitudeDirection = if (longitude >= 0) "E" else "W"
    return String.format(
        Locale.US,
        "%.4f° %s, %.4f° %s",
        kotlin.math.abs(latitude), latitudeDirection, kotlin.math.abs(longitude), longitudeDirection
    )
}

private fun Float.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)

@Composable
fun PersonCounter(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    count: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFFF1F5F9), CircleShape)
                    .clickable { onDecrease() },
                contentAlignment = Alignment.Center
            ) {
                Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .width(32.dp)
                    .height(32.dp)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(count.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(EmergencyRed.copy(alpha = 0.1f), CircleShape)
                    .clickable { onIncrease() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmergencyRed)
            }
        }
    }
}
