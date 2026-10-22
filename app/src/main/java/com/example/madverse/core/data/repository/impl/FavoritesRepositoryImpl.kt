package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.DefaultSessionManager
import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.NoOpCloudSyncService
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.entity.FavoriteEntity
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
 * All queries are reactive [Flow] streams bound to [SessionManager.currentUserIdFlow],
 * guaranteeing total data isolation across guest sessions and authenticated users.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val sessionManager: SessionManager,
    private val syncService: CloudSyncService
) : FavoritesRepository {

    // Secondary constructor preserving backward compatibility for isolated unit tests
    constructor(favoriteDao: FavoriteDao) : this(
        favoriteDao = favoriteDao,
        sessionManager = DefaultSessionManager(),
        syncService = NoOpCloudSyncService()
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
        favoriteDao.insertFavorite(FavoriteEntity.fromDomain(movie, userId = userId))
        syncService.pushFavorite(userId, movie)
    }

    override suspend fun removeFromFavorites(movieId: Int) {
        val userId = sessionManager.currentUserId
        favoriteDao.deleteFavoriteById(movieId, userId)
        syncService.deleteFavorite(userId, movieId)
    }
}
