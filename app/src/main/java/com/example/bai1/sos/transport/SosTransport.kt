package com.example.bai1.sos.transport

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult

/**
 * Transport interface for sending SOS events via an Internet connection.
 *
 * Phase 7A: Only [FakeInternetSosTransport] is provided.
 * Phase 7C: A real implementation will use NestJS REST.
 * Phase 7D: An alternative real implementation may use Firebase Realtime Database.
 *
 * IMPORTANT: Implementations must NOT import:
 *   - Firebase SDK
 *   - Retrofit / OkHttp
 *   - Activity
 * in Phase 7A.
 */
interface InternetSosTransport {
    /**
     * Attempts to send an SOS event via an internet channel.
     *
     * @param sos the SOS rescue info to transmit
     * @return [SosSubmissionResult.Success] or [SosSubmissionResult.Failed]
     */
    suspend fun send(sos: SosRescueInfo): SosSubmissionResult
}

/**
 * Transport interface for sending SOS events via a local mesh network.
 *
 * Phase 7A: Only [FakeMeshSosTransport] is provided.
 * Phase 7E: A real implementation will use Nearby Connections API.
 *
 * IMPORTANT: Implementations must NOT import:
 *   - com.google.android.gms.nearby
 *   - Bluetooth / BLE APIs
 * in Phase 7A.
 */
interface MeshSosTransport {
    /**
     * Attempts to queue or transmit an SOS event via peer-to-peer mesh.
     *
     * @param sos the SOS rescue info to relay
     * @return [SosSubmissionResult.Queued] (best-effort) or [SosSubmissionResult.Failed]
     */
    suspend fun send(sos: SosRescueInfo): SosSubmissionResult
}
