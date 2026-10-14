package com.example.madverse.core.model

enum class FilmFormat(val label: String) { SHORT_FILM("Complete short film"), FEATURE_FILM("Feature-length film") }
enum class AuthorizationBasis { PUBLISHER_CC_BY_3, PUBLISHER_CC_BY_NC_ND, RIGHTSHOLDER_PERMISSION }

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
    val licenseUrl: String = "https://creativecommons.org/licenses/by/3.0/"
) {
    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
