package com.example.madverse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.madverse.core.designsystem.theme.MADVerseTheme
import com.example.madverse.feature.library.presentation.FavoritesEmptyState
import com.example.madverse.feature.library.presentation.WatchlistEmptyState
import com.example.madverse.feature.quiz.presentation.QuizAnswers
import com.example.madverse.feature.quiz.presentation.QuizCompletionConfirmationDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class UxRecoveryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyFavoritesOffersDiscoverAction() {
        var clicks = 0
        compose.setContent { MADVerseTheme { Surface(modifier = Modifier.fillMaxSize()) { FavoritesEmptyState(onExploreMovies = { clicks++ }) } } }
        compose.onNodeWithText("No Favorites Yet").assertIsDisplayed()
        compose.onNodeWithText("Explore Movies").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
        capture("favorites-empty")
    }

    @Test fun emptyWatchlistOffersDiscoverAction() {
        var clicks = 0
        compose.setContent { MADVerseTheme { Surface(modifier = Modifier.fillMaxSize()) { WatchlistEmptyState(onExploreMovies = { clicks++ }) } } }
        compose.onNodeWithText("Your Watchlist is Empty").assertIsDisplayed()
        compose.onNodeWithText("Explore Movies").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
        capture("watchlist-empty")
    }

    @Test fun compactEmptyStateKeepsExploreActionReachable() {
        var clicks = 0
        compose.setContent {
            MADVerseTheme {
                Surface(modifier = Modifier.size(320.dp, 280.dp).testTag("compact-empty")) {
                    FavoritesEmptyState(onExploreMovies = { clicks++ })
                }
            }
        }
        compose.onNodeWithText("Explore Movies").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    @Test fun failedQuizSaveOffersRetryAndReview() {
        var retries = 0
        var reviews = 0
        compose.setContent {
            MADVerseTheme {
                QuizCompletionConfirmationDialog(
                    answers = QuizAnswers(selectedGenreIds = setOf(28, 18, 35, 878, 9648, 10749)),
                    isSaving = false,
                    saveErrorMessage = "Device storage is unavailable",
                    onConfirm = { retries++ }, onDismiss = { reviews++ }
                )
            }
        }
        compose.onNodeWithText("Retry Save").assertIsDisplayed().performClick()
        compose.onNodeWithText("Review").assertIsDisplayed().performClick()
        assertEquals(1, retries)
        assertEquals(1, reviews)
        capture("quiz-save-error", dialog = true)
    }

    @Test fun savingQuizDisablesDuplicateSubmissionAndHidesReview() {
        compose.setContent {
            MADVerseTheme {
                QuizCompletionConfirmationDialog(QuizAnswers(), true,
                    onConfirm = { error("Save must be disabled") }, onDismiss = {})
            }
        }
        compose.onNodeWithContentDescription("Save taste profile and find movie matches").assertIsNotEnabled()
        compose.onNodeWithText("Review").assertDoesNotExist()
    }

    private fun capture(name: String, dialog: Boolean = false) {
        val root = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = root.captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ux-prompt2")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
