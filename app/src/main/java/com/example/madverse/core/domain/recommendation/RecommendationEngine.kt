package com.example.madverse.core.domain.recommendation

import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.ScoredMovieRecommendation
import com.example.madverse.core.model.UserPreferenceProfile

/**
 * Contract for the MADVerse Recommendation Engine.
 * Must operate deterministically without requiring external network AI calls,
 * with optional AI enhancement via [AiRecommendationEnhancer].
 */
interface RecommendationEngine {

    /**
     * Scores and ranks candidate movies against a user's preference profile.
     *
     * @param profile The user's persisted taste profile from the quiz.
     * @param candidates Movies fetched as candidates (from discover/trending/genres).
     * @param limit Maximum number of top recommendations to return.
     * @return List of scored movies sorted by matchPercentage descending.
     */
    fun scoreAndRank(
        profile: UserPreferenceProfile,
        candidates: List<Movie>,
        limit: Int = 10
    ): List<ScoredMovieRecommendation>
}

/**
 * Optional AI layer that decorates scored recommendations with conversational insights.
 * Gracefully degrades if Gemini API key is missing or offline.
 */
interface AiRecommendationEnhancer {
    suspend fun enhanceRecommendationRationale(
        recommendation: ScoredMovieRecommendation,
        profile: UserPreferenceProfile
    ): ScoredMovieRecommendation
}
