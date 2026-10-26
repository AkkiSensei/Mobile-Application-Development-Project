package com.example.madverse.core.data.account

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stages in the durable account deletion state machine.
 */
enum class DeletionStage {
    IDLE,
    STARTED,
    CLOUD_PURGED,
    AUTH_DELETE_REQUESTED,
    REMOTE_AUTH_CONFIRMED,
    ACCOUNT_DATA_CLEANED,
    REMOTE_AUTH_DELETED,
    REMOTE_DATA_DELETED,
    LOCAL_DATA_CLEANED,
    COMPLETED
}

/**
 * Journal entry recording the active deletion state for a user.
 */
data class DeletionJournalEntry(
    val userId: String,
    val stage: DeletionStage,
    val timestamp: Long = System.currentTimeMillis(),
    val lastError: String? = null
)

/**
 * Contract for durable account deletion journaling.
 * Prevents premature local data wipe when remote auth deletion fails or requires re-authentication.
 */
interface AccountDeletionJournal {
    suspend fun recordStage(userId: String, stage: DeletionStage, error: String? = null)
    suspend fun getJournalEntry(userId: String): DeletionJournalEntry?
    suspend fun isDeletionPending(userId: String): Boolean
    suspend fun clearJournal(userId: String)
    suspend fun pendingEntries(): List<DeletionJournalEntry>
}

/**
 * Persistent SharedPreferences-backed implementation surviving process death and app restarts.
 */
@Singleton
class SharedPreferencesAccountDeletionJournal @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AccountDeletionJournal {

    private val prefs = context.getSharedPreferences("madverse_account_deletion_journal", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun recordStage(userId: String, stage: DeletionStage, error: String?) = mutex.withLock {
        check(prefs.edit()
            .putString("stage_$userId", stage.name)
            .putLong("timestamp_$userId", System.currentTimeMillis())
            .putString("error_$userId", error)
            .commit()) { "Could not persist deletion journal" }
    }

    override suspend fun getJournalEntry(userId: String): DeletionJournalEntry? = mutex.withLock {
        val stageName = prefs.getString("stage_$userId", null) ?: return null
        val stage = try {
            DeletionStage.valueOf(stageName)
        } catch (_: Exception) {
            DeletionStage.STARTED
        }
        val timestamp = prefs.getLong("timestamp_$userId", 0L)
        val error = prefs.getString("error_$userId", null)
        DeletionJournalEntry(userId, stage, timestamp, error)
    }

    override suspend fun isDeletionPending(userId: String): Boolean = mutex.withLock {
        val stageName = prefs.getString("stage_$userId", null) ?: return false
        val stage = try {
            DeletionStage.valueOf(stageName)
        } catch (_: Exception) {
            DeletionStage.STARTED
        }
        stage != DeletionStage.IDLE && stage != DeletionStage.COMPLETED
    }

    override suspend fun clearJournal(userId: String) = mutex.withLock {
        check(prefs.edit()
            .remove("stage_$userId")
            .remove("timestamp_$userId")
            .remove("error_$userId")
            .commit()) { "Could not persist deletion journal" }
    }

    override suspend fun pendingEntries(): List<DeletionJournalEntry> {
        val ids = mutex.withLock { prefs.all.keys.filter { it.startsWith("stage_") }.map { it.removePrefix("stage_") } }
        return ids.mapNotNull { getJournalEntry(it) }.filter { it.stage != DeletionStage.IDLE && it.stage != DeletionStage.COMPLETED }
    }
}

/**
 * Lightweight in-memory journal for unit testing.
 */
class InMemoryAccountDeletionJournal : AccountDeletionJournal {
    private val entries = mutableMapOf<String, DeletionJournalEntry>()
    private val mutex = Mutex()

    override suspend fun recordStage(userId: String, stage: DeletionStage, error: String?) = mutex.withLock {
        entries[userId] = DeletionJournalEntry(
            userId = userId,
            stage = stage,
            timestamp = System.currentTimeMillis(),
            lastError = error
        )
    }

    override suspend fun getJournalEntry(userId: String): DeletionJournalEntry? = mutex.withLock {
        entries[userId]
    }

    override suspend fun isDeletionPending(userId: String): Boolean = mutex.withLock {
        val entry = entries[userId] ?: return false
        entry.stage != DeletionStage.IDLE && entry.stage != DeletionStage.COMPLETED
    }

    override suspend fun clearJournal(userId: String) = mutex.withLock {
        entries.remove(userId)
        Unit
    }

    override suspend fun pendingEntries(): List<DeletionJournalEntry> = mutex.withLock {
        entries.values.filter { it.stage != DeletionStage.IDLE && it.stage != DeletionStage.COMPLETED }
    }
}
