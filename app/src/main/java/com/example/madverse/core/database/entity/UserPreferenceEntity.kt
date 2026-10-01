package com.example.madverse.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile

@Entity(tableName = "user_preference_profile")
data class UserPreferenceEntity(
    @PrimaryKey
    val id: Int = 1, // Singleton row for the device's user profile

    @ColumnInfo(name = "genre_ids_csv")
    val genreIdsCsv: String, // Comma-separated genre IDs, e.g. "28,878,12"

    @ColumnInfo(name = "mood")
    val mood: String,

    @ColumnInfo(name = "era")
    val era: String,

    @ColumnInfo(name = "runtime_preference")
    val runtimePreference: String,

    @ColumnInfo(name = "preferred_language")
    val preferredLanguage: String?,

    @ColumnInfo(name = "min_rating_threshold")
    val minRatingThreshold: Double = 6.0,

    @ColumnInfo(name = "updated_timestamp")
    val updatedTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): UserPreferenceProfile {
        val genres = if (genreIdsCsv.isBlank()) emptySet() else {
            genreIdsCsv.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
        }

        val parsedMood = try {
            MovieMood.valueOf(mood)
        } catch (_: Exception) {
            MovieMood.ANY
        }

        val parsedEra = try {
            MovieEra.valueOf(era)
        } catch (_: Exception) {
            MovieEra.ANY
        }

        val parsedRuntime = try {
            RuntimePreference.valueOf(runtimePreference)
        } catch (_: Exception) {
            RuntimePreference.ANY
        }

        return UserPreferenceProfile(
            selectedGenreIds = genres,
            mood = parsedMood,
            era = parsedEra,
            runtimePreference = parsedRuntime,
            preferredLanguage = preferredLanguage,
            minRatingThreshold = minRatingThreshold,
            updatedAtTimestamp = updatedTimestamp
        )
    }

    companion object {
        fun fromDomain(profile: UserPreferenceProfile): UserPreferenceEntity {
            return UserPreferenceEntity(
                id = 1,
                genreIdsCsv = profile.selectedGenreIds.joinToString(","),
                mood = profile.mood.name,
                era = profile.era.name,
                runtimePreference = profile.runtimePreference.name,
                preferredLanguage = profile.preferredLanguage,
                minRatingThreshold = profile.minRatingThreshold,
                updatedTimestamp = profile.updatedAtTimestamp
            )
        }
    }
}
