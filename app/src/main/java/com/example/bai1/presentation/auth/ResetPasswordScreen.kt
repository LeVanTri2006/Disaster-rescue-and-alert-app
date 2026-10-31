package com.example.bai1.presentation.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.bai1.presentation.components.AppTopBar
import com.example.bai1.presentation.components.PrimaryButton
import com.example.bai1.ui.theme.*
import com.example.bai1.auth.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun ResetPasswordScreen(
    identifier: String,
    otp: String,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Đặt lại mật khẩu",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Tạo mật khẩu mới",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Mật khẩu mới") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Xác nhận mật khẩu") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = if (isLoading) "Đang lưu..." else "Lưu mật khẩu",
                    onClick = {
                        if (newPassword != confirmPassword) {
                            Toast.makeText(context, "Mật khẩu không khớp", Toast.LENGTH_SHORT).show()
                            return@PrimaryButton
                        }
                        if (newPassword.length < 6) {
                            Toast.makeText(context, "Mật khẩu phải từ 6 ký tự", Toast.LENGTH_SHORT).show()
                            return@PrimaryButton
                        }
                        isLoading = true
                        scope.launch {
                            val result = authViewModel.resetPassword(identifier, otp, newPassword)
                            isLoading = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Đặt lại mật khẩu thành công!", Toast.LENGTH_SHORT).show()
                                onResetSuccess()
                            } else {
                                Toast.makeText(context, result.exceptionOrNull()?.message ?: "Lỗi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isLoading && newPassword.isNotBlank() && confirmPassword.isNotBlank()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
