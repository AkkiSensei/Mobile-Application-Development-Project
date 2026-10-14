package com.example.madverse.feature.player.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.madverse.feature.player.presentation.components.YouTubePlayerView
import com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer
import kotlinx.coroutines.delay

@Composable
fun YouTubePlayerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubePlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    // Timeout only while foregrounded. The player stays mounted during initialization.
    LaunchedEffect(state.attempt, state.isLoading, owner) {
        if (state.isLoading) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(30_000)
            viewModel.initializationTimedOut(state.attempt)
        }
    }
    val openExternal = {
        val url = state.webWatchUrl
        if (YouTubeUrlSanitizer.isPermittedUrl(url)) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube"))
            } catch (_: Exception) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: Exception) { viewModel.externalFailed() }
            }
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.movieTitle.ifBlank { "YouTube video" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        if (state.canEmbed) {
            val attempt = state.attempt
            YouTubePlayerView(videoId = state.videoId, attempt = attempt,
                onInitializing = { viewModel.initializing(attempt) },
                onReady = { viewModel.onReady(attempt) },
                onPlayingChange = { viewModel.setPlaying(attempt, it) },
                onError = { viewModel.fail(attempt, it) })
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Connecting to YouTube…")
            }
            if (state.isReady) Text(if (state.isPlaying) "Playing" else "Player ready. Tap the YouTube play control to begin.")
            Text(state.contentLabel)
            state.attribution?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.retryable) Button(onClick = viewModel::retry) { Text("Retry player") }
            if (state.webWatchUrl.isNotBlank()) OutlinedButton(onClick = openExternal) { Text("Open in YouTube") }
            if (state.webWatchUrl.isNotBlank()) TextButton(onClick = {
                try {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.webWatchUrl)
                    }, "Share video"))
                } catch (_: Exception) { viewModel.externalFailed("No app could share this video.") }
            }) { Text("Share video") }
            state.externalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onNavigateBack) { Text("Back") }
            Text("Playback and regional availability are controlled by YouTube and the publisher.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
