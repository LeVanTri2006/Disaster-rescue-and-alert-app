package com.example.bai1.sos.repository

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for SOS rescue request operations.
 *
 * The ViewModel depends ONLY on this interface. It must never depend on:
 *   - Firebase
 *   - Retrofit / HTTP client
 *   - Nearby Connections
 *   - Activity / Context (directly)
 *   - Android Bluetooth APIs
 *
 * Phase 7A: [InMemorySosRepository] is the only implementation.
 * Phase 7C: A real REST-backed repository will be added.
 * Phase 7D: A Firebase-backed repository may be added for real-time sync.
 */
interface SosRepository {

    /**
     * Submits a new SOS rescue request and returns the transmission result.
     *
     * This stores the SOS locally AND attempts to transmit it via the
     * appropriate transport channel (internet or mesh).
     *
     * @param sos the rescue information to submit
     * @return [SosSubmissionResult] describing success, queued, or failure state
     */
    suspend fun submitSos(sos: SosRescueInfo): SosSubmissionResult

    /**
     * Returns all SOS events submitted by the current user.
     */
    suspend fun getMySos(): List<SosRescueInfo>

    /**
     * Returns all currently active SOS events (PENDING or QUEUED status).
     */
    suspend fun getActiveSos(): List<SosRescueInfo>

    /**
     * Provides a reactive stream of all SOS events.
     * Emits a new list whenever the in-memory state changes.
     *
     * The Disaster Map observes this flow to display markers.
     */
    fun observeSos(): Flow<List<SosRescueInfo>>

    /** Realtime projection stream, kept separate from local/optimistic SOS state. */
    fun observeRealtimeSos(): Flow<List<SosRescueInfo>> = observeSos()

    /**
     * Retrieves a specific SOS event by its client-generated event ID.
     *
     * @param eventId the UUID of the SOS event
     * @return the matching [SosRescueInfo] or null if not found
     */
    suspend fun getSosById(eventId: String): SosRescueInfo?
}
