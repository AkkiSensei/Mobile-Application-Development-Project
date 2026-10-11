package com.example.madverse.feature.quiz.presentation

import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile

/**
 * Sequential question steps for the Movie Preference Taste Quiz.
 */
enum class QuestionStep(
    val stepNumber: Int,
    val title: String,
    val subtitle: String
) {
    GENRES(
        stepNumber = 1,
        title = "Favorite Genres",
        subtitle = "Select one or more genres that excite you most."
    ),
    LANGUAGE(
        stepNumber = 2,
        title = "Preferred Language",
        subtitle = "Choose your preferred original audio language or explore all."
    ),
    MOOD(
        stepNumber = 3,
        title = "Viewing Mood",
        subtitle = "What emotional atmosphere are you craving tonight?"
    ),
    ERA(
        stepNumber = 4,
        title = "Release Era",
        subtitle = "Which era of cinematic storytelling resonates with you?"
    ),
    RUNTIME(
        stepNumber = 5,
        title = "Ideal Runtime",
        subtitle = "How much time do you want to invest in this viewing session?"
    ),
    RATING(
        stepNumber = 6,
        title = "Quality Baseline",
        subtitle = "Set your minimum acceptable critic and audience score."
    )
}

/**
 * Supported language options in the preference quiz.
 */
data class QuizLanguageOption(
    val code: String?,
    val displayName: String,
    val nativeName: String
) {
    companion object {
        val ALL_LANGUAGES = listOf(
            QuizLanguageOption(null, "Any Language", "All World Cinema"),
            QuizLanguageOption("en", "English", "English"),
            QuizLanguageOption("hi", "Hindi", "हिन्दी"),
            QuizLanguageOption("fr", "French", "Français"),
            QuizLanguageOption("es", "Spanish", "Español"),
            QuizLanguageOption("ja", "Japanese", "日本語"),
            QuizLanguageOption("ko", "Korean", "한국어")
        )
    }
}

/**
 * Genre definition item for Step 1 multi-selection.
 */
data class QuizGenreItem(
    val id: Int,
    val name: String,
    val description: String
) {
    companion object {
        val CANONICAL_GENRES = listOf(
            QuizGenreItem(28, "Action", "High-octane stunts & combat"),
            QuizGenreItem(12, "Adventure", "Epic quests & expeditions"),
            QuizGenreItem(35, "Comedy", "Laughter & feel-good humor"),
            QuizGenreItem(18, "Drama", "Deep emotional narratives"),
            QuizGenreItem(27, "Horror", "Chilling suspense & terror"),
            QuizGenreItem(878, "Sci-Fi", "Futuristic worlds & speculative tech"),
            QuizGenreItem(53, "Thriller", "Edge-of-your-seat psychological tension"),
            QuizGenreItem(10749, "Romance", "Passion, intimacy & relationships"),
            QuizGenreItem(16, "Animation", "Visual artistry & imaginative tales"),
            QuizGenreItem(9648, "Mystery", "Enigmas, puzzles & plot twists"),
            QuizGenreItem(14, "Fantasy", "Magical realms & mythical lore"),
            QuizGenreItem(80, "Crime", "Underworld, heists & investigations")
        )
    }
}

/**
 * Answer bundle tracking user selections across all quiz steps.
 */
data class QuizAnswers(
    val selectedGenreIds: Set<Int> = emptySet(),
    val preferredLanguage: String? = null,
    val mood: MovieMood = MovieMood.ANY,
    val era: MovieEra = MovieEra.ANY,
    val runtimePreference: RuntimePreference = RuntimePreference.ANY,
    val minRatingThreshold: Double = 6.0
) {
    fun isValidForStep(step: QuestionStep): Boolean {
        return when (step) {
            QuestionStep.GENRES -> selectedGenreIds.isNotEmpty()
            QuestionStep.LANGUAGE -> true
            QuestionStep.MOOD -> true
            QuestionStep.ERA -> true
            QuestionStep.RUNTIME -> true
            QuestionStep.RATING -> minRatingThreshold in 0.0..10.0
        }
    }

    fun isComplete(): Boolean = selectedGenreIds.isNotEmpty()

    fun toUserPreferenceProfile(): UserPreferenceProfile {
        return UserPreferenceProfile(
            selectedGenreIds = selectedGenreIds,
            mood = mood,
            era = era,
            runtimePreference = runtimePreference,
            preferredLanguage = preferredLanguage,
            minRatingThreshold = minRatingThreshold,
            updatedAtTimestamp = System.currentTimeMillis()
        )
    }

    companion object {
        fun fromUserProfile(profile: UserPreferenceProfile): QuizAnswers {
            return QuizAnswers(
                selectedGenreIds = profile.selectedGenreIds,
                preferredLanguage = profile.preferredLanguage,
                mood = profile.mood,
                era = profile.era,
                runtimePreference = profile.runtimePreference,
                minRatingThreshold = profile.minRatingThreshold
            )
        }
    }
}

/**
 * Reactive UI state model for the Movie Preference Taste Quiz.
 */
data class QuizUiState(
    val currentStep: QuestionStep = QuestionStep.GENRES,
    val answers: QuizAnswers = QuizAnswers(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
    val showCompletionConfirmation: Boolean = false,
    val errorMessage: String? = null,
    val initialProfileRestored: Boolean = false
) {
    val stepIndex: Int
        get() = currentStep.ordinal

    val totalSteps: Int
        get() = QuestionStep.entries.size

    val progress: Float
        get() = (stepIndex + 1).toFloat() / totalSteps.toFloat()

    val canGoBack: Boolean
        get() = currentStep.ordinal > 0

    val isLastStep: Boolean
        get() = currentStep.ordinal == QuestionStep.entries.size - 1

    val isCurrentStepValid: Boolean
        get() = answers.isValidForStep(currentStep)
}
