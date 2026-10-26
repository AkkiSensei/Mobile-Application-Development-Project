package com.example.madverse.core.data.sync

import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.UserPreferenceProfile
import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * Utility for serializing and deserializing sync queue payloads.
 * Preserves immutable operation intent across process restarts and offline periods.
 */
object SyncPayloadSerializer {
    private val gson = Gson()

    fun movieToJson(movie: Movie): String = gson.toJson(movie)

    fun jsonToMovie(json: String): Movie? = try {
        val fields = JsonParser.parseString(json).asJsonObject
        require(listOf("id", "title", "overview", "voteAverage", "voteCount", "genreIds", "popularity").all { fields.has(it) && !fields[it].isJsonNull })
        require(fields["title"].asJsonPrimitive.isString && fields["overview"].asJsonPrimitive.isString)
        require(fields["genreIds"].isJsonArray)
        val movie = gson.fromJson(json, Movie::class.java)
        require(movie.id > 0 && movie.voteAverage.isFinite() && movie.popularity.isFinite())
        movie
    } catch (_: Exception) {
        null
    }

    fun preferenceToJson(profile: UserPreferenceProfile): String = gson.toJson(profile)

    fun jsonToPreference(json: String): UserPreferenceProfile? = try {
        val fields = JsonParser.parseString(json).asJsonObject
        require(listOf("selectedGenreIds", "mood", "era", "runtimePreference", "minRatingThreshold", "updatedAtTimestamp").all { fields.has(it) && !fields[it].isJsonNull })
        require(fields["selectedGenreIds"].isJsonArray)
        require(fields["mood"].asString in com.example.madverse.core.model.MovieMood.entries.map { it.name })
        require(fields["era"].asString in com.example.madverse.core.model.MovieEra.entries.map { it.name })
        require(fields["runtimePreference"].asString in com.example.madverse.core.model.RuntimePreference.entries.map { it.name })
        val profile = gson.fromJson(json, UserPreferenceProfile::class.java)
        require(profile.minRatingThreshold.isFinite() && profile.minRatingThreshold in 0.0..10.0)
        profile
    } catch (_: Exception) {
        null
    }
}
