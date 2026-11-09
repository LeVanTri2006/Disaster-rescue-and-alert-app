package com.example.bai1.rescue.routing.diagnostic

import com.example.bai1.rescue.routing.algorithm.DfaBuildResult
import com.example.bai1.rescue.routing.algorithm.TurnRestrictionIndex
import com.example.bai1.rescue.routing.algorithm.UniformCostSearchRouter
import com.example.bai1.rescue.routing.algorithm.ViaWayTurnRestrictionDfa
import com.example.bai1.rescue.routing.model.EdgeCompatibilityStatus
import com.example.bai1.rescue.routing.model.EdgeMetadata
import com.example.bai1.rescue.routing.model.GeoPoint
import com.example.bai1.rescue.routing.model.RestrictionSupportStatus
import com.example.bai1.rescue.routing.model.RoadCondition
import com.example.bai1.rescue.routing.model.RoadEdge
import com.example.bai1.rescue.routing.model.RoadGraph
import com.example.bai1.rescue.routing.model.RoadNode
import com.example.bai1.rescue.routing.model.RoutingBoundingBox
import com.example.bai1.rescue.routing.model.TurnRestriction
import com.example.bai1.rescue.routing.model.TurnRestrictionType
import com.example.bai1.rescue.routing.model.WayFlowPolicy

/**
 * Nhà cung cấp đồ thị đường bộ chẩn đoán phục vụ mô phỏng thuật toán Uniform Cost Search (UCS)
 * trên Google Maps trong môi trường thử nghiệm và nghiệm thu.
 *
 * Tọa độ các nút và cạnh bao phủ khu vực trung tâm Đà Nẵng (Hải Châu / Thanh Khê).
 */
object DiagnosticGraphProvider {

    private val router = UniformCostSearchRouter()

    /**
     * Danh sách các nút giao thông trung tâm Đà Nẵng.
     */
    val daNangNodes = listOf(
        RoadNode("N_BASE", GeoPoint(16.0500, 108.2000)),         // Trung tâm Cứu hộ (Xuất phát mặc định)
        RoadNode("N_DUY_TAN", GeoPoint(16.0510, 108.2010)),      // Ngã tư Duy Tân
        RoadNode("N_NGUYEN_VAN_LINH", GeoPoint(16.0500, 108.2020)), // Nguyễn Văn Linh
        RoadNode("N_LE_DUAN", GeoPoint(16.0520, 108.2020)),      // Lê Duẩn
        RoadNode("N_BACH_DANG", GeoPoint(16.0544, 108.2022)),    // Bạch Đằng (Khớp tọa độ SOS thực tế)
        RoadNode("N_TRAN_PHU", GeoPoint(16.0510, 108.2030)),     // Trần Phú
        RoadNode("N_HOANG_DIEU", GeoPoint(16.0490, 108.2005)),   // Hoàng Diệu
        RoadNode("N_CAU_RONG", GeoPoint(16.0490, 108.2020))      // Đầu cầu Rồng
    )

    private fun createEdge(
        id: String,
        fromId: String,
        toId: String,
        name: String,
        condition: RoadCondition = RoadCondition.CLEAR,
        intermediatePoints: List<GeoPoint> = emptyList()
    ): RoadEdge {
        val fromNode = daNangNodes.first { it.id == fromId }
        val toNode = daNangNodes.first { it.id == toId }
        val geom = mutableListOf<GeoPoint>()
        geom.add(fromNode.position)
        geom.addAll(intermediatePoints)
        geom.add(toNode.position)

        var totalDist = 0.0
        for (i in 0 until geom.size - 1) {
            totalDist += router.distanceMeters(geom[i], geom[i + 1])
        }

        return RoadEdge(
            id = id,
            fromNodeId = fromId,
            toNodeId = toId,
            lengthMeters = totalDist,
            geometry = geom,
            condition = condition,
            compatibilityStatus = EdgeCompatibilityStatus.VERIFIED_COMPATIBLE,
            isEligibleForStrictRouting = true,
            wayFlowPolicy = WayFlowPolicy.BIDIRECTIONAL,
            metadata = EdgeMetadata(
                name = name,
                highwayType = "primary",
                surface = "asphalt"
            )
        )
    }

    /**
     * Khởi tạo đồ thị mạng lưới đường giao thông Đà Nẵng với geometry đầy đủ phục vụ UCS.
     */
    fun getDaNangGraph(scenario: String = "normal"): RoadGraph {
        val edges = mutableListOf<RoadEdge>()

        // 1. Tuyến BASE -> DUY TÂN (2 chiều)
        edges.add(createEdge("E_BASE_DUYTAN", "N_BASE", "N_DUY_TAN", "Đường Duy Tân",
            intermediatePoints = listOf(GeoPoint(16.0505, 108.2005))))
        edges.add(createEdge("E_DUYTAN_BASE", "N_DUY_TAN", "N_BASE", "Đường Duy Tân",
            intermediatePoints = listOf(GeoPoint(16.0505, 108.2005))))

        // 2. Tuyến DUY TÂN -> NGUYỄN VĂN LINH (2 chiều)
        edges.add(createEdge("E_DUYTAN_NVL", "N_DUY_TAN", "N_NGUYEN_VAN_LINH", "Đường Nguyễn Văn Linh",
            intermediatePoints = listOf(GeoPoint(16.0505, 108.2015))))
        edges.add(createEdge("E_NVL_DUYTAN", "N_NGUYEN_VAN_LINH", "N_DUY_TAN", "Đường Nguyễn Văn Linh",
            intermediatePoints = listOf(GeoPoint(16.0505, 108.2015))))

        // 3. Tuyến DUY TÂN -> LÊ DUẨN (2 chiều)
        edges.add(createEdge("E_DUYTAN_LEDUAN", "N_DUY_TAN", "N_LE_DUAN", "Đường Lê Duẩn",
            intermediatePoints = listOf(GeoPoint(16.0515, 108.2015))))
        edges.add(createEdge("E_LEDUAN_DUYTAN", "N_LE_DUAN", "N_DUY_TAN", "Đường Lê Duẩn",
            intermediatePoints = listOf(GeoPoint(16.0515, 108.2015))))

        // 4. Tuyến LÊ DUẨN -> BẠCH ĐẰNG (2 chiều)
        val condLd = if (scenario == "blocked_leduan") RoadCondition.BLOCKED else RoadCondition.CLEAR
        edges.add(createEdge("E_LEDUAN_BACH_DANG", "N_LE_DUAN", "N_BACH_DANG", "Đường Bạch Đằng",
            condition = condLd,
            intermediatePoints = listOf(GeoPoint(16.0532, 108.2021))))
        edges.add(createEdge("E_BACH_DANG_LEDUAN", "N_BACH_DANG", "N_LE_DUAN", "Đường Bạch Đằng",
            condition = condLd,
            intermediatePoints = listOf(GeoPoint(16.0532, 108.2021))))

        // 5. Tuyến NGUYỄN VĂN LINH -> TRẦN PHÚ (2 chiều)
        edges.add(createEdge("E_NVL_TRANPHU", "N_NGUYEN_VAN_LINH", "N_TRAN_PHU", "Đường Trần Phú",
            intermediatePoints = listOf(GeoPoint(16.0505, 108.2025))))
        edges.add(createEdge("E_TRANPHU_NVL", "N_TRAN_PHU", "N_NGUYEN_VAN_LINH", "Đường Trần Phú",
            intermediatePoints = listOf(GeoPoint(16.0505, 108.2025))))

        // 6. Tuyến TRẦN PHÚ -> BẠCH ĐẰNG (2 chiều - tuyến ven sông)
        edges.add(createEdge("E_TRANPHU_BACH_DANG", "N_TRAN_PHU", "N_BACH_DANG", "Đường Trần Phú nối Bạch Đằng",
            intermediatePoints = listOf(GeoPoint(16.0527, 108.2026), GeoPoint(16.0538, 108.2024))))
        edges.add(createEdge("E_BACH_DANG_TRANPHU", "N_BACH_DANG", "N_TRAN_PHU", "Đường Bạch Đằng nối Trần Phú",
            intermediatePoints = listOf(GeoPoint(16.0538, 108.2024), GeoPoint(16.0527, 108.2026))))

        // 7. Tuyến BASE -> HOÀNG DIỆU (2 chiều)
        edges.add(createEdge("E_BASE_HOANGDIEU", "N_BASE", "N_HOANG_DIEU", "Đường Hoàng Diệu",
            intermediatePoints = listOf(GeoPoint(16.0495, 108.2002))))
        edges.add(createEdge("E_HOANGDIEU_BASE", "N_HOANG_DIEU", "N_BASE", "Đường Hoàng Diệu",
            intermediatePoints = listOf(GeoPoint(16.0495, 108.2002))))

        // 8. Tuyến HOÀNG DIỆU -> CẦU RỒNG (2 chiều)
        edges.add(createEdge("E_HOANGDIEU_CAURONG", "N_HOANG_DIEU", "N_CAU_RONG", "Đường dẫn Cầu Rồng",
            intermediatePoints = listOf(GeoPoint(16.0490, 108.2012))))
        edges.add(createEdge("E_CAURONG_HOANGDIEU", "N_CAU_RONG", "N_HOANG_DIEU", "Đường dẫn Cầu Rồng",
            intermediatePoints = listOf(GeoPoint(16.0490, 108.2012))))

        // 9. Tuyến CẦU RỒNG -> TRẦN PHÚ (2 chiều)
        edges.add(createEdge("E_CAURONG_TRANPHU", "N_CAU_RONG", "N_TRAN_PHU", "Đường Trần Phú nối Cầu Rồng",
            intermediatePoints = listOf(GeoPoint(16.0500, 108.2025))))
        edges.add(createEdge("E_TRANPHU_CAURONG", "N_TRAN_PHU", "N_CAU_RONG", "Đường Trần Phú nối Cầu Rồng",
            intermediatePoints = listOf(GeoPoint(16.0500, 108.2025))))

        val restrictions = if (scenario == "turn_restriction") {
            listOf(
                TurnRestriction(
                    id = "RESTRICT_NO_LEFT_DUYTAN_LEDUAN",
                    type = TurnRestrictionType.NO_LEFT_TURN,
                    fromOsmWayIds = listOf(101L),
                    toOsmWayIds = listOf(102L),
                    viaOsmNodeIds = listOf(201L),
                    supportStatus = RestrictionSupportStatus.FULLY_SUPPORTED,
                    prohibitedTransitions = listOf(
                        com.example.bai1.rescue.routing.model.EdgeTransition(
                            fromEdgeId = "E_BASE_DUYTAN",
                            toEdgeId = "E_DUYTAN_LEDUAN",
                            viaNodeId = "N_DUY_TAN"
                        )
                    )
                )
            )
        } else {
            emptyList()
        }

        return RoadGraph(
            nodes = daNangNodes.associateBy { it.id },
            directedEdges = edges,
            graphVersion = "dn_central_diagnostic_v1",
            turnRestrictions = restrictions,
            boundingBoxType = "FIXTURE",
            boundingBox = RoutingBoundingBox(
                minLon = 108.195,
                minLat = 16.045,
                maxLon = 108.205,
                maxLat = 16.058,
                bufferedMeters = 500.0
            )
        )
    }

    @Volatile
    private var cachedFullGraph: RoadGraph? = null
    @Volatile
    private var cachedTurnIndex: TurnRestrictionIndex? = null
    @Volatile
    private var cachedDfa: ViaWayTurnRestrictionDfa? = null
    private val graphLoadLock = Any()

    fun getCachedGraph(): RoadGraph? = cachedFullGraph

    fun getPrecomputedRoutingAssets(): Pair<ViaWayTurnRestrictionDfa?, TurnRestrictionIndex?> {
        return Pair(cachedDfa, cachedTurnIndex)
    }

    fun clearCache() {
        synchronized(graphLoadLock) {
            cachedFullGraph = null
            cachedTurnIndex = null
            cachedDfa = null
        }
    }

    /**
     * Khởi chạy nạp đồ thị trước (pre-warm) dưới nền ngay khi ứng dụng khởi động.
     */
    fun preload(context: android.content.Context) {
        if (cachedFullGraph != null) return
        val appContext = context.applicationContext
        Thread({
            try {
                getRealOrDiagnosticGraph(appContext)
            } catch (e: Exception) {
                android.util.Log.w("DiagnosticGraphProvider", "Background preload failed: ${e.message}")
            }
        }, "OSMGraphPreloader").start()
    }

    /**
     * Nạp đồ thị OSM thật của Đà Nẵng từ Android Assets (BaseRoadGraph.json.gz) hoặc file test nếu có,
     * tự động fallback về getDaNangGraph() nếu môi trường không có file assets.
     */
    fun getRealOrDiagnosticGraph(context: android.content.Context? = null): RoadGraph {
        cachedFullGraph?.let { return it }

        synchronized(graphLoadLock) {
            cachedFullGraph?.let { return it }

            var loadedGraph: RoadGraph? = null

            if (context != null) {
                val assetNames = listOf("routing/BaseRoadGraph.json.gz", "routing/BaseRoadGraph.json")
                for (assetName in assetNames) {
                    try {
                        context.assets.open(assetName).use { input ->
                            val graph = if (assetName.endsWith(".gz")) {
                                java.util.zip.GZIPInputStream(input).use { gis ->
                                    com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser().parseInputStream(gis)
                                }
                            } else {
                                com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser().parseInputStream(input)
                            }
                            loadedGraph = graph
                            android.util.Log.i("DiagnosticGraphProvider", "Successfully loaded OSM graph from asset $assetName: nodes=${graph.nodes.size}, edges=${graph.directedEdges.size}")
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("DiagnosticGraphProvider", "Cannot load asset $assetName: ${e.message}")
                    }
                }
            }

            if (loadedGraph == null) {
                // Check local storage / test paths
                val candidatePaths = listOf(
                    java.io.File("../canh-bao-thien-tai-backend-app/tools/osm-importer/output-danang-261103/BaseRoadGraph.json.gz"),
                    java.io.File("/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app/tools/osm-importer/output-danang-261103/BaseRoadGraph.json.gz"),
                    java.io.File("src/main/assets/routing/BaseRoadGraph.json.gz"),
                    java.io.File("src/main/assets/routing/BaseRoadGraph.json")
                )
                for (f in candidatePaths) {
                    if (f.exists()) {
                        try {
                            val graph = if (f.name.endsWith(".gz")) {
                                java.io.FileInputStream(f).use { fis ->
                                    java.util.zip.GZIPInputStream(fis).use { gis ->
                                        com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser().parseInputStream(gis)
                                    }
                                }
                            } else {
                                java.io.FileInputStream(f).use { fis ->
                                    com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser().parseInputStream(fis)
                                }
                            }
                            loadedGraph = graph
                            break
                        } catch (_: Exception) {}
                    }
                }
            }

            val finalGraph = loadedGraph ?: getDaNangGraph()
            cachedFullGraph = finalGraph

            // Tiền tính toán TurnRestrictionIndex và DFA để thuật toán UCS chạy ngay trong <15ms
            try {
                val dfa = when (val res = ViaWayTurnRestrictionDfa.build(finalGraph.turnRestrictions, finalGraph)) {
                    is DfaBuildResult.Success -> res.dfa
                    else -> ViaWayTurnRestrictionDfa.empty()
                }
                cachedDfa = dfa
                cachedTurnIndex = TurnRestrictionIndex.build(finalGraph, dfa.acceptedRestrictionIds)
                android.util.Log.i("DiagnosticGraphProvider", "Precomputed TurnRestrictionIndex and DFA successfully")
            } catch (e: Exception) {
                android.util.Log.w("DiagnosticGraphProvider", "Precomputing turn restrictions failed: ${e.message}")
            }

            return finalGraph
        }
    }
}
