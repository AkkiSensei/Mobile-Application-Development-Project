package com.example.madverse.core.data.repository

import com.example.madverse.core.model.QuizQuestion
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for managing user taste quiz questions and persisted preference profiles.
 */
interface QuizRepository {
    fun getUserPreferenceProfile(): Flow<UserPreferenceProfile>
    suspend fun getUserPreferenceProfileOnce(): UserPreferenceProfile
    suspend fun saveUserPreferenceProfile(profile: UserPreferenceProfile)
    suspend fun clearUserPreferenceProfile()
    fun getQuizQuestions(): List<QuizQuestion>
}
