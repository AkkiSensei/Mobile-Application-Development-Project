package com.example.madverse.feature.player.presentation

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants

enum class PlayerFailure(val message: String, val retryable: Boolean) {
    INVALID_VIDEO("The YouTube video identifier is missing or invalid.", false),
    UNAVAILABLE("YouTube reports that this video is removed, private or unavailable (100).", false),
    EMBEDDING_DISABLED("The owner does not allow embedded playback (101/150). Try opening it in YouTube.", false),
    CLIENT_IDENTITY("YouTube could not identify this app (153). Try again or open in YouTube.", true),
    HTML5("YouTube could not play this video in the embedded player (5). Try again or open in YouTube.", true),
    INITIALIZATION("The player could not become ready. Check your connection and try again.", true),
    UNKNOWN("YouTube reported an unknown playback error. Try again or open in YouTube.", true);

    companion object {
        fun from(error: PlayerConstants.PlayerError): PlayerFailure = when (error) {
            PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST -> INVALID_VIDEO
            PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> UNAVAILABLE
            PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER -> EMBEDDING_DISABLED
            PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER -> CLIENT_IDENTITY
            PlayerConstants.PlayerError.HTML_5_PLAYER -> HTML5
            PlayerConstants.PlayerError.UNKNOWN -> UNKNOWN
        }
    }
}
