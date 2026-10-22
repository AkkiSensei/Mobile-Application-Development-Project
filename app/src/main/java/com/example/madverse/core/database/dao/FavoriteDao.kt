package com.example.madverse.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.madverse.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorite_movies WHERE user_id = :userId ORDER BY saved_timestamp DESC")
    fun getAllFavorites(userId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorite_movies WHERE movie_id = :movieId AND user_id = :userId LIMIT 1")
    suspend fun getFavoriteById(movieId: Int, userId: String): FavoriteEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_movies WHERE movie_id = :movieId AND user_id = :userId)")
    fun isFavorite(movieId: Int, userId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_movies WHERE movie_id = :movieId AND user_id = :userId)")
    suspend fun isFavoriteOnce(movieId: Int, userId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(movie: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(movie: FavoriteEntity)

    @Query("DELETE FROM favorite_movies WHERE movie_id = :movieId AND user_id = :userId")
    suspend fun deleteFavoriteById(movieId: Int, userId: String)

    @Query("DELETE FROM favorite_movies WHERE user_id = :userId")
    suspend fun deleteAllFavoritesForUser(userId: String)

    @Query("SELECT COUNT(*) FROM favorite_movies WHERE user_id = :userId")
    fun getFavoritesCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM favorite_movies WHERE user_id = :userId")
    suspend fun getFavoritesCountOnce(userId: String): Int

    @Query("SELECT * FROM favorite_movies WHERE user_id = :userId")
    suspend fun getAllFavoritesOnce(userId: String): List<FavoriteEntity>

    // Default overloads for guest / single-user backward compatibility
    fun getAllFavorites(): Flow<List<FavoriteEntity>> = getAllFavorites("guest")
    suspend fun getFavoriteById(movieId: Int): FavoriteEntity? = getFavoriteById(movieId, "guest")
    fun isFavorite(movieId: Int): Flow<Boolean> = isFavorite(movieId, "guest")
    suspend fun isFavoriteOnce(movieId: Int): Boolean = isFavoriteOnce(movieId, "guest")
    suspend fun deleteFavoriteById(movieId: Int) = deleteFavoriteById(movieId, "guest")
}
