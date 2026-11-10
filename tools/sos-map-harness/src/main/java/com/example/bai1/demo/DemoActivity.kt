package com.example.bai1.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** TEST-ONLY UI adapter; exact production SOS transport + HTTP client, no default DI/GPS. */
class DemoActivity : ComponentActivity() {
 var citizenIndex by mutableStateOf(1); private set
 var role by mutableStateOf(""); private set
 val loggedIn get()=role.isNotEmpty()
 var page by mutableStateOf("home"); private set
 var adults by mutableStateOf(1); private set
 var description by mutableStateOf("SYNTHETIC_TEST_DATA cần hỗ trợ"); private set
 var goal by mutableStateOf("B"); private set
 var message by mutableStateOf("Chọn vai trò synthetic"); private set
 var fault by mutableStateOf("ok"); private set
 var busy by mutableStateOf(false); private set
 var markers by mutableStateOf<List<SosEventResponse>>(emptyList()); private set
 var selected by mutableStateOf<SosEventResponse?>(null); private set
 var lastCreated by mutableStateOf<String?>(null); private set
 var refreshes by mutableStateOf(0); private set
 val graph=DemoGraph.parse()
 private var epoch=0
 private var token:String?=null
 private lateinit var pinned:OkHttpClient
 private lateinit var auth:OkHttpAuthApiClient
 private lateinit var api:OkHttpSosApiClient
 private lateinit var transport:ApiSosTransport
 var base="";private set
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs=getSharedPreferences("p5e10a_demo_only",MODE_PRIVATE)
  val supplied=intent.getIntExtra("fixturePort",0)
  val port=if(supplied in 1024..65535)supplied else prefs.getInt("fixturePort",0)
  if(supplied in 1024..65535)prefs.edit().putInt("fixturePort",supplied).apply()
  if(port in 1024..65535) {
   base="http://127.0.0.1:$port"
   pinned=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
    .connectTimeout(1,TimeUnit.SECONDS).readTimeout(1,TimeUnit.SECONDS).callTimeout(2,TimeUnit.SECONDS)
    .addInterceptor { chain ->
     val u=chain.request().url
     check(u.scheme=="http" && u.host=="127.0.0.1" && u.port==port && u.encodedPath in setOf("/demo/info","/auth/login","/sos","/sos/active"))
     if(fault=="offline")throw IOException("Owned fixture offline")
     check((chain.request().body?.contentLength() ?: 0)<=4096)
     chain.proceed(chain.request().newBuilder().header("X-Demo-Fault",fault).build())
    }.addNetworkInterceptor { chain ->
     val u=chain.request().url
     check(u.scheme=="http" && u.host=="127.0.0.1" && u.port==port)
     val r=chain.proceed(chain.request())
     if((r.body?.contentLength() ?: -1) !in 0..65536){r.close();throw IOException("Bounded fixture response required")};r
    }.build()
   auth=OkHttpAuthApiClient(base,pinned)
   api=OkHttpSosApiClient(base,object:AccessTokenProvider {override fun getAccessToken()=token},pinned)
   transport=ApiSosTransport(api)
  }
  setContent { DisasterAlertTheme { Surface { DemoUi() } } }
 }
 fun login(wanted:String) {
  if(busy || base.isBlank() || wanted !in setOf("citizen","rescuer"))return
  val submitted=epoch;busy=true
  lifecycleScope.launch {
   val handshake=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {pinned.newCall(Request.Builder().url("$base/demo/info").build()).execute().use {it.isSuccessful && it.body!!.string()=="P5-E10A-SYNTHETIC-NO-DISPATCH"}}.getOrDefault(false)
   }
   val r=if(handshake)auth.login((if(wanted=="citizen" && citizenIndex>1)"citizen$citizenIndex" else wanted)+"@example.invalid","fixture-only-password") else AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.NO_INTERNET,"Fixture unavailable"))
   if(epoch==submitted){when(r){is AuthApiResult.Success->{token=r.value.accessToken;role=wanted;page=if(wanted=="rescuer")"map" else "home";message="Phiên $wanted synthetic"};is AuthApiResult.Failure->message="LOGIN_ERROR"};busy=false}
  }
 }
 fun logout(){citizenIndex=1;epoch++;token=null;role="";page="home";markers=emptyList();selected=null;lastCreated=null;busy=false;fault="ok";adults=1;description="SYNTHETIC_TEST_DATA cần hỗ trợ";goal="B";refreshes=0;message="Đã xóa phiên demo"}
 fun submit(){
  if(role!="citizen" || busy)return
  if(!description.startsWith("SYNTHETIC_TEST_DATA") || description.length>300){message="FORM_ERROR: dùng mô tả synthetic";return}
  val submitted=epoch;busy=true;lastCreated=null;message="Đang gửi fixture"
  val target=graph.nodes.getValue(goal).position
  val request=SosRescueInfo(adults=adults,condition="SYNTHETIC_TEST_DATA",description=description,latitude=target.latitude,longitude=target.longitude,createdAt=1794268800000L,batteryPercent=-1)
  lifecycleScope.launch {
   val r=transport.send(request)
   if(epoch==submitted){message=when(r){is SosSubmissionResult.Success->{lastCreated=r.eventId;"FIXTURE_STORED ${r.eventId} status=${r.serverSos?.status}"};is SosSubmissionResult.Failed->"FIXTURE_ERROR ${r.reason}";is SosSubmissionResult.Queued->"FIXTURE_ERROR queued"};busy=false}
  }
 }
 fun refresh(){
  if(role!="rescuer" || busy)return
  val submitted=epoch;busy=true;message="Đang đọc fixture"
  lifecycleScope.launch {
   val r=api.getActiveSos()
   if(epoch==submitted){
    selected=null
    when(r){is SosApiResultList.Success->{
     val valid=r.events.all{runCatching{java.util.UUID.fromString(it.eventId)}.isSuccess && it.condition=="SYNTHETIC_TEST_DATA" && it.status=="ACTIVE" && it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in 16.04..16.06 && it.longitude in 108.19..108.21}
     markers=if(valid)r.events.distinctBy{it.eventId} else emptyList()
     message=if(valid)"GET_OK ${markers.size} SOS" else "GET_ERROR invalid fixture data"
    };is SosApiResultList.Failure->{markers=emptyList();message="GET_ERROR ${r.error.kind}"}}
    refreshes++;busy=false
   }
  }
 }
 @Composable private fun DemoUi(){
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp)){
   Text("SYNTHETIC_TEST_DATA — P5-E10A",style=MaterialTheme.typography.titleMedium)
   Text("Không SOS thật / GPS / dispatch. F06 OPEN",style=MaterialTheme.typography.labelMedium)
   Text("NAVIGATION ALLOWED = FALSE • STRICT_ROUTING_DISABLED",style=MaterialTheme.typography.labelSmall)
   if(!loggedIn){
    TextButton(onClick={citizenIndex=citizenIndex%3+1},enabled=!busy){Text("Người dân TEST: $citizenIndex")}
    Button(onClick={login("citizen")},enabled=!busy && base.isNotBlank()){Text("Người dân demo")}
    Button(onClick={login("rescuer")},enabled=!busy && base.isNotBlank()){Text("Đội cứu hộ demo")}
    Text(message)
   }else{
    Row{Text("Vai trò: $role",Modifier.weight(1f));TextButton(onClick=::logout){Text("Đăng xuất")}}
    if(role=="citizen")Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
     if(page=="home")Button(onClick={page="form"}){Text("SOS synthetic")}
     else{
      Text("FORM TEST-ONLY • production ApiSosTransport")
      Text("Không nhập thông tin cá nhân; tọa độ TEST")
      Row{Text("Người lớn: $adults");TextButton(onClick={adults=(adults+1).coerceAtMost(10)}){Text("Thêm người")}}
      OutlinedTextField(value=description,onValueChange={description=it.take(300)},label={Text("Mô tả synthetic")},modifier=Modifier.fillMaxWidth())
      Button(onClick={goal=if(goal=="B")"H" else "B"}){Text("Tọa độ TEST: $goal")}
      FaultControls()
      Button(onClick=::submit,enabled=!busy){Text("Gửi SOS fixture")}
     }
     Text(message)
    }else{
     GraphCanvas()
     Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
      Button(onClick=::refresh,enabled=!busy){Text("Refresh SOS")}
      Text(message)
      FaultControls()
      Text("Marker từ GET /sos/active: ${markers.size}")
      selected?.let {s->Text("ID: ${s.eventId}");Text("Trạng thái: ${s.status}");Text("Người lớn: ${s.adults}");Text("Mô tả: ${s.description}");Text("Tọa độ: ${s.latitude}, ${s.longitude}")}
      Text("Graph diagnostic E09; không tính tuyến / nhận nhiệm vụ")
     }
    }
   }
  }
 }
 @Composable private fun FaultControls(){
  Row{TextButton(onClick={fault="ok"}){Text("HTTP OK")};TextButton(onClick={fault="403"}){Text("HTTP 403")};TextButton(onClick={fault="500"}){Text("HTTP 500")}}
  Row{TextButton(onClick={fault="timeout"}){Text("Timeout")};TextButton(onClick={fault="offline"}){Text("Offline")};Text("Mode: $fault")}
 }
 // Shared E09 projection, applied to GET response coordinates. Overlay markers are real clickable UI.
 fun fraction(latitude:Double,longitude:Double)=Pair((longitude-108.2000)/.0035,(latitude-16.0490)/.0035)
 @Composable private fun GraphCanvas(){
  BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp).background(Color(0xffe9eff2))){
   Canvas(Modifier.fillMaxSize()){
    fun xy(p:GeoPoint):Offset{val(fx,fy)=fraction(p.latitude,p.longitude);return Offset(24f+(fx*(size.width-48)).toFloat(),size.height-24f-(fy*(size.height-48)).toFloat())}
    for(e in graph.directedEdges)drawLine(Color.Gray,xy(graph.nodes.getValue(e.fromNodeId).position),xy(graph.nodes.getValue(e.toNodeId).position),4f)
    val paint=android.graphics.Paint().apply{color=android.graphics.Color.BLACK;textSize=26f;isAntiAlias=true}
    for(n in graph.nodes.values){val p=xy(n.position);drawCircle(Color.DarkGray,8f,p);drawContext.canvas.nativeCanvas.drawText(n.id,p.x+10,p.y-10,paint)}
   }
   val density=androidx.compose.ui.platform.LocalDensity.current
   val margin=with(density){24f.toDp()}
   for((index,s) in markers.withIndex()){
    val(fx,fy)=fraction(s.latitude,s.longitude)
    val x=margin+(maxWidth-margin*2)*fx.toFloat();val y=maxHeight-margin-(maxHeight-margin*2)*fy.toFloat()
    // Same-coordinate records retain individual controls; fan offset is disclosed in UI.
    val same=markers.take(index).count{it.latitude==s.latitude && it.longitude==s.longitude}
    FilledIconButton(onClick={selected=s},modifier=Modifier.offset(x-20.dp+16.dp*same,y-20.dp).size(40.dp).semantics{contentDescription="SOS ${s.eventId}"},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.Magenta)){Text((index+1).toString(),color=Color.White)}
   }
  }
  Text("Tím: SOS từ GET; trùng tọa độ xếp lệch 16dp",style=MaterialTheme.typography.labelSmall)
 }
}
