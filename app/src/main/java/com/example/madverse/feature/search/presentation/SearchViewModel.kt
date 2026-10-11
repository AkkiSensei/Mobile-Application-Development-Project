package com.example.madverse.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
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
        _uiState.update { it.copy(query = newQuery) }

        val trimmed = newQuery.trim()
        if (trimmed.isEmpty()) {
            searchJob?.cancel()
            searchJob = null
            _uiState.update {
                it.copy(
                    searchState = UiState.Empty(SearchUiState.DEFAULT_EMPTY_PROMPT),
                    activeQuery = ""
                )
            }
            return
        }

        // Cancel obsolete in-flight or pending search job
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceDurationMillis > 0) {
                delay(debounceDurationMillis)
            }
            executeSearch(trimmed)
        }
    }

    /**
     * Instantly triggers search without waiting for debounce, e.g. when user presses
     * Search/Enter on software keyboard or selects a quick suggestion chip.
     */
    fun onSearchSubmitted(submittedQuery: String = _uiState.value.query) {
        val trimmed = submittedQuery.trim()
        if (trimmed.isEmpty()) return

        _uiState.update { it.copy(query = submittedQuery) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(trimmed)
        }
    }

    /**
     * Clears the search input and cancels any pending or active search.
     */
    fun onClearQuery() {
        searchJob?.cancel()
        searchJob = null
        _uiState.update {
            it.copy(
                query = "",
                activeQuery = "",
                searchState = UiState.Empty(SearchUiState.DEFAULT_EMPTY_PROMPT)
            )
        }
    }

    /**
     * Retries search with the current query.
     */
    fun retry() {
        val trimmed = _uiState.value.query.trim()
        if (trimmed.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                executeSearch(trimmed)
            }
        }
    }

    private suspend fun executeSearch(queryToSearch: String) {
        _uiState.update { it.copy(searchState = UiState.Loading) }

        movieRepository.searchMovies(query = queryToSearch).collect { resource ->
            // Guard against stale results if query changed while request was in-flight
            if (_uiState.value.query.trim() != queryToSearch) {
                return@collect
            }

            _uiState.update { current ->
                current.copy(
                    activeQuery = queryToSearch,
                    searchState = resource.toUiState(queryToSearch)
                )
            }
        }
    }

    private fun Resource<List<Movie>>.toUiState(querySearched: String): UiState<List<Movie>> {
        return when (this) {
            is Resource.Loading -> UiState.Loading
            is Resource.Success -> {
                if (data.isEmpty()) {
                    UiState.Empty("No movies found for \"$querySearched\".")
                } else {
                    UiState.Success(data)
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
