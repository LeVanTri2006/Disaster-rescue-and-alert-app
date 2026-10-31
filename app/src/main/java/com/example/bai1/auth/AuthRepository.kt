package com.example.bai1.auth

import android.util.Log
import com.example.bai1.auth.network.AuthApiClient
import com.example.bai1.auth.network.AuthApiResult
import com.example.bai1.auth.session.AuthState
import com.example.bai1.auth.session.TokenStorage
import com.example.bai1.auth.firebase.FirebaseAuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val apiClient: AuthApiClient,
    private val tokenStorage: TokenStorage,
    private val firebaseAuthSession: FirebaseAuthSession? = null
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun register(fullName: String, email: String, phone: String, password: String) {
        _state.value = AuthState.Loading
        when (val result = apiClient.register(fullName, email, phone, password)) {
            is AuthApiResult.Failure -> _state.value = AuthState.Error(result.error.userMessage())
            is AuthApiResult.Success -> {
                if (tokenStorage.save(result.value.accessToken, result.value.refreshToken)) {
                    loadCurrentUserOrClear(true)
                } else {
                    tokenStorage.clear()
                    _state.value = AuthState.Error("Không thể lưu phiên đăng ký.")
                }
            }
        }
    }

    suspend fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _state.value = AuthState.Error("Vui lòng nhập email/số điện thoại và mật khẩu.")
            return
        }

        _state.value = AuthState.Loading
        when (val result = apiClient.login(identifier.trim(), password)) {
            is AuthApiResult.Failure -> _state.value = AuthState.Error(result.error.userMessage())
            is AuthApiResult.Success -> {
                if (tokenStorage.save(result.value.accessToken, result.value.refreshToken)) {
                    loadCurrentUserOrClear()
                } else {
                    tokenStorage.clear()
                    _state.value = AuthState.Error("Không thể lưu phiên đăng nhập.")
                }
            }
        }
    }

    suspend fun googleLogin(firebaseIdToken: String) {
        _state.value = AuthState.Loading
        when (val result = apiClient.googleLogin(firebaseIdToken)) {
            is AuthApiResult.Failure -> {
                Log.d("GOOGLE_STEP", "Backend request failure: ${result.error.kind} ${result.error.userMessage()}")
                _state.value = AuthState.Error(result.error.userMessage())
            }
            is AuthApiResult.Success -> {
                Log.d("GOOGLE_STEP", "Backend request success")
                if (tokenStorage.save(result.value.accessToken, result.value.refreshToken)) {
                    Log.d("GOOGLE_STEP", "Token saved, loading user")
                    loadCurrentUserOrClear(result.value.isNewUser)
                } else {
                    Log.d("GOOGLE_STEP", "Token save failed")
                    tokenStorage.clear()
                    _state.value = AuthState.Error("Không thể lưu phiên đăng nhập.")
                }
            }
        }
    }

    suspend fun restoreSession() {
        val accessToken = tokenStorage.getAccessToken()?.takeIf { it.isNotBlank() }
        if (accessToken == null) {
            _state.value = AuthState.Unauthenticated
            return
        }

        _state.value = AuthState.Loading
        when (val result = apiClient.me(accessToken)) {
            is AuthApiResult.Success -> {
                synchronizeFirebase(accessToken)
                _state.value = AuthState.Authenticated(result.value)
            }
            is AuthApiResult.Failure -> {
                if (result.error.kind == com.example.bai1.auth.network.AuthApiErrorKind.UNAUTHORIZED) {
                    refreshOnce()
                } else {
                    _state.value = AuthState.Error("Không thể khôi phục phiên đăng nhập.")
                }
            }
        }
    }

    suspend fun logout() {
        tokenStorage.clear()
        firebaseAuthSession?.signOut()
        _state.value = AuthState.Unauthenticated
    }

    suspend fun updateRole(role: String): Result<Unit> {
        val accessToken = tokenStorage.getAccessToken()
        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("Phiên đăng nhập không hợp lệ."))
        }

        return when (val result = apiClient.updateRole(accessToken, role)) {
            is AuthApiResult.Success -> {
                _state.value = AuthState.Authenticated(result.value)
                Result.success(Unit)
            }
            is AuthApiResult.Failure -> {
                Result.failure(Exception(result.error.userMessage()))
            }
        }
    }

    suspend fun forgotPassword(identifier: String): Result<Unit> {
        return when (val result = apiClient.forgotPassword(identifier.trim())) {
            is AuthApiResult.Success -> Result.success(Unit)
            is AuthApiResult.Failure -> Result.failure(Exception(result.error.userMessage()))
        }
    }

    suspend fun resetPassword(identifier: String, otp: String, newPassword: String): Result<Unit> {
        return when (val result = apiClient.resetPassword(identifier.trim(), otp.trim(), newPassword)) {
            is AuthApiResult.Success -> Result.success(Unit)
            is AuthApiResult.Failure -> Result.failure(Exception(result.error.userMessage()))
        }
    }

    private suspend fun loadCurrentUserOrClear(isNewUser: Boolean = false) {
        val accessToken = tokenStorage.getAccessToken()
        if (accessToken.isNullOrBlank()) {
            tokenStorage.clear()
            _state.value = AuthState.Error("Không thể lưu phiên đăng nhập.")
            return
        }

        when (val result = apiClient.me(accessToken)) {
            is AuthApiResult.Success -> {
                synchronizeFirebase(accessToken)
                _state.value = AuthState.Authenticated(result.value, isNewUser)
            }
            is AuthApiResult.Failure -> {
                if (result.error.kind == com.example.bai1.auth.network.AuthApiErrorKind.UNAUTHORIZED) {
                    tokenStorage.clear()
                }
                _state.value = AuthState.Error(result.error.userMessage())
            }
        }
    }

    private suspend fun refreshOnce() {
        val refreshToken = tokenStorage.getRefreshToken()?.takeIf { it.isNotBlank() }
        if (refreshToken == null) {
            tokenStorage.clear()
            _state.value = AuthState.Unauthenticated
            return
        }

        when (val result = apiClient.refresh(refreshToken)) {
            is AuthApiResult.Success -> {
                if (!tokenStorage.save(result.value.accessToken, result.value.refreshToken)) {
                    tokenStorage.clear()
                    _state.value = AuthState.Unauthenticated
                } else {
                    val accessToken = tokenStorage.getAccessToken()
                    if (accessToken.isNullOrBlank()) {
                        tokenStorage.clear()
                        _state.value = AuthState.Unauthenticated
                        return
                    }
                    when (val meResult = apiClient.me(accessToken)) {
                        is AuthApiResult.Success -> {
                            synchronizeFirebase(accessToken)
                            _state.value = AuthState.Authenticated(meResult.value)
                        }
                        is AuthApiResult.Failure -> {
                            tokenStorage.clear()
                            _state.value = AuthState.Unauthenticated
                        }
                    }
                }
            }
            is AuthApiResult.Failure -> {
                tokenStorage.clear()
                _state.value = AuthState.Unauthenticated
            }
        }
    }

    private suspend fun synchronizeFirebase(accessToken: String) {
        val firebaseSession = firebaseAuthSession ?: return
        when (val result = apiClient.firebaseCustomToken(accessToken)) {
            is AuthApiResult.Success -> {
                if (!firebaseSession.signInWithCustomToken(result.value)) {
                    firebaseSession.signOut()
                }
            }
            is AuthApiResult.Failure -> firebaseSession.signOut()
        }
    }
}
