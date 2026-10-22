package com.example.madverse.core.common.network

/**
 * Public API endpoints and CDN image constants for TMDB.
 * Sensitive keys are kept out of this file and injected via BuildConfig.
 */
object ApiConstants {
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val TMDB_IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_LOGO_BASE_W154 = "https://image.tmdb.org/t/p/w154"
    const val TMDB_PROFILE_BASE_W185 = "https://image.tmdb.org/t/p/w185"
    const val TMDB_IMAGE_BASE_ORIGINAL = "https://image.tmdb.org/t/p/original"
    const val TMDB_BACKDROP_BASE_W780 = "https://image.tmdb.org/t/p/w780"

    const val TIMEOUT_SECONDS = 30L

    /** Default ISO 3166-1 alpha-2 country code for streaming and regional discovery */
    const val DEFAULT_REGION = "IN"
}
