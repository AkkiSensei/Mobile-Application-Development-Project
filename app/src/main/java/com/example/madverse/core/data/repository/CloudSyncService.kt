package com.example.madverse.core.data.repository

import com.example.madverse.core.model.MergeSummary
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.SyncState
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cloud synchronization contract between local Room database and Cloud Firestore.
 */
interface CloudSyncService {
    val syncState: StateFlow<SyncState>

    fun startUserSession(userId: String)
    fun stopUserSession()

    // Compatibility enqueue methods: success means durably accepted, not remote acknowledgement.
    // enqueueOnFailure is retained for source compatibility; production always queues before sending.
    suspend fun pushFavorite(userId: String, movie: Movie, enqueueOnFailure: Boolean = true): Result<Unit>
    suspend fun deleteFavorite(userId: String, movieId: Int, enqueueOnFailure: Boolean = true): Result<Unit>
    suspend fun pushWatchlist(userId: String, movie: Movie, enqueueOnFailure: Boolean = true): Result<Unit>
    suspend fun deleteWatchlist(userId: String, movieId: Int, enqueueOnFailure: Boolean = true): Result<Unit>
    suspend fun pushPreferences(userId: String, profile: UserPreferenceProfile, enqueueOnFailure: Boolean = true): Result<Unit>

    suspend fun syncAll(userId: String): Result<Unit>
    suspend fun mergeGuestDataIntoAccount(authenticatedUserId: String): Result<MergeSummary>
    suspend fun purgeRemoteUserData(userId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Remote deletion is unavailable"))
    // Local cleanup only, permitted after journaled cloud purge AND confirmed Auth deletion.
    suspend fun deleteAllUserData(userId: String): Result<Unit>
    suspend fun processPendingSyncActions(userId: String)
}

/**
 * No-op cloud sync service used in isolated DAO unit tests.
 */
class NoOpCloudSyncService : CloudSyncService {
    override val syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.Idle)

    override fun startUserSession(userId: String) {}
    override fun stopUserSession() {}

    override suspend fun pushFavorite(userId: String, movie: Movie, enqueueOnFailure: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun deleteFavorite(userId: String, movieId: Int, enqueueOnFailure: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun pushWatchlist(userId: String, movie: Movie, enqueueOnFailure: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun deleteWatchlist(userId: String, movieId: Int, enqueueOnFailure: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun pushPreferences(userId: String, profile: UserPreferenceProfile, enqueueOnFailure: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun syncAll(userId: String): Result<Unit> = Result.success(Unit)
    override suspend fun mergeGuestDataIntoAccount(authenticatedUserId: String): Result<MergeSummary> =
        Result.success(MergeSummary())
    override suspend fun deleteAllUserData(userId: String): Result<Unit> = Result.success(Unit)
    override suspend fun processPendingSyncActions(userId: String) {}
}
