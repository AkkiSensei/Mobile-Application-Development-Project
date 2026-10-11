package com.example.madverse.feature.details.presentation

import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.model.CastMember
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.VideoTrailer

/**
 * UI State for the Movie Details screen.
 *
 * @param detailsState Represents loading/success/error state of primary movie details metadata.
 * @param castState Top billed cast members list.
 * @param videosState Associated trailers, teasers, and clips.
 * @param watchProvidersState Official streaming, rental, and purchase providers.
 * @param isFavorite Whether this movie is saved in the user's local Favorites table.
 * @param isInWatchlist Whether this movie is saved in the user's local Watchlist table.
 */
data class MovieDetailsUiState(
    val detailsState: UiState<MovieDetails> = UiState.Loading,
    val castState: UiState<List<CastMember>> = UiState.Loading,
    val videosState: UiState<List<VideoTrailer>> = UiState.Loading,
    val watchProvidersState: UiState<MovieWatchProviders> = UiState.Loading,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false
) {
    /** Resolved title for top bar or accessibility */
    val movieTitle: String
        get() = (detailsState as? UiState.Success)?.data?.title ?: "Movie Details"

    /** Primary trailer, prioritized by official YouTube trailer type */
    val primaryTrailer: VideoTrailer?
        get() = (videosState as? UiState.Success)?.data?.firstOrNull { it.isEmbeddableYouTubeTrailer }
            ?: (videosState as? UiState.Success)?.data?.firstOrNull()
}
