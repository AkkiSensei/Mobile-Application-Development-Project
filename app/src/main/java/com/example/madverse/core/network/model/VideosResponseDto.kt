package com.example.madverse.core.network.model

import com.example.madverse.core.model.VideoTrailer
import com.google.gson.annotations.SerializedName

data class VideosResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("results") val results: List<VideoDto>?
)

data class VideoDto(
    @SerializedName("id") val id: String,
    @SerializedName("key") val key: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("site") val site: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("official") val official: Boolean?
) {
    fun toDomain(): VideoTrailer? {
        val videoKey = key ?: return null
        return VideoTrailer(
            id = id,
            key = videoKey,
            name = name ?: "Trailer",
            site = site ?: "YouTube",
            type = type ?: "Trailer",
            isOfficial = official ?: false
        )
    }
}
