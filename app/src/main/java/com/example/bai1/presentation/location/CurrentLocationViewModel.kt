package com.example.bai1.presentation.location

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bai1.sos.presentation.LocationUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the current-location state used by the emergency landing screen. */
class CurrentLocationViewModel(
    private val locationProvider: LocationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<LocationUiState>(LocationUiState.Loading)
    val state: StateFlow<LocationUiState> = _state.asStateFlow()
    private var requestVersion = 0

    fun refresh() {
        val version = ++requestVersion
        _state.value = LocationUiState.Loading
        locationProvider.getCurrentLocation { result ->
            if (version != requestVersion) return@getCurrentLocation
            _state.value = when (result) {
                is LocationResult.Success -> LocationUiState.Success(
                    latitude = result.latitude,
                    longitude = result.longitude,
                    accuracyMeters = result.accuracyMeters,
                    timestamp = result.timestamp,
                )
                is LocationResult.Error -> LocationUiState.Error(result.reason.toUserMessage())
            }
        }
    }

    fun permissionDenied() {
        ++requestVersion
        _state.value = LocationUiState.Error(LocationError.PERMISSION_DENIED.toUserMessage())
    }

    private fun LocationError.toUserMessage(): String = when (this) {
        LocationError.PERMISSION_DENIED -> "Chưa được cấp quyền vị trí. Hãy cấp quyền để xác định vị trí."
        LocationError.SERVICES_DISABLED -> "Dịch vụ vị trí đang tắt. Hãy bật GPS rồi thử lại."
        LocationError.UNAVAILABLE -> "Không xác định được vị trí hiện tại."
        LocationError.TIMEOUT -> "Lấy vị trí quá lâu. Hãy thử lại."
        LocationError.INVALID -> "Vị trí nhận được không hợp lệ. Hãy thử lại."
        LocationError.UNKNOWN -> "Có lỗi khi lấy vị trí. Hãy thử lại."
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CurrentLocationViewModel(AndroidLocationProvider(context.applicationContext)) as T
        }
    }
}
