package com.example.madverse

import androidx.lifecycle.SavedStateHandle
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.model.CastMember
import com.example.madverse.core.model.Genre
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.ProviderType
import com.example.madverse.core.model.VideoTrailer
import com.example.madverse.core.model.WatchProvider
import com.example.madverse.core.navigation.Screen
import com.example.madverse.feature.details.presentation.MovieDetailsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
 * Unit tests verifying [MovieDetailsViewModel] metadata loading,
 * video/provider integration, Favorites/Watchlist toggles, invalid ID handling,
 * and missing metadata resilience.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailsViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var fakeMovieRepository: FakeMovieRepository
    private lateinit var fakeFavoritesRepository: FakeFavoritesRepository
    private lateinit var fakeWatchlistRepository: FakeWatchlistRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeMovieRepository = FakeMovieRepository()
        fakeFavoritesRepository = FakeFavoritesRepository()
        fakeWatchlistRepository = FakeWatchlistRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMovieDetails(
        id: Int = 550,
        title: String = "Fight Club",
        posterUrl: String? = "https://image.tmdb.org/t/p/w500/poster.jpg",
        backdropUrl: String? = "https://image.tmdb.org/t/p/w780/backdrop.jpg",
        runtimeMinutes: Int? = 139,
        releaseDate: String? = "1999-10-15"
    ): MovieDetails {
        return MovieDetails(
            id = id,
            title = title,
            tagline = "Mischief. Mayhem. Soap.",
            overview = "An insomniac office worker and a soap salesman build an underground fight club.",
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseDate = releaseDate,
            runtimeMinutes = runtimeMinutes,
            voteAverage = 8.4,
            voteCount = 28000,
            genres = listOf(Genre(18, "Drama"), Genre(53, "Thriller")),
            status = "Released",
            budget = 63000000L,
            revenue = 101200000L,
            originalLanguage = "en"
        )
    }

    @Test
    fun loadMovieDetails_validId_emitsAllMetadataSuccessfully() = runTest(testDispatcher) {
        val details = createMovieDetails()
        val cast = listOf(
            CastMember(1, "Brad Pitt", "Tyler Durden", null, 0),
            CastMember(2, "Edward Norton", "The Narrator", null, 1)
        )
        val videos = listOf(
            VideoTrailer("v1", "key123", "Official Trailer", "YouTube", "Trailer", true)
        )
        val providers = MovieWatchProviders(
            movieId = 550,
            tmdbWatchPageUrl = "https://tmdb.org/watch",
            flatrate = listOf(WatchProvider(1, "Hulu", null, ProviderType.STREAMING_SUBSCRIPTION)),
            rent = emptyList(),
            buy = emptyList()
        )

        fakeMovieRepository.detailsResult = Resource.Success(details)
        fakeMovieRepository.castResult = Resource.Success(cast)
        fakeMovieRepository.videosResult = Resource.Success(videos)
        fakeMovieRepository.providersResult = Resource.Success(providers)

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 550))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertTrue(state.detailsState is UiState.Success)
        val loadedDetails = (state.detailsState as UiState.Success).data
        assertEquals("Fight Club", loadedDetails.title)
        assertEquals("2h 19m", loadedDetails.formattedRuntime)
        assertEquals("1999", loadedDetails.releaseYear)
        assertEquals("8.4", loadedDetails.formattedRating)

        assertTrue(state.castState is UiState.Success)
        assertEquals(2, (state.castState as UiState.Success).data.size)

        assertTrue(state.videosState is UiState.Success)
        assertEquals(1, (state.videosState as UiState.Success).data.size)
        assertNotNull(state.primaryTrailer)
        assertEquals("key123", state.primaryTrailer?.key)

        assertTrue(state.watchProvidersState is UiState.Success)
        val loadedProviders = (state.watchProvidersState as UiState.Success).data
        assertEquals(1, loadedProviders.flatrate.size)
        assertEquals("Hulu", loadedProviders.flatrate[0].providerName)
    }

    @Test
    fun loadMovieDetails_invalidId_emitsErrorWithoutCallingRepository() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 0))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )

        viewModel.loadMovieDetails(-5)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.detailsState is UiState.Error)
        val errorState = state.detailsState as UiState.Error
        assertTrue(errorState.message.contains("Invalid movie ID"))
        assertEquals(0, fakeMovieRepository.detailsCallCount)
    }

    @Test
    fun loadMovieDetails_apiError_emitsErrorState() = runTest(testDispatcher) {
        fakeMovieRepository.detailsResult = Resource.Error(
            message = "Movie not found on TMDB (404).",
            cause = IOException()
        )

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 999999))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.detailsState is UiState.Error)
        val errorState = state.detailsState as UiState.Error
        assertTrue(errorState.message.contains("404"))
    }

    @Test
    fun toggleFavorite_togglesDatabasePersistence() = runTest(testDispatcher) {
        val details = createMovieDetails()
        fakeMovieRepository.detailsResult = Resource.Success(details)

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 550))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)

        // Add to favorites
        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertEquals(1, fakeFavoritesRepository.addedFavorites.size)
        assertEquals(550, fakeFavoritesRepository.addedFavorites[0].id)
        assertTrue(viewModel.uiState.value.isFavorite)

        // Remove from favorites
        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertEquals(1, fakeFavoritesRepository.removedMovieIds.size)
        assertEquals(550, fakeFavoritesRepository.removedMovieIds[0])
        assertFalse(viewModel.uiState.value.isFavorite)
    }

    @Test
    fun toggleWatchlist_togglesDatabasePersistence() = runTest(testDispatcher) {
        val details = createMovieDetails()
        fakeMovieRepository.detailsResult = Resource.Success(details)

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 550))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isInWatchlist)

        // Add to watchlist
        viewModel.toggleWatchlist()
        advanceUntilIdle()

        assertEquals(1, fakeWatchlistRepository.addedWatchlist.size)
        assertEquals(550, fakeWatchlistRepository.addedWatchlist[0].id)
        assertTrue(viewModel.uiState.value.isInWatchlist)

        // Remove from watchlist
        viewModel.toggleWatchlist()
        advanceUntilIdle()

        assertEquals(1, fakeWatchlistRepository.removedMovieIds.size)
        assertEquals(550, fakeWatchlistRepository.removedMovieIds[0])
        assertFalse(viewModel.uiState.value.isInWatchlist)
    }

    @Test
    fun missingMetadata_calculatesFallbacksWithoutCrashing() = runTest(testDispatcher) {
        val sparseDetails = createMovieDetails(
            posterUrl = null,
            backdropUrl = null,
            runtimeMinutes = null,
            releaseDate = null
        )
        fakeMovieRepository.detailsResult = Resource.Success(sparseDetails)

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 550))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        val loaded = (viewModel.uiState.value.detailsState as UiState.Success).data
        assertEquals("N/A", loaded.formattedRuntime)
        assertEquals("Unknown", loaded.releaseYear)
        assertEquals(null, loaded.posterUrl)
        assertEquals(null, loaded.backdropUrl)

        val domainMovie = loaded.toMovie()
        assertEquals(550, domainMovie.id)
    }

    @Test
    fun loadMovieDetails_sortsAndCategorizesWatchProviders() = runTest(testDispatcher) {
        val details = createMovieDetails()
        fakeMovieRepository.detailsResult = Resource.Success(details)

        val unsortedFlatrate = listOf(
            WatchProvider(providerId = 2, providerName = "Zee5", logoUrl = "/zee.png", type = ProviderType.STREAMING_SUBSCRIPTION),
            WatchProvider(providerId = 1, providerName = "Amazon Prime Video", logoUrl = "/prime.png", type = ProviderType.STREAMING_SUBSCRIPTION),
            WatchProvider(providerId = 3, providerName = "Netflix", logoUrl = "/netflix.png", type = ProviderType.STREAMING_SUBSCRIPTION)
        )
        val unsortedRent = listOf(
            WatchProvider(providerId = 5, providerName = "YouTube", logoUrl = "/yt.png", type = ProviderType.RENT),
            WatchProvider(providerId = 4, providerName = "Apple TV", logoUrl = "/apple.png", type = ProviderType.RENT)
        )
        val unsortedBuy = listOf(
            WatchProvider(providerId = 6, providerName = "Google Play Movies", logoUrl = "/gp.png", type = ProviderType.BUY)
        )

        fakeMovieRepository.providersResult = Resource.Success(
            MovieWatchProviders(
                movieId = 550,
                tmdbWatchPageUrl = "https://www.themoviedb.org/movie/550/watch?locale=IN",
                flatrate = unsortedFlatrate,
                rent = unsortedRent,
                buy = unsortedBuy
            )
        )

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 550))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasAnyWatchProviders)
        assertEquals("https://www.themoviedb.org/movie/550/watch?locale=IN", state.tmdbWatchPageUrl)

        // Flatrate sorted alphabetically
        assertEquals(3, state.flatrateProviders.size)
        assertEquals("Amazon Prime Video", state.flatrateProviders[0].providerName)
        assertEquals("Netflix", state.flatrateProviders[1].providerName)
        assertEquals("Zee5", state.flatrateProviders[2].providerName)

        // Rent sorted alphabetically
        assertEquals(2, state.rentProviders.size)
        assertEquals("Apple TV", state.rentProviders[0].providerName)
        assertEquals("YouTube", state.rentProviders[1].providerName)

        // Buy
        assertEquals(1, state.buyProviders.size)
        assertEquals("Google Play Movies", state.buyProviders[0].providerName)
    }

    @Test
    fun loadMovieDetails_emptyProviders_setsEmptyStateAndFalseHasProviders() = runTest(testDispatcher) {
        val details = createMovieDetails()
        fakeMovieRepository.detailsResult = Resource.Success(details)
        fakeMovieRepository.providersResult = Resource.Success(
            MovieWatchProviders(
                movieId = 550,
                tmdbWatchPageUrl = "https://www.themoviedb.org/movie/550/watch?locale=IN",
                flatrate = emptyList(),
                rent = emptyList(),
                buy = emptyList()
            )
        )

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 550))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasAnyWatchProviders)
        assertTrue(state.watchProvidersState is UiState.Empty)
        assertEquals("https://www.themoviedb.org/movie/550/watch?locale=IN", state.tmdbWatchPageUrl)
        assertTrue(state.flatrateProviders.isEmpty())
        assertTrue(state.rentProviders.isEmpty())
        assertTrue(state.buyProviders.isEmpty())
    }

    @Test
    fun setRegion_updatesCountryCodeAndRefetchesProviders() = runTest(testDispatcher) {
        val details = createMovieDetails()
        fakeMovieRepository.detailsResult = Resource.Success(details)

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 550))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        assertEquals("IN", viewModel.uiState.value.selectedCountryCode)

        viewModel.setRegion("US")
        advanceUntilIdle()

        assertEquals("US", viewModel.uiState.value.selectedCountryCode)
        assertTrue(fakeMovieRepository.requestedCountryCodes.contains("US"))
    }

    @Test
    fun loadMovieDetails_watchProvidersError_emitsErrorStateGracefully() = runTest(testDispatcher) {
        val details = createMovieDetails()
        fakeMovieRepository.detailsResult = Resource.Success(details)
        fakeMovieRepository.providersResult = Resource.Error(
            message = "Streaming providers unavailable due to network timeout."
        )

        val savedStateHandle = SavedStateHandle(mapOf(Screen.ARG_MOVIE_ID to 550))
        val viewModel = MovieDetailsViewModel(
            movieRepository = fakeMovieRepository,
            favoritesRepository = fakeFavoritesRepository,
            watchlistRepository = fakeWatchlistRepository,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.detailsState is UiState.Success)
        assertTrue(state.watchProvidersState is UiState.Error)
        assertFalse(state.hasAnyWatchProviders)
    }

    @Test
    fun changingRegionCancelsSlowerPreviousResponse() = runTest(testDispatcher) {
        fakeMovieRepository.providerDelays = mapOf("IN" to 1000L, "US" to 10L)
        val viewModel = MovieDetailsViewModel(fakeMovieRepository, fakeFavoritesRepository, fakeWatchlistRepository, SavedStateHandle())
        viewModel.loadMovieDetails(1)
        runCurrent()
        viewModel.setRegion("US")
        advanceUntilIdle()
        assertEquals("US", viewModel.uiState.value.selectedCountryCode)
        assertEquals("https://example.com/US", viewModel.uiState.value.tmdbWatchPageUrl)
    }

    private class FakeMovieRepository : MovieRepository {
        var providerDelays: Map<String, Long> = emptyMap()
        var detailsResult: Resource<MovieDetails> = Resource.Loading
        var castResult: Resource<List<CastMember>> = Resource.Success(emptyList())
        var videosResult: Resource<List<VideoTrailer>> = Resource.Success(emptyList())
        var providersResult: Resource<MovieWatchProviders> = Resource.Success(
            MovieWatchProviders(0, null, emptyList(), emptyList(), emptyList())
        )
        var detailsCallCount = 0
        val requestedCountryCodes = mutableListOf<String>()

        override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> {
            detailsCallCount++
            return flowOf(detailsResult)
        }

        override fun getMovieCredits(movieId: Int): Flow<Resource<List<CastMember>>> =
            flowOf(castResult)

        override fun getMovieVideos(movieId: Int): Flow<Resource<List<VideoTrailer>>> =
            flowOf(videosResult)

        override fun getMovieWatchProviders(movieId: Int, countryCode: String): Flow<Resource<MovieWatchProviders>> {
            requestedCountryCodes.add(countryCode)
            if (providerDelays.isNotEmpty()) return kotlinx.coroutines.flow.flow {
                kotlinx.coroutines.delay(providerDelays[countryCode] ?: 0L)
                emit(Resource.Success(MovieWatchProviders(movieId, "https://example.com/$countryCode", emptyList(), emptyList(), emptyList())))
            }
            return flowOf(providersResult)
        }

        override fun getTrendingMovies(page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun getTopRatedMovies(page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun getUpcomingMovies(page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun searchMovies(query: String, page: Int, releaseYear: Int?): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun getSimilarMovies(movieId: Int, page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun getGenres(): Flow<Resource<List<Genre>>> = flowOf(Resource.Success(emptyList()))
        override fun discoverCandidateMovies(genreIds: Set<Int>, startYear: Int?, endYear: Int?, minRating: Double?, page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        private val isFavFlow = MutableStateFlow(false)
        val addedFavorites = mutableListOf<Movie>()
        val removedMovieIds = mutableListOf<Int>()

        override fun getFavoriteMovies(): Flow<List<Movie>> = flowOf(addedFavorites)
        override fun isFavorite(movieId: Int): Flow<Boolean> = isFavFlow
        override suspend fun addToFavorites(movie: Movie) {
            addedFavorites.add(movie)
            isFavFlow.value = true
        }
        override suspend fun removeFromFavorites(movieId: Int) {
            removedMovieIds.add(movieId)
            addedFavorites.removeAll { it.id == movieId }
            isFavFlow.value = false
        }
    }

    private class FakeWatchlistRepository : WatchlistRepository {
        private val inWatchlistFlow = MutableStateFlow(false)
        val addedWatchlist = mutableListOf<Movie>()
        val removedMovieIds = mutableListOf<Int>()

        override fun getWatchlistMovies(): Flow<List<Movie>> = flowOf(addedWatchlist)
        override fun isMovieInWatchlist(movieId: Int): Flow<Boolean> = inWatchlistFlow
        override suspend fun addToWatchlist(movie: Movie) {
            addedWatchlist.add(movie)
            inWatchlistFlow.value = true
        }
        override suspend fun removeFromWatchlist(movieId: Int) {
            removedMovieIds.add(movieId)
            addedWatchlist.removeAll { it.id == movieId }
            inWatchlistFlow.value = false
        }
    }
}
