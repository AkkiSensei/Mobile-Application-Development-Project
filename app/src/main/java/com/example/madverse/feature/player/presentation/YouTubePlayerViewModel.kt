package com.example.madverse.feature.player.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.madverse.core.data.repository.FreeMovieRepository
import com.example.madverse.core.model.FilmFormat
import com.example.madverse.core.model.VideoCategory
import com.example.madverse.core.navigation.Screen
import com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class YouTubePlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogue: FreeMovieRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(YouTubePlayerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val catalogueId = savedStateHandle.get<String>(Screen.ARG_CATALOGUE_ID)
        if (catalogueId != null) {
            val film = catalogue.find(catalogueId)
            if (film == null) {
                _uiState.value = YouTubePlayerUiState(isLoading = false, errorMessage = "This catalogue film is unavailable. Return to Free Movies.")
            } else {
                val isYouTube = film.site.equals("YouTube", ignoreCase = true)
                val valid = isYouTube && YouTubeUrlSanitizer.isValidVideoId(film.videoId)
                val feature = film.format == FilmFormat.FEATURE_FILM
                _uiState.value = YouTubePlayerUiState(
                    videoId = if (valid) film.videoId else "", movieTitle = film.title,
                    webWatchUrl = if (valid) YouTubeUrlSanitizer.buildWebWatchUrl(film.videoId) else "",
                    isValid = valid, isFullLengthMovie = feature,
                    isAuthorizedFullLength = feature && film.isAuthorized,
                    isRestricted = !film.isEligibleForYouTube, isLoading = film.isEligibleForYouTube,
                    attribution = film.attribution, contentLabel = film.format.label,
                    errorMessage = when {
                        !isYouTube -> "This catalogue film is not available through the YouTube player."
                        !valid -> PlayerFailure.INVALID_VIDEO.message
                        !film.isAuthorized -> "This film's authorization has not been established. In-app playback is unavailable."
                        else -> null
                    })
            }
        } else {
            val category = runCatching { VideoCategory.valueOf(savedStateHandle.get<String>(Screen.ARG_VIDEO_TYPE).orEmpty()) }
                .getOrDefault(VideoCategory.OTHER)
            setVideo(savedStateHandle.get<String>(Screen.ARG_VIDEO_ID).orEmpty(),
                savedStateHandle.get<String>(Screen.ARG_MOVIE_TITLE).orEmpty(), category)
        }
    }

    // Navigation Compose already decodes title arguments; never URL-decode them a second time.
    fun setVideo(videoId: String, movieTitle: String = "", category: VideoCategory = VideoCategory.OTHER) {
        val valid = YouTubeUrlSanitizer.isValidVideoId(videoId)
        val allowed = category in setOf(VideoCategory.TRAILER, VideoCategory.TEASER, VideoCategory.CLIP,
            VideoCategory.FEATURETTE, VideoCategory.BEHIND_THE_SCENES)
        _uiState.value = YouTubePlayerUiState(
            videoId = if (valid) videoId else "", movieTitle = movieTitle,
            webWatchUrl = if (valid) YouTubeUrlSanitizer.buildWebWatchUrl(videoId) else "",
            isValid = valid, isLoading = valid && allowed, isRestricted = !allowed,
            isFullLengthMovie = category == VideoCategory.FULL_MOVIE, contentLabel = category.displayName,
            attempt = _uiState.value.attempt + 1,
            errorMessage = when {
                !valid -> PlayerFailure.INVALID_VIDEO.message
                !allowed -> "This video's eligibility for in-app playback has not been established. Use the verified Free Movies catalogue for complete films."
                else -> null
            }
        )
    }

    fun initializing(attempt: Int) = updateAttempt(attempt) { it.copy(isLoading = true, isReady = false, isPlaying = false) }
    fun onReady(attempt: Int) = updateAttempt(attempt) { it.copy(isLoading = false, isReady = true) }
    fun setPlaying(attempt: Int, playing: Boolean) = updateAttempt(attempt) { it.copy(isPlaying = playing) }
    fun fail(attempt: Int, failure: PlayerFailure) = updateAttempt(attempt) {
        it.copy(isLoading = false, isReady = false, isPlaying = false, errorMessage = failure.message, retryable = failure.retryable)
    }
    fun initializationTimedOut(attempt: Int) {
        if (_uiState.value.isLoading) fail(attempt, PlayerFailure.INITIALIZATION)
    }
    fun retry() {
        _uiState.update {
            if (!it.retryable || !it.isValid || it.isRestricted) it else it.copy(
                attempt = it.attempt + 1, isLoading = true, isReady = false, isPlaying = false,
                errorMessage = null, retryable = false, externalError = null)
        }
    }
    fun externalFailed(message: String = "No YouTube app or browser could open this link.") {
        _uiState.update { it.copy(externalError = message) }
    }
    private fun updateAttempt(attempt: Int, update: (YouTubePlayerUiState) -> YouTubePlayerUiState) {
        _uiState.update { if (it.attempt == attempt && it.canEmbed) update(it) else it }
    }
}
