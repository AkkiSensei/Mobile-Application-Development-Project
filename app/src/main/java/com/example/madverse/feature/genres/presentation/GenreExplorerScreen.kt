package com.example.madverse.feature.genres.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.designsystem.components.*

@Composable
fun GenreExplorerScreen(onMovieClick: (Int) -> Unit, viewModel: GenreExplorerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sortedMovies = remember(state.movies, state.sort) { state.sortedMovies }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Explore by genre", style = MaterialTheme.typography.headlineSmall) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = state.genreId == null, onClick = { viewModel.selectGenre(null) }, label = { Text("All genres") }) }
                items(state.genres, key = { it.id }) { genre ->
                    FilterChip(selected = state.genreId == genre.id, onClick = { viewModel.selectGenre(genre.id) }, label = { Text(genre.name) })
                }
            }
        }
        if (state.genresLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.genreError?.let { message -> item { SectionErrorCard(errorMessage = message, onRetry = viewModel::loadGenres) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.startYear, onValueChange = { viewModel.editYears(it, state.endYear) },
                    label = { Text("From year") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = state.endYear, onValueChange = { viewModel.editYears(state.startYear, it) },
                    label = { Text("To year") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        item {
            Text("Minimum rating: ${state.minRating}/10")
            Slider(value = state.minRating.toFloat(), onValueChange = { viewModel.setRating(it.toInt()) }, valueRange = 0f..10f, steps = 9)
            state.filterError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::applyFilters) { Text("Apply filters") }
            Text("Sort loaded results. More movies are fetched in popularity order.", style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MovieSort.entries) { sort -> FilterChip(selected = state.sort == sort, onClick = { viewModel.setSort(sort) }, label = { Text(sort.label) }) }
            }
        }
        items(sortedMovies, key = { it.id }) { movie -> MovieHorizontalCard(movie, onClick = { onMovieClick(movie.id) }) }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item { SectionErrorCard(errorMessage = message, onRetry = viewModel::loadMore) } }
        if (!state.loading && state.error == null && state.movies.isEmpty()) item { SectionEmptyCard(message = "No movies match these filters. Try a wider year range or lower rating.") }
        if (!state.loading && state.error == null && state.hasMore) item { OutlinedButton(onClick = viewModel::loadMore) { Text("Load more") } }
        item { TmdbAttributionFooter() }
    }
}
