package com.example.madverse.feature.genres.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.model.Genre
import com.example.madverse.core.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MovieSort(val label: String) { POPULARITY("Popularity"), RATING("Rating"), RELEASE("Release date") }

data class GenreExplorerState(
    val genres: List<Genre> = emptyList(),
    val genreError: String? = null,
    val genresLoading: Boolean = true,
    val genreId: Int? = null,
    val startYear: String = "",
    val endYear: String = "",
    val minRating: Int = 0,
    val sort: MovieSort = MovieSort.POPULARITY,
    val movies: List<Movie> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val filterError: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = true
) {
    val sortedMovies: List<Movie> get() = when (sort) {
        MovieSort.POPULARITY -> movies.sortedByDescending { it.popularity }
        MovieSort.RATING -> movies.sortedByDescending { it.voteAverage }
        MovieSort.RELEASE -> movies.sortedByDescending { it.releaseDate.orEmpty() }
    }
}

@HiltViewModel
class GenreExplorerViewModel @Inject constructor(private val repository: MovieRepository) : ViewModel() {
    private val _state = MutableStateFlow(GenreExplorerState())
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var genreRequest: Job? = null
    private var applied = GenreExplorerState()

    init { loadGenres(); applyFilters() }

    fun loadGenres() {
        genreRequest?.cancel()
        genreRequest = viewModelScope.launch {
            repository.getGenres().catch { emit(Resource.Error("Unable to load genres.")) }.collect { result ->
                _state.update { it.copy(genresLoading = result is Resource.Loading, genreError = (result as? Resource.Error)?.message,
                    genres = (result as? Resource.Success)?.data ?: it.genres) }
            }
        }
    }

    fun selectGenre(id: Int?) { _state.update { it.copy(genreId = id) }; applyFilters() }
    fun editYears(start: String, end: String) { _state.update { it.copy(startYear = start, endYear = end, filterError = null) } }
    fun setRating(value: Int) { _state.update { it.copy(minRating = value.coerceIn(0, 10)) } }
    fun setSort(sort: MovieSort) { _state.update { it.copy(sort = sort) } }

    fun applyFilters() {
        val filters = _state.value
        val start = filters.startYear.toIntOrNull()
        val end = filters.endYear.toIntOrNull()
        if ((filters.startYear.isNotBlank() && (start == null || start !in 1870..2100)) ||
            (filters.endYear.isNotBlank() && (end == null || end !in 1870..2100)) ||
            (start != null && end != null && start > end)) {
            _state.update { it.copy(filterError = "Enter years from 1870 to 2100, with the start no later than the end.") }
            return
        }
        applied = filters
        request?.cancel()
        _state.update { it.copy(movies = emptyList(), page = 0, hasMore = true, filterError = null, loading = false) }
        loadMore()
    }

    fun loadMore() {
        if (_state.value.loading || !_state.value.hasMore) return
        val page = _state.value.page + 1
        val filters = applied
        _state.update { it.copy(loading = true, error = null) }
        request = viewModelScope.launch {
            repository.discoverCandidateMovies(
                genreIds = filters.genreId?.let { setOf(it) } ?: emptySet(),
                startYear = filters.startYear.toIntOrNull(), endYear = filters.endYear.toIntOrNull(),
                minRating = filters.minRating.toDouble(), page = page
            ).catch { emit(Resource.Error("Unable to load movies. Please retry.")) }.collect { result ->
                when (result) {
                    Resource.Loading -> Unit
                    is Resource.Error -> _state.update { it.copy(loading = false, error = result.message) }
                    is Resource.Success -> _state.update { it.copy(loading = false, error = null, page = page,
                        movies = (it.movies + result.data).distinctBy(Movie::id), hasMore = result.data.isNotEmpty() && page < 500) }
                }
            }
        }
    }
}
