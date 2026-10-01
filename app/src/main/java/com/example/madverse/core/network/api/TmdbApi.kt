package com.example.madverse.core.network.api

import com.example.madverse.core.network.model.CreditsDto
import com.example.madverse.core.network.model.GenresResponseDto
import com.example.madverse.core.network.model.MovieDetailsDto
import com.example.madverse.core.network.model.MovieResponseDto
import com.example.madverse.core.network.model.VideosResponseDto
import com.example.madverse.core.network.model.WatchProvidersResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for TMDB endpoints used by MADVerse.
 * Note: Authentication parameter (api_key) is injected automatically via AuthInterceptor.
 */
interface TmdbApi {

    @GET("trending/movie/day")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1
    ): MovieResponseDto

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = 1
    ): MovieResponseDto

    @GET("movie/upcoming")
    suspend fun getUpcomingMovies(
        @Query("page") page: Int = 1
    ): MovieResponseDto

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("primary_release_year") primaryReleaseYear: Int? = null
    ): MovieResponseDto

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int
    ): MovieDetailsDto

    @GET("movie/{movie_id}/credits")
    suspend fun getMovieCredits(
        @Path("movie_id") movieId: Int
    ): CreditsDto

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int
    ): VideosResponseDto

    @GET("movie/{movie_id}/watch/providers")
    suspend fun getMovieWatchProviders(
        @Path("movie_id") movieId: Int
    ): WatchProvidersResponseDto

    @GET("movie/{movie_id}/similar")
    suspend fun getSimilarMovies(
        @Path("movie_id") movieId: Int,
        @Query("page") page: Int = 1
    ): MovieResponseDto

    @GET("genre/movie/list")
    suspend fun getMovieGenres(): GenresResponseDto

    /**
     * Discover movies endpoint used for candidate pool generation by genre, rating, and release date.
     */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") withGenres: String? = null,
        @Query("primary_release_date.gte") releaseDateGte: String? = null,
        @Query("primary_release_date.lte") releaseDateLte: String? = null,
        @Query("vote_average.gte") minVoteAverage: Double? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): MovieResponseDto
}
