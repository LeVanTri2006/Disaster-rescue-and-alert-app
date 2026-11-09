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

        // Fast-path: Trả về ngay nếu đã có vị trí trong cache hoặc từ hệ thống để mở bản đồ và tìm đường tức thì
        // Tuy nhiên, nếu tọa độ đó nằm ngoài Việt Nam (ví dụ tọa độ mặc định emulator ở California),
        // thì KHÔNG fast-path mà để logic emulator fallback xử lý
        val fastCandidate = cachedLocation ?: lastKnown
        if (fastCandidate != null && !isEmulator() && isInsideVietnam(fastCandidate.latitude, fastCandidate.longitude)) {
            com.example.bai1.presentation.map.DisasterMapSharedState.lastKnownRescuerLocation =
                com.google.android.gms.maps.model.LatLng(fastCandidate.latitude, fastCandidate.longitude)
            onResult(fastCandidate)
            return
        }
        // Nếu fast candidate tồn tại nhưng ngoài Việt Nam trên emulator → redirect về Đà Nẵng
        if (fastCandidate != null && !isInsideVietnam(fastCandidate.latitude, fastCandidate.longitude)) {
            resolveRealLocationIfEmulator(fastCandidate, onResult)
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        var completed = false

        fun finish(result: LocationResult) {
            if (completed) return
            completed = true
            mainHandler.removeCallbacksAndMessages(cancellationTokenSource)
            cancellationTokenSource.cancel()
            if (result is LocationResult.Success) {
                cachedLocation = result
                com.example.bai1.presentation.map.DisasterMapSharedState.lastKnownRescuerLocation =
                    com.google.android.gms.maps.model.LatLng(result.latitude, result.longitude)
            }
            if (result is LocationResult.Success && !isInsideVietnam(result.latitude, result.longitude)) {
                resolveRealLocationIfEmulator(result, onResult)
            } else if (result is LocationResult.Error && result.reason != LocationError.PERMISSION_DENIED && isEmulator()) {
                resolveRealLocationIfEmulator(null, onResult)
            } else {
                onResult(result)
            }
        }

        // Thử lấy nhanh vị trí cuối cùng được Google Play Services ghi nhận
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (!completed && loc != null) {
                        val res = loc.toResult()
                        if (res is LocationResult.Success) {
                            finish(res)
                        }
                    }
                }
        } catch (_: SecurityException) {}

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

    override fun requestLocationUpdates(
        intervalMs: Long,
        onLocationUpdate: (LocationResult) -> Unit
    ): () -> Unit {
        if (!hasLocationPermission()) {
            onLocationUpdate(LocationResult.Error(LocationError.PERMISSION_DENIED))
            return {}
        }
        val request = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs
        ).setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs)
            .build()

        val callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                val loc = locationResult.lastLocation ?: return
                val res = loc.toResult()
                if (res is LocationResult.Success) {
                    // Nếu tọa độ nằm ngoài Việt Nam (ví dụ emulator mặc định trả về Mountain View)
                    // → bỏ qua, không cập nhật cache cũng không thông báo location update sai
                    if (!isInsideVietnam(res.latitude, res.longitude)) {
                        return
                    }
                    cachedLocation = res
                    com.example.bai1.presentation.map.DisasterMapSharedState.lastKnownRescuerLocation =
                        com.google.android.gms.maps.model.LatLng(res.latitude, res.longitude)
                }
                onLocationUpdate(res)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            onLocationUpdate(LocationResult.Error(LocationError.PERMISSION_DENIED))
            return {}
        }

        return {
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (_: Exception) {}
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
                timestamp = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                bearing = if (hasBearing()) bearing else 0f,
                speedMps = if (hasSpeed()) speed else 0f
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

    /**
     * Kiểm tra tọa độ có nằm trong phạm vi lãnh thổ Việt Nam không.
     * Bounding box xấp xỉ: lat 8.18 – 23.39, lng 102.14 – 109.46
     * (Mở rộng nhẹ để bao gồm vùng biển gần bờ và đảo)
     */
    private fun isInsideVietnam(lat: Double, lng: Double): Boolean {
        return lat in 7.5..24.0 && lng in 101.0..115.0
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

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
        @Volatile
        var cachedLocation: LocationResult.Success? = null
    }
}
