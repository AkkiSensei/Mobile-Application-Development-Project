package com.example.madverse.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing Watchlist state and operations.
 * Backed by [WatchlistRepository] with reactive Room Flow observation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)

    private val _sortOption = MutableStateFlow(LibrarySortOption.RECENTLY_ADDED)
    val sortOption: StateFlow<LibrarySortOption> = _sortOption.asStateFlow()

    val uiState: StateFlow<UiState<List<Movie>>> = _refreshTrigger
        .flatMapLatest { _: Int ->
            combine(
                watchlistRepository.getWatchlistMovies(),
                _sortOption
            ) { movies: List<Movie>, sort: LibrarySortOption ->
                if (movies.isEmpty()) {
                    UiState.Empty("Your watchlist is empty. Add movies you want to watch later.")
                } else {
                    UiState.Success(sortMovies(movies, sort))
                }
            }.catch { error: Throwable ->
                emit(UiState.Error(error.localizedMessage ?: "Failed to load watchlist movies."))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    fun setSortOption(option: LibrarySortOption) {
        _sortOption.value = option
    }

    fun removeFromWatchlist(movieId: Int) {
        viewModelScope.launch {
            watchlistRepository.removeFromWatchlist(movieId)
        }
    }

    fun addToWatchlist(movie: Movie) {
        viewModelScope.launch {
            watchlistRepository.addToWatchlist(movie)
        }
    }

    fun retry() {
        _refreshTrigger.value += 1
    }

    private fun sortMovies(movies: List<Movie>, sort: LibrarySortOption): List<Movie> {
        return when (sort) {
            LibrarySortOption.RECENTLY_ADDED -> movies
            LibrarySortOption.RATING -> movies.sortedByDescending { it.voteAverage }
            LibrarySortOption.RELEASE_DATE -> movies.sortedByDescending { it.releaseDate ?: "" }
            LibrarySortOption.TITLE -> movies.sortedBy { it.title.lowercase() }
        }
    }
}
