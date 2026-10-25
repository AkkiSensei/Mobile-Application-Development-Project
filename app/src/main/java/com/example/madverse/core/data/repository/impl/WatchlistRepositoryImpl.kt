package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.DefaultSessionManager
import com.example.madverse.core.data.repository.NoOpCloudSyncService
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.data.sync.SyncPayloadSerializer
import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.DirectTransactionRunner
import com.example.madverse.core.database.dao.NoOpPendingSyncDao
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.database.entity.WatchlistEntity
import com.example.madverse.core.model.Movie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed and Firestore-synchronized implementation of [WatchlistRepository].
 *
 * Local mutations and their sync-queue entries are executed in atomic Room transactions.
 * A failed queue insertion rolls back the local mutation to ensure complete state consistency.
 * Exact-action acknowledgement only occurs after confirmed remote completion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WatchlistRepositoryImpl @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val sessionManager: SessionManager,
    private val syncService: CloudSyncService,
    private val pendingSyncDao: PendingSyncDao,
    private val transactionRunner: DatabaseTransactionRunner
) : WatchlistRepository {

    // 3-arg constructor preserving backward compatibility for isolated unit tests
    constructor(
        watchlistDao: WatchlistDao,
        sessionManager: SessionManager,
        syncService: CloudSyncService
    ) : this(
        watchlistDao = watchlistDao,
        sessionManager = sessionManager,
        syncService = syncService,
        pendingSyncDao = NoOpPendingSyncDao(),
        transactionRunner = DirectTransactionRunner()
    )

    // 1-arg constructor preserving backward compatibility for isolated unit tests
    constructor(watchlistDao: WatchlistDao) : this(
        watchlistDao = watchlistDao,
        sessionManager = DefaultSessionManager(),
        syncService = NoOpCloudSyncService(),
        pendingSyncDao = NoOpPendingSyncDao(),
        transactionRunner = DirectTransactionRunner()
    )

    override fun getWatchlistMovies(): Flow<List<Movie>> =
        sessionManager.currentUserIdFlow.flatMapLatest { userId ->
            watchlistDao.getAllWatchlistMovies(userId).map { entities ->
                entities.map { it.toDomain() }
            }
        }

    override fun isMovieInWatchlist(movieId: Int): Flow<Boolean> =
        sessionManager.currentUserIdFlow.flatMapLatest { userId ->
            watchlistDao.isMovieInWatchlist(movieId, userId)
        }

    override suspend fun isMovieInWatchlistOnce(movieId: Int): Boolean {
        val userId = sessionManager.currentUserId
        return watchlistDao.isMovieInWatchlistOnce(movieId, userId)
    }

    override suspend fun getWatchlistMovieById(movieId: Int): Movie? {
        val userId = sessionManager.currentUserId
        return watchlistDao.getWatchlistMovieById(movieId, userId)?.toDomain()
    }

    override suspend fun addToWatchlist(movie: Movie) {
        val userId = sessionManager.currentUserId
        var actionId = 0L
        transactionRunner.runInTransaction {
            watchlistDao.insertMovie(WatchlistEntity.fromDomain(movie, userId = userId))
            if (userId != "guest") {
                actionId = pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "UPSERT",
                        entityType = "WATCHLIST",
                        itemId = movie.id.toString(),
                        payloadJson = SyncPayloadSerializer.movieToJson(movie)
                    )
                )
            }
        }
        if (userId != "guest") {
            val result = syncService.pushWatchlist(userId, movie, enqueueOnFailure = false)
            if (result.isSuccess && actionId != 0L) {
                pendingSyncDao.deleteActionById(actionId)
            }
            syncService.processPendingSyncActions(userId)
        }
    }

    override suspend fun removeFromWatchlist(movieId: Int) {
        val userId = sessionManager.currentUserId
        var actionId = 0L
        transactionRunner.runInTransaction {
            watchlistDao.deleteMovieById(movieId, userId)
            if (userId != "guest") {
                actionId = pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "DELETE",
                        entityType = "WATCHLIST",
                        itemId = movieId.toString(),
                        payloadJson = null
                    )
                )
            }
        }
        if (userId != "guest") {
            val result = syncService.deleteWatchlist(userId, movieId, enqueueOnFailure = false)
            if (result.isSuccess && actionId != 0L) {
                pendingSyncDao.deleteActionById(actionId)
            }
            syncService.processPendingSyncActions(userId)
        }
    }
}
