package com.example.bai1.sos.realtime

import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.domain.SosUrgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseSosEventMapperTest {
    @Test
    fun `maps active projection using server event id and rescue fields`() {
        val change = FirebaseSosEventMapper.parse(
            eventId = "server-event-1",
            values = mapOf(
                "status" to "ACTIVE",
                "adults" to 2L,
                "children" to 1L,
                "elderly" to 1L,
                "neededItems" to listOf("Nước uống", "Thuốc"),
                "condition" to "Bị thương",
                "latitude" to 10.25,
                "longitude" to 106.7,
                "accuracyMeters" to 4.5,
                "batteryPercent" to 72L,
                "urgency" to "CRITICAL",
                "createdAt" to "2026-09-16T10:00:00.000Z",
            ),
        )

        val event = (change as FirebaseSosChange.Upsert).event
        assertEquals("server-event-1", event.eventId)
        assertEquals(4, event.totalPeople)
        assertEquals(setOf("Nước uống", "Thuốc"), event.neededItems)
        assertEquals(10.25, event.latitude ?: -1.0, 0.0001)
        assertEquals(106.7, event.longitude ?: -1.0, 0.0001)
        assertEquals(SosUrgency.CRITICAL, event.urgency)
        assertEquals(SosStatus.SUBMITTED, event.status)
    }

    @Test
    fun `maps terminal statuses to removal`() {
        assertEquals(
            FirebaseSosChange.Removed("event-resolved"),
            FirebaseSosEventMapper.parse("event-resolved", mapOf("status" to "RESOLVED")),
        )
        assertEquals(
            FirebaseSosChange.Removed("event-false-alarm"),
            FirebaseSosEventMapper.parse("event-false-alarm", mapOf("status" to "FALSE_ALARM")),
        )
    }

    @Test
    fun `rejects missing ids and invalid coordinates`() {
        assertNull(FirebaseSosEventMapper.parse(null, mapOf("status" to "ACTIVE")))
        assertNull(FirebaseSosEventMapper.parse("bad-lat", mapOf("status" to "ACTIVE", "latitude" to 91, "longitude" to 20)))
        assertNull(FirebaseSosEventMapper.parse("bad-lon", mapOf("status" to "ACTIVE", "latitude" to 10, "longitude" to 181)))
        assertTrue(FirebaseSosEventMapper.parse("terminal", mapOf("status" to "RESOLVED")) is FirebaseSosChange.Removed)
    }
}
