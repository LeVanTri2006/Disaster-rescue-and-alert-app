package com.example.bai1.presentation.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bai1.rescue.network.RescueMissionApiResult
import com.example.bai1.rescue.repository.RescueMissionRepository
import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.repository.SosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DisasterMapViewModel(
    private val sosRepository: SosRepository,
    private val rescueMissionRepository: RescueMissionRepository
) : ViewModel() {

    private var currentSosList = emptyList<com.example.bai1.sos.domain.SosRescueInfo>()
    private val _markers = MutableStateFlow<List<MapRescueMarker>>(emptyList())
    val markers: StateFlow<List<MapRescueMarker>> = _markers.asStateFlow()

    init {
        // Observe SOS events continuously
        viewModelScope.launch {
            sosRepository.observeSos().collect { sosList ->
                Log.d("MAP_DEBUG", "OBSERVE_SOS_EMIT: count=${sosList.size}, statuses=${sosList.map { it.status }}")
                currentSosList = sosList
                updateMarkers(
                    sosList = sosList,
                    requestList = currentRequests
                )
            }
        }
    }

    private var currentRequests = listOf<com.example.bai1.rescue.network.RescueMissionRequestInfo>()

    fun loadNearbyRequests(lat: Double, lon: Double, radius: Int = if (com.example.bai1.BuildConfig.DEBUG) 50000 else 10000) {
        // LUONG A: Fetch SOS doc lap
        viewModelScope.launch {
            try {
                Log.d("MAP_DEBUG", "SOS_REST_REQUEST_START")
                val activeSos = sosRepository.getActiveSos()
                Log.d("MAP_DEBUG", "SOS_REST_RESPONSE_CODE=200")
                Log.d("MAP_DEBUG", "SOS_REST_COUNT=${activeSos.size}")
            } catch (e: Exception) {
                Log.e("MAP_DEBUG", "SOS_REST_ERROR: ${e.message}", e)
            }
        }

        // LUONG B: Fetch Rescue Requests
        viewModelScope.launch {
            Log.d("MAP_DEBUG", "NEARBY_REQUEST: lat=$lat, lng=$lon, radius=$radius")
            val result = rescueMissionRepository.getNearbyRequests(lat, lon, radius)
            if (result is RescueMissionApiResult.Success) {
                Log.d("MAP_DEBUG", "NEARBY_RESPONSE: count=${result.data?.size}")
                currentRequests = result.data ?: emptyList()
                updateMarkers(currentSosList, currentRequests)
            } else if (result is RescueMissionApiResult.Failure) {
                Log.d("MAP_DEBUG", "NEARBY_ERROR: ${result.error.message}")
            }
        }
    }

    private fun updateMarkers(
        sosList: List<com.example.bai1.sos.domain.SosRescueInfo>?,
        requestList: List<com.example.bai1.rescue.network.RescueMissionRequestInfo>?
    ) {
        _markers.value = combineToMarkers(sosList ?: emptyList(), requestList ?: emptyList())
    }

    private fun combineToMarkers(
        sosList: List<com.example.bai1.sos.domain.SosRescueInfo>?,
        requestList: List<com.example.bai1.rescue.network.RescueMissionRequestInfo>?
    ): List<MapRescueMarker> {
        val result = mutableListOf<MapRescueMarker>()
        
        // Add SOS
        sosList?.filter { it.status == SosStatus.ACTIVE }?.forEach { sos ->
            result.add(
                MapRescueMarker(
                    type = MarkerType.SOS,
                    eventId = sos.eventId,
                    requestId = null,
                    latitude = sos.latitude ?: 0.0,
                    longitude = sos.longitude ?: 0.0,
                    title = "Cứu hộ khẩn cấp: ${sos.urgency?.name ?: "UNKNOWN"}",
                    snippet = "Người: ${sos.totalPeople} - ${sos.condition}",
                    adults = sos.adults,
                    children = sos.children,
                    elderly = sos.elderly,
                    totalPeople = sos.totalPeople,
                    urgency = sos.urgency?.name ?: "UNKNOWN",
                    condition = sos.condition ?: "",
                    neededItems = sos.neededItems?.toList() ?: emptyList(),
                    description = sos.description ?: "",
                    createdAt = sos.createdAt
                )
            )
        }
        
        Log.d("MAP_DEBUG", "COMBINE_SOS_INPUT=${sosList?.size}, ACTIVE_FILTERED=${sosList?.count { it.status == SosStatus.ACTIVE }}, SOS_MARKERS_ADDED=${result.size}")
        
        // Add Rescue Requests
        requestList?.filter { it.status == "PENDING" }?.forEach { req ->
            result.add(
                MapRescueMarker(
                    type = MarkerType.RESCUE_REQUEST,
                    eventId = req.sosEventId,
                    requestId = req.id,
                    latitude = req.latitude ?: 0.0,
                    longitude = req.longitude ?: 0.0,
                    title = "Yêu cầu cứu hộ: ${req.urgency}",
                    snippet = "Người: ${req.peopleCount} - ${req.description ?: "Không có mô tả"}",
                    totalPeople = req.peopleCount,
                    urgency = req.urgency,
                    description = req.description ?: "",
                    createdAt = 0L // if not parsed from backend yet
                )
            )
        }
        
        Log.d("MAP_DEBUG", "SOS_MERGE_COUNT=${result.count { it.type == MarkerType.SOS }}")
        Log.d("MAP_DEBUG", "MAP_MARKER_COUNT=${result.size}")
        Log.d("MAP_DEBUG", "TOTAL_MARKERS=${result.size} (SOS=${result.count { it.type == MarkerType.SOS }}, RESCUE=${result.count { it.type == MarkerType.RESCUE_REQUEST }})")
        result.forEachIndexed { i, m -> Log.d("MAP_DEBUG", "  MARKER[$i] type=${m.type} id=${m.eventId ?: m.requestId} lat=${m.latitude} lng=${m.longitude}") }
        return result
    }
}
