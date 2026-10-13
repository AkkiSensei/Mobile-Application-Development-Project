package com.example.madverse.feature.comparison.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.designsystem.components.*

@Composable
fun ComparisonScreen(onMovieClick: (Int) -> Unit, viewModel: ComparisonViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Compare movies", style = MaterialTheme.typography.headlineSmall)
            Text("Choose two or three movies. Swipe the comparison sideways to see every column.")
        }
        if (state.entries.isNotEmpty()) item {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.entries.forEach { entry -> Column(Modifier.width(156.dp)) {
                        val movie = entry.movie ?: entry.details?.toMovie()
                        if (movie != null) MoviePosterCard(movie, onClick = { onMovieClick(entry.id) }, cardWidth = 156.dp)
                        else Text("Movie ${entry.id}")
                        TextButton(onClick = { viewModel.remove(entry.id) }) { Text("Remove") }
                        TextButton(onClick = { viewModel.replace(entry.id) }) { Text("Replace") }
                        if (entry.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (entry.error != null) {
                            Text(entry.error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.loadDetails(entry.id) }) { Text("Retry details") }
                        }
                    } }
                }
                ComparisonRow("Release", state.entries) { it.details?.releaseDate ?: it.movie?.releaseDate }
                ComparisonRow("TMDB rating", state.entries) { entry ->
                    val votes = entry.details?.voteCount ?: entry.movie?.voteCount ?: 0
                    if (votes > 0) "${entry.details?.formattedRating ?: entry.movie?.formattedRating}/10" else null
                }
                ComparisonRow("Vote count", state.entries) { (it.details?.voteCount ?: it.movie?.voteCount)?.toString() }
                ComparisonRow("Genres", state.entries) { it.details?.genres?.map { genre -> genre.name }?.joinToString()?.takeIf(String::isNotBlank) }
                ComparisonRow("Runtime", state.entries) { (it.details?.runtimeMinutes ?: it.movie?.runtimeMinutes)?.takeIf { minutes -> minutes > 0 }?.let { minutes -> "$minutes min" } }
                ComparisonRow("Original language", state.entries) { it.details?.originalLanguage ?: it.movie?.originalLanguage }
                // MovieDetails has no popularity field; never display toMovie()'s synthetic zero.
                ComparisonRow("Popularity", state.entries) { it.movie?.popularity?.toString() }
            }
        }
        item {
            Text("${state.entries.size}/3 selected")
            if (state.replacingId != null) {
                Text("Choose a search result to replace the selected movie.")
                TextButton(onClick = { viewModel.replace(null) }) { Text("Cancel replacement") }
            }
            OutlinedTextField(value = state.query, onValueChange = viewModel::search, label = { Text("Search movies to compare") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.searchError?.let { message -> item { SectionErrorCard(errorMessage = message, onRetry = { viewModel.search(state.query) }) } }
        if (state.query.isNotBlank() && !state.searching && state.searchError == null && state.results.isEmpty()) item { SectionEmptyCard(message = "No movies found. Try another title.") }
        items(state.results, key = { it.id }) { movie ->
            Column {
                MovieHorizontalCard(movie, onClick = { onMovieClick(movie.id) })
                val selected = state.entries.any { it.id == movie.id }
                OutlinedButton(onClick = { viewModel.select(movie) }, enabled = !selected && (state.entries.size < 3 || state.replacingId != null)) {
                    Text(if (selected) "Selected" else if (state.replacingId != null) "Use as replacement" else "Add to comparison")
                }
            }
        }
        item { TmdbAttributionFooter() }
    }
}

@Composable
private fun ComparisonRow(label: String, entries: List<ComparisonEntry>, value: (ComparisonEntry) -> String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        entries.forEach { entry -> Column(Modifier.width(156.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value(entry)?.takeIf(String::isNotBlank) ?: "Not available")
        } }
    }
}
