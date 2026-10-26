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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private var restoreJob: Job? = null

    init {
        restoreExistingPreferences()
    }

    /**
     * Restores existing user preference profile from Room on launch.
     */
    fun restoreExistingPreferences() {
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
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
            } catch (e: CancellationException) {
                throw e
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
        if (!currentState.isCurrentStepValid || currentState.isLoading || currentState.isSaving) return

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
        if (currentState.canGoBack && !currentState.isSaving && !currentState.isLoading) {
            val prevStep = QuestionStep.entries[currentState.currentStep.ordinal - 1]
            _uiState.update { it.copy(currentStep = prevStep) }
        }
    }

    /**
     * Navigates directly to a specific step.
     */
    fun goToStep(step: QuestionStep) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
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
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(showCompletionConfirmation = false, saveErrorMessage = null) }
    }

    /**
     * Persists quiz answers into Room via [QuizRepository] and marks completion.
     */
    fun submitQuiz(onComplete: () -> Unit = {}) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        val profile = _uiState.value.answers.toUserPreferenceProfile()
        _uiState.update { it.copy(isSaving = true, errorMessage = null, saveErrorMessage = null) }
        viewModelScope.launch {
            try {
                quizRepository.saveUserPreferenceProfile(profile)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isComplete = true,
                        showCompletionConfirmation = false
                    )
                }
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showCompletionConfirmation = true,
                        saveErrorMessage = e.localizedMessage ?: "Failed to save preferences."
                    )
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /**
     * Clears local answers and resets quiz flow to Step 1.
     */
    fun resetQuiz() {
        if (_uiState.value.isSaving) return
        restoreJob?.cancel()
        _uiState.update {
            it.copy(
                currentStep = QuestionStep.GENRES,
                isLoading = false,
                answers = QuizAnswers(),
                isComplete = false,
                errorMessage = null,
                saveErrorMessage = null,
                showCompletionConfirmation = false
            )
        }
    }
}
