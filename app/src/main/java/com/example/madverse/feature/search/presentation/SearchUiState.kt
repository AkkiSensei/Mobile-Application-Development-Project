package com.example.madverse.feature.search.presentation

import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.model.Movie

/**
 * UI State for the Movie Search screen.
 *
 * @param query The current text entered into the search input.
 * @param searchState The reactive UI state (Loading, Success, Empty, Error) representing search results.
 * @param activeQuery The query string for which the current [searchState] was actually resolved.
 */
data class SearchUiState(
    val query: String = "",
    val searchState: UiState<List<Movie>> = UiState.Empty(DEFAULT_EMPTY_PROMPT),
    val activeQuery: String = ""
) {
    /** True if user has not yet entered any search text. */
    val isIdle: Boolean
        get() = query.isBlank()

    companion object {
        const val DEFAULT_EMPTY_PROMPT = "Search movies by title, director, or franchise..."
    }
}
