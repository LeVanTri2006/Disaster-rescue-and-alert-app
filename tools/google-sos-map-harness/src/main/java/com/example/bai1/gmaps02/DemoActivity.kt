package com.example.bai1.gmaps02

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.bai1.ui.theme.DisasterAlertTheme
import com.example.bai1.auth.network.*
import com.example.bai1.sos.domain.*
import com.example.bai1.sos.network.*
import com.example.bai1.sos.transport.ApiSosTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
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
 companion object {
 val B=LatLng(16.0510,108.2030);val H=LatLng(16.0520,108.2030)
 val CENTER=LatLng(16.0510,108.2025);val RESCUER=LatLng(16.0500,108.2020)
 }
 var sdkMap:com.google.android.gms.maps.GoogleMap?=null;private set
 var clickedMarker:com.google.android.gms.maps.model.Marker?=null;private set
 val markerStates=mutableMapOf<String,MarkerState>()
 var loaded by mutableStateOf(false);private set
 var loadedCallbacks by mutableStateOf(0);private set
 var loadState by mutableStateOf("LOADING");private set
 private var epoch=0
 private var token:String?=null
 private lateinit var pinned:OkHttpClient
 private lateinit var auth:OkHttpAuthApiClient
 private lateinit var api:OkHttpSosApiClient
 private lateinit var transport:ApiSosTransport
 var base="";private set
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  val prefs=getSharedPreferences("gmaps02_demo_only",MODE_PRIVATE)
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
    runCatching {pinned.newCall(Request.Builder().url("$base/demo/info").build()).execute().use {it.isSuccessful && it.body!!.string()=="P5-E10-M02-SYNTHETIC-NO-DISPATCH"}}.getOrDefault(false)
   }
   val r=if(handshake)auth.login((if(wanted=="citizen" && citizenIndex>1)"citizen$citizenIndex" else wanted)+"@example.invalid","fixture-only-password") else AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.NO_INTERNET,"Fixture unavailable"))
   if(epoch==submitted){when(r){is AuthApiResult.Success->{token=r.value.accessToken;role=wanted;page=if(wanted=="rescuer")"map" else "home";message="Phiên $wanted synthetic"};is AuthApiResult.Failure->message="LOGIN_ERROR"};busy=false}
  }
 }
 fun logout(){citizenIndex=1;epoch++;token=null;role="";page="home";markers=emptyList();selected=null;clickedMarker=null;markerStates.clear();sdkMap=null;loaded=false;loadedCallbacks=0;loadState="LOADING";lastCreated=null;busy=false;fault="ok";adults=1;description="SYNTHETIC_TEST_DATA cần hỗ trợ";goal="B";refreshes=0;message="Đã xóa phiên demo"}
 fun submit(){
  if(role!="citizen" || busy)return
  if(!description.startsWith("SYNTHETIC_TEST_DATA") || description.length>300){message="FORM_ERROR: dùng mô tả synthetic";return}
  val submitted=epoch;busy=true;lastCreated=null;message="Đang gửi fixture"
  val target=if(goal=="B")B else H
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
    selected=null;clickedMarker=null
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
   Text("GOOGLE MAPS + SOS — M02",style=MaterialTheme.typography.titleMedium)
   Text("DỮ LIỆU VỊ TRÍ MÔ PHỎNG • F06 OPEN",style=MaterialTheme.typography.labelMedium)
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
     SosGoogleMap()
     Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
      Button(onClick=::refresh,enabled=!busy){Text("Refresh SOS")}
      Text(message)
      FaultControls()
      Text("Marker từ GET /sos/active: ${markers.size}")
      selected?.let {s->Text("ID: ${s.eventId}");Text("Trạng thái: ${s.status}");Text("Người lớn: ${s.adults}");Text("Trẻ em: ${s.children}; cao tuổi: ${s.elderly}");Text("Mô tả: ${s.description}");Text("Tọa độ: ${s.latitude}, ${s.longitude}")}
      for(s in markers) TextButton(onClick={selected=s;markerStates[s.eventId]?.showInfoWindow()}){Text("SOS ${s.eventId}")}
      Text("Danh sách UUID hỗ trợ pin trùng tọa độ; không dịch LatLng")
     }
    }
   }
  }
 }
 @Composable private fun FaultControls(){
  Row{TextButton(onClick={fault="ok"}){Text("HTTP OK")};TextButton(onClick={fault="403"}){Text("HTTP 403")};TextButton(onClick={fault="500"}){Text("HTTP 500")}}
  TextButton(onClick={fault="commit_timeout"}){Text("Timeout sau commit")}
  Row{TextButton(onClick={fault="timeout"}){Text("Timeout")};TextButton(onClick={fault="offline"}){Text("Offline")};Text("Mode: $fault")}
 }

 @OptIn(MapsComposeExperimentalApi::class)
 @Composable private fun SosGoogleMap(){
  val camera=rememberCameraPositionState{position=CameraPosition.fromLatLngZoom(CENTER,15f)}
  LaunchedEffect(Unit){delay(30000);if(!loaded)loadState="ERROR_OR_TIMEOUT"}
  Text("SDK: $loadState",style=MaterialTheme.typography.labelSmall)
  GoogleMap(modifier=Modifier.fillMaxWidth().height(290.dp),cameraPositionState=camera,
   properties=MapProperties(isMyLocationEnabled=false),
   uiSettings=MapUiSettings(myLocationButtonEnabled=false,mapToolbarEnabled=false,zoomControlsEnabled=true,rotationGesturesEnabled=false,tiltGesturesEnabled=false),
   onMapLoaded={loaded=true;loadedCallbacks++;loadState="LOADED_CALLBACK";android.util.Log.i("M02_MAP","onMapLoaded count=$loadedCallbacks")}){
   MapEffect(Unit){map->sdkMap=map;if(!loaded)loadState="SDK_READY";android.util.Log.i("M02_MAP","SDK_READY location=false")}
   Marker(state=rememberMarkerState(position=RESCUER),title="RESCUER TEST",snippet="16.0500, 108.2020 — mô phỏng",icon=BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
   // The only SOS input is the validated typed GET list. POST never changes markers.
   for(s in markers) key(s.eventId,s.latitude,s.longitude){
    val state=rememberMarkerState(position=LatLng(s.latitude,s.longitude))
    DisposableEffect(s.eventId){markerStates[s.eventId]=state;onDispose{if(markerStates[s.eventId]===state)markerStates.remove(s.eventId)}}
    Marker(state=state,title="SOS ${s.eventId}",snippet="${s.status} | ${s.adults+s.children+s.elderly} người | ${s.description} | ${s.latitude}, ${s.longitude}",onClick={m->clickedMarker=m;selected=s;false})
   }
  }
 }
}
