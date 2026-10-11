package com.example.madverse.feature.player.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.madverse.core.navigation.Screen
import com.example.madverse.feature.player.util.YouTubeUrlSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel managing YouTube player state, argument validation, and security sanitization.
 */
@HiltViewModel
class YouTubePlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubePlayerUiState())
    val uiState: StateFlow<YouTubePlayerUiState> = _uiState.asStateFlow()

    init {
        val initialVideoId = savedStateHandle.get<String>(Screen.ARG_VIDEO_ID).orEmpty()
        val initialMovieTitle = savedStateHandle.get<String>(Screen.ARG_MOVIE_TITLE).orEmpty()
        val decodedTitle = decodeTitle(initialMovieTitle)

        validateAndLoadVideo(videoId = initialVideoId, movieTitle = decodedTitle)
    }

    /**
     * Updates the active video ID and movie title, applying sanitization.
     */
    fun setVideo(videoId: String, movieTitle: String = "") {
        validateAndLoadVideo(videoId = videoId, movieTitle = movieTitle)
    }

    /**
     * Marks playback as restricted (e.g. YouTube embedding disabled by creator).
     */
    fun markRestricted(restricted: Boolean) {
        _uiState.update { it.copy(isRestricted = restricted) }
    }

    /**
     * Sets player error message.
     */
    fun setPlayerError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                isLoading = false
            )
        }
    }

    /**
     * Updates playback playing state.
     */
    fun setPlaying(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
    }

    /**
     * Toggles full-screen view state.
     */
    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    private fun validateAndLoadVideo(videoId: String, movieTitle: String) {
        val sanitizedId = YouTubeUrlSanitizer.extractVideoId(videoId)
        val isFullMovie = movieTitle.contains("Full Movie", ignoreCase = true) ||
                movieTitle.contains("Feature Film", ignoreCase = true)
        val isAuthorized = movieTitle.contains("Public Domain", ignoreCase = true) ||
                movieTitle.contains("Free", ignoreCase = true)

        if (sanitizedId != null) {
            val embedUrl = YouTubeUrlSanitizer.buildEmbedUrl(sanitizedId)
            val webWatchUrl = YouTubeUrlSanitizer.buildWebWatchUrl(sanitizedId)

            _uiState.update {
                it.copy(
                    videoId = sanitizedId,
                    movieTitle = movieTitle,
                    embedUrl = embedUrl,
                    webWatchUrl = webWatchUrl,
                    isFullLengthMovie = isFullMovie,
                    isAuthorizedFullLength = isAuthorized,
                    isRestricted = isFullMovie && !isAuthorized,
                    isValid = true,
                    isLoading = false,
                    errorMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    videoId = videoId,
                    movieTitle = movieTitle,
                    embedUrl = null,
                    webWatchUrl = YouTubeUrlSanitizer.buildWebWatchUrl(videoId),
                    isValid = false,
                    isLoading = false,
                    errorMessage = if (videoId.isBlank()) {
                        "No video ID provided."
                    } else {
                        "Invalid YouTube video ID format: '$videoId'. Expected standard 11-character identifier."
                    }
                )
            }
        }
    }

    private fun decodeTitle(encoded: String): String {
        return try {
            java.net.URLDecoder.decode(encoded, "UTF-8")
        } catch (_: Exception) {
            encoded
        }
    }
}
