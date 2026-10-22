package com.example.madverse.core.data.repository

import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repository contract for local Favorites storage.
 */
interface FavoritesRepository {
    fun getFavoriteMovies(): Flow<List<Movie>>
    fun isFavorite(movieId: Int): Flow<Boolean>
    suspend fun isFavoriteOnce(movieId: Int): Boolean = isFavorite(movieId).first()
    suspend fun getFavoriteById(movieId: Int): Movie? = null
    suspend fun addToFavorites(movie: Movie)
    suspend fun removeFromFavorites(movieId: Int)
}
