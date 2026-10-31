package com.example.bai1.auth

import android.content.Context
import com.example.bai1.BuildConfig
import com.example.bai1.auth.network.OkHttpAuthApiClient
import com.example.bai1.auth.session.SharedPreferencesTokenStorage
import com.example.bai1.auth.firebase.AndroidFirebaseAuthSession

object AuthAppDependencies {
    lateinit var repository: AuthRepository
        private set

    fun initialize(context: Context) {
        if (::repository.isInitialized) return

        val tokenStorage = SharedPreferencesTokenStorage(context)
        repository = AuthRepository(
            apiClient = OkHttpAuthApiClient(BuildConfig.SOS_API_BASE_URL),
            tokenStorage = tokenStorage,
            firebaseAuthSession = AndroidFirebaseAuthSession()
        )
    }
}
