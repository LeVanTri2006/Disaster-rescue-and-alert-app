package com.example.bai1.sos.domain

import java.util.UUID

/**
 * Core domain model for an SOS rescue request.
 *
 * This is the single source of truth for SOS data inside the Android app.
 * It is transport-agnostic: neither REST nor Nearby Connections details live here.
 *
 * Android domain model only. No database mapping yet.
 */
data class SosRescueInfo(
    /** Client-generated unique ID (UUID). Will become the server eventId in Phase 7C. */
    val eventId: String = UUID.randomUUID().toString(),

    // ── People ──────────────────────────────────────────────────────────────
    val adults: Int = 1,
    val children: Int = 0,
    val elderly: Int = 0,

    // ── Situation ───────────────────────────────────────────────────────────
    val neededItems: Set<String> = emptySet(),
    val condition: String = "",
    val description: String = "",

    // ── Media ───────────────────────────────────────────────────────────────
    /** URI string referencing a local image. It remains local until a later upload phase. */
    val photoUri: String? = null,
    val photos: List<String>? = null,

    // ── Location ─────────────────────────────────────────────────────────────
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** GPS accuracy in meters. Null when unavailable. */
    val locationAccuracy: Float? = null,
    /** Timestamp supplied by the location provider, when available. */
    val locationTimestamp: Long? = null,

    // ── Metadata ─────────────────────────────────────────────────────────────
    val createdAt: Long = System.currentTimeMillis(),
    /** Battery percent at submission time. From BatteryInfoProvider. */
    val batteryPercent: Int = -1,
    val urgency: SosUrgency = SosUrgency.HIGH,
    val status: SosStatus = SosStatus.PENDING,
    val transportMode: SosTransportMode = SosTransportMode.UNKNOWN
) {
    /** Derived total: adults + children + elderly. */
    val totalPeople: Int get() = adults + children + elderly
}
