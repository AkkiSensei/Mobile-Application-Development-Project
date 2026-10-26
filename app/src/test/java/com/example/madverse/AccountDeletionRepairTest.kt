package com.example.madverse

import com.example.madverse.core.data.account.*
import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.NoOpCloudSyncService
import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import com.example.madverse.fakes.FakeAuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AccountDeletionRepairTest {
    private val journal = InMemoryAccountDeletionJournal()
    private val auth = FakeAuthRepository(AuthState.Authenticated(AuthUser("A", "test@example.invalid", "Test", null, true)))
    private val events = mutableListOf<String>()
    private fun sync(purgeFailure: Boolean = false, cleanupFailure: Boolean = false) = object : CloudSyncService by NoOpCloudSyncService() {
        override fun stopUserSession() { events += "stop" }
        override suspend fun purgeRemoteUserData(userId: String): Result<Unit> {
            assertEquals(DeletionStage.STARTED, journal.getJournalEntry(userId)!!.stage)
            assertEquals(userId, auth.currentUserId)
            events += "purge"
            return if (purgeFailure) Result.failure(IOException("Purge unavailable")) else Result.success(Unit)
        }
        override suspend fun deleteAllUserData(userId: String): Result<Unit> {
            assertTrue(journal.getJournalEntry(userId)!!.stage in setOf(DeletionStage.REMOTE_AUTH_CONFIRMED, DeletionStage.ACCOUNT_DATA_CLEANED))
            events += "local"
            return if (cleanupFailure) Result.failure(IOException("Disk unavailable")) else Result.success(Unit)
        }
    }

    @Test fun cloudPurgeFailureCannotDeleteIdentityCleanLocalOrClearJournal() = runTest {
        val result = AccountDeletionCoordinator(auth, sync(purgeFailure = true), journal).delete("A")
        assertTrue(result.isFailure)
        assertEquals("A", auth.currentUserId)
        assertEquals(listOf("stop", "purge"), events)
        assertEquals(DeletionStage.STARTED, journal.getJournalEntry("A")!!.stage)
        assertTrue(journal.isDeletionPending("A"))
    }

    @Test fun authFailureAfterPurgePreservesLocalAndRequestedStage() = runTest {
        auth.shouldFailNextOperation = true
        auth.failureException = IOException("Ambiguous response")
        val result = AccountDeletionCoordinator(auth, sync(), journal).delete("A")
        assertTrue(result.isFailure)
        assertEquals(listOf("stop", "purge"), events)
        assertEquals(DeletionStage.AUTH_DELETE_REQUESTED, journal.getJournalEntry("A")!!.stage)
    }

    @Test fun nullUserAfterAmbiguousAuthResultDoesNotEstablishSuccess() = runTest {
        journal.recordStage("A", DeletionStage.AUTH_DELETE_REQUESTED)
        auth.signOut()
        assertTrue(AccountDeletionCoordinator(auth, sync(), journal).delete("A").isFailure)
        assertEquals(DeletionStage.AUTH_DELETE_REQUESTED, journal.getJournalEntry("A")!!.stage)
        assertTrue(events.isEmpty())
        assertEquals("A", journal.pendingEntries().single().userId)
    }

    @Test fun restartWithConfirmedAuthResumesOnlyLocalCleanupEvenWithNullUser() = runTest {
        journal.recordStage("A", DeletionStage.REMOTE_AUTH_CONFIRMED)
        auth.signOut()
        assertTrue(AccountDeletionCoordinator(auth, sync(), journal).delete("A").isSuccess)
        assertEquals(listOf("local"), events)
        assertEquals(DeletionStage.COMPLETED, journal.getJournalEntry("A")!!.stage)
    }

    @Test fun localCleanupFailureRetainsRemoteConfirmationForSafeRetry() = runTest {
        journal.recordStage("A", DeletionStage.REMOTE_AUTH_CONFIRMED)
        auth.signOut()
        assertTrue(AccountDeletionCoordinator(auth, sync(cleanupFailure = true), journal).delete("A").isFailure)
        assertEquals(DeletionStage.REMOTE_AUTH_CONFIRMED, journal.getJournalEntry("A")!!.stage)
        assertTrue(AccountDeletionCoordinator(auth, sync(), journal).delete("A").isSuccess)
        assertEquals(listOf("local", "local"), events)
    }

    @Test fun successfulFlowChecksEveryStageBeforeReportingCompletion() = runTest {
        val verifiedAuth = object : AuthRepository by auth {
            override suspend fun deleteAccountForUser(expectedUserId: String): Result<Unit> {
                assertEquals(DeletionStage.AUTH_DELETE_REQUESTED, journal.getJournalEntry(expectedUserId)!!.stage)
                events += "auth"
                return auth.deleteAccountForUser(expectedUserId)
            }
        }
        assertTrue(AccountDeletionCoordinator(verifiedAuth, sync(), journal).delete("A").isSuccess)
        assertEquals(listOf("stop", "purge", "auth", "local"), events)
        assertFalse(journal.isDeletionPending("A"))
        assertEquals(DeletionStage.COMPLETED, journal.getJournalEntry("A")!!.stage)
    }

    @Test fun cancellationDuringAuthPreservesAmbiguousJournalAndPropagates() = runTest {
        val cancelledAuth = object : AuthRepository by auth {
            override suspend fun deleteAccountForUser(expectedUserId: String): Result<Unit> = throw CancellationException("Interrupted")
        }
        try { AccountDeletionCoordinator(cancelledAuth, sync(), journal).delete("A"); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals(DeletionStage.AUTH_DELETE_REQUESTED, journal.getJournalEntry("A")!!.stage)
        assertEquals(listOf("stop", "purge"), events)
    }

    @Test fun failedJournalWritePreventsAnyDestructiveOperation() = runTest {
        val broken = object : AccountDeletionJournal by journal {
            override suspend fun recordStage(userId: String, stage: DeletionStage, error: String?) { throw IOException("Disk full") }
        }
        assertTrue(AccountDeletionCoordinator(auth, sync(), broken).delete("A").isFailure)
        assertTrue(events.isEmpty())
        assertEquals("A", auth.currentUserId)
    }

    @Test fun oldUnreliableStagesCannotBePromotedToConfirmedDeletion() = runTest {
        for (stage in listOf(DeletionStage.REMOTE_AUTH_DELETED, DeletionStage.REMOTE_DATA_DELETED, DeletionStage.LOCAL_DATA_CLEANED)) {
            journal.recordStage("A", stage)
            assertTrue(AccountDeletionCoordinator(auth, sync(), journal).delete("A").isFailure)
            assertEquals(stage, journal.getJournalEntry("A")!!.stage)
        }
        assertTrue(events.isEmpty())
    }

    @Test fun anotherSignedInUserCannotResumeAmbiguousDeletion() = runTest {
        journal.recordStage("A", DeletionStage.AUTH_DELETE_REQUESTED)
        auth.setAuthenticated(AuthUser("B", "b@example.invalid", "B", null, true))
        assertTrue(AccountDeletionCoordinator(auth, sync(), journal).delete("A").isFailure)
        assertEquals("B", auth.currentUserId)
        assertTrue(events.isEmpty())
    }
}
