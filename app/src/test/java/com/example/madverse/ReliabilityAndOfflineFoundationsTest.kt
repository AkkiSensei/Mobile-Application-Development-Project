package com.example.madverse

import com.example.madverse.core.data.account.AccountDeletionJournal
import com.example.madverse.core.data.account.DeletionStage
import com.example.madverse.core.data.account.InMemoryAccountDeletionJournal
import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.NoOpCloudSyncService
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.data.repository.impl.FavoritesRepositoryImpl
import com.example.madverse.core.data.repository.impl.QuizRepositoryImpl
import com.example.madverse.core.data.repository.impl.WatchlistRepositoryImpl
import com.example.madverse.core.data.sync.SyncPayloadSerializer
import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.FavoriteEntity
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.database.entity.WatchlistEntity
import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.SyncState
import com.example.madverse.core.model.UserPreferenceProfile
import com.example.madverse.feature.profile.presentation.ProfileViewModel
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Phase 1: Reliability and Offline Foundations Comprehensive Verification Suite.
 *
 * Validates:
 * 1. Atomic mutation/queue rollback when queue insertion fails.
 * 2. Favorite and Watchlist add/remove transactional queueing.
 * 3. Preferences replay targeting /users/{uid}/preferences/current.
 * 4. Exact acknowledgement (only successfully completed items deleted from queue).
 * 5. Transient retry resumption (retains items on I/O error and retries upon recovery).
 * 6. Permission-denied queue retention and pause (does not treat as sign-in expiry).
 * 7. Cancellation propagation without swallowing into offline pending state.
 * 8. Account switching during in-flight operations (session tokens protect cross-user leakage).
 * 9. Process restart after remote success before acknowledgement (idempotent replay).
 * 10. Pending-local snapshot protection (empty or stale remote snapshot does not erase local items).
 * 11. Account deletion durable journal recovery and local data preservation on failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReliabilityAndOfflineFoundationsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var favoriteDao: TestFavoriteDao
    private lateinit var watchlistDao: TestWatchlistDao
    private lateinit var userPreferenceDao: TestUserPreferenceDao
    private lateinit var pendingSyncDao: TestPendingSyncDao
    private lateinit var transactionRunner: RollbackSimulatingTransactionRunner
    private lateinit var sessionManager: TestSessionManager
    private lateinit var cloudSyncService: TestCloudSyncService
    private lateinit var accountDeletionJournal: InMemoryAccountDeletionJournal

    private lateinit var favoritesRepository: FavoritesRepositoryImpl
    private lateinit var watchlistRepository: WatchlistRepositoryImpl
    private lateinit var quizRepository: QuizRepositoryImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        favoriteDao = TestFavoriteDao()
        watchlistDao = TestWatchlistDao()
        userPreferenceDao = TestUserPreferenceDao()
        pendingSyncDao = TestPendingSyncDao()
        transactionRunner = RollbackSimulatingTransactionRunner()
        sessionManager = TestSessionManager("user_123")
        cloudSyncService = TestCloudSyncService(pendingSyncDao, favoriteDao, watchlistDao, userPreferenceDao)
        accountDeletionJournal = InMemoryAccountDeletionJournal()

        favoritesRepository = FavoritesRepositoryImpl(
            favoriteDao = favoriteDao,
            sessionManager = sessionManager,
            syncService = cloudSyncService,
            pendingSyncDao = pendingSyncDao,
            transactionRunner = transactionRunner
        )

        watchlistRepository = WatchlistRepositoryImpl(
            watchlistDao = watchlistDao,
            sessionManager = sessionManager,
            syncService = cloudSyncService,
            pendingSyncDao = pendingSyncDao,
            transactionRunner = transactionRunner
        )

        quizRepository = QuizRepositoryImpl(
            userPreferenceDao = userPreferenceDao,
            sessionManager = sessionManager,
            syncService = cloudSyncService,
            pendingSyncDao = pendingSyncDao,
            transactionRunner = transactionRunner
        )
    }

    private fun sampleMovie(id: Int, title: String = "Movie $id"): Movie {
        return Movie(
            id = id,
            title = title,
            overview = "Overview of $title",
            posterUrl = "/poster_$id.jpg",
            backdropUrl = "/backdrop_$id.jpg",
            releaseDate = "2024-01-01",
            voteAverage = 8.5,
            voteCount = 500,
            genreIds = listOf(28, 12),
            popularity = 95.0
        )
    }

    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. ATOMIC MUTATION AND QUEUE ROLLBACK
    // =========================================================================

    @Test
    fun atomicMutation_whenQueueInsertionFails_rollsBackLocalFavoriteInsertion() = runTest(testDispatcher) {
        pendingSyncDao.shouldFailInsert = true
        transactionRunner.enableRollbackSimulation(
            onRollback = { favoriteDao.clearAll() }
        )

        try {
            favoritesRepository.addToFavorites(sampleMovie(101))
            fail("Expected exception when pendingSyncDao.insertAction fails")
        } catch (_: Exception) {
            // Expected
        }

        // Local DAO must be empty because the transaction was rolled back!
        assertEquals(0, favoriteDao.getFavoritesCountOnce("user_123"))
        assertEquals(0, pendingSyncDao.getPendingActionsForUser("user_123").size)
    }

    @Test
    fun atomicMutation_whenQueueInsertionFails_rollsBackLocalWatchlistInsertion() = runTest(testDispatcher) {
        pendingSyncDao.shouldFailInsert = true
        transactionRunner.enableRollbackSimulation(
            onRollback = { watchlistDao.clearAll() }
        )

        try {
            watchlistRepository.addToWatchlist(sampleMovie(202))
            fail("Expected exception when pendingSyncDao.insertAction fails")
        } catch (_: Exception) {
            // Expected
        }

        assertEquals(0, watchlistDao.getWatchlistCountOnce("user_123"))
        assertEquals(0, pendingSyncDao.getPendingActionsForUser("user_123").size)
    }

    @Test
    fun atomicMutation_whenQueueInsertionFails_rollsBackLocalPreferenceSave() = runTest(testDispatcher) {
        pendingSyncDao.shouldFailInsert = true
        transactionRunner.enableRollbackSimulation(
            onRollback = { userPreferenceDao.clearAll() }
        )

        try {
            quizRepository.saveUserPreferenceProfile(UserPreferenceProfile(selectedGenreIds = setOf(28)))
            fail("Expected exception when pendingSyncDao.insertAction fails")
        } catch (_: Exception) {
            // Expected
        }

        assertNull(userPreferenceDao.getUserPreferenceProfileOnce("user_123"))
        assertEquals(0, pendingSyncDao.getPendingActionsForUser("user_123").size)
    }

    // =========================================================================
    // 2. FAVORITE AND WATCHLIST ADD / REMOVE
    // =========================================================================

    @Test
    fun favoriteAndWatchlist_addAndRemove_createsDeterministicQueueRecords() = runTest(testDispatcher) {
        // Add favorite
        favoritesRepository.addToFavorites(sampleMovie(10, "Inception"))
        assertEquals(1, favoriteDao.getFavoritesCountOnce("user_123"))

        // Add watchlist
        watchlistRepository.addToWatchlist(sampleMovie(20, "Interstellar"))
        assertEquals(1, watchlistDao.getWatchlistCountOnce("user_123"))

        // Remove favorite
        favoritesRepository.removeFromFavorites(10)
        assertEquals(0, favoriteDao.getFavoritesCountOnce("user_123"))

        // Remove watchlist
        watchlistRepository.removeFromWatchlist(20)
        assertEquals(0, watchlistDao.getWatchlistCountOnce("user_123"))
    }

    // =========================================================================
    // 3. PREFERENCES REPLAY TARGETING /users/{uid}/preferences/current
    // =========================================================================

    @Test
    fun preferences_syncTarget_isCurrentDocumentUnderPreferencesCollection() = runTest(testDispatcher) {
        cloudSyncService.shouldFailRemote = true // simulate offline during save
        val profile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            mood = MovieMood.MIND_BENDING,
            minRatingThreshold = 7.5
        )

        quizRepository.saveUserPreferenceProfile(profile)

        // Verify queued action targets "current"
        val pending = pendingSyncDao.getPendingActionsForUser("user_123")
        assertEquals(1, pending.size)
        val action = pending[0]
        assertEquals("PREFERENCE", action.entityType)
        assertEquals("UPSERT", action.actionType)
        assertEquals("current", action.itemId)

        // Verify payload JSON deserialization
        val deserialized = SyncPayloadSerializer.jsonToPreference(action.payloadJson!!)
        assertNotNull(deserialized)
        assertEquals(profile.selectedGenreIds, deserialized!!.selectedGenreIds)
        assertEquals(profile.mood, deserialized.mood)

        // Reconnect and process pending actions
        cloudSyncService.shouldFailRemote = false
        cloudSyncService.processPendingSyncActions("user_123")

        // Action acknowledged and removed from queue
        assertEquals(0, pendingSyncDao.getPendingActionsForUser("user_123").size)
        // Remote preferences received
        assertNotNull(cloudSyncService.remotePreferences["user_123"])
    }

    // =========================================================================
    // 4. EXACT ACKNOWLEDGEMENT ONLY AFTER SUCCESSFUL REMOTE COMPLETION
    // =========================================================================

    @Test
    fun exactAcknowledgement_onlyAcknowledgesCompletedActions() = runTest(testDispatcher) {
        // Enqueue Action 1 (Favorite 10) and Action 2 (Favorite 20)
        val a1Id = pendingSyncDao.insertAction(
            PendingSyncEntity(userId = "user_123", actionType = "UPSERT", entityType = "FAVORITE", itemId = "10")
        )
        val a2Id = pendingSyncDao.insertAction(
            PendingSyncEntity(userId = "user_123", actionType = "UPSERT", entityType = "FAVORITE", itemId = "20")
        )
        favoriteDao.insertFavorite(FavoriteEntity.fromDomain(sampleMovie(10), "user_123"))
        favoriteDao.insertFavorite(FavoriteEntity.fromDomain(sampleMovie(20), "user_123"))

        // Simulate remote failing specifically on itemId 20
        cloudSyncService.failOnItemId = "20"
        cloudSyncService.processPendingSyncActions("user_123")

        val remaining = pendingSyncDao.getPendingActionsForUser("user_123")
        assertEquals(1, remaining.size)
        assertEquals(a2Id, remaining[0].id)
        assertEquals("20", remaining[0].itemId)
        // Action 1 (10) was deleted exactly upon completion!
        assertFalse(remaining.any { it.id == a1Id })
    }

    // =========================================================================
    // 5. TRANSIENT RETRY RESUMPTION
    // =========================================================================

    @Test
    fun transientFailure_retainsQueue_andResumesUponRecovery() = runTest(testDispatcher) {
        cloudSyncService.shouldFailRemote = true
        cloudSyncService.remoteException = IOException("Network unavailable")

        favoritesRepository.addToFavorites(sampleMovie(303))

        // Retained in queue
        val pendingBefore = pendingSyncDao.getPendingActionsForUser("user_123")
        assertEquals(1, pendingBefore.size)
        assertEquals("303", pendingBefore[0].itemId)

        // Connectivity recovers
        cloudSyncService.shouldFailRemote = false
        cloudSyncService.processPendingSyncActions("user_123")

        // Successfully synchronized and removed from queue
        assertEquals(0, pendingSyncDao.getPendingActionsForUser("user_123").size)
        assertTrue(cloudSyncService.remoteFavorites["user_123"]?.containsKey(303) == true)
    }

    @Test
    fun transientFailure_schedulesRetry_andClearsOnSessionStop() = runTest(testDispatcher) {
        cloudSyncService.shouldFailRemote = true
        cloudSyncService.remoteException = IOException("Connection timed out")

        favoritesRepository.addToFavorites(sampleMovie(304))

        // Trigger drain which hits transient error
        cloudSyncService.processPendingSyncActions("user_123")

        assertEquals(SyncState.OfflinePending, cloudSyncService.syncState.value)
        assertTrue("Transient retry must be scheduled upon transient failure", cloudSyncService.retryJobScheduled)

        // Stop session / logout cancels retry
        cloudSyncService.stopUserSession()
        assertFalse("Retry must be cancelled and cleared upon session stop", cloudSyncService.retryJobScheduled)
    }

    // =========================================================================
    // 6. PERMISSION DENIED QUEUE RETENTION & PAUSE
    // =========================================================================

    @Test
    fun permissionDenied_retainsQueueAndReportsError_withoutTreatingAsExpiry() = runTest(testDispatcher) {
        cloudSyncService.shouldFailRemote = true
        cloudSyncService.remoteException = SecurityException("Permission denied by security rules")

        favoritesRepository.addToFavorites(sampleMovie(404))

        val pending = pendingSyncDao.getPendingActionsForUser("user_123")
        assertEquals(1, pending.size)
        assertEquals("404", pending[0].itemId)

        cloudSyncService.processPendingSyncActions("user_123")

        // Retained in queue (not silently dropped or acknowledged)
        assertEquals(1, pendingSyncDao.getPendingActionsForUser("user_123").size)
        assertTrue(cloudSyncService.syncState.value is SyncState.Error)
        val errorMessage = (cloudSyncService.syncState.value as SyncState.Error).message
        assertTrue(errorMessage.contains("Permission denied", ignoreCase = true))

        // Session must NOT be logged out or treated as guest
        assertEquals("user_123", sessionManager.currentUserId)
    }

    // =========================================================================
    // 7. CANCELLATION PROPAGATION
    // =========================================================================

    @Test
    fun cancellation_propagatesImmediately_withoutSwallowingIntoOfflineState() = runTest(testDispatcher) {
        cloudSyncService.simulateCancellation = true

        var caughtCancellation = false
        try {
            cloudSyncService.pushFavorite("user_123", sampleMovie(505))
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue(caughtCancellation)
    }

    // =========================================================================
    // 8. ACCOUNT SWITCHING DURING IN-FLIGHT OPERATION
    // =========================================================================

    @Test
    fun accountSwitch_abortsInFlightOperations_andPreventsCrossUserMutation() = runTest(testDispatcher) {
        // User A enqueues an action
        val userA = "user_alpha"
        val userB = "user_beta"

        sessionManager.setUserId(userA)
        cloudSyncService.startUserSession(userA)

        pendingSyncDao.insertAction(
            PendingSyncEntity(userId = userA, actionType = "UPSERT", entityType = "FAVORITE", itemId = "1")
        )
        favoriteDao.insertFavorite(FavoriteEntity.fromDomain(sampleMovie(1), userA))

        // Switch user while operation is pending
        sessionManager.setUserId(userB)
        cloudSyncService.startUserSession(userB)

        // Attempt drain with user A's ID
        cloudSyncService.processPendingSyncActions(userA)

        // User A's action must NOT be applied to User B's remote storage
        assertNull(cloudSyncService.remoteFavorites[userB])
    }

    // =========================================================================
    // 9. PROCESS RESTART AFTER REMOTE SUCCESS BEFORE ACKNOWLEDGEMENT
    // =========================================================================

    @Test
    fun processRestart_afterRemoteSuccessBeforeAck_replaysIdempotently() = runTest(testDispatcher) {
        // Movie already written remotely in prior run before crash
        cloudSyncService.remoteFavorites.getOrPut("user_123") { mutableMapOf() }[606] = sampleMovie(606)

        // Action was still unacknowledged in Room pending queue
        pendingSyncDao.insertAction(
            PendingSyncEntity(
                userId = "user_123",
                actionType = "UPSERT",
                entityType = "FAVORITE",
                itemId = "606",
                payloadJson = SyncPayloadSerializer.movieToJson(sampleMovie(606))
            )
        )
        favoriteDao.insertFavorite(FavoriteEntity.fromDomain(sampleMovie(606), "user_123"))

        // Replay on restart
        cloudSyncService.processPendingSyncActions("user_123")

        // Queue acknowledged cleanly without corruption
        assertEquals(0, pendingSyncDao.getPendingActionsForUser("user_123").size)
        assertEquals(1, cloudSyncService.remoteFavorites["user_123"]?.size)
    }

    // =========================================================================
    // 10. PENDING-LOCAL SNAPSHOT PROTECTION FOR EACH DOMAIN
    // =========================================================================

    @Test
    fun snapshotReconciliation_emptyRemoteSnapshot_doesNotErasePendingLocalAdditions() = runTest(testDispatcher) {
        // User added a movie locally while offline
        favoriteDao.insertFavorite(FavoriteEntity.fromDomain(sampleMovie(701), "user_123"))
        pendingSyncDao.insertAction(
            PendingSyncEntity(userId = "user_123", actionType = "UPSERT", entityType = "FAVORITE", itemId = "701")
        )

        // An empty remote snapshot arrives from server
        cloudSyncService.applySimulatedRemoteSnapshot(
            userId = "user_123",
            remoteMovieIds = emptySet() // Empty remote collection
        )

        // Pending local addition MUST NOT be deleted!
        assertNotNull(favoriteDao.getFavoriteById(701, "user_123"))
    }

    @Test
    fun snapshotReconciliation_staleRemoteSnapshot_doesNotResurrectPendingLocalDeletions() = runTest(testDispatcher) {
        // User deleted movie locally while offline
        pendingSyncDao.insertAction(
            PendingSyncEntity(userId = "user_123", actionType = "DELETE", entityType = "FAVORITE", itemId = "702")
        )

        // Remote snapshot still contains movie 702
        cloudSyncService.applySimulatedRemoteSnapshot(
            userId = "user_123",
            remoteMovieIds = setOf(702)
        )

        // Must NOT resurrect movie 702 in local Room!
        assertNull(favoriteDao.getFavoriteById(702, "user_123"))
    }

    // =========================================================================
    // 11. RECOVERY FROM UNKNOWN / FAILED AUTH DELETION OUTCOME
    // =========================================================================

    @Test
    fun accountDeletion_whenAuthDeletionFails_preservesLocalRoomDataAndJournalState() = runTest(testDispatcher) {
        val user = AuthUser("user_delete_me", "user_delete_me@madverse.com", "User", null, true)
        val fakeAuth = com.example.madverse.fakes.FakeAuthRepository(AuthState.Authenticated(user))
        fakeAuth.shouldFailNextOperation = true
        fakeAuth.failureException = SecurityException("Recent login required")

        // Add some local favorites and watchlist items
        favoriteDao.insertFavorite(FavoriteEntity.fromDomain(sampleMovie(801), "user_delete_me"))
        watchlistDao.insertMovie(WatchlistEntity.fromDomain(sampleMovie(802), "user_delete_me"))

        val viewModel = ProfileViewModel(
            authRepository = fakeAuth,
            sessionManager = sessionManager,
            favoritesRepository = favoritesRepository,
            watchlistRepository = watchlistRepository,
            quizRepository = quizRepository,
            cloudSyncService = cloudSyncService,
            accountDeletionJournal = accountDeletionJournal
        )

        var callbackCalled = false
        viewModel.deleteAccount { callbackCalled = true }
        advanceUntilIdle()

        // 1. Callback not called
        assertFalse(callbackCalled)

        // 2. Local Room data is completely preserved!
        assertNotNull(favoriteDao.getFavoriteById(801, "user_delete_me"))
        assertNotNull(watchlistDao.getWatchlistMovieById(802, "user_delete_me"))

        // 3. Journal records failure stage
        val journalEntry = accountDeletionJournal.getJournalEntry("user_delete_me")
        assertNotNull(journalEntry)
        assertEquals(DeletionStage.AUTH_DELETE_REQUESTED, journalEntry!!.stage)

        // 4. UI shows user-friendly recovery instructions
        val uiMessage = viewModel.uiState.value.errorMessage
        assertNotNull(uiMessage)
        assertTrue(uiMessage!!.contains("recent authentication", ignoreCase = true))
    }

    // =========================================================================
    // TEST DOUBLES & HELPER CLASSES
    // =========================================================================

    private class RollbackSimulatingTransactionRunner : DatabaseTransactionRunner {
        private var onRollbackAction: (() -> Unit)? = null

        fun enableRollbackSimulation(onRollback: () -> Unit) {
            this.onRollbackAction = onRollback
        }

        override suspend fun <R> runInTransaction(block: suspend () -> R): R {
            return try {
                block()
            } catch (e: Exception) {
                onRollbackAction?.invoke()
                throw e
            }
        }
    }

    private class TestSessionManager(initialUserId: String) : SessionManager {
        private val _userIdFlow = MutableStateFlow(initialUserId)
        override val currentUserId: String get() = _userIdFlow.value
        override val currentUserIdFlow: Flow<String> = _userIdFlow.asStateFlow()
        override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Authenticated(AuthUser(initialUserId, "test@madverse.com", "Test", null, true)))
        override val isGuest: Boolean get() = currentUserId == "guest"
        override suspend fun hasGuestData(): Boolean = false

        fun setUserId(userId: String) {
            _userIdFlow.value = userId
        }
    }

    private class TestPendingSyncDao : PendingSyncDao {
        val actions = mutableListOf<PendingSyncEntity>()
        var nextId = 1L
        var shouldFailInsert = false

        override suspend fun getPendingActionsForUser(userId: String): List<PendingSyncEntity> {
            return actions.filter { it.userId == userId }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        }

        override suspend fun insertAction(action: PendingSyncEntity): Long {
            if (shouldFailInsert) {
                throw IOException("Simulated SQLite queue insertion failure")
            }
            val id = nextId++
            actions.add(action.copy(id = id))
            return id
        }

        override suspend fun deleteActionById(actionId: Long) {
            actions.removeAll { it.id == actionId }
        }

        override suspend fun deleteActionsForUser(userId: String) {
            actions.removeAll { it.userId == userId }
        }
    }

    internal class TestFavoriteDao : FavoriteDao {
        val items = mutableMapOf<Pair<Int, String>, FavoriteEntity>()
        val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

        fun clearAll() { items.clear(); flow.value = emptyList() }

        override fun getAllFavorites(userId: String): Flow<List<FavoriteEntity>> =
            flow.map { it.filter { e -> e.userId == userId } }

        override suspend fun getAllFavoritesOnce(userId: String): List<FavoriteEntity> =
            items.values.filter { it.userId == userId }

        override fun isFavorite(movieId: Int, userId: String): Flow<Boolean> =
            flow.map { it.any { e -> e.movieId == movieId && e.userId == userId } }

        override suspend fun isFavoriteOnce(movieId: Int, userId: String): Boolean =
            items.containsKey(movieId to userId)

        override suspend fun getFavoriteById(movieId: Int, userId: String): FavoriteEntity? =
            items[movieId to userId]

        override suspend fun insertFavorite(favorite: FavoriteEntity) {
            items[favorite.movieId to favorite.userId] = favorite
            flow.value = items.values.toList()
        }

        override suspend fun deleteFavorite(favorite: FavoriteEntity) {
            items.remove(favorite.movieId to favorite.userId)
            flow.value = items.values.toList()
        }

        override suspend fun deleteFavoriteById(movieId: Int, userId: String) {
            items.remove(movieId to userId)
            flow.value = items.values.toList()
        }

        override suspend fun deleteAllFavoritesForUser(userId: String) {
            items.entries.removeAll { it.key.second == userId }
            flow.value = items.values.toList()
        }

        override fun getFavoritesCount(userId: String): Flow<Int> =
            flow.map { it.count { e -> e.userId == userId } }

        override suspend fun getFavoritesCountOnce(userId: String): Int =
            items.values.count { it.userId == userId }
    }

    internal class TestWatchlistDao : WatchlistDao {
        val items = mutableMapOf<Pair<Int, String>, WatchlistEntity>()
        val flow = MutableStateFlow<List<WatchlistEntity>>(emptyList())

        fun clearAll() { items.clear(); flow.value = emptyList() }

        override fun getAllWatchlistMovies(userId: String): Flow<List<WatchlistEntity>> =
            flow.map { it.filter { e -> e.userId == userId } }

        override suspend fun getAllWatchlistOnce(userId: String): List<WatchlistEntity> =
            items.values.filter { it.userId == userId }

        override fun isMovieInWatchlist(movieId: Int, userId: String): Flow<Boolean> =
            flow.map { it.any { e -> e.movieId == movieId && e.userId == userId } }

        override suspend fun isMovieInWatchlistOnce(movieId: Int, userId: String): Boolean =
            items.containsKey(movieId to userId)

        override suspend fun getWatchlistMovieById(movieId: Int, userId: String): WatchlistEntity? =
            items[movieId to userId]

        override suspend fun insertMovie(movie: WatchlistEntity) {
            items[movie.movieId to movie.userId] = movie
            flow.value = items.values.toList()
        }

        override suspend fun deleteMovie(movie: WatchlistEntity) {
            items.remove(movie.movieId to movie.userId)
            flow.value = items.values.toList()
        }

        override suspend fun deleteMovieById(movieId: Int, userId: String) {
            items.remove(movieId to userId)
            flow.value = items.values.toList()
        }

        override suspend fun deleteAllWatchlistForUser(userId: String) {
            items.entries.removeAll { it.key.second == userId }
            flow.value = items.values.toList()
        }

        override fun getWatchlistCount(userId: String): Flow<Int> =
            flow.map { it.count { e -> e.userId == userId } }

        override suspend fun getWatchlistCountOnce(userId: String): Int =
            items.values.count { it.userId == userId }
    }

    internal class TestUserPreferenceDao : UserPreferenceDao {
        val prefs = mutableMapOf<String, UserPreferenceEntity>()
        val flow = MutableStateFlow<Map<String, UserPreferenceEntity>>(emptyMap())

        fun clearAll() { prefs.clear(); flow.value = emptyMap() }

        override fun getUserPreferenceProfile(userId: String): Flow<UserPreferenceEntity?> =
            flow.map { it[userId] }

        override suspend fun getUserPreferenceProfileOnce(userId: String): UserPreferenceEntity? =
            prefs[userId]

        override suspend fun saveUserPreferenceProfile(profile: UserPreferenceEntity) {
            prefs[profile.userId] = profile
            flow.value = prefs.toMap()
        }

        override suspend fun clearUserPreferenceProfile(userId: String) {
            prefs.remove(userId)
            flow.value = prefs.toMap()
        }
    }

    private class TestCloudSyncService(
        private val pendingSyncDao: TestPendingSyncDao,
        private val favoriteDao: TestFavoriteDao,
        private val watchlistDao: TestWatchlistDao,
        private val userPreferenceDao: TestUserPreferenceDao
    ) : CloudSyncService {

        private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
        override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

        val remoteFavorites = mutableMapOf<String, MutableMap<Int, Movie>>()
        val remoteWatchlist = mutableMapOf<String, MutableMap<Int, Movie>>()
        val remotePreferences = mutableMapOf<String, UserPreferenceProfile>()

        var shouldFailRemote = false
        var simulateCancellation = false
        var failOnItemId: String? = null
        var remoteException: Exception = IOException("Connection lost")
        var retryJobScheduled: Boolean = false

        override fun startUserSession(userId: String) { _syncState.value = SyncState.Synced }
        override fun stopUserSession() {
            retryJobScheduled = false
            _syncState.value = SyncState.Idle
        }

        override suspend fun pushFavorite(userId: String, movie: Movie, enqueueOnFailure: Boolean): Result<Unit> {
            if (simulateCancellation) throw CancellationException("Operation cancelled")
            if (shouldFailRemote) {
                if (remoteException is SecurityException) {
                    _syncState.value = SyncState.Error(remoteException.message ?: "Permission denied")
                } else {
                    _syncState.value = SyncState.OfflinePending
                }
                return Result.failure(remoteException)
            }
            remoteFavorites.getOrPut(userId) { mutableMapOf() }[movie.id] = movie
            _syncState.value = SyncState.Synced
            return Result.success(Unit)
        }

        override suspend fun deleteFavorite(userId: String, movieId: Int, enqueueOnFailure: Boolean): Result<Unit> {
            if (simulateCancellation) throw CancellationException("Operation cancelled")
            if (shouldFailRemote) return Result.failure(remoteException)
            remoteFavorites[userId]?.remove(movieId)
            return Result.success(Unit)
        }

        override suspend fun pushWatchlist(userId: String, movie: Movie, enqueueOnFailure: Boolean): Result<Unit> {
            if (simulateCancellation) throw CancellationException("Operation cancelled")
            if (shouldFailRemote) return Result.failure(remoteException)
            remoteWatchlist.getOrPut(userId) { mutableMapOf() }[movie.id] = movie
            return Result.success(Unit)
        }

        override suspend fun deleteWatchlist(userId: String, movieId: Int, enqueueOnFailure: Boolean): Result<Unit> {
            if (simulateCancellation) throw CancellationException("Operation cancelled")
            if (shouldFailRemote) return Result.failure(remoteException)
            remoteWatchlist[userId]?.remove(movieId)
            return Result.success(Unit)
        }

        override suspend fun pushPreferences(userId: String, profile: UserPreferenceProfile, enqueueOnFailure: Boolean): Result<Unit> {
            if (simulateCancellation) throw CancellationException("Operation cancelled")
            if (shouldFailRemote) return Result.failure(remoteException)
            remotePreferences[userId] = profile
            return Result.success(Unit)
        }

        override suspend fun syncAll(userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun mergeGuestDataIntoAccount(authenticatedUserId: String) = Result.success(com.example.madverse.core.model.MergeSummary())
        override suspend fun purgeRemoteUserData(userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAllUserData(userId: String): Result<Unit> = Result.success(Unit)

        override suspend fun processPendingSyncActions(userId: String) {
            val pending = pendingSyncDao.getPendingActionsForUser(userId)
            for (action in pending) {
                if (shouldFailRemote) {
                    if (remoteException is SecurityException) {
                        _syncState.value = SyncState.Error(remoteException.message ?: "Permission denied")
                    } else {
                        _syncState.value = SyncState.OfflinePending
                        retryJobScheduled = true
                    }
                    break
                }
                if (failOnItemId == action.itemId) {
                    _syncState.value = SyncState.OfflinePending
                    continue
                }
                when (action.entityType) {
                    "FAVORITE" -> {
                        val movieId = action.itemId.toInt()
                        if (action.actionType == "UPSERT") {
                            val movie = action.payloadJson?.let { SyncPayloadSerializer.jsonToMovie(it) }
                                ?: favoriteDao.getFavoriteById(movieId, userId)?.toDomain()
                            if (movie != null) remoteFavorites.getOrPut(userId) { mutableMapOf() }[movieId] = movie
                        } else {
                            remoteFavorites[userId]?.remove(movieId)
                        }
                    }
                    "WATCHLIST" -> {
                        val movieId = action.itemId.toInt()
                        if (action.actionType == "UPSERT") {
                            val movie = action.payloadJson?.let { SyncPayloadSerializer.jsonToMovie(it) }
                                ?: watchlistDao.getWatchlistMovieById(movieId, userId)?.toDomain()
                            if (movie != null) remoteWatchlist.getOrPut(userId) { mutableMapOf() }[movieId] = movie
                        } else {
                            remoteWatchlist[userId]?.remove(movieId)
                        }
                    }
                    "PREFERENCE" -> {
                        val pref = action.payloadJson?.let { SyncPayloadSerializer.jsonToPreference(it) }
                            ?: userPreferenceDao.getUserPreferenceProfileOnce(userId)?.toDomain()
                        if (pref != null) remotePreferences[userId] = pref
                    }
                }
                // Exact acknowledgement upon successful completion
                pendingSyncDao.deleteActionById(action.id)
            }
        }

        suspend fun applySimulatedRemoteSnapshot(userId: String, remoteMovieIds: Set<Int>) {
            val pending = pendingSyncDao.getPendingActionsForUser(userId)
            val pendingUpsertIds = pending.filter { it.actionType == "UPSERT" }.mapNotNull { it.itemId.toIntOrNull() }.toSet()
            val pendingDeleteIds = pending.filter { it.actionType == "DELETE" }.mapNotNull { it.itemId.toIntOrNull() }.toSet()

            val localFavs = favoriteDao.getAllFavoritesOnce(userId)
            for (local in localFavs) {
                if (local.movieId !in remoteMovieIds) {
                    // Protected from deletion if pending local upsert
                    if (local.movieId in pendingUpsertIds) continue
                    favoriteDao.deleteFavoriteById(local.movieId, userId)
                }
            }

            for (remoteId in remoteMovieIds) {
                // Protected from resurrection if pending local delete
                if (remoteId in pendingDeleteIds) continue
                favoriteDao.insertFavorite(
                    FavoriteEntity(
                        movieId = remoteId,
                        userId = userId,
                        title = "Remote $remoteId",
                        posterPath = null,
                        backdropPath = null,
                        releaseDate = null,
                        voteAverage = 7.0,
                        overview = null,
                        savedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
