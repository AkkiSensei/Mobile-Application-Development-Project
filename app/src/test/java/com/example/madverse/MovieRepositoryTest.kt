package com.example.madverse

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.data.repository.impl.MovieRepositoryImpl
import com.example.madverse.core.network.api.TmdbApi
import com.example.madverse.core.network.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class MovieRepositoryTest {

    private fun createRepository(api: TmdbApi): MovieRepositoryImpl = MovieRepositoryImpl(api)

    private fun errorResponse(code: Int, body: String = "{}"): Response<MovieResponseDto> {
        val mediaType = "application/json".toMediaTypeOrNull()
        val responseBody = body.toResponseBody(mediaType)
        return Response.error(code, responseBody)
    }

    @Test
    fun cancellationException_isRethrownAndNotConvertedToResourceError() = runTest {
        val fakeApi = object : FakeBaseTmdbApi() {
            override suspend fun getTrendingMovies(page: Int): MovieResponseDto {
                throw CancellationException("Coroutine was cancelled")
            }
        }
        val repository = createRepository(fakeApi)

        var thrown = false
        try {
            repository.getTrendingMovies().toList()
        } catch (e: CancellationException) {
            thrown = true
            assertEquals("Coroutine was cancelled", e.message)
        }
        assertTrue("CancellationException must be rethrown and not swallowed", thrown)
    }

    @Test
    fun ioException_isMappedToOfflineResourceError() = runTest {
        val fakeApi = object : FakeBaseTmdbApi() {
            override suspend fun getTrendingMovies(page: Int): MovieResponseDto {
                throw IOException("Connection reset by peer")
            }
        }
        val repository = createRepository(fakeApi)
        val emissions = repository.getTrendingMovies().toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is Resource.Loading)
        assertTrue(emissions[1] is Resource.Error)

        val error = emissions[1] as Resource.Error
        assertTrue(error.message.contains("Network error"))
        assertTrue(error.cause is IOException)
    }

    @Test
    fun http401_mapsToAuthenticationFailedError() = runTest {
        val fakeApi = object : FakeBaseTmdbApi() {
            override suspend fun getTrendingMovies(page: Int): MovieResponseDto {
                throw HttpException(errorResponse(401))
            }
        }
        val repository = createRepository(fakeApi)
        val emissions = repository.getTrendingMovies().toList()

        val error = emissions[1] as Resource.Error
        assertTrue(error.message.contains("TMDB authentication failed"))
    }

    @Test
    fun http404_mapsToMovieNotFoundError() = runTest {
        val fakeApi = object : FakeBaseTmdbApi() {
            override suspend fun getMovieDetails(movieId: Int): MovieDetailsDto {
                throw HttpException(errorResponse(404))
            }
        }
        val repository = createRepository(fakeApi)
        val emissions = repository.getMovieDetails(99999).toList()

        val error = emissions[1] as Resource.Error
        assertTrue(error.message.contains("404"))
    }

    @Test
    fun http429_mapsToRateLimitError() = runTest {
        val fakeApi = object : FakeBaseTmdbApi() {
            override suspend fun getTrendingMovies(page: Int): MovieResponseDto {
                throw HttpException(errorResponse(429))
            }
        }
        val repository = createRepository(fakeApi)
        val emissions = repository.getTrendingMovies().toList()

        val error = emissions[1] as Resource.Error
        assertTrue(error.message.contains("rate limit"))
    }

    @Test
    fun http500_mapsToServiceUnavailableError() = runTest {
        val fakeApi = object : FakeBaseTmdbApi() {
            override suspend fun getTrendingMovies(page: Int): MovieResponseDto {
                throw HttpException(errorResponse(503))
            }
        }
        val repository = createRepository(fakeApi)
        val emissions = repository.getTrendingMovies().toList()

        val error = emissions[1] as Resource.Error
        assertTrue(error.message.contains("temporarily unavailable"))
    }

    @Test
    fun getTrendingMovies_emitsLoadingThenSuccessWithMappedDomain() = runTest {
        val fakeApi = object : FakeBaseTmdbApi() {
            override suspend fun getTrendingMovies(page: Int): MovieResponseDto {
                return MovieResponseDto(
                    page = 1,
                    results = listOf(
                        MovieDto(
                            id = 550, title = "Fight Club", overview = "Underground fight club",
                            posterPath = "/poster.jpg", backdropPath = "/backdrop.jpg",
                            releaseDate = "1999-10-15", voteAverage = 8.4, voteCount = 28000,
                            genreIds = listOf(18), popularity = 100.0, originalLanguage = "en"
                        )
                    ),
                    totalPages = 1,
                    totalResults = 1
                )
            }
        }
        val repository = createRepository(fakeApi)
        val emissions = repository.getTrendingMovies().toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is Resource.Loading)
        assertTrue(emissions[1] is Resource.Success)

        val movies = (emissions[1] as Resource.Success).data
        assertEquals(1, movies.size)
        assertEquals("Fight Club", movies[0].title)
        assertEquals(550, movies[0].id)
    }

    private open class FakeBaseTmdbApi : TmdbApi {
        override suspend fun getTrendingMovies(page: Int): MovieResponseDto =
            MovieResponseDto(1, emptyList(), 1, 0)
        override suspend fun getTopRatedMovies(page: Int): MovieResponseDto =
            MovieResponseDto(1, emptyList(), 1, 0)
        override suspend fun getUpcomingMovies(page: Int): MovieResponseDto =
            MovieResponseDto(1, emptyList(), 1, 0)
        override suspend fun searchMovies(query: String, page: Int, includeAdult: Boolean, primaryReleaseYear: Int?): MovieResponseDto =
            MovieResponseDto(1, emptyList(), 1, 0)
        override suspend fun getMovieDetails(movieId: Int): MovieDetailsDto =
            MovieDetailsDto(movieId, "Movie", null, "", null, null, null, null, 0.0, 0, emptyList(), "Released", 0L, 0L, "en")
        override suspend fun getMovieCredits(movieId: Int): CreditsDto = CreditsDto(movieId, emptyList())
        override suspend fun getMovieVideos(movieId: Int): VideosResponseDto = VideosResponseDto(movieId, emptyList())
        override suspend fun getMovieWatchProviders(movieId: Int): WatchProvidersResponseDto = WatchProvidersResponseDto(movieId, emptyMap())
        override suspend fun getSimilarMovies(movieId: Int, page: Int): MovieResponseDto = MovieResponseDto(1, emptyList(), 1, 0)
        override suspend fun getMovieGenres(): GenresResponseDto = GenresResponseDto(emptyList())
        override suspend fun discoverMovies(withGenres: String?, releaseDateGte: String?, releaseDateLte: String?, minVoteAverage: Double?, sortBy: String, page: Int): MovieResponseDto =
            MovieResponseDto(1, emptyList(), 1, 0)
    }
}
