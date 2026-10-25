package com.example.madverse.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.madverse.core.database.entity.PendingSyncEntity

@Dao
interface PendingSyncDao {

    @Query("SELECT * FROM pending_sync_actions WHERE user_id = :userId ORDER BY created_at ASC, id ASC")
    suspend fun getPendingActionsForUser(userId: String): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingSyncEntity): Long

    @Query("DELETE FROM pending_sync_actions WHERE id = :actionId")
    suspend fun deleteActionById(actionId: Long)

    @Query("DELETE FROM pending_sync_actions WHERE user_id = :userId")
    suspend fun deleteActionsForUser(userId: String)
}

/**
 * In-memory fallback/test implementation of [PendingSyncDao] for isolated unit tests.
 */
class NoOpPendingSyncDao : PendingSyncDao {
    private val actions = mutableListOf<PendingSyncEntity>()
    private var nextId = 1L

    override suspend fun getPendingActionsForUser(userId: String): List<PendingSyncEntity> {
        return actions.filter { it.userId == userId }.sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    override suspend fun insertAction(action: PendingSyncEntity): Long {
        val id = if (action.id == 0L) nextId++ else action.id
        val copy = action.copy(id = id)
        actions.removeAll { it.id == id }
        actions.add(copy)
        return id
    }

    override suspend fun deleteActionById(actionId: Long) {
        actions.removeAll { it.id == actionId }
    }

    override suspend fun deleteActionsForUser(userId: String) {
        actions.removeAll { it.userId == userId }
    }
}
