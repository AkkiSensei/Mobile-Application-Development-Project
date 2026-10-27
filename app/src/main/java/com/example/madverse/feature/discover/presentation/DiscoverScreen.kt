package com.example.madverse.feature.discover.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.designsystem.components.HeroFeaturedMovieCard
import com.example.madverse.core.designsystem.components.HeroFeaturedMovieSkeleton
import com.example.madverse.core.designsystem.components.MADVerseErrorView
import com.example.madverse.core.designsystem.components.MoviePosterCard
import com.example.madverse.core.designsystem.components.MoviePosterSkeleton
import com.example.madverse.core.designsystem.components.SectionEmptyCard
import com.example.madverse.core.designsystem.components.SectionErrorCard
import com.example.madverse.core.designsystem.components.SectionHeader
import com.example.madverse.core.designsystem.components.TmdbAttributionFooter
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ErrorRed
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.model.Movie

/**
 * Discover & Home screen for MADVerse.
 *
 * Implements R2: Home and Movie Discovery.
 * Features:
 * - Spotlight Hero banner featuring top trending movie
 * - Horizontal carousel of Trending movies
 * - Horizontal carousel of Top-Rated movies
 * - Horizontal carousel of Upcoming movies
 * - Per-section loading skeletons, empty states, and error retries
 * - Lifecycle-aware UI state collection
 * - Navigation to Movie Details via TMDB movie IDs
 */
@Composable
fun DiscoverScreen(
    onMovieClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        if (uiState.showFullScreenError) {
            val isOffline = uiState.isAnyOffline
            MADVerseErrorView(
                title = if (isOffline) "No Internet Connection" else "Unable to Load Movies",
                message = if (isOffline)
                    "You are currently offline. Please check your connection to browse trending and upcoming movies."
                else
                    "Could not fetch trending and discovery feeds. Please check your internet connection or TMDB API configuration.",
                isOffline = isOffline,
                retryLabel = "Retry Discovery Feed",
                onRetry = { viewModel.loadAllSections() }
            )
        } else {
            DiscoverContent(
                uiState = uiState,
                onMovieClick = onMovieClick,
                onRetryTrending = { viewModel.loadTrending() },
                onRetryTopRated = { viewModel.loadTopRated() },
                onRetryUpcoming = { viewModel.loadUpcoming() },
                onClearHistory = viewModel::clearHistory
            )
        }
    }
}

@Composable
private fun DiscoverContent(
    uiState: HomeUiState,
    onMovieClick: (Int) -> Unit,
    onRetryTrending: () -> Unit,
    onRetryTopRated: () -> Unit,
    onRetryUpcoming: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // -------------------------------------------------------------
        // Featured Spotlight Hero
        // -------------------------------------------------------------
        if (uiState.featuredMovie != null || uiState.trendingState is UiState.Loading) {
            item(key = "featured_spotlight") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    when {
                        uiState.featuredMovie != null -> {
                            HeroFeaturedMovieCard(
                                movie = uiState.featuredMovie!!,
                                onClick = { onMovieClick(uiState.featuredMovie!!.id) }
                            )
                        }
                        uiState.trendingState is UiState.Loading -> {
                            HeroFeaturedMovieSkeleton()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // -------------------------------------------------------------
        // Section 1: Trending Movies
        // -------------------------------------------------------------
        item(key = "section_trending") {
            SectionHeader(
                title = "Trending Now",
                subtitle = "The most-watched films worldwide today"
            )
            Spacer(modifier = Modifier.height(10.dp))

            when (val state = uiState.trendingState) {
                is UiState.Loading -> {
                    MovieCarouselLoadingSkeleton()
                }
                is UiState.Success -> {
                    val movies = uiState.trendingMoviesList
                    if (movies.isEmpty()) {
                        SectionEmptyCard(message = "No additional trending movies available.")
                    } else {
                        MovieHorizontalCarousel(
                            movies = movies,
                            onMovieClick = onMovieClick
                        )
                    }
                }
                is UiState.Empty -> {
                    SectionEmptyCard(message = state.message)
                }
                is UiState.Error -> {
                    SectionErrorCard(
                        errorMessage = state.message,
                        onRetry = onRetryTrending,
                        isOffline = state.isOffline
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // -------------------------------------------------------------
        // Section: Recently Viewed (Local History)
        // -------------------------------------------------------------
        if (uiState.recentlyViewed.isNotEmpty()) {
            item(key = "section_recently_viewed") {
                RecentlyViewedSection(uiState.recentlyViewed, uiState.isClearingHistory,
                    uiState.historyError, onMovieClick, onClearHistory)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // -------------------------------------------------------------
        // Section 2: Top Rated Movies
        // -------------------------------------------------------------
        item(key = "section_top_rated") {
            SectionHeader(
                title = "Top Rated Masterworks",
                subtitle = "Films with the highest community scores"
            )
            Spacer(modifier = Modifier.height(10.dp))

            when (val state = uiState.topRatedState) {
                is UiState.Loading -> {
                    MovieCarouselLoadingSkeleton()
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        SectionEmptyCard(message = "No top-rated movies found at this time.")
                    } else {
                        MovieHorizontalCarousel(
                            movies = state.data,
                            onMovieClick = onMovieClick
                        )
                    }
                }
                is UiState.Empty -> {
                    SectionEmptyCard(message = state.message)
                }
                is UiState.Error -> {
                    SectionErrorCard(
                        errorMessage = state.message,
                        onRetry = onRetryTopRated,
                        isOffline = state.isOffline
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // -------------------------------------------------------------
        // Section 3: Upcoming Releases
        // -------------------------------------------------------------
        item(key = "section_upcoming") {
            SectionHeader(
                title = "Upcoming Releases",
                subtitle = "Anticipated films arriving in theaters soon"
            )
            Spacer(modifier = Modifier.height(10.dp))

            when (val state = uiState.upcomingState) {
                is UiState.Loading -> {
                    MovieCarouselLoadingSkeleton()
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        SectionEmptyCard(message = "No upcoming releases scheduled.")
                    } else {
                        MovieHorizontalCarousel(
                            movies = state.data,
                            onMovieClick = onMovieClick
                        )
                    }
                }
                is UiState.Empty -> {
                    SectionEmptyCard(message = state.message)
                }
                is UiState.Error -> {
                    SectionErrorCard(
                        errorMessage = state.message,
                        onRetry = onRetryUpcoming,
                        isOffline = state.isOffline
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TmdbAttributionFooter()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Horizontal scrolling row of movie poster cards.
 */
@Composable
internal fun RecentlyViewedSection(
    movies: List<Movie>, isClearing: Boolean, error: String?,
    onMovieClick: (Int) -> Unit, onClearHistory: () -> Unit
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column {
        SectionHeader(title = "Recently Viewed", subtitle = "Movie details you opened on this device")
        TextButton(onClick = { confirmClear = true }, enabled = !isClearing && movies.isNotEmpty(),
            modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(if (isClearing) "Clearing history..." else "Clear history")
        }
        error?.let { Text(it, color = ErrorRed, modifier = Modifier.padding(horizontal = 16.dp)) }
        MovieHorizontalCarousel(movies = movies, onMovieClick = onMovieClick)
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear recently viewed history?") },
            text = { Text("Remove the movie details history stored on this device. Favorites and Watchlist will stay saved. Movies opened later will appear again.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearHistory() }, enabled = !isClearing) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MovieHorizontalCarousel(
    movies: List<Movie>,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            items = movies,
            key = { movie -> movie.id }
        ) { movie ->
            MoviePosterCard(
                movie = movie,
                onClick = { onMovieClick(movie.id) }
            )
        }
    }
}

/**
 * Placeholder loading skeleton for a horizontal carousel row.
 */
@Composable
private fun MovieCarouselLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false
    ) {
        items(count = 5) {
            MoviePosterSkeleton()
        }
    }
}
