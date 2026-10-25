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

    private var trendingJob: kotlinx.coroutines.Job? = null
    private var topRatedJob: kotlinx.coroutines.Job? = null
    private var upcomingJob: kotlinx.coroutines.Job? = null
    private var refreshJob: kotlinx.coroutines.Job? = null

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
        refreshJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            val j1 = launchTrending(1)
            val j2 = launchTopRated(1)
            val j3 = launchUpcoming(1)
            kotlinx.coroutines.joinAll(j1, j2, j3)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Retries or fetches trending movies.
     */
    fun loadTrending(page: Int = 1) {
        launchTrending(page)
    }

    /**
     * Retries or fetches top rated movies.
     */
    fun loadTopRated(page: Int = 1) {
        launchTopRated(page)
    }

    /**
     * Retries or fetches upcoming movies.
     */
    fun loadUpcoming(page: Int = 1) {
        launchUpcoming(page)
    }

    private fun launchTrending(page: Int): kotlinx.coroutines.Job {
        trendingJob?.cancel()
        val job = viewModelScope.launch {
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
        trendingJob = job
        return job
    }

    private fun launchTopRated(page: Int): kotlinx.coroutines.Job {
        topRatedJob?.cancel()
        val job = viewModelScope.launch {
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
        topRatedJob = job
        return job
    }

    private fun launchUpcoming(page: Int): kotlinx.coroutines.Job {
        upcomingJob?.cancel()
        val job = viewModelScope.launch {
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
        upcomingJob = job
        return job
    }

    private fun Resource<List<Movie>>.toUiState(emptyMessage: String): UiState<List<Movie>> {
        return when (this) {
            is Resource.Loading -> UiState.Loading
            is Resource.Success -> {
                val uniqueMovies = data.filter { it.id > 0 }.distinctBy(Movie::id)
                if (uniqueMovies.isEmpty()) {
                    UiState.Empty(emptyMessage)
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

typealias DiscoverViewModel = HomeViewModel
