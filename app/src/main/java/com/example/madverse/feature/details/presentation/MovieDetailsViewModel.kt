package com.example.madverse.feature.details.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * ViewModel managing Movie Details state for MADVerse.
 *
 * Implements R4: Movie Details with:
 * - Comprehensive movie metadata, genres, runtime, and financial stats
 * - Video trailers & clips metadata integration
 * - Streaming watch-provider availability integration point
 * - Favorites and Watchlist persistence integration points using R1 repositories
 * - Resilient error mapping and retry handling
 */
@HiltViewModel
class MovieDetailsViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val favoritesRepository: FavoritesRepository,
    private val watchlistRepository: WatchlistRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailsUiState())
    val uiState: StateFlow<MovieDetailsUiState> = _uiState.asStateFlow()

    var currentMovieId: Int = 0
        private set

    init {
        val navMovieId = savedStateHandle.get<Int>(Screen.ARG_MOVIE_ID) ?: 0
        if (navMovieId > 0) {
            loadMovieDetails(navMovieId)
        }
    }

    /**
     * Loads all movie details, cast, video metadata, streaming availability,
     * and local library persistence status for the given movie ID.
     */
    fun loadMovieDetails(movieId: Int) {
        currentMovieId = movieId

        if (movieId <= 0) {
            _uiState.update {
                it.copy(
                    detailsState = UiState.Error(
                        message = "Invalid movie ID ($movieId). Please select a valid movie.",
                        isOffline = false
                    )
                )
            }
            return
        }

        fetchDetails(movieId)
        fetchCast(movieId)
        fetchVideos(movieId)
        fetchWatchProviders(movieId)
        observeFavorites(movieId)
        observeWatchlist(movieId)
    }

    /**
     * Retries loading movie details for the current movie ID.
     */
    fun retry() {
        if (currentMovieId > 0) {
            loadMovieDetails(currentMovieId)
        }
    }

    /**
     * Toggles the favorite status in the local Room database via [FavoritesRepository].
     */
    fun toggleFavorite() {
        val details = (_uiState.value.detailsState as? UiState.Success)?.data ?: return
        val movie = details.toMovie()

        viewModelScope.launch {
            if (_uiState.value.isFavorite) {
                favoritesRepository.removeFromFavorites(movie.id)
            } else {
                favoritesRepository.addToFavorites(movie)
            }
        }
    }

    /**
     * Toggles the watchlist status in the local Room database via [WatchlistRepository].
     */
    fun toggleWatchlist() {
        val details = (_uiState.value.detailsState as? UiState.Success)?.data ?: return
        val movie = details.toMovie()

        viewModelScope.launch {
            if (_uiState.value.isInWatchlist) {
                watchlistRepository.removeFromWatchlist(movie.id)
            } else {
                watchlistRepository.addToWatchlist(movie)
            }
        }
    }

    private fun fetchDetails(movieId: Int) {
        viewModelScope.launch {
            movieRepository.getMovieDetails(movieId).collect { resource ->
                _uiState.update { current ->
                    current.copy(
                        detailsState = when (resource) {
                            is Resource.Loading -> UiState.Loading
                            is Resource.Success -> UiState.Success(resource.data)
                            is Resource.Error -> {
                                val isOffline = resource.cause is IOException ||
                                        resource.message.contains("network", ignoreCase = true) ||
                                        resource.message.contains("connection", ignoreCase = true)
                                UiState.Error(resource.message, isOffline = isOffline)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun fetchCast(movieId: Int) {
        viewModelScope.launch {
            movieRepository.getMovieCredits(movieId).collect { resource ->
                _uiState.update { current ->
                    current.copy(
                        castState = when (resource) {
                            is Resource.Loading -> UiState.Loading
                            is Resource.Success -> {
                                if (resource.data.isEmpty()) {
                                    UiState.Empty("No cast information listed.")
                                } else {
                                    UiState.Success(resource.data)
                                }
                            }
                            is Resource.Error -> UiState.Error(resource.message)
                        }
                    )
                }
            }
        }
    }

    private fun fetchVideos(movieId: Int) {
        viewModelScope.launch {
            movieRepository.getMovieVideos(movieId).collect { resource ->
                _uiState.update { current ->
                    current.copy(
                        videosState = when (resource) {
                            is Resource.Loading -> UiState.Loading
                            is Resource.Success -> {
                                if (resource.data.isEmpty()) {
                                    UiState.Empty("No official trailers or clips listed.")
                                } else {
                                    UiState.Success(resource.data)
                                }
                            }
                            is Resource.Error -> UiState.Error(resource.message)
                        }
                    )
                }
            }
        }
    }

    private fun fetchWatchProviders(movieId: Int) {
        viewModelScope.launch {
            movieRepository.getMovieWatchProviders(movieId, countryCode = "US").collect { resource ->
                _uiState.update { current ->
                    current.copy(
                        watchProvidersState = when (resource) {
                            is Resource.Loading -> UiState.Loading
                            is Resource.Success -> {
                                if (!resource.data.hasAnyProviders) {
                                    UiState.Empty("No streaming providers currently reported.")
                                } else {
                                    UiState.Success(resource.data)
                                }
                            }
                            is Resource.Error -> UiState.Error(resource.message)
                        }
                    )
                }
            }
        }
    }

    private fun observeFavorites(movieId: Int) {
        viewModelScope.launch {
            favoritesRepository.isFavorite(movieId).collect { isFav ->
                _uiState.update { it.copy(isFavorite = isFav) }
            }
        }
    }

    private fun observeWatchlist(movieId: Int) {
        viewModelScope.launch {
            watchlistRepository.isMovieInWatchlist(movieId).collect { inWl ->
                _uiState.update { it.copy(isInWatchlist = inWl) }
            }
        }
    }
}
