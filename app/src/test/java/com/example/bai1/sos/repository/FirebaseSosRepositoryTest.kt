package com.example.bai1.sos.repository

import com.example.bai1.sos.domain.SosRescueInfo
import com.example.bai1.sos.domain.SosSubmissionResult
import com.example.bai1.sos.realtime.FirebaseSosChange
import com.example.bai1.sos.realtime.SosRealtimeDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirebaseSosRepositoryTest {
    @Test
    fun `deduplicates by event id and removes terminal events`() = runTest {
        val first = SosRescueInfo(eventId = "server-event-1", condition = "Bị thương")
        val changed = first.copy(condition = "Đã được hỗ trợ")
        val changes = MutableSharedFlow<FirebaseSosChange>(extraBufferCapacity = 1)
        val source = object : SosRealtimeDataSource {
            override fun observeChanges(): Flow<FirebaseSosChange> = changes
        }
        val repository = FirebaseSosRepository(
            localRepository = EmptySosRepository(),
            realtimeDataSource = source,
            scope = backgroundScope,
        )

        val realtime = repository.observeRealtimeSos()
        val snapshots = mutableListOf<List<SosRescueInfo>>()
        val job = launch {
            realtime.collect { snapshots += it }
        }
        runCurrent()

        changes.tryEmit(FirebaseSosChange.Upsert(first))
        runCurrent()
        changes.tryEmit(FirebaseSosChange.Upsert(changed))
        runCurrent()
        changes.tryEmit(FirebaseSosChange.Removed(first.eventId))
        runCurrent()
        job.cancel()

        assertEquals(4, snapshots.size)
        assertEquals(emptyList<SosRescueInfo>(), snapshots[0])
        assertEquals(listOf(first), snapshots[1])
        assertEquals(listOf(changed), snapshots[2])
        assertEquals(emptyList<SosRescueInfo>(), snapshots[3])
    }

    private class EmptySosRepository : SosRepository {
        override suspend fun submitSos(sos: SosRescueInfo): SosSubmissionResult =
            SosSubmissionResult.Failed("not used")

        override suspend fun getMySos(): List<SosRescueInfo> = emptyList()
        override suspend fun getActiveSos(): List<SosRescueInfo> = emptyList()
        override fun observeSos(): Flow<List<SosRescueInfo>> = emptyFlow()
        override suspend fun getSosById(eventId: String): SosRescueInfo? = null
    }
}
