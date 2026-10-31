package com.example.bai1.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RoleSelectionState {
    data object Idle : RoleSelectionState
    data object Loading : RoleSelectionState
    data object Success : RoleSelectionState
    data class Error(val message: String) : RoleSelectionState
}

class RoleSelectionViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoleSelectionState>(RoleSelectionState.Idle)
    val uiState: StateFlow<RoleSelectionState> = _uiState.asStateFlow()

    fun selectRole(role: String) {
        _uiState.value = RoleSelectionState.Loading
        viewModelScope.launch {
            repository.updateRole(role).fold(
                onSuccess = {
                    _uiState.value = RoleSelectionState.Success
                },
                onFailure = {
                    _uiState.value = RoleSelectionState.Error(it.message ?: "Lỗi không xác định")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = RoleSelectionState.Idle
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoleSelectionViewModel(repository) as T
                }
            }
    }
}
