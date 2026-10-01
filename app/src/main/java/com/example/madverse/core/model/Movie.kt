package com.example.madverse.core.model

/**
 * Domain representation of a movie item used across lists (Trending, Top Rated, Upcoming, Search).
 */
data class Movie(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val voteAverage: Double,
    val voteCount: Int,
    val genreIds: List<Int>,
    val popularity: Double
) {
    val releaseYear: String
        get() = releaseDate?.take(4) ?: "Unknown"

    val formattedRating: String
        get() = String.format("%.1f", voteAverage)
}
