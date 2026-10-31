package com.example.bai1.sos.transport

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.network.CreateSosRequest
import com.example.bai1.sos.network.SosApiClient
import com.example.bai1.sos.network.SosApiError
import com.example.bai1.sos.network.SosApiErrorKind
import com.example.bai1.sos.network.SosApiResult

class ApiSosTransport(
    private val client: SosApiClient
) : InternetSosTransport {
    override suspend fun send(sos: SosRescueInfo): SosSubmissionResult {
        return when (val result = client.createSos(sos.toCreateRequest())) {
            is SosApiResult.Success -> SosSubmissionResult.Success(
                eventId = result.event.eventId,
                transportMode = SosTransportMode.ONLINE,
                timestamp = result.event.createdAt,
                message = "Đã gửi yêu cầu cứu hộ",
                serverSos = result.event.toDomain()
            )
            is SosApiResult.Failure -> SosSubmissionResult.Failed(
                reason = result.error.technicalReason,
                message = result.error.userMessage()
            )
        }
    }

    private fun SosRescueInfo.toCreateRequest() = CreateSosRequest(
        adults = adults,
        children = children,
        elderly = elderly,
        neededItems = neededItems,
        condition = condition,
        description = description.takeIf { it.isNotBlank() },
        latitude = requireNotNull(latitude),
        longitude = requireNotNull(longitude),
        accuracyMeters = locationAccuracy,
        batteryPercent = batteryPercent.takeIf { it in 0..100 },
        urgency = urgency,
        timestamp = createdAt,
        photoUrl = photoUri,
        photos = photos
    )

    private fun SosApiError.userMessage(): String = when (kind) {
        SosApiErrorKind.NO_TOKEN, SosApiErrorKind.UNAUTHORIZED -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        SosApiErrorKind.FORBIDDEN -> "Bạn không có quyền gửi yêu cầu cứu hộ."
        SosApiErrorKind.BAD_REQUEST -> "Thông tin SOS không hợp lệ."
        SosApiErrorKind.NOT_FOUND -> "Không tìm thấy dịch vụ SOS."
        SosApiErrorKind.CONFLICT -> "Bạn đã có một yêu cầu SOS đang hoạt động."
        SosApiErrorKind.SERVER -> "Máy chủ đang bận. Vui lòng thử lại sau."
        SosApiErrorKind.TIMEOUT -> "Kết nối quá thời gian. Vui lòng thử lại."
        SosApiErrorKind.NO_INTERNET -> "Không có kết nối mạng. Yêu cầu đã được lưu cục bộ."
        SosApiErrorKind.UNKNOWN -> "Không thể gửi yêu cầu cứu hộ."
    }
}
