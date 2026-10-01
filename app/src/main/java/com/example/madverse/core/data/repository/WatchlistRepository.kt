package com.example.madverse.core.data.repository

import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for local Watchlist storage.
 */
interface WatchlistRepository {
    fun getWatchlistMovies(): Flow<List<Movie>>
    fun isMovieInWatchlist(movieId: Int): Flow<Boolean>
    suspend fun addToWatchlist(movie: Movie)
    suspend fun removeFromWatchlist(movieId: Int)
}
