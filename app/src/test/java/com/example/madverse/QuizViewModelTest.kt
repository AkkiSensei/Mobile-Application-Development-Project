package com.example.madverse

import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.QuizQuestion
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile
import com.example.madverse.feature.quiz.presentation.QuestionStep
import com.example.madverse.feature.quiz.presentation.QuizAnswers
import com.example.madverse.feature.quiz.presentation.QuizProfileMapper
import com.example.madverse.feature.quiz.presentation.QuizViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
import java.io.IOException

/**
 * Unit tests for [QuizViewModel], [QuizProfileMapper], and quiz state validation rules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var fakeQuizRepository: FakeQuizRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeQuizRepository = FakeQuizRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_defaultsToStep1WithEmptySelections() = runTest {
        val viewModel = QuizViewModel(fakeQuizRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(QuestionStep.GENRES, state.currentStep)
        assertEquals(0, state.stepIndex)
        assertTrue(state.answers.selectedGenreIds.isEmpty())
        assertFalse(state.canGoBack)
        assertFalse(state.canGoForward) // Cannot advance without picking a genre
        assertFalse(state.initialProfileRestored)
    }

    @Test
    fun restoredProfile_populatesAnswersOnLaunch() = runTest {
        val savedProfile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878),
            mood = MovieMood.MIND_BENDING,
            era = MovieEra.CLASSIC,
            runtimePreference = RuntimePreference.EPIC,
            preferredLanguage = "ja",
            minRatingThreshold = 7.5
        )
        fakeQuizRepository.saveUserPreferenceProfile(savedProfile)

        val viewModel = QuizViewModel(fakeQuizRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf(28, 878), state.answers.selectedGenreIds)
        assertEquals(MovieMood.MIND_BENDING, state.answers.mood)
        assertEquals(MovieEra.CLASSIC, state.answers.era)
        assertEquals(RuntimePreference.EPIC, state.answers.runtimePreference)
        assertEquals("ja", state.answers.preferredLanguage)
        assertEquals(7.5, state.answers.minRatingThreshold, 0.001)
        assertTrue(state.canGoForward)
    }

    @Test
    fun toggleGenre_addsAndRemovesGenres_updatesValidation() = runTest {
        val viewModel = QuizViewModel(fakeQuizRepository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canGoForward)

        viewModel.toggleGenre(28) // Action
        assertTrue(viewModel.uiState.value.answers.selectedGenreIds.contains(28))
        assertTrue(viewModel.uiState.value.canGoForward)

        viewModel.toggleGenre(878) // Sci-Fi
        assertEquals(setOf(28, 878), viewModel.uiState.value.answers.selectedGenreIds)

        viewModel.toggleGenre(28) // Remove Action
        assertEquals(setOf(878), viewModel.uiState.value.answers.selectedGenreIds)
        assertTrue(viewModel.uiState.value.canGoForward)

        viewModel.toggleGenre(878) // Remove Sci-Fi
        assertTrue(viewModel.uiState.value.answers.selectedGenreIds.isEmpty())
        assertFalse(viewModel.uiState.value.canGoForward)
    }

    @Test
    fun navigation_stepProgressionAndBackwardNavigation() = runTest {
        val viewModel = QuizViewModel(fakeQuizRepository)
        advanceUntilIdle()

        // At Step 1 without genres: cannot advance
        viewModel.goToNextStep()
        assertEquals(QuestionStep.GENRES, viewModel.uiState.value.currentStep)

        // Select genre to unlock step 1
        viewModel.toggleGenre(35) // Comedy
        assertTrue(viewModel.uiState.value.canGoForward)

        // Advance: GENRES -> LANGUAGE
        viewModel.goToNextStep()
        assertEquals(QuestionStep.LANGUAGE, viewModel.uiState.value.currentStep)
        assertTrue(viewModel.uiState.value.canGoBack)

        // Advance: LANGUAGE -> MOOD
        viewModel.goToNextStep()
        assertEquals(QuestionStep.MOOD, viewModel.uiState.value.currentStep)

        // Advance: MOOD -> ERA
        viewModel.goToNextStep()
        assertEquals(QuestionStep.ERA, viewModel.uiState.value.currentStep)

        // Advance: ERA -> RUNTIME
        viewModel.goToNextStep()
        assertEquals(QuestionStep.RUNTIME, viewModel.uiState.value.currentStep)

        // Advance: RUNTIME -> RATING
        viewModel.goToNextStep()
        assertEquals(QuestionStep.RATING, viewModel.uiState.value.currentStep)

        // At final step, next step shows completion confirmation dialog
        assertFalse(viewModel.uiState.value.showCompletionConfirmation)
        viewModel.goToNextStep()
        assertTrue(viewModel.uiState.value.showCompletionConfirmation)

        // Dismiss dialog
        viewModel.dismissCompletionDialog()
        assertFalse(viewModel.uiState.value.showCompletionConfirmation)

        // Navigate back step by step
        viewModel.goToPreviousStep()
        assertEquals(QuestionStep.RUNTIME, viewModel.uiState.value.currentStep)

        viewModel.goToPreviousStep()
        assertEquals(QuestionStep.ERA, viewModel.uiState.value.currentStep)
    }

    @Test
    fun answerMutations_updateStateProperly() = runTest {
        val viewModel = QuizViewModel(fakeQuizRepository)
        advanceUntilIdle()

        viewModel.setPreferredLanguage("ko")
        assertEquals("ko", viewModel.uiState.value.answers.preferredLanguage)

        viewModel.setMood(MovieMood.ADRENALINE)
        assertEquals(MovieMood.ADRENALINE, viewModel.uiState.value.answers.mood)

        viewModel.setEra(MovieEra.MODERN)
        assertEquals(MovieEra.MODERN, viewModel.uiState.value.answers.era)

        viewModel.setRuntimePreference(RuntimePreference.QUICK)
        assertEquals(RuntimePreference.QUICK, viewModel.uiState.value.answers.runtimePreference)

        viewModel.setMinRatingThreshold(8.2)
        assertEquals(8.2, viewModel.uiState.value.answers.minRatingThreshold, 0.001)
    }

    @Test
    fun submitQuiz_persistsProfileToRoom_andCompletes() = runTest {
        val viewModel = QuizViewModel(fakeQuizRepository)
        advanceUntilIdle()

        viewModel.toggleGenre(28)
        viewModel.setPreferredLanguage("en")
        viewModel.setMood(MovieMood.FEEL_GOOD)
        viewModel.setEra(MovieEra.CONTEMPORARY)
        viewModel.setRuntimePreference(RuntimePreference.STANDARD)
        viewModel.setMinRatingThreshold(7.0)

        var completedCallbackTriggered = false
        viewModel.submitQuiz {
            completedCallbackTriggered = true
        }
        advanceUntilIdle()

        assertTrue(completedCallbackTriggered)
        assertTrue(viewModel.uiState.value.isComplete)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.errorMessage)

        // Verify Room persistence via FakeQuizRepository
        val persisted = fakeQuizRepository.getUserPreferenceProfileOnce()
        assertEquals(setOf(28), persisted.selectedGenreIds)
        assertEquals("en", persisted.preferredLanguage)
        assertEquals(MovieMood.FEEL_GOOD, persisted.mood)
        assertEquals(MovieEra.CONTEMPORARY, persisted.era)
        assertEquals(RuntimePreference.STANDARD, persisted.runtimePreference)
        assertEquals(7.0, persisted.minRatingThreshold, 0.001)
    }

    @Test
    fun submitQuiz_repositoryError_updatesErrorMessage() = runTest {
        fakeQuizRepository.shouldThrowOnSave = true
        val viewModel = QuizViewModel(fakeQuizRepository)
        advanceUntilIdle()

        viewModel.toggleGenre(28)

        var completedCallbackTriggered = false
        viewModel.submitQuiz {
            completedCallbackTriggered = true
        }
        advanceUntilIdle()

        assertFalse(completedCallbackTriggered)
        assertFalse(viewModel.uiState.value.isComplete)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.saveErrorMessage)
        assertTrue(viewModel.uiState.value.saveErrorMessage!!.contains("Disk write failure"))
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.showCompletionConfirmation)
    }

    @Test
    fun resetQuiz_restoresDefaults() = runTest {
        val viewModel = QuizViewModel(fakeQuizRepository)
        advanceUntilIdle()

        viewModel.toggleGenre(28)
        viewModel.goToNextStep()
        viewModel.setPreferredLanguage("es")

        viewModel.resetQuiz()

        val state = viewModel.uiState.value
        assertEquals(QuestionStep.GENRES, state.currentStep)
        assertTrue(state.answers.selectedGenreIds.isEmpty())
        assertNull(state.answers.preferredLanguage)
        assertFalse(state.isComplete)
        assertFalse(state.showCompletionConfirmation)
    }

    @Test fun cancelledSaveDoesNotBecomeErrorOrCompleteQuiz() = runTest {
        fakeQuizRepository.cancelSave = true
        val vm = QuizViewModel(fakeQuizRepository); advanceUntilIdle()
        vm.toggleGenre(28)
        var completed = false
        vm.submitQuiz { completed = true }; advanceUntilIdle()
        assertFalse(completed); assertFalse(vm.uiState.value.isComplete)
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun duplicateSubmissionWritesOnceAndCompletesOnce() = runTest {
        val vm = QuizViewModel(fakeQuizRepository); advanceUntilIdle()
        vm.toggleGenre(28)
        var completions = 0
        vm.submitQuiz { completions++ }; vm.submitQuiz { completions++ }
        assertTrue(vm.uiState.value.isSaving)
        advanceUntilIdle()
        assertEquals(1, fakeQuizRepository.saveCalls)
        assertEquals(1, completions)
    }

    @Test fun cancelledRestoreDoesNotReportDatabaseFailure() = runTest {
        fakeQuizRepository.cancelRestore = true
        val vm = QuizViewModel(fakeQuizRepository); advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        vm.resetQuiz()
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun retrySave_preservesEditedAnswersInsteadOfRestoringOldProfile() = runTest {
        fakeQuizRepository.saveUserPreferenceProfile(UserPreferenceProfile(selectedGenreIds = setOf(18)))
        val vm = QuizViewModel(fakeQuizRepository); advanceUntilIdle()
        vm.toggleGenre(28)
        vm.goToStep(QuestionStep.RATING)
        vm.setMinRatingThreshold(8.5)
        val edited = vm.uiState.value.answers
        fakeQuizRepository.shouldThrowOnSave = true
        vm.submitQuiz(); advanceUntilIdle()
        assertEquals(edited, vm.uiState.value.answers)
        assertEquals(QuestionStep.RATING, vm.uiState.value.currentStep)
        assertTrue(vm.uiState.value.showCompletionConfirmation)
        fakeQuizRepository.shouldThrowOnSave = false
        var completed = false
        vm.submitQuiz { completed = true }; advanceUntilIdle()
        assertTrue(completed)
        assertEquals(edited.selectedGenreIds, fakeQuizRepository.getUserPreferenceProfileOnce().selectedGenreIds)
        assertEquals(8.5, fakeQuizRepository.getUserPreferenceProfileOnce().minRatingThreshold, 0.0)
        assertNull(vm.uiState.value.saveErrorMessage)
    }

    @Test
    fun reviewAfterSaveFailure_keepsAnswersAndClearsOnlySaveError() = runTest {
        val vm = QuizViewModel(fakeQuizRepository); advanceUntilIdle()
        vm.toggleGenre(28)
        fakeQuizRepository.shouldThrowOnSave = true
        vm.submitQuiz(); advanceUntilIdle()
        vm.dismissCompletionDialog()
        assertFalse(vm.uiState.value.showCompletionConfirmation)
        assertEquals(setOf(28), vm.uiState.value.answers.selectedGenreIds)
        assertNull(vm.uiState.value.saveErrorMessage)
        vm.resetQuiz()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun quizProfileMapper_bidirectionalMappingIntegrity() {
        val answers = QuizAnswers(
            selectedGenreIds = setOf(18, 10749),
            preferredLanguage = "  fr  ",
            mood = MovieMood.EMOTIONAL,
            era = MovieEra.MODERN,
            runtimePreference = RuntimePreference.STANDARD,
            minRatingThreshold = 6.8
        )

        val profile = QuizProfileMapper.mapToUserProfile(answers, currentTimeMillis = 123456789L)

        assertEquals(setOf(18, 10749), profile.selectedGenreIds)
        assertEquals("fr", profile.preferredLanguage) // Whitespace trimmed
        assertEquals(MovieMood.EMOTIONAL, profile.mood)
        assertEquals(MovieEra.MODERN, profile.era)
        assertEquals(RuntimePreference.STANDARD, profile.runtimePreference)
        assertEquals(6.8, profile.minRatingThreshold, 0.001)
        assertEquals(123456789L, profile.updatedAtTimestamp)

        val restoredAnswers = QuizProfileMapper.mapToQuizAnswers(profile)
        assertEquals(setOf(18, 10749), restoredAnswers.selectedGenreIds)
        assertEquals("fr", restoredAnswers.preferredLanguage)
        assertEquals(MovieMood.EMOTIONAL, restoredAnswers.mood)
        assertEquals(MovieEra.MODERN, restoredAnswers.era)
        assertEquals(RuntimePreference.STANDARD, restoredAnswers.runtimePreference)
        assertEquals(6.8, restoredAnswers.minRatingThreshold, 0.001)
    }

    @Test
    fun quizProfileMapper_clampsRatingThreshold() {
        val answersHigh = QuizAnswers(selectedGenreIds = setOf(28), minRatingThreshold = 15.0)
        val profileHigh = QuizProfileMapper.mapToUserProfile(answersHigh)
        assertEquals(10.0, profileHigh.minRatingThreshold, 0.001)

        val answersLow = QuizAnswers(selectedGenreIds = setOf(28), minRatingThreshold = -2.0)
        val profileLow = QuizProfileMapper.mapToUserProfile(answersLow)
        assertEquals(0.0, profileLow.minRatingThreshold, 0.001)
    }
}

/**
 * In-memory test double for [QuizRepository].
 */
private class FakeQuizRepository : QuizRepository {
    private val profileFlow = MutableStateFlow(UserPreferenceProfile())
    var shouldThrowOnSave = false
    var cancelSave = false
    var cancelRestore = false
    var saveCalls = 0

    override fun getUserPreferenceProfile(): Flow<UserPreferenceProfile> = profileFlow

    override suspend fun getUserPreferenceProfileOnce(): UserPreferenceProfile {
        if (cancelRestore) throw kotlinx.coroutines.CancellationException("Cancelled")
        return profileFlow.value
    }

    override suspend fun saveUserPreferenceProfile(profile: UserPreferenceProfile) {
        saveCalls++
        if (cancelSave) throw kotlinx.coroutines.CancellationException("Cancelled")
        if (shouldThrowOnSave) {
            throw IOException("Disk write failure")
        }
        profileFlow.value = profile
    }

    override suspend fun clearUserPreferenceProfile() {
        profileFlow.value = UserPreferenceProfile()
    }

    override fun getQuizQuestions(): List<QuizQuestion> = emptyList()
}
