package com.example.bai1.sos.network

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.domain.SosUrgency
import com.example.bai1.sos.transport.ApiSosTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSosTransportTest {
    private val sos = SosRescueInfo(
        eventId = "local-id",
        adults = 2,
        condition = "Bị thương",
        latitude = 10.0,
        longitude = 20.0,
        locationAccuracy = 5f,
        batteryPercent = 80,
        urgency = SosUrgency.HIGH,
        createdAt = 1_700_000_000_000L
    )

    private val response = SosEventResponse(
        eventId = "server-id",
        adults = 2,
        children = 0,
        elderly = 0,
        neededItems = emptySet(),
        condition = "Bị thương",
        description = null,
        latitude = 10.0,
        longitude = 20.0,
        accuracyMeters = 5f,
        batteryPercent = 80,
        createdAt = 1_700_000_000_500L,
        status = "ACTIVE",
        urgency = SosUrgency.HIGH,
        transportMode = SosTransportMode.ONLINE
    )

    @Test
    fun `successful request uses server event id and response snapshot`() = runTest {
        var sent: CreateSosRequest? = null
        val transport = ApiSosTransport(object : SosApiClient {
            override suspend fun getActiveSos(): SosApiResultList = SosApiResultList.Failure(SosApiError(SosApiErrorKind.UNKNOWN, "not implemented")) 

            override suspend fun createSos(request: CreateSosRequest): SosApiResult {
                sent = request
                return SosApiResult.Success(response)
            }
        })

        val result = transport.send(sos) as SosSubmissionResult.Success

        assertEquals("server-id", result.eventId)
        assertEquals(1_700_000_000_500L, result.timestamp)
        assertEquals("server-id", result.serverSos?.eventId)
        assertEquals(2, sent?.adults)
        assertEquals(10.0, sent?.latitude)
    }

    @Test
    fun `400 is mapped to user friendly failure`() = runTest {
        assertFailure(SosApiErrorKind.BAD_REQUEST, "Thông tin SOS không hợp lệ.")
    }

    @Test
    fun `401 is mapped to authentication failure`() = runTest {
        assertFailure(SosApiErrorKind.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
    }

    @Test
    fun `403 is mapped to authorization failure`() = runTest {
        assertFailure(SosApiErrorKind.FORBIDDEN, "Bạn không có quyền gửi yêu cầu cứu hộ.")
    }

    @Test
    fun `timeout is mapped to retryable failure`() = runTest {
        assertFailure(SosApiErrorKind.TIMEOUT, "Kết nối quá thời gian. Vui lòng thử lại.")
    }

    @Test
    fun `5xx is mapped to server failure`() = runTest {
        assertFailure(SosApiErrorKind.SERVER, "Máy chủ đang bận. Vui lòng thử lại sau.")
    }

    private suspend fun assertFailure(kind: SosApiErrorKind, message: String) {
        val transport = ApiSosTransport(object : SosApiClient {
            override suspend fun getActiveSos(): SosApiResultList = SosApiResultList.Failure(SosApiError(SosApiErrorKind.UNKNOWN, "not implemented")) 

            override suspend fun createSos(request: CreateSosRequest) =
                SosApiResult.Failure(SosApiError(kind, "test"))
        })
        val result = transport.send(sos) as SosSubmissionResult.Failed
        assertEquals("test", result.reason)
        assertEquals(message, result.message)
        assertTrue(result.timestamp > 0)
    }
}
