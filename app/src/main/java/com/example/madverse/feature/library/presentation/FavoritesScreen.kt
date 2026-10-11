package com.example.madverse.feature.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.madverse.core.designsystem.components.MovieHorizontalCard
import com.example.madverse.core.designsystem.components.MovieHorizontalSkeleton
import com.example.madverse.core.designsystem.components.SectionErrorCard
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.core.model.Movie

/**
 * Stateful entry point for the Favorites screen.
 */
@Composable
fun FavoritesScreen(
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()

    FavoritesContent(
        uiState = uiState,
        selectedSort = sortOption,
        onSortSelected = viewModel::setSortOption,
        onMovieClick = onMovieClick,
        onRemoveMovie = viewModel::removeFromFavorites,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/**
 * Stateless UI rendering Favorites state, sort chips, offline data, and removal actions.
 */
@Composable
fun FavoritesContent(
    uiState: UiState<List<Movie>>,
    selectedSort: LibrarySortOption,
    onSortSelected: (LibrarySortOption) -> Unit,
    onMovieClick: (Int) -> Unit,
    onRemoveMovie: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        when (uiState) {
            is UiState.Loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(5) {
                        MovieHorizontalSkeleton()
                    }
                }
            }

            is UiState.Empty -> {
                FavoritesEmptyState()
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SectionErrorCard(
                        errorMessage = uiState.message,
                        onRetry = onRetry,
                        isOffline = uiState.isOffline
                    )
                }
            }

            is UiState.Success -> {
                val movies = uiState.data
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header with count badge and offline availability
                    FavoritesHeader(
                        movieCount = movies.size,
                        selectedSort = selectedSort,
                        onSortSelected = onSortSelected
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = movies,
                            key = { it.id }
                        ) { movie ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                MovieHorizontalCard(
                                    movie = movie,
                                    onClick = { onMovieClick(movie.id) }
                                )

                                IconButton(
                                    onClick = { onRemoveMovie(movie.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianSurface.copy(alpha = 0.9f))
                                        .border(1.dp, ObsidianCardBorder, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove from Favorites",
                                        tint = CinematicCrimson,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesHeader(
    movieCount: Int,
    selectedSort: LibrarySortOption,
    onSortSelected: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = CinematicCrimson,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "$movieCount ${if (movieCount == 1) "Movie" else "Movies"}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = ContentHighEmphasis,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.OfflinePin,
                    contentDescription = null,
                    tint = CinematicAmber,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Offline Cached",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CinematicAmber,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibrarySortOption.entries.toTypedArray()) { option ->
                val isSelected = option == selectedSort
                FilterChip(
                    selected = isSelected,
                    onClick = { onSortSelected(option) },
                    label = {
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = ObsidianSurface,
                        labelColor = ContentMediumEmphasis,
                        selectedContainerColor = CinematicCrimson.copy(alpha = 0.25f),
                        selectedLabelColor = ContentHighEmphasis
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = ObsidianCardBorder,
                        selectedBorderColor = CinematicCrimson,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp,
                        enabled = true,
                        selected = isSelected
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

@Composable
fun FavoritesEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(ObsidianSurfaceVariant)
                .border(1.dp, ObsidianCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = CinematicCrimson,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No Favorites Yet",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = ContentHighEmphasis
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Explore movies and tap the heart icon to save them to your favorites for instant offline access.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = ContentMediumEmphasis
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.OfflinePin,
                contentDescription = null,
                tint = CinematicAmber,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Offline Storage Active",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = CinematicAmber,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
