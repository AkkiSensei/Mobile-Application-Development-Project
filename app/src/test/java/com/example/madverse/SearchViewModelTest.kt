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
import com.example.madverse.feature.search.presentation.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests verifying [SearchViewModel] debouncing, obsolete request cancellation,
 * error handling, empty results mapping, and retry flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

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
            releaseDate = "2024-03-01",
            voteAverage = 8.2,
            voteCount = 5400,
            genreIds = listOf(28, 12),
            popularity = 85.0
        )
    }

    @Test
    fun initialState_isIdleWithEmptyQuery() {
        val viewModel = SearchViewModel(fakeMovieRepository)
        val state = viewModel.uiState.value

        assertEquals("", state.query)
        assertEquals("", state.activeQuery)
        assertTrue(state.isIdle)
        assertTrue(state.searchState is UiState.Empty)
    }

    @Test
    fun onQueryChanged_blankQuery_doesNotTriggerSearch() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(fakeMovieRepository)
        viewModel.onQueryChanged("    ")
        advanceUntilIdle()

        assertEquals("    ", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.isIdle)
        assertEquals(0, fakeMovieRepository.searchCallCount)
    }

    @Test
    fun onQueryChanged_debouncesRequests() = runTest(testDispatcher) {
        val expectedMovies = listOf(createMovie(101, "Interstellar"))
        fakeMovieRepository.searchResults = expectedMovies

        val viewModel = SearchViewModel(fakeMovieRepository)
        viewModel.debounceDurationMillis = 400L

        viewModel.onQueryChanged("Interstellar")

        // Advance 200ms — within debounce window: should NOT have searched yet
        advanceTimeBy(200L)
        assertEquals(0, fakeMovieRepository.searchCallCount)

        // Advance remaining 201ms — debounce threshold crossed: search executes
        advanceTimeBy(201L)
        advanceUntilIdle()

        assertEquals(1, fakeMovieRepository.searchCallCount)
        assertEquals("Interstellar", fakeMovieRepository.lastSearchQuery)

        val state = viewModel.uiState.value
        assertTrue(state.searchState is UiState.Success)
        assertEquals(expectedMovies, (state.searchState as UiState.Success).data)
        assertEquals("Interstellar", state.activeQuery)
        assertFalse(state.isIdle)
    }

    @Test
    fun onQueryChanged_rapidTyping_cancelsObsoleteQueries() = runTest(testDispatcher) {
        val expectedMovies = listOf(createMovie(102, "Inception"))
        fakeMovieRepository.searchResults = expectedMovies

        val viewModel = SearchViewModel(fakeMovieRepository)
        viewModel.debounceDurationMillis = 300L

        // Simulate rapid typing within debounce thresholds
        viewModel.onQueryChanged("I")
        advanceTimeBy(100L)
        viewModel.onQueryChanged("In")
        advanceTimeBy(100L)
        viewModel.onQueryChanged("Inc")
        advanceTimeBy(100L)
        viewModel.onQueryChanged("Inception")

        // Wait for final debounce to complete
        advanceTimeBy(301L)
        advanceUntilIdle()

        // Only the final complete query should have been sent to repository
        assertEquals(1, fakeMovieRepository.searchCallCount)
        assertEquals("Inception", fakeMovieRepository.lastSearchQuery)
        assertTrue(viewModel.uiState.value.searchState is UiState.Success)
    }

    @Test
    fun onSearchSubmitted_executesImmediatelyWithoutDebounceDelay() = runTest(testDispatcher) {
        val expectedMovies = listOf(createMovie(103, "Dune: Part Two"))
        fakeMovieRepository.searchResults = expectedMovies

        val viewModel = SearchViewModel(fakeMovieRepository)
        viewModel.debounceDurationMillis = 500L

        viewModel.onSearchSubmitted("Dune")
        advanceUntilIdle()

        assertEquals(1, fakeMovieRepository.searchCallCount)
        assertEquals("Dune", fakeMovieRepository.lastSearchQuery)
        assertEquals("Dune", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.searchState is UiState.Success)
    }

    @Test
    fun searchReturnsEmpty_mapsToEmptyUiState() = runTest(testDispatcher) {
        fakeMovieRepository.searchResults = emptyList()

        val viewModel = SearchViewModel(fakeMovieRepository)
        viewModel.debounceDurationMillis = 0L

        viewModel.onQueryChanged("NonexistentMovieXYZ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.searchState is UiState.Empty)
        val emptyState = state.searchState as UiState.Empty
        assertTrue(emptyState.message.contains("NonexistentMovieXYZ"))
    }

    @Test
    fun searchReturnsError_mapsToOfflineAwareErrorState() = runTest(testDispatcher) {
        fakeMovieRepository.searchError = Resource.Error(
            message = "Network error: Check your internet connection.",
            cause = IOException("No internet connection")
        )

        val viewModel = SearchViewModel(fakeMovieRepository)
        viewModel.debounceDurationMillis = 0L

        viewModel.onQueryChanged("Avatar")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.searchState is UiState.Error)
        val errorState = state.searchState as UiState.Error
        assertTrue(errorState.isOffline)
        assertTrue(errorState.message.contains("Network error"))
    }

    @Test
    fun retry_reexecutesSearchForCurrentQuery() = runTest(testDispatcher) {
        fakeMovieRepository.searchError = Resource.Error("Initial failure")

        val viewModel = SearchViewModel(fakeMovieRepository)
        viewModel.debounceDurationMillis = 0L

        viewModel.onQueryChanged("Gladiator")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.searchState is UiState.Error)
        assertEquals(1, fakeMovieRepository.searchCallCount)

        // Recover repository
        val recoveredMovies = listOf(createMovie(104, "Gladiator II"))
        fakeMovieRepository.searchError = null
        fakeMovieRepository.searchResults = recoveredMovies

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, fakeMovieRepository.searchCallCount)
        assertTrue(viewModel.uiState.value.searchState is UiState.Success)
        assertEquals(recoveredMovies, (viewModel.uiState.value.searchState as UiState.Success).data)
    }

    @Test
    fun onClearQuery_resetsStateAndClearsInput() = runTest(testDispatcher) {
        fakeMovieRepository.searchResults = listOf(createMovie(105, "Batman"))

        val viewModel = SearchViewModel(fakeMovieRepository)
        viewModel.debounceDurationMillis = 0L

        viewModel.onQueryChanged("Batman")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.searchState is UiState.Success)

        viewModel.onClearQuery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertEquals("", state.activeQuery)
        assertTrue(state.isIdle)
        assertTrue(state.searchState is UiState.Empty)
    }

    private class FakeMovieRepository : MovieRepository {
        var searchResults: List<Movie> = emptyList()
        var searchError: Resource.Error? = null
        var searchCallCount: Int = 0
        var lastSearchQuery: String? = null

        override fun searchMovies(
            query: String,
            page: Int,
            releaseYear: Int?
        ): Flow<Resource<List<Movie>>> {
            searchCallCount++
            lastSearchQuery = query
            return flowOf(
                searchError ?: Resource.Success(searchResults)
            )
        }

        override fun getTrendingMovies(page: Int): Flow<Resource<List<Movie>>> =
            flowOf(Resource.Success(emptyList()))

        override fun getTopRatedMovies(page: Int): Flow<Resource<List<Movie>>> =
            flowOf(Resource.Success(emptyList()))

        override fun getUpcomingMovies(page: Int): Flow<Resource<List<Movie>>> =
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
