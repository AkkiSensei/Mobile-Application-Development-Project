package com.example.madverse.feature.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.madverse.core.designsystem.components.LibraryActionError
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianSurface

/**
 * Personal Movie Library Screen.
 * Provides unified tabbed navigation between Watchlist and Favorites,
 * backed by reactive Room persistent flows with sorting and offline accessibility.
 */
@Composable
fun LibraryScreen(
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    LibraryActionError(actionError, viewModel::dismissActionError)

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val favoritesState by viewModel.favoritesState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        val selectedIndex = if (selectedTab == LibraryTab.WATCHLIST) 0 else 1

        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = ObsidianSurface,
            contentColor = ContentHighEmphasis,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == LibraryTab.WATCHLIST,
                onClick = { viewModel.selectTab(LibraryTab.WATCHLIST) },
                text = {
                    Text(
                        text = "Watchlist",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (selectedTab == LibraryTab.WATCHLIST) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                },
                icon = {
                    Icon(
                        imageVector = if (selectedTab == LibraryTab.WATCHLIST) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = if (selectedTab == LibraryTab.WATCHLIST) CinematicAmber else ContentMediumEmphasis
                    )
                },
                selectedContentColor = CinematicAmber,
                unselectedContentColor = ContentMediumEmphasis
            )

            Tab(
                selected = selectedTab == LibraryTab.FAVORITES,
                onClick = { viewModel.selectTab(LibraryTab.FAVORITES) },
                text = {
                    Text(
                        text = "Favorites",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (selectedTab == LibraryTab.FAVORITES) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                },
                icon = {
                    Icon(
                        imageVector = if (selectedTab == LibraryTab.FAVORITES) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (selectedTab == LibraryTab.FAVORITES) CinematicCrimson else ContentMediumEmphasis
                    )
                },
                selectedContentColor = CinematicCrimson,
                unselectedContentColor = ContentMediumEmphasis
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                LibraryTab.WATCHLIST -> {
                    WatchlistContent(
                        uiState = watchlistState,
                        selectedSort = sortOption,
                        onSortSelected = viewModel::setSortOption,
                        onMovieClick = onMovieClick,
                        onRemoveMovie = viewModel::removeFromWatchlist,
                        onRetry = viewModel::retryWatchlist
                    )
                }

                LibraryTab.FAVORITES -> {
                    FavoritesContent(
                        uiState = favoritesState,
                        selectedSort = sortOption,
                        onSortSelected = viewModel::setSortOption,
                        onMovieClick = onMovieClick,
                        onRemoveMovie = viewModel::removeFromFavorites,
                        onRetry = viewModel::retryFavorites
                    )
                }
            }
        }
    }
}
