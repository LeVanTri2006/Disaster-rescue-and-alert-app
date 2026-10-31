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
}
