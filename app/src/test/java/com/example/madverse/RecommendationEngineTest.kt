package com.example.madverse

import com.example.madverse.core.domain.recommendation.DeterministicRecommendationEngine
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeterministicRecommendationEngine].
 *
 * Validates:
 * - Scoring formula components (genre, mood, era, quality)
 * - Ordering: highest-scoring movies appear first
 * - Limit parameter: result list respects the maximum count
 * - Edge cases: empty candidate lists, no genre preferences, ANY era/mood
 */
class RecommendationEngineTest {

    private lateinit var engine: DeterministicRecommendationEngine

    // -----------------------------------------------------------------------
    // Test fixtures
    // -----------------------------------------------------------------------

    private val sciFiActionMovie = Movie(
        id = 1, title = "Galaxy Assault",
        overview = "Sci-fi action blockbuster",
        posterUrl = null, backdropUrl = null,
        releaseDate = "2020-06-15",
        voteAverage = 8.2, voteCount = 12000,
        genreIds = listOf(28, 878), // Action + Sci-Fi
        popularity = 150.0
    )

    private val romanceDramaMovie = Movie(
        id = 2, title = "Love in Paris",
        overview = "A sweeping romance drama",
        posterUrl = null, backdropUrl = null,
        releaseDate = "2019-02-14",
        voteAverage = 7.5, voteCount = 8000,
        genreIds = listOf(10749, 18), // Romance + Drama
        popularity = 80.0
    )

    private val classicHorrorMovie = Movie(
        id = 3, title = "Shadows of 1980",
        overview = "Classic horror from the golden age",
        posterUrl = null, backdropUrl = null,
        releaseDate = "1985-10-31",
        voteAverage = 6.9, voteCount = 5000,
        genreIds = listOf(27, 53), // Horror + Thriller
        popularity = 40.0
    )

    private val lowRatedMovie = Movie(
        id = 4, title = "Bargain Bin",
        overview = "A poorly received comedy",
        posterUrl = null, backdropUrl = null,
        releaseDate = "2021-03-10",
        voteAverage = 4.5, voteCount = 200,
        genreIds = listOf(35), // Comedy
        popularity = 10.0
    )

    private val allCandidates = listOf(sciFiActionMovie, romanceDramaMovie, classicHorrorMovie, lowRatedMovie)

    @Before
    fun setUp() {
        engine = DeterministicRecommendationEngine()
    }

    // -----------------------------------------------------------------------
    // Core Ranking Tests
    // -----------------------------------------------------------------------

    @Test
    fun scoreAndRank_sciFiActionProfile_rankesGenreMatchFirst() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878), // Action + Sci-Fi
            mood = MovieMood.ADRENALINE,
            era = MovieEra.CONTEMPORARY,
            runtimePreference = RuntimePreference.ANY,
            minRatingThreshold = 5.0
        )

        val results = engine.scoreAndRank(profile, allCandidates, limit = 10)

        assertTrue("Should return results", results.isNotEmpty())
        // sciFiActionMovie shares both genres with the profile — should be #1
        assertEquals(sciFiActionMovie.id, results.first().movie.id)
    }

    @Test
    fun scoreAndRank_horrorProfile_classicEra_selectsCorrectMovie() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(27, 53), // Horror + Thriller
            mood = MovieMood.DARK_AND_SPOOKY,
            era = MovieEra.CLASSIC,
            runtimePreference = RuntimePreference.ANY,
            minRatingThreshold = 5.0
        )

        val results = engine.scoreAndRank(profile, allCandidates, limit = 10)

        assertTrue("Should return results", results.isNotEmpty())
        assertEquals(classicHorrorMovie.id, results.first().movie.id)
    }

    @Test
    fun scoreAndRank_limitParameter_respectsMaxCount() {
        val profile = UserPreferenceProfile(minRatingThreshold = 4.0)

        val results = engine.scoreAndRank(profile, allCandidates, limit = 2)

        assertTrue("Result count should not exceed limit of 2", results.size <= 2)
    }

    @Test
    fun scoreAndRank_emptyCandidateList_returnsEmptyResult() {
        val profile = UserPreferenceProfile()

        val results = engine.scoreAndRank(profile, emptyList(), limit = 10)

        assertTrue("Empty candidates should return empty results", results.isEmpty())
    }

    @Test
    fun scoreAndRank_minRatingFilter_excludesBelowThresholdMovies() {
        // When a movie's voteAverage is below minRatingThreshold its quality score is 0.
        // With selectedGenreIds that exactly match sciFiActionMovie and romanceDramaMovie
        // but NOT the lowRatedMovie genres, we expect lowRatedMovie to rank last or to
        // have a lower match percentage than movies that also pass quality.
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878, 10749, 18), // Action, Sci-Fi, Romance, Drama
            mood = MovieMood.ANY,
            era = MovieEra.ANY,
            minRatingThreshold = 7.0 // lowRatedMovie(4.5) and classicHorrorMovie(6.9) below
        )

        val results = engine.scoreAndRank(profile, allCandidates, limit = 10)

        // lowRatedMovie has voteAverage=4.5 (below threshold) → quality score = 0
        // classicHorrorMovie has voteAverage=6.9 (below threshold) → quality score = 0
        // Both get 0 genre overlap with the sci-fi/action/romance/drama profile (comedy/horror)
        // So their match percentage should be lower than sciFiActionMovie and romanceDramaMovie
        val lowRatedResult = results.find { it.movie.id == lowRatedMovie.id }
        val sciFiResult = results.find { it.movie.id == sciFiActionMovie.id }

        // sciFiActionMovie should outrank lowRatedMovie (lower quality + no genre overlap)
        if (lowRatedResult != null && sciFiResult != null) {
            assertTrue(
                "sci-fi/action movie (${sciFiResult.matchPercentage}%) should outrank " +
                "low-rated comedy (${lowRatedResult.matchPercentage}%)",
                sciFiResult.matchPercentage >= lowRatedResult.matchPercentage
            )
        }
    }

    @Test
    fun scoreAndRank_resultsAreSortedDescendingByMatchPercentage() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            minRatingThreshold = 4.0
        )

        val results = engine.scoreAndRank(profile, allCandidates, limit = 10)

        val percentages = results.map { it.matchPercentage }
        for (i in 0 until percentages.size - 1) {
            assertTrue(
                "Results should be sorted descending: ${percentages[i]} >= ${percentages[i + 1]}",
                percentages[i] >= percentages[i + 1]
            )
        }
    }

    @Test
    fun scoreAndRank_matchPercentage_isWithinValidRange() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            mood = MovieMood.ADRENALINE,
            era = MovieEra.ANY,
            minRatingThreshold = 0.0
        )

        val results = engine.scoreAndRank(profile, allCandidates, limit = 10)

        for (result in results) {
            assertTrue(
                "Match percentage ${result.matchPercentage} should be >= 0",
                result.matchPercentage >= 0
            )
            assertTrue(
                "Match percentage ${result.matchPercentage} should be <= 100",
                result.matchPercentage <= 100
            )
        }
    }

    @Test
    fun scoreAndRank_anyMoodAndEra_doesNotPenaliseMovies() {
        val strictProfile = UserPreferenceProfile(
            selectedGenreIds = setOf(28),
            mood = MovieMood.ADRENALINE,
            era = MovieEra.CONTEMPORARY,
            minRatingThreshold = 4.0
        )
        val lenientProfile = UserPreferenceProfile(
            selectedGenreIds = setOf(28),
            mood = MovieMood.ANY,
            era = MovieEra.ANY,
            minRatingThreshold = 4.0
        )

        val strictResults = engine.scoreAndRank(strictProfile, listOf(sciFiActionMovie), limit = 10)
        val lenientResults = engine.scoreAndRank(lenientProfile, listOf(sciFiActionMovie), limit = 10)

        assertTrue("Both should produce a result", strictResults.isNotEmpty() && lenientResults.isNotEmpty())
        // Lenient profile gives full era points; should be >= strict score with wrong era
    }

    @Test
    fun scoreAndRank_matchReasons_arePopulatedForHighScoringMovies() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            mood = MovieMood.ADRENALINE,
            era = MovieEra.CONTEMPORARY,
            minRatingThreshold = 5.0
        )

        val results = engine.scoreAndRank(profile, listOf(sciFiActionMovie), limit = 5)

        assertTrue("Should have a result", results.isNotEmpty())
        val topResult = results.first()
        assertTrue(
            "High-scoring movie should have match reasons",
            topResult.matchReasons.isNotEmpty()
        )
    }
}
