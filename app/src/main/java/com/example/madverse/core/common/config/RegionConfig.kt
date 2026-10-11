package com.example.madverse.core.common.config

/**
 * Centralized region and locale configuration for MADVerse.
 *
 * Defines India ("IN") as the default target region for streaming availability,
 * watch providers, and regional catalog discovery, with fallback to "US".
 * Extensible for future user region preferences.
 */
/**
 * Representation of a supported streaming region/country.
 */
data class RegionOption(
    val countryCode: String,
    val displayName: String,
    val flagEmoji: String
)

object RegionConfig {
    const val DEFAULT_COUNTRY_CODE = "IN"
    const val FALLBACK_COUNTRY_CODE = "US"

    val SUPPORTED_REGIONS: List<RegionOption> = listOf(
        RegionOption(countryCode = "IN", displayName = "India", flagEmoji = "🇮🇳"),
        RegionOption(countryCode = "US", displayName = "United States", flagEmoji = "🇺🇸"),
        RegionOption(countryCode = "GB", displayName = "United Kingdom", flagEmoji = "🇬🇧"),
        RegionOption(countryCode = "CA", displayName = "Canada", flagEmoji = "🇨🇦"),
        RegionOption(countryCode = "AU", displayName = "Australia", flagEmoji = "🇦🇺"),
        RegionOption(countryCode = "JP", displayName = "Japan", flagEmoji = "🇯🇵")
    )
}
