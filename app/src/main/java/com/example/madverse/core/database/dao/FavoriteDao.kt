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

    @Query("SELECT * FROM favorite_movies ORDER BY saved_timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorite_movies WHERE movie_id = :movieId LIMIT 1")
    suspend fun getFavoriteById(movieId: Int): FavoriteEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_movies WHERE movie_id = :movieId)")
    fun isFavorite(movieId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(movie: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(movie: FavoriteEntity)

    @Query("DELETE FROM favorite_movies WHERE movie_id = :movieId")
    suspend fun deleteFavoriteById(movieId: Int)
}
