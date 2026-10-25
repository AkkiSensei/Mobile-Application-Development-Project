package com.example.madverse.core.data.repository

import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for managing local recently viewed movies.
 * Deduplicated by TMDB movie ID, ordered by actual last-viewed time, and bounded to ~20 movies.
 * Kept local-only per MADVerse offline-first architecture.
 */
interface RecentlyViewedRepository {
    fun getRecentlyViewed(limit: Int = 20): Flow<List<Movie>>
    suspend fun getRecentlyViewedOnce(limit: Int = 20): List<Movie>
    suspend fun recordMovieView(movie: Movie)
    suspend fun clearHistory()
}
