package com.example.madverse.feature.recommendations.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.designsystem.components.*

@Composable
fun RecommendationsScreen(onMovieClick: (Int) -> Unit, onQuiz: () -> Unit, onCompare: (Int) -> Unit,
    viewModel: RecommendationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Picked for your taste", style = MaterialTheme.typography.headlineSmall)
            Text("Preference match measures fit with your quiz. TMDB rating measures audience votes.")
            Button(onClick = onQuiz) { Text(if (state.needsQuiz) "Take the taste quiz" else "Update preferences") }
        }
        state.actionError?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
        } }
        when (val results = state.results) {
            UiState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is UiState.Error -> item { SectionErrorCard(errorMessage = results.message, onRetry = viewModel::refresh) }
            is UiState.Empty -> item { SectionEmptyCard(message = results.message) }
            is UiState.Success -> items(results.data, key = { it.recommendation.movie.id }) { row ->
                val movie = row.recommendation.movie
                var expanded by rememberSaveable(movie.id) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${row.recommendation.matchPercentage}% preference match", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                    MovieHorizontalCard(movie = movie, onClick = { onMovieClick(movie.id) })
                    Text(row.insight.summary)
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide match details" else "Why this movie?") }
                    if (expanded) {
                        row.insight.reasons.forEach { Text("• $it") }
                        Text(row.insight.detail, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.toggleFavorite(movie) }, enabled = movie.id !in state.busy, modifier = Modifier.weight(1f)) {
                            Text(if (movie.id in state.favorites) "Unfavorite" else "Favorite")
                        }
                        OutlinedButton(onClick = { viewModel.toggleWatchlist(movie) }, enabled = movie.id !in state.busy, modifier = Modifier.weight(1f)) {
                            Text(if (movie.id in state.watchlist) "Remove saved" else "Watchlist")
                        }
                    }
                    TextButton(onClick = { onCompare(movie.id) }) { Text("Compare this movie") }
                    HorizontalDivider()
                }
            }
        }
        item { TmdbAttributionFooter() }
    }
}
