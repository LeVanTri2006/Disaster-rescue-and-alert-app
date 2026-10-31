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
            onResult(result)
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
