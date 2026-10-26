package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.DefaultSessionManager
import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.NoOpCloudSyncService
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.data.sync.SyncPayloadSerializer
import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.DirectTransactionRunner
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.NoOpPendingSyncDao
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.entity.FavoriteEntity
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.model.Movie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed and Firestore-synchronized implementation of [FavoritesRepository].
 *
 * Local mutations and their sync-queue entries are executed in atomic Room transactions.
 * A failed queue insertion rolls back the local mutation to ensure complete state consistency.
 * Exact-action acknowledgement only occurs after confirmed remote completion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val sessionManager: SessionManager,
    private val syncService: CloudSyncService,
    private val pendingSyncDao: PendingSyncDao,
    private val transactionRunner: DatabaseTransactionRunner
) : FavoritesRepository {

    // 3-arg constructor preserving backward compatibility for isolated unit tests
    constructor(
        favoriteDao: FavoriteDao,
        sessionManager: SessionManager,
        syncService: CloudSyncService
    ) : this(
        favoriteDao = favoriteDao,
        sessionManager = sessionManager,
        syncService = syncService,
        pendingSyncDao = NoOpPendingSyncDao(),
        transactionRunner = DirectTransactionRunner()
    )

    // 1-arg constructor preserving backward compatibility for isolated unit tests
    constructor(favoriteDao: FavoriteDao) : this(
        favoriteDao = favoriteDao,
        sessionManager = DefaultSessionManager(),
        syncService = NoOpCloudSyncService(),
        pendingSyncDao = NoOpPendingSyncDao(),
        transactionRunner = DirectTransactionRunner()
    )

    override fun getFavoriteMovies(): Flow<List<Movie>> =
        sessionManager.currentUserIdFlow.flatMapLatest { userId ->
            favoriteDao.getAllFavorites(userId).map { entities ->
                entities.map { it.toDomain() }
            }
        }

    override fun isFavorite(movieId: Int): Flow<Boolean> =
        sessionManager.currentUserIdFlow.flatMapLatest { userId ->
            favoriteDao.isFavorite(movieId, userId)
        }

    override suspend fun isFavoriteOnce(movieId: Int): Boolean {
        val userId = sessionManager.currentUserId
        return favoriteDao.isFavoriteOnce(movieId, userId)
    }

    override suspend fun getFavoriteById(movieId: Int): Movie? {
        val userId = sessionManager.currentUserId
        return favoriteDao.getFavoriteById(movieId, userId)?.toDomain()
    }

    override suspend fun addToFavorites(movie: Movie) {
        val userId = sessionManager.currentUserId
        transactionRunner.runInTransaction {
            favoriteDao.insertFavorite(FavoriteEntity.fromDomain(movie, userId = userId))
            if (userId != "guest") {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "UPSERT",
                        entityType = "FAVORITE",
                        itemId = movie.id.toString(),
                        payloadJson = SyncPayloadSerializer.movieToJson(movie)
                    )
                )
            }
        }
        if (userId != "guest") {
            syncService.processPendingSyncActions(userId)
        }
    }

    override suspend fun removeFromFavorites(movieId: Int) {
        val userId = sessionManager.currentUserId
        transactionRunner.runInTransaction {
            favoriteDao.deleteFavoriteById(movieId, userId)
            if (userId != "guest") {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "DELETE",
                        entityType = "FAVORITE",
                        itemId = movieId.toString(),
                        payloadJson = null
                    )
                )
            }
        }
        if (userId != "guest") {
            syncService.processPendingSyncActions(userId)
        }
    }
}
