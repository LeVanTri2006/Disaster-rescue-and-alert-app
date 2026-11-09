package com.example.bai1.productionisolated
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.bai1.MainAppHost
import com.example.bai1.auth.*
import com.example.bai1.auth.session.*
import com.example.bai1.auth.network.OkHttpAuthApiClient
import com.example.bai1.sos.network.*
import com.example.bai1.sos.repository.*
import com.example.bai1.sos.transport.*
import com.example.bai1.sos.provider.*
import com.example.bai1.presentation.location.*
import com.example.bai1.presentation.navigation.*
import okhttp3.*
import java.net.Proxy
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Debug/test-only DI. Screens, navigation, ViewModels, repositories and serializers are production. */
class ProductionFixtureApplication:Application(),MainAppHost {
 class MemoryTokens:TokenStorage {
  private var access:String?=null;private var refresh:String?=null
  override fun save(accessToken:String,refreshToken:String):Boolean{access=accessToken;refresh=refreshToken;return true}
  override fun getAccessToken()=access;override fun getRefreshToken()=refresh
  override fun clear(){access=null;refresh=null};override fun isLoggedIn()=access!=null
 }
 val storage=MemoryTokens()
 lateinit var repository:AuthRepository;private set
 lateinit var api:OkHttpSosApiClient;private set
 lateinit var rescueRepository:com.example.bai1.rescue.repository.DefaultRescueMissionRepository;private set
 lateinit var nav:NavHostController;private set
 lateinit var authModel:AuthViewModel;private set
 var formModel:com.example.bai1.presentation.sos.SosRescueRequestViewModel?=null
 var mapModel:com.example.bai1.presentation.map.DisasterMapViewModel?=null
 var sdkMap:com.google.android.gms.maps.GoogleMap?=null
 var clicked:com.google.android.gms.maps.model.Marker?=null
 var selected:com.example.bai1.presentation.map.MapRescueMarker?=null
 var loadedCallbacks=0
 @Volatile var fault="ok"
 var testLatitude=16.051;var testLongitude=108.203
 private var sessionRepository:InMemorySosRepository?=null
 private var boundUser:String?=null
 private lateinit var transport:SosTransportManager
 val location=object:LocationProvider {override fun getCurrentLocation(onResult:(LocationResult)->Unit)=onResult(LocationResult.Success(testLatitude,testLongitude,0f,System.currentTimeMillis()))}
 fun <T:ViewModel> factory(create:()->T):ViewModelProvider.Factory=object:ViewModelProvider.Factory{
  @Suppress("UNCHECKED_CAST") override fun <V:ViewModel> create(modelClass:Class<V>):V=create() as V
 }
 fun sessionSos():InMemorySosRepository {
  val id=(repository.state.value as? AuthState.Authenticated)?.user?.id ?: error("Authenticated synthetic session required")
  if(boundUser!=id||sessionRepository==null){boundUser=id;sessionRepository=InMemorySosRepository(transport,api,serverOnlyActiveReads=true)}
  return sessionRepository!!
 }
 override fun initialize(activity:ComponentActivity){
  activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  if(::repository.isInitialized)return
  val port=activity.intent.getIntExtra("fixturePort",0);require(port in 1024..65535)
  val base="http://127.0.0.1:$port"
  val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
   .connectTimeout(1,TimeUnit.SECONDS).readTimeout(1,TimeUnit.SECONDS).callTimeout(2,TimeUnit.SECONDS)
   .addInterceptor {chain->val u=chain.request().url
    check(u.scheme=="http"&&u.host=="127.0.0.1"&&u.port==port&&u.encodedQuery==null&&u.encodedPath in setOf("/auth/login","/auth/me","/sos","/sos/active"))
    check((chain.request().body?.contentLength() ?: 0)<=4096)
    if(fault=="offline")throw IOException("Owned fixture offline")
    chain.proceed(chain.request().newBuilder().header("X-Demo-Fault",fault).build())
   }.addNetworkInterceptor{chain->val u=chain.request().url;check(u.host=="127.0.0.1"&&u.port==port&&u.scheme=="http");val r=chain.proceed(chain.request());if((r.body?.contentLength() ?: -1) !in 0..65536){r.close();throw IOException("Bounded fixture response required")};r}.build()
  val tokens=object:AccessTokenProvider{override fun getAccessToken()=storage.getAccessToken()}
  api=OkHttpSosApiClient(base,tokens,client)
  transport=SosTransportManager(FakeNetworkAvailabilityProvider(true),ApiSosTransport(api),object:MeshSosTransport{override suspend fun send(sos:com.example.bai1.sos.domain.SosRescueInfo)=com.example.bai1.sos.domain.SosSubmissionResult.Failed("Mesh disabled in isolated test")})
  rescueRepository=com.example.bai1.rescue.repository.DefaultRescueMissionRepository(com.example.bai1.rescue.network.OkHttpRescueMissionApiClient(base,tokens,client))
  repository=AuthRepository(OkHttpAuthApiClient(base,client),storage,firebaseAuthSession=null,invalidateRoutingSession={sessionRepository=null;boundUser=null;formModel=null;mapModel=null;sdkMap=null;selected=null;clicked=null;loadedCallbacks=0})
 }
 @Composable override fun AppGraph(navController:NavHostController){
  val model:AuthViewModel=viewModel(factory=AuthViewModel.factory(repository))
  val state by repository.state.collectAsState()
  SideEffect{nav=navController;authModel=model}
  val screens=remember{FixtureScreens(this)}
  Column(Modifier.fillMaxSize()){
   Text("SYNTHETIC TEST • Không SOS thật/GPS/Firebase\nF06 OPEN • STRICT_ROUTING_DISABLED=true",modifier=Modifier.statusBarsPadding().padding(4.dp),style=MaterialTheme.typography.labelSmall)
   Box(Modifier.weight(1f)){AppNavGraph(navController,model,screens,com.example.bai1.auth.credentials.CredentialStateCleaner{},onUserRoleChanged={com.example.bai1.data.repository.DisasterRepository.setUserRole(it)},startDestination=Routes.LOGIN)}
  }
 }
}
