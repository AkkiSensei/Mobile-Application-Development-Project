package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.WatchlistEntity
import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [WatchlistRepository].
 *
 * Watchlist movies are persisted locally in the `watchlist_movies` table.
 * All list and membership queries are reactive [Flow] streams so the UI
 * stays in sync automatically when the database is mutated.
 */
@Singleton
class WatchlistRepositoryImpl @Inject constructor(
    private val watchlistDao: WatchlistDao
) : WatchlistRepository {

    override fun getWatchlistMovies(): Flow<List<Movie>> =
        watchlistDao.getAllWatchlistMovies().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun isMovieInWatchlist(movieId: Int): Flow<Boolean> =
        watchlistDao.isMovieInWatchlist(movieId)

    override suspend fun isMovieInWatchlistOnce(movieId: Int): Boolean =
        watchlistDao.isMovieInWatchlistOnce(movieId)

    override suspend fun getWatchlistMovieById(movieId: Int): Movie? =
        watchlistDao.getWatchlistMovieById(movieId)?.toDomain()

    override suspend fun addToWatchlist(movie: Movie) {
        watchlistDao.insertMovie(WatchlistEntity.fromDomain(movie))
    }

    override suspend fun removeFromWatchlist(movieId: Int) {
        watchlistDao.deleteMovieById(movieId)
    }
}
