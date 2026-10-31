package com.example.bai1.presentation.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.repository.SosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.auth.session.AuthState
import com.example.bai1.rescue.RescueMissionAppDependencies
import com.example.bai1.rescue.network.RescueMissionApiResult
import com.example.bai1.rescue.network.RescueMissionRequestInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay


sealed class SosDetailState {
    object Loading : SosDetailState()
        data class Success(
        val sos: SosRescueInfo,
        val isRescuer: Boolean = false,
        val rescueRequest: RescueMissionRequestInfo? = null,
        val isAccepting: Boolean = false,
        val acceptError: String? = null,
        val acceptSuccess: Boolean = false
    ) : SosDetailState()
    data class Error(val message: String) : SosDetailState()
}

class SosDetailViewModel(
    private val repository: SosRepository,
    private val eventId: String
) : ViewModel() {

    private val _state = MutableStateFlow<SosDetailState>(SosDetailState.Loading)
    val state: StateFlow<SosDetailState> = _state.asStateFlow()

    init {
        loadSosDetail()
    }

    
    private fun loadSosDetail() {
        viewModelScope.launch {
            _state.value = SosDetailState.Loading
            try {
                val sos = repository.getSosById(eventId)
                if (sos != null) {
                    var isRescuer = false
                    try {
                        val authState = AuthAppDependencies.repository.state.first()
                        if (authState is AuthState.Authenticated) {
                            isRescuer = authState.user.role == "RESCUER"
                        }
                    } catch (e: Exception) { }

                    var foundRequest: RescueMissionRequestInfo? = null
                    if (isRescuer && sos.latitude != null && sos.longitude != null) {
                        try {
                            val rescueRepo = RescueMissionAppDependencies.rescueMissionRepository
                            val result = rescueRepo.getNearbyRequests(sos.latitude, sos.longitude, 50000)
                            if (result is RescueMissionApiResult.Success) {
                                foundRequest = result.data.find { it.sosEventId == eventId }
                            }
                        } catch (e: Exception) { }
                    }
                    _state.value = SosDetailState.Success(
                        sos = sos,
                        isRescuer = isRescuer,
                        rescueRequest = foundRequest
                    )
                } else {
                    _state.value = SosDetailState.Error("Không tìm thấy thông tin SOS.")
                }
            } catch (e: Exception) {
                _state.value = SosDetailState.Error(e.message ?: "Đã xảy ra lỗi.")
            }
        }
    }

    fun acceptMission() {
        val currentState = _state.value
        if (currentState !is SosDetailState.Success) return
        val requestId = currentState.rescueRequest?.id ?: return
        
        viewModelScope.launch {
            _state.value = currentState.copy(isAccepting = true, acceptError = null)
            try {
                val rescueRepo = RescueMissionAppDependencies.rescueMissionRepository
                val result = rescueRepo.acceptRescueRequest(requestId)
                if (result is RescueMissionApiResult.Success) {
                    _state.value = currentState.copy(isAccepting = false, acceptSuccess = true)
                } else if (result is RescueMissionApiResult.Failure) {
                    _state.value = currentState.copy(isAccepting = false, acceptError = result.error.message)
                }
            } catch (e: Exception) {
                _state.value = currentState.copy(isAccepting = false, acceptError = e.message ?: "Đã xảy ra lỗi.")
            }
        }
    }
    
    fun clearAcceptError() {
        val currentState = _state.value
        if (currentState is SosDetailState.Success) {
            _state.value = currentState.copy(acceptError = null)
        }
    }

}

class SosDetailViewModelFactory(
    private val repository: SosRepository,
    private val eventId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SosDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SosDetailViewModel(repository, eventId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}