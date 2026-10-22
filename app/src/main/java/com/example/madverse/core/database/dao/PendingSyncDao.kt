package com.example.madverse.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.madverse.core.database.entity.PendingSyncEntity

@Dao
interface PendingSyncDao {

    @Query("SELECT * FROM pending_sync_actions WHERE user_id = :userId ORDER BY created_at ASC")
    suspend fun getPendingActionsForUser(userId: String): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingSyncEntity): Long

    @Query("DELETE FROM pending_sync_actions WHERE id = :actionId")
    suspend fun deleteActionById(actionId: Long)

    @Query("DELETE FROM pending_sync_actions WHERE user_id = :userId")
    suspend fun deleteActionsForUser(userId: String)
}
