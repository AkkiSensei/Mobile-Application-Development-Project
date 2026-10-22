package com.example.madverse.fakes

import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.model.MergeSummary
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.SyncState
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCloudSyncService : CloudSyncService {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    var activeSessionUserId: String? = null
    val syncedFavorites = mutableMapOf<String, MutableMap<Int, Movie>>()
    val syncedWatchlist = mutableMapOf<String, MutableMap<Int, Movie>>()
    val syncedPreferences = mutableMapOf<String, UserPreferenceProfile>()

    var shouldFailNextOperation: Boolean = false
    var failureException: Exception = Exception("Simulated cloud sync error")

    fun setSyncState(state: SyncState) {
        _syncState.value = state
    }

    override fun startUserSession(userId: String) {
        activeSessionUserId = userId
        _syncState.value = SyncState.Synced
    }

    override fun stopUserSession() {
        activeSessionUserId = null
        _syncState.value = SyncState.Idle
    }

    override suspend fun pushFavorite(userId: String, movie: Movie): Result<Unit> {
        if (shouldFailNextOperation) {
            _syncState.value = SyncState.OfflinePending
            return Result.failure(failureException)
        }
        syncedFavorites.getOrPut(userId) { mutableMapOf() }[movie.id] = movie
        _syncState.value = SyncState.Synced
        return Result.success(Unit)
    }

    override suspend fun deleteFavorite(userId: String, movieId: Int): Result<Unit> {
        if (shouldFailNextOperation) {
            _syncState.value = SyncState.OfflinePending
            return Result.failure(failureException)
        }
        syncedFavorites[userId]?.remove(movieId)
        _syncState.value = SyncState.Synced
        return Result.success(Unit)
    }

    override suspend fun pushWatchlist(userId: String, movie: Movie): Result<Unit> {
        if (shouldFailNextOperation) {
            _syncState.value = SyncState.OfflinePending
            return Result.failure(failureException)
        }
        syncedWatchlist.getOrPut(userId) { mutableMapOf() }[movie.id] = movie
        _syncState.value = SyncState.Synced
        return Result.success(Unit)
    }

    override suspend fun deleteWatchlist(userId: String, movieId: Int): Result<Unit> {
        if (shouldFailNextOperation) {
            _syncState.value = SyncState.OfflinePending
            return Result.failure(failureException)
        }
        syncedWatchlist[userId]?.remove(movieId)
        _syncState.value = SyncState.Synced
        return Result.success(Unit)
    }

    override suspend fun pushPreferences(
        userId: String,
        profile: UserPreferenceProfile
    ): Result<Unit> {
        if (shouldFailNextOperation) {
            _syncState.value = SyncState.OfflinePending
            return Result.failure(failureException)
        }
        syncedPreferences[userId] = profile
        _syncState.value = SyncState.Synced
        return Result.success(Unit)
    }

    override suspend fun syncAll(userId: String): Result<Unit> {
        if (shouldFailNextOperation) {
            _syncState.value = SyncState.Error("Sync failed")
            return Result.failure(failureException)
        }
        _syncState.value = SyncState.Synced
        return Result.success(Unit)
    }

    override suspend fun mergeGuestDataIntoAccount(
        authenticatedUserId: String
    ): Result<MergeSummary> {
        if (shouldFailNextOperation) return Result.failure(failureException)
        val guestFavs = syncedFavorites["guest"] ?: emptyMap()
        val guestWatch = syncedWatchlist["guest"] ?: emptyMap()
        val guestPref = syncedPreferences["guest"]

        val userFavs = syncedFavorites.getOrPut(authenticatedUserId) { mutableMapOf() }
        var favMerged = 0
        guestFavs.forEach { (id, movie) ->
            if (!userFavs.containsKey(id)) {
                userFavs[id] = movie
                favMerged++
            }
        }

        val userWatch = syncedWatchlist.getOrPut(authenticatedUserId) { mutableMapOf() }
        var watchMerged = 0
        guestWatch.forEach { (id, movie) ->
            if (!userWatch.containsKey(id)) {
                userWatch[id] = movie
                watchMerged++
            }
        }

        var prefMerged = false
        if (guestPref != null && !syncedPreferences.containsKey(authenticatedUserId)) {
            syncedPreferences[authenticatedUserId] = guestPref
            prefMerged = true
        }

        return Result.success(
            MergeSummary(
                favoritesMerged = favMerged,
                watchlistMerged = watchMerged,
                preferencesMerged = prefMerged
            )
        )
    }

    override suspend fun deleteAllUserData(userId: String): Result<Unit> {
        syncedFavorites.remove(userId)
        syncedWatchlist.remove(userId)
        syncedPreferences.remove(userId)
        stopUserSession()
        return Result.success(Unit)
    }

    override suspend fun processPendingSyncActions(userId: String) {}
}
