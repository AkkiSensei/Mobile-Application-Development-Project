package com.example.madverse.feature.discover.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * ViewModel managing discovery and home feed state for MADVerse.
 * Integrates directly with [MovieRepository] established in R1 without duplicating API services.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAllSections()
    }

    /**
     * Loads or reloads all three movie discovery sections concurrently.
     */
    fun loadAllSections() {
        loadTrending()
        loadTopRated()
        loadUpcoming()
    }

    /**
     * Pull-to-refresh or full reload triggering fresh network fetches.
     */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadAllSections()
        _uiState.update { it.copy(isRefreshing = false) }
    }

    /**
     * Retries or fetches trending movies.
     */
    fun loadTrending(page: Int = 1) {
        viewModelScope.launch {
            movieRepository.getTrendingMovies(page).collect { resource ->
                _uiState.update { current ->
                    current.copy(
                        trendingState = resource.toUiState(
                            emptyMessage = "No trending movies found at this time."
                        )
                    )
                }
            }
        }
    }

    /**
     * Retries or fetches top rated movies.
     */
    fun loadTopRated(page: Int = 1) {
        viewModelScope.launch {
            movieRepository.getTopRatedMovies(page).collect { resource ->
                _uiState.update { current ->
                    current.copy(
                        topRatedState = resource.toUiState(
                            emptyMessage = "No top-rated movies found at this time."
                        )
                    )
                }
            }
        }
    }

    /**
     * Retries or fetches upcoming movies.
     */
    fun loadUpcoming(page: Int = 1) {
        viewModelScope.launch {
            movieRepository.getUpcomingMovies(page).collect { resource ->
                _uiState.update { current ->
                    current.copy(
                        upcomingState = resource.toUiState(
                            emptyMessage = "No upcoming releases scheduled."
                        )
                    )
                }
            }
        }
    }

    private fun Resource<List<Movie>>.toUiState(emptyMessage: String): UiState<List<Movie>> {
        return when (this) {
            is Resource.Loading -> UiState.Loading
            is Resource.Success -> {
                if (data.isEmpty()) {
                    UiState.Empty(emptyMessage)
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

typealias DiscoverViewModel = HomeViewModel
