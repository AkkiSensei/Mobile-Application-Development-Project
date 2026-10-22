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

    @Query("SELECT * FROM watchlist_movies WHERE user_id = :userId ORDER BY saved_timestamp DESC")
    fun getAllWatchlistMovies(userId: String): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist_movies WHERE movie_id = :movieId AND user_id = :userId LIMIT 1")
    suspend fun getWatchlistMovieById(movieId: Int, userId: String): WatchlistEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_movies WHERE movie_id = :movieId AND user_id = :userId)")
    fun isMovieInWatchlist(movieId: Int, userId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_movies WHERE movie_id = :movieId AND user_id = :userId)")
    suspend fun isMovieInWatchlistOnce(movieId: Int, userId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: WatchlistEntity)

    @Delete
    suspend fun deleteMovie(movie: WatchlistEntity)

    @Query("DELETE FROM watchlist_movies WHERE movie_id = :movieId AND user_id = :userId")
    suspend fun deleteMovieById(movieId: Int, userId: String)

    @Query("DELETE FROM watchlist_movies WHERE user_id = :userId")
    suspend fun deleteAllWatchlistForUser(userId: String)

    @Query("SELECT COUNT(*) FROM watchlist_movies WHERE user_id = :userId")
    fun getWatchlistCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM watchlist_movies WHERE user_id = :userId")
    suspend fun getWatchlistCountOnce(userId: String): Int

    @Query("SELECT * FROM watchlist_movies WHERE user_id = :userId")
    suspend fun getAllWatchlistOnce(userId: String): List<WatchlistEntity>

    // Default overloads for guest / single-user backward compatibility
    fun getAllWatchlistMovies(): Flow<List<WatchlistEntity>> = getAllWatchlistMovies("guest")
    suspend fun getWatchlistMovieById(movieId: Int): WatchlistEntity? = getWatchlistMovieById(movieId, "guest")
    fun isMovieInWatchlist(movieId: Int): Flow<Boolean> = isMovieInWatchlist(movieId, "guest")
    suspend fun isMovieInWatchlistOnce(movieId: Int): Boolean = isMovieInWatchlistOnce(movieId, "guest")
    suspend fun deleteMovieById(movieId: Int) = deleteMovieById(movieId, "guest")
}
