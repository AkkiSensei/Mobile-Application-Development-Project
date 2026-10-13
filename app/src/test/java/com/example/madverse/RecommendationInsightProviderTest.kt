package com.example.madverse

import com.example.madverse.core.domain.recommendation.*
import com.example.madverse.core.model.*
import org.junit.Assert.*
import org.junit.Test

class RecommendationInsightProviderTest {
    private val provider = LocalRecommendationInsightProvider()

    @Test fun explanationPreservesEngineReasonsAndScore() {
        val profile = UserPreferenceProfile(selectedGenreIds = setOf(28), preferredLanguage = "en")
        val ranked = DeterministicRecommendationEngine().scoreAndRank(profile, listOf(discoveryMovie()), 1).single()
        val insight = provider.explain(ranked, profile)
        assertEquals(ranked.matchReasons, insight.reasons)
        assertTrue(insight.summary.contains("${ranked.matchPercentage}%"))
        assertTrue(insight.detail.contains("audience votes"))
    }
    @Test fun missingMetadataIsAcknowledgedWithoutInventedMatches() {
        val movie = discoveryMovie().copy(releaseDate = null, originalLanguage = null, runtimeMinutes = null, genreIds = emptyList())
        val profile = UserPreferenceProfile(preferredLanguage = "hi", runtimePreference = RuntimePreference.EPIC)
        val ranked = DeterministicRecommendationEngine().scoreAndRank(profile, listOf(movie), 1).single()
        val insight = provider.explain(ranked, profile)
        assertTrue(insight.detail.contains("release date, language, runtime"))
        assertFalse(insight.reasons.any { it.contains("language") || it.contains("runtime") })
    }
    @Test fun emptyReasonsHaveUsefulLocalFallback() {
        val insight = provider.explain(ScoredMovieRecommendation(discoveryMovie(), 60, listOf("", " ")), UserPreferenceProfile())
        assertEquals(emptyList<String>(), insight.reasons)
        assertTrue(insight.summary.contains("No specific matching factors"))
        assertTrue(insight.summary.contains("60%"))
    }
    @Test fun metadataAloneNeverAddsFactorsOrUsesAiNote() {
        val recommendation = ScoredMovieRecommendation(discoveryMovie(), 42, listOf("Matches 1 of your preferred genre"), "Invented AI text")
        val insight = provider.explain(recommendation, UserPreferenceProfile(preferredLanguage = "en"))
        assertEquals(recommendation.matchReasons, insight.reasons)
        assertFalse(insight.summary.contains("language"))
        assertFalse(insight.toString().contains("Invented AI text"))
    }
}
