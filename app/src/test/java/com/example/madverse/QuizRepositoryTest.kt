package com.example.madverse

import com.example.madverse.core.data.repository.impl.QuizRepositoryImpl
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [QuizRepositoryImpl] and [UserPreferenceEntity].
 * Verifies:
 * - Reactive flow and one-time retrieval of [UserPreferenceProfile]
 * - Profile saving and singleton row update
 * - Clearing preferences
 * - Authoritative quiz question set definitions
 * - State restoration across simulated app process restarts
 * - Robust error recovery from corrupted or legacy database records
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizRepositoryTest {

    private lateinit var testDao: TestUserPreferenceDao
    private lateinit var repository: QuizRepositoryImpl

    @Before
    fun setUp() {
        testDao = TestUserPreferenceDao()
        repository = QuizRepositoryImpl(testDao)
    }

    @Test
    fun savedProfilePresenceDistinguishesDefaultsFromNoQuiz() = runTest {
        assertFalse(repository.hasSavedProfile().first())
        repository.saveUserPreferenceProfile(UserPreferenceProfile())
        assertTrue(repository.hasSavedProfile().first())
        repository.clearUserPreferenceProfile()
        assertFalse(repository.hasSavedProfile().first())
    }

    @Test
    fun getUserPreferenceProfile_emptyByDefault_emitsDefaultProfile() = runTest {
        val profile = repository.getUserPreferenceProfile().first()
        assertTrue(profile.selectedGenreIds.isEmpty())
        assertEquals(MovieMood.ANY, profile.mood)
        assertEquals(MovieEra.ANY, profile.era)
        assertEquals(RuntimePreference.ANY, profile.runtimePreference)
        assertEquals(null, profile.preferredLanguage)
        assertEquals(6.0, profile.minRatingThreshold, 0.001)
    }

    @Test
    fun getUserPreferenceProfileOnce_emptyByDefault_returnsDefaultProfile() = runTest {
        val profile = repository.getUserPreferenceProfileOnce()
        assertTrue(profile.selectedGenreIds.isEmpty())
        assertEquals(MovieMood.ANY, profile.mood)
        assertEquals(MovieEra.ANY, profile.era)
        assertEquals(RuntimePreference.ANY, profile.runtimePreference)
    }

    @Test
    fun saveUserPreferenceProfile_persistsProfile_emitsUpdatedProfile() = runTest {
        val customProfile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878, 53),
            mood = MovieMood.MIND_BENDING,
            era = MovieEra.CONTEMPORARY,
            runtimePreference = RuntimePreference.STANDARD,
            preferredLanguage = "en",
            minRatingThreshold = 7.5,
            updatedAtTimestamp = 999999L
        )

        repository.saveUserPreferenceProfile(customProfile)

        val retrievedFlow = repository.getUserPreferenceProfile().first()
        assertEquals(setOf(28, 878, 53), retrievedFlow.selectedGenreIds)
        assertEquals(MovieMood.MIND_BENDING, retrievedFlow.mood)
        assertEquals(MovieEra.CONTEMPORARY, retrievedFlow.era)
        assertEquals(RuntimePreference.STANDARD, retrievedFlow.runtimePreference)
        assertEquals("en", retrievedFlow.preferredLanguage)
        assertEquals(7.5, retrievedFlow.minRatingThreshold, 0.001)
        assertEquals(999999L, retrievedFlow.updatedAtTimestamp)

        val retrievedOnce = repository.getUserPreferenceProfileOnce()
        assertEquals(retrievedFlow, retrievedOnce)
    }

    @Test
    fun saveUserPreferenceProfile_overwritesPreviousProfile_maintainingSingleton() = runTest {
        val profile1 = UserPreferenceProfile(
            selectedGenreIds = setOf(28),
            mood = MovieMood.ADRENALINE
        )
        val profile2 = UserPreferenceProfile(
            selectedGenreIds = setOf(35, 16),
            mood = MovieMood.FEEL_GOOD
        )

        repository.saveUserPreferenceProfile(profile1)
        repository.saveUserPreferenceProfile(profile2)

        val current = repository.getUserPreferenceProfileOnce()
        assertEquals(setOf(35, 16), current.selectedGenreIds)
        assertEquals(MovieMood.FEEL_GOOD, current.mood)
        assertEquals(1, testDao.rowCount)
    }

    @Test
    fun clearUserPreferenceProfile_revertsToDefault() = runTest {
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(27),
            mood = MovieMood.DARK_AND_SPOOKY
        )
        repository.saveUserPreferenceProfile(profile)

        assertEquals(MovieMood.DARK_AND_SPOOKY, repository.getUserPreferenceProfileOnce().mood)

        repository.clearUserPreferenceProfile()

        val afterClear = repository.getUserPreferenceProfileOnce()
        assertTrue(afterClear.selectedGenreIds.isEmpty())
        assertEquals(MovieMood.ANY, afterClear.mood)
    }

    @Test
    fun getQuizQuestions_returnsAuthoritativeOrderedQuestionSet() {
        val questions = repository.getQuizQuestions()
        assertEquals(4, questions.size)

        // Step 1: Genres
        val q1 = questions[0]
        assertEquals("q_genre", q1.id)
        assertTrue(q1.allowsMultipleSelection)
        assertEquals(12, q1.options.size)
        assertTrue(q1.options.any { it.label == "Action" && it.genreIds.contains(28) })
        assertTrue(q1.options.any { it.label == "Sci-Fi" && it.genreIds.contains(878) })

        // Step 2: Mood
        val q2 = questions[1]
        assertEquals("q_mood", q2.id)
        assertFalse(q2.allowsMultipleSelection)
        assertEquals(MovieMood.entries.size, q2.options.size)
        assertTrue(q2.options.any { it.mood == MovieMood.MIND_BENDING })

        // Step 3: Era
        val q3 = questions[2]
        assertEquals("q_era", q3.id)
        assertFalse(q3.allowsMultipleSelection)
        assertEquals(MovieEra.entries.size, q3.options.size)
        assertTrue(q3.options.any { it.era == MovieEra.CLASSIC })

        // Step 4: Runtime
        val q4 = questions[3]
        assertEquals("q_runtime", q4.id)
        assertFalse(q4.allowsMultipleSelection)
        assertEquals(RuntimePreference.entries.size, q4.options.size)
        assertTrue(q4.options.any { it.runtime == RuntimePreference.QUICK })
    }

    @Test
    fun processRestartSimulation_savedStateRestoredInFreshRepositoryInstance() = runTest {
        val savedProfile = UserPreferenceProfile(
            selectedGenreIds = setOf(18, 10749),
            mood = MovieMood.EMOTIONAL,
            era = MovieEra.MODERN,
            runtimePreference = RuntimePreference.EPIC,
            preferredLanguage = "fr",
            minRatingThreshold = 8.0,
            updatedAtTimestamp = 555123L
        )

        // Save on instance 1
        repository.saveUserPreferenceProfile(savedProfile)

        // Simulate app process kill & restart by creating a new repository referencing the persistent DAO
        val restartedRepository = QuizRepositoryImpl(testDao)

        val restoredOnce = restartedRepository.getUserPreferenceProfileOnce()
        val restoredFlow = restartedRepository.getUserPreferenceProfile().first()

        assertEquals(savedProfile.selectedGenreIds, restoredOnce.selectedGenreIds)
        assertEquals(savedProfile.mood, restoredOnce.mood)
        assertEquals(savedProfile.era, restoredOnce.era)
        assertEquals(savedProfile.runtimePreference, restoredOnce.runtimePreference)
        assertEquals(savedProfile.preferredLanguage, restoredOnce.preferredLanguage)
        assertEquals(savedProfile.minRatingThreshold, restoredOnce.minRatingThreshold, 0.001)
        assertEquals(savedProfile.updatedAtTimestamp, restoredOnce.updatedAtTimestamp)
        assertEquals(restoredOnce, restoredFlow)
    }

    @Test
    fun userPreferenceEntity_malformedData_recoversGracefullyWithDefaults() {
        val corruptedEntity = UserPreferenceEntity(
            id = 1,
            genreIdsCsv = "invalid,28,,NaN,878",
            mood = "NON_EXISTENT_MOOD",
            era = "UNKNOWN_ERA",
            runtimePreference = "INVALID_RUNTIME",
            preferredLanguage = "es",
            minRatingThreshold = 6.5,
            updatedTimestamp = 12345L
        )

        val domain = corruptedEntity.toDomain()

        // Valid genre IDs parsed, non-integers ignored safely
        assertEquals(setOf(28, 878), domain.selectedGenreIds)
        // Unknown enums fall back to ANY
        assertEquals(MovieMood.ANY, domain.mood)
        assertEquals(MovieEra.ANY, domain.era)
        assertEquals(RuntimePreference.ANY, domain.runtimePreference)
        assertEquals("es", domain.preferredLanguage)
        assertEquals(6.5, domain.minRatingThreshold, 0.001)
        assertEquals(12345L, domain.updatedAtTimestamp)
    }

    private class TestUserPreferenceDao : UserPreferenceDao {
        private var storedEntity: UserPreferenceEntity? = null
        private val flow = MutableStateFlow<UserPreferenceEntity?>(null)

        val rowCount: Int get() = if (storedEntity != null) 1 else 0

        override fun getUserPreferenceProfile(): Flow<UserPreferenceEntity?> = flow

        override suspend fun getUserPreferenceProfileOnce(): UserPreferenceEntity? = storedEntity

        override suspend fun saveUserPreferenceProfile(profile: UserPreferenceEntity) {
            storedEntity = profile
            flow.value = profile
        }

        override suspend fun clearUserPreferenceProfile() {
            storedEntity = null
            flow.value = null
        }
    }
}
