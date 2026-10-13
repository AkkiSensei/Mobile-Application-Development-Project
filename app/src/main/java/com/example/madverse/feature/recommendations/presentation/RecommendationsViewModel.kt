package com.example.madverse.feature.recommendations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.*
import com.example.madverse.core.domain.recommendation.*
import com.example.madverse.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecommendationRow(val recommendation: ScoredMovieRecommendation, val insight: RecommendationInsight)
data class RecommendationsState(
    val results: UiState<List<RecommendationRow>> = UiState.Loading,
    val needsQuiz: Boolean = false,
    val favorites: Set<Int> = emptySet(),
    val watchlist: Set<Int> = emptySet(),
    val busy: Set<Int> = emptySet(),
    val actionError: String? = null
)

@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val quiz: QuizRepository,
    private val recommendations: GetPersonalizedRecommendationsUseCase,
    private val favorites: FavoritesRepository,
    private val watchlist: WatchlistRepository,
    private val insights: RecommendationInsightProvider
) : ViewModel() {
    private val _state = MutableStateFlow(RecommendationsState())
    val state = _state.asStateFlow()
    private var request: Job? = null

    init {
        viewModelScope.launch {
            favorites.getFavoriteMovies().catch { _state.update { it.copy(actionError = "Unable to read favorites.") } }
                .collect { movies -> _state.update { it.copy(favorites = movies.map(Movie::id).toSet()) } }
        }
        viewModelScope.launch {
            watchlist.getWatchlistMovies().catch { _state.update { it.copy(actionError = "Unable to read watchlist.") } }
                .collect { movies -> _state.update { it.copy(watchlist = movies.map(Movie::id).toSet()) } }
        }
        refresh()
    }

    fun refresh() {
        request?.cancel()
        request = viewModelScope.launch {
            try {
                quiz.hasSavedProfile().collectLatest { saved ->
                    if (!saved) {
                        _state.update { it.copy(needsQuiz = true, results = UiState.Empty("Complete your taste quiz to get personalized recommendations.")) }
                    } else {
                        quiz.getUserPreferenceProfile().collectLatest { profile ->
                            _state.update { it.copy(needsQuiz = false, results = UiState.Loading) }
                            recommendations.invokeWithProfile(profile, limit = 20).collect { result ->
                                _state.update { it.copy(results = when (result) {
                                    Resource.Loading -> UiState.Loading
                                    is Resource.Error -> UiState.Error(result.message)
                                    is Resource.Success -> if (result.data.isEmpty()) UiState.Empty("No matches found. Try updating your preferences.")
                                        else UiState.Success(result.data.map { recommendation ->
                                            val insight = try { insights.explain(recommendation, profile) }
                                                catch (e: CancellationException) { throw e }
                                                catch (_: Exception) { LocalRecommendationInsightProvider().explain(recommendation, profile) }
                                            RecommendationRow(recommendation, insight)
                                        })
                                }) }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(results = UiState.Error("Unable to load recommendations. Please retry.")) } }
        }
    }

    fun dismissError() { _state.update { it.copy(actionError = null) } }
    fun toggleFavorite(movie: Movie) = changeLibrary(movie) {
        if (favorites.isFavoriteOnce(movie.id)) favorites.removeFromFavorites(movie.id) else favorites.addToFavorites(movie)
    }
    fun toggleWatchlist(movie: Movie) = changeLibrary(movie) {
        if (watchlist.isMovieInWatchlistOnce(movie.id)) watchlist.removeFromWatchlist(movie.id) else watchlist.addToWatchlist(movie)
    }
    private fun changeLibrary(movie: Movie, action: suspend () -> Unit) {
        if (movie.id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + movie.id, actionError = null) }
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(actionError = "Could not update your library. Please try again.") } }
            finally { _state.update { it.copy(busy = it.busy - movie.id) } }
        }
    }
}
