package com.example.bai1.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.presentation.components.PrimaryButton
import com.example.bai1.ui.theme.*

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.bai1.R

import android.util.Log
import java.security.MessageDigest
import java.util.UUID
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onLogin: ((identifier: String, password: String) -> Unit)? = null,
    onGoogleLogin: ((idToken: String) -> Unit)? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val scrollState = rememberScrollState()

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

            // Logo Badge
            Image(
                painter = painterResource(id = R.drawable.icondangnhap),
                contentDescription = "Login Logo",
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Đăng nhập",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Input: Email / Phone
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                placeholder = { Text("Email hoặc số điện thoại") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = TextSecondary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderColor
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input: Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Mật khẩu") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextSecondary)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                            tint = TextSecondary
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderColor
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Remember me + Forgot password
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { rememberMe = !rememberMe }
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                    )
                    Text(
                        text = "Ghi nhớ đăng nhập",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Text(
                    text = "Quên mật khẩu?",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryBlue,
                    modifier = Modifier.clickable(onClick = onForgotPasswordClick)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Login Button
            PrimaryButton(
                text = if (isLoading) "Đang đăng nhập..." else "Đăng nhập",
                onClick = {
                    onLogin?.invoke(identifier, password) ?: onLoginSuccess()
                },
                enabled = !isLoading
            )

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    color = EmergencyRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Social Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
                Text(
                    text = "  Hoặc đăng nhập bằng  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Social Buttons (Google, Facebook, Zalo)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialLoginButton(iconRes = R.drawable.google, iconSize = 28.dp, onClick = {
                    coroutineScope.launch {
                        Log.d("GOOGLE_STEP", "Credential request started")
                        
                        val rawNonce = UUID.randomUUID().toString()
                        val bytes = rawNonce.toByteArray()
                        val md = MessageDigest.getInstance("SHA-256")
                        val digest = md.digest(bytes)
                        val hashedNonce = digest.joinToString("") { "%02x".format(it) }

                        val getGoogleIdOptionBuilder = GetGoogleIdOption.Builder()
                            .setServerClientId(context.getString(R.string.default_web_client_id))
                            .setNonce(hashedNonce)
                            .setAutoSelectEnabled(false) // Never auto-select to allow switching accounts

                        var credentialResponse: androidx.credentials.GetCredentialResponse? = null
                        
                        try {
                            val googleIdOption = getGoogleIdOptionBuilder
                                .setFilterByAuthorizedAccounts(false) // Show all accounts on the device
                                .build()
                                
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            
                            credentialResponse = credentialManager.getCredential(context, request)
                        } catch (e: GetCredentialCancellationException) {
                            Log.d("GOOGLE_STEP", "User cancelled during all accounts check")
                            return@launch
                        } catch (e: Exception) {
                            Log.d("GOOGLE_STEP", "Exception during all accounts check: ${e.message}")
                            e.printStackTrace()
                            return@launch
                        }

                        if (credentialResponse == null) return@launch
                        
                        Log.d("GOOGLE_STEP", "Credential returned")
                        val credential = credentialResponse.credential
                        Log.d("GOOGLE_STEP", "Credential type: ${credential.type}")

                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            try {
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val idToken = googleIdTokenCredential.idToken
                                Log.d("GOOGLE_STEP", "Google token obtained: YES")
                                
                                Log.d("GOOGLE_STEP", "Firebase sign-in started")
                                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                                val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                                
                                if (authResult.user != null) {
                                    Log.d("GOOGLE_STEP", "Firebase sign-in success")
                                    val tokenResult = authResult.user!!.getIdToken(false).await()
                                    val firebaseIdToken = tokenResult.token
                                    if (firebaseIdToken != null) {
                                        Log.d("GOOGLE_STEP", "Firebase ID token obtained: YES")
                                        Log.d("GOOGLE_STEP", "Backend request started")
                                        onGoogleLogin?.invoke(firebaseIdToken)
                                    } else {
                                        Log.d("GOOGLE_STEP", "Firebase ID token obtained: NO")
                                    }
                                } else {
                                    Log.d("GOOGLE_STEP", "Firebase sign-in failure: User is null")
                                }
                            } catch (e: Exception) {
                                Log.d("GOOGLE_STEP", "Firebase sign-in failure: ${e.message}")
                                e.printStackTrace()
                            }
                        } else {
                            Log.d("GOOGLE_STEP", "Unsupported credential type")
                        }
                    }
                })
                SocialLoginButton(iconRes = R.drawable.facebook_logo, iconSize = 28.dp, onClick = {})
                SocialLoginButton(iconRes = R.drawable.zalo_logo, iconSize = 46.dp, onClick = {})
            }
        }

        // Footer Register Link
        Row(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .clickable(onClick = onRegisterClick),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Chưa có tài khoản? ",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = "Đăng ký",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PrimaryBlue
            )
        }
    }
}

@Composable
private fun SocialLoginButton(
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, BorderColor, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.size(iconSize)
        )
    }
}
