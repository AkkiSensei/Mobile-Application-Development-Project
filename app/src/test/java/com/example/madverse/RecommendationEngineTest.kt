package com.example.madverse

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.domain.recommendation.DeterministicRecommendationEngine
import com.example.madverse.core.domain.recommendation.GetPersonalizedRecommendationsUseCase
import com.example.madverse.core.model.CastMember
import com.example.madverse.core.model.Genre
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile
import com.example.madverse.core.model.VideoTrailer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeterministicRecommendationEngine] and [GetPersonalizedRecommendationsUseCase].
 *
 * Comprehensive coverage of R5:
 * - Genre preference scoring
 * - Language matching
 * - Era and runtime preferences
 * - Rating and popularity scoring
 * - Missing metadata fairness
 * - Empty profile & empty candidate lists
 * - Duplicate candidate removal
 * - Deterministic ranking and stable tie-breaking
 * - Explanation generation
 * - Integration via UseCase
 */
class RecommendationEngineTest {

    private lateinit var engine: DeterministicRecommendationEngine

    private val sciFiActionMovie = Movie(
        id = 1,
        title = "Galaxy Assault",
        overview = "Sci-fi action blockbuster",
        posterUrl = null,
        backdropUrl = null,
        releaseDate = "2020-06-15",
        voteAverage = 8.2,
        voteCount = 12000,
        genreIds = listOf(28, 878), // Action + Sci-Fi
        popularity = 150.0,
        originalLanguage = "en"
    )

    private val romanceDramaMovie = Movie(
        id = 2,
        title = "Love in Paris",
        overview = "A sweeping romance drama",
        posterUrl = null,
        backdropUrl = null,
        releaseDate = "2019-02-14",
        voteAverage = 7.5,
        voteCount = 8000,
        genreIds = listOf(10749, 18), // Romance + Drama
        popularity = 80.0,
        originalLanguage = "fr"
    )

    private val classicHorrorMovie = Movie(
        id = 3,
        title = "Shadows of 1980",
        overview = "Classic horror from the golden age",
        posterUrl = null,
        backdropUrl = null,
        releaseDate = "1985-10-31",
        voteAverage = 6.9,
        voteCount = 5000,
        genreIds = listOf(27, 53), // Horror + Thriller
        popularity = 40.0,
        originalLanguage = "en"
    )

    private val lowRatedMovie = Movie(
        id = 4,
        title = "Bargain Bin",
        overview = "A poorly received comedy",
        posterUrl = null,
        backdropUrl = null,
        releaseDate = "2021-03-10",
        voteAverage = 4.5,
        voteCount = 200,
        genreIds = listOf(35), // Comedy
        popularity = 10.0,
        originalLanguage = "en"
    )

    private val allCandidates = listOf(sciFiActionMovie, romanceDramaMovie, classicHorrorMovie, lowRatedMovie)

    @Before
    fun setUp() {
        engine = DeterministicRecommendationEngine()
    }

    // -----------------------------------------------------------------------
    // Core Ranking & Profile Tests
    // -----------------------------------------------------------------------

    @Test
    fun scoreAndRank_sciFiActionProfile_ranksGenreMatchFirst() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            mood = MovieMood.ADRENALINE,
            era = MovieEra.CONTEMPORARY,
            runtimePreference = RuntimePreference.ANY,
            minRatingThreshold = 5.0
        )

        val results = engine.scoreAndRank(profile, allCandidates, limit = 10)

        assertTrue(results.isNotEmpty())
        assertEquals(sciFiActionMovie.id, results.first().movie.id)
    }

    @Test
    fun scoreAndRank_horrorProfile_classicEra_selectsCorrectMovie() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(27, 53),
            mood = MovieMood.DARK_AND_SPOOKY,
            era = MovieEra.CLASSIC,
            runtimePreference = RuntimePreference.ANY,
            minRatingThreshold = 5.0
        )

        val results = engine.scoreAndRank(profile, allCandidates, limit = 10)

        assertTrue(results.isNotEmpty())
        assertEquals(classicHorrorMovie.id, results.first().movie.id)
    }

    @Test
    fun scoreAndRank_languageMatching_boostsMatchingLanguage() {
        val frenchProfile = UserPreferenceProfile(
            selectedGenreIds = emptySet(),
            mood = MovieMood.ANY,
            era = MovieEra.ANY,
            preferredLanguage = "fr",
            minRatingThreshold = 0.0
        )

        val englishProfile = UserPreferenceProfile(
            selectedGenreIds = emptySet(),
            mood = MovieMood.ANY,
            era = MovieEra.ANY,
            preferredLanguage = "en",
            minRatingThreshold = 0.0
        )

        val frenchResult = engine.scoreAndRank(frenchProfile, listOf(romanceDramaMovie)).first()
        val englishResult = engine.scoreAndRank(englishProfile, listOf(romanceDramaMovie)).first()

        assertTrue(
            "French profile score (${frenchResult.matchPercentage}) should be higher than English profile score (${englishResult.matchPercentage}) for French movie",
            frenchResult.matchPercentage > englishResult.matchPercentage
        )
        assertTrue(frenchResult.matchReasons.any { it.contains("preferred language (FR)", ignoreCase = true) })
    }

    @Test
    fun scoreAndRank_deduplicatesCandidateMovies() {
        val duplicateList = listOf(sciFiActionMovie, sciFiActionMovie, sciFiActionMovie, romanceDramaMovie)
        val profile = UserPreferenceProfile(minRatingThreshold = 0.0)

        val results = engine.scoreAndRank(profile, duplicateList, limit = 10)

        assertEquals(2, results.size)
        assertEquals(listOf(1, 2), results.map { it.movie.id }.sorted())
    }

    @Test
    fun scoreAndRank_runtimePreference_matchesAppropriateDuration() {
        val quickMovie = Movie(
            id = 50,
            title = "Brisk Animated Short",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = "2022-01-01",
            voteAverage = 7.5,
            voteCount = 1000,
            genreIds = listOf(16),
            popularity = 50.0,
            originalLanguage = "en",
            runtimeMinutes = 85
        )

        val epicMovie = Movie(
            id = 51,
            title = "Epic Historical Drama",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = "2022-01-01",
            voteAverage = 7.5,
            voteCount = 1000,
            genreIds = listOf(18),
            popularity = 50.0,
            originalLanguage = "en",
            runtimeMinutes = 165
        )

        val quickProfile = UserPreferenceProfile(
            runtimePreference = RuntimePreference.QUICK,
            minRatingThreshold = 0.0
        )
        val epicProfile = UserPreferenceProfile(
            runtimePreference = RuntimePreference.EPIC,
            minRatingThreshold = 0.0
        )

        val quickResultForQuickMovie = engine.scoreAndRank(quickProfile, listOf(quickMovie)).first()
        val epicResultForQuickMovie = engine.scoreAndRank(epicProfile, listOf(quickMovie)).first()

        assertTrue(
            "Quick movie should score higher for QUICK profile (${quickResultForQuickMovie.matchPercentage}) than for EPIC profile (${epicResultForQuickMovie.matchPercentage})",
            quickResultForQuickMovie.matchPercentage > epicResultForQuickMovie.matchPercentage
        )
        assertTrue(quickResultForQuickMovie.matchReasons.any { it.contains("Brisk", ignoreCase = true) })

        val epicResultForEpicMovie = engine.scoreAndRank(epicProfile, listOf(epicMovie)).first()
        assertTrue(epicResultForEpicMovie.matchReasons.any { it.contains("Epic", ignoreCase = true) })
    }

    @Test
    fun scoreAndRank_ratingBelowThreshold_disqualifiesQualityScore() {
        val subThresholdProfile = UserPreferenceProfile(
            minRatingThreshold = 7.0
        )
        val lowMovie = lowRatedMovie.copy(voteAverage = 4.5)

        val results = engine.scoreAndRank(subThresholdProfile, listOf(lowMovie))
        assertTrue(results.isNotEmpty())
        assertFalse(results.first().matchReasons.any { it.contains("Highly rated", ignoreCase = true) || it.contains("Well-rated", ignoreCase = true) })
    }

    @Test
    fun scoreAndRank_deterministicTieBreaking_breaksTiesByAttributesAndId() {
        val movieA = Movie(
            id = 10,
            title = "Movie A",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = "2020-01-01",
            voteAverage = 8.0,
            voteCount = 5000,
            genreIds = listOf(28),
            popularity = 50.0,
            originalLanguage = "en"
        )

        val movieB = Movie(
            id = 20,
            title = "Movie B",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = "2020-01-01",
            voteAverage = 8.0,
            voteCount = 5000,
            genreIds = listOf(28),
            popularity = 50.0,
            originalLanguage = "en"
        )

        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28),
            minRatingThreshold = 5.0
        )

        // movieA has lower ID than movieB -> movieA must strictly precede movieB
        val results1 = engine.scoreAndRank(profile, listOf(movieB, movieA))
        val results2 = engine.scoreAndRank(profile, listOf(movieA, movieB))

        assertEquals(10, results1[0].movie.id)
        assertEquals(20, results1[1].movie.id)
        assertEquals(results1.map { it.movie.id }, results2.map { it.movie.id })
    }

    @Test
    fun scoreAndRank_emptyCandidateList_returnsEmptyResult() {
        val profile = UserPreferenceProfile()
        val results = engine.scoreAndRank(profile, emptyList(), limit = 10)
        assertTrue(results.isEmpty())
    }

    @Test
    fun scoreAndRank_emptyProfile_computesNeutralScoresWithoutCrashing() {
        val emptyProfile = UserPreferenceProfile(
            selectedGenreIds = emptySet(),
            mood = MovieMood.ANY,
            era = MovieEra.ANY,
            preferredLanguage = null,
            minRatingThreshold = 0.0
        )

        val results = engine.scoreAndRank(emptyProfile, allCandidates, limit = 10)
        assertTrue(results.isNotEmpty())
        for (res in results) {
            assertTrue(res.matchPercentage in 0..100)
        }
    }

    @Test
    fun scoreAndRank_missingMetadata_doesNotCrashOrPenalizeUnfairly() {
        val movieMissingData = Movie(
            id = 99,
            title = "Unknown Mystery",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = null, // Missing release date
            voteAverage = 7.5,
            voteCount = 100,
            genreIds = emptyList(), // Missing genres
            popularity = 5.0,
            originalLanguage = null // Missing language
        )

        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28),
            preferredLanguage = "en",
            minRatingThreshold = 6.0
        )

        val results = engine.scoreAndRank(profile, listOf(movieMissingData))
        assertTrue(results.isNotEmpty())
        val scored = results.first()
        assertTrue(scored.matchPercentage >= 0)
    }

    @Test
    fun scoreAndRank_limitParameter_respectsMaxCount() {
        val profile = UserPreferenceProfile(minRatingThreshold = 0.0)
        val results = engine.scoreAndRank(profile, allCandidates, limit = 2)
        assertEquals(2, results.size)
    }

    @Test
    fun scoreAndRank_resultsAreSortedDescendingByMatchPercentage() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            minRatingThreshold = 0.0
        )

        val results = engine.scoreAndRank(profile, allCandidates, limit = 10)
        val percentages = results.map { it.matchPercentage }
        for (i in 0 until percentages.size - 1) {
            assertTrue(percentages[i] >= percentages[i + 1])
        }
    }

    @Test
    fun scoreAndRank_matchReasons_arePopulatedAccurately() {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            mood = MovieMood.ADRENALINE,
            era = MovieEra.CONTEMPORARY,
            preferredLanguage = "en",
            minRatingThreshold = 5.0
        )

        val results = engine.scoreAndRank(profile, listOf(sciFiActionMovie), limit = 5)
        assertTrue(results.isNotEmpty())
        val topResult = results.first()
        val reasons = topResult.matchReasons

        assertTrue(reasons.any { it.contains("preferred genre", ignoreCase = true) })
        assertTrue(reasons.any { it.contains("Adrenaline", ignoreCase = true) })
        assertTrue(reasons.any { it.contains("contemporary", ignoreCase = true) })
        assertTrue(reasons.any { it.contains("Highly rated by TMDB audiences", ignoreCase = true) })
        assertTrue(reasons.any { it.contains("preferred language", ignoreCase = true) })
    }

    // -----------------------------------------------------------------------
    // UseCase Integration Test
    // -----------------------------------------------------------------------

    @Test
    fun useCase_orchestratesQuizRepoAndMovieRepoCorrectly() = runTest {
        val fakeQuizRepo = FakeQuizRepository()
        fakeQuizRepo.profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            mood = MovieMood.ADRENALINE
        )

        val fakeMovieRepo = FakeMovieRepository()
        fakeMovieRepo.candidates = listOf(sciFiActionMovie, romanceDramaMovie)

        val useCase = GetPersonalizedRecommendationsUseCase(
            quizRepository = fakeQuizRepo,
            movieRepository = fakeMovieRepo,
            recommendationEngine = engine
        )

        val resourceFlow = useCase(limit = 5)
        val resourceList = mutableListOf<Resource<*>>()

        resourceFlow.collect { resourceList.add(it) }

        assertTrue(resourceList.any { it is Resource.Loading })
        val success = resourceList.last() as Resource.Success<*>
        val ranked = success.data as List<*>

        assertEquals(2, ranked.size)
        val firstRec = ranked.first() as com.example.madverse.core.model.ScoredMovieRecommendation
        assertEquals(sciFiActionMovie.id, firstRec.movie.id)
    }

    private class FakeQuizRepository : QuizRepository {
        var profile: UserPreferenceProfile = UserPreferenceProfile()

        override fun getUserPreferenceProfile(): Flow<UserPreferenceProfile> =
            flowOf(profile)

        override suspend fun getUserPreferenceProfileOnce(): UserPreferenceProfile =
            profile

        override suspend fun saveUserPreferenceProfile(profile: UserPreferenceProfile) {
            this.profile = profile
        }

        override suspend fun clearUserPreferenceProfile() {
            this.profile = UserPreferenceProfile()
        }

        override fun getQuizQuestions(): List<com.example.madverse.core.model.QuizQuestion> =
            emptyList()
    }

    private class FakeMovieRepository : MovieRepository {
        var candidates: List<Movie> = emptyList()

        override fun discoverCandidateMovies(
            genreIds: Set<Int>,
            startYear: Int?,
            endYear: Int?,
            minRating: Double?,
            page: Int
        ): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(candidates))

        override fun getTrendingMovies(page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun getTopRatedMovies(page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun getUpcomingMovies(page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun searchMovies(query: String, page: Int, releaseYear: Int?): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> = flowOf(Resource.Loading)
        override fun getMovieCredits(movieId: Int): Flow<Resource<List<CastMember>>> = flowOf(Resource.Success(emptyList()))
        override fun getMovieVideos(movieId: Int): Flow<Resource<List<VideoTrailer>>> = flowOf(Resource.Success(emptyList()))
        override fun getMovieWatchProviders(movieId: Int, countryCode: String): Flow<Resource<MovieWatchProviders>> = flowOf(Resource.Loading)
        override fun getSimilarMovies(movieId: Int, page: Int): Flow<Resource<List<Movie>>> = flowOf(Resource.Success(emptyList()))
        override fun getGenres(): Flow<Resource<List<Genre>>> = flowOf(Resource.Success(emptyList()))
    }
}
