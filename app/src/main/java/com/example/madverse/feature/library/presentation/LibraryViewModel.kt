package com.example.madverse.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.ui.RepositoryActions
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.FavoritesRepository
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
 * Unified ViewModel for the Library screen managing tab selection,
 * sorting, and collection observations from both [FavoritesRepository] and [WatchlistRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {
    private val libraryActions = RepositoryActions(viewModelScope)
    val actionError = libraryActions.error
    fun dismissActionError() = libraryActions.dismissError()


    private val _selectedTab = MutableStateFlow(LibraryTab.WATCHLIST)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _sortOption = MutableStateFlow(LibrarySortOption.RECENTLY_ADDED)
    val sortOption: StateFlow<LibrarySortOption> = _sortOption.asStateFlow()

    private val _refreshFavoritesTrigger = MutableStateFlow(0)
    private val _refreshWatchlistTrigger = MutableStateFlow(0)

    val favoritesState: StateFlow<UiState<List<Movie>>> = _refreshFavoritesTrigger
        .flatMapLatest { _: Int ->
            combine(
                favoritesRepository.getFavoriteMovies(),
                _sortOption
            ) { movies: List<Movie>, sort: LibrarySortOption ->
                if (movies.isEmpty()) {
                    UiState.Empty("You haven't added any movies to your favorites yet.")
                } else {
                    UiState.Success(sortMovies(movies, sort))
                }
            }.catch { error: Throwable ->
                emit(UiState.Error(error.localizedMessage ?: "Failed to load favorite movies."))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    val watchlistState: StateFlow<UiState<List<Movie>>> = _refreshWatchlistTrigger
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

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun setSortOption(option: LibrarySortOption) {
        _sortOption.value = option
    }

    fun removeFromFavorites(movieId: Int) {
        libraryActions.run("favorite:${movieId}") {
            favoritesRepository.removeFromFavorites(movieId)
        }
    }

    fun addToFavorites(movie: Movie) {
        libraryActions.run("favorite:${movie.id}") {
            favoritesRepository.addToFavorites(movie)
        }
    }

    fun removeFromWatchlist(movieId: Int) {
        libraryActions.run("watchlist:${movieId}") {
            watchlistRepository.removeFromWatchlist(movieId)
        }
    }

    fun addToWatchlist(movie: Movie) {
        libraryActions.run("watchlist:${movie.id}") {
            watchlistRepository.addToWatchlist(movie)
        }
    }

    fun retryFavorites() {
        _refreshFavoritesTrigger.value += 1
    }

    fun retryWatchlist() {
        _refreshWatchlistTrigger.value += 1
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
