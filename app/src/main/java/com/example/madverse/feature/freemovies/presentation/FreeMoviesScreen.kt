package com.example.madverse.feature.freemovies.presentation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.designsystem.components.*
import com.example.madverse.core.model.Movie

@Composable
fun FreeMoviesScreen(onPlay: (String) -> Unit, viewModel: FreeMoviesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun openEvidence(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(context, "No browser could open this source.", Toast.LENGTH_LONG).show() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Free Movies", style = MaterialTheme.typography.headlineSmall)
            Text("Complete films from verified publisher sources. Each entry states its format and license. YouTube controls regional and embedding availability.")
        }
        when (val result = state) {
            UiState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is UiState.Empty -> item { SectionEmptyCard(message = result.message) }
            is UiState.Error -> item { SectionErrorCard(errorMessage = result.message, onRetry = viewModel::load) }
            is UiState.Success -> items(result.data, key = { it.catalogueId }) { film ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Shared presentation card only; these are catalogue IDs, never TMDB movie IDs.
                    MovieHorizontalCard(movie = Movie(0, film.title, film.description, film.thumbnailUrl, null,
                        null, 0.0, 0, emptyList(), 0.0), onClick = { onPlay(film.catalogueId) })
                    Text(film.format.label, style = MaterialTheme.typography.titleMedium)
                    Text(film.publisher)
                    Text(film.attribution, style = MaterialTheme.typography.bodySmall)
                    Text("Rights/source checked ${film.verifiedOn}. ${film.regionNotes}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onPlay(film.catalogueId) }) { Text("Watch in app") }
                    TextButton(onClick = { openEvidence(film.sourceUrl) }) { Text("Publisher's film page") }
                    TextButton(onClick = { openEvidence(film.rightsUrl) }) { Text("Rights evidence") }
                    TextButton(onClick = { openEvidence(film.licenseUrl) }) { Text("License terms") }
                    HorizontalDivider()
                }
            }
        }
        item { Text("Catalogue attribution belongs to each film's publisher. Catalogue films are separate from TMDB trailers.", style = MaterialTheme.typography.bodySmall) }
    }
}
