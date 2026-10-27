package com.example.madverse.core.domain.recommendation

import com.example.madverse.core.model.ScoredMovieRecommendation
import com.example.madverse.core.model.UserPreferenceProfile
import javax.inject.Inject

data class RecommendationInsight(val summary: String, val reasons: List<String>, val detail: String)

/** Local, synchronous extension point. Implementations must not transmit movie data externally. */
interface RecommendationInsightProvider {
    fun explain(recommendation: ScoredMovieRecommendation, profile: UserPreferenceProfile): RecommendationInsight
}

class LocalRecommendationInsightProvider @Inject constructor() : RecommendationInsightProvider {
    override fun explain(recommendation: ScoredMovieRecommendation, profile: UserPreferenceProfile): RecommendationInsight {
        // The engine is the authority for scoring factors. Never infer extra factors from metadata.
        val reasons = recommendation.matchReasons.map(String::trim).filter(String::isNotEmpty).distinct()
        val movie = recommendation.movie
        val missing = buildList {
            if (movie.releaseDate?.take(4)?.toIntOrNull()?.takeIf { it in 1870..2100 } == null) add("release date")
            if (!profile.preferredLanguage.isNullOrBlank() && movie.originalLanguage.isNullOrBlank()) add("language")
            if (movie.runtimeMinutes == null || movie.runtimeMinutes <= 0) add("runtime")
        }
        return RecommendationInsight(
            summary = if (reasons.isEmpty()) "A ${recommendation.matchPercentage}% preference match. No specific matching factors were supplied."
                else "${recommendation.matchPercentage}% preference match: ${reasons.first()}",
            reasons = reasons,
            detail = "Preference match reflects your saved quiz answers and the engine's scoring rules. " +
                "TMDB rating reflects audience votes; neither score guarantees you will enjoy a movie. " +
                "Recommendations require at least ${profile.effectiveMinRating}/10. Neutral points may be awarded for unspecified preferences or unavailable metadata." +
                if (missing.isEmpty()) "" else " Unavailable metadata: ${missing.joinToString()}."
        )
    }
}
