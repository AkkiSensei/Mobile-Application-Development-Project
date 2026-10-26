package com.example.madverse

import com.example.madverse.core.data.sync.*
import com.example.madverse.core.database.DirectTransactionRunner
import com.example.madverse.core.database.dao.NoOpPendingSyncDao
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.SyncState
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncReliabilityRepairTest {
    private fun movie(title: String = "Original") = Movie(1, title, "", null, null, null, 7.0, 0, emptyList(), 0.0)
    private fun action(type: String = "FAVORITE", operation: String = "UPSERT", uid: String = "A", timestamp: Long = 1) =
        PendingSyncEntity(userId = uid, actionType = operation, entityType = type, itemId = if (type == "PREFERENCE") "current" else "1",
            payloadJson = if (operation == "DELETE") null else if (type == "PREFERENCE") SyncPayloadSerializer.preferenceToJson(UserPreferenceProfile()) else SyncPayloadSerializer.movieToJson(movie()), createdAt = timestamp)

    private fun worker(dao: NoOpPendingSyncDao, uid: String = "A", current: suspend () -> Boolean = { true }, publish: (SyncState) -> Unit = {}, execute: suspend (PendingSyncEntity, SyncOperation) -> ReplayResult) =
        SyncQueueWorker(uid, dao, DirectTransactionRunner(), current, publish, execute, backoff = { 1000L })

    @Test fun allSixOperationsReplayInDurableIdOrderDespiteClockReversal() = runTest {
        val dao = NoOpPendingSyncDao()
        val seen = mutableListOf<Pair<String, Boolean>>()
        for (type in listOf("FAVORITE", "WATCHLIST", "PREFERENCE")) {
            dao.insertAction(action(type, timestamp = 100))
            dao.insertAction(action(type, "DELETE", timestamp = 1))
        }
        worker(dao) { _, op -> seen += op.collection to op.delete; ReplayResult.Success }.start(backgroundScope)
        runCurrent()
        assertEquals(listOf("favorites" to false, "favorites" to true, "watchlist" to false, "watchlist" to true, "preferences" to false, "preferences" to true), seen)
        assertTrue(dao.getPendingActionsForUser("A").isEmpty())
    }

    @Test fun immutableAddUpdateRemoveConvergesWithoutReadingCurrentEntity() = runTest {
        val dao = NoOpPendingSyncDao()
        dao.insertAction(action())
        dao.insertAction(action().copy(payloadJson = SyncPayloadSerializer.movieToJson(movie("Updated"))))
        dao.insertAction(action(operation = "DELETE"))
        val seen = mutableListOf<String?>()
        var remote: String? = null
        worker(dao) { _, op -> remote = if (op.delete) null else op.movie!!.title; seen += remote; ReplayResult.Success }.start(backgroundScope)
        runCurrent()
        assertEquals(listOf("Original", "Updated", null), seen)
        assertNull(remote)
    }

    @Test fun acknowledgementDoesNotRemoveNewerIntentOrOtherUser() = runTest {
        val dao = NoOpPendingSyncDao()
        val first = dao.insertAction(action())
        val other = dao.insertAction(action(uid = "B"))
        var newer = 0L
        worker(dao) { row, _ ->
            if (row.id == first) { newer = dao.insertAction(action(operation = "DELETE")); ReplayResult.Success }
            else ReplayResult.PermissionDenied
        }.start(backgroundScope)
        runCurrent()
        assertEquals(listOf(newer), dao.getPendingActionsForUser("A").map { it.id })
        assertEquals(listOf(other), dao.getPendingActionsForUser("B").map { it.id })
    }

    @Test fun repeatedTransientFailuresRetryWithoutExternalWake() = runTest {
        val dao = NoOpPendingSyncDao()
        dao.insertAction(action())
        var attempts = 0
        worker(dao) { _, _ -> if (++attempts < 4) ReplayResult.Transient else ReplayResult.Success }.start(backgroundScope)
        runCurrent()
        repeat(3) { assertEquals(1, dao.getPendingActionsForUser("A").size); advanceTimeBy(1000); runCurrent() }
        assertEquals(4, attempts)
        assertTrue(dao.getPendingActionsForUser("A").isEmpty())
    }

    @Test fun authenticationLossRetainsIntentAndRecoversOnSameSession() = runTest {
        val dao = NoOpPendingSyncDao()
        dao.insertAction(action())
        var signedIn = false
        worker(dao) { _, _ -> if (signedIn) ReplayResult.Success else ReplayResult.AuthRequired }.start(backgroundScope)
        runCurrent()
        assertEquals(1, dao.getPendingActionsForUser("A").size)
        signedIn = true
        advanceTimeBy(1000); runCurrent()
        assertTrue(dao.getPendingActionsForUser("A").isEmpty())
    }

    @Test fun permissionDenialPausesUntilExplicitRetryAndIsNotAuthExpiry() = runTest {
        val dao = NoOpPendingSyncDao()
        dao.insertAction(action())
        var attempts = 0
        var state: SyncState = SyncState.Idle
        var permitted = false
        val worker = worker(dao, publish = { state = it }) { _, _ -> attempts++; if (permitted) ReplayResult.Success else ReplayResult.PermissionDenied }
        worker.start(backgroundScope)
        runCurrent(); advanceTimeBy(120000); runCurrent()
        assertEquals(1, attempts)
        assertTrue((state as SyncState.Error).message.contains("permission denied"))
        assertEquals(1, dao.getPendingActionsForUser("A").size)
        permitted = true; worker.requestDrain(); runCurrent()
        assertTrue(dao.getPendingActionsForUser("A").isEmpty())
    }

    @Test fun malformedLegacyAndMismatchedPayloadsAreDurablyRetained() = runTest {
        val bad = listOf(action().copy(payloadJson = null), action().copy(payloadJson = "{}"), action().copy(payloadJson = "broken"),
            action().copy(itemId = "2"), action().copy(actionType = "MYSTERY"), action().copy(entityType = "UNKNOWN"),
            action("PREFERENCE").copy(itemId = "other"), action("PREFERENCE").copy(payloadJson = "{}"), action(operation = "DELETE").copy(payloadJson = "{}"))
        for (row in bad) {
            val dao = NoOpPendingSyncDao()
            dao.insertAction(row)
            var calls = 0
            val job = worker(dao) { _, _ -> calls++; ReplayResult.Success }.start(backgroundScope)
            runCurrent()
            assertEquals(0, calls)
            assertEquals(1, dao.getPendingActionsForUser("A").size)
            job.cancel()
        }
    }

    @Test fun cancellingOwningJobCancelsActualRequestAndRetainsRow() = runTest {
        val dao = NoOpPendingSyncDao()
        dao.insertAction(action())
        var cancelled = false
        val job = worker(dao) { _, _ -> try { awaitCancellation() } finally { cancelled = true } }.start(backgroundScope)
        runCurrent(); job.cancel(); runCurrent()
        assertTrue(cancelled)
        assertTrue(job.isCancelled)
        assertEquals(1, dao.getPendingActionsForUser("A").size)
    }

    @Test fun sessionSwitchDuringRemoteSuccessCannotAcknowledgeAOrBlockB() = runTest {
        val dao = NoOpPendingSyncDao()
        dao.insertAction(action()); dao.insertAction(action(operation = "DELETE")); dao.insertAction(action(uid = "B"))
        var active = "A"
        val seen = mutableListOf<String>()
        worker(dao, current = { active == "A" }) { row, _ -> seen += row.userId; active = "B"; ReplayResult.Success }.start(backgroundScope)
        runCurrent()
        worker(dao, uid = "B", current = { active == "B" }) { row, _ -> seen += row.userId; ReplayResult.Success }.start(backgroundScope)
        runCurrent()
        assertEquals(listOf("A", "B"), seen)
        assertEquals(2, dao.getPendingActionsForUser("A").size)
        assertTrue(dao.getPendingActionsForUser("B").isEmpty())
    }

    @Test fun restartAfterRemoteSuccessBeforeAckReplaysSameIntent() = runTest {
        val dao = NoOpPendingSyncDao()
        dao.insertAction(action())
        val remote = mutableMapOf<String, String>()
        worker(dao) { row, op -> remote[op.documentId] = op.movie!!.title; throw CancellationException("Simulated process loss before ack ${row.id}") }.start(backgroundScope)
        runCurrent()
        assertEquals(1, dao.getPendingActionsForUser("A").size)
        worker(dao) { _, op -> remote[op.documentId] = op.movie!!.title; ReplayResult.Success }.start(backgroundScope)
        runCurrent()
        assertEquals(mapOf("1" to "Original"), remote)
        assertTrue(dao.getPendingActionsForUser("A").isEmpty())
    }

    @Test fun stalledRemoteAwaitTimesOutAndRetries() = runTest {
        val dao = NoOpPendingSyncDao()
        dao.insertAction(action())
        var attempts = 0
        worker(dao) { _, _ -> if (++attempts == 1) awaitCancellation() else ReplayResult.Success }.start(backgroundScope)
        runCurrent(); advanceTimeBy(31000); runCurrent()
        assertEquals(2, attempts)
        assertTrue(dao.getPendingActionsForUser("A").isEmpty())
    }
}
