package com.example.madverse.feature.genres.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.designsystem.components.MovieHorizontalCard
import com.example.madverse.core.designsystem.components.SectionEmptyCard
import com.example.madverse.core.designsystem.components.SectionErrorCard
import com.example.madverse.core.designsystem.components.TmdbAttributionFooter
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant

@Composable
fun GenreExplorerScreen(
    onMovieClick: (Int) -> Unit,
    viewModel: GenreExplorerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sortedMovies = remember(state.movies, state.sort) { state.sortedMovies }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Screen Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = CinematicAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Explore by Genre",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = ContentHighEmphasis
                        )
                    )
                }
            }

            // Genre Selector Carousel
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Select Genre",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ContentMediumEmphasis,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.genreId == null,
                                onClick = { viewModel.selectGenre(null) },
                                label = { Text("All genres") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CinematicCrimson,
                                    selectedLabelColor = ContentHighEmphasis,
                                    containerColor = ObsidianSurface,
                                    labelColor = ContentMediumEmphasis
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (state.genreId == null) CinematicCrimson else ObsidianCardBorder
                                )
                            )
                        }
                        items(state.genres, key = { it.id }) { genre ->
                            val isSelected = state.genreId == genre.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectGenre(genre.id) },
                                label = { Text(genre.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CinematicCrimson,
                                    selectedLabelColor = ContentHighEmphasis,
                                    containerColor = ObsidianSurface,
                                    labelColor = ContentMediumEmphasis
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) CinematicCrimson else ObsidianCardBorder
                                )
                            )
                        }
                    }
                }
            }

            if (state.genresLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = CinematicAmber,
                        trackColor = ObsidianSurfaceVariant
                    )
                }
            }

            state.genreError?.let { message ->
                item {
                    SectionErrorCard(
                        errorMessage = message,
                        onRetry = viewModel::loadGenres
                    )
                }
            }

            // Filter controls card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = CinematicAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Refine Release Years",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = ContentHighEmphasis
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.startYear,
                                onValueChange = { viewModel.editYears(it, state.endYear) },
                                label = { Text("From year") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CinematicAmber,
                                    unfocusedBorderColor = ObsidianCardBorder,
                                    focusedLabelColor = CinematicAmber,
                                    unfocusedLabelColor = ContentMediumEmphasis,
                                    focusedTextColor = ContentHighEmphasis,
                                    unfocusedTextColor = ContentHighEmphasis
                                )
                            )
                            OutlinedTextField(
                                value = state.endYear,
                                onValueChange = { viewModel.editYears(state.startYear, it) },
                                label = { Text("To year") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CinematicAmber,
                                    unfocusedBorderColor = ObsidianCardBorder,
                                    focusedLabelColor = CinematicAmber,
                                    unfocusedLabelColor = ContentMediumEmphasis,
                                    focusedTextColor = ContentHighEmphasis,
                                    unfocusedTextColor = ContentHighEmphasis
                                )
                            )
                        }

                        // Rating Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = CinematicAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Minimum Rating",
                                        style = MaterialTheme.typography.bodySmall.copy(color = ContentMediumEmphasis)
                                    )
                                }
                                Text(
                                    text = "${state.minRating} / 10",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CinematicAmber
                                    )
                                )
                            }
                            Slider(
                                value = state.minRating.toFloat(),
                                onValueChange = { viewModel.setRating(it.toInt()) },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = CinematicAmber,
                                    activeTrackColor = CinematicAmber,
                                    inactiveTrackColor = ObsidianSurfaceVariant
                                )
                            )
                        }

                        state.filterError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = viewModel::applyFilters,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Apply release year and rating filters"
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CinematicCrimson,
                                contentColor = ContentHighEmphasis
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Apply Filters",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Sorting chips inside filter card
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Sort Results",
                                style = MaterialTheme.typography.labelSmall.copy(color = ContentMediumEmphasis)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(MovieSort.entries) { sort ->
                                    val isSortSelected = state.sort == sort
                                    FilterChip(
                                        selected = isSortSelected,
                                        onClick = { viewModel.setSort(sort) },
                                        label = { Text(sort.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CinematicAmber.copy(alpha = 0.25f),
                                            selectedLabelColor = CinematicAmber,
                                            containerColor = ObsidianSurfaceVariant.copy(alpha = 0.4f),
                                            labelColor = ContentMediumEmphasis
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSortSelected) CinematicAmber else ObsidianCardBorder
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Movies list
            items(sortedMovies, key = { it.id }) { movie ->
                MovieHorizontalCard(movie, onClick = { onMovieClick(movie.id) })
            }

            if (state.loading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = CinematicAmber,
                        trackColor = ObsidianSurfaceVariant
                    )
                }
            }

            state.error?.let { message ->
                item {
                    SectionErrorCard(
                        errorMessage = message,
                        onRetry = viewModel::loadMore
                    )
                }
            }

            if (!state.loading && state.error == null && state.movies.isEmpty()) {
                item {
                    SectionEmptyCard(message = "No movies match these filters. Try a wider year range or lower rating.")
                }
            }

            if (!state.loading && state.error == null && state.hasMore) {
                item {
                    OutlinedButton(
                        onClick = viewModel::loadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CinematicAmber
                        ),
                        border = BorderStroke(1.dp, CinematicAmber.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Load More Movies",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item { TmdbAttributionFooter() }
        }
    }
}
