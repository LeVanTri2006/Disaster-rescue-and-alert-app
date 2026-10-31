package com.example.bai1.sos

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.provider.FakeNetworkAvailabilityProvider
import com.example.bai1.sos.transport.FakeInternetSosTransport
import com.example.bai1.sos.transport.FakeMeshSosTransport
import com.example.bai1.sos.transport.SosTransportManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SosTransportManagerTest {

    @Test
    fun `test routeSosEvent when online uses Internet Transport`() = runBlocking {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        val internetTransport = FakeInternetSosTransport(delayMs = 0, shouldFail = false)
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)

        val sos = SosRescueInfo(
            eventId = UUID.randomUUID().toString(),
            condition = "Ngập sâu",
            adults = 3,
            neededItems = emptySet(),
            latitude = 0.0,
            longitude = 0.0,
            batteryPercent = 100
        )

        val result = transportManager.send(sos)

        assertTrue(result is SosSubmissionResult.Success)
        val successResult = result as SosSubmissionResult.Success
        assertEquals(SosTransportMode.ONLINE, successResult.transportMode)
    }

    @Test
    fun `test routeSosEvent when offline uses Mesh Transport`() = runBlocking {
        val networkProvider = FakeNetworkAvailabilityProvider(online = false)
        val internetTransport = FakeInternetSosTransport(delayMs = 0, shouldFail = false)
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)

        val sos = SosRescueInfo(
            eventId = UUID.randomUUID().toString(),
            condition = "Bị thương",
            adults = 1,
            neededItems = emptySet(),
            latitude = 0.0,
            longitude = 0.0,
            batteryPercent = 100
        )

        val result = transportManager.send(sos)

        assertTrue(result is SosSubmissionResult.Queued)
        val queuedResult = result as SosSubmissionResult.Queued
        assertEquals(SosTransportMode.MESH, queuedResult.transportMode)
    }

    @Test
    fun `test routeSosEvent failure propagation`() = runBlocking {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        val internetTransport = FakeInternetSosTransport(delayMs = 0, shouldFail = true)
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)

        val sos = SosRescueInfo(
            eventId = UUID.randomUUID().toString(),
            condition = "Sập nhà",
            adults = 2,
            neededItems = emptySet(),
            latitude = 0.0,
            longitude = 0.0,
            batteryPercent = 100
        )

        val result = transportManager.send(sos)

        assertTrue(result is SosSubmissionResult.Failed)
        val failedResult = result as SosSubmissionResult.Failed
        assertEquals("Fake internet transport: simulated failure", failedResult.reason)
    }
}
