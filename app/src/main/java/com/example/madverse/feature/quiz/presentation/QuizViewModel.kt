package com.example.madverse.feature.quiz.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing quiz step progression, answer caching, and profile restoration.
 * Integrates directly with [QuizRepository] for Room preference persistence.
 */
@HiltViewModel
class QuizViewModel @Inject constructor(
    private val quizRepository: QuizRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState(isLoading = true))
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    init {
        restoreExistingPreferences()
    }

    /**
     * Restores existing user preference profile from Room on launch.
     */
    fun restoreExistingPreferences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val existingProfile = quizRepository.getUserPreferenceProfileOnce()
                if (existingProfile.selectedGenreIds.isNotEmpty()) {
                    val restoredAnswers = QuizAnswers.fromUserProfile(existingProfile)
                    _uiState.update { current ->
                        current.copy(
                            answers = restoredAnswers,
                            isLoading = false,
                            initialProfileRestored = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, initialProfileRestored = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to restore existing preferences."
                    )
                }
            }
        }
    }

    /**
     * Advances to the next question step or triggers completion dialog if on the final step.
     */
    fun goToNextStep() {
        val currentState = _uiState.value
        if (!currentState.isCurrentStepValid) return

        if (currentState.isLastStep) {
            _uiState.update { it.copy(showCompletionConfirmation = true) }
        } else {
            val nextStep = QuestionStep.entries[currentState.currentStep.ordinal + 1]
            _uiState.update { it.copy(currentStep = nextStep) }
        }
    }

    /**
     * Navigates back to the preceding question step if available.
     */
    fun goToPreviousStep() {
        val currentState = _uiState.value
        if (currentState.canGoBack) {
            val prevStep = QuestionStep.entries[currentState.currentStep.ordinal - 1]
            _uiState.update { it.copy(currentStep = prevStep) }
        }
    }

    /**
     * Navigates directly to a specific step.
     */
    fun goToStep(step: QuestionStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    /**
     * Toggles inclusion of a genre ID in Step 1.
     */
    fun toggleGenre(genreId: Int) {
        _uiState.update { current ->
            val updatedGenres = current.answers.selectedGenreIds.toMutableSet()
            if (updatedGenres.contains(genreId)) {
                updatedGenres.remove(genreId)
            } else {
                updatedGenres.add(genreId)
            }
            current.copy(answers = current.answers.copy(selectedGenreIds = updatedGenres))
        }
    }

    /**
     * Sets preferred original audio language in Step 2.
     */
    fun setPreferredLanguage(languageCode: String?) {
        _uiState.update { current ->
            current.copy(answers = current.answers.copy(preferredLanguage = languageCode))
        }
    }

    /**
     * Sets viewing mood in Step 3.
     */
    fun setMood(mood: MovieMood) {
        _uiState.update { current ->
            current.copy(answers = current.answers.copy(mood = mood))
        }
    }

    /**
     * Sets cinematic release era in Step 4.
     */
    fun setEra(era: MovieEra) {
        _uiState.update { current ->
            current.copy(answers = current.answers.copy(era = era))
        }
    }

    /**
     * Sets ideal runtime range in Step 5.
     */
    fun setRuntimePreference(runtime: RuntimePreference) {
        _uiState.update { current ->
            current.copy(answers = current.answers.copy(runtimePreference = runtime))
        }
    }

    /**
     * Sets minimum rating threshold in Step 6.
     */
    fun setMinRatingThreshold(rating: Double) {
        _uiState.update { current ->
            current.copy(answers = current.answers.copy(minRatingThreshold = rating))
        }
    }

    /**
     * Dismisses the completion confirmation dialog.
     */
    fun dismissCompletionDialog() {
        _uiState.update { it.copy(showCompletionConfirmation = false) }
    }

    /**
     * Persists quiz answers into Room via [QuizRepository] and marks completion.
     */
    fun submitQuiz(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val profile = _uiState.value.answers.toUserPreferenceProfile()
                quizRepository.saveUserPreferenceProfile(profile)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isComplete = true,
                        showCompletionConfirmation = false
                    )
                }
                onComplete()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.localizedMessage ?: "Failed to save preferences."
                    )
                }
            }
        }
    }

    /**
     * Clears local answers and resets quiz flow to Step 1.
     */
    fun resetQuiz() {
        _uiState.update {
            it.copy(
                currentStep = QuestionStep.GENRES,
                answers = QuizAnswers(),
                isComplete = false,
                showCompletionConfirmation = false
            )
        }
    }
}
