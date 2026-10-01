package com.example.madverse.core.common.network

/**
 * Public API endpoints and CDN image constants for TMDB.
 * Sensitive keys are kept out of this file and injected via BuildConfig.
 */
object ApiConstants {
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val TMDB_IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_IMAGE_BASE_ORIGINAL = "https://image.tmdb.org/t/p/original"
    const val TMDB_BACKDROP_BASE_W780 = "https://image.tmdb.org/t/p/w780"

    const val TIMEOUT_SECONDS = 30L
}
