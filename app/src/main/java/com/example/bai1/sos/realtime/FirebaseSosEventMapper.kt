package com.example.bai1.sos.realtime

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.domain.SosUrgency
import com.google.firebase.database.DataSnapshot
import java.time.Instant

sealed interface FirebaseSosChange {
    data class Upsert(val event: SosRescueInfo) : FirebaseSosChange
    data class Removed(val eventId: String) : FirebaseSosChange
}

object FirebaseSosEventMapper {
    fun fromSnapshot(snapshot: DataSnapshot): FirebaseSosChange? =
        parse(snapshot.key, snapshot.value as? Map<*, *>)

    fun parse(eventId: String?, values: Map<*, *>?): FirebaseSosChange? {
        val id = eventId?.takeIf { it.isNotBlank() } ?: return null
        values ?: return null

        // Terminal projections need only an ID: remove stale markers even without coordinates.
        val status = values["status"]?.toString()
        if (status == "RESOLVED" || status == "FALSE_ALARM") {
            return FirebaseSosChange.Removed(id)
        }

        val latitude = values.number("latitude")?.toDouble()
        val longitude = values.number("longitude")?.toDouble()
        if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return null
        }

        val urgency = runCatching {
            SosUrgency.valueOf(values["urgency"]?.toString() ?: SosUrgency.HIGH.name)
        }.getOrDefault(SosUrgency.HIGH)

        return FirebaseSosChange.Upsert(
            SosRescueInfo(
                eventId = id,
                adults = values.number("adults")?.toInt() ?: 0,
                children = values.number("children")?.toInt() ?: 0,
                elderly = values.number("elderly")?.toInt() ?: 0,
                neededItems = (values["neededItems"] as? List<*>)
                    ?.mapNotNull { it?.toString() }
                    ?.toSet()
                    ?: emptySet(),
                condition = values["condition"]?.toString().orEmpty(),
                photoUri = values["photoUrl"]?.toString(),
                latitude = latitude,
                longitude = longitude,
                locationAccuracy = values.number("accuracyMeters")?.toFloat(),
                createdAt = values.timestamp("createdAt") ?: System.currentTimeMillis(),
                batteryPercent = values.number("batteryPercent")?.toInt() ?: -1,
                urgency = urgency,
                status = when (values["status"]?.toString()) {
                    "ACTIVE" -> SosStatus.ACTIVE
                    "RESOLVED" -> SosStatus.RESOLVED
                    "FALSE_ALARM" -> SosStatus.FALSE_ALARM
                    else -> SosStatus.PENDING
                },
                transportMode = SosTransportMode.ONLINE,
            )
        )
    }

    private fun Map<*, *>.number(key: String): Number? = when (val v = this[key]) {
        is Number -> v
        is String -> v.toDoubleOrNull()
        else -> null
    }

    private fun Map<*, *>.timestamp(key: String): Long? = when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        else -> null
    }
}
