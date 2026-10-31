package com.example.bai1.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    val state = repository.state

    init {
        restoreSession()
    }

    fun googleLogin(firebaseIdToken: String) {
        viewModelScope.launch { repository.googleLogin(firebaseIdToken) }
    }

    fun register(fullName: String, email: String, phone: String, password: String) {
        viewModelScope.launch { repository.register(fullName, email, phone, password) }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch { repository.login(identifier, password) }
    }

    suspend fun forgotPassword(identifier: String): Result<Unit> {
        return repository.forgotPassword(identifier)
    }

    suspend fun resetPassword(identifier: String, otp: String, newPassword: String): Result<Unit> {
        return repository.resetPassword(identifier, otp, newPassword)
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    private fun restoreSession() {
        viewModelScope.launch { repository.restoreSession() }
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(repository) as T
                }
            }
    }
}
