package com.example.madverse.feature.player.presentation

/**
 * UI State for the embedded in-app YouTube Player screen.
 */
data class YouTubePlayerUiState(
    val videoId: String = "",
    val movieTitle: String = "",
    val embedUrl: String? = null,
    val webWatchUrl: String = "",
    val isValid: Boolean = false,
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val isRestricted: Boolean = false,
    val isFullScreen: Boolean = false,
    val errorMessage: String? = null
)
