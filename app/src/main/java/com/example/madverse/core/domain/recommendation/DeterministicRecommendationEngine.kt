package com.example.madverse.core.domain.recommendation

import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.ScoredMovieRecommendation
import com.example.madverse.core.model.UserPreferenceProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic, testable recommendation engine for MADVerse.
 *
 * Implements R5: Recommendation Engine with transparent multi-factor scoring:
 *
 *   MatchScore = S_genre(30) + S_mood(20) + S_era(15) + S_quality(15) + S_language(10) + S_runtime(10)
 *   Total Normalized Points: 100
 *
 * Key Architectural Properties:
 * 1. 100% Deterministic: Pure mathematical calculation without randomness or time dependency.
 * 2. Unambiguous Tie-Breaking: Breaks score ties by voteAverage -> voteCount -> popularity -> id.
 * 3. Candidate Deduplication: Automatically removes duplicate candidate movies by ID.
 * 4. Missing Metadata Fairness: Never penalizes movies unfairly for missing optional metadata (language, era, runtime).
 * 5. Explainable AI / Viva Ready: Generates verifiable reasons directly from scoring factors.
 * 6. TMDB AI Compliance: Operates entirely locally without forwarding TMDB content to external AI.
 */
@Singleton
class DeterministicRecommendationEngine @Inject constructor() : RecommendationEngine {

    override fun scoreAndRank(
        profile: UserPreferenceProfile,
        candidates: List<Movie>,
        limit: Int
    ): List<ScoredMovieRecommendation> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        return candidates
            .distinctBy { it.id } // Deduplicate candidate movies
            .map { movie -> scoreMovie(movie, profile) }
            .sortedWith(
                compareByDescending<ScoredMovieRecommendation> { it.matchPercentage }
                    .thenByDescending { it.movie.voteAverage }
                    .thenByDescending { it.movie.voteCount }
                    .thenByDescending { it.movie.popularity }
                    .thenBy { it.movie.id } // Deterministic stable tie-breaker
            )
            .take(limit)
    }

    private fun scoreMovie(
        movie: Movie,
        profile: UserPreferenceProfile
    ): ScoredMovieRecommendation {
        val reasons = mutableListOf<String>()

        val genreScore    = scoreGenre(movie, profile, reasons)
        val moodScore     = scoreMood(movie, profile, reasons)
        val eraScore      = scoreEra(movie, profile, reasons)
        val qualityScore  = scoreQuality(movie, profile, reasons)
        val languageScore = scoreLanguage(movie, profile, reasons)
        val runtimeScore  = scoreRuntime(movie, profile, reasons)

        val rawScore = genreScore + moodScore + eraScore + qualityScore + languageScore + runtimeScore
        val clamped  = rawScore.coerceIn(0, 100)

        return ScoredMovieRecommendation(
            movie = movie,
            matchPercentage = clamped,
            matchReasons = reasons.toList()
        )
    }

    // -------------------------------------------------------------------------
    // 1. Genre Score — up to 30 points
    // -------------------------------------------------------------------------

    private fun scoreGenre(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        if (profile.selectedGenreIds.isEmpty()) return GENRE_MAX_PTS / 2 // Neutral default

        val movieGenreSet = movie.genreIds.toSet()
        val overlap = profile.selectedGenreIds.count { it in movieGenreSet }
        val score = ((overlap.toDouble() / profile.selectedGenreIds.size) * GENRE_MAX_PTS).roundToInt()

        if (overlap > 0) {
            reasons += "Matches ${overlap} of your preferred genre${if (overlap > 1) "s" else ""}"
        }
        return score
    }

    // -------------------------------------------------------------------------
    // 2. Mood Score — up to 20 points
    // -------------------------------------------------------------------------

    private fun scoreMood(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        val mood = profile.mood
        if (mood == MovieMood.ANY) return MOOD_MAX_PTS / 2 // Neutral when any mood selected

        val moodGenres = mood.associatedGenreIds
        if (moodGenres.isEmpty()) return MOOD_MAX_PTS / 2

        val movieGenreSet = movie.genreIds.toSet()
        val overlap = moodGenres.count { it in movieGenreSet }
        val score = ((overlap.toDouble() / moodGenres.size) * MOOD_MAX_PTS).roundToInt()

        if (overlap > 0) {
            reasons += "Fits your \"${mood.displayName}\" mood"
        }
        return score
    }

    // -------------------------------------------------------------------------
    // 3. Era Score — up to 15 points
    // -------------------------------------------------------------------------

    private fun scoreEra(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        val era = profile.era
        if (era == MovieEra.ANY) return ERA_MAX_PTS // Neutral — award full era points

        val releaseYear = movie.releaseDate?.take(4)?.toIntOrNull()
        // Missing metadata fairness: do not penalize movies with missing release date
        if (releaseYear == null) return ERA_MAX_PTS

        val inEra = when (era) {
            MovieEra.CLASSIC      -> releaseYear <= 1989
            MovieEra.MODERN       -> releaseYear in 1990..2014
            MovieEra.CONTEMPORARY -> releaseYear >= 2015
            MovieEra.ANY          -> true
        }

        return if (inEra) {
            reasons += "From the ${era.displayName.lowercase()}"
            ERA_MAX_PTS
        } else {
            0
        }
    }

    // -------------------------------------------------------------------------
    // 4. Quality (Rating & Popularity) Score — up to 15 points
    // -------------------------------------------------------------------------

    private fun scoreQuality(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        if (movie.voteAverage < profile.minRatingThreshold) return 0

        val normalised = min(10.0, movie.voteAverage) / 10.0
        val score = (normalised * QUALITY_MAX_PTS).roundToInt()

        if (movie.voteAverage >= 8.0) {
            reasons += "Critically acclaimed (${movie.formattedRating}★)"
        } else if (movie.voteAverage >= 7.0) {
            reasons += "Well-rated (${movie.formattedRating}★)"
        }

        if (movie.popularity >= 100.0 || movie.voteCount >= 10000) {
            reasons += "Popular with global audiences"
        }

        return score
    }

    // -------------------------------------------------------------------------
    // 5. Language Score — up to 10 points
    // -------------------------------------------------------------------------

    private fun scoreLanguage(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        val preferredLang = profile.preferredLanguage
        val movieLang = movie.originalLanguage

        // If user didn't specify language or movie lacks language metadata,
        // do not penalize: award full neutral points.
        if (preferredLang.isNullOrBlank() || movieLang.isNullOrBlank()) {
            return LANGUAGE_MAX_PTS
        }

        return if (movieLang.equals(preferredLang, ignoreCase = true)) {
            reasons += "Matches your preferred language (${preferredLang.uppercase()})"
            LANGUAGE_MAX_PTS
        } else {
            0
        }
    }

    // -------------------------------------------------------------------------
    // 6. Runtime Score — up to 10 points
    // -------------------------------------------------------------------------

    private fun scoreRuntime(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        val pref = profile.runtimePreference
        if (pref == RuntimePreference.ANY) return RUNTIME_MAX_PTS

        val runtime = movie.runtimeMinutes
        // Missing metadata fairness: do not penalize movies when runtime is unavailable
        if (runtime == null || runtime <= 0) return RUNTIME_MAX_PTS

        val inRuntime = when (pref) {
            RuntimePreference.QUICK    -> runtime < 100
            RuntimePreference.STANDARD -> runtime in 100..140
            RuntimePreference.EPIC     -> runtime > 140
            RuntimePreference.ANY      -> true
        }

        return if (inRuntime) {
            reasons += "Matches runtime preference: ${pref.displayName}"
            RUNTIME_MAX_PTS
        } else {
            0
        }
    }

    // -------------------------------------------------------------------------
    // Scoring Weights Constants
    // -------------------------------------------------------------------------

    companion object {
        const val GENRE_MAX_PTS    = 30
        const val MOOD_MAX_PTS     = 20
        const val ERA_MAX_PTS      = 15
        const val QUALITY_MAX_PTS  = 15
        const val LANGUAGE_MAX_PTS = 10
        const val RUNTIME_MAX_PTS  = 10
    }
}
