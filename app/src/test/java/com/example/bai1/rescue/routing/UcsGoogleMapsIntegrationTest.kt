package com.example.bai1.rescue.routing

import com.example.bai1.presentation.map.DisasterMapViewModel
import com.example.bai1.presentation.map.MapRescueMarker
import com.example.bai1.presentation.map.MarkerType
import com.example.bai1.rescue.network.MissionStatus
import com.example.bai1.rescue.network.RescueMissionApiError
import com.example.bai1.rescue.network.RescueMissionApiErrorKind
import com.example.bai1.rescue.network.RescueMissionApiResult
import com.example.bai1.rescue.network.RescueMissionRequestInfo
import com.example.bai1.rescue.network.RescueMissionResponse
import com.example.bai1.rescue.repository.RescueMissionRepository
import com.example.bai1.rescue.routing.algorithm.DfaBuildResult
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.UniformCostSearchRouter
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.diagnostic.DiagnosticGraphProvider
import com.example.bai1.rescue.routing.model.EdgeCompatibilityStatus
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.RoadCondition
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.UcsRouteStatus
import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.repository.SosRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UcsGoogleMapsIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var router: UniformCostSearchRouter
    private lateinit var diagnosticGraph: RoadGraph

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        DiagnosticGraphProvider.clearCache()
        router = UniformCostSearchRouter()
        diagnosticGraph = DiagnosticGraphProvider.getDaNangGraph("normal")
    }

    @After
    fun tearDown() {
        DiagnosticGraphProvider.clearCache()
        Dispatchers.resetMain()
    }

    // =========================================================================
    // UCS SNAPPING TESTS (UCS-T01 .. UCS-T04)
    // =========================================================================

    @Test
    fun `UCS-T01 Router snaps start coordinate (Rescuer Base) to nearest graph node`() {
        val rescuerPosition = GeoPoint(16.0501, 108.2001) // Near N_BASE (16.0500, 108.2000)
        val nearest = router.findNearestNode(diagnosticGraph, rescuerPosition, maxRadiusMeters = 500.0)
        assertNotNull(nearest)
        assertEquals("N_BASE", nearest?.id)
    }

    @Test
    fun `UCS-T02 Router snaps destination coordinate (SOS marker) to nearest graph node`() {
        val sosPosition = GeoPoint(16.0544, 108.2022) // Exact coordinates of N_BACH_DANG
        val nearest = router.findNearestNode(diagnosticGraph, sosPosition, maxRadiusMeters = 500.0)
        assertNotNull(nearest)
        assertEquals("N_BACH_DANG", nearest?.id)
    }

    @Test
    fun `UCS-T03 Snapping fails gracefully when point is beyond maxRadiusMeters`() {
        val farawayPosition = GeoPoint(21.0285, 105.8542) // Hanoi
        val nearest = router.findNearestNode(diagnosticGraph, farawayPosition, maxRadiusMeters = 1000.0)
        assertNull("Points outside Da Nang network radius must return null", nearest)
    }

    @Test
    fun `UCS-T04 Snapping chooses closest node by Haversine distance when multiple nodes exist`() {
        val nearDuyTan = GeoPoint(16.05105, 108.20105) // Right beside Duy Tan (16.0510, 108.2010)
        val nearest = router.findNearestNode(diagnosticGraph, nearDuyTan, maxRadiusMeters = 1000.0)
        assertNotNull(nearest)
        assertEquals("N_DUY_TAN", nearest?.id)
    }

    // =========================================================================
    // UCS ROUTE EXECUTION & LEAST-COST TESTS (UCS-T05 .. UCS-T09)
    // =========================================================================

    @Test
    fun `UCS-T05 Diagnostic UCS route calculates successfully between Base and Bach Dang SOS`() {
        val result = router.computeDiagnosticRoute(
            roadGraph = diagnosticGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = true
        )
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertEquals("N_BASE", result.pathNodeIds.first())
        assertEquals("N_BACH_DANG", result.pathNodeIds.last())
        assertTrue("Total cost must be positive", result.totalCost > 0.0)
        assertTrue("Total distance must be positive", result.totalDistanceMeters > 0.0)
        assertTrue("Path edges must not be empty", result.pathEdges.isNotEmpty())
        assertTrue("Path geometry must contain points", result.pathGeometry.isNotEmpty())
    }

    @Test
    fun `UCS-T06 UCS route selects least-cost path over alternative higher-cost paths`() {
        // Normal scenario: Direct route via Duy Tan -> Le Duan -> Bach Dang
        val result = router.computeDiagnosticRoute(
            roadGraph = diagnosticGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = true
        )
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        // Path should take the shorter/optimal Duy Tan -> Le Duan route
        assertEquals(listOf("N_BASE", "N_DUY_TAN", "N_LE_DUAN", "N_BACH_DANG"), result.pathNodeIds)
    }

    @Test
    fun `UCS-T07 UCS route correctly respects turn restrictions`() {
        val graphWithRestriction = DiagnosticGraphProvider.getDaNangGraph("turn_restriction")
        val dfa = when (val res = ViaWayTurnRestrictionDfa.build(graphWithRestriction.turnRestrictions, graphWithRestriction)) {
            is DfaBuildResult.Success -> res.dfa
            else -> ViaWayTurnRestrictionDfa.empty()
        }
        val turnIndex = TurnRestrictionIndex.build(graphWithRestriction, dfa.acceptedRestrictionIds)

        val result = router.computeDiagnosticRoute(
            roadGraph = graphWithRestriction,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            turnRestrictionIndex = turnIndex,
            viaWayDfa = dfa,
            allowUnverifiedForDiagnostic = true
        )
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        // Since Duy Tan -> Le Duan left turn is forbidden, path diverts via Nguyen Van Linh -> Tran Phu
        assertFalse("Must NOT transition directly from N_DUY_TAN to N_LE_DUAN",
            result.pathNodeIds.contains("N_DUY_TAN") && result.pathNodeIds.contains("N_LE_DUAN") &&
            result.pathNodeIds.indexOf("N_LE_DUAN") == result.pathNodeIds.indexOf("N_DUY_TAN") + 1
        )
        assertEquals("N_BASE", result.pathNodeIds.first())
        assertEquals("N_BACH_DANG", result.pathNodeIds.last())
        assertTrue(result.turnRestrictionsSkipped >= 1)
    }

    @Test
    fun `UCS-T08 UCS route diverts when a road segment is BLOCKED`() {
        val blockedGraph = DiagnosticGraphProvider.getDaNangGraph("blocked_leduan")
        val result = router.computeDiagnosticRoute(
            roadGraph = blockedGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = true
        )
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        // Le Duan is blocked, so path must divert through Tran Phu or Cau Rong
        assertFalse("Must not traverse through blocked Le Duan to Bach Dang",
            result.pathEdges.any { it.id == "E_LEDUAN_BACH_DANG" })
        assertTrue("Blocked edges must be skipped", result.blockedEdgesSkipped >= 1)
        assertEquals("N_BACH_DANG", result.pathNodeIds.last())
    }

    @Test
    fun `UCS-T09 UCS returns NO_PATH when destination is completely isolated`() {
        // Create graph where all incoming edges to N_BACH_DANG are blocked
        val isolatedEdges = diagnosticGraph.directedEdges.map { edge ->
            if (edge.toNodeId == "N_BACH_DANG") edge.copy(condition = RoadCondition.BLOCKED) else edge
        }
        val isolatedGraph = RoadGraph(
            nodes = diagnosticGraph.nodes,
            directedEdges = isolatedEdges,
            graphVersion = diagnosticGraph.graphVersion
        )

        val result = router.computeDiagnosticRoute(
            roadGraph = isolatedGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = true
        )
        assertEquals(UcsRouteStatus.NO_PATH, result.status)
        assertTrue(result.pathNodeIds.isEmpty())
        assertTrue(result.pathGeometry.isEmpty())
    }

    // =========================================================================
    // GEOMETRY & GOOGLE MAPS POLYLINE CONVERSION TESTS (UCS-T10 .. UCS-T13)
    // =========================================================================

    @Test
    fun `UCS-T10 UCS pathGeometry contains valid, ordered coordinates without nulls or NaNs`() {
        val result = router.computeDiagnosticRoute(
            roadGraph = diagnosticGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = true
        )
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertTrue("pathGeometry must have at least 2 points", result.pathGeometry.size >= 2)
        result.pathGeometry.forEach { pt ->
            assertTrue("Point must be valid WGS84", pt.isValid)
            assertFalse("Lat must not be NaN", pt.latitude.isNaN())
            assertFalse("Lon must not be NaN", pt.longitude.isNaN())
        }
    }

    @Test
    fun `UCS-T11 Conversion from GeoPoint pathGeometry to Google Maps LatLng is 1 to 1 and lossless`() {
        val result = router.computeDiagnosticRoute(
            roadGraph = diagnosticGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = true
        )
        val latLngList = result.pathGeometry.map { LatLng(it.latitude, it.longitude) }
        assertEquals(result.pathGeometry.size, latLngList.size)
        for (i in result.pathGeometry.indices) {
            assertEquals(result.pathGeometry[i].latitude, latLngList[i].latitude, 0.0000001)
            assertEquals(result.pathGeometry[i].longitude, latLngList[i].longitude, 0.0000001)
        }
    }

    @Test
    fun `UCS-T12 Polyline points match start node position as first point and goal node as last point`() {
        val result = router.computeDiagnosticRoute(
            roadGraph = diagnosticGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = true
        )
        val latLngList = result.pathGeometry.map { LatLng(it.latitude, it.longitude) }
        val startNode = diagnosticGraph.nodes["N_BASE"]!!
        val goalNode = diagnosticGraph.nodes["N_BACH_DANG"]!!

        assertEquals(startNode.lat, latLngList.first().latitude, 0.0000001)
        assertEquals(startNode.lon, latLngList.first().longitude, 0.0000001)
        assertEquals(goalNode.lat, latLngList.last().latitude, 0.0000001)
        assertEquals(goalNode.lon, latLngList.last().longitude, 0.0000001)
    }

    @Test
    fun `UCS-T13 Total distance meters matches the sum of edge segment lengths in pathGeometry`() {
        val result = router.computeDiagnosticRoute(
            roadGraph = diagnosticGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = true
        )
        val edgeLengthSum = result.pathEdges.sumOf { it.lengthMeters }
        assertEquals(edgeLengthSum, result.totalDistanceMeters, 0.01)
    }

    // =========================================================================
    // SAFETY GATES & PRODUCTION FAIL-CLOSED TESTS (UCS-T14 .. UCS-T15)
    // =========================================================================

    @Test
    fun `UCS-T14 Production strict routing findRoute remains strictly FAIL-CLOSED`() {
        val result = router.findRoute(
            roadGraph = diagnosticGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG"
        )
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, result.status)
        assertTrue(UniformCostSearchRouter.STRICT_ROUTING_DISABLED)
        assertNotNull(result.routingDisabledReason)
    }

    @Test
    fun `UCS-T15 Production F06 flag remains OPEN and graph readiness remains not ready`() {
        assertTrue("STRICT_ROUTING_DISABLED must be permanently true until qualification complete",
            UniformCostSearchRouter.STRICT_ROUTING_DISABLED)
        // Production public findRoute is always STRICT_ROUTING_DISABLED
        val prodResult = router.findRoute(
            roadGraph = diagnosticGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG"
        )
        assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED, prodResult.status)

        // With an unverified graph, when allowUnverifiedForDiagnostic = false, unverified edges are strictly excluded
        val unverifiedEdges = diagnosticGraph.directedEdges.map { it.copy(compatibilityStatus = EdgeCompatibilityStatus.UNVERIFIED) }
        val unverifiedGraph = RoadGraph(
            nodes = diagnosticGraph.nodes,
            directedEdges = unverifiedEdges,
            graphVersion = diagnosticGraph.graphVersion
        )
        val strictUnverifiedResult = router.computeDiagnosticRoute(
            roadGraph = unverifiedGraph,
            startNodeId = "N_BASE",
            goalNodeId = "N_BACH_DANG",
            allowUnverifiedForDiagnostic = false
        )
        assertEquals(UcsRouteStatus.NO_PATH, strictUnverifiedResult.status)
    }

    // =========================================================================
    // VIEWMODEL INTEGRATION TESTS (UCS-T16 .. UCS-T17)
    // =========================================================================

    private class MockSosRepository : SosRepository {
        private val sosFlow = MutableSharedFlow<List<SosRescueInfo>>(replay = 1)
        override fun observeSos(): Flow<List<SosRescueInfo>> = sosFlow.asSharedFlow()
        override suspend fun getActiveSos(): List<SosRescueInfo> = emptyList()
        override suspend fun getMySos(): List<SosRescueInfo> = emptyList()
        override suspend fun getSosById(eventId: String): SosRescueInfo? = null
        override suspend fun submitSos(sos: SosRescueInfo): SosSubmissionResult =
            SosSubmissionResult.Success(eventId = sos.eventId, transportMode = SosTransportMode.ONLINE, serverSos = sos)
    }

    private class MockRescueMissionRepository : RescueMissionRepository {
        override val missions: StateFlow<List<RescueMissionResponse>> = MutableStateFlow(emptyList())
        override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)
        override val error: StateFlow<String?> = MutableStateFlow(null)
        override suspend fun fetchMyMissions() = Unit
        override suspend fun getMissionDetail(id: String): RescueMissionApiResult<RescueMissionResponse> =
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.NOT_FOUND, "not found"))
        override suspend fun updateMissionStatus(id: String, newStatus: MissionStatus): RescueMissionApiResult<RescueMissionResponse> =
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.NOT_FOUND, "not found"))
        override suspend fun acceptRescueRequest(requestId: String): RescueMissionApiResult<RescueMissionResponse> =
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.NOT_FOUND, "not found"))
        override suspend fun getNearbyRequests(lat: Double, lon: Double, radius: Int): RescueMissionApiResult<List<RescueMissionRequestInfo>> =
            RescueMissionApiResult.Success(emptyList())
        override suspend fun getRescueRequestDetail(id: String): RescueMissionApiResult<RescueMissionRequestInfo> =
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.NOT_FOUND, "not found"))
    }

    @Test
    fun `UCS-T16 ViewModel computeRouteToMarker successfully transitions state Loading to Success with polylinePoints`() = runTest {
        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = diagnosticGraph
        )

        assertNull(viewModel.routeState.value)

        val sosMarker = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "sos-evt-danang-001",
            latitude = 16.0544,
            longitude = 108.2022,
            title = "SOS Bach Dang",
            snippet = "Người cần cứu: 1 - SOS Bach Dang",
            urgency = "KHẨN CẤP"
        )

        viewModel.computeRouteToMarker(sosMarker, LatLng(16.0500, 108.2000))
        advanceUntilIdle()

        val state = viewModel.routeState.value
        assertNotNull("routeState must not be null after computeRouteToMarker", state)
        assertFalse("isLoading must be false after completion", state!!.isLoading)
        assertNull("errorMessage must be null on success", state.errorMessage)
        assertEquals(UcsRouteStatus.SUCCESS, state.routeResult?.status)
        assertTrue("polylinePoints must contain points for Google Maps Polyline", state.polylinePoints.isNotEmpty())
        assertTrue("isDiagnosticSimulation flag must be true", state.isDiagnosticSimulation)
        assertNotNull(state.totalCost)
        assertNotNull(state.totalDistanceMeters)
    }

    @Test
    fun `UCS-T16-A Dynamic start point changes when rescuer position changes`() = runTest {
        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = diagnosticGraph
        )

        val sosMarker = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "sos-evt-danang-001",
            latitude = 16.0544,
            longitude = 108.2022,
            title = "SOS Bach Dang",
            snippet = "Người cần cứu: 1",
            urgency = "KHẨN CẤP"
        )

        // Rescuer A at Base (16.0500, 108.2000)
        viewModel.computeRouteToMarker(sosMarker, LatLng(16.0500, 108.2000))
        advanceUntilIdle()
        val routeA = viewModel.routeState.value
        assertNotNull(routeA)
        assertEquals("N_BASE", routeA?.startNodeId)
        val distA = routeA?.totalDistanceMeters ?: 0.0

        // Rescuer B at Hoang Dieu (16.0490, 108.2005)
        viewModel.computeRouteToMarker(sosMarker, LatLng(16.0490, 108.2005))
        advanceUntilIdle()
        val routeB = viewModel.routeState.value
        assertNotNull(routeB)
        assertEquals("N_HOANG_DIEU", routeB?.startNodeId)
        val distB = routeB?.totalDistanceMeters ?: 0.0

        assertNotEquals("Different rescuer locations must yield different start nodes", routeA?.startNodeId, routeB?.startNodeId)
        assertNotEquals("Different rescuer locations must yield different total distances", distA, distB, 0.1)
    }

    @Test
    fun `UCS-T16-B Dynamic destination changes when different SOS marker is selected`() = runTest {
        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = diagnosticGraph
        )

        val rescuerLocation = LatLng(16.0500, 108.2000)

        // SOS Marker 1: Bach Dang (16.0544, 108.2022)
        val sos1 = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "sos-01",
            latitude = 16.0544,
            longitude = 108.2022,
            title = "SOS Bach Dang",
            snippet = "1 người",
            urgency = "KHẨN CẤP"
        )
        viewModel.computeRouteToMarker(sos1, rescuerLocation)
        advanceUntilIdle()
        val route1 = viewModel.routeState.value
        assertNotNull(route1)
        assertEquals("N_BACH_DANG", route1?.goalNodeId)
        val dist1 = route1?.totalDistanceMeters ?: 0.0

        // SOS Marker 2: Cau Rong (16.0490, 108.2020)
        val sos2 = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "sos-02",
            latitude = 16.0490,
            longitude = 108.2020,
            title = "SOS Cau Rong",
            snippet = "2 người",
            urgency = "KHẨN CẤP"
        )
        viewModel.computeRouteToMarker(sos2, rescuerLocation)
        advanceUntilIdle()
        val route2 = viewModel.routeState.value
        assertNotNull(route2)
        assertEquals("N_CAU_RONG", route2?.goalNodeId)
        val dist2 = route2?.totalDistanceMeters ?: 0.0

        assertNotEquals("Different SOS markers must yield different goal nodes", route1?.goalNodeId, route2?.goalNodeId)
        assertNotEquals("Different SOS markers must yield different distances", dist1, dist2, 0.1)
    }

    @Test
    fun `UCS-T16-C No silent fallback to N_BASE when rescuer location is provided`() = runTest {
        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = diagnosticGraph
        )

        val sos = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "sos-01",
            latitude = 16.0544,
            longitude = 108.2022,
            title = "SOS Bach Dang",
            snippet = "1 người",
            urgency = "KHẨN CẤP"
        )

        // Rescuer at Le Duan (16.0520, 108.2020)
        val rescuerLeDuan = LatLng(16.0520, 108.2020)
        viewModel.computeRouteToMarker(sos, rescuerLeDuan)
        advanceUntilIdle()

        val route = viewModel.routeState.value
        assertNotNull(route)
        assertEquals(rescuerLeDuan, route?.startPoint)
        assertEquals("N_LE_DUAN", route?.startNodeId)
        assertNotEquals("Must not fallback to N_BASE when actual location is provided", "N_BASE", route?.startNodeId)
    }

    @Test
    fun `UCS-T16-D Rejection when rescuer location is absent - no silent fake route`() = runTest {
        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = diagnosticGraph
        )

        val sos = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "sos-01",
            latitude = 16.0544,
            longitude = 108.2022,
            title = "SOS Bach Dang",
            snippet = "1 người",
            urgency = "KHẨN CẤP"
        )

        // Null rescuer location
        viewModel.computeRouteToMarker(sos, null)
        advanceUntilIdle()

        val route = viewModel.routeState.value
        assertNotNull(route)
        assertNotNull("Must return clear error message when rescuer location is missing", route?.errorMessage)
        assertTrue(route!!.errorMessage!!.contains("vị trí của cứu hộ viên"))
        assertTrue("Polyline must remain empty when rescuer location is missing", route.polylinePoints.isEmpty())
        assertNull("routeResult must be null when missing rescuer location", route.routeResult)
    }

    @Test
    fun `UCS-T16-E Graph coverage check reports GRAPH_COVERAGE_MISSING when points outside bounds`() = runTest {
        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = diagnosticGraph
        )

        val farawaySos = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "sos-far",
            latitude = 21.0285, // Hanoi
            longitude = 105.8542,
            title = "SOS Hanoi",
            snippet = "Out of Da Nang",
            urgency = "KHẨN CẤP"
        )

        viewModel.computeRouteToMarker(farawaySos, LatLng(16.0500, 108.2000))
        advanceUntilIdle()

        val route = viewModel.routeState.value
        assertNotNull(route)
        assertNotNull(route?.errorMessage)
        assertTrue(
            "Error must specify GRAPH_COVERAGE_MISSING",
            route!!.errorMessage!!.contains("GRAPH_COVERAGE_MISSING")
        )
    }

    @Test
    fun `UCS-T16-F Real OSM Da Nang graph computes accurate multi-kilometer route and is not locked to 284m`() {
        val realGraph = DiagnosticGraphProvider.getRealOrDiagnosticGraph()
        assertNotNull(realGraph)
        assertTrue("Real graph must have nodes", realGraph.nodes.isNotEmpty())

        val p1 = GeoPoint(16.0500, 108.2000)
        val p2 = GeoPoint(16.0544, 108.2022)

        val n1 = router.findNearestNode(realGraph, p1, 1000.0)
        val n2 = router.findNearestNode(realGraph, p2, 1000.0)

        assertNotNull(n1)
        assertNotNull(n2)

        val result = router.computeDiagnosticRoute(realGraph, n1!!.id, n2!!.id, allowUnverifiedForDiagnostic = true)
        assertEquals(UcsRouteStatus.SUCCESS, result.status)
        assertTrue("Route distance must be positive", result.totalDistanceMeters > 0.0)
        assertTrue("Route must contain polyline vertices", result.pathGeometry.isNotEmpty())
    }

    @Test
    fun `UCS-T17 ViewModel clearRoute clears routeState and removes polylinePoints`() = runTest {
        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = diagnosticGraph
        )

        val sosMarker = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "sos-evt-danang-001",
            latitude = 16.0544,
            longitude = 108.2022,
            title = "SOS Bach Dang",
            snippet = "Người cần cứu: 1 - SOS Bach Dang",
            urgency = "KHẨN CẤP"
        )

        viewModel.computeRouteToMarker(sosMarker, LatLng(16.0500, 108.2000))
        advanceUntilIdle()
        assertNotNull(viewModel.routeState.value)

        viewModel.clearRoute()
        assertNull("routeState must be null after clearRoute", viewModel.routeState.value)
    }

    @Test
    fun `UCS-T18 Rescuer at physical location (15_9759, 108_2122) routes successfully to SOS without GRAPH_COVERAGE_MISSING`() = runTest {
        val realGraph = DiagnosticGraphProvider.getRealOrDiagnosticGraph()
        assertNotNull(realGraph)

        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = realGraph
        )

        // Tọa độ người cứu hộ (chấm tròn xanh GPS của thiết bị trên đường Đô Đốc Lân)
        val rescuerGpsLocation = LatLng(15.9759, 108.2122)

        // Tọa độ tín hiệu SOS người dân (hình giọt nước)
        val sosMarker = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = "2be79bdc-55ce-4996-8bad-459cf1881ca1",
            latitude = 15.9759197,
            longitude = 108.2122431,
            title = "SOS Liem Lac",
            snippet = "Người: 1 - An toàn, chỉ cần hỗ trợ",
            urgency = "THẤP"
        )

        // Gọi tìm đường từ vị trí chấm tròn xanh thực tế của cứu hộ viên đến tín hiệu SOS
        viewModel.computeRouteToMarker(
            marker = sosMarker,
            rescuerLocation = rescuerGpsLocation,
            isSimulatedRescuerLocation = false
        )
        advanceUntilIdle()

        val route = viewModel.routeState.value
        assertNotNull("RouteState must not be null", route)
        assertNull("Route must not fail with coverage or snap error, got: ${route?.errorMessage}", route?.errorMessage)
        assertTrue("Polyline points must be generated", route!!.polylinePoints.isNotEmpty())
        assertEquals("Start of polyline must match rescuer location", rescuerGpsLocation, route.polylinePoints.first())
        assertEquals("End of polyline must match SOS marker location", LatLng(sosMarker.latitude, sosMarker.longitude), route.polylinePoints.last())
        assertFalse("Must not be simulated rescuer location", route.isSimulatedRescuerLocation)
    }

    @Test
    fun `UCS-T19 Rescue Mission Detail Screen triggers UCS route calculation via DisasterMapSharedState`() = runTest {
        val realGraph = DiagnosticGraphProvider.getRealOrDiagnosticGraph()
        assertNotNull(realGraph)

        val viewModel = DisasterMapViewModel(
            sosRepository = MockSosRepository(),
            rescueMissionRepository = MockRescueMissionRepository(),
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            router = router,
            roadGraph = realGraph
        )

        // Tọa độ người cứu hộ GPS (Đô Đốc Lân)
        val rescuerGpsLocation = LatLng(15.9759, 108.2122)

        // Thiết lập targetDestination từ MissionDetailScreen (#B640B602: 16.0685, 108.2215)
        com.example.bai1.presentation.map.DisasterMapSharedState.targetDestination =
            com.example.bai1.presentation.map.DisasterMapSharedState.TargetDestination(
                latitude = 16.0685,
                longitude = 108.2215,
                title = "Nước dâng cao Bạch Đằng, cần xuồng hỗ trợ",
                urgency = "CAO",
                requestId = "mission_b640b602"
            )

        val dest = com.example.bai1.presentation.map.DisasterMapSharedState.targetDestination
        assertNotNull("TargetDestination must be set", dest)

        val missionMarker = MapRescueMarker(
            type = MarkerType.SOS,
            eventId = dest!!.eventId,
            requestId = dest.requestId,
            latitude = dest.latitude,
            longitude = dest.longitude,
            title = dest.title,
            snippet = dest.title,
            urgency = dest.urgency
        )

        viewModel.computeRouteToMarker(
            marker = missionMarker,
            rescuerLocation = rescuerGpsLocation,
            isSimulatedRescuerLocation = false
        )
        advanceUntilIdle()

        val route = viewModel.routeState.value
        assertNotNull("RouteState must not be null", route)
        assertNull("Route must not have error, got: ${route?.errorMessage}", route?.errorMessage)
        assertEquals(UcsRouteStatus.SUCCESS, route!!.routeResult?.status)
        assertTrue("Polyline points must not be empty", route.polylinePoints.isNotEmpty())
        assertEquals(rescuerGpsLocation, route.polylinePoints.first())
        assertEquals(LatLng(dest.latitude, dest.longitude), route.polylinePoints.last())
        assertFalse(route.isSimulatedRescuerLocation)
        assertTrue("Route distance must be > 8000m across city", (route.totalDistanceMeters ?: 0.0) > 8000.0)
    }

    // =========================================================================
    // CITIZEN REAL-TIME RESCUE TRACKING TESTS (CITIZEN-T01 .. CITIZEN-T03)
    // =========================================================================

    @Test
    fun `CITIZEN-T01 computeRouteBetweenPoints calculates valid UCS route from rescuer to victim`() = runTest {
        val fakeSosRepo = MockSosRepository()
        val fakeMissionRepo = MockRescueMissionRepository()
        val viewModel = DisasterMapViewModel(
            sosRepository = fakeSosRepo,
            rescueMissionRepository = fakeMissionRepo,
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            roadGraph = diagnosticGraph
        )

        val rescuerStart = LatLng(16.0500, 108.2000) // N_BASE
        val victimLocation = LatLng(16.0544, 108.2022) // N_BACH_DANG

        viewModel.computeRouteBetweenPoints(
            startPoint = rescuerStart,
            destinationPoint = victimLocation,
            isSimulatedRescuerLocation = false
        )
        advanceUntilIdle()

        val route = viewModel.routeState.value
        assertNotNull("RouteState must not be null", route)
        assertNull("Route must not have error: ${route?.errorMessage}", route?.errorMessage)
        assertEquals(UcsRouteStatus.SUCCESS, route!!.routeResult?.status)
        assertTrue("Polyline points must not be empty", route.polylinePoints.isNotEmpty())
        assertEquals("Start of polyline must be rescuer location", rescuerStart, route.polylinePoints.first())
        assertEquals("End of polyline must be victim location", victimLocation, route.polylinePoints.last())
        assertTrue("Route distance must be positive", (route.totalDistanceMeters ?: 0.0) > 0.0)
    }

    @Test
    fun `CITIZEN-T02 DisasterMapSharedState manages citizen tracking state cleanly`() {
        val eventId = "sos-uuid-12345"
        val victimLoc = LatLng(16.0544, 108.2022)
        val rescuerLoc = LatLng(16.0685, 108.2150)

        com.example.bai1.presentation.map.DisasterMapSharedState.citizenTrackingEventId = eventId
        com.example.bai1.presentation.map.DisasterMapSharedState.lastKnownVictimLocation = victimLoc
        com.example.bai1.presentation.map.DisasterMapSharedState.lastKnownRescuerLocation = rescuerLoc
        com.example.bai1.presentation.map.DisasterMapSharedState.isCitizenTrackingMode = true

        assertEquals(eventId, com.example.bai1.presentation.map.DisasterMapSharedState.citizenTrackingEventId)
        assertEquals(victimLoc, com.example.bai1.presentation.map.DisasterMapSharedState.lastKnownVictimLocation)
        assertEquals(rescuerLoc, com.example.bai1.presentation.map.DisasterMapSharedState.lastKnownRescuerLocation)
        assertTrue(com.example.bai1.presentation.map.DisasterMapSharedState.isCitizenTrackingMode)

        // Reset
        com.example.bai1.presentation.map.DisasterMapSharedState.isCitizenTrackingMode = false
        assertFalse(com.example.bai1.presentation.map.DisasterMapSharedState.isCitizenTrackingMode)
    }

    @Test
    fun `CITIZEN-T03 Victim position update triggers route re-anchoring to new location`() = runTest {
        val fakeSosRepo = MockSosRepository()
        val fakeMissionRepo = MockRescueMissionRepository()
        val viewModel = DisasterMapViewModel(
            sosRepository = fakeSosRepo,
            rescueMissionRepository = fakeMissionRepo,
            pollingEnabled = false,
            nearbyRequestsEnabled = false,
            roadGraph = diagnosticGraph
        )

        val rescuerLoc = LatLng(16.0500, 108.2000) // N_BASE
        val initialVictimLoc = LatLng(16.0544, 108.2022) // N_BACH_DANG
        val updatedVictimLoc = LatLng(16.0510, 108.2010) // N_DUY_TAN

        viewModel.computeRouteBetweenPoints(
            startPoint = rescuerLoc,
            destinationPoint = initialVictimLoc,
            isSimulatedRescuerLocation = false
        )
        advanceUntilIdle()

        val initialRoute = viewModel.routeState.value
        assertNotNull(initialRoute)
        assertEquals(initialVictimLoc, initialRoute!!.polylinePoints.last())

        // Citizen moves to a new location in real time
        viewModel.computeRouteBetweenPoints(
            startPoint = rescuerLoc,
            destinationPoint = updatedVictimLoc,
            isSimulatedRescuerLocation = false
        )
        advanceUntilIdle()

        val updatedRoute = viewModel.routeState.value
        assertNotNull(updatedRoute)
        assertEquals("Route must now end at updated victim location", updatedVictimLoc, updatedRoute!!.polylinePoints.last())
    }
}
