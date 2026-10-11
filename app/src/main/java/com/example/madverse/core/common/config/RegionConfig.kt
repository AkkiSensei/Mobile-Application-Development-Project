package com.example.madverse.core.common.config

/**
 * Centralized region and locale configuration for MADVerse.
 *
 * Defines India ("IN") as the default target region for streaming availability,
 * watch providers, and regional catalog discovery, with fallback to "US".
 * Extensible for future user region preferences.
 */
object RegionConfig {
    const val DEFAULT_COUNTRY_CODE = "IN"
    const val FALLBACK_COUNTRY_CODE = "US"
}
