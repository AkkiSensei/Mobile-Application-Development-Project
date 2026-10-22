package com.example.madverse.core.model

/**
 * Cloud synchronization state exposed to the UI.
 */
sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data object Synced : SyncState
    data object OfflinePending : SyncState
    data class Error(val message: String) : SyncState
}
