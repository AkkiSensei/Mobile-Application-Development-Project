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

    override suspend fun addToWatchlist(movie: Movie) {
        watchlistDao.insertMovie(movie.toWatchlistEntity())
    }

    override suspend fun removeFromWatchlist(movieId: Int) {
        watchlistDao.deleteMovieById(movieId)
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private fun Movie.toWatchlistEntity(): WatchlistEntity = WatchlistEntity(
        movieId = id,
        title = title,
        posterPath = posterUrl,
        backdropPath = backdropUrl,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        overview = overview,
        savedTimestamp = System.currentTimeMillis()
    )

    private fun WatchlistEntity.toDomain(): Movie = Movie(
        id = movieId,
        title = title,
        overview = overview ?: "",
        posterUrl = posterPath,
        backdropUrl = backdropPath,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        voteCount = 0,
        genreIds = emptyList(),
        popularity = 0.0
    )
}
