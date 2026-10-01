package com.example.madverse.core.data.repository

import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for local Favorites storage.
 */
interface FavoritesRepository {
    fun getFavoriteMovies(): Flow<List<Movie>>
    fun isFavorite(movieId: Int): Flow<Boolean>
    suspend fun addToFavorites(movie: Movie)
    suspend fun removeFromFavorites(movieId: Int)
}
