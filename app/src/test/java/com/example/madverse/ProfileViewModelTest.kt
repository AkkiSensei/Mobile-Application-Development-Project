package com.example.madverse

import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.QuizQuestion
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.SyncState
import com.example.madverse.core.model.UserPreferenceProfile
import com.example.madverse.fakes.FakeAuthRepository
import com.example.madverse.fakes.FakeCloudSyncService
import com.example.madverse.feature.profile.presentation.ProfileViewModel
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var cloudSyncService: FakeCloudSyncService
    private lateinit var testFavoritesRepo: FakeFavoritesRepository
    private lateinit var testWatchlistRepo: FakeWatchlistRepository
    private lateinit var testQuizRepo: FakeQuizRepository
    private lateinit var testSessionManager: FakeSessionManager
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        cloudSyncService = FakeCloudSyncService()
        testFavoritesRepo = FakeFavoritesRepository()
        testWatchlistRepo = FakeWatchlistRepository()
        testQuizRepo = FakeQuizRepository()
        testSessionManager = FakeSessionManager(authRepository)

        viewModel = ProfileViewModel(
            authRepository = authRepository,
            sessionManager = testSessionManager,
            favoritesRepository = testFavoritesRepo,
            watchlistRepository = testWatchlistRepo,
            quizRepository = testQuizRepo,
            cloudSyncService = cloudSyncService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_guestUser_reflectsGuestState() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.isGuest)
        assertNull(state.authUser)
        assertEquals(0, state.favoritesCount)
        assertEquals(0, state.watchlistCount)
    }

    @Test
    fun authenticatedUser_reflectsAccountDetails() = runTest {
        val user = AuthUser("uid_99", "tester@madverse.com", "Alex", null, true)
        authRepository.setAuthenticated(user)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGuest)
        assertEquals("uid_99", state.authUser?.uid)
        assertEquals("Alex", state.authUser?.displayName)
    }

    @Test
    fun authState_transitionFromLoadingToAuthenticated_clearsLoadingState() = runTest {
        authRepository.setLoading()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        val user = AuthUser("uid_100", "active@madverse.com", "Active User", null, true)
        authRepository.setAuthenticated(user)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isGuest)
        assertEquals("uid_100", state.authUser?.uid)
    }

    @Test
    fun authState_transitionFromLoadingToGuest_clearsLoadingState() = runTest {
        authRepository.setLoading()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        authRepository.setGuest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isGuest)
        assertNull(state.authUser)
    }

    @Test
    fun libraryCounts_reactivelyUpdateFromRepositories() = runTest {
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.favoritesCount)

        testFavoritesRepo.moviesFlow.value = listOf(
            Movie(1, "Movie 1", "", null, null, null, 8.0, 100, emptyList(), 1.0),
            Movie(2, "Movie 2", "", null, null, null, 7.5, 100, emptyList(), 1.0)
        )
        testWatchlistRepo.moviesFlow.value = listOf(
            Movie(3, "Movie 3", "", null, null, null, 8.5, 100, emptyList(), 1.0)
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.favoritesCount)
        assertEquals(1, viewModel.uiState.value.watchlistCount)
    }

    @Test
    fun editDisplayName_blankNameShowsError() = runTest {
        viewModel.startEditingName()
        viewModel.onEditedNameChanged("   ")
        viewModel.saveDisplayName()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("Name cannot be empty.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun editDisplayName_validNameUpdatesRepositoryAndState() = runTest {
        val user = AuthUser("uid_1", "user@test.com", "Old Name", null, true)
        authRepository.setAuthenticated(user)
        advanceUntilIdle()

        viewModel.startEditingName()
        viewModel.onEditedNameChanged("New Name")
        viewModel.saveDisplayName()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isEditingName)
        assertEquals("New Name", authRepository.currentUser?.displayName)
    }

    @Test
    fun sendEmailVerification_triggersRepositoryMethod() = runTest {
        val user = AuthUser("uid_1", "user@test.com", "User", null, false)
        authRepository.setAuthenticated(user)
        advanceUntilIdle()

        viewModel.sendEmailVerification()
        advanceUntilIdle()

        assertTrue(authRepository.emailVerificationSent)
        assertNotNull(viewModel.uiState.value.infoMessage)
    }

    @Test
    fun cloudSyncState_reactivelyReflected() = runTest {
        cloudSyncService.setSyncState(SyncState.OfflinePending)
        advanceUntilIdle()

        assertEquals(SyncState.OfflinePending, viewModel.uiState.value.syncState)

        cloudSyncService.setSyncState(SyncState.Synced)
        advanceUntilIdle()

        assertEquals(SyncState.Synced, viewModel.uiState.value.syncState)
    }

    @Test
    fun signOut_returnsToGuest() = runTest {
        authRepository.setAuthenticated(AuthUser("uid_1", "user@test.com", "User", null, true))
        advanceUntilIdle()

        var signedOutCallbackCalled = false
        viewModel.signOut { signedOutCallbackCalled = true }
        advanceUntilIdle()

        assertTrue(signedOutCallbackCalled)
        assertEquals(AuthState.Guest, authRepository.authState.value)
    }

    @Test
    fun deleteAccount_successPushesPurgeAndDeletes() = runTest {
        authRepository.setAuthenticated(AuthUser("uid_1", "user@test.com", "User", null, true))
        advanceUntilIdle()

        var deletedCallbackCalled = false
        viewModel.deleteAccount { deletedCallbackCalled = true }
        advanceUntilIdle()

        assertTrue(deletedCallbackCalled)
        assertEquals(AuthState.Guest, authRepository.authState.value)
    }

    @Test
    fun deleteAccount_recentLoginRequired_displaysUserFriendlyRecovery() = runTest {
        authRepository.setAuthenticated(AuthUser("uid_1", "user@test.com", "User", null, true))
        authRepository.shouldFailNextOperation = true
        authRepository.failureException = SecurityException("Recent login required")
        advanceUntilIdle()

        var deletedCallbackCalled = false
        viewModel.deleteAccount { deletedCallbackCalled = true }
        advanceUntilIdle()

        assertFalse(deletedCallbackCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("recent authentication", ignoreCase = true))
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        val moviesFlow = MutableStateFlow<List<Movie>>(emptyList())
        override fun getFavoriteMovies(): Flow<List<Movie>> = moviesFlow
        override fun isFavorite(movieId: Int): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun addToFavorites(movie: Movie) {}
        override suspend fun removeFromFavorites(movieId: Int) {}
    }

    private class FakeWatchlistRepository : WatchlistRepository {
        val moviesFlow = MutableStateFlow<List<Movie>>(emptyList())
        override fun getWatchlistMovies(): Flow<List<Movie>> = moviesFlow
        override fun isMovieInWatchlist(movieId: Int): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun addToWatchlist(movie: Movie) {}
        override suspend fun removeFromWatchlist(movieId: Int) {}
    }

    private class FakeQuizRepository : QuizRepository {
        val profileFlow = MutableStateFlow(UserPreferenceProfile())
        val hasProfileFlow = MutableStateFlow(false)
        override fun hasSavedProfile(): Flow<Boolean> = hasProfileFlow
        override fun getUserPreferenceProfile(): Flow<UserPreferenceProfile> = profileFlow
        override suspend fun getUserPreferenceProfileOnce(): UserPreferenceProfile = profileFlow.value
        override suspend fun saveUserPreferenceProfile(profile: UserPreferenceProfile) { profileFlow.value = profile }
        override suspend fun clearUserPreferenceProfile() { profileFlow.value = UserPreferenceProfile() }
        override fun getQuizQuestions(): List<QuizQuestion> = emptyList()
    }

    private class FakeSessionManager(private val authRepo: FakeAuthRepository) : SessionManager {
        override val currentUserId: String get() = authRepo.currentUserId
        override val currentUserIdFlow: Flow<String> get() = authRepo.currentUserIdFlow
        override val authState: StateFlow<AuthState> get() = authRepo.authState
        override val isGuest: Boolean get() = authRepo.currentUser == null
        override suspend fun hasGuestData(): Boolean = false
    }
}
