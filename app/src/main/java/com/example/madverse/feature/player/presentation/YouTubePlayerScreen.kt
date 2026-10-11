package com.example.madverse.feature.player.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.madverse.core.designsystem.theme.ObsidianBackground

/**
 * In-app embedded YouTube player screen destination.
 * Fleshed out in Step 4 with playback controls, lifecycle observer, and fallback actions.
 */
@Composable
fun YouTubePlayerScreen(
    videoId: String,
    movieTitle: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    )
}
