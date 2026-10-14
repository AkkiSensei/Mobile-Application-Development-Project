package com.example.madverse.feature.player.presentation.components

import android.util.Log
import android.view.ViewGroup
import android.view.LayoutInflater
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.madverse.BuildConfig
import com.example.madverse.R
import com.example.madverse.feature.player.presentation.PlayerFailure
import com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView as NativePlayer

/** Each video/attempt/owner gets one player. Standard YouTube controls remain unobstructed. */
@Composable
fun YouTubePlayerView(
    videoId: String,
    attempt: Int,
    onInitializing: () -> Unit,
    onReady: () -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    onError: (PlayerFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    val owner = LocalLifecycleOwner.current
    val initializingCallback by rememberUpdatedState(onInitializing)
    val readyCallback by rememberUpdatedState(onReady)
    val playingCallback by rememberUpdatedState(onPlayingChange)
    val errorCallback by rememberUpdatedState(onError)
    key(videoId, attempt, owner) {
        // This flag belongs to this player instance, not to subsequent retries.
        val active = remember { booleanArrayOf(true) }
        val release = remember { arrayOfNulls<() -> Unit>(1) }
        AndroidView(
            modifier = modifier.fillMaxWidth().heightIn(min = 200.dp).aspectRatio(16f / 9f),
            factory = { context ->
                // Disable auto-init in XML before the library constructor runs.
                (LayoutInflater.from(context).inflate(R.layout.view_youtube_player, null, false) as NativePlayer).apply {
                    enableAutomaticInitialization = false
                    val nativePlayer = this
                    lateinit var observer: LifecycleEventObserver
                    release[0] = {
                        if (active[0]) {
                            active[0] = false
                            owner.lifecycle.removeObserver(observer)
                            nativePlayer.release()
                            trace("released")
                        }
                    }
                    observer = LifecycleEventObserver { source, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) release[0]?.invoke()
                        else if (active[0]) {
                            nativePlayer.onStateChanged(source, event)
                            if (event == Lifecycle.Event.ON_STOP) {
                                playingCallback(false)
                                trace("stopped")
                            }
                        }
                    }
                    owner.lifecycle.addObserver(observer)
                    if (!active[0]) return@apply
                    secureEmbeddedWebView(this)
                    if (!YouTubeUrlSanitizer.isValidVideoId(videoId)) {
                        errorCallback(PlayerFailure.INVALID_VIDEO)
                    } else try {
                        initializingCallback()
                        trace("initializing")
                        // Version 13 derives the HTTPS origin from context.packageName. Do not spoof it.
                        val options = IFramePlayerOptions.Builder(context).controls(1).autoplay(0).fullscreen(0).build()
                        initialize(object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                if (!active[0]) return
                                trace("ready")
                                youTubePlayer.cueVideo(videoId, 0f)
                                readyCallback()
                            }
                            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                if (!active[0]) return
                                trace("error:${error.name}")
                                errorCallback(PlayerFailure.from(error))
                            }
                            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                if (!active[0]) return
                                trace("state:${state.name}")
                                playingCallback(state == PlayerConstants.PlayerState.PLAYING)
                            }
                        }, true, options, videoId)
                    } catch (_: Exception) {
                        errorCallback(PlayerFailure.INITIALIZATION)
                    }
                }
            },
            onRelease = { release[0]?.invoke() }
        )
    }
}

private fun trace(event: String) {
    // Only static lifecycle/state names, never movie titles, URLs, credentials or user data.
    if (BuildConfig.DEBUG) Log.d("MADVersePlayer", event)
}

private fun secureEmbeddedWebView(group: ViewGroup) {
    for (index in 0 until group.childCount) {
        when (val child = group.getChildAt(index)) {
            is WebView -> child.settings.apply {
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
            }
            is ViewGroup -> secureEmbeddedWebView(child)
        }
    }
}
