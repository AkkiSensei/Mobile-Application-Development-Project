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
 * Deterministic recommendation engine for MADVerse.
 *
 * Implements the scoring formula defined in ARCHITECTURE.md §8:
 *
 *   MatchScore = S_genre(40) + S_mood(25) + S_era(15) + S_quality(20)
 *
 * All computation is local and deterministic — no AI / network calls required.
 * The optional [AiRecommendationEnhancer] layer can decorate results after ranking.
 *
 * TMDB Attribution Note: This engine uses TMDB movie data solely for local,
 * deterministic scoring. It does NOT forward TMDB content to any AI service.
 * This architecture is consistent with TMDB's published API terms.
 */
@Singleton
class DeterministicRecommendationEngine @Inject constructor() : RecommendationEngine {

    override fun scoreAndRank(
        profile: UserPreferenceProfile,
        candidates: List<Movie>,
        limit: Int
    ): List<ScoredMovieRecommendation> {
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .map { movie -> scoreMovie(movie, profile) }
            .filter { it.matchPercentage >= MIN_MATCH_THRESHOLD }
            .sortedByDescending { it.matchPercentage }
            .take(limit)
    }

    // -------------------------------------------------------------------------
    // Per-Movie Scoring
    // -------------------------------------------------------------------------

    private fun scoreMovie(
        movie: Movie,
        profile: UserPreferenceProfile
    ): ScoredMovieRecommendation {
        val reasons = mutableListOf<String>()

        val genreScore  = scoreGenre(movie, profile, reasons)
        val moodScore   = scoreMood(movie, profile, reasons)
        val eraScore    = scoreEra(movie, profile, reasons)
        val qualityScore = scoreQuality(movie, profile, reasons)
        val runtimeOk   = meetsRuntimeFilter(movie, profile)

        // Runtime is a hard filter: movies outside the preferred window get penalised
        val runtimePenalty = if (runtimeOk) 0 else RUNTIME_PENALTY

        val rawScore  = genreScore + moodScore + eraScore + qualityScore - runtimePenalty
        val clamped   = min(100, rawScore).coerceAtLeast(0)

        return ScoredMovieRecommendation(
            movie = movie,
            matchPercentage = clamped,
            matchReasons = reasons.toList()
        )
    }

    // -------------------------------------------------------------------------
    // Genre Score  — up to 40 points
    // -------------------------------------------------------------------------

    /**
     * Jaccard-style overlap: proportion of the user's selected genres that
     * appear in the movie's genre list, scaled to 40 points.
     */
    private fun scoreGenre(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        if (profile.selectedGenreIds.isEmpty()) return GENRE_MAX_PTS / 2 // neutral default

        val movieGenreSet = movie.genreIds.toSet()
        val overlap = profile.selectedGenreIds.count { it in movieGenreSet }
        val score = ((overlap.toDouble() / profile.selectedGenreIds.size) * GENRE_MAX_PTS).roundToInt()

        if (score > 0) {
            reasons += "Matches ${overlap} of your preferred genre${if (overlap > 1) "s" else ""}"
        }
        return score
    }

    // -------------------------------------------------------------------------
    // Mood Score  — up to 25 points
    // -------------------------------------------------------------------------

    /**
     * Maps the user's mood enum to a set of associated TMDB genre IDs
     * (defined on [MovieMood]) and rewards partial and full coverage.
     */
    private fun scoreMood(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        val mood = profile.mood
        if (mood == MovieMood.ANY) return MOOD_MAX_PTS / 2 // neutral when no mood selected

        val moodGenres = mood.associatedGenreIds
        if (moodGenres.isEmpty()) return MOOD_MAX_PTS / 2

        val movieGenreSet = movie.genreIds.toSet()
        val overlap = moodGenres.count { it in movieGenreSet }
        val score = ((overlap.toDouble() / moodGenres.size) * MOOD_MAX_PTS).roundToInt()

        if (score > 0) {
            reasons += "Fits your \"${mood.displayName}\" mood"
        }
        return score
    }

    // -------------------------------------------------------------------------
    // Era Score  — up to 15 points
    // -------------------------------------------------------------------------

    private fun scoreEra(
        movie: Movie,
        profile: UserPreferenceProfile,
        reasons: MutableList<String>
    ): Int {
        val era = profile.era
        if (era == MovieEra.ANY) return ERA_MAX_PTS // neutral — give full points

        val releaseYear = movie.releaseDate?.take(4)?.toIntOrNull() ?: return 0
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
    // Quality (Rating) Score  — up to 20 points
    // -------------------------------------------------------------------------

    /**
     * Proportional to the movie's TMDB vote average, with a minimum threshold
     * filter applied independently.
     */
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
        return score
    }

    // -------------------------------------------------------------------------
    // Runtime Filter  — penalty applied, not a score component
    // -------------------------------------------------------------------------

    private fun meetsRuntimeFilter(
        movie: Movie,
        profile: UserPreferenceProfile
    ): Boolean {
        // Runtime is unknown for list-item movies (only full MovieDetails has it).
        // We trust the user's rating filter and skip the runtime check here.
        // Runtime filtering is applied server-side (discover endpoint) when building
        // the candidate pool from MovieRepository.discoverCandidateMovies().
        return true
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private companion object {
        private const val GENRE_MAX_PTS       = 40
        private const val MOOD_MAX_PTS        = 25
        private const val ERA_MAX_PTS         = 15
        private const val QUALITY_MAX_PTS     = 20
        private const val RUNTIME_PENALTY     = 15
        private const val MIN_MATCH_THRESHOLD = 0  // include all scored movies; callers may filter
    }
}
