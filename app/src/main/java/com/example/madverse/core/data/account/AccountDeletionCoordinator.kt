package com.example.madverse.core.data.account

import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.data.repository.CloudSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Never infer remote identity deletion from a null current user or a failed token refresh. */
class AccountDeletionCoordinator(
    private val auth: AuthRepository,
    private val sync: CloudSyncService,
    private val journal: AccountDeletionJournal
) {
    suspend fun delete(uid: String): Result<Unit> {
        try {
            require(uid.isNotBlank() && uid != "guest") { "Sign in to the same account to retry deletion. An absent session does not confirm deletion." }
            val previous = journal.getJournalEntry(uid)?.stage
            if (previous == DeletionStage.REMOTE_AUTH_DELETED || previous == DeletionStage.REMOTE_DATA_DELETED || previous == DeletionStage.LOCAL_DATA_CLEANED) {
                error("Earlier deletion has no verified cloud purge. Local data and journal are preserved; request administrator verification of remote data.")
            }
            if (previous != DeletionStage.REMOTE_AUTH_CONFIRMED && previous != DeletionStage.ACCOUNT_DATA_CLEANED) {
                check(auth.currentUserId == uid) { "Sign in to the same account to retry. Remote deletion is unconfirmed." }
                journal.recordStage(uid, DeletionStage.STARTED)
                sync.stopUserSession()
                // Owner-scoped Firestore rules require the identity to still exist during the purge.
                sync.purgeRemoteUserData(uid).getOrThrow()
                journal.recordStage(uid, DeletionStage.CLOUD_PURGED)
                check(auth.currentUserId == uid) { "Session changed. Identity deletion is unconfirmed; sign in to the same account to retry." }
                journal.recordStage(uid, DeletionStage.AUTH_DELETE_REQUESTED)
                auth.deleteAccountForUser(uid).getOrThrow()
                // Once the success is observed, persist it even if the screen is immediately cancelled.
                withContext(NonCancellable) { journal.recordStage(uid, DeletionStage.REMOTE_AUTH_CONFIRMED) }
            }
            sync.deleteAllUserData(uid).getOrThrow()
            journal.recordStage(uid, DeletionStage.ACCOUNT_DATA_CLEANED)
            journal.recordStage(uid, DeletionStage.COMPLETED)
            return Result.success(Unit)
        } catch (e: CancellationException) {
            throw e // Requested/ambiguous stage survives cancellation and process death.
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
