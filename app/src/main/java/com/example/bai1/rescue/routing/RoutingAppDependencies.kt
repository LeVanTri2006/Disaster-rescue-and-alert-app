package com.example.bai1.rescue.routing

import android.content.Context
import com.example.bai1.BuildConfig
import com.example.bai1.rescue.routing.download.GraphDownloader
import com.example.bai1.rescue.routing.download.OkHttpGraphDownloader
import com.example.bai1.rescue.routing.loader.DefaultRoutingGraphLoader
import com.example.bai1.rescue.routing.loader.RoutingGraphLoader
import com.example.bai1.rescue.routing.network.OkHttpRoutingApiClient
import com.example.bai1.rescue.routing.network.RoutingApiClient
import com.example.bai1.rescue.routing.parser.RoadGraphParser
import com.example.bai1.rescue.routing.parser.StreamingRoadGraphParser
import com.example.bai1.rescue.routing.repository.DefaultRoutingRepository
import com.example.bai1.rescue.routing.repository.RoutingRepository
import com.example.bai1.sos.network.SharedPreferencesAccessTokenProvider
import java.io.File

object RoutingAppDependencies {
    lateinit var routingApiClient: RoutingApiClient
        private set

    lateinit var graphDownloader: GraphDownloader
        private set

    lateinit var routingRepository: DefaultRoutingRepository
        private set

    lateinit var roadGraphParser: RoadGraphParser
        private set

    lateinit var routingGraphLoader: RoutingGraphLoader
        private set

    fun invalidateRoutingSession() {
        if (::routingRepository.isInitialized) routingRepository.invalidateSession()
        if (::routingGraphLoader.isInitialized) routingGraphLoader.clearLoadedGraph()
    }

    internal fun invalidateRoutingSession(repository: DefaultRoutingRepository, loader: RoutingGraphLoader) {
        repository.invalidateSession()
        loader.clearLoadedGraph()
    }

    fun initialize(context: Context) {
        if (::routingGraphLoader.isInitialized) return

        val tokenProvider = SharedPreferencesAccessTokenProvider(context)
        routingApiClient = OkHttpRoutingApiClient(
            baseUrl = BuildConfig.SOS_API_BASE_URL,
            tokenProvider = tokenProvider
        )

        val storageDir = File(context.applicationContext.cacheDir, "routing_graphs")
        graphDownloader = OkHttpGraphDownloader(storageDir = storageDir)

        routingRepository = DefaultRoutingRepository(
            apiClient = routingApiClient,
            graphDownloader = graphDownloader
        )

        roadGraphParser = StreamingRoadGraphParser()

        routingGraphLoader = DefaultRoutingGraphLoader(
            routingRepository = routingRepository,
            graphDownloader = graphDownloader,
            roadGraphParser = roadGraphParser
        )
    }
}
