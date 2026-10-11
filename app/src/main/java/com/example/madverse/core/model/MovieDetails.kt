package com.example.madverse.core.model

/**
 * Domain representation of comprehensive movie details.
 */
data class MovieDetails(
    val id: Int,
    val title: String,
    val tagline: String?,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val voteAverage: Double,
    val voteCount: Int,
    val genres: List<Genre>,
    val status: String,
    val budget: Long,
    val revenue: Long,
    val originalLanguage: String,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false
) {
    val releaseYear: String
        get() = releaseDate?.take(4) ?: "Unknown"

    val formattedRuntime: String
        get() = runtimeMinutes?.let { minutes ->
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (hours > 0) "${hours}h ${remainingMinutes}m" else "${remainingMinutes}m"
        } ?: "N/A"

    val formattedRating: String
        get() = String.format("%.1f", voteAverage)

    fun toMovie(): Movie = Movie(
        id = id,
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        voteCount = voteCount,
        genreIds = genres.map { it.id },
        popularity = 0.0
    )
}

data class Genre(
    val id: Int,
    val name: String
)
