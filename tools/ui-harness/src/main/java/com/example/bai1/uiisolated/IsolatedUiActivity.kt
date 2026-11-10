package com.example.bai1.uiisolated

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import com.example.bai1.auth.network.*
import com.example.bai1.presentation.auth.*
import com.example.bai1.presentation.settings.AboutAppScreen
import com.example.bai1.presentation.components.*
import com.example.bai1.ui.theme.DisasterAlertTheme
import com.example.bai1.rescue.routing.repository.DefaultRoutingRepository
import com.example.bai1.rescue.routing.download.OkHttpGraphDownloader
import com.example.bai1.rescue.routing.network.OkHttpRoutingApiClient
import com.example.bai1.sos.network.AccessTokenProvider
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Subset adapter only. Does not instantiate MainActivity/AppNavGraph/production DI or stored accounts. */
class IsolatedUiActivity : ComponentActivity() {
    var permissionState by mutableStateOf("not-granted"); private set
    var token: String? by mutableStateOf(null); private set
    var busy by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set
    var sessionState by mutableStateOf("unauthenticated"); private set
    var networkDiagnostic: String? = null; private set
    @Volatile var barrierTag: String? = null
    @Volatile var barrierPath: String = "/auth/login"
    @Volatile var fixtureFault: String? = null
    private val writes = java.util.concurrent.atomic.AtomicInteger()
    val tokenWrites get() = writes.get()
    var authScope = false; private set
    lateinit var repository: com.example.bai1.auth.AuthRepository; private set
    lateinit var authModel: com.example.bai1.auth.AuthViewModel; private set
    lateinit var storage: com.example.bai1.auth.session.SharedPreferencesTokenStorage; private set
    val cloud = NoCloudSession()
    private val authStore = androidx.lifecycle.ViewModelStore()
    class NoCloudSession : com.example.bai1.auth.firebase.FirebaseAuthSession {
        var signIns = 0; var signOuts = 0; var signedIn = false; var throwSignOut = false
        override suspend fun signInWithCustomToken(customToken: String): Boolean { signIns++; signedIn = true; return true }
        override suspend fun signOut() { signOuts++; if (throwSignOut) error("Controlled no-cloud cleanup failure"); signedIn = false }
    }
    var registrationSubmissions = 0; private set
    private lateinit var client: OkHttpClient
    private lateinit var base: String
    private lateinit var auth: OkHttpAuthApiClient
    lateinit var routing: DefaultRoutingRepository; private set
    private val requestLocal = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionState = if (granted) "granted" else "denied"
        if (!granted) error = "Fixture local network permission denied"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val port = intent.getIntExtra("fixturePort", 0); require(port in 1024..65535)
        val mode = intent.getStringExtra("mode") ?: "ok"
        require(mode in setOf("ok", "401", "403", "500", "offline", "timeout", "slow", "reset", "invalid", "refreshReject", "refreshOk", "barrier"))
        val host = if (intent.getBooleanExtra("usbFixture", false)) "127.0.0.1" else "10.0.2.2"
        base = "http://$host:$port"
        client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(2, TimeUnit.SECONDS).readTimeout(if (mode in setOf("slow", "barrier")) 12 else 1, TimeUnit.SECONDS)
            .callTimeout(if (mode in setOf("slow", "barrier")) 15 else 3, TimeUnit.SECONDS).eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    networkDiagnostic = ioe.javaClass.simpleName + ":" + (ioe.message ?: "").replace(Regex("https?://[^ ]+"), "<owned-url>")
                }
            }).addInterceptor { chain ->
                if (mode == "offline") throw IOException("Owned fixture offline")
                val u = chain.request().url
                check(u.scheme == "http" && u.host == host && u.port == port)
                check(u.encodedPath in setOf("/auth/register", "/auth/login", "/auth/me", "/auth/refresh", "/auth/firebase-token", "/routing/current", "/graph.gz", "/health", "/fixture/release-loading") || Regex("^/fixture/barrier/[a-z-]{1,40}/(entered|release|done)$").matches(u.encodedPath))
                val request = chain.request().newBuilder().header("X-Fixture-Mode", if (u.encodedPath == "/auth/login") fixtureFault ?: mode else mode)
                val tag = barrierTag
                if (tag != null && u.encodedPath == barrierPath) request.header("X-Fixture-Barrier", tag)
                chain.proceed(request.build())
            }.addNetworkInterceptor { chain ->
                val u = chain.request().url
                check(u.scheme == "http" && u.host == host && u.port == port)
                chain.proceed(chain.request())
            }.build()
        auth = OkHttpAuthApiClient(base, client)
        routing = DefaultRoutingRepository(OkHttpRoutingApiClient(base, object : AccessTokenProvider {
            override fun getAccessToken() = token
        }, client), OkHttpGraphDownloader(File(cacheDir, "owned-routing"), client))
        authScope = intent.getBooleanExtra("authScope", false)
        if (authScope) {
            storage = com.example.bai1.auth.session.SharedPreferencesTokenStorage(this)
            if (savedInstanceState == null && intent.getBooleanExtra("resetAuthStorage", true)) storage.clear()
            val countedStorage = object : com.example.bai1.auth.session.TokenStorage by storage {
                override fun save(accessToken: String, refreshToken: String): Boolean {
                    writes.incrementAndGet(); return storage.save(accessToken, refreshToken)
                }
            }
            repository = com.example.bai1.auth.AuthRepository(auth, countedStorage, cloud, routing::invalidateSession)
            authModel = androidx.lifecycle.ViewModelProvider(authStore, com.example.bai1.auth.AuthViewModel.factory(repository))[com.example.bai1.auth.AuthViewModel::class.java]
            lifecycleScope.launch {
                repository.state.collect { state ->
                    busy = state is com.example.bai1.auth.session.AuthState.Loading
                    error = (state as? com.example.bai1.auth.session.AuthState.Error)?.message
                    token = if (state is com.example.bai1.auth.session.AuthState.Authenticated) storage.getAccessToken() else null
                    sessionState = if (token != null) "repository-authenticated" else "unauthenticated"
                }
            }
        }
        setContent {
            DisasterAlertTheme {
                val nav = rememberNavController()
                Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    Text("ISOLATED PRODUCTION UI SUBSET — STRICT ROUTING DISABLED", Modifier.testTag("isolation"))
                    Row {
                        Button(onClick = { requestLocal.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }, Modifier.testTag("request-permission"), enabled = android.os.Build.VERSION.SDK_INT >= 37) { Text("Allow local fixture") }
                        Text(permissionState, Modifier.testTag("permission"))
                    }
                    Row {
                        TextButton(onClick = { nav.navigate("register") }) { Text("Fixture: register") }
                        TextButton(onClick = { nav.navigate("about") }) { Text("Fixture: about") }
                        TextButton(onClick = { nav.navigate("states") }) { Text("Fixture: states") }
                    }
                    if (authScope) TextButton(onClick = { nav.navigate("login") }) { Text("Fixture: login") }
                    if (token != null) Row {
                        TextButton(onClick = { invalidate("logged-out") }) { Text("Fixture logout") }
                        TextButton(onClick = { invalidate("expired") }) { Text("Fixture expire session") }
                    }
                    Text(sessionState, Modifier.testTag("session"))
                    NavHost(nav, startDestination = intent.getStringExtra("startRoute") ?: "onboarding", modifier = Modifier.weight(1f)) {
                        composable("onboarding") { OnboardingScreen { nav.navigate(if (authScope) "login" else "register") } }
                        composable("register") {
                            if (token != null) EmptyState("Phiên fixture", "Synthetic session only — not rescue authorization")
                            else RegisterScreen(onRegisterSubmit = { name, email, phone, password -> register(name, email, phone, password) },
                                onBackToLogin = { nav.navigate("login") }, isLoading = busy, errorMessage = error)
                        }
                        composable("login") {
                            if (token != null) EmptyState("Phiên repository", "Synthetic account only — strict routing disabled")
                            else LoginScreen(onRegisterClick = { nav.navigate("register") }, onForgotPasswordClick = { error = "Forgot password outside isolated scope" },
                                onLogin = { id, password -> authModel.login(id, password) }, isLoading = busy, errorMessage = error,
                                onGoogleSignInClick = { error = "Synthetic credential launcher cancelled" })
                        }
                        composable("about") { AboutAppScreen { nav.popBackStack() } }
                        composable("states") {
                            Column {
                                LoadingState("Fixture loading")
                                EmptyState("Fixture empty", "Synthetic reference data")
                                ErrorState(error ?: "Fixture offline", onRetry = { probe() })
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        permissionState = if (android.os.Build.VERSION.SDK_INT < 37) "not-applicable" else if (checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) "granted" else if (permissionState == "denied") "denied" else "not-granted"
    }
    suspend fun loginSyntheticFixture() = auth.login("fixture@example.invalid", "fixture-only-password")
    private fun register(name: String, email: String, phone: String, password: String) {
        registrationSubmissions++
        if (authScope) { authModel.register(name, email, phone, password); return }
        if (busy) return
        busy = true; error = null
        lifecycleScope.launch {
            when (val result = auth.register(name, email, phone, password)) {
                is AuthApiResult.Success -> { token = result.value.accessToken; sessionState = "fixture-authenticated" }
                is AuthApiResult.Failure -> { invalidate("unauthenticated"); error = result.error.userMessage() }
            }
            busy = false
        }
    }
    private fun invalidate(state: String) {
        if (authScope) { authModel.logout(); return }
        token = null; sessionState = state; routing.invalidateSession()
    }
    fun probe() {
        busy = true; error = null
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching {
                client.newCall(Request.Builder().url("$base/health").build()).execute().use { check(it.isSuccessful); check(it.body!!.string() == "P5E02_LOCAL_ONLY") }
            }.isSuccess }
            error = if (ok) "Fixture connected" else "Fixture unavailable — check local permission"
            busy = false
        }
    }
    override fun onDestroy() {
        if (::routing.isInitialized) routing.invalidateSession()
        token = null
        authStore.clear()
        if (::client.isInitialized) client.dispatcher.cancelAll()
        super.onDestroy()
    }
}
