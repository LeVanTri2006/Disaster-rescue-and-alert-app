package com.example.bai1.sos.repository

import android.util.Log
import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosStatus
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.network.SosApiClient
import com.example.bai1.sos.network.SosApiResultList
import com.example.bai1.sos.transport.SosTransportManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory implementation of [SosRepository].
 *
 * Phase 7A: The single production-grade implementation.
 * Data lives in [MutableStateFlow] and is lost when the process dies.
 *
 * Phase 7C replacement plan:
 *   class RestSosRepository(private val api: SosApiService) : SosRepository
 * Phase 7D replacement plan:
 *   class FirebaseSosRepository(private val db: FirebaseDatabase) : SosRepository
 *
 * @param transportManager decides which transport channel to use
 */
class InMemorySosRepository(
    private val transportManager: SosTransportManager,
    private val apiClient: SosApiClient? = null
) : SosRepository {

    /** In-memory store; observed by the Disaster Map. */
    private val _sosEvents = MutableStateFlow<List<SosRescueInfo>>(emptyList())

    override fun observeSos(): Flow<List<SosRescueInfo>> = _sosEvents.asStateFlow()

    override fun observeRealtimeSos(): Flow<List<SosRescueInfo>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun submitSos(sos: SosRescueInfo): SosSubmissionResult {
        // 1. Persist locally first (optimistic store)
        val optimistic = sos.copy(status = SosStatus.PENDING)
        _sosEvents.update { current -> current + optimistic }

        // 2. Attempt transport
        val result = transportManager.send(sos)

        // 3. Update stored status based on result
        val updatedSos = when (result) {
            is SosSubmissionResult.Success -> (result.serverSos ?: sos.copy(
                eventId = result.eventId,
                createdAt = result.timestamp
            )).copy(
                status = SosStatus.SUBMITTED,
                transportMode = result.transportMode
            )
            is SosSubmissionResult.Queued -> sos.copy(
                status = SosStatus.QUEUED,
                transportMode = result.transportMode
            )
            is SosSubmissionResult.Failed -> sos.copy(
                status = SosStatus.FAILED,
                transportMode = SosTransportMode.UNKNOWN
            )
        }

        _sosEvents.update { current ->
            current.map { if (it.eventId == sos.eventId) updatedSos else it }
        }

        return result
    }

    override suspend fun getMySos(): List<SosRescueInfo> {
        // Phase 7A: all stored events are "mine" (no user auth yet)
        return _sosEvents.value
    }

    override suspend fun getActiveSos(): List<SosRescueInfo> {
        if (apiClient != null) {
            when (val result = apiClient.getActiveSos()) {
                is SosApiResultList.Success -> {
                    val events = result.events.map { it.toDomain() }
                    Log.d("MAP_DEBUG", "REST_API_RAW_COUNT=${result.events.size}, MAPPED_COUNT=${events.size}")
                    events.forEach { Log.d("MAP_DEBUG", "  REST_EVENT: id=${it.eventId} status=${it.status} lat=${it.latitude} lng=${it.longitude}") }
                    _sosEvents.update { current -> 
                        val map = current.associateBy { it.eventId }.toMutableMap()
                        events.forEach { map[it.eventId] = it }
                        map.values.toList()
                    }
                }
                is SosApiResultList.Failure -> {
                    // Ignore for now, fall back to memory
                }
            }
        }
        val all = _sosEvents.value
        val filtered = all.filter {
            it.status == SosStatus.PENDING || it.status == SosStatus.QUEUED || it.status == SosStatus.SUBMITTED || it.status == SosStatus.ACTIVE
        }
        Log.d("MAP_DEBUG", "INMEMORY_ALL=${all.size}, INMEMORY_FILTERED=${filtered.size}")
        return filtered
    }

    override suspend fun getSosById(eventId: String): SosRescueInfo? {
        return _sosEvents.value.find { it.eventId == eventId }
    }
}
