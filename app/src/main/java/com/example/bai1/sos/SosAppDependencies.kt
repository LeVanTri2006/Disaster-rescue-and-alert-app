package com.example.bai1.sos

import android.content.Context
import com.example.bai1.BuildConfig
import com.example.bai1.sos.network.OkHttpSosApiClient
import com.example.bai1.sos.network.SosApiClient
import com.example.bai1.sos.network.SharedPreferencesAccessTokenProvider
import com.example.bai1.sos.provider.AndroidNetworkAvailabilityProvider
import com.example.bai1.sos.repository.InMemorySosRepository
import com.example.bai1.sos.repository.FirebaseSosRepository
import com.example.bai1.sos.realtime.FirebaseSosRealtimeDataSource
import com.google.firebase.database.FirebaseDatabase
import com.example.bai1.sos.repository.SosRepository
import com.example.bai1.sos.transport.ApiSosTransport
import com.example.bai1.sos.transport.FakeMeshSosTransport
import com.example.bai1.sos.transport.SosTransportManager

/**
 * Application-scoped SOS dependency graph for Phase 7A.
 *
 * Provides a single shared [SosRepository] instance so that:
 *   - [SosRescueRequestViewModel] can submit SOS events.
 *   - [DisasterMapScreen] can observe SOS events reactively.
 *
 * Phase 7C: This will be replaced by proper dependency injection (Hilt/Koin).
 *
 * IMPORTANT: This is NOT a singleton pattern for production use.
 *            It is a temporary DI container for Phase 7A architecture.
 */
object SosAppDependencies {
    lateinit var sosRepository: SosRepository
        private set

    lateinit var apiClient: SosApiClient
        private set

    fun initialize(context: Context) {
        if (::sosRepository.isInitialized) return

        val tokenProvider = SharedPreferencesAccessTokenProvider(context)
        apiClient = OkHttpSosApiClient(BuildConfig.SOS_API_BASE_URL, tokenProvider)
        val transportManager = SosTransportManager(
            network = AndroidNetworkAvailabilityProvider(context),
            internet = ApiSosTransport(apiClient),
            // Mesh remains intentionally local/fake until a later phase.
            mesh = FakeMeshSosTransport()
        )
        sosRepository = FirebaseSosRepository(
            localRepository = InMemorySosRepository(transportManager, apiClient),
            realtimeDataSource = FirebaseSosRealtimeDataSource(databaseProvider = { FirebaseDatabase.getInstance("https://canhbao-34075-default-rtdb.asia-southeast1.firebasedatabase.app") }),
        )
    }
}
