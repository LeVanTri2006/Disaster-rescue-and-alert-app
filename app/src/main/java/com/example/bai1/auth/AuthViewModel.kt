package com.example.bai1.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    val state = repository.state
    private val operations = mutableSetOf<Job>()
    private fun launchAuth(block: suspend CoroutineScope.() -> Unit) {
        val job = viewModelScope.launch(start = CoroutineStart.LAZY, block = block)
        synchronized(operations) { operations.add(job) }
        job.invokeOnCompletion { synchronized(operations) { operations.remove(job) } }
        job.start()
    }
    override fun onCleared() {
        repository.cancelPendingOperations(synchronized(operations) { operations.toSet() })
        super.onCleared()
    }

    init {
        restoreSession()
    }

    fun googleLogin(firebaseIdToken: String) {
        launchAuth { repository.googleLogin(firebaseIdToken) }
    }

    fun register(fullName: String, email: String, phone: String, password: String) {
        launchAuth { repository.register(fullName, email, phone, password) }
    }

    fun login(identifier: String, password: String) {
        launchAuth { repository.login(identifier, password) }
    }

    suspend fun forgotPassword(identifier: String): Result<Unit> {
        return repository.forgotPassword(identifier)
    }

    suspend fun resetPassword(identifier: String, otp: String, newPassword: String): Result<Unit> {
        return repository.resetPassword(identifier, otp, newPassword)
    }

    fun logout() {
        launchAuth { repository.logout() }
    }

    private fun restoreSession() {
        launchAuth { repository.restoreSession() }
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
