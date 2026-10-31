package com.example.bai1.sos.presentation

import android.net.Uri

/**
 * Mutable form data held by the ViewModel while the user fills in the SOS form.
 *
 * This is intentionally separate from [com.example.bai1.sos.domain.SosRescueInfo]:
 *   - Form data is mutable and UI-driven (counts, picker selections, text input).
 *   - Domain model is immutable and submitted to the repository.
 *
 * Conversion: [toSosRescueInfo()] produces a domain model snapshot on submit.
 */
data class SosFormData(
    val adults: Int = 1,
    val children: Int = 0,
    val elderly: Int = 0,
    val neededItems: Set<String> = emptySet(),
    val condition: String = "",
    val description: String = "",
    /** Android URIs of the selected/captured photos. Limited to 3. */
    val photoUris: List<Uri> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationTimestamp: Long? = null
) {
    val photoUri: Uri? get() = photoUris.firstOrNull()
    val totalPeople: Int get() = adults + children + elderly
}
