package com.example.madverse.core.network.model

import com.example.madverse.core.common.network.ApiConstants
import com.example.madverse.core.model.Movie
import com.google.gson.annotations.SerializedName

data class MovieResponseDto(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<MovieDto>?,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

data class MovieDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("vote_count") val voteCount: Int?,
    @SerializedName("genre_ids") val genreIds: List<Int>?,
    @SerializedName("popularity") val popularity: Double?,
    @SerializedName("original_language") val originalLanguage: String? = null
) {
    fun toDomain(): Movie {
        return Movie(
            id = id,
            title = title ?: "Untitled",
            overview = overview ?: "",
            posterUrl = posterPath?.let { "${ApiConstants.TMDB_IMAGE_BASE_W500}$it" },
            backdropUrl = backdropPath?.let { "${ApiConstants.TMDB_BACKDROP_BASE_W780}$it" },
            releaseDate = releaseDate,
            voteAverage = voteAverage ?: 0.0,
            voteCount = voteCount ?: 0,
            genreIds = genreIds ?: emptyList(),
            popularity = popularity ?: 0.0,
            originalLanguage = originalLanguage
        )
    }
}
