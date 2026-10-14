package com.example.madverse.feature.freemovies.presentation

import androidx.lifecycle.ViewModel
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.FreeMovieRepository
import com.example.madverse.core.model.FreeMovie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FreeMoviesViewModel @Inject constructor(private val repository: FreeMovieRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<FreeMovie>>>(UiState.Loading)
    val state = _state.asStateFlow()
    init { load() }
    fun load() {
        _state.value = try {
            val movies = repository.getMovies()
            if (movies.isEmpty()) UiState.Empty("No films have verified publishing evidence yet.") else UiState.Success(movies)
        } catch (_: Exception) { UiState.Error("The local catalogue could not be loaded.") }
    }
}
