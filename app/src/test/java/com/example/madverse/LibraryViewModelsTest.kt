package com.example.madverse

import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.model.Movie
import com.example.madverse.feature.library.presentation.FavoritesViewModel
import com.example.madverse.feature.library.presentation.LibrarySortOption
import com.example.madverse.feature.library.presentation.LibraryTab
import com.example.madverse.feature.library.presentation.LibraryViewModel
import com.example.madverse.feature.library.presentation.WatchlistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests verifying [FavoritesViewModel], [WatchlistViewModel], and [LibraryViewModel]
 * state flows, sort options, empty/success/error handling, and repository delegations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelsTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var fakeFavoritesRepository: FakeFavoritesRepository
    private lateinit var fakeWatchlistRepository: FakeWatchlistRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeFavoritesRepository = FakeFavoritesRepository()
        fakeWatchlistRepository = FakeWatchlistRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMovie(id: Int, title: String, rating: Double = 8.0, year: String = "2024"): Movie {
        return Movie(
            id = id,
            title = title,
            overview = "Overview of $title",
            posterUrl = "https://image.tmdb.org/t/p/w500/poster$id.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/backdrop$id.jpg",
            releaseDate = "$year-01-01",
            voteAverage = rating,
            voteCount = 500,
            genreIds = listOf(28, 12),
            popularity = 85.0
        )
    }

    @Test fun collectionWriteFailuresAreVisibleAndRetryable() = runTest(testDispatcher) {
        fakeFavoritesRepository.writeError = IOException("Disk full")
        fakeWatchlistRepository.writeError = IOException("Disk full")
        val favorites = FavoritesViewModel(fakeFavoritesRepository)
        val watchlist = WatchlistViewModel(fakeWatchlistRepository)
        val library = LibraryViewModel(fakeFavoritesRepository, fakeWatchlistRepository)
        favorites.removeFromFavorites(1); watchlist.removeFromWatchlist(1)
        library.addToFavorites(createMovie(1, "Movie")); advanceUntilIdle()
        org.junit.Assert.assertNotNull(favorites.actionError.value)
        org.junit.Assert.assertNotNull(watchlist.actionError.value)
        org.junit.Assert.assertNotNull(library.actionError.value)
        assertTrue(fakeFavoritesRepository.removedMovieIds.isEmpty())
        assertTrue(fakeWatchlistRepository.removedMovieIds.isEmpty())
        fakeFavoritesRepository.writeError = null; fakeWatchlistRepository.writeError = null
        favorites.dismissActionError(); watchlist.dismissActionError(); library.dismissActionError()
        favorites.removeFromFavorites(1); watchlist.removeFromWatchlist(1)
        library.addToFavorites(createMovie(1, "Movie")); advanceUntilIdle()
        assertEquals(listOf(1), fakeFavoritesRepository.removedMovieIds)
        assertEquals(listOf(1), fakeWatchlistRepository.removedMovieIds)
        org.junit.Assert.assertNull(library.actionError.value)
    }

    // =========================================================================
    // FavoritesViewModel Tests
    // =========================================================================

    @Test
    fun favoritesViewModel_emptyFavorites_emitsEmptyUiState() = runTest(testDispatcher) {
        val viewModel = FavoritesViewModel(fakeFavoritesRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected UiState.Empty when repository is empty", state is UiState.Empty)
        job.cancel()
    }

    @Test
    fun favoritesViewModel_populatedFavorites_emitsSuccessUiState() = runTest(testDispatcher) {
        val movies = listOf(createMovie(1, "Inception"), createMovie(2, "Interstellar"))
        fakeFavoritesRepository.emitMovies(movies)

        val viewModel = FavoritesViewModel(fakeFavoritesRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected UiState.Success when movies are present", state is UiState.Success)
        assertEquals(2, (state as UiState.Success).data.size)
        job.cancel()
    }

    @Test
    fun favoritesViewModel_sortByRating_sortsDescending() = runTest(testDispatcher) {
        val movieLow = createMovie(1, "Low Rated", rating = 6.0)
        val movieHigh = createMovie(2, "High Rated", rating = 9.2)
        val movieMid = createMovie(3, "Mid Rated", rating = 7.5)
        fakeFavoritesRepository.emitMovies(listOf(movieLow, movieHigh, movieMid))

        val viewModel = FavoritesViewModel(fakeFavoritesRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSortOption(LibrarySortOption.RATING)
        advanceUntilIdle()

        val state = viewModel.uiState.value as UiState.Success
        assertEquals(listOf(2, 3, 1), state.data.map { it.id })
        job.cancel()
    }

    @Test
    fun favoritesViewModel_sortByTitle_sortsAlphabetically() = runTest(testDispatcher) {
        val movieZ = createMovie(1, "Zootopia")
        val movieA = createMovie(2, "Avatar")
        val movieM = createMovie(3, "Matrix")
        fakeFavoritesRepository.emitMovies(listOf(movieZ, movieA, movieM))

        val viewModel = FavoritesViewModel(fakeFavoritesRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSortOption(LibrarySortOption.TITLE)
        advanceUntilIdle()

        val state = viewModel.uiState.value as UiState.Success
        assertEquals(listOf("Avatar", "Matrix", "Zootopia"), state.data.map { it.title })
        job.cancel()
    }

    @Test
    fun favoritesViewModel_sortByReleaseDate_sortsNewestFirst() = runTest(testDispatcher) {
        val movieOld = createMovie(1, "Old Movie", year = "1994")
        val movieNew = createMovie(2, "New Movie", year = "2024")
        val movieMid = createMovie(3, "Mid Movie", year = "2010")
        fakeFavoritesRepository.emitMovies(listOf(movieOld, movieNew, movieMid))

        val viewModel = FavoritesViewModel(fakeFavoritesRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSortOption(LibrarySortOption.RELEASE_DATE)
        advanceUntilIdle()

        val state = viewModel.uiState.value as UiState.Success
        assertEquals(listOf(2, 3, 1), state.data.map { it.id })
        job.cancel()
    }

    @Test
    fun favoritesViewModel_removeFromFavorites_delegatesToRepository() = runTest(testDispatcher) {
        val viewModel = FavoritesViewModel(fakeFavoritesRepository)
        viewModel.removeFromFavorites(42)
        advanceUntilIdle()

        assertEquals(listOf(42), fakeFavoritesRepository.removedMovieIds)
    }

    @Test
    fun favoritesViewModel_addToFavorites_delegatesToRepository() = runTest(testDispatcher) {
        val viewModel = FavoritesViewModel(fakeFavoritesRepository)
        val movie = createMovie(101, "Dune")
        viewModel.addToFavorites(movie)
        advanceUntilIdle()

        assertEquals(listOf(movie), fakeFavoritesRepository.addedMovies)
    }

    @Test
    fun favoritesViewModel_error_emitsErrorStateAndRetries() = runTest(testDispatcher) {
        fakeFavoritesRepository.throwError = true
        val viewModel = FavoritesViewModel(fakeFavoritesRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Error)

        // Resolve error and retry
        fakeFavoritesRepository.throwError = false
        fakeFavoritesRepository.emitMovies(listOf(createMovie(1, "Recovered")))
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Success)
        job.cancel()
    }

    // =========================================================================
    // WatchlistViewModel Tests
    // =========================================================================

    @Test
    fun watchlistViewModel_emptyWatchlist_emitsEmptyUiState() = runTest(testDispatcher) {
        val viewModel = WatchlistViewModel(fakeWatchlistRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected UiState.Empty when watchlist is empty", state is UiState.Empty)
        job.cancel()
    }

    @Test
    fun watchlistViewModel_populatedWatchlist_emitsSuccessUiState() = runTest(testDispatcher) {
        val movies = listOf(createMovie(10, "Arrival"), createMovie(20, "Blade Runner 2049"))
        fakeWatchlistRepository.emitMovies(movies)

        val viewModel = WatchlistViewModel(fakeWatchlistRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected UiState.Success when watchlist has movies", state is UiState.Success)
        assertEquals(2, (state as UiState.Success).data.size)
        job.cancel()
    }

    @Test
    fun watchlistViewModel_removeFromWatchlist_delegatesToRepository() = runTest(testDispatcher) {
        val viewModel = WatchlistViewModel(fakeWatchlistRepository)
        viewModel.removeFromWatchlist(99)
        advanceUntilIdle()

        assertEquals(listOf(99), fakeWatchlistRepository.removedMovieIds)
    }

    @Test
    fun watchlistViewModel_addToWatchlist_delegatesToRepository() = runTest(testDispatcher) {
        val viewModel = WatchlistViewModel(fakeWatchlistRepository)
        val movie = createMovie(202, "Oppenheimer")
        viewModel.addToWatchlist(movie)
        advanceUntilIdle()

        assertEquals(listOf(movie), fakeWatchlistRepository.addedMovies)
    }

    @Test
    fun watchlistViewModel_error_emitsErrorStateAndRetries() = runTest(testDispatcher) {
        fakeWatchlistRepository.throwError = true
        val viewModel = WatchlistViewModel(fakeWatchlistRepository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Error)

        // Resolve error and retry
        fakeWatchlistRepository.throwError = false
        fakeWatchlistRepository.emitMovies(listOf(createMovie(5, "Watchlist Movie")))
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Success)
        job.cancel()
    }

    // =========================================================================
    // LibraryViewModel (Unified) Tests
    // =========================================================================

    @Test
    fun libraryViewModel_tabSelection_togglesBetweenWatchlistAndFavorites() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(fakeFavoritesRepository, fakeWatchlistRepository)
        assertEquals(LibraryTab.WATCHLIST, viewModel.selectedTab.value)

        viewModel.selectTab(LibraryTab.FAVORITES)
        assertEquals(LibraryTab.FAVORITES, viewModel.selectedTab.value)

        viewModel.selectTab(LibraryTab.WATCHLIST)
        assertEquals(LibraryTab.WATCHLIST, viewModel.selectedTab.value)
    }

    @Test
    fun libraryViewModel_observesBothCollectionsIndependently() = runTest(testDispatcher) {
        val favMovie = createMovie(1, "Favorite Movie")
        val watchMovie = createMovie(2, "Watchlist Movie")
        fakeFavoritesRepository.emitMovies(listOf(favMovie))
        fakeWatchlistRepository.emitMovies(listOf(watchMovie))

        val viewModel = LibraryViewModel(fakeFavoritesRepository, fakeWatchlistRepository)
        val jobFav = launch { viewModel.favoritesState.collect {} }
        val jobWatch = launch { viewModel.watchlistState.collect {} }
        advanceUntilIdle()

        val favState = viewModel.favoritesState.value as UiState.Success
        val watchState = viewModel.watchlistState.value as UiState.Success

        assertEquals(1, favState.data.size)
        assertEquals(1, favState.data.first().id)

        assertEquals(1, watchState.data.size)
        assertEquals(2, watchState.data.first().id)

        jobFav.cancel()
        jobWatch.cancel()
    }

    @Test
    fun libraryViewModel_removalActions_delegateToRespectiveRepositories() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(fakeFavoritesRepository, fakeWatchlistRepository)

        viewModel.removeFromFavorites(11)
        viewModel.removeFromWatchlist(22)
        advanceUntilIdle()

        assertEquals(listOf(11), fakeFavoritesRepository.removedMovieIds)
        assertEquals(listOf(22), fakeWatchlistRepository.removedMovieIds)
    }

    @Test
    fun libraryViewModel_addActions_delegateToRespectiveRepositories() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(fakeFavoritesRepository, fakeWatchlistRepository)
        val movie1 = createMovie(1, "Fav")
        val movie2 = createMovie(2, "Watch")

        viewModel.addToFavorites(movie1)
        viewModel.addToWatchlist(movie2)
        advanceUntilIdle()

        assertEquals(listOf(movie1), fakeFavoritesRepository.addedMovies)
        assertEquals(listOf(movie2), fakeWatchlistRepository.addedMovies)
    }
}

// =============================================================================
// Fake Repository Implementations for Testing
// =============================================================================

private class FakeFavoritesRepository : FavoritesRepository {
    private val favoritesFlow = MutableStateFlow<List<Movie>>(emptyList())
    var throwError: Boolean = false
    var writeError: Exception? = null
    val addedMovies = mutableListOf<Movie>()
    val removedMovieIds = mutableListOf<Int>()

    fun emitMovies(movies: List<Movie>) {
        favoritesFlow.value = movies
    }

    override fun getFavoriteMovies(): Flow<List<Movie>> = flow {
        if (throwError) {
            throw IOException("Database read error")
        }
        emitAll(favoritesFlow)
    }

    override fun isFavorite(movieId: Int): Flow<Boolean> =
        favoritesFlow.map { list -> list.any { it.id == movieId } }

    override suspend fun isFavoriteOnce(movieId: Int): Boolean =
        favoritesFlow.value.any { it.id == movieId }

    override suspend fun getFavoriteById(movieId: Int): Movie? =
        favoritesFlow.value.find { it.id == movieId }

    override suspend fun addToFavorites(movie: Movie) {
        writeError?.let { throw it }
        addedMovies.add(movie)
        favoritesFlow.value = favoritesFlow.value.filterNot { it.id == movie.id } + movie
    }

    override suspend fun removeFromFavorites(movieId: Int) {
        writeError?.let { throw it }
        removedMovieIds.add(movieId)
        favoritesFlow.value = favoritesFlow.value.filterNot { it.id == movieId }
    }
}

private class FakeWatchlistRepository : WatchlistRepository {
    private val watchlistFlow = MutableStateFlow<List<Movie>>(emptyList())
    var throwError: Boolean = false
    var writeError: Exception? = null
    val addedMovies = mutableListOf<Movie>()
    val removedMovieIds = mutableListOf<Int>()

    fun emitMovies(movies: List<Movie>) {
        watchlistFlow.value = movies
    }

    override fun getWatchlistMovies(): Flow<List<Movie>> = flow {
        if (throwError) {
            throw IOException("Database read error")
        }
        emitAll(watchlistFlow)
    }

    override fun isMovieInWatchlist(movieId: Int): Flow<Boolean> =
        watchlistFlow.map { list -> list.any { it.id == movieId } }

    override suspend fun isMovieInWatchlistOnce(movieId: Int): Boolean =
        watchlistFlow.value.any { it.id == movieId }

    override suspend fun getWatchlistMovieById(movieId: Int): Movie? =
        watchlistFlow.value.find { it.id == movieId }

    override suspend fun addToWatchlist(movie: Movie) {
        writeError?.let { throw it }
        addedMovies.add(movie)
        watchlistFlow.value = watchlistFlow.value.filterNot { it.id == movie.id } + movie
    }

    override suspend fun removeFromWatchlist(movieId: Int) {
        writeError?.let { throw it }
        removedMovieIds.add(movieId)
        watchlistFlow.value = watchlistFlow.value.filterNot { it.id == movieId }
    }
}
