package com.example.madverse.core.network.model

import com.example.madverse.core.common.network.ApiConstants
import com.example.madverse.core.model.Genre
import com.example.madverse.core.model.MovieDetails
import com.google.gson.annotations.SerializedName

data class MovieDetailsDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("tagline") val tagline: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("runtime") val runtime: Int?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("vote_count") val voteCount: Int?,
    @SerializedName("genres") val genres: List<GenreDto>?,
    @SerializedName("status") val status: String?,
    @SerializedName("budget") val budget: Long?,
    @SerializedName("revenue") val revenue: Long?,
    @SerializedName("original_language") val originalLanguage: String?
) {
    fun toDomain(isFavorite: Boolean = false, isInWatchlist: Boolean = false): MovieDetails {
        return MovieDetails(
            id = id,
            title = title ?: "Untitled",
            tagline = tagline?.takeIf { it.isNotBlank() },
            overview = overview ?: "",
            posterUrl = posterPath?.let { "${ApiConstants.TMDB_IMAGE_BASE_W500}$it" },
            backdropUrl = backdropPath?.let { "${ApiConstants.TMDB_BACKDROP_BASE_W780}$it" },
            releaseDate = releaseDate,
            runtimeMinutes = runtime,
            voteAverage = voteAverage ?: 0.0,
            voteCount = voteCount ?: 0,
            genres = genres?.map { it.toDomain() } ?: emptyList(),
            status = status ?: "Released",
            budget = budget ?: 0L,
            revenue = revenue ?: 0L,
            originalLanguage = originalLanguage ?: "en",
            isFavorite = isFavorite,
            isInWatchlist = isInWatchlist
        )
    }
}

data class GenreDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?
) {
    fun toDomain(): Genre = Genre(id = id, name = name ?: "Unknown")
}

data class GenresResponseDto(
    @SerializedName("genres") val genres: List<GenreDto>?
)
