package com.example.madverse

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.model.CastMember
import com.example.madverse.core.model.Genre
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.VideoTrailer
import com.example.madverse.feature.discover.presentation.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests verifying [HomeViewModel] state management, repository integration,
 * error handling, empty handling, and retry flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var fakeMovieRepository: FakeMovieRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeMovieRepository = FakeMovieRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMovie(id: Int, title: String): Movie {
        return Movie(
            id = id,
            title = title,
            overview = "Overview of $title",
            posterUrl = "https://image.tmdb.org/t/p/w500/poster$id.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/backdrop$id.jpg",
            releaseDate = "2024-05-15",
            voteAverage = 8.5,
            voteCount = 1200,
            genreIds = listOf(28, 878),
            popularity = 95.0
        )
    }

    @Test
    fun init_loadsAllSectionsSuccessfully() = runTest(testDispatcher) {
        val trendingMovies = listOf(createMovie(1, "Trending 1"), createMovie(2, "Trending 2"))
        val topRatedMovies = listOf(createMovie(3, "Top Rated 1"))
        val upcomingMovies = listOf(createMovie(4, "Upcoming 1"))

        fakeMovieRepository.trendingFlow = flowOf(Resource.Success(trendingMovies))
        fakeMovieRepository.topRatedFlow = flowOf(Resource.Success(topRatedMovies))
        fakeMovieRepository.upcomingFlow = flowOf(Resource.Success(upcomingMovies))

        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertTrue(state.trendingState is UiState.Success)
        assertEquals(trendingMovies, (state.trendingState as UiState.Success).data)

        assertTrue(state.topRatedState is UiState.Success)
        assertEquals(topRatedMovies, (state.topRatedState as UiState.Success).data)

        assertTrue(state.upcomingState is UiState.Success)
        assertEquals(upcomingMovies, (state.upcomingState as UiState.Success).data)

        assertEquals("Trending 1", state.featuredMovie?.title)
        assertEquals(1, state.trendingMoviesList.size)
        assertEquals("Trending 2", state.trendingMoviesList[0].title)
        assertFalse(state.isFullError)
    }

    @Test
    fun init_emptyResults_mapsToEmptyUiState() = runTest(testDispatcher) {
        fakeMovieRepository.trendingFlow = flowOf(Resource.Success(emptyList()))
        fakeMovieRepository.topRatedFlow = flowOf(Resource.Success(emptyList()))
        fakeMovieRepository.upcomingFlow = flowOf(Resource.Success(emptyList()))

        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertTrue(state.trendingState is UiState.Empty)
        assertTrue(state.topRatedState is UiState.Empty)
        assertTrue(state.upcomingState is UiState.Empty)
        assertEquals(null, state.featuredMovie)
    }

    @Test
    fun init_networkError_mapsToOfflineErrorState() = runTest(testDispatcher) {
        fakeMovieRepository.trendingFlow = flowOf(
            Resource.Error("Network error: Check your internet connection.", IOException())
        )
        fakeMovieRepository.topRatedFlow = flowOf(Resource.Success(listOf(createMovie(1, "Top Rated"))))
        fakeMovieRepository.upcomingFlow = flowOf(Resource.Success(listOf(createMovie(2, "Upcoming"))))

        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertTrue(state.trendingState is UiState.Error)
        val trendingError = state.trendingState as UiState.Error
        assertTrue(trendingError.isOffline)
        assertTrue(trendingError.message.contains("Network error"))

        // Other sections must load normally despite trending error
        assertTrue(state.topRatedState is UiState.Success)
        assertTrue(state.upcomingState is UiState.Success)
        assertFalse(state.isFullError)
    }

    @Test
    fun allSectionsFail_isFullErrorIsTrue() = runTest(testDispatcher) {
        fakeMovieRepository.trendingFlow = flowOf(Resource.Error("Error 1"))
        fakeMovieRepository.topRatedFlow = flowOf(Resource.Error("Error 2"))
        fakeMovieRepository.upcomingFlow = flowOf(Resource.Error("Error 3"))

        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isFullError)
    }

    @Test
    fun isAnyOffline_whenOfflineErrorPresent_returnsTrue() = runTest(testDispatcher) {
        fakeMovieRepository.trendingFlow = flowOf(Resource.Error("Network error", java.io.IOException("Offline")))
        fakeMovieRepository.topRatedFlow = flowOf(Resource.Error("Generic server error"))
        fakeMovieRepository.upcomingFlow = flowOf(Resource.Error("Generic server error"))

        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isFullError)
        assertTrue(state.isAnyOffline)
    }

    @Test
    fun retryTrending_recoversFromError() = runTest(testDispatcher) {
        fakeMovieRepository.trendingFlow = flowOf(Resource.Error("Initial failure"))
        fakeMovieRepository.topRatedFlow = flowOf(Resource.Success(listOf(createMovie(1, "Top Rated"))))
        fakeMovieRepository.upcomingFlow = flowOf(Resource.Success(listOf(createMovie(2, "Upcoming"))))

        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.trendingState is UiState.Error)

        // Now simulate recovered repository response
        val recoveredMovies = listOf(createMovie(10, "Recovered Movie"))
        fakeMovieRepository.trendingFlow = flowOf(Resource.Success(recoveredMovies))

        viewModel.loadTrending()
        advanceUntilIdle()

        val recoveredState = viewModel.uiState.value.trendingState
        assertTrue(recoveredState is UiState.Success)
        assertEquals(recoveredMovies, (recoveredState as UiState.Success).data)
    }

    @Test
    fun refresh_reloadsAllSections() = runTest(testDispatcher) {
        var trendingCallCount = 0
        var topRatedCallCount = 0
        var upcomingCallCount = 0

        fakeMovieRepository.onTrendingCalled = { trendingCallCount++ }
        fakeMovieRepository.onTopRatedCalled = { topRatedCallCount++ }
        fakeMovieRepository.onUpcomingCalled = { upcomingCallCount++ }

        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        assertEquals(1, trendingCallCount)
        assertEquals(1, topRatedCallCount)
        assertEquals(1, upcomingCallCount)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, trendingCallCount)
        assertEquals(2, topRatedCallCount)
        assertEquals(2, upcomingCallCount)
    }

    @Test
    fun refresh_maintainsIsRefreshingTrueWhileSectionsAreLoading() = runTest(testDispatcher) {
        val slowFlow = kotlinx.coroutines.flow.flow {
            emit(Resource.Loading)
            kotlinx.coroutines.delay(1000L)
            emit(Resource.Success(listOf(createMovie(1, "Movie"))))
        }
        fakeMovieRepository.trendingFlow = slowFlow
        fakeMovieRepository.topRatedFlow = slowFlow
        fakeMovieRepository.upcomingFlow = slowFlow

        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        viewModel.refresh()
        // During async fetch, isRefreshing must be true
        assertTrue(viewModel.uiState.value.isRefreshing)

        // After completion, isRefreshing must revert to false
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun rapidTrendingReload_cancelsPreviousInFlightRequest() = runTest(testDispatcher) {
        val slowFlow = kotlinx.coroutines.flow.flow {
            emit(Resource.Loading)
            kotlinx.coroutines.delay(1000L)
            emit(Resource.Success(listOf(createMovie(1, "Old Movie"))))
        }
        val fastFlow = kotlinx.coroutines.flow.flow {
            emit(Resource.Loading)
            kotlinx.coroutines.delay(10L)
            emit(Resource.Success(listOf(createMovie(2, "New Movie"))))
        }

        fakeMovieRepository.trendingFlow = slowFlow
        val viewModel = HomeViewModel(fakeMovieRepository)
        advanceUntilIdle()

        // Trigger first slow reload
        fakeMovieRepository.trendingFlow = slowFlow
        viewModel.loadTrending()

        // Immediately trigger second fast reload
        fakeMovieRepository.trendingFlow = fastFlow
        viewModel.loadTrending()

        advanceUntilIdle()
        val trendingState = viewModel.uiState.value.trendingState
        assertTrue(trendingState is UiState.Success)
        assertEquals("New Movie", (trendingState as UiState.Success).data.first().title)
    }

    private class FakeMovieRepository : MovieRepository {
        var trendingFlow: Flow<Resource<List<Movie>>> = flowOf(Resource.Loading)
        var topRatedFlow: Flow<Resource<List<Movie>>> = flowOf(Resource.Loading)
        var upcomingFlow: Flow<Resource<List<Movie>>> = flowOf(Resource.Loading)

        var onTrendingCalled: (() -> Unit)? = null
        var onTopRatedCalled: (() -> Unit)? = null
        var onUpcomingCalled: (() -> Unit)? = null

        override fun getTrendingMovies(page: Int): Flow<Resource<List<Movie>>> {
            onTrendingCalled?.invoke()
            return trendingFlow
        }

        override fun getTopRatedMovies(page: Int): Flow<Resource<List<Movie>>> {
            onTopRatedCalled?.invoke()
            return topRatedFlow
        }

        override fun getUpcomingMovies(page: Int): Flow<Resource<List<Movie>>> {
            onUpcomingCalled?.invoke()
            return upcomingFlow
        }

        override fun searchMovies(query: String, page: Int, releaseYear: Int?): Flow<Resource<List<Movie>>> =
            flowOf(Resource.Success(emptyList()))

        override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> =
            flowOf(Resource.Loading)

        override fun getMovieCredits(movieId: Int): Flow<Resource<List<CastMember>>> =
            flowOf(Resource.Success(emptyList()))

        override fun getMovieVideos(movieId: Int): Flow<Resource<List<VideoTrailer>>> =
            flowOf(Resource.Success(emptyList()))

        override fun getMovieWatchProviders(movieId: Int, countryCode: String): Flow<Resource<MovieWatchProviders>> =
            flowOf(Resource.Loading)

        override fun getSimilarMovies(movieId: Int, page: Int): Flow<Resource<List<Movie>>> =
            flowOf(Resource.Success(emptyList()))

        override fun getGenres(): Flow<Resource<List<Genre>>> =
            flowOf(Resource.Success(emptyList()))

        override fun discoverCandidateMovies(
            genreIds: Set<Int>,
            startYear: Int?,
            endYear: Int?,
            minRating: Double?,
            page: Int
        ): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
    }
}
