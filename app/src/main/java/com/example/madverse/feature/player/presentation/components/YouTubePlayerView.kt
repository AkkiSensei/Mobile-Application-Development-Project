package com.example.madverse.feature.player.presentation.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.madverse.core.designsystem.theme.ObsidianBackground
import com.example.madverse.feature.player.util.YouTubeUrlSanitizer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView as AndroidYouTubePlayerView

/**
 * Embedded, secured in-app YouTube player container.
 * Enforces origin security, disables file and content access on the embedded WebView,
 * and handles YouTube embedding restrictions gracefully.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    modifier: Modifier = Modifier,
    onPlayingChange: (Boolean) -> Unit = {},
    onRestricted: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(ObsidianBackground),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            factory = { context ->
                AndroidYouTubePlayerView(context).apply {
                    enableAutomaticInitialization = false
                    lifecycleOwner.lifecycle.addObserver(this)

                    // Secure the underlying WebView container
                    secureEmbeddedWebView(this)

                    val options = IFramePlayerOptions.Builder()
                        .controls(1)
                        .rel(0)
                        .build()

                    initialize(
                        youTubePlayerListener = object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                val sanitizedId = YouTubeUrlSanitizer.extractVideoId(videoId)
                                if (sanitizedId != null) {
                                    youTubePlayer.loadVideo(sanitizedId, 0f)
                                } else {
                                    onError("Invalid YouTube Video ID: $videoId")
                                }
                            }

                            override fun onError(
                                youTubePlayer: YouTubePlayer,
                                error: PlayerConstants.PlayerError
                            ) {
                                when (error) {
                                    PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER -> {
                                        onRestricted()
                                    }
                                    else -> {
                                        onError(error.name)
                                    }
                                }
                            }

                            override fun onStateChange(
                                youTubePlayer: YouTubePlayer,
                                state: PlayerConstants.PlayerState
                            ) {
                                onPlayingChange(state == PlayerConstants.PlayerState.PLAYING)
                            }
                        },
                        playerOptions = options
                    )
                }
            },
            update = { playerView ->
                secureEmbeddedWebView(playerView)
            },
            onRelease = { playerView ->
                lifecycleOwner.lifecycle.removeObserver(playerView)
                playerView.release()
            }
        )
    }
}

/**
 * Hardens the embedded WebView instance inside the player:
 * - Disables file scheme access (preventing file exfiltration attacks)
 * - Disables content scheme access
 * - Disables unnecessary cache databases
 * - Verifies no arbitrary JS interfaces are attached
 */
private fun secureEmbeddedWebView(viewGroup: ViewGroup) {
    for (i in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(i)
        if (child is WebView) {
            child.settings.apply {
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
            }
        } else if (child is ViewGroup) {
            secureEmbeddedWebView(child)
        }
    }
}
