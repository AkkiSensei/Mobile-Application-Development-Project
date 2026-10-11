package com.example.madverse.feature.player.util

import java.net.URI

/**
 * Security and sanitization utility for YouTube video identifiers and URLs.
 * Ensures only authenticated HTTPS YouTube embed URLs are loaded.
 */
object YouTubeUrlSanitizer {

    private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    private val YOUTUBE_WATCH_REGEX = Regex("(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})")
    private val YOUTU_BE_REGEX = Regex("(?:https?://)?(?:www\\.)?youtu\\.be/([a-zA-Z0-9_-]{11})")
    private val YOUTUBE_EMBED_REGEX = Regex("(?:https?://)?(?:www\\.)?youtube(?:-nocookie)?\\.com/embed/([a-zA-Z0-9_-]{11})")

    private val PERMITTED_HOSTS = setOf(
        "www.youtube.com",
        "youtube.com",
        "www.youtube-nocookie.com",
        "youtube-nocookie.com",
        "m.youtube.com"
    )

    /**
     * Validates whether a string is a standard 11-character YouTube video ID.
     */
    fun isValidVideoId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return YOUTUBE_VIDEO_ID_REGEX.matches(id.trim())
    }

    /**
     * Extracts an 11-character video ID from a raw ID or full YouTube URL.
     * Returns null if no valid ID can be resolved.
     */
    fun extractVideoId(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()

        if (isValidVideoId(trimmed)) {
            return trimmed
        }

        YOUTUBE_WATCH_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let {
            if (isValidVideoId(it)) return it
        }

        YOUTU_BE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let {
            if (isValidVideoId(it)) return it
        }

        YOUTUBE_EMBED_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let {
            if (isValidVideoId(it)) return it
        }

        return null
    }

    /**
     * Constructs a strictly sanitized HTTPS YouTube embed URL.
     * Returns null if the video ID is invalid.
     */
    fun buildEmbedUrl(videoId: String?): String? {
        val validId = extractVideoId(videoId) ?: return null
        return "https://www.youtube-nocookie.com/embed/$validId?enablejsapi=1&autoplay=1&modestbranding=1&rel=0"
    }

    /**
     * Builds a safe standard watch URL for opening in external browser or YouTube app.
     */
    fun buildWebWatchUrl(videoId: String?): String {
        val validId = extractVideoId(videoId) ?: videoId.orEmpty()
        return "https://www.youtube.com/watch?v=$validId"
    }

    /**
     * Security check: Verifies if a given URI belongs strictly to an authorized YouTube HTTPS origin.
     * Prevents open-redirects or loading untrusted external web origins.
     */
    fun isPermittedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("https://", ignoreCase = true)) return false

        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            PERMITTED_HOSTS.contains(host)
        } catch (_: Exception) {
            false
        }
    }
}
