package com.example.bai1.rescue.repository

import com.example.bai1.rescue.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface RescueMissionRepository {
    val missions: StateFlow<List<RescueMissionResponse>>
    val isLoading: StateFlow<Boolean>
    val error: StateFlow<String?>

    suspend fun fetchMyMissions()
    suspend fun getMissionDetail(id: String): RescueMissionApiResult<RescueMissionResponse>
    suspend fun updateMissionStatus(id: String, newStatus: MissionStatus): RescueMissionApiResult<RescueMissionResponse>
    suspend fun acceptRescueRequest(requestId: String): RescueMissionApiResult<RescueMissionResponse>
    suspend fun getNearbyRequests(lat: Double, lon: Double, radius: Int): RescueMissionApiResult<List<RescueMissionRequestInfo>>
    suspend fun getRescueRequestDetail(id: String): RescueMissionApiResult<RescueMissionRequestInfo>
}

class DefaultRescueMissionRepository(
    private val apiClient: RescueMissionApiClient
) : RescueMissionRepository {

    private val _missions = MutableStateFlow<List<RescueMissionResponse>>(emptyList())
    override val missions: StateFlow<List<RescueMissionResponse>> = _missions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    override suspend fun fetchMyMissions() {
        _isLoading.value = true
        _error.value = null
        when (val result = apiClient.getMyMissions()) {
            is RescueMissionApiResult.Success -> {
                _missions.value = result.data
            }
            is RescueMissionApiResult.Failure -> {
                _error.value = result.error.message
            }
        }
        _isLoading.value = false
    }

    override suspend fun getMissionDetail(id: String): RescueMissionApiResult<RescueMissionResponse> {
        return apiClient.getMissionDetail(id)
    }

    override suspend fun updateMissionStatus(id: String, newStatus: MissionStatus): RescueMissionApiResult<RescueMissionResponse> {
        val result = apiClient.updateMissionStatus(id, newStatus.name)
        if (result is RescueMissionApiResult.Success) {
            fetchMyMissions()
        }
        return result
    }

    override suspend fun acceptRescueRequest(requestId: String): RescueMissionApiResult<RescueMissionResponse> {
        val result = apiClient.acceptRescueRequest(requestId)
        if (result is RescueMissionApiResult.Success) {
            fetchMyMissions()
        }
        return result
    }

    override suspend fun getNearbyRequests(lat: Double, lon: Double, radius: Int): RescueMissionApiResult<List<RescueMissionRequestInfo>> {
        return apiClient.getNearbyRequests(lat, lon, radius)
    }
    
    override suspend fun getRescueRequestDetail(id: String): RescueMissionApiResult<RescueMissionRequestInfo> {
        return apiClient.getRescueRequestDetail(id)
    }
}
