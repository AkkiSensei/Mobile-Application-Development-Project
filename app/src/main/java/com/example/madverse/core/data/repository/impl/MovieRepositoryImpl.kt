package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.model.CastMember
import com.example.madverse.core.model.Genre
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.VideoTrailer
import com.example.madverse.core.network.api.TmdbApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [MovieRepository].
 *
 * All calls are wrapped in a safeApiCall helper that converts network
 * exceptions into [Resource.Error] without leaking stack traces.
 * DTOs are mapped to domain models before emission so callers never
 * depend on network layer types.
 *
 * Public genre metadata is cached for this process; other requests follow HTTP freshness.
 * Private library state is never cached here:
 * Library persistence (Favorites / Watchlist) lives in [FavoritesRepositoryImpl]
 * and [WatchlistRepositoryImpl] which operate on their own Room DAOs.
 */
@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val tmdbApi: TmdbApi
) : MovieRepository {

    // -------------------------------------------------------------------------
    // Discovery / Listing
    // -------------------------------------------------------------------------

    override fun getTrendingMovies(page: Int): Flow<Resource<List<Movie>>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.getTrendingMovies(page)
                .results
                .orEmpty()
                .map { it.toDomain() }
        })
    }

    override fun getTopRatedMovies(page: Int): Flow<Resource<List<Movie>>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.getTopRatedMovies(page)
                .results
                .orEmpty()
                .map { it.toDomain() }
        })
    }

    override fun getUpcomingMovies(page: Int): Flow<Resource<List<Movie>>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.getUpcomingMovies(page)
                .results
                .orEmpty()
                .map { it.toDomain() }
        })
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    override fun searchMovies(
        query: String,
        page: Int,
        releaseYear: Int?
    ): Flow<Resource<List<Movie>>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.searchMovies(
                query = query,
                page = page,
                includeAdult = false,
                primaryReleaseYear = releaseYear
            )
                .results
                .orEmpty()
                .map { it.toDomain() }
        })
    }

    // -------------------------------------------------------------------------
    // Movie Details, Credits, Videos, Providers
    // -------------------------------------------------------------------------

    override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.getMovieDetails(movieId).toDomain()
        })
    }

    override fun getMovieCredits(movieId: Int): Flow<Resource<List<CastMember>>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.getMovieCredits(movieId)
                .cast
                .orEmpty()
                .map { it.toDomain() }
                .sortedBy { it.order }
                .take(MAX_CAST_MEMBERS)
        })
    }

    override fun getMovieVideos(movieId: Int): Flow<Resource<List<VideoTrailer>>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.getMovieVideos(movieId)
                .results
                .orEmpty()
                .mapNotNull { it.toDomain() }
                .filter { it.isEmbeddableYouTubeTrailer }
                .sortedByDescending { it.isOfficial }
        })
    }

    override fun getMovieWatchProviders(
        movieId: Int,
        countryCode: String
    ): Flow<Resource<MovieWatchProviders>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.getMovieWatchProviders(movieId).toDomain(movieId, countryCode)
        })
    }

    // -------------------------------------------------------------------------
    // Related Movies
    // -------------------------------------------------------------------------

    override fun getSimilarMovies(movieId: Int, page: Int): Flow<Resource<List<Movie>>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            tmdbApi.getSimilarMovies(movieId, page)
                .results
                .orEmpty()
                .map { it.toDomain() }
        })
    }

    // -------------------------------------------------------------------------
    // Genres
    // -------------------------------------------------------------------------

    @Volatile
    private var cachedGenres: List<Genre>? = null
    private val genresMutex = Mutex()

    override fun getGenres(): Flow<Resource<List<Genre>>> = flow {
        val cached = cachedGenres
        if (cached != null && cached.isNotEmpty()) {
            emit(Resource.Success(cached))
            return@flow
        }
        emit(Resource.Loading)
        // Recheck after acquiring the lock: a concurrent collector may have populated it.
        // The owning coroutine performs the request; cancellation releases the lock for waiters.
        val result = genresMutex.withLock {
            cachedGenres?.takeIf { it.isNotEmpty() }?.let { Resource.Success(it) }
                ?: safeApiCall {
                    tmdbApi.getMovieGenres().genres.orEmpty().map { it.toDomain() }
                }.also { result ->
                    if (result is Resource.Success && result.data.isNotEmpty()) cachedGenres = result.data
                }
        }
        emit(result)
    }

    // -------------------------------------------------------------------------
    // Recommendation Candidate Discovery
    // -------------------------------------------------------------------------

    /**
     * Fetches a candidate pool for the recommendation engine.
     * Passes genre IDs as a comma-separated string and applies optional
     * year range and minimum rating filters.
     */
    override fun discoverCandidateMovies(
        genreIds: Set<Int>,
        startYear: Int?,
        endYear: Int?,
        minRating: Double?,
        page: Int
    ): Flow<Resource<List<Movie>>> = flow {
        emit(Resource.Loading)
        emit(safeApiCall {
            val genreParam = if (genreIds.isNotEmpty()) genreIds.joinToString(",") else null
            val releaseDateGte = startYear?.let { "$it-01-01" }
            val releaseDateLte = endYear?.let { "$it-12-31" }

            val movies = tmdbApi.discoverMovies(
                withGenres = genreParam,
                releaseDateGte = releaseDateGte,
                releaseDateLte = releaseDateLte,
                minVoteAverage = minRating,
                sortBy = "popularity.desc",
                page = page
            )
                .results
                .orEmpty()
                .map { it.toDomain() }

            if (minRating != null && minRating > 0.0) {
                movies.filter { it.voteAverage >= minRating }
            } else {
                movies
            }
        })
    }

    // -------------------------------------------------------------------------
    // Internal Error Handling
    // -------------------------------------------------------------------------

    /**
     * Wraps an API call and maps any exception to a typed [Resource.Error].
     * Distinguishes offline/connectivity failures from server-side API errors.
     */
    private inline fun <T> safeApiCall(block: () -> T): Resource<T> {
        return try {
            Resource.Success(block())
        } catch (e: IOException) {
            Resource.Error(
                message = "Network error: Check your internet connection.",
                cause = e
            )
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val body = e.response()?.errorBody()?.string()
            when (code) {
                401 -> Resource.Error("TMDB authentication failed. Check your API key configuration.", e)
                404 -> Resource.Error("Movie not found on TMDB (404).", e)
                429 -> Resource.Error("TMDB rate limit exceeded. Please retry in a moment.", e)
                in 500..599 -> Resource.Error("TMDB service is temporarily unavailable (HTTP $code).", e)
                else -> Resource.Error("Unexpected API error (HTTP $code): ${body ?: "No details"}", e)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error("An unexpected error occurred: ${e.message}", e)
        }
    }

    private companion object {
        /** Limit cast list to top-billed members for UI performance. */
        private const val MAX_CAST_MEMBERS = 20
    }
}
