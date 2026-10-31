package com.example.bai1.sos.transport

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosTransportMode
import kotlinx.coroutines.delay

/**
 * Fake mesh transport — simulates queuing an SOS for peer-to-peer relay.
 *
 * Phase 7A ONLY. No Nearby Connections, Bluetooth, or BLE is imported here.
 *
 * Future replacement (Phase 7E):
 *   class NearbyConnectionsMeshSosTransport(
 *       private val connectionsClient: ConnectionsClient
 *   ) : MeshSosTransport
 *
 * @param delayMs simulated mesh handshake time in milliseconds.
 * @param shouldFail when true, always returns [SosSubmissionResult.Failed] for testing.
 */
class FakeMeshSosTransport(
    private val delayMs: Long = 600L,
    private val shouldFail: Boolean = false
) : MeshSosTransport {

    override suspend fun send(sos: SosRescueInfo): SosSubmissionResult {
        // Simulate peer discovery / handshake delay
        delay(delayMs)

        if (shouldFail) {
            return SosSubmissionResult.Failed(
                reason = "Fake mesh transport: simulated failure"
            )
        }

        return SosSubmissionResult.Queued(
            eventId = sos.eventId,
            transportMode = SosTransportMode.MESH,
            message = "Đã lưu yêu cầu, đang chờ kết nối"
        )
    }
}
