package com.example.madverse.feature.quiz.presentation

import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile

/**
 * Bidirectional mapper between interactive quiz answers ([QuizAnswers]) and the persisted
 * [UserPreferenceProfile] domain model consumed by [DeterministicRecommendationEngine] and Room database.
 */
object QuizProfileMapper {

    /**
     * Converts interactive [QuizAnswers] into a validated [UserPreferenceProfile] matching the
     * deterministic recommendation engine contract and Room persistence schema.
     */
    fun mapToUserProfile(
        answers: QuizAnswers,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): UserPreferenceProfile {
        return UserPreferenceProfile(
            selectedGenreIds = answers.selectedGenreIds.toSet(),
            mood = answers.mood,
            era = answers.era,
            runtimePreference = answers.runtimePreference,
            preferredLanguage = answers.preferredLanguage?.trim()?.takeIf { it.isNotEmpty() },
            minRatingThreshold = answers.minRatingThreshold.coerceIn(0.0, 10.0),
            updatedAtTimestamp = currentTimeMillis
        )
    }

    /**
     * Restores [QuizAnswers] from an existing [UserPreferenceProfile] to prefill the quiz UI
     * when the user returns to adjust their taste preferences.
     */
    fun mapToQuizAnswers(profile: UserPreferenceProfile): QuizAnswers {
        return QuizAnswers(
            selectedGenreIds = profile.selectedGenreIds.toSet(),
            preferredLanguage = profile.preferredLanguage?.trim()?.takeIf { it.isNotEmpty() },
            mood = profile.mood,
            era = profile.era,
            runtimePreference = profile.runtimePreference,
            minRatingThreshold = profile.minRatingThreshold.coerceIn(0.0, 10.0)
        )
    }
}
