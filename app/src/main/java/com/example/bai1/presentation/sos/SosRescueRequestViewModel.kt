package com.example.bai1.presentation.sos

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bai1.presentation.location.AndroidLocationProvider
import com.example.bai1.presentation.location.LocationError
import com.example.bai1.presentation.location.LocationProvider
import com.example.bai1.presentation.location.LocationResult
import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosUrgency
import com.example.bai1.sos.presentation.SosFormData
import com.example.bai1.sos.presentation.LocationUiState
import com.example.bai1.sos.presentation.SosUiState
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.sos.provider.AndroidBatteryInfoProvider
import com.example.bai1.sos.provider.BatteryInfoProvider
import com.example.bai1.sos.repository.SosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the SOS rescue request form.
 *
 * Dependencies are injected via constructor — the ViewModel knows nothing about:
 *   - Firebase
 *   - HTTP client
 *   - Nearby Connections
 *   - Activity
 *   - Android ConnectivityManager
 *   - Android BatteryManager
 *
 * All networking concerns are delegated to [SosRepository] → [SosTransportManager].
 *
 * @param repository     handles SOS submission and persistence
 * @param locationProvider provides GPS coordinates through an abstraction
 * @param batteryProvider  provides battery percentage through an abstraction
 */
class SosRescueRequestViewModel(
    private val repository: SosRepository,
    private val locationProvider: LocationProvider,
    private val batteryProvider: BatteryInfoProvider,
    private val photoUploader: com.example.bai1.sos.repository.R2SosPhotoUploader
) : ViewModel() {

    // ── Form data ─────────────────────────────────────────────────────────────
    private val _formData = MutableStateFlow(SosFormData())
    val formData: StateFlow<SosFormData> = _formData.asStateFlow()

    // ── UI state machine ──────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow<SosUiState>(SosUiState.Idle)
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SosUiState.Editing(location = LocationUiState.Loading)
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private fun fetchLocation() {
        setLocationLoading()
        locationProvider.getCurrentLocation { result ->
            when (result) {
                is LocationResult.Success -> {
                    if (!isValidLocation(result.latitude, result.longitude) ||
                        !result.accuracyMeters.isFinite() || result.accuracyMeters < 0f || result.timestamp <= 0L
                    ) {
                        _formData.update {
                            it.copy(latitude = null, longitude = null, locationAccuracy = null, locationTimestamp = null)
                        }
                        updateLocationUi(LocationUiState.Error(LocationError.INVALID.toUserMessage()))
                    } else {
                        _formData.update {
                            it.copy(
                                latitude = result.latitude,
                                longitude = result.longitude,
                                locationAccuracy = result.accuracyMeters,
                                locationTimestamp = result.timestamp
                            )
                        }
                        updateLocationUi(
                            LocationUiState.Success(
                                result.latitude,
                                result.longitude,
                                result.accuracyMeters,
                                result.timestamp
                            )
                        )
                    }
                }
                is LocationResult.Error -> {
                    _formData.update {
                        it.copy(latitude = null, longitude = null, locationAccuracy = null, locationTimestamp = null)
                    }
                    updateLocationUi(LocationUiState.Error(result.reason.toUserMessage()))
                }
            }
        }
    }

    fun refreshLocation() = fetchLocation()

    fun setLocationPermissionDenied() {
        _formData.update { it.copy(latitude = null, longitude = null, locationAccuracy = null, locationTimestamp = null) }
        updateLocationUi(LocationUiState.Error(LocationError.PERMISSION_DENIED.toUserMessage()))
    }

    private fun setLocationLoading() {
        val current = _uiState.value
        _uiState.value = if (current is SosUiState.Editing) {
            current.copy(location = LocationUiState.Loading)
        } else {
            SosUiState.Editing(location = LocationUiState.Loading)
        }
    }

    private fun updateLocationUi(location: LocationUiState) {
        val current = _uiState.value
        if (current is SosUiState.Editing) _uiState.value = current.copy(location = location)
    }

    private fun LocationError.toUserMessage(): String = when (this) {
        LocationError.PERMISSION_DENIED -> "Chưa được cấp quyền vị trí. Hãy cấp quyền để gửi SOS."
        LocationError.SERVICES_DISABLED -> "Dịch vụ vị trí đang tắt. Hãy bật GPS rồi thử lại."
        LocationError.UNAVAILABLE -> "Không xác định được vị trí hiện tại."
        LocationError.TIMEOUT -> "Lấy vị trí quá lâu. Hãy thử lại."
        LocationError.INVALID -> "Vị trí nhận được không hợp lệ. Hãy thử lại."
        LocationError.UNKNOWN -> "Có lỗi khi lấy vị trí. Hãy thử lại."
    }

    // ── Form update handlers ──────────────────────────────────────────────────

    fun updateAdultsCount(count: Int) {
        _formData.update { it.copy(adults = maxOf(0, count)) }
        clearValidationError()
    }

    fun updateChildrenCount(count: Int) {
        _formData.update { it.copy(children = maxOf(0, count)) }
        clearValidationError()
    }

    fun updateEldersCount(count: Int) {
        _formData.update { it.copy(elderly = maxOf(0, count)) }
        clearValidationError()
    }

    fun toggleNeededItem(item: String) {
        _formData.update { form ->
            val current = form.neededItems.toMutableSet()
            if (current.contains(item)) current.remove(item) else current.add(item)
            form.copy(neededItems = current)
        }
    }

    private val _selectedConditions = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val selectedConditions: kotlinx.coroutines.flow.StateFlow<Set<String>> = _selectedConditions.asStateFlow()

    fun toggleCondition(condition: String) {
        _selectedConditions.update { current ->
            if (current.contains(condition)) current - condition else current + condition
        }
        clearValidationError()
    }

    fun updateDescription(description: String) {
        _formData.update { it.copy(description = description) }
    }

    fun addPhotos(uris: List<Uri>) {
        _formData.update { form ->
            val combined = (form.photoUris + uris).distinct().take(3)
            form.copy(photoUris = combined)
        }
    }

    fun addPhoto(uri: Uri) {
        addPhotos(listOf(uri))
    }

    fun removePhoto(uri: Uri) {
        _formData.update { form ->
            form.copy(photoUris = form.photoUris - uri)
        }
    }
    
    // Replaced setPhotos with addPhotos

    fun updateLocation(lat: Double?, lng: Double?, accuracy: Float? = null) {
        val valid = lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            (accuracy == null || (accuracy.isFinite() && accuracy >= 0f))
        _formData.update {
            it.copy(
                latitude = if (valid) lat else null,
                longitude = if (valid) lng else null,
                locationAccuracy = if (valid) accuracy else null,
                locationTimestamp = if (valid) System.currentTimeMillis() else null
            )
        }
        updateLocationUi(
            if (valid) LocationUiState.Success(lat!!, lng!!, accuracy ?: 0f, System.currentTimeMillis())
            else LocationUiState.Error("Vị trí không hợp lệ. Hãy thử lại.")
        )
    }

    private fun clearValidationError() {
        val current = _uiState.value
        if (current is SosUiState.Editing && current.validationError != null) {
            _uiState.value = current.copy(validationError = null)
        }
    }

    // ── Submission ────────────────────────────────────────────────────────────

    private fun setValidationError(message: String) {
        val location = (_uiState.value as? SosUiState.Editing)?.location ?: LocationUiState.Loading
        _uiState.value = SosUiState.Editing(validationError = message, location = location)
    }

    /**
     * Validates the form and submits the SOS via the repository.
     *
     * Double-tap prevention: if state is already [SosUiState.Submitting], this call is ignored.
     */
    fun submitRequest() {
        // Double-tap / duplicate submission guard
        if (_uiState.value is SosUiState.Submitting) return

        val form = _formData.value
        val conditionString = _selectedConditions.value.joinToString(" - ")

        // Validation
        if (form.totalPeople <= 0) {
            setValidationError("Số người phải lớn hơn 0")
            return
        }
        if (conditionString.isBlank()) {
            setValidationError("Vui lòng chọn tình trạng hiện tại")
            return
        }
        if (!isValidLocation(form.latitude, form.longitude)) {
            setValidationError("Không lấy được vị trí hiện tại")
            return
        }
        if (form.photoUris.size > 3) {
            setValidationError("Vui lòng chỉ chọn tối đa 3 ảnh.")
            return
        }

        // Begin submission
        _uiState.value = SosUiState.Submitting

        viewModelScope.launch {
            val eventId = java.util.UUID.randomUUID().toString()
            var finalPhotoUrl: String? = null
            var finalPhotos: List<String>? = null
            
            if (form.photoUris.isNotEmpty()) {
                android.util.Log.d("SosRescueRequestViewModel", "Uploading ${form.photoUris.size} photos")
                val uploadResult = photoUploader.uploadSosPhotos(form.photoUris, eventId)
                if (uploadResult.isSuccess) {
                    val objectKeys = uploadResult.getOrThrow()
                    if (objectKeys.isNotEmpty()) {
                        finalPhotoUrl = objectKeys.first()
                        finalPhotos = objectKeys
                    }
                    android.util.Log.d("SosRescueRequestViewModel", "Upload success! Keys: $finalPhotos")
                } else {
                    android.util.Log.e("SosRescueRequestViewModel", "Upload failed!", uploadResult.exceptionOrNull())
                    setValidationError("Lỗi tải ảnh. Vui lòng thử lại.")
                    return@launch
                }
            }

            val sos = SosRescueInfo(
                eventId = eventId,
                adults = form.adults,
                children = form.children,
                elderly = form.elderly,
                neededItems = form.neededItems,
                condition = conditionString,
                description = form.description,
                photoUri = finalPhotoUrl,
                photos = finalPhotos,
                latitude = form.latitude,
                longitude = form.longitude,
                locationAccuracy = form.locationAccuracy,
                locationTimestamp = form.locationTimestamp,
                createdAt = System.currentTimeMillis(),
                batteryPercent = batteryProvider.getBatteryPercent(),
                urgency = calculateUrgency(form.copy(condition = conditionString))
            )

            val result = repository.submitSos(sos)

            _uiState.value = when (result) {
                is SosSubmissionResult.Success -> SosUiState.Submitted(result)
                is SosSubmissionResult.Queued  -> SosUiState.Queued(result)
                is SosSubmissionResult.Failed  -> SosUiState.Error(result.message)
            }
        }
    }

    private fun calculateUrgency(form: SosFormData): SosUrgency {
        val conditions = form.condition.split("-").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val neededItems = form.neededItems.toSet()

        val hasVulnerable = form.children > 0 || form.elderly > 0
        val needsBoat = neededItems.contains("Thuyền") || neededItems.contains("Áo phao")

        // 1. CRITICAL
        val criticalConditions = setOf("Nguy hiểm tính mạng", "Bất tỉnh", "Khó thở", "Chảy máu nhiều", "Bị thương nặng")
        if (conditions.any { it in criticalConditions }) {
            return SosUrgency.CRITICAL
        }
        if (conditions.contains("Kẹt trong nhà") && hasVulnerable) {
            return SosUrgency.CRITICAL
        }
        if (conditions.contains("Kẹt trong nhà") && needsBoat) {
            return SosUrgency.CRITICAL
        }

        // 2. HIGH
        if (conditions.contains("Mất liên lạc")) {
            return SosUrgency.HIGH
        }
        if (conditions.contains("Kẹt trong nhà")) {
            return SosUrgency.HIGH
        }
        if (conditions.contains("Bị thương nhẹ") && hasVulnerable) {
            return SosUrgency.HIGH
        }
        if (conditions.contains("Thiếu thức ăn/nước") && hasVulnerable) {
            return SosUrgency.HIGH
        }
        if (neededItems.contains("Thuốc men") || needsBoat) {
            return SosUrgency.HIGH
        }

        // 3. MEDIUM
        if (conditions.contains("Bị thương nhẹ") && !hasVulnerable) {
            return SosUrgency.MEDIUM
        }
        if (conditions.contains("Thiếu thức ăn/nước") && !hasVulnerable) {
            return SosUrgency.MEDIUM
        }
        if (neededItems.contains("Thực phẩm") || neededItems.contains("Nước uống")) {
            return SosUrgency.MEDIUM
        }

        // 4. LOW
        return SosUrgency.LOW
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Default factory wires all fake implementations for Phase 7A.
     *
     * Phase 7C: Replace [FakeInternetSosTransport] with a real REST transport.
     * Phase 7E: Replace [FakeMeshSosTransport] with NearbyConnections transport.
     */
    companion object {
        private fun isValidLocation(latitude: Double?, longitude: Double?): Boolean =
            latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                SosAppDependencies.initialize(context.applicationContext)
                return SosRescueRequestViewModel(
                    repository = SosAppDependencies.sosRepository,
                    locationProvider = AndroidLocationProvider(context.applicationContext),
                    batteryProvider = AndroidBatteryInfoProvider(context.applicationContext),
                    photoUploader = com.example.bai1.sos.repository.R2SosPhotoUploader(context.applicationContext, SosAppDependencies.apiClient)
                ) as T
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BACKWARD COMPATIBILITY BRIDGE — TEMPORARY (Phase 7A only)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Kept alive ONLY as a compatibility bridge so [DisasterMapScreen] can render
 * the SOS marker while the nav-graph is being wired to the repository flow.
 *
 * @deprecated TEMPORARY. Will be removed in Phase 7B when the map is wired
 *             to [SosRepository.observeSos()]. Do NOT add new references to this.
 */
@Deprecated(
    message = "TEMPORARY bridge. Use SosRepository.observeSos() instead. Will be removed in Phase 7B.",
    level = DeprecationLevel.WARNING
)
object MockSosSharedState {
    @Volatile
    var lastSubmittedSos: SosRescueInfo? = null
}

// Legacy UI-model kept for backward compatibility with the form screen rendering.
// New code should use SosRescueInfo from sos.domain instead.
/**
 * @deprecated Use [SosRescueInfo] from [com.example.bai1.sos.domain] instead.
 */
@Deprecated("Use com.example.bai1.sos.domain.SosRescueInfo instead.")
typealias SosRescueInfo = com.example.bai1.sos.domain.SosRescueInfo
