package com.example.madverse.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.ui.RepositoryActions
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.FavoritesRepository
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
 * ViewModel managing Favorites state and operations.
 * Backed by [FavoritesRepository] with reactive Room Flow observation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {
    private val libraryActions = RepositoryActions(viewModelScope)
    val actionError = libraryActions.error
    fun dismissActionError() = libraryActions.dismissError()


    private val _refreshTrigger = MutableStateFlow(0)

    private val _sortOption = MutableStateFlow(LibrarySortOption.RECENTLY_ADDED)
    val sortOption: StateFlow<LibrarySortOption> = _sortOption.asStateFlow()

    val uiState: StateFlow<UiState<List<Movie>>> = _refreshTrigger
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
