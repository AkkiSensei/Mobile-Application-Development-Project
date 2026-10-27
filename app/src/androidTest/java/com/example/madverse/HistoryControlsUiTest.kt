package com.example.madverse

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.madverse.core.designsystem.theme.MADVerseTheme
import com.example.madverse.core.model.Movie
import com.example.madverse.feature.discover.presentation.RecentlyViewedSection
import com.example.madverse.core.navigation.Screen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoryControlsUiTest {
    @get:Rule val compose = createComposeRule()
    private val movie = Movie(42, "History Movie", "", null, null, null, 7.5, 0, emptyList(), 0.0)

    @Test fun historyRequiresConfirmationAndForwardsTheExactMovieId() {
        var clears = 0
        var selected = 0
        compose.setContent { MADVerseTheme { RecentlyViewedSection(listOf(movie), false, null, { selected = it }, { clears++ }) } }
        compose.onNodeWithText("History Movie").performClick()
        assertEquals(42, selected)
        assertEquals("movie_details/42", Screen.MovieDetails.createRoute(selected))
        compose.onNodeWithText("Clear history").performClick()
        assertEquals(0, clears)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, clears)
        compose.onNodeWithText("Clear history").performClick()
        compose.onNodeWithText("Clear", substring = false).performClick()
        assertEquals(1, clears)
    }

    @Test fun pendingClearIsDisabledAndStorageErrorIsVisible() {
        compose.setContent { MADVerseTheme { RecentlyViewedSection(listOf(movie), true,
            "Could not clear history. Please try again.", {}, { error("Must be disabled") }) } }
        compose.onNodeWithText("Clearing history...").assertIsNotEnabled()
        compose.onNodeWithText("Could not clear history. Please try again.").assertIsDisplayed()
    }
}
