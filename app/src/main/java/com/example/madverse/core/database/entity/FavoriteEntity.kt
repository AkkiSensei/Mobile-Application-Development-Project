package com.example.madverse.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.madverse.core.model.Movie

@Entity(tableName = "favorite_movies")
data class FavoriteEntity(
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

    @ColumnInfo(name = "saved_timestamp")
    val savedTimestamp: Long = System.currentTimeMillis()
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
}
