package com.example.bai1.rescue.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bai1.rescue.network.MissionStatus
import com.example.bai1.rescue.network.RescueMissionResponse
import com.example.bai1.rescue.network.RescueMissionRequestInfo
import com.example.bai1.rescue.repository.RescueMissionRepository
import com.example.bai1.sos.repository.SosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

class RescueMissionViewModel(
    private val repository: RescueMissionRepository,
    private val sosRepository: SosRepository? = null
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

    private var lastLat: Double = 16.0544
    private var lastLon: Double = 108.2022
    private var lastRadius: Int = 50000

    init {
        // 1. Real-time sync: when any SOS event arrives in real-time, reload nearby requests immediately
        sosRepository?.let { sosRepo ->
            viewModelScope.launch {
                sosRepo.observeSos().collect {
                    loadNearbyRequests(lastLat, lastLon, lastRadius)
                }
            }
        }

        // 2. Periodic background poll every 4 seconds to guarantee real-time updates for missions & requests
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(4000)
                try {
                    repository.fetchMyMissions()
                    val result = repository.getNearbyRequests(lastLat, lastLon, lastRadius)
                    if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Success) {
                        val newReqs = result.data.filter { it.status == "PENDING" }
                        if (newReqs != _nearbyRequests.value) {
                            _nearbyRequests.value = newReqs
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

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
            _isCompleting.value = true
            val result = repository.updateMissionStatus(id, newStatus)
            if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Failure) {
                _missionActionError.value = result.error.message
            } else if (result is com.example.bai1.rescue.network.RescueMissionApiResult.Success) {
                _selectedMission.value = result.data
                loadMissionDetail(id)
            }
            _isCompleting.value = false
        }
    }

    fun loadNearbyRequests(lat: Double, lon: Double, radius: Int = 50000) {
        lastLat = lat
        lastLon = lon
        lastRadius = radius
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
                loadMissions()
                loadNearbyRequests(lastLat, lastLon, lastRadius)
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
