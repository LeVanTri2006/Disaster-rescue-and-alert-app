package com.example.bai1.rescue.network

enum class MissionStatus {
    ASSIGNED,
    IN_ROUTE,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class RescueMissionResponse(
    val id: String,
    val requestId: String,
    val rescuerId: String?,
    val rescuerName: String? = null,
    val rescuerPhone: String? = null,
    val teamId: String?,
    val status: String,
    val notes: String?,
    val acceptedAt: String?,
    val startedAt: String?,
    val completedAt: String?,
    val request: RescueMissionRequestInfo?
)

data class RescueMissionRequestInfo(
    val id: String,
    val requesterId: String,
    val requesterName: String? = null,
    val requesterPhone: String? = null,
    val peopleCount: Int,
    val urgency: String,
    val description: String?,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val distance: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val sosEventId: String? = null,
    val adults: Int = 0,
    val children: Int = 0,
    val elderly: Int = 0,
    val photoUrl: String? = null,
    val emergencyType: String? = null,
    val neededItems: List<String> = emptyList()
)

enum class RescueMissionApiErrorKind {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    BAD_REQUEST,
    SERVER,
    TIMEOUT,
    NO_INTERNET,
    NO_TOKEN,
    UNKNOWN
}

data class RescueMissionApiError(val kind: RescueMissionApiErrorKind, val message: String)

sealed class RescueMissionApiResult<out T> {
    data class Success<out T>(val data: T) : RescueMissionApiResult<T>()
    data class Failure(val error: RescueMissionApiError) : RescueMissionApiResult<Nothing>()
}
