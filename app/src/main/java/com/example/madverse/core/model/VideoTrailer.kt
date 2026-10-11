package com.example.madverse.core.model

/**
 * Categorization of movie videos to distinguish promotional trailers/clips
 * from authorized or restricted full-length films.
 */
enum class VideoCategory(val displayName: String) {
    TRAILER("Trailer"),
    TEASER("Teaser"),
    CLIP("Clip"),
    FEATURETTE("Featurette"),
    BEHIND_THE_SCENES("Behind the Scenes"),
    FULL_MOVIE("Full Movie"),
    OTHER("Video")
}

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
    val isOfficial: Boolean,
    val isAuthorizedFullLength: Boolean = false
) {
    val category: VideoCategory
        get() = when {
            isAuthorizedFullLength ||
            type.contains("Full Movie", ignoreCase = true) ||
            type.contains("Feature Film", ignoreCase = true) ||
            name.contains("Full Movie", ignoreCase = true) -> VideoCategory.FULL_MOVIE
            type.equals("Trailer", ignoreCase = true) -> VideoCategory.TRAILER
            type.equals("Teaser", ignoreCase = true) -> VideoCategory.TEASER
            type.equals("Clip", ignoreCase = true) -> VideoCategory.CLIP
            type.equals("Featurette", ignoreCase = true) -> VideoCategory.FEATURETTE
            type.contains("Behind the Scenes", ignoreCase = true) -> VideoCategory.BEHIND_THE_SCENES
            else -> VideoCategory.OTHER
        }

    val isFullLengthMovie: Boolean
        get() = category == VideoCategory.FULL_MOVIE

    val isTrailerOrTeaser: Boolean
        get() = category == VideoCategory.TRAILER || category == VideoCategory.TEASER

    /**
     * Determines whether in-app playback is permitted:
     * - Trailers, teasers, and clips are always permitted.
     * - Full-length films are permitted only if verified as authorized/public-domain.
     */
    val isPlayableInApp: Boolean
        get() = site.equals("YouTube", ignoreCase = true) &&
                (!isFullLengthMovie || isAuthorizedFullLength)

    val isEmbeddableYouTubeTrailer: Boolean
        get() = site.equals("YouTube", ignoreCase = true) &&
                (type.equals("Trailer", ignoreCase = true) || type.equals("Teaser", ignoreCase = true))

    val youtubeWatchUrl: String
        get() = "https://www.youtube.com/watch?v=$key"
}
