package com.example.bai1.sos.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Abstraction for checking internet availability.
 *
 * The ViewModel depends ONLY on this interface — never on Android's
 * ConnectivityManager directly. This allows:
 *   - Unit testing without Android framework
 *   - Easy swap to a real ConnectivityManager-backed implementation in Phase 7C
 *
 * Phase 7A: Only [FakeNetworkAvailabilityProvider] is provided.
 * Phase 7C: AndroidNetworkAvailabilityProvider will be wired via DI.
 */
interface NetworkAvailabilityProvider {
    /**
     * Returns true when an active internet connection is believed to be available.
     *
     * This is a best-effort, synchronous check. For real implementations,
     * consider making this a Flow to react to connectivity changes.
     */
    fun isInternetAvailable(): Boolean
}

/**
 * Fake implementation for architecture testing and UI development.
 *
 * @param online simulated connectivity state. Defaults to true (online).
 */
class FakeNetworkAvailabilityProvider(
    private val online: Boolean = true
) : NetworkAvailabilityProvider {
    override fun isInternetAvailable(): Boolean = online
}

class AndroidNetworkAvailabilityProvider(context: Context) : NetworkAvailabilityProvider {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    override fun isInternetAvailable(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
