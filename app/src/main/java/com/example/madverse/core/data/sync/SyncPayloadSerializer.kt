package com.example.madverse.core.data.sync

import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.UserPreferenceProfile
import com.google.gson.Gson

/**
 * Utility for serializing and deserializing sync queue payloads.
 * Preserves immutable operation intent across process restarts and offline periods.
 */
object SyncPayloadSerializer {
    private val gson = Gson()

    fun movieToJson(movie: Movie): String = gson.toJson(movie)

    fun jsonToMovie(json: String): Movie? = try {
        gson.fromJson(json, Movie::class.java)
    } catch (_: Exception) {
        null
    }

    fun preferenceToJson(profile: UserPreferenceProfile): String = gson.toJson(profile)

    fun jsonToPreference(json: String): UserPreferenceProfile? = try {
        gson.fromJson(json, UserPreferenceProfile::class.java)
    } catch (_: Exception) {
        null
    }
}
