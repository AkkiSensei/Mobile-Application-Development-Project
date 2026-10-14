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
    // Legacy metadata only. Full-film eligibility is resolved exclusively through FreeMovieRepository.
    val isAuthorizedFullLength: Boolean = false
) {
    val category: VideoCategory
        get() = when {
            type.contains("Full Movie", ignoreCase = true) ||
            type.contains("Feature Film", ignoreCase = true) -> VideoCategory.FULL_MOVIE
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
     * Promotional source eligibility only; YouTube decides actual embedding availability.
     * Complete films must resolve through the curated catalogue, never a metadata Boolean.
     */
    val isPlayableInApp: Boolean
        get() = site.equals("YouTube", ignoreCase = true) &&
                com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer.isValidVideoId(key) &&
                category in setOf(VideoCategory.TRAILER, VideoCategory.TEASER, VideoCategory.CLIP,
                    VideoCategory.FEATURETTE, VideoCategory.BEHIND_THE_SCENES)

    val isEmbeddableYouTubeTrailer: Boolean
        get() = isPlayableInApp &&
                (type.equals("Trailer", ignoreCase = true) || type.equals("Teaser", ignoreCase = true))

    val youtubeWatchUrl: String
        get() = if (site.equals("YouTube", ignoreCase = true))
            com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer.buildWebWatchUrl(key) else ""
}
