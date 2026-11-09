package com.example.bai1.sos.network

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.domain.SosUrgency
import java.time.Instant

data class CreateSosRequest(
    val adults: Int,
    val children: Int,
    val elderly: Int,
    val neededItems: Set<String>,
    val condition: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val batteryPercent: Int?,
    val urgency: SosUrgency,
    val timestamp: Long,
    val photoUrl: String? = null,
    val photos: List<String>? = null
)

data class SosEventResponse(
    val eventId: String,
    val adults: Int,
    val children: Int,
    val elderly: Int,
    val neededItems: Set<String>,
    val condition: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val batteryPercent: Int?,
    val createdAt: Long,
    val status: String,
    val urgency: SosUrgency,
    val transportMode: SosTransportMode,
    val photoUrl: String? = null,
    val photos: List<String>? = null
) {
    fun toDomain(): SosRescueInfo = SosRescueInfo(
        eventId = eventId,
        adults = adults,
        children = children,
        elderly = elderly,
        neededItems = neededItems,
        condition = condition,
        description = description.orEmpty(),
        latitude = latitude,
        longitude = longitude,
        locationAccuracy = accuracyMeters,
        createdAt = createdAt,
        batteryPercent = batteryPercent ?: -1,
        urgency = urgency,
        status = runCatching { SosStatus.valueOf(status) }.getOrDefault(SosStatus.PENDING),
        transportMode = transportMode,
        photoUri = photoUrl,
        photos = photos
    )
}

enum class SosApiErrorKind {
    NO_TOKEN,
    NO_INTERNET,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    SERVER,
    TIMEOUT,
    UNKNOWN
}

data class SosApiError(
    val kind: SosApiErrorKind,
    val technicalReason: String
)

sealed interface SosApiResult {
    data class Success(val event: SosEventResponse) : SosApiResult
    data class Failure(val error: SosApiError) : SosApiResult
}

sealed interface SosApiResultList {
    data class Success(val events: List<SosEventResponse>) : SosApiResultList
    data class Failure(val error: SosApiError) : SosApiResultList
}

fun CreateSosRequest.timestampIso(): String = Instant.ofEpochMilli(timestamp).toString()

// ── R2 Presigned Upload URL ──────────────────────────────────────────────────

data class GetPhotoUploadUrlRequest(
    val contentType: String
)

data class GetPhotoUploadUrlResponse(
    val uploadUrl: String,
    val objectKey: String,
    val expiresIn: Int,
    val contentType: String
)

sealed interface SosPhotoUploadUrlResult {
    data class Success(val response: GetPhotoUploadUrlResponse) : SosPhotoUploadUrlResult
    data class Failure(val error: SosApiError) : SosPhotoUploadUrlResult
}
