package com.example.bai1.presentation.location

sealed interface LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val timestamp: Long,
        val bearing: Float = 0f,
        val speedMps: Float = 0f
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

    /**
     * Lắng nghe cập nhật vị trí GPS liên tục thời gian thực (real-time updates).
     * @return hàm lambda để hủy đăng ký lắng nghe khi dừng di chuyển
     */
    fun requestLocationUpdates(
        intervalMs: Long = 2000L,
        onLocationUpdate: (LocationResult) -> Unit
    ): () -> Unit = { }
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
