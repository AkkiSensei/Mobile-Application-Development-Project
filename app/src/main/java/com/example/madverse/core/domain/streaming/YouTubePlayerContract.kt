package com.example.madverse.core.domain.streaming

/**
 * State contract for embedded YouTube trailer playback.
 */
sealed interface YouTubePlayerState {
    data object Idle : YouTubePlayerState
    data class Ready(val videoKey: String) : YouTubePlayerState
    data class Playing(val videoKey: String) : YouTubePlayerState
    data class Paused(val videoKey: String) : YouTubePlayerState
    data class Error(val errorMessage: String) : YouTubePlayerState
    data object Unavailable : YouTubePlayerState
}
