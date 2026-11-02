package com.example.bai1.presentation.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bai1.rescue.network.RescueMissionApiResult
import com.example.bai1.rescue.network.RescueMissionRequestInfo
import com.example.bai1.rescue.repository.RescueMissionRepository
import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.domain.SosUrgency
import com.example.bai1.sos.repository.SosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

class DisasterMapViewModel(
    private val sosRepository: SosRepository,
    private val rescueMissionRepository: RescueMissionRepository
) : ViewModel() {

    private var currentSosList = emptyList<SosRescueInfo>()
    private var currentRequests = listOf<RescueMissionRequestInfo>()
    private val _markers = MutableStateFlow<List<MapRescueMarker>>(emptyList())
    val markers: StateFlow<List<MapRescueMarker>> = _markers.asStateFlow()

    private var lastLat: Double = 16.0544
    private var lastLon: Double = 108.2022
    private var lastRadius: Int = 50000

    init {
        // 1. Observe real-time SOS events continuously
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

        // 2. Periodic background refresh (every 4 seconds) to guarantee real-time sync with backend REST
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(4000)
                try {
                    val activeSos = sosRepository.getActiveSos()
                    if (activeSos.isNotEmpty() && activeSos != currentSosList) {
                        currentSosList = activeSos
                        updateMarkers(currentSosList, currentRequests)
                    }
                    val reqResult = rescueMissionRepository.getNearbyRequests(lastLat, lastLon, lastRadius)
                    if (reqResult is RescueMissionApiResult.Success) {
                        val newReqs = reqResult.data?.filter { it.status == "PENDING" } ?: emptyList()
                        if (newReqs != currentRequests) {
                            currentRequests = newReqs
                            updateMarkers(currentSosList, currentRequests)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun loadNearbyRequests(lat: Double, lon: Double, radius: Int = if (com.example.bai1.BuildConfig.DEBUG) 50000 else 10000) {
        lastLat = lat
        lastLon = lon
        lastRadius = radius

        // Fetch SOS via REST
        viewModelScope.launch {
            try {
                Log.d("MAP_DEBUG", "SOS_REST_REQUEST_START")
                val activeSos = sosRepository.getActiveSos()
                Log.d("MAP_DEBUG", "SOS_REST_RESPONSE_CODE=200, count=${activeSos.size}")
                currentSosList = activeSos
                updateMarkers(currentSosList, currentRequests)
            } catch (e: Exception) {
                Log.e("MAP_DEBUG", "SOS_REST_ERROR: ${e.message}", e)
            }
        }

        // Fetch Rescue Requests
        viewModelScope.launch {
            Log.d("MAP_DEBUG", "NEARBY_REQUEST: lat=$lat, lng=$lon, radius=$radius")
            val result = rescueMissionRepository.getNearbyRequests(lat, lon, radius)
            if (result is RescueMissionApiResult.Success) {
                Log.d("MAP_DEBUG", "NEARBY_RESPONSE: count=${result.data?.size}")
                currentRequests = result.data?.filter { it.status == "PENDING" } ?: emptyList()
                updateMarkers(currentSosList, currentRequests)
            } else if (result is RescueMissionApiResult.Failure) {
                Log.d("MAP_DEBUG", "NEARBY_ERROR: ${result.error.message}")
            }
        }
    }

    private fun updateMarkers(
        sosList: List<SosRescueInfo>?,
        requestList: List<RescueMissionRequestInfo>?
    ) {
        _markers.value = combineToMarkers(sosList ?: emptyList(), requestList ?: emptyList())
    }

    private fun combineToMarkers(
        sosList: List<SosRescueInfo>?,
        requestList: List<RescueMissionRequestInfo>?
    ): List<MapRescueMarker> {
        val rawMarkers = mutableListOf<MapRescueMarker>()
        val seenEventIds = mutableSetOf<String>()
        val requestsByEventId = requestList?.filter { !it.sosEventId.isNullOrBlank() }
            ?.associateBy { it.sosEventId!! } ?: emptyMap()

        // 1. Add SOS (Active, Pending or Submitted), merging with matched rescue request info if available
        sosList?.filter { 
            it.status == SosStatus.ACTIVE || it.status == SosStatus.PENDING || it.status == SosStatus.SUBMITTED 
        }?.forEach { sos ->
            seenEventIds.add(sos.eventId)
            val matchingReq = requestsByEventId[sos.eventId]

            val urgencyDisplay = sos.urgency?.displayTitle ?: SosUrgency.toDisplayTitle(matchingReq?.urgency)
            val urgencyVietnamese = sos.urgency?.vietnameseName ?: SosUrgency.toVietnamese(matchingReq?.urgency)
            val conditionStr = if (sos.condition.isNotBlank()) sos.condition else (matchingReq?.emergencyType ?: "")
            val neededItemsList = if (sos.neededItems.isNotEmpty()) sos.neededItems.toList() else (matchingReq?.neededItems ?: emptyList())
            val descStr = if (sos.description.isNotBlank()) sos.description else (matchingReq?.description ?: "")
            val peopleCount = if (sos.totalPeople > 0) sos.totalPeople else (matchingReq?.peopleCount ?: 1)

            rawMarkers.add(
                MapRescueMarker(
                    type = MarkerType.SOS,
                    eventId = sos.eventId,
                    requestId = matchingReq?.id,
                    latitude = sos.latitude ?: matchingReq?.latitude ?: 0.0,
                    longitude = sos.longitude ?: matchingReq?.longitude ?: 0.0,
                    title = "Cứu hộ khẩn cấp: $urgencyDisplay",
                    snippet = "Người: $peopleCount - ${if (conditionStr.isNotBlank()) conditionStr else descStr}",
                    adults = if (sos.adults > 0) sos.adults else (matchingReq?.adults ?: 0),
                    children = if (sos.children > 0) sos.children else (matchingReq?.children ?: 0),
                    elderly = if (sos.elderly > 0) sos.elderly else (matchingReq?.elderly ?: 0),
                    totalPeople = peopleCount,
                    urgency = urgencyVietnamese,
                    condition = conditionStr,
                    neededItems = neededItemsList,
                    description = descStr,
                    createdAt = if (sos.createdAt > 0L) sos.createdAt else parseIsoTimestamp(matchingReq?.createdAt)
                )
            )
        }

        // 2. Add standalone Rescue Requests that do NOT link to an already displayed SOS event
        requestList?.filter { 
            it.status == "PENDING" && (it.sosEventId.isNullOrBlank() || it.sosEventId !in seenEventIds) 
        }?.forEach { req ->
            val conditionStr = req.emergencyType ?: ""
            val descStr = req.description ?: ""
            val createdAtMillis = parseIsoTimestamp(req.createdAt)
            rawMarkers.add(
                MapRescueMarker(
                    type = MarkerType.RESCUE_REQUEST,
                    eventId = req.sosEventId,
                    requestId = req.id,
                    latitude = req.latitude ?: 0.0,
                    longitude = req.longitude ?: 0.0,
                    title = "Yêu cầu cứu hộ: ${SosUrgency.toDisplayTitle(req.urgency)}",
                    snippet = "Người: ${req.peopleCount} - ${if (conditionStr.isNotBlank()) conditionStr else descStr}",
                    adults = req.adults,
                    children = req.children,
                    elderly = req.elderly,
                    totalPeople = req.peopleCount,
                    urgency = SosUrgency.toVietnamese(req.urgency),
                    condition = conditionStr,
                    neededItems = req.neededItems,
                    description = descStr,
                    createdAt = createdAtMillis
                )
            )
        }

        Log.d("MAP_DEBUG", "RAW_MARKERS_COUNT=${rawMarkers.size}")
        // 3. Offset multiple distinct markers if they share the exact identical GPS coordinate
        val adjusted = adjustOverlappingMarkers(rawMarkers)
        Log.d("MAP_DEBUG", "ADJUSTED_MARKERS_COUNT=${adjusted.size}")
        return adjusted
    }

    private fun adjustOverlappingMarkers(markers: List<MapRescueMarker>): List<MapRescueMarker> {
        if (markers.size <= 1) return markers

        val clusters = mutableListOf<MutableList<MapRescueMarker>>()
        for (marker in markers) {
            var placed = false
            for (cluster in clusters) {
                val rep = cluster[0]
                val dLat = (marker.latitude - rep.latitude) * 111139.0
                val cosLat = Math.cos(Math.toRadians(rep.latitude)).coerceAtLeast(0.1)
                val dLng = (marker.longitude - rep.longitude) * 111139.0 * cosLat
                val distMeters = Math.hypot(dLat, dLng)
                if (distMeters < 12.0) { // Cùng tọa độ hoặc trong phạm vi trùng nhau (< 12m)
                    cluster.add(marker)
                    placed = true
                    break
                }
            }
            if (!placed) {
                clusters.add(mutableListOf(marker))
            }
        }

        val result = mutableListOf<MapRescueMarker>()
        for (cluster in clusters) {
            if (cluster.size == 1) {
                result.add(cluster[0])
            } else {
                val count = cluster.size
                val centerLat = cluster.map { it.latitude }.average()
                val centerLng = cluster.map { it.longitude }.average()
                val cosLat = Math.cos(Math.toRadians(centerLat)).coerceAtLeast(0.1)

                // Tách các icon cách nhau ~10 mét (tránh xa quá 20m)
                val targetDistanceMeters = 10.0
                val radiusMeters = if (count == 2) {
                    targetDistanceMeters / 2.0 // 5.0m => 2 marker đối diện cách nhau đúng 10m
                } else {
                    (targetDistanceMeters / (2.0 * Math.sin(Math.PI / count))).coerceIn(5.0, 15.0)
                }

                val radiusLat = radiusMeters / 111139.0
                val radiusLng = radiusMeters / (111139.0 * cosLat)

                cluster.forEachIndexed { index, marker ->
                    val angle = 2.0 * Math.PI * index / count
                    val offsetLat = radiusLat * Math.cos(angle)
                    val offsetLng = radiusLng * Math.sin(angle)
                    result.add(
                        marker.copy(
                            latitude = centerLat + offsetLat,
                            longitude = centerLng + offsetLng
                        )
                    )
                }
            }
        }
        return result
    }

    private fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-ddTHH:mm:ss", Locale.US).parse(isoString)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
