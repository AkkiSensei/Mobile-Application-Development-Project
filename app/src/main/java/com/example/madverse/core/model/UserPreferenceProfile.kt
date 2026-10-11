package com.example.madverse.core.model

/**
 * User taste profile captured via the interactive preference quiz.
 */
data class UserPreferenceProfile(
    val selectedGenreIds: Set<Int> = emptySet(),
    val mood: MovieMood = MovieMood.ANY,
    val era: MovieEra = MovieEra.ANY,
    val runtimePreference: RuntimePreference = RuntimePreference.ANY,
    val preferredLanguage: String? = null,
    val minRatingThreshold: Double = 6.0,
    val updatedAtTimestamp: Long = System.currentTimeMillis()
)

typealias RecommendationProfile = UserPreferenceProfile

enum class MovieMood(val displayName: String, val associatedGenreIds: List<Int>) {
    ANY("Any Mood", emptyList()),
    MIND_BENDING("Mind-Bending & Thought-Provoking", listOf(878, 9648, 53)), // Sci-Fi, Mystery, Thriller
    ADRENALINE("Adrenaline & Action-Packed", listOf(28, 12)),               // Action, Adventure
    FEEL_GOOD("Feel-Good & Laughs", listOf(35, 10751, 16)),                 // Comedy, Family, Animation
    DARK_AND_SPOOKY("Dark & Chilling", listOf(27, 53)),                     // Horror, Thriller
    EMOTIONAL("Deep & Tearjerker", listOf(18, 10749))                       // Drama, Romance
}

enum class MovieEra(val displayName: String, val startYear: Int?, val endYear: Int?) {
    ANY("Any Era", null, null),
    CLASSIC("Golden Classics (Pre-1990)", null, 1989),
    MODERN("Modern Hits (1990 - 2014)", 1990, 2014),
    CONTEMPORARY("Contemporary & Recent (2015 - Present)", 2015, null)
}

enum class RuntimePreference(val displayName: String, val maxMinutes: Int?) {
    ANY("Any Runtime", null),
    QUICK("Brisk (< 100 mins)", 100),
    STANDARD("Standard (100 - 140 mins)", 140),
    EPIC("Epic & Immersive (> 140 mins)", null)
}
