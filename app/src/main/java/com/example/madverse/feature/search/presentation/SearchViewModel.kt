package com.example.madverse.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * ViewModel managing movie search for MADVerse.
 *
 * Implements R3: Movie Search with:
 * - Real-time query debouncing (configurable, default 400ms)
 * - Obsolete request cancellation upon new input
 * - Immediate idle reset on empty/blank query
 * - Safe error mapping with offline detection
 * - Manual retry and clear actions
 * - Stale result avoidance
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var currentQuerySequence: Long = 0L
    private var executingQuery: String? = null

    /**
     * Configurable debounce duration to support immediate execution in unit tests.
     */
    var debounceDurationMillis: Long = 400L

    /**
     * Called whenever the user modifies the search input.
     * Updates text immediately, cancels any previous search in flight,
     * and triggers a debounced search query.
     */
    fun onQueryChanged(newQuery: String) {
        if (newQuery.trim() == _uiState.value.query.trim()) {
            _uiState.update { it.copy(query = newQuery) }
            return
        }
        startSearch(newQuery, debounceDurationMillis)
    }

    /** IME submit skips debounce; repeated submits share an already-running request. */
    fun onSearchSubmitted(submittedQuery: String = _uiState.value.query) {
        val trimmed = submittedQuery.trim()
        if (trimmed.isNotEmpty() && executingQuery == trimmed && searchJob?.isActive == true) {
            _uiState.update { it.copy(query = submittedQuery) }
            return
        }
        startSearch(submittedQuery, 0)
    }

    fun onClearQuery() = startSearch("", 0)

    /** Explicit retry remains a fresh attempt; there is no result cache for search. */
    fun retry() = onSearchSubmitted()

    private fun startSearch(query: String, debounceMillis: Long) {
        val sequence = ++currentQuerySequence
        searchJob?.cancel()
        searchJob = null
        executingQuery = null
        val trimmed = query.trim()
        _uiState.update {
            it.copy(query = query, activeQuery = trimmed,
                searchState = if (trimmed.isEmpty()) UiState.Empty(SearchUiState.DEFAULT_EMPTY_PROMPT) else UiState.Loading)
        }
        if (trimmed.isEmpty()) return
        searchJob = viewModelScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            if (sequence != currentQuerySequence) return@launch
            executingQuery = trimmed
            try {
                executeSearch(trimmed, sequence)
            } finally {
                if (sequence == currentQuerySequence) executingQuery = null
            }
        }
    }

    private suspend fun executeSearch(queryToSearch: String, sequence: Long) {
        try {
            movieRepository.searchMovies(query = queryToSearch).collect { resource ->
                if (sequence != currentQuerySequence || _uiState.value.query.trim() != queryToSearch) return@collect
                _uiState.update { it.copy(activeQuery = queryToSearch, searchState = resource.toUiState(queryToSearch)) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (sequence == currentQuerySequence) {
                _uiState.update { it.copy(searchState = Resource.Error("Search failed. Please retry.", error).toUiState(queryToSearch)) }
            }
        }
        if (sequence == currentQuerySequence && _uiState.value.searchState is UiState.Loading) {
            _uiState.update { it.copy(searchState = UiState.Error("Search returned no response. Please retry.")) }
        }
    }

    private fun Resource<List<Movie>>.toUiState(querySearched: String): UiState<List<Movie>> {
        return when (this) {
            is Resource.Loading -> UiState.Loading
            is Resource.Success -> {
                val uniqueMovies = data.filter { it.id > 0 }.distinctBy(Movie::id)
                if (uniqueMovies.isEmpty()) {
                    UiState.Empty("No movies found for \"$querySearched\".")
                } else {
                    UiState.Success(uniqueMovies)
                }
            }
            is Resource.Error -> {
                val isOffline = cause is IOException ||
                        message.contains("network", ignoreCase = true) ||
                        message.contains("connection", ignoreCase = true) ||
                        message.contains("offline", ignoreCase = true)
                UiState.Error(message = message, isOffline = isOffline)
            }
        }
    }
}
