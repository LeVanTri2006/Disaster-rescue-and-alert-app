package com.example.bai1.sos.domain

/**
 * Result of attempting to submit an SOS rescue request.
 *
 * The UI renders different messages depending on the variant:
 *   Success → "Đã gửi yêu cầu cứu hộ"
 *   Queued  → "Đã lưu yêu cầu, đang chờ kết nối"
 *   Failed  → "Không thể gửi yêu cầu"
 */
sealed class SosSubmissionResult {

    /**
     * SOS was successfully transmitted (via Internet or confirmed relay).
     *
     * @param eventId       the client-generated (and optionally server-confirmed) event ID
     * @param transportMode how the SOS was transmitted
     * @param timestamp     epoch ms when the result was received
     * @param message       localised success message for the UI
     */
    data class Success(
        val eventId: String,
        val transportMode: SosTransportMode,
        val timestamp: Long = System.currentTimeMillis(),
        val message: String = "Đã gửi yêu cầu cứu hộ",
        /** Full server snapshot when ONLINE transport has received one. */
        val serverSos: SosRescueInfo? = null
    ) : SosSubmissionResult()

    /**
     * SOS was saved locally and queued for later transmission (no internet).
     *
     * @param eventId       the client-generated event ID
     * @param transportMode the intended future transport (usually MESH or RELAY)
     * @param timestamp     epoch ms when the result was produced
     * @param message       localised queued message for the UI
     */
    data class Queued(
        val eventId: String,
        val transportMode: SosTransportMode,
        val timestamp: Long = System.currentTimeMillis(),
        val message: String = "Đã lưu yêu cầu, đang chờ kết nối"
    ) : SosSubmissionResult()

    /**
     * Submission failed completely (validation error, all transports unavailable).
     *
     * @param reason    developer-facing failure description
     * @param timestamp epoch ms when the failure occurred
     * @param message   localised failure message for the UI
     */
    data class Failed(
        val reason: String,
        val timestamp: Long = System.currentTimeMillis(),
        val message: String = "Không thể gửi yêu cầu"
    ) : SosSubmissionResult()
}
