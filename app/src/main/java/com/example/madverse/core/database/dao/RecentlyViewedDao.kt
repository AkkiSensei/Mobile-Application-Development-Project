package com.example.madverse.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.madverse.core.database.entity.RecentlyViewedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyViewedDao {

    @Query("SELECT * FROM recently_viewed_movies ORDER BY viewed_timestamp DESC LIMIT :limit")
    fun getRecentlyViewed(limit: Int = 20): Flow<List<RecentlyViewedEntity>>

    @Query("SELECT * FROM recently_viewed_movies ORDER BY viewed_timestamp DESC LIMIT :limit")
    suspend fun getRecentlyViewedOnce(limit: Int = 20): List<RecentlyViewedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: RecentlyViewedEntity)

    /**
     * Bounded history maintenance: keeps only the latest [keepCount] movies by last-viewed time.
     */
    @Query("DELETE FROM recently_viewed_movies WHERE movie_id NOT IN (SELECT movie_id FROM recently_viewed_movies ORDER BY viewed_timestamp DESC LIMIT :keepCount)")
    suspend fun trimToMaxLimit(keepCount: Int = 20)

    @Query("DELETE FROM recently_viewed_movies WHERE movie_id = :movieId")
    suspend fun deleteByMovieId(movieId: Int)

    @Query("DELETE FROM recently_viewed_movies")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM recently_viewed_movies")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recently_viewed_movies")
    suspend fun getCountOnce(): Int
}
