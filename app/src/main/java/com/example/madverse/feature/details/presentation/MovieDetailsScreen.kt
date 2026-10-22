package com.example.madverse.feature.details.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import com.example.madverse.feature.details.presentation.components.StreamingProvidersSection
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.madverse.feature.details.presentation.components.RegionSelectorDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.designsystem.components.GenreChipFlowRow
import com.example.madverse.core.designsystem.components.MovieRatingBadge
import com.example.madverse.core.designsystem.components.RatingBadgeStyle
import com.example.madverse.core.designsystem.components.SectionErrorCard
import com.example.madverse.core.designsystem.components.TmdbAttributionFooter
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.CinematicViolet
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.GlassDark
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.designsystem.theme.RatingGold
import com.example.madverse.core.model.CastMember
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.VideoCategory
import com.example.madverse.core.model.VideoTrailer
import com.example.madverse.core.model.WatchProvider
import com.example.madverse.core.domain.streaming.ProviderDestinationResolver
import com.example.madverse.core.domain.streaming.StreamingContract
import java.text.NumberFormat
import java.util.Locale

/**
 * Movie Details Screen for MADVerse.
 *
 * Implements R4: Movie Details with:
 * - Rich cinematic header (backdrop, poster, title, tagline, rating, runtime)
 * - Favorites & Watchlist persistence toggle actions
 * - Synopsis & genre pills
 * - Top-billed cast credits carousel
 * - YouTube video trailer metadata & launch intent
 * - Official streaming availability (flatrate/rent/buy) integration point
 * - Financial & production metadata
 * - Resilient error handling and loading skeletons
 */
@Composable
fun MovieDetailsScreen(
    movieId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPlayer: ((videoId: String, title: String, category: com.example.madverse.core.model.VideoCategory) -> Unit)? = null,
    viewModel: MovieDetailsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRegionSelector by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(movieId) {
        if (viewModel.currentMovieId != movieId) {
            viewModel.loadMovieDetails(movieId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        when (val detailsState = uiState.detailsState) {
            is UiState.Loading -> {
                MovieDetailsSkeleton(onNavigateBack = onNavigateBack)
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SectionErrorCard(
                        errorMessage = detailsState.message,
                        onRetry = { viewModel.retry() },
                        isOffline = detailsState.isOffline
                    )
                }
            }
            is UiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = detailsState.message,
                        style = MaterialTheme.typography.bodyMedium.copy(color = ContentMediumEmphasis)
                    )
                }
            }
            is UiState.Success -> {
                MovieDetailsContent(
                    movie = detailsState.data,
                    uiState = uiState,
                    onNavigateBack = onNavigateBack,
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onToggleWatchlist = { viewModel.toggleWatchlist() },
                    onSelectRegion = { showRegionSelector = true },
                    onRetryProviders = { viewModel.fetchWatchProviders(movieId) },
                    onOpenVideo = { video ->
                        if (!video.isPlayableInApp) {
                            android.widget.Toast.makeText(context, "This video source is not eligible for in-app playback.", android.widget.Toast.LENGTH_LONG).show()
                        } else if (onNavigateToPlayer != null) {
                            onNavigateToPlayer(video.key, "${detailsState.data.title} - ${video.name}", video.category)
                        } else {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.youtubeWatchUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "No app could open this video.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }

        if (showRegionSelector) {
            RegionSelectorDialog(
                currentCountryCode = uiState.selectedCountryCode,
                onRegionSelected = { selectedCode ->
                    viewModel.setRegion(selectedCode)
                },
                onDismissRequest = {
                    showRegionSelector = false
                }
            )
        }
    }
}

@Composable
private fun MovieDetailsContent(
    movie: MovieDetails,
    uiState: MovieDetailsUiState,
    onNavigateBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onRetryProviders: () -> Unit,
    onSelectRegion: (() -> Unit)? = null,
    onOpenVideo: (VideoTrailer) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val providerResolver = remember { ProviderDestinationResolver() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // -----------------------------------------------------------------
        // 1. Cinematic Backdrop & Poster Hero
        // -----------------------------------------------------------------
        item(key = "details_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                // Backdrop Image
                if (!movie.backdropUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(movie.backdropUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!movie.posterUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(movie.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianSurfaceVariant)
                    )
                }

                // Vertical Gradient Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ObsidianBackground.copy(alpha = 0.6f),
                                    ObsidianBackground
                                ),
                                startY = 30f
                            )
                        )
                )

                // Quick Back Navigation Button
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassDark)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ContentHighEmphasis
                    )
                }

                // Poster & Quick Rating Overlay at bottom of Hero
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .align(Alignment.BottomStart),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Floating Poster
                    Box(
                        modifier = Modifier
                            .width(105.dp)
                            .height(155.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceVariant)
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                    ) {
                        if (!movie.posterUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(movie.posterUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = movie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = CinematicCrimson,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Rating & Vote Badges
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MovieRatingBadge(
                            rating = movie.voteAverage,
                            style = RatingBadgeStyle.Prominent
                        )

                        if (movie.voteCount > 0) {
                            Text(
                                text = "${NumberFormat.getIntegerInstance().format(movie.voteCount)} TMDB votes",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ContentMediumEmphasis,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------
        // 2. Title, Tagline & Meta Badges
        // -----------------------------------------------------------------
        item(key = "details_title_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ContentHighEmphasis
                    )
                )

                if (!movie.tagline.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${movie.tagline}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            color = CinematicAmber
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Meta Attributes Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaBadge(text = movie.releaseYear)
                    MetaBadge(text = movie.formattedRuntime)
                    MetaBadge(text = movie.originalLanguage.uppercase())
                    MetaBadge(text = movie.status)
                }
            }
        }

        // -----------------------------------------------------------------
        // 3. Favorites & Watchlist Action Buttons (Persistence Integration)
        // -----------------------------------------------------------------
        item(key = "details_actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorite Button
                Button(
                    onClick = onToggleFavorite,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isFavorite) CinematicCrimson else ObsidianSurfaceVariant,
                        contentColor = ContentHighEmphasis
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (uiState.isFavorite) ContentHighEmphasis else CinematicCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isFavorite) "Favorited" else "Favorite",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Watchlist Button
                Button(
                    onClick = onToggleWatchlist,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isInWatchlist) CinematicAmber else ObsidianSurfaceVariant,
                        contentColor = if (uiState.isInWatchlist) ObsidianBackground else ContentHighEmphasis
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isInWatchlist) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Watchlist",
                        tint = if (uiState.isInWatchlist) ObsidianBackground else CinematicAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isInWatchlist) "In Watchlist" else "Watchlist",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // -----------------------------------------------------------------
        // 4. Genres Section
        // -----------------------------------------------------------------
        if (movie.genres.isNotEmpty()) {
            item(key = "details_genres") {
                GenreChipFlowRow(
                    genres = movie.genres.map { it.name },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // -----------------------------------------------------------------
        // 5. Storyline / Overview
        // -----------------------------------------------------------------
        item(key = "details_overview") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Storyline",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ContentHighEmphasis
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (movie.overview.isNotBlank()) movie.overview else "No synopsis provided by TMDB.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = ContentMediumEmphasis,
                        lineHeight = 22.sp,
                        fontSize = 14.sp
                    )
                )
            }
        }

        // -----------------------------------------------------------------
        // 6. Cast & Crew Credits
        // -----------------------------------------------------------------
        item(key = "details_cast") {
            CastSection(castState = uiState.castState)
        }

        // -----------------------------------------------------------------
        // 7. Video Trailers & Clips (Requirement 2)
        // -----------------------------------------------------------------
        item(key = "details_videos") {
            VideosSection(
                videosState = uiState.videosState,
                onOpenVideo = onOpenVideo
            )
        }

        // -----------------------------------------------------------------
        // 8. Streaming Availability / Watch Providers (Requirement 4)
        // -----------------------------------------------------------------
        item(key = "details_watch_providers") {
            StreamingProvidersSection(
                flatrate = uiState.flatrateProviders,
                rent = uiState.rentProviders,
                buy = uiState.buyProviders,
                watchLink = uiState.tmdbWatchPageUrl,
                countryCode = uiState.selectedCountryCode,
                isLoading = uiState.watchProvidersState is UiState.Loading,
                errorMessage = (uiState.watchProvidersState as? UiState.Error)?.message,
                onOpenWatchLink = { tmdbUrl -> StreamingContract.open(context,
                    providerResolver.resolve(null, movie.id, movie.title, movie.releaseYear.toIntOrNull(),
                        uiState.selectedCountryCode, tmdbUrl)) },
                onProviderClick = { provider -> StreamingContract.open(context,
                    providerResolver.resolve(provider, movie.id, movie.title, movie.releaseYear.toIntOrNull(),
                        uiState.selectedCountryCode, uiState.tmdbWatchPageUrl)) },
                onRetry = onRetryProviders,
                onRegionClick = onSelectRegion
            )
        }

        // -----------------------------------------------------------------
        // 9. Financial & Production Stats
        // -----------------------------------------------------------------
        if (movie.budget > 0 || movie.revenue > 0) {
            item(key = "details_financials") {
                FinancialsSection(budget = movie.budget, revenue = movie.revenue)
            }
        }

        item(key = "details_tmdb_attribution") {
            TmdbAttributionFooter()
        }
    }
}

@Composable
private fun MetaBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ObsidianSurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = ContentMediumEmphasis,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun CastSection(castState: UiState<List<CastMember>>) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Top Cast",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ContentHighEmphasis
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        when (castState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CinematicCrimson)
                }
            }
            is UiState.Success -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(items = castState.data, key = { it.id }) { actor ->
                        CastItemCard(actor = actor)
                    }
                }
            }
            is UiState.Empty -> {
                Text(
                    text = castState.message,
                    style = MaterialTheme.typography.bodySmall.copy(color = ContentLowEmphasis),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            is UiState.Error -> {
                Text(
                    text = castState.message,
                    style = MaterialTheme.typography.bodySmall.copy(color = ContentLowEmphasis),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CastItemCard(actor: CastMember) {
    Column(
        modifier = Modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(ObsidianSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!actor.profileUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(actor.profileUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = actor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = ContentMediumEmphasis,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = actor.name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = ContentHighEmphasis,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = actor.character,
            style = MaterialTheme.typography.labelSmall.copy(
                color = ContentLowEmphasis,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Trailers & video metadata section.
 * Handover note: Shraddha will integrate the embedded YouTube Player Core here.
 */
@Composable
private fun VideosSection(
    videosState: UiState<List<VideoTrailer>>,
    onOpenVideo: (VideoTrailer) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trailers & Clips",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                )
            )

            Text(
                text = "In-App Player",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = CinematicCrimson,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (videosState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CinematicCrimson)
                }
            }
            is UiState.Success -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = videosState.data, key = { it.id }) { video ->
                        VideoCard(video = video, onClick = { onOpenVideo(video) })
                    }
                }
            }
            is UiState.Empty -> {
                Text(
                    text = videosState.message,
                    style = MaterialTheme.typography.bodySmall.copy(color = ContentLowEmphasis),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            is UiState.Error -> {
                Text(
                    text = videosState.message,
                    style = MaterialTheme.typography.bodySmall.copy(color = ContentLowEmphasis),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoTrailer, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Type badge
                val badgeColor = when (video.category) {
                    VideoCategory.FULL_MOVIE -> CinematicAmber
                    VideoCategory.TRAILER -> CinematicCrimson
                    VideoCategory.TEASER -> CinematicAmber
                    else -> CinematicViolet
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    val badgeLabel = when {
                        video.isFullLengthMovie && video.isAuthorizedFullLength -> "Full Film (Free)"
                        video.isFullLengthMovie -> "Full Movie"
                        video.isOfficial -> "Official ${video.category.displayName}"
                        else -> video.category.displayName
                    }
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }

                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = CinematicCrimson,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = video.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = ContentHighEmphasis
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Watch on YouTube",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ContentMediumEmphasis,
                    fontSize = 10.sp
                )
            )
        }
    }
}


@Composable
private fun FinancialsSection(budget: Long, revenue: Long) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Financials & Production",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ContentHighEmphasis
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (budget > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurface)
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Budget", style = MaterialTheme.typography.labelSmall.copy(color = ContentMediumEmphasis))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currencyFormat.format(budget), style = MaterialTheme.typography.bodyMedium.copy(color = ContentHighEmphasis, fontWeight = FontWeight.Bold))
                    }
                }
            }
            if (revenue > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurface)
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Revenue", style = MaterialTheme.typography.labelSmall.copy(color = ContentMediumEmphasis))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currencyFormat.format(revenue), style = MaterialTheme.typography.bodyMedium.copy(color = ContentHighEmphasis, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieDetailsSkeleton(onNavigateBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(ObsidianSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = CinematicCrimson,
                    strokeWidth = 3.dp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(24.dp).background(ObsidianSurfaceVariant))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp).background(ObsidianSurfaceVariant))
                Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(ObsidianSurfaceVariant))
            }
        }

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassDark)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ContentHighEmphasis
            )
        }
    }
}
