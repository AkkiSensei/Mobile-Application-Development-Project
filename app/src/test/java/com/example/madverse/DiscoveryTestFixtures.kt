package com.example.madverse

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal fun discoveryMovie(id: Int = 1) = Movie(id, "Movie $id", "", null, null, "2020-01-01", 8.0, 100,
    listOf(28), 50.0, "en", 110)

internal open class DiscoveryTestRepository : MovieRepository {
    override fun getTrendingMovies(page: Int) = flowOf(Resource.Success(emptyList<Movie>()))
    override fun getTopRatedMovies(page: Int) = getTrendingMovies(page)
    override fun getUpcomingMovies(page: Int) = getTrendingMovies(page)
    override fun searchMovies(query: String, page: Int, releaseYear: Int?): Flow<Resource<List<Movie>>> = getTrendingMovies(page)
    override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> = flowOf(Resource.Error("Not available"))
    override fun getMovieCredits(movieId: Int) = flowOf(Resource.Success(emptyList<CastMember>()))
    override fun getMovieVideos(movieId: Int) = flowOf(Resource.Success(emptyList<VideoTrailer>()))
    override fun getMovieWatchProviders(movieId: Int, countryCode: String): Flow<Resource<MovieWatchProviders>> = flowOf(Resource.Error("Not available"))
    override fun getSimilarMovies(movieId: Int, page: Int) = getTrendingMovies(page)
    override fun getGenres(): Flow<Resource<List<Genre>>> = flowOf(Resource.Success(listOf(Genre(28, "Action"))))
    override fun discoverCandidateMovies(genreIds: Set<Int>, startYear: Int?, endYear: Int?, minRating: Double?, page: Int): Flow<Resource<List<Movie>>> = getTrendingMovies(page)
}
