package com.example.bai1.rescue.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bai1.rescue.network.MissionStatus
import com.example.bai1.rescue.network.RescueMissionResponse
import com.example.bai1.rescue.network.RescueMissionRequestInfo
import com.example.bai1.rescue.repository.RescueMissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RescueMissionViewModel(
    private val repository: RescueMissionRepository
) : ViewModel() {

    val missions: StateFlow<List<RescueMissionResponse>> = repository.missions
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val error: StateFlow<String?> = repository.error

    private val _nearbyRequests = MutableStateFlow<List<RescueMissionRequestInfo>>(emptyList())
    val nearbyRequests: StateFlow<List<RescueMissionRequestInfo>> = _nearbyRequests.asStateFlow()


    private val _selectedMission = MutableStateFlow<RescueMissionResponse?>(null)
    val selectedMission: StateFlow<RescueMissionResponse?> = _selectedMission.asStateFlow()

    private val _selectedRequest = MutableStateFlow<RescueMissionRequestInfo?>(null)
    val selectedRequest: StateFlow<RescueMissionRequestInfo?> = _selectedRequest.asStateFlow()


    private val _missionActionError = MutableStateFlow<String?>(null)
    val missionActionError: StateFlow<String?> = _missionActionError.asStateFlow()

    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()

    fun loadMissions() {
        viewModelScope.launch {
            repository.fetchMyMissions()
        }
    }

    fun loadMissionDetail(id: String) {
        viewModelScope.launch {
            val result = repository.getMissionDetail(id)
            if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Success) {
                _selectedMission.value = result.data
            } else if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Failure) {
                _missionActionError.value = result.error.message
            }
        }
    }

    fun updateMissionStatus(id: String, newStatus: MissionStatus) {
        viewModelScope.launch {
            _missionActionError.value = null
            val result = repository.updateMissionStatus(id, newStatus)
            if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Failure) {
                _missionActionError.value = result.error.message
            } else {
                // Refresh detail if needed
                if (_selectedMission.value?.id == id) {
                    loadMissionDetail(id)
                }
            }
        }
    }
    

    fun loadNearbyRequests(lat: Double, lon: Double, radius: Int = 10000) {
        viewModelScope.launch {
            _missionActionError.value = null
            val result = repository.getNearbyRequests(lat, lon, radius)
            if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Success) {
                _nearbyRequests.value = result.data.filter { it.status == "PENDING" }
            } else if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Failure) {
                _missionActionError.value = result.error.message
            }
        }
    }


    fun loadRequestDetail(id: String) {
        viewModelScope.launch {
            _missionActionError.value = null
            // Preserve distance and dates from the list if available since detail API doesn't return them
            val localCached = _nearbyRequests.value.find { it.id == id }
            
            val result = repository.getRescueRequestDetail(id)
            if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Success) {
                var finalData = result.data
                if (localCached != null) {
                    finalData = finalData.copy(
                        distance = localCached.distance,
                        createdAt = localCached.createdAt,
                        updatedAt = localCached.updatedAt
                    )
                }
                _selectedRequest.value = finalData
            } else if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Failure) {
                // If network fails but we have it locally, use local as fallback
                if (localCached != null) {
                    _selectedRequest.value = localCached
                } else {
                    _missionActionError.value = result.error.message
                }
            }
        }
    }

    fun acceptRequest(requestId: String) {
        viewModelScope.launch {
            _missionActionError.value = null
            val result = repository.acceptRescueRequest(requestId)
            if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Success) {
                // Request accepted successfully! reload missions
                loadMissions()
            } else if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Failure) {
                _missionActionError.value = result.error.message
            }
        }
    }
    
    fun completeMission(missionId: String) {
        if (_isCompleting.value) return
        viewModelScope.launch {
            _isCompleting.value = true
            _missionActionError.value = null
            val result = repository.updateMissionStatus(missionId, MissionStatus.COMPLETED)
            if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Success) {
                _selectedMission.value = result.data
            } else if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Failure) {
                _missionActionError.value = result.error.message
            }
            _isCompleting.value = false
        }
    }
    
    fun clearMissionActionError() {
        _missionActionError.value = null
    }

    fun clearError() {
        _missionActionError.value = null
    }
}
