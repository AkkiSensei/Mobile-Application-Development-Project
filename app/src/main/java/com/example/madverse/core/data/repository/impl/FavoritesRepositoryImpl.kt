package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.entity.FavoriteEntity
import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [FavoritesRepository].
 *
 * Favorite movies are persisted locally in the `favorite_movies` table.
 * All list and membership queries are reactive [Flow] streams so the UI
 * stays in sync automatically when the database is mutated.
 */
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao
) : FavoritesRepository {

    override fun getFavoriteMovies(): Flow<List<Movie>> =
        favoriteDao.getAllFavorites().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun isFavorite(movieId: Int): Flow<Boolean> =
        favoriteDao.isFavorite(movieId)

    override suspend fun isFavoriteOnce(movieId: Int): Boolean =
        favoriteDao.isFavoriteOnce(movieId)

    override suspend fun getFavoriteById(movieId: Int): Movie? =
        favoriteDao.getFavoriteById(movieId)?.toDomain()

    override suspend fun addToFavorites(movie: Movie) {
        favoriteDao.insertFavorite(FavoriteEntity.fromDomain(movie))
    }

    override suspend fun removeFromFavorites(movieId: Int) {
        favoriteDao.deleteFavoriteById(movieId)
    }
}
