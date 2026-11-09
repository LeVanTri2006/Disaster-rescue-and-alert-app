package com.example.bai1.uiisolated

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bai1.data.model.WarningItem
import com.example.bai1.data.repository.DisasterRepository
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.bai1.MainAppHost
import com.example.bai1.auth.*
import com.example.bai1.auth.session.*
import com.example.bai1.auth.credentials.CredentialStateCleaner
import com.example.bai1.auth.network.OkHttpAuthApiClient
import com.example.bai1.presentation.navigation.AppNavGraph
import com.example.bai1.rescue.routing.repository.DefaultRoutingRepository
import com.example.bai1.rescue.routing.download.OkHttpGraphDownloader
import com.example.bai1.rescue.routing.network.OkHttpRoutingApiClient
import com.example.bai1.sos.network.AccessTokenProvider
import okhttp3.*
import java.net.Proxy
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Standalone source set only: no production Application, DI, SDK, backend fallback or account access. */
class GraphFixtureApplication : Application(), MainAppHost {
    lateinit var repository: AuthRepository; private set
    lateinit var storage: SharedPreferencesTokenStorage; private set
    lateinit var routing: DefaultRoutingRepository; private set
    lateinit var model: AuthViewModel; private set
    lateinit var roleModel: RoleSelectionViewModel
    lateinit var nav: NavHostController; private set
    lateinit var client: OkHttpClient; private set
    lateinit var base: String; private set
    val cloud = IsolatedUiActivity.NoCloudSession()
    val unavailableRenders = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val writes = AtomicInteger()
    val credentialClears = AtomicInteger()
    val credentialLaunches = AtomicInteger()
    @Volatile var mode = "ok"
    @Volatile var expiredAccess = false
    @Volatile var barrier: String? = null
    @Volatile var barrierPath = "/auth/login"
    var warningScreens = false; private set
    var warningItems by mutableStateOf<List<WarningItem>>(DisasterRepository.warningList)
    var accountScreens = false; private set
    @Volatile var profileShape = "normal"
    @Volatile var roleFault = "ok"
    val roleCallbacks = java.util.concurrent.CopyOnWriteArrayList<com.example.bai1.data.model.UserRole>()
    var initializations = 0; private set
    override fun initialize(activity: ComponentActivity) {
        initializations++
        if (::repository.isInitialized) return
        val port = activity.intent.getIntExtra("fixturePort", 0)
        require(port in 1024..65535)
        require(activity.intent.getBooleanExtra("usbFixture", false))
        warningScreens = activity.intent.getBooleanExtra("warningScreens", false)
        accountScreens = warningScreens || activity.intent.getBooleanExtra("accountScreens", false)
        if (accountScreens) com.example.bai1.data.repository.DisasterRepository.setUserRole(com.example.bai1.data.model.UserRole.USER)
        mode = activity.intent.getStringExtra("mode") ?: "ok"
        require(mode in setOf("ok", "401", "403", "500", "offline", "timeout", "refreshReject", "barrier"))
        base = "http://127.0.0.1:$port"
        client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(if (mode == "barrier") 12 else 1, TimeUnit.SECONDS)
            .callTimeout(if (mode == "barrier") 15 else 3, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val u = chain.request().url
                check(u.scheme == "http" && u.host == "127.0.0.1" && u.port == port)
                check(u.encodedPath in setOf("/auth/login", "/auth/register", "/auth/me", "/auth/refresh", "/auth/firebase-token", "/users/me/role", "/routing/current", "/graph.gz") || Regex("^/fixture/barrier/[a-z-]{1,40}/(entered|release|done)$").matches(u.encodedPath))
                if (mode == "offline") throw IOException("Owned graph fixture offline")
                val request = chain.request().newBuilder().header("X-Fixture-Mode", mode)
                if (accountScreens) {
                    request.header("X-Fixture-Profile-Shape", profileShape)
                    request.header("X-Fixture-Role-Fault", roleFault)
                }
                if (expiredAccess) request.header("X-Fixture-Expire-Access", "true")
                barrier?.takeIf { u.encodedPath == barrierPath }?.let { request.header("X-Fixture-Barrier", it) }
                chain.proceed(request.build())
            }.addNetworkInterceptor { chain ->
                val u = chain.request().url
                check(u.scheme == "http" && u.host == "127.0.0.1" && u.port == port)
                chain.proceed(chain.request())
            }.build()
        storage = SharedPreferencesTokenStorage(this)
        if (activity.intent.getBooleanExtra("resetAuthStorage", true)) storage.clear()
        val counted = object : TokenStorage by storage {
            override fun save(accessToken: String, refreshToken: String): Boolean {
                writes.incrementAndGet(); return storage.save(accessToken, refreshToken)
            }
        }
        val api = OkHttpAuthApiClient(base, client)
        routing = DefaultRoutingRepository(OkHttpRoutingApiClient(base, object : AccessTokenProvider {
            override fun getAccessToken() = storage.getAccessToken()
        }, client), OkHttpGraphDownloader(java.io.File(cacheDir, "p5e06_graphs"), client))
        repository = AuthRepository(api, counted, cloud, routing::invalidateSession)
    }
    @Composable override fun AppGraph(navController: NavHostController) {
        val authModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(repository))
        SideEffect { nav = navController; model = authModel }
        val screens = remember { if (warningScreens) ActualWarningFixtureScreens(this) else if (accountScreens) ActualAccountFixtureScreens(this) else GraphFixtureScreens(this) }
        Column(Modifier.fillMaxSize()) {
            if (warningScreens) Text(
                "SYNTHETIC_TEST_DATA — DEMO\nKhông phải cảnh báo thực tế; dữ liệu chưa xác minh. Không có dữ liệu không đồng nghĩa không có nguy cơ.",
                modifier = Modifier.fillMaxWidth().padding(6.dp)
            )
            Box(Modifier.weight(1f)) {
        AppNavGraph(navController, authModel, screens,
            CredentialStateCleaner { credentialClears.incrementAndGet() }, onUserRoleChanged = { role ->
                if (accountScreens) { roleCallbacks.add(role); com.example.bai1.data.repository.DisasterRepository.setUserRole(role) }
            })
            }
        }
    }
}
