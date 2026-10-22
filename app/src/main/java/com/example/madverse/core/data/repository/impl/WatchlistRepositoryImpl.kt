package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.DefaultSessionManager
import com.example.madverse.core.data.repository.NoOpCloudSyncService
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.database.dao.WatchlistDao
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
 * All queries are reactive [Flow] streams bound to [SessionManager.currentUserIdFlow],
 * guaranteeing total data isolation across guest sessions and authenticated users.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WatchlistRepositoryImpl @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val sessionManager: SessionManager,
    private val syncService: CloudSyncService
) : WatchlistRepository {

    // Secondary constructor preserving backward compatibility for isolated unit tests
    constructor(watchlistDao: WatchlistDao) : this(
        watchlistDao = watchlistDao,
        sessionManager = DefaultSessionManager(),
        syncService = NoOpCloudSyncService()
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
        watchlistDao.insertMovie(WatchlistEntity.fromDomain(movie, userId = userId))
        syncService.pushWatchlist(userId, movie)
    }

    override suspend fun removeFromWatchlist(movieId: Int) {
        val userId = sessionManager.currentUserId
        watchlistDao.deleteMovieById(movieId, userId)
        syncService.deleteWatchlist(userId, movieId)
    }
}
