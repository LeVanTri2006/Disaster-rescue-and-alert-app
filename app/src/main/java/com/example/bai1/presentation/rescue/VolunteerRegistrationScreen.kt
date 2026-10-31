package com.example.bai1.presentation.rescue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.presentation.components.AppTopBar
import com.example.bai1.presentation.components.PrimaryButton
import com.example.bai1.presentation.components.SecondaryButton
import com.example.bai1.ui.theme.*

@Composable
fun VolunteerRegistrationScreen(
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    // Step 1 states
    var fullName by remember { mutableStateOf("Nguyễn Văn A") }
    var phone by remember { mutableStateOf("0775 109 883") }
    var activeArea by remember { mutableStateOf("Đà Nẵng") }
    var selectedSkills by remember {
        mutableStateOf(setOf("Cứu hộ đường thủy", "Sơ cứu y tế", "Vận chuyển hàng hóa"))
    }
    var experience by remember { mutableStateOf("") }

    // Step 2 states
    var selectedVehicle by remember { mutableStateOf("Thuyền") }
    var readyProvinces by remember {
        mutableStateOf(setOf("Đà Nẵng", "Quảng Nam", "Thừa Thiên Huế"))
    }
    var committedToDeployment by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Đăng ký nhà hoạt động cứu hộ",
                showBackButton = step < 2,
                onBackClick = {
                    if (step > 0) step-- else onNavigateBack()
                }
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step Progress Indicator (1 - 2 - 3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepCircle(stepNum = 1, isActive = step >= 0, isCurrent = step == 0)
                StepHorizontalDivider(isActive = step >= 1)
                StepCircle(stepNum = 2, isActive = step >= 1, isCurrent = step == 1)
                StepHorizontalDivider(isActive = step >= 2)
                StepCircle(stepNum = 3, isActive = step >= 2, isCurrent = step == 2)
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                0 -> {
                    // Step 1: Thông tin cá nhân & Kỹ năng
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Thông tin cá nhân",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Họ và tên") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Số điện thoại") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = activeArea,
                            onValueChange = { activeArea = it },
                            label = { Text("Khu vực hoạt động") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Kỹ năng / Chuyên môn",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Skills chips
                        val availableSkills = listOf("Cứu hộ đường thủy", "Sơ cứu y tế", "Vận chuyển hàng hóa", "Khác")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableSkills.forEach { skill ->
                                val isSelected = selectedSkills.contains(skill)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedSkills = if (isSelected) selectedSkills - skill else selectedSkills + skill
                                    },
                                    label = { Text(skill) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        OutlinedTextField(
                            value = experience,
                            onValueChange = { experience = it },
                            label = { Text("Kinh nghiệm (nếu có)") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        PrimaryButton(
                            text = "Tiếp tục",
                            onClick = { step = 1 }
                        )
                    }
                }
                1 -> {
                    // Step 2: Thông tin bổ sung
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Thông tin bổ sung",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Chứng chỉ / Giấy tờ (nếu có)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Upload Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, BorderColor, RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .clickable {},
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("+ Tải lên hình ảnh", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Phương tiện có thể hỗ trợ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            VehicleOption(name = "Thuyền", icon = Icons.Default.DirectionsBoat, isSelected = selectedVehicle == "Thuyền") { selectedVehicle = "Thuyền" }
                            VehicleOption(name = "Ô tô", icon = Icons.Default.DirectionsCar, isSelected = selectedVehicle == "Ô tô") { selectedVehicle = "Ô tô" }
                            VehicleOption(name = "Xe máy", icon = Icons.Default.TwoWheeler, isSelected = selectedVehicle == "Xe máy") { selectedVehicle = "Xe máy" }
                            VehicleOption(name = "Khác", icon = Icons.Default.MoreHoriz, isSelected = selectedVehicle == "Khác") { selectedVehicle = "Khác" }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Khu vực sẵn sàng hỗ trợ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val provinces = listOf("Đà Nẵng", "Quảng Nam", "Thừa Thiên Huế", "Khu vực khác")
                        provinces.forEach { prov ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        readyProvinces = if (readyProvinces.contains(prov)) readyProvinces - prov else readyProvinces + prov
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = readyProvinces.contains(prov),
                                    onCheckedChange = { isChecked ->
                                        readyProvinces = if (isChecked) readyProvinces + prov else readyProvinces - prov
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                                )
                                Text(text = prov, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Commitment Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tôi cam kết tham gia khi được điều động",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                                color = TextPrimary
                            )
                            Switch(
                                checked = committedToDeployment,
                                onCheckedChange = { committedToDeployment = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        PrimaryButton(
                            text = "Tiếp tục",
                            onClick = { step = 2 }
                        )
                    }
                }
                2 -> {
                    // Step 3: Success Screen (Màn hình 15)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Thành công",
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Đã gửi hồ sơ!",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Hồ sơ của bạn đang được xét duyệt.\nChúng tôi sẽ thông báo kết quả qua ứng dụng.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        PrimaryButton(
                            text = "Về trang chủ",
                            onClick = onNavigateHome
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        SecondaryButton(
                            text = "Xem hồ sơ",
                            onClick = onNavigateProfile
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCircle(stepNum: Int, isActive: Boolean, isCurrent: Boolean) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isActive) PrimaryBlue else Color(0xFFCBD5E1)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$stepNum",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun StepHorizontalDivider(isActive: Boolean) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
            .background(if (isActive) PrimaryBlue else Color(0xFFCBD5E1))
    )
}

@Composable
private fun VehicleOption(
    name: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) PrimaryBlue else BorderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .background(if (isSelected) PrimaryLightBlue else Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = if (isSelected) PrimaryBlue else TextSecondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            color = if (isSelected) PrimaryBlue else TextPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
