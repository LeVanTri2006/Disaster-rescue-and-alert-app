package com.example.bai1.sos.transport

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.provider.NetworkAvailabilityProvider

/**
 * Decides which transport channel to use for an SOS event.
 *
 * Routing logic (Phase 7A):
 *   internet available → [InternetSosTransport]  → expected result: Success
 *   no internet        → [MeshSosTransport]       → expected result: Queued
 *
 * Future enhancements (Phase 7E/7F):
 *   - Relay mode: mesh → gateway node → server
 *   - Priority override: user can force mesh even when internet is available
 *
 * The ViewModel depends on this manager via [SosRepository].
 * It does NOT depend on this class directly.
 *
 * @param network   detects internet availability (injected for testability)
 * @param internet  the internet transport implementation
 * @param mesh      the mesh transport implementation
 */
class SosTransportManager(
    private val network: NetworkAvailabilityProvider,
    private val internet: InternetSosTransport,
    private val mesh: MeshSosTransport
) {

    /**
     * Sends the SOS via the most appropriate available transport.
     *
     * @param sos the SOS event to transmit
     * @return a [SosSubmissionResult] describing the outcome
     */
    suspend fun send(sos: SosRescueInfo): SosSubmissionResult {
        return if (network.isInternetAvailable()) {
            internet.send(sos)
        } else {
            mesh.send(sos)
        }
    }

    /**
     * Returns the transport mode that would be selected given current network state.
     * Useful for UI to preview the expected transport before submission.
     */
    fun expectedTransportMode(): SosTransportMode {
        return if (network.isInternetAvailable()) SosTransportMode.ONLINE
               else SosTransportMode.MESH
    }
}
