package com.example.bai1.rescue

import android.content.Context
import com.example.bai1.BuildConfig
import com.example.bai1.sos.network.SharedPreferencesAccessTokenProvider
import com.example.bai1.rescue.network.OkHttpRescueMissionApiClient
import com.example.bai1.rescue.repository.DefaultRescueMissionRepository
import com.example.bai1.rescue.repository.RescueMissionRepository

object RescueMissionAppDependencies {
    lateinit var rescueMissionRepository: RescueMissionRepository
        private set

    fun initialize(context: Context) {
        if (::rescueMissionRepository.isInitialized) return

        val tokenProvider = SharedPreferencesAccessTokenProvider(context)
        val apiClient = OkHttpRescueMissionApiClient(
            baseUrl = BuildConfig.SOS_API_BASE_URL,
            tokenProvider = tokenProvider
        )
        rescueMissionRepository = DefaultRescueMissionRepository(apiClient)
    }
}
