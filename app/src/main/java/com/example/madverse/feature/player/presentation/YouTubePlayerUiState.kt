package com.example.madverse.feature.player.presentation

data class YouTubePlayerUiState(
    val videoId: String = "",
    val movieTitle: String = "",
    val webWatchUrl: String = "",
    val contentLabel: String = "Video",
    val attribution: String? = null,
    val isValid: Boolean = false,
    val isFullLengthMovie: Boolean = false,
    val isAuthorizedFullLength: Boolean = false,
    val isRestricted: Boolean = false,
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val attempt: Int = 0,
    val errorMessage: String? = null,
    val retryable: Boolean = false,
    val externalError: String? = null
) {
    val canEmbed: Boolean get() = isValid && !isRestricted && errorMessage == null
}
