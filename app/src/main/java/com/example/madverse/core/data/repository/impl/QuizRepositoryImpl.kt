package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.DefaultSessionManager
import com.example.madverse.core.data.repository.NoOpCloudSyncService
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.data.sync.SyncPayloadSerializer
import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.DirectTransactionRunner
import com.example.madverse.core.database.dao.NoOpPendingSyncDao
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.QuizOption
import com.example.madverse.core.model.QuizQuestion
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed and Firestore-synchronized implementation of [QuizRepository].
 *
 * Local mutations and their sync-queue entries are executed in atomic Room transactions.
 * A failed queue insertion rolls back the local mutation to ensure complete state consistency.
 * Exact-action acknowledgement only occurs after confirmed remote completion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class QuizRepositoryImpl @Inject constructor(
    private val userPreferenceDao: UserPreferenceDao,
    private val sessionManager: SessionManager,
    private val syncService: CloudSyncService,
    private val pendingSyncDao: PendingSyncDao,
    private val transactionRunner: DatabaseTransactionRunner
) : QuizRepository {

    // 3-arg constructor preserving backward compatibility for isolated unit tests
    constructor(
        userPreferenceDao: UserPreferenceDao,
        sessionManager: SessionManager,
        syncService: CloudSyncService
    ) : this(
        userPreferenceDao = userPreferenceDao,
        sessionManager = sessionManager,
        syncService = syncService,
        pendingSyncDao = NoOpPendingSyncDao(),
        transactionRunner = DirectTransactionRunner()
    )

    // 1-arg constructor preserving backward compatibility for isolated unit tests
    constructor(userPreferenceDao: UserPreferenceDao) : this(
        userPreferenceDao = userPreferenceDao,
        sessionManager = DefaultSessionManager(),
        syncService = NoOpCloudSyncService(),
        pendingSyncDao = NoOpPendingSyncDao(),
        transactionRunner = DirectTransactionRunner()
    )

    override fun hasSavedProfile(): Flow<Boolean> =
        sessionManager.currentUserIdFlow.flatMapLatest { userId ->
            userPreferenceDao.getUserPreferenceProfile(userId).map { it != null }
        }

    // -------------------------------------------------------------------------
    // Preference Profile Persistence
    // -------------------------------------------------------------------------

    override fun getUserPreferenceProfile(): Flow<UserPreferenceProfile> =
        sessionManager.currentUserIdFlow.flatMapLatest { userId ->
            userPreferenceDao.getUserPreferenceProfile(userId).map { entity ->
                entity?.toDomain() ?: UserPreferenceProfile()
            }
        }

    override suspend fun getUserPreferenceProfileOnce(): UserPreferenceProfile {
        val userId = sessionManager.currentUserId
        return userPreferenceDao.getUserPreferenceProfileOnce(userId)?.toDomain() ?: UserPreferenceProfile()
    }

    override suspend fun saveUserPreferenceProfile(profile: UserPreferenceProfile) {
        val userId = sessionManager.currentUserId
        transactionRunner.runInTransaction {
            userPreferenceDao.saveUserPreferenceProfile(
                UserPreferenceEntity.fromDomain(profile, userId = userId)
            )
            if (userId != "guest") {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "UPSERT",
                        entityType = "PREFERENCE",
                        itemId = "current",
                        payloadJson = SyncPayloadSerializer.preferenceToJson(profile)
                    )
                )
            }
        }
        if (userId != "guest") {
            syncService.processPendingSyncActions(userId)
        }
    }

    override suspend fun clearUserPreferenceProfile() {
        val userId = sessionManager.currentUserId
        transactionRunner.runInTransaction {
            userPreferenceDao.clearUserPreferenceProfile(userId)
            if (userId != "guest") {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "DELETE",
                        entityType = "PREFERENCE",
                        itemId = "current",
                        payloadJson = null
                    )
                )
            }
        }
        if (userId != "guest") {
            syncService.processPendingSyncActions(userId)
        }
    }

    // -------------------------------------------------------------------------
    // Quiz Question Set  —  canonical definition shared by Quiz + Recommendations
    // -------------------------------------------------------------------------

    override fun getQuizQuestions(): List<QuizQuestion> = listOf(
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
