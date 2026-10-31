package com.example.bai1.presentation.map

enum class MarkerType {
    SOS,
    RESCUE_REQUEST
}

data class MapRescueMarker(
    val type: MarkerType,
    val eventId: String? = null,
    val requestId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String,
    // Add extra common fields if necessary
    val adults: Int = 0,
    val children: Int = 0,
    val elderly: Int = 0,
    val totalPeople: Int = 0,
    val urgency: String = "",
    val condition: String = "",
    val neededItems: List<String> = emptyList(),
    val description: String = "",
    val createdAt: Long = 0L
)
