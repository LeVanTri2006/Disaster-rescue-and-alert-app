package com.example.bai1.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.bai1.ui.theme.DisasterAlertTheme
import com.example.bai1.auth.network.*
import com.example.bai1.rescue.routing.model.*
import com.example.bai1.sos.domain.*
import com.example.bai1.sos.network.*
import com.example.bai1.sos.transport.ApiSosTransport
import kotlinx.coroutines.launch
import okhttp3.*
import java.net.Proxy
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Standalone diagnostic activity. No production NavHost, permissions, SDK keys or GPS. */
class DemoActivity : ComponentActivity() {
    var loggedIn by mutableStateOf(false); private set
    var offlinePreview by mutableStateOf(false); private set
    var page by mutableStateOf("map"); private set
    var scenario by mutableStateOf("normal"); private set
    var start by mutableStateOf("A"); private set
    var goal by mutableStateOf("B"); private set
    var graph by mutableStateOf(DemoGraph.parse()); private set
    var result by mutableStateOf<UcsRouteResult?>(null); private set
    var transition by mutableStateOf("N/A"); private set
    var validation by mutableStateOf("PARSER_PASS 8 nodes / 8 edges"); private set
    var message by mutableStateOf("Chưa gửi tới fixture"); private set
    var adults by mutableStateOf(1); private set
    var description by mutableStateOf("SYNTHETIC_TEST_DATA cần hỗ trợ tại điểm B"); private set
    var busy by mutableStateOf(false); private set
    var fault by mutableStateOf("ok"); private set
    var marker by mutableStateOf<GeoPoint?>(null); private set
    var invocations = 0; private set
    private var epoch=0
    private var token:String?=null
    private lateinit var pinned:OkHttpClient
    private lateinit var auth:OkHttpAuthApiClient
    private lateinit var transport:ApiSosTransport
    var base:String="";private set
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // Persist only this task's explicit local port to make the launcher icon useful after testing.
        val prefs=getSharedPreferences("p5e09_demo_only",MODE_PRIVATE)
        val supplied=intent.getIntExtra("fixturePort",0)
        val port=if(supplied in 1024..65535) supplied else prefs.getInt("fixturePort",0)
        if(supplied in 1024..65535) prefs.edit().putInt("fixturePort",supplied).apply()
        if(port in 1024..65535) {
            base="http://127.0.0.1:$port"
            val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(1,TimeUnit.SECONDS).readTimeout(1,TimeUnit.SECONDS).callTimeout(2,TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val u=chain.request().url
                    check(u.scheme=="http" && u.host=="127.0.0.1" && u.port==port && u.encodedPath in setOf("/demo/info","/auth/login","/sos"))
                    if(fault=="offline") throw IOException("Owned fixture offline")
                    check((chain.request().body?.contentLength() ?: 0)<=4096)
                    chain.proceed(chain.request().newBuilder().header("X-Demo-Fault",fault).build())
                }.addNetworkInterceptor { chain ->
                    val u=chain.request().url
                    check(u.scheme=="http" && u.host=="127.0.0.1" && u.port==port)
                    val response=chain.proceed(chain.request())
                    if((response.body?.contentLength() ?: -1) !in 0..65536) {response.close();throw IOException("Bounded fixture response required")}
                    response
                }.build()
            pinned=client
            auth=OkHttpAuthApiClient(base,client)
            transport=ApiSosTransport(OkHttpSosApiClient(base,object:AccessTokenProvider { override fun getAccessToken()=token },client))
        } else message="Chưa cấu hình cổng fixture; xem runbook USB"
        setContent { DisasterAlertTheme { Surface { DemoUi() } } }
    }
    fun login() {
        if(base.isBlank() || busy)return
        val submitted=epoch;busy=true
        lifecycleScope.launch {
            val handshake=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { pinned.newCall(Request.Builder().url("$base/demo/info").build()).execute().use { it.isSuccessful && it.body!!.string()=="P5-E09-SYNTHETIC-NO-DISPATCH" } }.getOrDefault(false)
            }
            val r=if(handshake) auth.login("fixture@example.invalid","fixture-only-password") else AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.NO_INTERNET,"Owned fixture handshake unavailable"))
            if(epoch==submitted) { when(r) { is AuthApiResult.Success -> {token=r.value.accessToken;loggedIn=true;message="Phiên synthetic; không Firebase"};is AuthApiResult.Failure -> message=r.error.userMessage() };busy=false }
        }
    }
    fun logout() { epoch++;token=null;loggedIn=false;offlinePreview=false;busy=false;marker=null;result=null;message="Đã xóa phiên demo" }
    fun analyze() {
        invocations++
        result=DemoGraph.analyze(graph,start,goal)
        check(result!!.pathEdges.isEmpty() && result!!.pathGeometry.isEmpty() && result!!.status!=UcsRouteStatus.SUCCESS)
        android.util.Log.i("P5E09_UCS", "graph=${graph.graphVersion} from=$start to=$goal status=${result!!.status} path=${result!!.pathNodeIds} visited=${result!!.visitedNodesCount} cost=N/A authority=false")
        transition=DemoGraph.transition(graph)
    }
    fun changeScenario(value:String) {
        scenario=value;result=null;transition="N/A"
        try { graph=DemoGraph.parse(value);validation="PARSER_PASS 8 nodes / 8 edges" }
        catch(e:Exception) {validation="PARSER_REJECTED ${e.javaClass.simpleName}";result=null}
    }
    fun submit() {
        if(!loggedIn || busy || base.isBlank())return
        val submitted=epoch;busy=true;message="Đang gửi tới fixture..."
        val target=graph.nodes[goal]?.position ?: graph.nodes.getValue("B").position
        val request=SosRescueInfo(adults=adults,condition="SYNTHETIC_TEST_DATA",description=description,
            latitude=target.latitude,longitude=target.longitude,createdAt=1794268800000L,batteryPercent=-1)
        lifecycleScope.launch {
            val response=transport.send(request)
            if(epoch==submitted) { when(response) {
                is SosSubmissionResult.Success -> {marker=GeoPoint(response.serverSos!!.latitude!!,response.serverSos.longitude!!);message="FIXTURE_RECEIVED ${response.eventId} status=${response.serverSos.status}"}
                is SosSubmissionResult.Failed -> message="FIXTURE_ERROR ${response.reason} — ${response.message}"
                is SosSubmissionResult.Queued -> message="FIXTURE_QUEUED"
            };busy=false }
        }
    }
    @Composable private fun DemoUi() {
        BackHandler(enabled=(loggedIn || offlinePreview) && page=="sos") {page="map"}
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
            Text("SYNTHETIC_TEST_DATA — P5-E09 DEMO",style=MaterialTheme.typography.titleMedium)
            Text("MÔ PHỎNG / CHẨN ĐOÁN — KHÔNG PHẢI TUYẾN CỨU HỘ ĐÃ XÁC MINH",color=Color(0xffa80000),style=MaterialTheme.typography.labelMedium)
            Text("NAVIGATION ALLOWED = FALSE • F06 OPEN",style=MaterialTheme.typography.labelMedium)
            if(!loggedIn && !offlinePreview) {
                Text("Tài khoản synthetic / fixture local. Không Google/Firebase.")
                Button(onClick=::login,enabled=!busy && base.isNotBlank()) { Text("Đăng nhập demo") }
                TextButton(onClick={offlinePreview=true}) {Text("Chẩn đoán offline")}
                Text(message)
            } else {
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick={page="map"}) { Text("Map / UCS") }
                    TextButton(onClick={page="sos"}) { Text("Cứu hộ fixture") }
                    TextButton(onClick=::logout) { Text("Đăng xuất") }
                }
                if(page=="map") {
                    GraphCanvas()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Graph: ${graph.graphVersion}")
                        Text("Overlay TEST-ONLY: xám UNKNOWN; đỏ BLOCKED. Không có tuyến đã tính.")
                        Row {
                            Button(onClick={start=if(start=="A")"C" else "A";result=null}) { Text("Điểm đi: $start") }
                            Spacer(Modifier.width(6.dp))
                            Button(onClick={goal=if(goal=="B")"H" else "B";result=null}) { Text("Điểm đến: $goal") }
                        }
                        Row {
                            TextButton(onClick={changeScenario("normal")}){Text("Gốc")}
                            TextButton(onClick={changeScenario("blocked")}){Text("Chặn AC")}
                        }
                        Row {
                            TextButton(onClick={changeScenario("turn")}){Text("Cấm rẽ")}
                            TextButton(onClick={changeScenario("invalid")}){Text("Graph lỗi")}
                        }
                        Text(validation)
                        Button(onClick=::analyze,enabled=scenario!="invalid") { Text("Phân tích đường đi") }
                        Text("Hàm thật: UniformCostSearchRouter.findRoute • lần gọi $invocations")
                        Text("KẾT QUẢ: ${result?.status?.name ?: "CHƯA CHẠY"}")
                        Text("Transition (chỉ luật rẽ) AC → CB: $transition")
                        Text("Input $start → $goal • vehicle=${graph.vehicleProfile}")
                        Text("Path: ∅ • Cost: N/A • UCS kernel chưa được chạy")
                        Text("${result?.routingDisabledReason ?: "Thiếu qualification authority; không cho phép dẫn đường."}")
                    }
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("FORM TEST-ONLY — ApiSosTransport production → HTTP fixture")
                    Text("Tọa độ TEST lấy từ điểm $goal của graph; không GPS.")
                    Row { Text("Người lớn: $adults");TextButton(onClick={adults=(adults+1).coerceAtMost(10)}){Text("Thêm người")} }
                    OutlinedTextField(value=description,onValueChange={description=it.take(300)},label={Text("Mô tả synthetic")},modifier=Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick={fault="ok"}){Text("HTTP OK")}
                        TextButton(onClick={fault="403"}){Text("HTTP 403")}
                        TextButton(onClick={fault="timeout"}){Text("Timeout")}
                        TextButton(onClick={fault="offline"}){Text("Offline")}
                    }
                    Text("Chế độ: $fault")
                    Button(onClick=::submit,enabled=loggedIn && !busy) { Text("Gửi tới fixture") }
                    Text(message)
                    Text("Chỉ tạo yêu cầu synthetic; chưa có dispatch / nhiệm vụ / route tự động.")
                }
            }
        }
    }
    @Composable private fun GraphCanvas() {
        val g=graph;val m=marker;val a=start;val b=goal
        Canvas(Modifier.fillMaxWidth().height(230.dp).background(Color(0xffe9eff2))) {
            fun xy(p:GeoPoint)=Offset(24f+((p.longitude-108.2000)/.0035*(size.width-48)).toFloat(),size.height-24f-((p.latitude-16.0490)/.0035*(size.height-48)).toFloat())
            for(edge in g.directedEdges)drawLine(if(edge.condition==RoadCondition.BLOCKED)Color.Red else Color.Gray,xy(g.nodes.getValue(edge.fromNodeId).position),xy(g.nodes.getValue(edge.toNodeId).position),5f)
            val paint=android.graphics.Paint().apply {color=android.graphics.Color.BLACK;textSize=28f;isAntiAlias=true}
            for(node in g.nodes.values) { val p=xy(node.position);drawCircle(if(node.id==a)Color.Blue else if(node.id==b)Color(0xfff3b000) else Color.DarkGray,10f,p);drawContext.canvas.nativeCanvas.drawText(node.id,p.x+11,p.y-10,paint) }
            m?.let {drawCircle(Color.Magenta,17f,xy(it))}
        }
        Text("8 nút / 8 cạnh • A/B được chọn; H tách rời • tím: SOS fixture")
    }
}
