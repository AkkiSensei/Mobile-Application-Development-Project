package com.example.madverse.feature.player.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.madverse.core.designsystem.theme.CinematicAmber
import com.example.madverse.core.designsystem.theme.CinematicCrimson
import com.example.madverse.core.designsystem.theme.ContentHighEmphasis
import com.example.madverse.core.designsystem.theme.ContentLowEmphasis
import com.example.madverse.core.designsystem.theme.ContentMediumEmphasis
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.core.designsystem.theme.ObsidianCardBorder
import com.example.madverse.core.designsystem.theme.ObsidianSurface
import com.example.madverse.core.designsystem.theme.ObsidianSurfaceVariant
import com.example.madverse.feature.player.presentation.components.YouTubePlayerView

/**
 * YouTubePlayerScreen provides an embedded in-app trailer playback experience
 * with fallback actions to launch the official YouTube app if embedded playback is restricted.
 */
@Composable
fun YouTubePlayerScreen(
    videoId: String,
    movieTitle: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubePlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(videoId, movieTitle) {
        if (videoId.isNotBlank() && uiState.videoId != videoId) {
            viewModel.setVideo(videoId, movieTitle)
        }
    }

    val openInYouTubeApp: () -> Unit = {
        val watchUrl = uiState.webWatchUrl.ifBlank { "https://www.youtube.com/watch?v=$videoId" }
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        } catch (_: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {
            }
        }
    }

    val shareVideo: () -> Unit = {
        val watchUrl = uiState.webWatchUrl.ifBlank { "https://www.youtube.com/watch?v=$videoId" }
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, uiState.movieTitle.ifBlank { movieTitle })
                putExtra(Intent.EXTRA_TEXT, "Watch ${uiState.movieTitle.ifBlank { movieTitle }}: $watchUrl")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Video Link"))
        } catch (_: Exception) {
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
        containerColor = ObsidianBackground,
        topBar = {
            PlayerTopBar(
                title = uiState.movieTitle.ifBlank { movieTitle },
                onNavigateBack = onNavigateBack,
                onShare = shareVideo,
                onOpenExternal = openInYouTubeApp
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(ObsidianSurface),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = CinematicCrimson
                    )
                }
            } else if (uiState.isValid) {
                // Embedded YouTube Player
                YouTubePlayerView(
                    videoId = uiState.videoId,
                    onPlayingChange = { isPlaying -> viewModel.setPlaying(isPlaying) },
                    onRestricted = { viewModel.markRestricted(true) },
                    onError = { error -> viewModel.setPlayerError(error) }
                )

                // Video Info & Badges
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = uiState.movieTitle.ifBlank { movieTitle }.ifBlank { "Official Trailer" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = ContentHighEmphasis
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CinematicCrimson.copy(alpha = 0.2f))
                                .border(1.dp, CinematicCrimson, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "YouTube HD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CinematicCrimson,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Text(
                            text = "Video ID: ${uiState.videoId}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ContentLowEmphasis,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content owner restriction warning banner
                    if (uiState.isRestricted) {
                        RestrictedPlaybackBanner(onOpenInYouTubeApp = openInYouTubeApp)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = openInYouTubeApp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CinematicCrimson,
                                contentColor = ContentHighEmphasis
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open in YouTube",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = shareVideo,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = ContentHighEmphasis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share",
                                color = ContentHighEmphasis,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                // Invalid or Error fallback state
                PlayerErrorFallback(
                    errorMessage = uiState.errorMessage ?: "Unable to play video.",
                    onOpenExternal = openInYouTubeApp,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ObsidianSurface)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ContentHighEmphasis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "Trailer Player" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ContentHighEmphasis
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "In-App Video Playback",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ContentLowEmphasis,
                    fontSize = 11.sp
                )
            )
        }

        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share",
                tint = ContentMediumEmphasis
            )
        }

        IconButton(onClick = onOpenExternal) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open in YouTube App",
                tint = ContentMediumEmphasis
            )
        }
    }
}

@Composable
private fun RestrictedPlaybackBanner(
    onOpenInYouTubeApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CinematicAmber.copy(alpha = 0.12f))
            .border(1.dp, CinematicAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = CinematicAmber,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Embedded Playback Restricted",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = CinematicAmber
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The content owner does not permit embedding this video in external applications. You can still watch it smoothly in the YouTube app or browser.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = ContentHighEmphasis,
                lineHeight = 18.sp
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onOpenInYouTubeApp,
            colors = ButtonDefaults.buttonColors(
                containerColor = CinematicAmber,
                contentColor = ObsidianBackground
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Watch on YouTube App",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlayerErrorFallback(
    errorMessage: String,
    onOpenExternal: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CinematicCrimson.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = CinematicCrimson,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Unable to Play Trailer",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ContentHighEmphasis
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = ContentMediumEmphasis,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Go Back", color = ContentHighEmphasis)
            }

            Button(
                onClick = onOpenExternal,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinematicCrimson,
                    contentColor = ContentHighEmphasis
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Try External Player")
            }
        }
    }
}
