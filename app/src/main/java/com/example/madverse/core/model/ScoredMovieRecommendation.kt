package com.example.madverse.core.model

/**
 * A candidate movie scored by the MADVerse Recommendation Engine with rationale.
 */
data class ScoredMovieRecommendation(
    val movie: Movie,
    val matchPercentage: Int, // 0 to 100%
    val matchReasons: List<String>,
    val aiCuratorNote: String? = null // Optional Gemini reasoning note
)

typealias RecommendationResult = ScoredMovieRecommendation
