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
    val replacingId: Int? = null, val selectionError: String? = null)

@HiltViewModel
class ComparisonViewModel @Inject constructor(private val repository: MovieRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(ComparisonState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null
    private val detailJobs = mutableMapOf<Int, Job>()
    private val detailVersions = mutableMapOf<Int, Long>()
    private var nextVersion = 0L
    private var searchVersion = 0L

    init {
        val restored = savedState.get<IntArray>("selectedIds")?.toList()
            ?: listOfNotNull(savedState.get<Int>("movieId")?.takeIf { it != 0 })
        _state.value = ComparisonState(entries = restored.distinct().take(3).map { ComparisonEntry(it) },
            replacingId = savedState.get<Int>("replacingId")?.takeIf { it in restored })
        _state.value.entries.forEach { loadDetails(it.id) }
        savedState.get<String>("query")?.takeIf(String::isNotBlank)?.let(::search)
    }

    fun search(query: String) {
        searchJob?.cancel()
        val version = ++searchVersion
        savedState["query"] = query
        _state.update { it.copy(query = query, results = emptyList(), searching = query.isNotBlank(), searchError = null) }
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(350)
            flow { emitAll(repository.searchMovies(query.trim())) }
                .catch { emit(Resource.Error("Search failed. Please retry.")) }.collect { result ->
                if (version != searchVersion) return@collect
                when (result) {
                    Resource.Loading -> Unit
                    is Resource.Error -> _state.update { it.copy(searching = false, searchError = result.message) }
                    is Resource.Success -> _state.update { it.copy(searching = false, searchError = null, results = result.data.filter { movie -> movie.id > 0 }.distinctBy(Movie::id)) }
                }
            }
            if (version == searchVersion && _state.value.searching) {
                _state.update { it.copy(searching = false, searchError = "Search returned no response. Please retry.") }
            }
        }
    }

    fun select(movie: Movie) {
        val current = _state.value
        if (movie.id <= 0 || current.entries.any { it.id == movie.id }) {
            _state.update { it.copy(selectionError = if (movie.id <= 0) "Please select a valid movie." else "That movie is already selected. Choose a different movie.") }
            return
        }
        val replace = current.replacingId
        if (current.entries.size >= 3 && replace == null) return
        if (replace != null) {
            detailVersions.remove(replace)
            detailJobs.remove(replace)?.cancel()
        }
        val entry = ComparisonEntry(movie.id, movie)
        _state.update { it.copy(entries = if (replace == null) it.entries + entry else it.entries.map { old -> if (old.id == replace) entry else old }, replacingId = null, selectionError = null) }
        persist()
        loadDetails(movie.id)
    }

    fun replace(id: Int?) {
        _state.update { it.copy(replacingId = id?.takeIf { candidate -> it.entries.any { entry -> entry.id == candidate } }, selectionError = null) }
        persist()
    }
    fun remove(id: Int) {
        detailVersions.remove(id)
        detailJobs.remove(id)?.cancel()
        _state.update { it.copy(entries = it.entries.filterNot { entry -> entry.id == id }, replacingId = it.replacingId?.takeUnless { replacement -> replacement == id }) }
        persist()
    }
    private fun persist() {
        savedState["selectedIds"] = _state.value.entries.map { it.id }.toIntArray()
        savedState["replacingId"] = _state.value.replacingId
    }

    fun loadDetails(id: Int) {
        if (_state.value.entries.none { it.id == id }) return
        detailJobs.remove(id)?.cancel()
        val version = ++nextVersion
        detailVersions[id] = version
        if (id <= 0) {
            updateEntry(id) { it.copy(loading = false, error = "Invalid movie ID. Remove or replace this movie.") }
            return
        }
        updateEntry(id) { it.copy(loading = true, details = null, error = null) }
        detailJobs[id] = viewModelScope.launch {
            flow { emitAll(repository.getMovieDetails(id)) }
                .catch { emit(Resource.Error("Details unavailable. Please retry.")) }.collect { result ->
                if (detailVersions[id] != version) return@collect
                when (result) {
                    Resource.Loading -> Unit
                    is Resource.Error -> updateEntry(id) { it.copy(loading = false, error = result.message) }
                    is Resource.Success -> updateEntry(id) {
                        if (result.data.id == id) it.copy(loading = false, details = result.data, error = null)
                        else it.copy(loading = false, error = "Details did not match this movie. Please retry.")
                    }
                }
            }
            if (detailVersions[id] == version) updateEntry(id) {
                if (it.loading) it.copy(loading = false, error = "Details returned no response. Please retry.") else it
            }
        }
    }
    private fun updateEntry(id: Int, transform: (ComparisonEntry) -> ComparisonEntry) {
        _state.update { it.copy(entries = it.entries.map { entry -> if (entry.id == id) transform(entry) else entry }) }
    }
}
