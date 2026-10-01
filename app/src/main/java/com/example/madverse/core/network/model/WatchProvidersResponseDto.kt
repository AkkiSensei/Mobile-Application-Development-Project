package com.example.madverse.core.network.model

import com.example.madverse.core.common.network.ApiConstants
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.ProviderType
import com.example.madverse.core.model.WatchProvider
import com.google.gson.annotations.SerializedName

data class WatchProvidersResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("results") val results: Map<String, CountryWatchProvidersDto>?
) {
    fun toDomain(movieId: Int, countryCode: String = "US"): MovieWatchProviders {
        // Fall back to US if requested country not present, or first available country
        val countryData = results?.get(countryCode) ?: results?.get("US") ?: results?.values?.firstOrNull()

        fun mapProviders(items: List<ProviderItemDto>?, type: ProviderType): List<WatchProvider> {
            return items?.map { item ->
                WatchProvider(
                    providerId = item.providerId,
                    providerName = item.providerName ?: "Unknown Provider",
                    logoUrl = item.logoPath?.let { "${ApiConstants.TMDB_IMAGE_BASE_W500}$it" },
                    type = type
                )
            } ?: emptyList()
        }

        return MovieWatchProviders(
            movieId = movieId,
            tmdbWatchPageUrl = countryData?.link,
            flatrate = mapProviders(countryData?.flatrate, ProviderType.STREAMING_SUBSCRIPTION),
            rent = mapProviders(countryData?.rent, ProviderType.RENT),
            buy = mapProviders(countryData?.buy, ProviderType.BUY)
        )
    }
}

data class CountryWatchProvidersDto(
    @SerializedName("link") val link: String?,
    @SerializedName("flatrate") val flatrate: List<ProviderItemDto>?,
    @SerializedName("rent") val rent: List<ProviderItemDto>?,
    @SerializedName("buy") val buy: List<ProviderItemDto>?
)

data class ProviderItemDto(
    @SerializedName("provider_id") val providerId: Int,
    @SerializedName("provider_name") val providerName: String?,
    @SerializedName("logo_path") val logoPath: String?,
    @SerializedName("display_priority") val displayPriority: Int?
)
