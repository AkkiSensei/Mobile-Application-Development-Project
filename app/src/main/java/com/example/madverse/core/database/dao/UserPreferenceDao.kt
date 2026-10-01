package com.example.madverse.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.madverse.core.database.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferenceDao {

    @Query("SELECT * FROM user_preference_profile WHERE id = 1 LIMIT 1")
    fun getUserPreferenceProfile(): Flow<UserPreferenceEntity?>

    @Query("SELECT * FROM user_preference_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserPreferenceProfileOnce(): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserPreferenceProfile(profile: UserPreferenceEntity)

    @Query("DELETE FROM user_preference_profile WHERE id = 1")
    suspend fun clearUserPreferenceProfile()
}
