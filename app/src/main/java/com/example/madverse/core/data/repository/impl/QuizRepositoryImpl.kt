package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.QuizOption
import com.example.madverse.core.model.QuizQuestion
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [QuizRepository].
 *
 * Stores and retrieves the singleton [UserPreferenceProfile] from the
 * `user_preference_profile` table via [UserPreferenceDao]. Also provides
 * the hard-coded quiz question set used by Shraddha's Quiz UI.
 *
 * The quiz question list is defined here (not in a separate ViewModel)
 * so that MADVerse's recommendation engine and quiz UI share a single,
 * authoritative question set without requiring a network call.
 */
@Singleton
class QuizRepositoryImpl @Inject constructor(
    private val userPreferenceDao: UserPreferenceDao
) : QuizRepository {

    override fun hasSavedProfile(): Flow<Boolean> =
        userPreferenceDao.getUserPreferenceProfile().map { it != null }

    // -------------------------------------------------------------------------
    // Preference Profile Persistence
    // -------------------------------------------------------------------------

    override fun getUserPreferenceProfile(): Flow<UserPreferenceProfile> =
        userPreferenceDao.getUserPreferenceProfile().map { entity ->
            entity?.toDomain() ?: UserPreferenceProfile()
        }

    override suspend fun getUserPreferenceProfileOnce(): UserPreferenceProfile =
        userPreferenceDao.getUserPreferenceProfileOnce()?.toDomain() ?: UserPreferenceProfile()

    override suspend fun saveUserPreferenceProfile(profile: UserPreferenceProfile) {
        userPreferenceDao.saveUserPreferenceProfile(
            UserPreferenceEntity.fromDomain(profile)
        )
    }

    override suspend fun clearUserPreferenceProfile() {
        userPreferenceDao.clearUserPreferenceProfile()
    }

    // -------------------------------------------------------------------------
    // Quiz Question Set  —  canonical definition shared by Quiz + Recommendations
    // -------------------------------------------------------------------------

    /**
     * Returns the ordered list of preference quiz questions.
     * Shraddha's QuizScreen iterates through these in sequence.
     * Ritunjay's RecommendationEngine reads the resulting profile.
     */
    override fun getQuizQuestions(): List<QuizQuestion> = listOf(

        // Step 1 — Genre Selection (multi-select)
        QuizQuestion(
            id = "q_genre",
            title = "What genres do you love?",
            description = "Select all the genres you enjoy watching.",
            allowsMultipleSelection = true,
            options = listOf(
                QuizOption(id = "action",    label = "Action",       subtitle = "High-octane thrills",          genreIds = listOf(28)),
                QuizOption(id = "adventure", label = "Adventure",    subtitle = "Epic journeys & exploration",   genreIds = listOf(12)),
                QuizOption(id = "comedy",    label = "Comedy",       subtitle = "Laughter guaranteed",           genreIds = listOf(35)),
                QuizOption(id = "drama",     label = "Drama",        subtitle = "Powerful emotional storytelling", genreIds = listOf(18)),
                QuizOption(id = "horror",    label = "Horror",       subtitle = "Things that go bump",          genreIds = listOf(27)),
                QuizOption(id = "scifi",     label = "Sci-Fi",       subtitle = "Space, tech & the unknown",   genreIds = listOf(878)),
                QuizOption(id = "thriller",  label = "Thriller",     subtitle = "Edge-of-your-seat suspense",  genreIds = listOf(53)),
                QuizOption(id = "romance",   label = "Romance",      subtitle = "Love stories & chemistry",     genreIds = listOf(10749)),
                QuizOption(id = "animation", label = "Animation",    subtitle = "Animated wonders",             genreIds = listOf(16)),
                QuizOption(id = "mystery",   label = "Mystery",      subtitle = "Whodunits & twists",           genreIds = listOf(9648)),
                QuizOption(id = "fantasy",   label = "Fantasy",      subtitle = "Magical worlds & creatures",   genreIds = listOf(14)),
                QuizOption(id = "crime",     label = "Crime",        subtitle = "Heists, detectives & capers",  genreIds = listOf(80))
            )
        ),

        // Step 2 — Mood / Viewing Intent (single select)
        QuizQuestion(
            id = "q_mood",
            title = "What's your viewing mood?",
            description = "Pick the feeling you're after tonight.",
            allowsMultipleSelection = false,
            options = MovieMood.entries.map { mood ->
                QuizOption(
                    id = "mood_${mood.name.lowercase()}",
                    label = mood.displayName,
                    mood = mood
                )
            }
        ),

        // Step 3 — Movie Era (single select)
        QuizQuestion(
            id = "q_era",
            title = "Which era of cinema?",
            description = "Do you have a favourite decade?",
            allowsMultipleSelection = false,
            options = MovieEra.entries.map { era ->
                QuizOption(
                    id = "era_${era.name.lowercase()}",
                    label = era.displayName,
                    era = era
                )
            }
        ),

        // Step 4 — Runtime Preference (single select)
        QuizQuestion(
            id = "q_runtime",
            title = "How long do you want to watch?",
            description = "Choose your ideal runtime range.",
            allowsMultipleSelection = false,
            options = RuntimePreference.entries.map { rt ->
                QuizOption(
                    id = "runtime_${rt.name.lowercase()}",
                    label = rt.displayName,
                    runtime = rt
                )
            }
        )
    )
}
