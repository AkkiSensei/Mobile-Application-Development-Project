package com.example.madverse.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ErrorRed
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.model.Movie

/**
 * Standard vertical movie poster card used across Discovery carousels and grids.
 * Designed for immediate reuse across Search, Home, Library, and Recommendations.
 *
 * Integrates [MovieRatingBadge] for standard rating overlays and provides
 * accessible descriptions for screen readers.
 */
@Composable
fun MoviePosterCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 150.dp,
    posterHeight: Dp = 225.dp
) {
    val accessibleDescription = "${movie.title}, release year ${movie.releaseYear}${if (movie.voteAverage > 0) ", rated ${movie.formattedRating} out of 10" else ""}"

    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibleDescription
            }
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
    ) {
        // Poster Box with Rating Badge overlay
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(posterHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurfaceVariant)
                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
        ) {
            if (movie.posterUrl.isNullOrBlank()) {
                MoviePosterFallback(title = movie.title)
            } else {
                SubcomposeAsyncImage(
                    model = movie.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ObsidianSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = CinematicCrimson,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        MoviePosterFallback(title = movie.title)
                    }
                )
            }

            // Rating Badge overlay in top-right corner
            if (movie.voteAverage > 0.0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    MovieRatingBadge(
                        rating = movie.voteAverage,
                        style = RatingBadgeStyle.OverlayBadge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = movie.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = ContentHighEmphasis
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Metadata: Release Year & Popularity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = movie.releaseYear,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ContentMediumEmphasis,
                    fontSize = 12.sp
                )
            )

            if (movie.voteCount > 0) {
                Text(
                    text = "${movie.voteCount} votes",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ContentLowEmphasis,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

/**
 * Cinematic Hero Featured Movie Banner used at the top of the Discover screen.
 * Displays backdrop image, title, rating badge, release year, and overview snippet.
 */
@Composable
fun HeroFeaturedMovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroImageUrl = movie.backdropUrl ?: movie.posterUrl
    val accessibleDescription = "Featured spotlight movie: ${movie.title}, release year ${movie.releaseYear}${if (movie.voteAverage > 0) ", rated ${movie.formattedRating}" else ""}"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibleDescription
            }
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
    ) {
        if (!heroImageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = heroImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = CinematicCrimson
                        )
                    }
                },
                error = {
                    MoviePosterFallback(title = movie.title)
                }
            )
        } else {
            MoviePosterFallback(title = movie.title)
        }

        // Gradient Scrim for readable high-contrast text overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ObsidianBackground.copy(alpha = 0.6f),
                            ObsidianBackground.copy(alpha = 0.95f)
                        ),
                        startY = 40f
                    )
                )
        )

        // Hero Content overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            // Spotlight pill badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CinematicCrimson)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "FEATURED SPOTLIGHT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = ContentHighEmphasis,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis,
                    fontSize = 22.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Rating & Release Year metadata
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (movie.voteAverage > 0.0) {
                    MovieRatingBadge(
                        rating = movie.voteAverage,
                        style = RatingBadgeStyle.Inline
                    )
                    Text(
                        text = "•",
                        color = ContentMediumEmphasis,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = movie.releaseYear,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = CinematicAmber,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            if (movie.overview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = movie.overview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ContentMediumEmphasis,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Horizontal movie card designed for lists, search results, recommendations, and library views.
 * Ready for reuse across all MADVerse features with optional trailing action slot.
 */
@Composable
fun MovieHorizontalCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val accessibleDescription = "${movie.title}, ${movie.releaseYear}${if (movie.voteAverage > 0) ", rated ${movie.formattedRating} out of 10" else ""}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibleDescription
            }
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Poster
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(115.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianSurfaceVariant)
        ) {
            if (movie.posterUrl.isNullOrBlank()) {
                MoviePosterFallback(title = movie.title)
            } else {
                SubcomposeAsyncImage(
                    model = movie.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { MoviePosterFallback(title = movie.title) }
                )
            }
        }

        // Details Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (movie.voteAverage > 0.0) {
                    MovieRatingBadge(
                        rating = movie.voteAverage,
                        style = RatingBadgeStyle.Inline
                    )
                }

                Text(
                    text = movie.releaseYear,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ContentMediumEmphasis
                    )
                )
            }

            if (movie.overview.isNotBlank()) {
                Text(
                    text = movie.overview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ContentLowEmphasis,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Optional trailing action slot (e.g. favorite/watchlist toggle or delete button)
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/**
 * Reusable Section Header for movie category carousels with accessible "See All" touch target.
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis,
                    fontSize = 18.sp
                )
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ContentMediumEmphasis,
                        fontSize = 12.sp
                    )
                )
            }
        }

        if (onSeeAllClick != null) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "See all $title",
                        onClick = onSeeAllClick
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = CinematicAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

/**
 * Shimmer / Skeleton card matching [MoviePosterCard] dimensions during loading.
 */
@Composable
fun MoviePosterSkeleton(
    modifier: Modifier = Modifier,
    cardWidth: Dp = 150.dp,
    posterHeight: Dp = 225.dp
) {
    Column(
        modifier = modifier.width(cardWidth)
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(posterHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurfaceVariant)
                .border(1.dp, ObsidianCardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = CinematicCrimson.copy(alpha = 0.5f),
                strokeWidth = 2.dp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .width(cardWidth * 0.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ObsidianSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .width(cardWidth * 0.4f)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ObsidianSurfaceVariant.copy(alpha = 0.6f))
        )
    }
}

/**
 * Hero skeleton placeholder during initial feed load.
 */
@Composable
fun HeroFeaturedMovieSkeleton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = CinematicCrimson,
            strokeWidth = 3.dp
        )
    }
}

/**
 * Skeleton loading card matching [MovieHorizontalCard] dimensions for search and library lists.
 */
@Composable
fun MovieHorizontalSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(115.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = CinematicCrimson.copy(alpha = 0.5f),
                strokeWidth = 2.dp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianSurfaceVariant)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianSurfaceVariant.copy(alpha = 0.6f))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

/**
 * Loading skeleton placeholder for genre chips.
 */
@Composable
fun GenreChipSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    height: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceVariant)
            .border(1.dp, ObsidianCardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    )
}

/**
 * In-line error message with retry button for an individual discovery or screen section.
 */
@Composable
fun SectionErrorCard(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isOffline: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isOffline) Icons.Filled.WifiOff else Icons.Filled.Refresh,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ContentMediumEmphasis,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinematicCrimson,
                    contentColor = ContentHighEmphasis
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry", fontSize = 12.sp)
            }
        }
    }
}

/**
 * In-line empty state for a discovery or list section when no movies match.
 */
@Composable
fun SectionEmptyCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = ContentMediumEmphasis,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ContentMediumEmphasis
                )
            )
        }
    }
}

/**
 * Fallback layout shown when a movie poster image URL is missing or fails to load.
 */
@Composable
private fun MoviePosterFallback(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSurfaceVariant)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = CinematicCrimson.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ContentMediumEmphasis,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
