package com.example.madverse.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.madverse.core.database.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferenceDao {

    @Query("SELECT * FROM user_preference_profile WHERE user_id = :userId LIMIT 1")
    fun getUserPreferenceProfile(userId: String): Flow<UserPreferenceEntity?>

    @Query("SELECT * FROM user_preference_profile WHERE user_id = :userId LIMIT 1")
    suspend fun getUserPreferenceProfileOnce(userId: String): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserPreferenceProfile(profile: UserPreferenceEntity)

    @Query("DELETE FROM user_preference_profile WHERE user_id = :userId")
    suspend fun clearUserPreferenceProfile(userId: String)

    // Default overloads for guest / single-user backward compatibility
    fun getUserPreferenceProfile(): Flow<UserPreferenceEntity?> = getUserPreferenceProfile("guest")
    suspend fun getUserPreferenceProfileOnce(): UserPreferenceEntity? = getUserPreferenceProfileOnce("guest")
    suspend fun clearUserPreferenceProfile() = clearUserPreferenceProfile("guest")
}
