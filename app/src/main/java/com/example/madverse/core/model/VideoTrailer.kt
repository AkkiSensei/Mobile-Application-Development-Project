package com.example.madverse.core.model

/**
 * Domain representation of a movie video/trailer.
 * Key corresponds to the YouTube video ID if site is "YouTube".
 */
data class VideoTrailer(
    val id: String,
    val key: String,
    val name: String,
    val site: String,
    val type: String,
    val isOfficial: Boolean
) {
    val isEmbeddableYouTubeTrailer: Boolean
        get() = site.equals("YouTube", ignoreCase = true) &&
                (type.equals("Trailer", ignoreCase = true) || type.equals("Teaser", ignoreCase = true))

    val youtubeWatchUrl: String
        get() = "https://www.youtube.com/watch?v=$key"
}
