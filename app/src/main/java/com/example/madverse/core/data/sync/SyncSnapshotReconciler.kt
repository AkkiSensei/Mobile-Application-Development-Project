package com.example.madverse.core.data.sync

import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.FavoriteEntity
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.database.entity.WatchlistEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** All reads deciding a reconciliation and its writes share the same Room transaction. */
internal class SyncSnapshotReconciler(
    private val favorites: FavoriteDao,
    private val watchlist: WatchlistDao,
    private val preferences: UserPreferenceDao,
    private val pending: PendingSyncDao,
    private val transactions: DatabaseTransactionRunner
) {
    private suspend fun checkSession(isCurrent: suspend () -> Boolean) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("Snapshot session is no longer active")
    }

    suspend fun favorites(uid: String, remote: List<FavoriteEntity>, isCurrent: suspend () -> Boolean) =
        transactions.runInTransaction {
            checkSession(isCurrent)
            val protected = pending.getPendingActionsForUser(uid).filter { it.entityType == "FAVORITE" }.map { it.itemId }.toSet()
            val local = favorites.getAllFavoritesOnce(uid).associateBy { it.movieId }
            val remoteIds = remote.map { it.movieId }.toSet()
            for (row in remote) {
                if (row.movieId.toString() !in protected && local[row.movieId] != row) favorites.insertFavorite(row.copy(userId = uid))
            }
            for (row in local.values) {
                if (row.movieId !in remoteIds && row.movieId.toString() !in protected) favorites.deleteFavoriteById(row.movieId, uid)
            }
            checkSession(isCurrent) // A session switch during a suspending DAO call rolls back the transaction.
        }

    suspend fun watchlist(uid: String, remote: List<WatchlistEntity>, isCurrent: suspend () -> Boolean) =
        transactions.runInTransaction {
            checkSession(isCurrent)
            val protected = pending.getPendingActionsForUser(uid).filter { it.entityType == "WATCHLIST" }.map { it.itemId }.toSet()
            val local = watchlist.getAllWatchlistOnce(uid).associateBy { it.movieId }
            val remoteIds = remote.map { it.movieId }.toSet()
            for (row in remote) {
                if (row.movieId.toString() !in protected && local[row.movieId] != row) watchlist.insertMovie(row.copy(userId = uid))
            }
            for (row in local.values) {
                if (row.movieId !in remoteIds && row.movieId.toString() !in protected) watchlist.deleteMovieById(row.movieId, uid)
            }
            checkSession(isCurrent)
        }

    suspend fun preferences(uid: String, remote: UserPreferenceEntity?, isCurrent: suspend () -> Boolean) =
        transactions.runInTransaction {
            checkSession(isCurrent)
            if (pending.getPendingActionsForUser(uid).none { it.entityType == "PREFERENCE" }) {
                val local = preferences.getUserPreferenceProfileOnce(uid)
                if (remote == null) {
                    if (local != null) preferences.clearUserPreferenceProfile(uid)
                } else if (local != remote) {
                    preferences.saveUserPreferenceProfile(remote.copy(userId = uid))
                }
            }
            checkSession(isCurrent)
        }
}
