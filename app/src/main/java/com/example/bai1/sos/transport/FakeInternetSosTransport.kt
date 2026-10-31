package com.example.bai1.sos.transport

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosTransportMode
import kotlinx.coroutines.delay

/**
 * Fake internet transport — simulates a successful REST/Firebase submission.
 *
 * Phase 7A ONLY. No real network request is made.
 *
 * Future replacement (Phase 7C/7D):
 *   class NestJsInternetSosTransport(private val api: SosApiService) : InternetSosTransport
 *   class FirebaseInternetSosTransport(private val db: FirebaseDatabase) : InternetSosTransport
 *
 * @param delayMs simulated network round-trip time in milliseconds.
 * @param shouldFail when true, always returns [SosSubmissionResult.Failed] for testing.
 */
class FakeInternetSosTransport(
    private val delayMs: Long = 800L,
    private val shouldFail: Boolean = false
) : InternetSosTransport {

    override suspend fun send(sos: SosRescueInfo): SosSubmissionResult {
        // Simulate network delay
        delay(delayMs)

        if (shouldFail) {
            return SosSubmissionResult.Failed(
                reason = "Fake internet transport: simulated failure"
            )
        }

        return SosSubmissionResult.Success(
            eventId = sos.eventId,
            transportMode = SosTransportMode.ONLINE,
            message = "Đã gửi yêu cầu cứu hộ"
        )
    }
}
