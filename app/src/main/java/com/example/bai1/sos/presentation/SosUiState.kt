package com.example.bai1.sos.presentation

import com.example.bai1.sos.domain.SosSubmissionResult

sealed interface LocationUiState {
    data object Loading : LocationUiState
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val timestamp: Long
    ) : LocationUiState
    data class Error(val message: String) : LocationUiState
}

/**
 * Single, exhaustive UI state for the SOS rescue request screen.
 *
 * Replaces the scattered boolean flags (isLoading, isSuccess, hasError) that
 * existed in the original [SosRescueRequestState].
 *
 * State transitions:
 *
 *   Idle → Editing → Submitting → Submitted
 *                              ↘ Queued
 *                              ↘ Error
 *
 * The UI renders exactly one of these states at a time.
 * No other boolean flags should be added to the ViewModel.
 */
sealed class SosUiState {

    /**
     * The form has not been interacted with yet, or was just reset.
     */
    object Idle : SosUiState()

    /**
     * The user is actively filling in the form.
     * @param validationError non-null when a field-level error should be shown.
     */
    data class Editing(
        val validationError: String? = null,
        val location: LocationUiState = LocationUiState.Loading
    ) : SosUiState()

    /**
     * Form submission is in progress. The UI must disable the submit button
     * and show a loading indicator. Double-tap prevention is enforced here.
     */
    object Submitting : SosUiState()

    /**
     * Submission succeeded and the SOS is confirmed on the transport channel.
     * @param result the full success result including eventId and transport mode.
     */
    data class Submitted(
        val result: SosSubmissionResult.Success
    ) : SosUiState()

    /**
     * Submission was accepted locally but internet was unavailable.
     * The SOS is queued for later transmission (mesh or retry).
     * @param result the queued result including eventId and intended transport mode.
     */
    data class Queued(
        val result: SosSubmissionResult.Queued
    ) : SosUiState()

    /**
     * Submission failed completely. Shows error message and allows retry.
     * @param message localised error description for the UI.
     */
    data class Error(
        val message: String
    ) : SosUiState()
}
