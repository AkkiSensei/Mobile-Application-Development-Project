package com.example.madverse

import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import com.example.madverse.fakes.FakeAuthRepository
import com.example.madverse.fakes.FakeCloudSyncService
import com.example.madverse.feature.auth.presentation.AuthMode
import com.example.madverse.feature.auth.presentation.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var cloudSyncService: FakeCloudSyncService
    private lateinit var testSessionManager: TestSessionManager
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        cloudSyncService = FakeCloudSyncService()
        testSessionManager = TestSessionManager(authRepository)
        viewModel = AuthViewModel(
            authRepository = authRepository,
            sessionManager = testSessionManager,
            cloudSyncService = cloudSyncService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isWelcomeMode_andEmptyFields() {
        val state = viewModel.uiState.value
        assertEquals(AuthMode.WELCOME, state.mode)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun modeSwitching_updatesModeAndClearsMessages() {
        viewModel.setMode(AuthMode.SIGN_IN)
        assertEquals(AuthMode.SIGN_IN, viewModel.uiState.value.mode)

        viewModel.setMode(AuthMode.SIGN_UP)
        assertEquals(AuthMode.SIGN_UP, viewModel.uiState.value.mode)

        viewModel.setMode(AuthMode.FORGOT_PASSWORD)
        assertEquals(AuthMode.FORGOT_PASSWORD, viewModel.uiState.value.mode)
    }

    @Test
    fun signIn_validationFailsOnInvalidEmail() = runTest {
        viewModel.onEmailChanged("invalid-email")
        viewModel.onPasswordChanged("validPassword123")

        var successCalled = false
        viewModel.signInWithEmail { successCalled = true }
        advanceUntilIdle()

        assertFalse(successCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("valid email", ignoreCase = true))
    }

    @Test
    fun signIn_validationFailsOnShortPassword() = runTest {
        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("12345")

        var successCalled = false
        viewModel.signInWithEmail { successCalled = true }
        advanceUntilIdle()

        assertFalse(successCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("6 characters", ignoreCase = true))
    }

    @Test
    fun signIn_successWithoutGuestData_triggersOnSuccessDirectly() = runTest {
        testSessionManager.hasGuestDataResult = false
        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password123")

        var successCalled = false
        viewModel.signInWithEmail { successCalled = true }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(viewModel.uiState.value.showMergeDialog)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun signIn_successWithGuestData_showsMergeDialog() = runTest {
        testSessionManager.hasGuestDataResult = true
        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password123")

        var successCalled = false
        viewModel.signInWithEmail { successCalled = true }
        advanceUntilIdle()

        assertFalse(successCalled)
        assertTrue(viewModel.uiState.value.showMergeDialog)
    }

    @Test
    fun signUp_validationFailsOnMismatchedPasswords() = runTest {
        viewModel.onDisplayNameChanged("Movie Fan")
        viewModel.onEmailChanged("fan@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("password456")

        var successCalled = false
        viewModel.signUpWithEmail { successCalled = true }
        advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals("Passwords do not match.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun signUp_successRegistersUser() = runTest {
        testSessionManager.hasGuestDataResult = false
        viewModel.onDisplayNameChanged("Ritunjay Deo")
        viewModel.onEmailChanged("ritunjay@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("password123")

        var successCalled = false
        viewModel.signUpWithEmail { successCalled = true }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals(AuthState.Authenticated::class, authRepository.authState.value::class)
        assertEquals("ritunjay@example.com", authRepository.currentUser?.email)
    }

    @Test
    fun googleSignIn_successTransitionsState() = runTest {
        testSessionManager.hasGuestDataResult = false

        var successCalled = false
        viewModel.signInWithGoogle("mock_google_id_token") { successCalled = true }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("google_user_123", authRepository.currentUser?.uid)
        assertFalse(viewModel.uiState.value.isGoogleSignInLoading)
    }

    @Test
    fun passwordReset_sendsEmailAndSetsInfoMessage() = runTest {
        viewModel.onEmailChanged("resetme@example.com")
        viewModel.sendPasswordResetEmail()
        advanceUntilIdle()

        assertEquals(1, authRepository.passwordResetEmailsSent.size)
        assertEquals("resetme@example.com", authRepository.passwordResetEmailsSent.first())
        assertNotNull(viewModel.uiState.value.infoMessage)
        assertTrue(viewModel.uiState.value.infoMessage!!.contains("Password reset email sent"))
    }

    @Test
    fun mergeConfirmed_executesMergeAndFinishes() = runTest {
        authRepository.setAuthenticated(AuthUser("uid_123", "u@e.com", "User", null, true))
        var finishCalled = false
        viewModel.onMergeConfirmed { finishCalled = true }
        advanceUntilIdle()

        assertTrue(finishCalled)
        assertFalse(viewModel.uiState.value.showMergeDialog)
    }

    @Test
    fun mergeDismissed_dismissesDialogAndFinishes() = runTest {
        var finishCalled = false
        viewModel.onMergeDismissed { finishCalled = true }

        assertTrue(finishCalled)
        assertFalse(viewModel.uiState.value.showMergeDialog)
    }

    private class TestSessionManager(
        private val authRepo: FakeAuthRepository
    ) : SessionManager {
        var hasGuestDataResult: Boolean = false

        override val currentUserId: String get() = authRepo.currentUserId
        override val currentUserIdFlow: Flow<String> get() = authRepo.currentUserIdFlow
        override val authState: StateFlow<AuthState> get() = authRepo.authState
        override val isGuest: Boolean get() = authRepo.currentUser == null

        override suspend fun hasGuestData(): Boolean = hasGuestDataResult
    }
}
