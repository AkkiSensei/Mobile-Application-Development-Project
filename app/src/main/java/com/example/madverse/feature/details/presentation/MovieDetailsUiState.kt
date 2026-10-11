package com.example.madverse.feature.details.presentation

import com.example.madverse.core.common.config.RegionConfig
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.model.CastMember
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.VideoTrailer
import com.example.madverse.core.model.WatchProvider

/**
 * UI State for the Movie Details screen.
 *
 * @param detailsState Represents loading/success/error state of primary movie details metadata.
 * @param castState Top billed cast members list.
 * @param videosState Associated trailers, teasers, and clips.
 * @param watchProvidersState Official streaming, rental, and purchase providers state wrapper.
 * @param flatrateProviders Subscription-based streaming providers (e.g. Netflix, Prime Video, Disney+).
 * @param rentProviders Digital rental providers (e.g. Apple TV, Google Play).
 * @param buyProviders Digital purchase providers.
 * @param selectedCountryCode Currently selected ISO 3166-1 alpha-2 region code (defaults to "IN").
 * @param tmdbWatchPageUrl Direct link to the JustWatch-powered TMDB watch page.
 * @param isFavorite Whether this movie is saved in the user's local Favorites table.
 * @param isInWatchlist Whether this movie is saved in the user's local Watchlist table.
 */
data class MovieDetailsUiState(
    val detailsState: UiState<MovieDetails> = UiState.Loading,
    val castState: UiState<List<CastMember>> = UiState.Loading,
    val videosState: UiState<List<VideoTrailer>> = UiState.Loading,
    val watchProvidersState: UiState<MovieWatchProviders> = UiState.Loading,
    val flatrateProviders: List<WatchProvider> = emptyList(),
    val rentProviders: List<WatchProvider> = emptyList(),
    val buyProviders: List<WatchProvider> = emptyList(),
    val selectedCountryCode: String = RegionConfig.DEFAULT_COUNTRY_CODE,
    val tmdbWatchPageUrl: String? = null,
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

    /** Whether any watch providers are available for streaming/rent/buy */
    val hasAnyWatchProviders: Boolean
        get() = flatrateProviders.isNotEmpty() || rentProviders.isNotEmpty() || buyProviders.isNotEmpty()
}
