package com.example.madverse.feature.recommendations.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.designsystem.components.MovieHorizontalCard
import com.example.madverse.core.designsystem.components.SectionEmptyCard
import com.example.madverse.core.designsystem.components.SectionErrorCard
import com.example.madverse.core.designsystem.components.TmdbAttributionFooter
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant

@Composable
fun RecommendationsScreen(
    onMovieClick: (Int) -> Unit,
    onQuiz: () -> Unit,
    onCompare: (Int) -> Unit,
    viewModel: RecommendationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CinematicCrimson.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = CinematicAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Picked For Your Taste",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ContentHighEmphasis
                                    )
                                )
                                Text(
                                    text = "Match measures alignment with your quiz profile.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ContentMediumEmphasis
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = onQuiz,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CinematicCrimson,
                                contentColor = ContentHighEmphasis
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (state.needsQuiz) "Take the Taste Quiz" else "Update Preferences",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Action error banner
            state.actionError?.let { message ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::dismissError,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { role = Role.Button }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Results states
            when (val results = state.results) {
                UiState.Loading -> item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = CinematicAmber,
                        trackColor = ObsidianSurfaceVariant
                    )
                }
                is UiState.Error -> item {
                    SectionErrorCard(
                        errorMessage = results.message,
                        onRetry = viewModel::refresh
                    )
                }
                is UiState.Empty -> item {
                    SectionEmptyCard(message = results.message)
                }
                is UiState.Success -> items(
                    items = results.data,
                    key = { it.recommendation.movie.id }
                ) { row ->
                    val movie = row.recommendation.movie
                    var expanded by rememberSaveable(movie.id) { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Match badge and score header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (row.recommendation.matchPercentage >= 75)
                                                CinematicCrimson.copy(alpha = 0.25f)
                                            else
                                                CinematicAmber.copy(alpha = 0.2f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (row.recommendation.matchPercentage >= 75)
                                                CinematicCrimson.copy(alpha = 0.6f)
                                            else
                                                CinematicAmber.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${row.recommendation.matchPercentage}% Taste Match",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (row.recommendation.matchPercentage >= 75)
                                                CinematicAmber
                                            else
                                                ContentHighEmphasis
                                        )
                                    )
                                }

                                TextButton(
                                    onClick = { onCompare(movie.id) },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Compare ${movie.title} with another movie"
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                        contentDescription = null,
                                        tint = CinematicAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Compare",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = CinematicAmber,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            // Movie horizontal card
                            MovieHorizontalCard(
                                movie = movie,
                                onClick = { onMovieClick(movie.id) }
                            )

                            // Insight summary
                            Text(
                                text = row.insight.summary,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = ContentMediumEmphasis
                                )
                            )

                            // Expandable "Why this movie?" accordion
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceVariant.copy(alpha = 0.4f)),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianCardBorder.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics { role = Role.Button },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { expanded = !expanded },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (expanded) "Hide Match Breakdown" else "Why This Recommendation?",
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        color = CinematicAmber,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                                Icon(
                                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = if (expanded) "Collapse match breakdown" else "Expand match breakdown",
                                                    tint = CinematicAmber
                                                )
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(top = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            row.insight.reasons.forEach { reason ->
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Text(
                                                        text = "•",
                                                        color = CinematicAmber,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = reason,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = ContentHighEmphasis
                                                        )
                                                    )
                                                }
                                            }
                                            if (row.insight.detail.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = row.insight.detail,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = ContentMediumEmphasis
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Favorite and Watchlist actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val isFavorite = movie.id in state.favorites
                                val isWatchlist = movie.id in state.watchlist
                                val isBusy = movie.id in state.busy

                                OutlinedButton(
                                    onClick = { viewModel.toggleFavorite(movie) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = if (isFavorite) "Remove ${movie.title} from favorites" else "Add ${movie.title} to favorites"
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isFavorite) CinematicCrimson else ContentHighEmphasis,
                                        disabledContentColor = ContentLowEmphasis
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isFavorite) CinematicCrimson else ObsidianCardBorder
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isFavorite) CinematicCrimson else ContentMediumEmphasis,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isFavorite) "Favorited" else "Favorite",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.toggleWatchlist(movie) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = if (isWatchlist) "Remove ${movie.title} from watchlist" else "Add ${movie.title} to watchlist"
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isWatchlist) CinematicAmber else ContentHighEmphasis,
                                        disabledContentColor = ContentLowEmphasis
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isWatchlist) CinematicAmber else ObsidianCardBorder
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        tint = if (isWatchlist) CinematicAmber else ContentMediumEmphasis,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isWatchlist) "Saved" else "Watchlist",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { TmdbAttributionFooter() }
        }
    }
}
