package com.example.bai1.rescue.network

enum class MissionStatus {
    ASSIGNED,
    IN_ROUTE,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class RescueMissionResponse(
    val id: String,
    val requestId: String,
    val rescuerId: String?,
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
    val peopleCount: Int,
    val urgency: String,
    val description: String?,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val distance: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val sosEventId: String? = null
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
