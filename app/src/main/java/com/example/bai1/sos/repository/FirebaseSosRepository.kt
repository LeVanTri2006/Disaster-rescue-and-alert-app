package com.example.bai1.sos.repository

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.realtime.FirebaseSosChange
import com.example.bai1.sos.realtime.FirebaseSosRealtimeDataSource
import com.example.bai1.sos.realtime.SosRealtimeDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FirebaseSosRepository(
    private val localRepository: SosRepository,
    private val realtimeDataSource: SosRealtimeDataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SosRepository {
    private val realtimeEvents = MutableStateFlow<Map<String, SosRescueInfo>>(emptyMap())
    private var realtimeStarted = false

    override suspend fun submitSos(sos: SosRescueInfo): SosSubmissionResult =
        localRepository.submitSos(sos)

    override suspend fun getMySos(): List<SosRescueInfo> {
        val now = System.currentTimeMillis()
        return localRepository.getMySos().filter { !it.isExpiredAt(now) }
    }

    override suspend fun getActiveSos(): List<SosRescueInfo> {
        android.util.Log.d("MAP_DEBUG", "FirebaseSosRepository.getActiveSos() called")
        val localActive = localRepository.getActiveSos()
        return merge(localActive, realtimeEvents.value.values.toList()).filter { it.isActive() }
    }

    override fun observeSos(): Flow<List<SosRescueInfo>> {
        ensureRealtimeStarted()
        return combine(localRepository.observeSos(), realtimeEvents) { local, realtime ->
            val result = merge(local, realtime.values.toList())
            android.util.Log.d("MAP_DEBUG", "OBSERVE_SOS_COUNT=${result.size}")
            result
        }
    }

    override fun observeRealtimeSos(): Flow<List<SosRescueInfo>> {
        ensureRealtimeStarted()
        return realtimeEvents.asStateFlow().map { map ->
            val now = System.currentTimeMillis()
            map.values.filter { !it.isExpiredAt(now) }
        }
    }

    override suspend fun getSosById(eventId: String): SosRescueInfo? {
        ensureRealtimeStarted()
        val now = System.currentTimeMillis()
        val event = localRepository.getSosById(eventId) ?: realtimeEvents.value[eventId]
        return event?.takeUnless { it.isExpiredAt(now) }
    }

    private fun ensureRealtimeStarted() {
        if (realtimeStarted) return
        realtimeStarted = true
        scope.launch {
            realtimeDataSource.observeChanges().collect { change ->
                val now = System.currentTimeMillis()
                realtimeEvents.update { current ->
                    val newState = when (change) {
                        is FirebaseSosChange.Upsert -> {
                            if (!change.event.isExpiredAt(now)) {
                                current + (change.event.eventId to change.event)
                            } else {
                                current - change.event.eventId
                            }
                        }
                        is FirebaseSosChange.Removed -> current - change.eventId
                    }
                    android.util.Log.d("FirebaseRealtime", "RTDB_EVENT_COUNT=${newState.size}")
                    newState
                }
            }
        }
    }

    private suspend fun mergedEvents(): List<SosRescueInfo> =
        merge(localRepository.getMySos(), realtimeEvents.value.values.toList())

    private fun merge(local: List<SosRescueInfo>, realtime: List<SosRescueInfo>): List<SosRescueInfo> {
        android.util.Log.d("MAP_DEBUG", "MERGE_BEFORE_DEDUPE=${local.size + realtime.size}")
        val now = System.currentTimeMillis()
        val merged = (local + realtime)
            .filter { !it.isExpiredAt(now) }
            .associateBy { it.eventId }
            .values
            .toList()
        android.util.Log.d("MAP_DEBUG", "MERGE_AFTER_DEDUPE=${merged.size}")
        return merged
    }

    private fun SosRescueInfo.isActive(): Boolean = status == SosStatus.SUBMITTED ||
        status == SosStatus.PENDING || status == SosStatus.QUEUED || status == SosStatus.ACTIVE
}
