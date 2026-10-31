package com.example.bai1.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
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
import com.example.bai1.auth.RoleSelectionViewModel
import com.example.bai1.auth.RoleSelectionState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.data.model.UserRole
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.PrimaryButton
import com.example.bai1.ui.theme.*

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.bai1.R

@Composable
fun RoleSelectionScreen(
    onContinue: (UserRole) -> Unit,
    viewModel: RoleSelectionViewModel = viewModel(
        factory = RoleSelectionViewModel.factory(AuthAppDependencies.repository)
    )
) {
    var selectedRole by remember { mutableStateOf(UserRole.RESCUER) }
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState) {
        if (uiState is RoleSelectionState.Success) {
            onContinue(selectedRole)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Top Illustration
            Image(
                painter = painterResource(id = R.drawable.chonvaitro),
                contentDescription = "Role Selection Logo",
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Chọn vai trò của bạn",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Chọn cách bạn muốn trải nghiệm ứng dụng",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Option 1: Người dùng
            RoleCard(
                title = "Người dùng",
                subtitle = "Theo dõi cảnh báo, nhận thông tin, yêu cầu cứu hộ khẩn cấp khi gặp nguy hiểm",
                icon = Icons.Default.Person,
                iconColor = PrimaryBlue,
                isSelected = selectedRole == UserRole.USER,
                selectedBorderColor = PrimaryBlue,
                onClick = { selectedRole = UserRole.USER }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Option 2: Nhà hoạt động cứu hộ
            RoleCard(
                title = "Nhà hoạt động cứu hộ",
                subtitle = "Tham gia đội cứu trợ, tiếp nhận nhiệm vụ, hỗ trợ người dân vùng thiên tai",
                icon = Icons.Default.Favorite,
                iconColor = EmergencyRed,
                isSelected = selectedRole == UserRole.RESCUER,
                selectedBorderColor = EmergencyRed,
                onClick = { selectedRole = UserRole.RESCUER }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Bạn có thể thay đổi vai trò sau trong phần cài đặt",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            if (uiState is RoleSelectionState.Error) {
                Text(
                    text = (uiState as RoleSelectionState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (uiState is RoleSelectionState.Loading) {
                CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(36.dp))
            } else {
                PrimaryButton(
                    text = "Tiếp tục",
                    onClick = {
                        viewModel.selectRole(selectedRole.name)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    isSelected: Boolean,
    selectedBorderColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = if (isSelected) BorderStroke(2.dp, selectedBorderColor) else BorderStroke(1.dp, BorderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) selectedBorderColor.copy(alpha = 0.05f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) selectedBorderColor else TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
