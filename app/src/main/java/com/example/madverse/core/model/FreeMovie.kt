package com.example.madverse.core.model

enum class FilmFormat(val label: String) { SHORT_FILM("Complete short film"), FEATURE_FILM("Feature-length film") }
enum class AuthorizationBasis { PUBLISHER_CC_BY_3, PUBLISHER_CC_BY_NC_ND, RIGHTSHOLDER_PERMISSION, UNKNOWN }

/** Rights evidence is curated in source, never accepted from navigation arguments or video titles. */
data class FreeMovie(
    val catalogueId: String,
    val title: String,
    val videoId: String,
    val publisher: String,
    val description: String,
    val format: FilmFormat,
    val authorization: AuthorizationBasis,
    val sourceUrl: String,
    val rightsUrl: String,
    val attribution: String,
    val verifiedOn: String,
    val verificationNotes: String,
    val regionNotes: String,
    val licenseUrl: String = "https://creativecommons.org/licenses/by/3.0/",
    val site: String = "YouTube"
) {
    val isAuthorized: Boolean get() = when (authorization) {
        AuthorizationBasis.PUBLISHER_CC_BY_3, AuthorizationBasis.PUBLISHER_CC_BY_NC_ND,
        AuthorizationBasis.RIGHTSHOLDER_PERMISSION -> true
        AuthorizationBasis.UNKNOWN -> false
    }
    val isEligibleForYouTube: Boolean get() = isAuthorized && site.equals("YouTube", ignoreCase = true) &&
        com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer.isValidVideoId(videoId)
    val thumbnailUrl: String get() = if (site.equals("YouTube", ignoreCase = true))
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
}
