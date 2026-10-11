package com.example.madverse.feature.discover.presentation

import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.model.Movie

/**
 * UI State for the Discover / Home screen.
 *
 * Holds independent [UiState] streams for Trending, Top Rated, and Upcoming movies,
 * allowing granular loading, error, and retry states for each section without blocking others.
 */
data class HomeUiState(
    val trendingState: UiState<List<Movie>> = UiState.Loading,
    val topRatedState: UiState<List<Movie>> = UiState.Loading,
    val upcomingState: UiState<List<Movie>> = UiState.Loading,
    val isRefreshing: Boolean = false
) {
    /** True if all three sections are currently in their initial loading state. */
    val isInitialLoading: Boolean
        get() = trendingState is UiState.Loading &&
                topRatedState is UiState.Loading &&
                upcomingState is UiState.Loading

    /** True if all three sections failed to load. */
    val isFullError: Boolean
        get() = trendingState is UiState.Error &&
                topRatedState is UiState.Error &&
                upcomingState is UiState.Error

    /** Featured hero movie selected from the top of the trending list, if available. */
    val featuredMovie: Movie?
        get() = (trendingState as? UiState.Success)?.data?.firstOrNull()

    /** Remaining trending movies excluding the featured hero movie. */
    val trendingMoviesList: List<Movie>
        get() = (trendingState as? UiState.Success)?.data?.let { list ->
            if (list.size > 1) list.drop(1) else list
        } ?: emptyList()
}

typealias DiscoverUiState = HomeUiState
