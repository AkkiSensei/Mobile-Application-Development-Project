package com.example.madverse.core.data.repository

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.model.CastMember
import com.example.madverse.core.model.Genre
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.VideoTrailer
import kotlinx.coroutines.flow.Flow

/**
 * Primary repository contract for TMDB movie discovery, details, credits, search, and trailers.
 */
interface MovieRepository {
    fun getTrendingMovies(page: Int = 1): Flow<Resource<List<Movie>>>
    fun getTopRatedMovies(page: Int = 1): Flow<Resource<List<Movie>>>
    fun getUpcomingMovies(page: Int = 1): Flow<Resource<List<Movie>>>
    fun searchMovies(query: String, page: Int = 1, releaseYear: Int? = null): Flow<Resource<List<Movie>>>
    fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>>
    fun getMovieCredits(movieId: Int): Flow<Resource<List<CastMember>>>
    fun getMovieVideos(movieId: Int): Flow<Resource<List<VideoTrailer>>>
    fun getMovieWatchProviders(movieId: Int, countryCode: String = "US"): Flow<Resource<MovieWatchProviders>>
    fun getSimilarMovies(movieId: Int, page: Int = 1): Flow<Resource<List<Movie>>>
    fun getGenres(): Flow<Resource<List<Genre>>>
    fun discoverCandidateMovies(
        genreIds: Set<Int>,
        startYear: Int? = null,
        endYear: Int? = null,
        minRating: Double? = null,
        page: Int = 1
    ): Flow<Resource<List<Movie>>>
}
