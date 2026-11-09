package com.example.bai1.sos

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.provider.FakeNetworkAvailabilityProvider
import com.example.bai1.sos.repository.InMemorySosRepository
import com.example.bai1.sos.transport.FakeInternetSosTransport
import com.example.bai1.sos.transport.FakeMeshSosTransport
import com.example.bai1.sos.transport.SosTransportManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SosRepositoryTest {

    @Test
    fun `test initial state is empty list`() = runBlocking {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        val internetTransport = FakeInternetSosTransport(delayMs = 0, shouldFail = false)
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)
        val repository = InMemorySosRepository(transportManager)

        val initialEvents = repository.observeSos().first()
        assertTrue(initialEvents.isEmpty())
    }

    @Test
    fun `test submitSos success updates flow`() = runBlocking {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        val internetTransport = FakeInternetSosTransport(delayMs = 0, shouldFail = false)
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)
        val repository = InMemorySosRepository(transportManager)

        val sos = SosRescueInfo(
            eventId = UUID.randomUUID().toString(),
            condition = "Lũ quét",
            adults = 4,
            neededItems = setOf("Nước", "Thực phẩm"),
            latitude = 0.0,
            longitude = 0.0,
            batteryPercent = 100
        )

        val result = repository.submitSos(sos)

        assertTrue(result is SosSubmissionResult.Success)

        val activeEvents = repository.observeSos().first()
        assertEquals(1, activeEvents.size)
        assertEquals(sos.eventId, activeEvents[0].eventId)
        assertEquals(SosStatus.SUBMITTED, activeEvents[0].status)
    }

    @Test
    fun `test expired events older than 3 days are filtered out`() = runBlocking {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        // Supply a server snapshot with the original event time, as the real API transport does.
        // The generic fake otherwise acknowledges a NEW submission with today's timestamp.
        val internetTransport = object : com.example.bai1.sos.transport.InternetSosTransport {
            override suspend fun send(sos: SosRescueInfo): SosSubmissionResult = SosSubmissionResult.Success(
                eventId = sos.eventId,
                transportMode = com.example.bai1.sos.domain.SosTransportMode.ONLINE,
                serverSos = sos
            )
        }
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)
        val repository = InMemorySosRepository(transportManager)

        val fourDaysAgo = System.currentTimeMillis() - (4 * 24 * 60 * 60 * 1000L)
        val expiredSos = SosRescueInfo(
            eventId = "expired-1",
            condition = "Lũ lụt cũ",
            createdAt = fourDaysAgo,
            status = SosStatus.PENDING
        )
        val freshSos = SosRescueInfo(
            eventId = "fresh-1",
            condition = "Cần cứu gấp",
            createdAt = System.currentTimeMillis(),
            status = SosStatus.PENDING
        )

        repository.submitSos(expiredSos)
        repository.submitSos(freshSos)

        val activeEvents = repository.getActiveSos()
        assertEquals(1, activeEvents.size)
        assertEquals("fresh-1", activeEvents[0].eventId)

        val observed = repository.observeSos().first()
        assertEquals(1, observed.size)
        assertEquals("fresh-1", observed[0].eventId)
    }
    @Test
    fun `expiry retains exactly 72 hours and expires one millisecond later`() {
        val now = 1_800_000_000_000L
        val boundary = SosRescueInfo(createdAt = now - SosRescueInfo.MAX_SOS_AGE_MS)
        org.junit.Assert.assertFalse(boundary.isExpiredAt(now))
        assertTrue(boundary.isExpiredAt(now + 1))
        org.junit.Assert.assertFalse(boundary.copy(createdAt = now).isExpiredAt(now))
    }

}
