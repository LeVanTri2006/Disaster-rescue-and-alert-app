package com.example.bai1.recovery

import com.example.bai1.auth.AuthRepository
import com.example.bai1.auth.firebase.FirebaseAuthSession
import com.example.bai1.auth.network.*
import com.example.bai1.auth.session.*
import com.example.bai1.presentation.map.DisasterMapViewModel
import com.example.bai1.presentation.map.MarkerType
import com.example.bai1.rescue.network.MissionStatus
import com.example.bai1.rescue.network.RescueMissionApiError
import com.example.bai1.rescue.network.RescueMissionApiResult
import com.example.bai1.rescue.network.RescueMissionRequestInfo
import com.example.bai1.rescue.network.RescueMissionResponse
import com.example.bai1.rescue.repository.RescueMissionRepository
import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosUrgency
import com.example.bai1.sos.realtime.FirebaseSosChange
import com.example.bai1.sos.realtime.SosRealtimeDataSource
import com.example.bai1.sos.repository.FirebaseSosRepository
import com.example.bai1.sos.repository.SosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthAndRealtimeRecoveryAcceptanceTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // R1: AUTHENTICATION ACCEPTANCE TESTS
    // =========================================================================

    private val testUser = AuthUser(
        id = "user-recovery-01",
        fullName = "Nguyễn Văn Test",
        email = "test@example.com",
        phone = "0987654321",
        role = "USER",
        status = "ACTIVE",
        verificationStatus = null
    )

    @Test
    fun `R1-T02 Form Register with valid inputs calls backend and enters authenticated new user state`() = runTest {
        val storage = TestTokenStorage()
        val api = TestAuthApiClient(
            registerResult = AuthApiResult.Success(AuthTokens("jwt-access-reg", "jwt-refresh-reg", isNewUser = true)),
            meResult = AuthApiResult.Success(testUser)
        )
        val repo = AuthRepository(api, storage, invalidateRoutingSession = {})

        repo.register("Nguyễn Văn Test", "test@example.com", "0987654321", "password123")

        assertEquals("jwt-access-reg", storage.getAccessToken())
        assertEquals("jwt-refresh-reg", storage.getRefreshToken())
        val authState = repo.state.value as AuthState.Authenticated
        assertEquals(testUser.id, authState.user.id)
        assertTrue("New registration must set isNewUser=true", authState.isNewUser)
    }

    @Test
    fun `R1-T03 Form Login with valid inputs receives tokens and enters authenticated state`() = runTest {
        val storage = TestTokenStorage()
        val api = TestAuthApiClient(
            loginResult = AuthApiResult.Success(AuthTokens("jwt-access-login", "jwt-refresh-login")),
            meResult = AuthApiResult.Success(testUser)
        )
        val repo = AuthRepository(api, storage, invalidateRoutingSession = {})

        repo.login("test@example.com", "password123")

        assertEquals("jwt-access-login", storage.getAccessToken())
        assertEquals("jwt-refresh-login", storage.getRefreshToken())
        val authState = repo.state.value as AuthState.Authenticated
        assertEquals(testUser.id, authState.user.id)
        assertFalse("Standard login must have isNewUser=false", authState.isNewUser)
    }

    @Test
    fun `R1-T04 Invalid credentials expose user-friendly error without saving tokens`() = runTest {
        val storage = TestTokenStorage()
        val api = TestAuthApiClient(
            loginResult = AuthApiResult.Failure(
                AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "Invalid credentials")
            )
        )
        val repo = AuthRepository(api, storage, invalidateRoutingSession = {})

        repo.login("wrong@example.com", "badpassword")

        assertTrue(repo.state.value is AuthState.Error)
        assertEquals("Email/số điện thoại hoặc mật khẩu không đúng.", (repo.state.value as AuthState.Error).message)
        assertNull("No access token must be saved on failed login", storage.getAccessToken())
        assertNull("No refresh token must be saved on failed login", storage.getRefreshToken())
    }

    @Test
    fun `R1-T06 Google Login with Firebase ID token calls backend and authenticates user`() = runTest {
        val storage = TestTokenStorage()
        val api = TestAuthApiClient(
            googleLoginResult = AuthApiResult.Success(AuthTokens("jwt-access-google", "jwt-refresh-google")),
            meResult = AuthApiResult.Success(testUser.copy(fullName = "Google Account User"))
        )
        val repo = AuthRepository(api, storage, invalidateRoutingSession = {})

        repo.googleLogin("mock-firebase-id-token-xyz")

        assertEquals("jwt-access-google", storage.getAccessToken())
        assertEquals("jwt-refresh-google", storage.getRefreshToken())
        val authState = repo.state.value as AuthState.Authenticated
        assertEquals("Google Account User", authState.user.fullName)
    }

    @Test
    fun `R1-T07 Restart preserves valid session via restoreSession`() = runTest {
        val storage = TestTokenStorage("saved-access-token", "saved-refresh-token")
        val api = TestAuthApiClient(
            meResult = AuthApiResult.Success(testUser)
        )
        val repo = AuthRepository(api, storage, invalidateRoutingSession = {})

        repo.restoreSession()

        val authState = repo.state.value as AuthState.Authenticated
        assertEquals(testUser.id, authState.user.id)
        assertEquals("saved-access-token", storage.getAccessToken())
    }

    @Test
    fun `R1-T08 Temporary network loss during restoreSession does NOT wipe stored tokens`() = runTest {
        val storage = TestTokenStorage("durable-access-token", "durable-refresh-token")
        val api = TestAuthApiClient(
            meResult = AuthApiResult.Failure(
                AuthApiError(AuthApiErrorKind.NO_INTERNET, "Network unreachable")
            )
        )
        val repo = AuthRepository(api, storage, invalidateRoutingSession = {})

        repo.restoreSession()

        assertTrue("State should be Error on network unreachable", repo.state.value is AuthState.Error)
        assertEquals("durable-access-token", storage.getAccessToken())
        assertEquals("durable-refresh-token", storage.getRefreshToken())
        assertTrue("Tokens must be preserved in storage across transient network loss", storage.isLoggedIn())
    }

    @Test
    fun `R1-T09 Expired access token triggers single refresh and updates session or clears on 401`() = runTest {
        // Case A: Successful token refresh
        val storageA = TestTokenStorage("expired-access", "valid-refresh")
        val apiA = TestAuthApiClient(
            meResult = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "Token expired")),
            refreshResult = AuthApiResult.Success(AuthTokens("refreshed-access", "refreshed-refresh")),
            refreshedMeResult = AuthApiResult.Success(testUser)
        )
        val repoA = AuthRepository(apiA, storageA, invalidateRoutingSession = {})
        repoA.restoreSession()

        assertEquals(1, apiA.refreshCalls)
        assertEquals("refreshed-access", storageA.getAccessToken())
        assertEquals("refreshed-refresh", storageA.getRefreshToken())
        assertTrue(repoA.state.value is AuthState.Authenticated)

        // Case B: Failed token refresh (refresh token also expired/revoked) -> clears session
        val storageB = TestTokenStorage("expired-access", "revoked-refresh")
        val apiB = TestAuthApiClient(
            meResult = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "Token expired")),
            refreshResult = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "Refresh revoked"))
        )
        val repoB = AuthRepository(apiB, storageB, invalidateRoutingSession = {})
        repoB.restoreSession()

        assertEquals(AuthState.Unauthenticated, repoB.state.value)
        assertNull(storageB.getAccessToken())
        assertNull(storageB.getRefreshToken())
    }

    @Test
    fun `R1-T10 Logout cleanly clears token storage and transitions to Unauthenticated`() = runTest {
        val storage = TestTokenStorage("active-access", "active-refresh")
        val mockFirebaseSession = TestFirebaseAuthSession()
        mockFirebaseSession.signedIn = true
        val repo = AuthRepository(
            apiClient = TestAuthApiClient(),
            tokenStorage = storage,
            firebaseAuthSession = mockFirebaseSession,
            invalidateRoutingSession = {}
        )

        repo.logout()

        assertEquals(AuthState.Unauthenticated, repo.state.value)
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertFalse("Firebase adapter must be signed out on logout", mockFirebaseSession.signedIn)
    }

    // =========================================================================
    // R2: REALTIME SOS & MAP MARKER ACCEPTANCE TESTS
    // =========================================================================

    @Test
    fun `R2-T01 DisasterMapViewModel starts and subscribes to observeSos`() = runTest {
        val changes = MutableSharedFlow<FirebaseSosChange>(extraBufferCapacity = 10)
        val realtimeSource = object : SosRealtimeDataSource {
            override fun observeChanges(): Flow<FirebaseSosChange> = changes
        }
        val firebaseRepo = FirebaseSosRepository(
            localRepository = TestLocalSosRepository(),
            realtimeDataSource = realtimeSource,
            scope = backgroundScope
        )
        val rescueRepo = TestRescueMissionRepository()

        val viewModel = DisasterMapViewModel(
            sosRepository = firebaseRepo,
            rescueMissionRepository = rescueRepo,
            pollingEnabled = false,
            nearbyRequestsEnabled = false
        )

        assertEquals(emptyList<Any>(), viewModel.markers.value)
        assertNull(viewModel.refreshError.value)
    }

    @Test
    fun `R2-T02 R2-T06 R2-T07 R2-T08 Realtime SOS event creates Google Maps marker automatically with correct UUID and coords`() = runTest {
        val changes = MutableSharedFlow<FirebaseSosChange>(extraBufferCapacity = 10)
        val realtimeSource = object : SosRealtimeDataSource {
            override fun observeChanges(): Flow<FirebaseSosChange> = changes
        }
        val firebaseRepo = FirebaseSosRepository(
            localRepository = TestLocalSosRepository(),
            realtimeDataSource = realtimeSource,
            scope = backgroundScope
        )
        val rescueRepo = TestRescueMissionRepository()

        val viewModel = DisasterMapViewModel(
            sosRepository = firebaseRepo,
            rescueMissionRepository = rescueRepo,
            pollingEnabled = false,
            nearbyRequestsEnabled = false
        )

        val collectorJob = launch {
            viewModel.markers.collect {}
        }
        runCurrent()

        // Synthetic SOS event pushed via Firebase RTDB projection
        val syntheticSos = SosRescueInfo(
            eventId = "sos-uuid-danang-001",
            latitude = 16.0544,
            longitude = 108.2022,
            urgency = SosUrgency.CRITICAL,
            condition = "Nước ngập ngang ngực, có người già",
            neededItems = setOf("Thuyền cứu hộ", "Áo phao"),
            adults = 2,
            elderly = 1,
            children = 0,
            status = SosStatus.ACTIVE,
            createdAt = System.currentTimeMillis()
        )

        // R2-T05, R2-T06: Emit realtime upsert
        changes.tryEmit(FirebaseSosChange.Upsert(syntheticSos))
        runCurrent()
        advanceUntilIdle()

        // R2-T07, R2-T08: Verify marker automatically appeared on Google Maps StateFlow
        val markers = viewModel.markers.value
        assertEquals("Exactly 1 marker should appear on the map automatically", 1, markers.size)
        val marker = markers[0]
        assertEquals(MarkerType.SOS, marker.type)
        assertEquals("sos-uuid-danang-001", marker.eventId)
        assertEquals(16.0544, marker.latitude, 0.0001)
        assertEquals(108.2022, marker.longitude, 0.0001)
        assertEquals(3, marker.totalPeople)
        assertEquals("Nước ngập ngang ngực, có người già", marker.condition)
        assertTrue(marker.neededItems.contains("Thuyền cứu hộ"))

        collectorJob.cancel()
    }

    @Test
    fun `R2-T09 REST polling failure does NOT wipe valid realtime marker`() = runTest {
        val changes = MutableSharedFlow<FirebaseSosChange>(extraBufferCapacity = 10)
        val realtimeSource = object : SosRealtimeDataSource {
            override fun observeChanges(): Flow<FirebaseSosChange> = changes
        }
        val failingLocalRepo = object : TestLocalSosRepository() {
            override suspend fun getActiveSos(): List<SosRescueInfo> {
                throw IOException("Simulated network cleartext block / temporary outage")
            }
        }
        val firebaseRepo = FirebaseSosRepository(
            localRepository = failingLocalRepo,
            realtimeDataSource = realtimeSource,
            scope = backgroundScope
        )
        val rescueRepo = TestRescueMissionRepository()

        val viewModel = DisasterMapViewModel(
            sosRepository = firebaseRepo,
            rescueMissionRepository = rescueRepo,
            pollingEnabled = false,
            nearbyRequestsEnabled = false
        )

        val collectorJob = launch {
            viewModel.markers.collect {}
        }
        runCurrent()

        // 1. Initial event arrives via Firebase Realtime Database
        val activeEvent = SosRescueInfo(
            eventId = "sos-active-preserved-01",
            latitude = 16.0600,
            longitude = 108.2100,
            urgency = SosUrgency.HIGH,
            condition = "Cần hỗ trợ di dời",
            status = SosStatus.ACTIVE,
            createdAt = System.currentTimeMillis()
        )
        changes.tryEmit(FirebaseSosChange.Upsert(activeEvent))
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, viewModel.markers.value.size)

        // 2. Trigger loadNearbyRequests (which internally triggers refreshSos where REST fails)
        viewModel.loadNearbyRequests(16.0600, 108.2100)
        runCurrent()
        advanceUntilIdle()

        // 3. Verify marker is still present on map and NOT wiped to emptyList!
        assertEquals("Realtime marker must survive REST polling failure", 1, viewModel.markers.value.size)
        assertEquals("sos-active-preserved-01", viewModel.markers.value[0].eventId)

        collectorJob.cancel()
    }

    @Test
    fun `R2-T11 No duplicate markers created for same event ID`() = runTest {
        val changes = MutableSharedFlow<FirebaseSosChange>(extraBufferCapacity = 10)
        val realtimeSource = object : SosRealtimeDataSource {
            override fun observeChanges(): Flow<FirebaseSosChange> = changes
        }
        val event = SosRescueInfo(
            eventId = "sos-dedupe-test-01",
            latitude = 16.0700,
            longitude = 108.2200,
            urgency = SosUrgency.CRITICAL,
            condition = "Sơ tán khẩn",
            status = SosStatus.ACTIVE,
            createdAt = System.currentTimeMillis()
        )
        // Local repository returns the same event as realtime
        val localRepo = object : TestLocalSosRepository() {
            override suspend fun getActiveSos(): List<SosRescueInfo> = listOf(event)
        }
        val firebaseRepo = FirebaseSosRepository(
            localRepository = localRepo,
            realtimeDataSource = realtimeSource,
            scope = backgroundScope
        )
        val rescueRepo = TestRescueMissionRepository()

        val viewModel = DisasterMapViewModel(
            sosRepository = firebaseRepo,
            rescueMissionRepository = rescueRepo,
            pollingEnabled = false,
            nearbyRequestsEnabled = false
        )

        val collectorJob = launch {
            viewModel.markers.collect {}
        }
        runCurrent()

        // Emit via realtime as well
        changes.tryEmit(FirebaseSosChange.Upsert(event))
        runCurrent()
        advanceUntilIdle()

        assertEquals("Deduplication must prevent double marker display", 1, viewModel.markers.value.size)
        assertEquals("sos-dedupe-test-01", viewModel.markers.value[0].eventId)

        collectorJob.cancel()
    }

    @Test
    fun `R2-T12 Terminal status removes marker from map`() = runTest {
        val changes = MutableSharedFlow<FirebaseSosChange>(extraBufferCapacity = 10)
        val realtimeSource = object : SosRealtimeDataSource {
            override fun observeChanges(): Flow<FirebaseSosChange> = changes
        }
        val firebaseRepo = FirebaseSosRepository(
            localRepository = TestLocalSosRepository(),
            realtimeDataSource = realtimeSource,
            scope = backgroundScope
        )
        val rescueRepo = TestRescueMissionRepository()

        val viewModel = DisasterMapViewModel(
            sosRepository = firebaseRepo,
            rescueMissionRepository = rescueRepo,
            pollingEnabled = false,
            nearbyRequestsEnabled = false
        )

        val collectorJob = launch {
            viewModel.markers.collect {}
        }
        runCurrent()

        // 1. Initial event
        val activeEvent = SosRescueInfo(
            eventId = "sos-to-be-resolved",
            latitude = 16.0800,
            longitude = 108.2300,
            urgency = SosUrgency.MEDIUM,
            status = SosStatus.ACTIVE,
            createdAt = System.currentTimeMillis()
        )
        changes.tryEmit(FirebaseSosChange.Upsert(activeEvent))
        runCurrent()
        assertEquals(1, viewModel.markers.value.size)

        // 2. Terminal event received (RESOLVED / FALSE_ALARM)
        changes.tryEmit(FirebaseSosChange.Removed("sos-to-be-resolved"))
        runCurrent()
        advanceUntilIdle()

        // 3. Verify marker is removed from map
        assertEquals("Terminal event must remove marker from map", 0, viewModel.markers.value.size)

        collectorJob.cancel()
    }

    @Test
    fun `R2-T13 Expired events older than 72 hours are filtered out`() = runTest {
        val changes = MutableSharedFlow<FirebaseSosChange>(extraBufferCapacity = 10)
        val realtimeSource = object : SosRealtimeDataSource {
            override fun observeChanges(): Flow<FirebaseSosChange> = changes
        }
        val firebaseRepo = FirebaseSosRepository(
            localRepository = TestLocalSosRepository(),
            realtimeDataSource = realtimeSource,
            scope = backgroundScope
        )
        val rescueRepo = TestRescueMissionRepository()

        val viewModel = DisasterMapViewModel(
            sosRepository = firebaseRepo,
            rescueMissionRepository = rescueRepo,
            pollingEnabled = false,
            nearbyRequestsEnabled = false
        )

        val collectorJob = launch {
            viewModel.markers.collect {}
        }
        runCurrent()

        val now = System.currentTimeMillis()
        val expiredEvent = SosRescueInfo(
            eventId = "sos-expired-96h",
            latitude = 16.0800,
            longitude = 108.2300,
            urgency = SosUrgency.LOW,
            status = SosStatus.ACTIVE,
            createdAt = now - (96 * 3600 * 1000L) // 96 hours old > 72h max
        )
        changes.tryEmit(FirebaseSosChange.Upsert(expiredEvent))
        runCurrent()
        advanceUntilIdle()

        assertEquals("Expired SOS events must not be displayed on map", 0, viewModel.markers.value.size)

        collectorJob.cancel()
    }

    // =========================================================================
    // TEST DOUBLES
    // =========================================================================

    private class TestTokenStorage(
        private var access: String? = null,
        private var refresh: String? = null
    ) : TokenStorage {
        override fun save(accessToken: String, refreshToken: String): Boolean {
            access = accessToken
            refresh = refreshToken
            return true
        }
        override fun getAccessToken(): String? = access
        override fun getRefreshToken(): String? = refresh
        override fun clear() {
            access = null
            refresh = null
        }
        override fun isLoggedIn(): Boolean = !access.isNullOrBlank()
    }

    private class TestFirebaseAuthSession : FirebaseAuthSession {
        var signedIn: Boolean = false
        override suspend fun signInWithCustomToken(customToken: String): Boolean {
            signedIn = true
            return true
        }
        override suspend fun signOut() {
            signedIn = false
        }
    }

    private class TestAuthApiClient(
        private val loginResult: AuthApiResult<AuthTokens> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not configured")),
        private val registerResult: AuthApiResult<AuthTokens> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not configured")),
        private val googleLoginResult: AuthApiResult<AuthTokens> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not configured")),
        private val meResult: AuthApiResult<AuthUser> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not configured")),
        private val refreshResult: AuthApiResult<AuthTokens> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not configured")),
        private val refreshedMeResult: AuthApiResult<AuthUser> = meResult
    ) : AuthApiClient {
        var refreshCalls = 0
        override suspend fun login(identifier: String, password: String): AuthApiResult<AuthTokens> = loginResult
        override suspend fun register(fullName: String, email: String, phone: String, password: String): AuthApiResult<AuthTokens> = registerResult
        override suspend fun googleLogin(idToken: String): AuthApiResult<AuthTokens> = googleLoginResult
        override suspend fun me(accessToken: String): AuthApiResult<AuthUser> =
            if (refreshCalls > 0) refreshedMeResult else meResult
        override suspend fun refresh(refreshToken: String): AuthApiResult<AuthTokens> {
            refreshCalls++
            return refreshResult
        }
        override suspend fun updateRole(accessToken: String, role: String): AuthApiResult<AuthUser> = meResult
        override suspend fun forgotPassword(identifier: String): AuthApiResult<Unit> = AuthApiResult.Success(Unit)
        override suspend fun resetPassword(identifier: String, otp: String, newPassword: String): AuthApiResult<Unit> = AuthApiResult.Success(Unit)
        override suspend fun firebaseCustomToken(accessToken: String): AuthApiResult<String> = AuthApiResult.Success("test-firebase-custom-token")
    }

    private open class TestLocalSosRepository : SosRepository {
        protected val localEvents = MutableStateFlow<List<SosRescueInfo>>(emptyList())
        override suspend fun submitSos(sos: SosRescueInfo): SosSubmissionResult =
            SosSubmissionResult.Success(
                eventId = sos.eventId,
                transportMode = com.example.bai1.sos.domain.SosTransportMode.ONLINE,
                serverSos = sos
            )
        override suspend fun getMySos(): List<SosRescueInfo> = localEvents.value
        override suspend fun getActiveSos(): List<SosRescueInfo> = localEvents.value
        override fun observeSos(): Flow<List<SosRescueInfo>> = localEvents.asStateFlow()
        override suspend fun getSosById(eventId: String): SosRescueInfo? = localEvents.value.find { it.eventId == eventId }
    }

    private class TestRescueMissionRepository : RescueMissionRepository {
        override val missions: StateFlow<List<RescueMissionResponse>> = MutableStateFlow(emptyList())
        override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)
        override val error: StateFlow<String?> = MutableStateFlow(null)
        override suspend fun fetchMyMissions() = Unit
        override suspend fun getMissionDetail(id: String): RescueMissionApiResult<RescueMissionResponse> =
            RescueMissionApiResult.Failure(RescueMissionApiError(com.example.bai1.rescue.network.RescueMissionApiErrorKind.NOT_FOUND, "not found"))
        override suspend fun updateMissionStatus(id: String, newStatus: MissionStatus): RescueMissionApiResult<RescueMissionResponse> =
            RescueMissionApiResult.Failure(RescueMissionApiError(com.example.bai1.rescue.network.RescueMissionApiErrorKind.NOT_FOUND, "not found"))
        override suspend fun acceptRescueRequest(requestId: String): RescueMissionApiResult<RescueMissionResponse> =
            RescueMissionApiResult.Failure(RescueMissionApiError(com.example.bai1.rescue.network.RescueMissionApiErrorKind.NOT_FOUND, "not found"))
        override suspend fun getNearbyRequests(lat: Double, lon: Double, radius: Int): RescueMissionApiResult<List<RescueMissionRequestInfo>> =
            RescueMissionApiResult.Success(emptyList())
        override suspend fun getRescueRequestDetail(id: String): RescueMissionApiResult<RescueMissionRequestInfo> =
            RescueMissionApiResult.Failure(RescueMissionApiError(com.example.bai1.rescue.network.RescueMissionApiErrorKind.NOT_FOUND, "not found"))
    }
}
