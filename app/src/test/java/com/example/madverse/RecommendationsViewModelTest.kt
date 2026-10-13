package com.example.madverse

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.*
import com.example.madverse.core.domain.recommendation.*
import com.example.madverse.core.model.*
import com.example.madverse.feature.recommendations.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val saved = MutableStateFlow(false)
    private val profile = MutableStateFlow(UserPreferenceProfile(selectedGenreIds = setOf(28)))
    private val favoriteMovies = MutableStateFlow(emptyList<Movie>())
    private val watchlistMovies = MutableStateFlow(emptyList<Movie>())
    private var failWrites = false
    private var requests = 0
    private var failFetch = false
    private val quiz = object : QuizRepository {
        override fun hasSavedProfile() = saved
        override fun getUserPreferenceProfile() = profile
        override suspend fun getUserPreferenceProfileOnce() = profile.value
        override suspend fun saveUserPreferenceProfile(profile: UserPreferenceProfile) { this@RecommendationsViewModelTest.profile.value = profile; saved.value = true }
        override suspend fun clearUserPreferenceProfile() { saved.value = false }
        override fun getQuizQuestions() = emptyList<QuizQuestion>()
    }
    private val movies = object : DiscoveryTestRepository() {
        override fun discoverCandidateMovies(genreIds: Set<Int>, startYear: Int?, endYear: Int?, minRating: Double?, page: Int): Flow<Resource<List<Movie>>> {
            requests++
            return flowOf(if (failFetch) Resource.Error("Offline") else Resource.Success(listOf(discoveryMovie(1), discoveryMovie(2))))
        }
    }
    private val favorites = object : FavoritesRepository {
        override fun getFavoriteMovies() = favoriteMovies
        override fun isFavorite(movieId: Int) = favoriteMovies.map { list -> list.any { it.id == movieId } }
        override suspend fun isFavoriteOnce(movieId: Int) = favoriteMovies.value.any { it.id == movieId }
        override suspend fun addToFavorites(movie: Movie) { if (failWrites) error("Disk full"); favoriteMovies.value += movie }
        override suspend fun removeFromFavorites(movieId: Int) { favoriteMovies.value = favoriteMovies.value.filterNot { it.id == movieId } }
    }
    private val watchlist = object : WatchlistRepository {
        override fun getWatchlistMovies() = watchlistMovies
        override fun isMovieInWatchlist(movieId: Int) = watchlistMovies.map { list -> list.any { it.id == movieId } }
        override suspend fun isMovieInWatchlistOnce(movieId: Int) = watchlistMovies.value.any { it.id == movieId }
        override suspend fun addToWatchlist(movie: Movie) { watchlistMovies.value += movie }
        override suspend fun removeFromWatchlist(movieId: Int) { watchlistMovies.value = watchlistMovies.value.filterNot { it.id == movieId } }
    }
    private val engine = object : RecommendationEngine {
        override fun scoreAndRank(profile: UserPreferenceProfile, candidates: List<Movie>, limit: Int) =
            candidates.reversed().map { ScoredMovieRecommendation(it, 63, listOf("Engine reason")) }
    }
    private fun create(provider: RecommendationInsightProvider = LocalRecommendationInsightProvider()) = RecommendationsViewModel(
        quiz, GetPersonalizedRecommendationsUseCase(quiz, movies, engine), favorites, watchlist, provider)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun missingProfilePromptsQuizWithoutFetchingAndSavedProfileRefreshes() = runTest(dispatcher) {
        val vm = create(); advanceUntilIdle()
        assertTrue(vm.state.value.needsQuiz); assertEquals(0, requests)
        saved.value = true; advanceUntilIdle()
        assertFalse(vm.state.value.needsQuiz)
        val rows = (vm.state.value.results as UiState.Success).data
        assertEquals(listOf(2, 1), rows.map { it.recommendation.movie.id })
        assertEquals(listOf("Engine reason"), rows.first().insight.reasons)
        profile.value = profile.value.copy(preferredLanguage = "hi"); advanceUntilIdle()
        assertEquals(2, requests)
        saved.value = false; advanceUntilIdle()
        assertTrue(vm.state.value.needsQuiz)
    }
    @Test fun libraryActionsObserveRepositoryAndRecoverFromWriteFailure() = runTest(dispatcher) {
        val vm = create(); advanceUntilIdle()
        vm.toggleFavorite(discoveryMovie()); vm.toggleFavorite(discoveryMovie()); advanceUntilIdle()
        assertEquals(setOf(1), vm.state.value.favorites)
        vm.toggleWatchlist(discoveryMovie()); advanceUntilIdle()
        assertEquals(setOf(1), vm.state.value.watchlist)
        vm.toggleFavorite(discoveryMovie()); advanceUntilIdle()
        assertTrue(vm.state.value.favorites.isEmpty())
        failWrites = true; vm.toggleFavorite(discoveryMovie()); advanceUntilIdle()
        assertNotNull(vm.state.value.actionError)
        assertTrue(vm.state.value.busy.isEmpty())
    }
    @Test fun failedProviderUsesLocalReasonsAndNetworkRetryWorks() = runTest(dispatcher) {
        saved.value = true; failFetch = true
        val vm = create(object : RecommendationInsightProvider {
            override fun explain(recommendation: ScoredMovieRecommendation, profile: UserPreferenceProfile): RecommendationInsight = error("Unavailable")
        })
        advanceUntilIdle()
        assertTrue(vm.state.value.results is UiState.Error)
        failFetch = false; vm.refresh(); advanceUntilIdle()
        val rows = (vm.state.value.results as UiState.Success).data
        assertEquals(listOf("Engine reason"), rows.first().insight.reasons)
    }
}
