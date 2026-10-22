package com.example.madverse.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks local mutations that require cloud synchronization when connectivity is restored.
 * Ensures offline actions (add/remove favorites, watchlist, preference changes) never disappear.
 */
@Entity(tableName = "pending_sync_actions")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "action_type")
    val actionType: String, // "UPSERT", "DELETE"

    @ColumnInfo(name = "entity_type")
    val entityType: String, // "FAVORITE", "WATCHLIST", "PREFERENCE"

    @ColumnInfo(name = "item_id")
    val itemId: String, // e.g. movieId or "current"

    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
