package com.example.madverse.feature.comparison.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComparisonEntry(val id: Int, val movie: Movie? = null, val details: MovieDetails? = null,
    val loading: Boolean = true, val error: String? = null)
data class ComparisonState(val entries: List<ComparisonEntry> = emptyList(), val query: String = "",
    val results: List<Movie> = emptyList(), val searching: Boolean = false, val searchError: String? = null,
    val replacingId: Int? = null)

@HiltViewModel
class ComparisonViewModel @Inject constructor(private val repository: MovieRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(ComparisonState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null
    private val detailJobs = mutableMapOf<Int, Job>()

    init {
        val restored = savedState.get<IntArray>("selectedIds")?.toList()
            ?: listOfNotNull(savedState.get<Int>("movieId")?.takeIf { it > 0 })
        _state.value = ComparisonState(entries = restored.distinct().take(3).map { ComparisonEntry(it) })
        _state.value.entries.forEach { loadDetails(it.id) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(query = query, results = emptyList(), searching = query.isNotBlank(), searchError = null) }
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(350)
            repository.searchMovies(query.trim()).catch { emit(Resource.Error("Search failed. Please retry.")) }.collect { result ->
                when (result) {
                    Resource.Loading -> Unit
                    is Resource.Error -> _state.update { it.copy(searching = false, searchError = result.message) }
                    is Resource.Success -> _state.update { it.copy(searching = false, results = result.data.distinctBy(Movie::id)) }
                }
            }
        }
    }

    fun select(movie: Movie) {
        val current = _state.value
        if (current.entries.any { it.id == movie.id }) return
        val replace = current.replacingId
        if (current.entries.size >= 3 && replace == null) return
        if (replace != null) detailJobs.remove(replace)?.cancel()
        val entry = ComparisonEntry(movie.id, movie)
        _state.update { it.copy(entries = if (replace == null) it.entries + entry else it.entries.map { old -> if (old.id == replace) entry else old }, replacingId = null) }
        persist()
        loadDetails(movie.id)
    }

    fun replace(id: Int?) { _state.update { it.copy(replacingId = id) } }
    fun remove(id: Int) {
        detailJobs.remove(id)?.cancel()
        _state.update { it.copy(entries = it.entries.filterNot { entry -> entry.id == id }, replacingId = it.replacingId?.takeUnless { replacement -> replacement == id }) }
        persist()
    }
    private fun persist() { savedState["selectedIds"] = _state.value.entries.map { it.id }.toIntArray() }

    fun loadDetails(id: Int) {
        detailJobs.remove(id)?.cancel()
        updateEntry(id) { it.copy(loading = true, error = null) }
        detailJobs[id] = viewModelScope.launch {
            repository.getMovieDetails(id).catch { emit(Resource.Error("Details unavailable. Please retry.")) }.collect { result ->
                when (result) {
                    Resource.Loading -> Unit
                    is Resource.Error -> updateEntry(id) { it.copy(loading = false, error = result.message) }
                    is Resource.Success -> updateEntry(id) { it.copy(loading = false, details = result.data, error = null) }
                }
            }
        }
    }
    private fun updateEntry(id: Int, transform: (ComparisonEntry) -> ComparisonEntry) {
        _state.update { it.copy(entries = it.entries.map { entry -> if (entry.id == id) transform(entry) else entry }) }
    }
}
