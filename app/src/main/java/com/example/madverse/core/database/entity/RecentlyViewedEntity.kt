package com.example.madverse.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.madverse.core.model.Movie

/**
 * Local Room entity representing a movie that the user has recently viewed in MovieDetails.
 * Deduplicated by [movieId] (TMDB movie ID) and ordered by [viewedTimestamp].
 * Bounded to ~20 items.
 */
@Entity(tableName = "recently_viewed_movies")
data class RecentlyViewedEntity(
    @PrimaryKey
    @ColumnInfo(name = "movie_id")
    val movieId: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "poster_path")
    val posterPath: String?,

    @ColumnInfo(name = "backdrop_path")
    val backdropPath: String?,

    @ColumnInfo(name = "release_date")
    val releaseDate: String?,

    @ColumnInfo(name = "vote_average")
    val voteAverage: Double,

    @ColumnInfo(name = "overview")
    val overview: String?,

    @ColumnInfo(name = "viewed_timestamp")
    val viewedTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): Movie {
        return Movie(
            id = movieId,
            title = title,
            overview = overview ?: "",
            posterUrl = posterPath,
            backdropUrl = backdropPath,
            releaseDate = releaseDate,
            voteAverage = voteAverage,
            voteCount = 0,
            genreIds = emptyList(),
            popularity = 0.0
        )
    }

    companion object {
        fun fromDomain(
            movie: Movie,
            viewedTimestamp: Long = System.currentTimeMillis()
        ): RecentlyViewedEntity {
            return RecentlyViewedEntity(
                movieId = movie.id,
                title = movie.title,
                posterPath = movie.posterUrl,
                backdropPath = movie.backdropUrl,
                releaseDate = movie.releaseDate,
                voteAverage = movie.voteAverage,
                overview = movie.overview,
                viewedTimestamp = viewedTimestamp
            )
        }
    }
}
