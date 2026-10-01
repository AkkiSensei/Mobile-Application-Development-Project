package com.example.madverse.core.model

/**
 * Domain representation of an official streaming / rental watch provider.
 */
data class WatchProvider(
    val providerId: Int,
    val providerName: String,
    val logoUrl: String?,
    val type: ProviderType,
    val directLink: String? = null
)

enum class ProviderType {
    STREAMING_SUBSCRIPTION, // Flatrate (Netflix, Prime, Disney+, etc.)
    RENT,                   // Rent (Apple TV, Google Play, etc.)
    BUY                     // Purchase
}

data class MovieWatchProviders(
    val movieId: Int,
    val tmdbWatchPageUrl: String?,
    val flatrate: List<WatchProvider>,
    val rent: List<WatchProvider>,
    val buy: List<WatchProvider>
) {
    val hasAnyProviders: Boolean
        get() = flatrate.isNotEmpty() || rent.isNotEmpty() || buy.isNotEmpty()
}
