package com.example.madverse.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.madverse.core.database.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist_movies ORDER BY saved_timestamp DESC")
    fun getAllWatchlistMovies(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist_movies WHERE movie_id = :movieId LIMIT 1")
    suspend fun getWatchlistMovieById(movieId: Int): WatchlistEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_movies WHERE movie_id = :movieId)")
    fun isMovieInWatchlist(movieId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_movies WHERE movie_id = :movieId)")
    suspend fun isMovieInWatchlistOnce(movieId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: WatchlistEntity)

    @Delete
    suspend fun deleteMovie(movie: WatchlistEntity)

    @Query("DELETE FROM watchlist_movies WHERE movie_id = :movieId")
    suspend fun deleteMovieById(movieId: Int)
}
