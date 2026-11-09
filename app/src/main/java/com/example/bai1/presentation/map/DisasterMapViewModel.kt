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
import com.example.bai1.rescue.routing.algorithm.DfaBuildResult
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.UniformCostSearchRouter
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.diagnostic.DiagnosticGraphProvider
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.UcsRouteResult
import com.example.bai1.rescue.routing.model.UcsRouteStatus
import com.google.android.gms.maps.model.LatLng

data class RouteUiState(
    val isLoading: Boolean = false,
    val routeResult: UcsRouteResult? = null,
    val polylinePoints: List<LatLng> = emptyList(),
    val pathGeometry: List<GeoPoint> = emptyList(),
    val startPoint: LatLng? = null,
    val destinationPoint: LatLng? = null,
    val startNodeId: String? = null,
    val goalNodeId: String? = null,
    val totalCost: Double? = null,
    val totalDistanceMeters: Double? = null,
    val statusMessage: String? = null,
    val isDiagnosticSimulation: Boolean = true,
    val isSimulatedRescuerLocation: Boolean = false,
    val errorMessage: String? = null
)

class DisasterMapViewModel(
    private val sosRepository: SosRepository,
    private val rescueMissionRepository: RescueMissionRepository,
    private val pollingEnabled: Boolean = true,
    private val nearbyRequestsEnabled: Boolean = true,
    private val router: UniformCostSearchRouter = UniformCostSearchRouter(),
    private var roadGraph: RoadGraph = DiagnosticGraphProvider.getDaNangGraph(),
    private val context: android.content.Context? = null
) : ViewModel() {

    private var currentSosList = emptyList<SosRescueInfo>()
    private var currentRequests = listOf<RescueMissionRequestInfo>()
    private val _markers = MutableStateFlow<List<MapRescueMarker>>(emptyList())
    val markers: StateFlow<List<MapRescueMarker>> = _markers.asStateFlow()

    private val _routeState = MutableStateFlow<RouteUiState?>(null)
    val routeState: StateFlow<RouteUiState?> = _routeState.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()
    private var lastLat: Double = 16.0544
    private var lastLon: Double = 108.2022
    private var lastRadius: Int = 50000

    init {
        // Gán đồ thị OSM ngay lập tức nếu đã được nạp sẵn dưới nền từ khi mở ứng dụng
        val preloadedGraph = DiagnosticGraphProvider.getCachedGraph()
        if (preloadedGraph != null && preloadedGraph.nodes.size > roadGraph.nodes.size) {
            roadGraph = preloadedGraph
            Log.i("DisasterMapViewModel", "Pre-warmed RoadGraph attached immediately: ${preloadedGraph.graphVersion}, nodes=${preloadedGraph.nodes.size}")
        } else if (context != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val fullGraph = DiagnosticGraphProvider.getRealOrDiagnosticGraph(context)
                    if (fullGraph.nodes.size > roadGraph.nodes.size) {
                        roadGraph = fullGraph
                        Log.i("DisasterMapViewModel", "Loaded expanded RoadGraph: ${fullGraph.graphVersion}, nodes=${fullGraph.nodes.size}, edges=${fullGraph.directedEdges.size}")
                    }
                } catch (e: Exception) {
                    Log.w("DisasterMapViewModel", "Async load of real graph fallback: ${e.message}")
                }
            }
        }
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

        if (pollingEnabled) viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(4000)
                refreshSos()
                if (nearbyRequestsEnabled) refreshNearby()
            }
        }
    }

    private suspend fun refreshSos() {
        try {
            val activeSos = sosRepository.getActiveSos()
            if (activeSos.isNotEmpty() && activeSos != currentSosList) {
                currentSosList = activeSos
                updateMarkers(currentSosList, currentRequests)
            }
            _refreshError.value = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            _refreshError.value = "Không thể tải SOS. Vui lòng thử lại."
        }
    }
    private suspend fun refreshNearby() {
        when (val result = rescueMissionRepository.getNearbyRequests(lastLat,lastLon,lastRadius)) {
            is RescueMissionApiResult.Success -> {
                val newReqs = result.data.filter { it.status == "PENDING" }
                if (newReqs != currentRequests) {
                    currentRequests = newReqs
                    updateMarkers(currentSosList, currentRequests)
                }
            }
            is RescueMissionApiResult.Failure -> Unit
        }
    }

    fun loadNearbyRequests(lat: Double, lon: Double, radius: Int = if (com.example.bai1.BuildConfig.DEBUG) 50000 else 10000) {
        lastLat = lat
        lastLon = lon
        lastRadius = radius

        viewModelScope.launch { refreshSos() }
        if (nearbyRequestsEnabled) viewModelScope.launch { refreshNearby() }
    }

    fun setRoadGraphForTesting(graph: RoadGraph) {
        this.roadGraph = graph
    }

    fun computeRouteToMarker(
        marker: MapRescueMarker,
        rescuerLocation: LatLng? = null,
        isSimulatedRescuerLocation: Boolean = false
    ) {
        val destLatLng = LatLng(marker.latitude, marker.longitude)

        // 1. Kiểm tra vị trí của cứu hộ viên: TUYỆT ĐỐI KHÔNG âm thầm dùng N_BASE!
        if (rescuerLocation == null ||
            !rescuerLocation.latitude.isFinite() || !rescuerLocation.longitude.isFinite() ||
            (rescuerLocation.latitude == 0.0 && rescuerLocation.longitude == 0.0)
        ) {
            _routeState.value = RouteUiState(
                isLoading = false,
                destinationPoint = destLatLng,
                isDiagnosticSimulation = true,
                isSimulatedRescuerLocation = false,
                errorMessage = "Chưa xác định được vị trí của cứu hộ viên. Vui lòng bật GPS hoặc chọn vị trí mô phỏng."
            )
            return
        }

        computeRouteBetweenPoints(
            startPoint = rescuerLocation,
            destinationPoint = destLatLng,
            isSimulatedRescuerLocation = isSimulatedRescuerLocation
        )
    }

    fun computeRouteBetweenPoints(
        startPoint: LatLng,
        destinationPoint: LatLng,
        isSimulatedRescuerLocation: Boolean = false
    ) {
        viewModelScope.launch {
            computeRouteInternal(startPoint, destinationPoint, isSimulatedRescuerLocation)
        }
    }

    private suspend fun computeRouteInternal(
        startLatLng: LatLng,
        destLatLng: LatLng,
        isSimulatedRescuerLocation: Boolean
    ) {
        _routeState.value = RouteUiState(
            isLoading = true,
            startPoint = startLatLng,
            destinationPoint = destLatLng,
            isDiagnosticSimulation = true,
            isSimulatedRescuerLocation = isSimulatedRescuerLocation
        )

        fun doCompute(): RouteUiState {
                // 0. Đảm bảo nạp xong đồ thị thực tế trước khi tính toán nếu đang dùng đồ thị fixture
                if (roadGraph.nodes.size <= 8) {
                    val cached = DiagnosticGraphProvider.getCachedGraph()
                    if (cached != null && cached.nodes.size > roadGraph.nodes.size) {
                        roadGraph = cached
                    } else if (context != null) {
                        try {
                            val fullGraph = DiagnosticGraphProvider.getRealOrDiagnosticGraph(context)
                            if (fullGraph.nodes.size > roadGraph.nodes.size) {
                                roadGraph = fullGraph
                            }
                        } catch (_: Exception) {}
                    }
                }

                val startGeo = GeoPoint(startLatLng.latitude, startLatLng.longitude)
                val destGeo = GeoPoint(destLatLng.latitude, destLatLng.longitude)

                // 2. Kiểm tra độ bao phủ của đồ thị (Graph Coverage Check) với buffer 5000m
                if (!router.isPointWithinCoverage(roadGraph, startGeo, bufferMeters = 5000.0)) {
                    return RouteUiState(
                        isLoading = false,
                        startPoint = startLatLng,
                        destinationPoint = destLatLng,
                        isDiagnosticSimulation = true,
                        isSimulatedRescuerLocation = isSimulatedRescuerLocation,
                        errorMessage = "Vị trí cứu hộ viên nằm ngoài phạm vi đồ thị đường bộ (GRAPH_COVERAGE_MISSING)."
                    )
                }

                if (!router.isPointWithinCoverage(roadGraph, destGeo, bufferMeters = 5000.0)) {
                    return RouteUiState(
                        isLoading = false,
                        startPoint = startLatLng,
                        destinationPoint = destLatLng,
                        isDiagnosticSimulation = true,
                        isSimulatedRescuerLocation = isSimulatedRescuerLocation,
                        errorMessage = "Vị trí người gửi SOS nằm ngoài phạm vi đồ thị đường bộ (GRAPH_COVERAGE_MISSING)."
                    )
                }

                // 3. Snapping với bán kính hợp lý (2500m)
                val maxSnapRadius = 2500.0
                val startNode = router.findNearestNode(roadGraph, startGeo, maxRadiusMeters = maxSnapRadius)
                val goalNode = router.findNearestNode(roadGraph, destGeo, maxRadiusMeters = maxSnapRadius)

                if (startNode == null) {
                    return RouteUiState(
                        isLoading = false,
                        startPoint = startLatLng,
                        destinationPoint = destLatLng,
                        isDiagnosticSimulation = true,
                        isSimulatedRescuerLocation = isSimulatedRescuerLocation,
                        errorMessage = "Không tìm thấy nút giao thông gần vị trí cứu hộ viên trong bán kính ${maxSnapRadius.toInt()}m (START_NOT_SNAPPED)."
                    )
                }

                if (goalNode == null) {
                    return RouteUiState(
                        isLoading = false,
                        startPoint = startLatLng,
                        destinationPoint = destLatLng,
                        startNodeId = startNode.id,
                        isDiagnosticSimulation = true,
                        isSimulatedRescuerLocation = isSimulatedRescuerLocation,
                        errorMessage = "Không tìm thấy nút giao thông gần vị trí SOS trong bán kính ${maxSnapRadius.toInt()}m (GOAL_NOT_SNAPPED)."
                    )
                }

                // 4. Nếu cứu hộ viên và người gửi SOS cùng ở tại một nút
                if (startNode.id == goalNode.id) {
                    val directDist = router.distanceMeters(startGeo, destGeo)
                    return RouteUiState(
                        isLoading = false,
                        polylinePoints = listOf(startLatLng, destLatLng),
                        pathGeometry = listOf(startGeo, destGeo),
                        startPoint = startLatLng,
                        destinationPoint = destLatLng,
                        startNodeId = startNode.id,
                        goalNodeId = goalNode.id,
                        totalCost = directDist,
                        totalDistanceMeters = directDist,
                        statusMessage = "Cứu hộ viên đã ở ngay tại khu vực người gửi SOS (cùng nút giao thông).",
                        isDiagnosticSimulation = true,
                        isSimulatedRescuerLocation = isSimulatedRescuerLocation
                    )
                }

                // 5. Thực thi UCS (Tận dụng assets đã tiền tính toán để chạy trong <15ms)
                val (precomputedDfa, precomputedTurnIndex) = DiagnosticGraphProvider.getPrecomputedRoutingAssets()
                val dfa = if (precomputedDfa != null && roadGraph == DiagnosticGraphProvider.getCachedGraph()) {
                    precomputedDfa
                } else {
                    when (val res = ViaWayTurnRestrictionDfa.build(roadGraph.turnRestrictions, roadGraph)) {
                        is DfaBuildResult.Success -> res.dfa
                        else -> ViaWayTurnRestrictionDfa.empty()
                    }
                }
                val turnIndex = if (precomputedTurnIndex != null && roadGraph == DiagnosticGraphProvider.getCachedGraph()) {
                    precomputedTurnIndex
                } else {
                    TurnRestrictionIndex.build(roadGraph, dfa.acceptedRestrictionIds)
                }

                val result = router.computeDiagnosticRoute(
                    roadGraph = roadGraph,
                    startNodeId = startNode.id,
                    goalNodeId = goalNode.id,
                    turnRestrictionIndex = turnIndex,
                    viaWayDfa = dfa,
                    allowUnverifiedForDiagnostic = true
                )

                return if (result.status == UcsRouteStatus.SUCCESS) {
                    // Tạo danh sách điểm Polyline nối thông suốt: Điểm cứu hộ -> Tuyến đường đồ thị -> Điểm SOS
                    val polyPoints = mutableListOf<LatLng>()
                    polyPoints.add(startLatLng)
                    result.pathGeometry.forEach { pt ->
                        polyPoints.add(LatLng(pt.latitude, pt.longitude))
                    }
                    polyPoints.add(destLatLng)

                    RouteUiState(
                        isLoading = false,
                        routeResult = result,
                        polylinePoints = polyPoints,
                        pathGeometry = result.pathGeometry,
                        startPoint = startLatLng,
                        destinationPoint = destLatLng,
                        startNodeId = startNode.id,
                        goalNodeId = goalNode.id,
                        totalCost = result.totalCost,
                        totalDistanceMeters = result.totalDistanceMeters,
                        statusMessage = "Tìm thấy tuyến đường UCS (${result.pathNodeIds.size} nút, ${result.pathEdges.size} đoạn đường)",
                        isDiagnosticSimulation = true,
                        isSimulatedRescuerLocation = isSimulatedRescuerLocation,
                        errorMessage = null
                    )
                } else if (result.status == UcsRouteStatus.NO_PATH) {
                    RouteUiState(
                        isLoading = false,
                        routeResult = result,
                        polylinePoints = emptyList(),
                        pathGeometry = emptyList(),
                        startPoint = startLatLng,
                        destinationPoint = destLatLng,
                        startNodeId = startNode.id,
                        goalNodeId = goalNode.id,
                        totalCost = null,
                        totalDistanceMeters = null,
                        statusMessage = "Không có tuyến đường khả dụng đến vị trí SOS này",
                        isDiagnosticSimulation = true,
                        isSimulatedRescuerLocation = isSimulatedRescuerLocation,
                        errorMessage = "NO_PATH: Tuyến đường bị phong tỏa hoặc không có đường nối hợp lệ."
                    )
                } else {
                    RouteUiState(
                        isLoading = false,
                        routeResult = result,
                        polylinePoints = emptyList(),
                        pathGeometry = emptyList(),
                        startPoint = startLatLng,
                        destinationPoint = destLatLng,
                        startNodeId = startNode.id,
                        goalNodeId = goalNode.id,
                        totalCost = null,
                        totalDistanceMeters = null,
                        statusMessage = "Lỗi tìm đường: ${result.status}",
                        isDiagnosticSimulation = true,
                        isSimulatedRescuerLocation = isSimulatedRescuerLocation,
                        errorMessage = "Lỗi tìm đường UCS: ${result.status}"
                    )
                }
            }

            val computedState = if (context != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    doCompute()
                }
            } else {
                doCompute()
            }

            _routeState.value = computedState
    }

    fun clearRoute() {
        _routeState.value = null
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

        // Native marker geography stays equal to the server. Never move LatLng to spread icons.
        return rawMarkers.filter { it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
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
