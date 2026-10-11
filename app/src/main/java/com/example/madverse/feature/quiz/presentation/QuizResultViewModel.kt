package com.example.madverse.feature.quiz.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.domain.recommendation.GetPersonalizedRecommendationsUseCase
import com.example.madverse.core.model.ScoredMovieRecommendation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing recommendation results generated from the persisted quiz taste profile.
 */
@HiltViewModel
class QuizResultViewModel @Inject constructor(
    private val getPersonalizedRecommendationsUseCase: GetPersonalizedRecommendationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<ScoredMovieRecommendation>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ScoredMovieRecommendation>>> = _uiState.asStateFlow()

    init {
        loadRecommendations()
    }

    /**
     * Executes the recommendation pipeline using the persisted preference profile.
     */
    fun loadRecommendations(limit: Int = 12) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getPersonalizedRecommendationsUseCase(limit = limit).collect { resource ->
                _uiState.value = when (resource) {
                    is Resource.Loading -> UiState.Loading
                    is Resource.Success -> {
                        if (resource.data.isEmpty()) {
                            UiState.Empty("No movie recommendations matched your exact criteria. Try adjusting your mood or era.")
                        } else {
                            UiState.Success(resource.data)
                        }
                    }
                    is Resource.Error -> UiState.Error(
                        message = resource.message ?: "Failed to generate recommendations. Please try again."
                    )
                }
            }
        }
    }
}
