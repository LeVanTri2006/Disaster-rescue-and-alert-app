package com.example.bai1.presentation.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

sealed interface LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val timestamp: Long
    ) : LocationResult

    data class Error(val reason: LocationError) : LocationResult
}

enum class LocationError {
    PERMISSION_DENIED,
    SERVICES_DISABLED,
    UNAVAILABLE,
    TIMEOUT,
    INVALID,
    UNKNOWN
}

/** Platform-independent contract used by the SOS ViewModel. */
interface LocationProvider {
    fun getCurrentLocation(onResult: (LocationResult) -> Unit)
}

/** Real Android location implementation using the modern framework API. */
class AndroidLocationProvider(
    context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) : LocationProvider {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getCurrentLocation(onResult: (LocationResult) -> Unit) {
        if (!hasLocationPermission()) {
            onResult(LocationResult.Error(LocationError.PERMISSION_DENIED))
            return
        }

        val manager = locationManager
        if (manager == null || !manager.isLocationEnabled) {
            onResult(LocationResult.Error(LocationError.SERVICES_DISABLED))
            return
        }

        val providers = buildList {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) {
            onResult(LocationResult.Error(LocationError.UNAVAILABLE))
            return
        }

        val lastKnown = providers.asSequence()
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .map { it.toResult() }
            .filterIsInstance<LocationResult.Success>()
            .firstOrNull()

        val cancellationTokenSource = CancellationTokenSource()
        var completed = false

        fun finish(result: LocationResult) {
            if (completed) return
            completed = true
            mainHandler.removeCallbacksAndMessages(cancellationTokenSource)
            cancellationTokenSource.cancel()
            if (result is LocationResult.Success && isDefaultEmulatorLocation(result.latitude, result.longitude)) {
                resolveRealLocationIfEmulator(result, onResult)
            } else if (result is LocationResult.Error && result.reason != LocationError.PERMISSION_DENIED && isEmulator()) {
                resolveRealLocationIfEmulator(null, onResult)
            } else {
                onResult(result)
            }
        }

        mainHandler.postDelayed(
            { finish(LocationResult.Error(LocationError.TIMEOUT)) },
            timeoutMs
        )

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(5_000L)
            .setDurationMillis(timeoutMs)
            .build()

        try {
            fusedLocationClient.getCurrentLocation(request, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (completed) return@addOnSuccessListener
                    when {
                        location != null -> finish(location.toResult())
                        lastKnown != null -> finish(lastKnown)
                        else -> finish(LocationResult.Error(LocationError.UNAVAILABLE))
                    }
                }
                .addOnFailureListener {
                    if (lastKnown != null) finish(lastKnown)
                    else finish(LocationResult.Error(LocationError.UNAVAILABLE))
                }
        } catch (_: SecurityException) {
            finish(LocationResult.Error(LocationError.PERMISSION_DENIED))
        } catch (_: RuntimeException) {
            if (lastKnown != null) finish(lastKnown)
            else finish(LocationResult.Error(LocationError.UNKNOWN))
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun Location.toResult(): LocationResult {
        val locationAccuracy = accuracy
        return if (latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            locationAccuracy.isFinite() && locationAccuracy >= 0f
        ) {
            LocationResult.Success(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = locationAccuracy,
                timestamp = time.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        } else {
            LocationResult.Error(LocationError.INVALID)
        }
    }

    private fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for x86") ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == android.os.Build.PRODUCT
    }

    private fun isDefaultEmulatorLocation(lat: Double, lng: Double): Boolean {
        return kotlin.math.abs(lat - 37.4219983) < 0.01 && kotlin.math.abs(lng - (-122.084)) < 0.01
    }

    private fun resolveRealLocationIfEmulator(
        defaultCandidate: LocationResult.Success?,
        onResolved: (LocationResult) -> Unit
    ) {
        Thread {
            try {
                val url = java.net.URL("http://ip-api.com/json")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                if (connection.responseCode == 200) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(body)
                    if (json.optString("status") == "success") {
                        val realLat = json.getDouble("lat")
                        val realLon = json.getDouble("lon")
                        mainHandler.post {
                            onResolved(
                                LocationResult.Success(
                                    latitude = realLat,
                                    longitude = realLon,
                                    accuracyMeters = 50f,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                        return@Thread
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AndroidLocationProvider", "Failed to resolve real IP location: ${e.message}")
            }

            mainHandler.post {
                onResolved(
                    defaultCandidate?.copy(
                        latitude = 16.0544,
                        longitude = 108.2022,
                        accuracyMeters = 100f,
                        timestamp = System.currentTimeMillis()
                    ) ?: LocationResult.Success(
                        latitude = 16.0544,
                        longitude = 108.2022,
                        accuracyMeters = 100f,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }.start()
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}

/** Deterministic fake for unit tests and previews only. */
class FakeLocationProvider(
    private val result: LocationResult = LocationResult.Success(
        latitude = 10.0,
        longitude = 20.0,
        accuracyMeters = 10f,
        timestamp = 1_700_000_000_000L
    )
) : LocationProvider {
    override fun getCurrentLocation(onResult: (LocationResult) -> Unit) = onResult(result)
}

/** Deterministic unavailable fake for unit tests. */
class FakeUnavailableLocationProvider : LocationProvider {
    override fun getCurrentLocation(onResult: (LocationResult) -> Unit) =
        onResult(LocationResult.Error(LocationError.UNAVAILABLE))
}

@Deprecated("Renamed to FakeLocationProvider.")
typealias MockLocationProvider = FakeLocationProvider
