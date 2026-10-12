package com.example.madverse.feature.search.presentation

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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.designsystem.components.GenreChip
import com.example.madverse.core.designsystem.components.MADVerseEmptyState
import com.example.madverse.core.designsystem.components.MovieHorizontalCard
import com.example.madverse.core.designsystem.components.MovieHorizontalSkeleton
import com.example.madverse.core.designsystem.components.SectionErrorCard
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant

/**
 * Search Screen for MADVerse.
 *
 * Implements R3: Movie Search with:
 * - Search input with clear-query action
 * - Real-time debounced query searching
 * - Instant submission on IME Search action or suggested chip tap
 * - Loading skeletons, empty states, and offline-aware error states
 * - Reusable movie card presentation via [MovieHorizontalCard]
 * - Direct navigation to Movie Details via TMDB movie IDs
 */
@Composable
fun SearchScreen(
    onMovieClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Search Input Bar
        SearchBarInput(
            query = uiState.query,
            onQueryChanged = { viewModel.onQueryChanged(it) },
            onClearQuery = { viewModel.onClearQuery() },
            onSearchSubmitted = {
                keyboardController?.hide()
                focusManager.clearFocus()
                viewModel.onSearchSubmitted()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Body Content based on state
        when {
            uiState.isIdle -> {
                SearchIdleContent(
                    onSelectSuggestion = { suggestion ->
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        viewModel.onSearchSubmitted(suggestion)
                    }
                )
            }
            uiState.searchState is UiState.Loading -> {
                SearchLoadingContent()
            }
            uiState.searchState is UiState.Error -> {
                val errorState = uiState.searchState as UiState.Error
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    SectionErrorCard(
                        errorMessage = errorState.message,
                        onRetry = { viewModel.retry() },
                        isOffline = errorState.isOffline
                    )
                }
            }
            uiState.searchState is UiState.Empty -> {
                val emptyState = uiState.searchState as UiState.Empty
                SearchEmptyContent(
                    message = emptyState.message,
                    query = uiState.activeQuery.ifBlank { uiState.query }
                )
            }
            uiState.searchState is UiState.Success -> {
                val movies = (uiState.searchState as UiState.Success).data
                SearchResultsContent(
                    movies = movies,
                    activeQuery = uiState.activeQuery,
                    onMovieClick = onMovieClick
                )
            }
        }
    }
}

/**
 * Text field component styled with obsidian cinematic aesthetics.
 */
@Composable
private fun SearchBarInput(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearchSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Search movies by title, e.g. Inception...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = ContentLowEmphasis,
                    fontSize = 14.sp
                )
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = if (query.isNotBlank()) CinematicCrimson else ContentMediumEmphasis,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = ContentMediumEmphasis,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ObsidianSurfaceVariant,
            unfocusedContainerColor = ObsidianSurfaceVariant,
            focusedBorderColor = CinematicCrimson,
            unfocusedBorderColor = ObsidianCardBorder,
            focusedTextColor = ContentHighEmphasis,
            unfocusedTextColor = ContentHighEmphasis,
            cursorColor = CinematicCrimson
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSearchSubmitted() }
        )
    )
}

/**
 * Initial idle state when the search bar is empty, displaying suggestions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchIdleContent(
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "Inception",
        "Interstellar",
        "The Dark Knight",
        "Dune",
        "Spider-Man",
        "Oppenheimer",
        "Avatar",
        "Fight Club"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ObsidianSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = CinematicAmber,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Discover Any Movie",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = ContentHighEmphasis
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Search thousands of movies across TMDB by title, find release dates, scores, and overviews.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = ContentMediumEmphasis,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Popular Searches",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = ContentHighEmphasis,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { title ->
                GenreChip(
                    name = title,
                    textColor = ContentMediumEmphasis,
                    onClick = { onSelectSuggestion(title) }
                )
            }
        }
    }
}

/**
 * Loading state during active search queries using skeleton placeholders.
 */
@Composable
private fun SearchLoadingContent(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        userScrollEnabled = false
    ) {
        items(count = 5) {
            MovieHorizontalSkeleton()
        }
    }
}

/**
 * Empty search results presentation.
 */
@Composable
private fun SearchEmptyContent(
    message: String,
    query: String,
    modifier: Modifier = Modifier
) {
    MADVerseEmptyState(
        icon = Icons.Outlined.SearchOff,
        title = "No Results Found",
        message = if (query.isNotBlank()) "We couldn't find any movies matching \"$query\". Check for spelling errors or try a different title." else message,
        iconTint = ContentMediumEmphasis,
        modifier = modifier
    )
}

/**
 * Search results list using the reusable [MovieHorizontalCard] components.
 */
@Composable
private fun SearchResultsContent(
    movies: List<com.example.madverse.core.model.Movie>,
    activeQuery: String,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
    ) {
        item(key = "search_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Results for \"$activeQuery\"",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = ContentMediumEmphasis,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )

                Text(
                    text = "${movies.size} found",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ContentLowEmphasis,
                        fontSize = 12.sp
                    )
                )
            }
        }

        items(
            items = movies,
            key = { movie -> movie.id }
        ) { movie ->
            MovieHorizontalCard(
                movie = movie,
                onClick = { onMovieClick(movie.id) }
            )
        }
    }
}
