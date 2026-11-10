package com.example.bai1.auth.session

data class AuthUser(
    val id: String,
    val fullName: String?,
    val email: String?,
    val phone: String?,
    val role: String?,
    val status: String?,
    val verificationStatus: String?
)

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: AuthUser, val isNewUser: Boolean = false) : AuthState
    data class Error(val message: String) : AuthState
}
