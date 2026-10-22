package com.example.madverse.core.network.model

import com.example.madverse.core.common.network.ApiConstants
import com.example.madverse.core.model.CastMember
import com.google.gson.annotations.SerializedName

data class CreditsDto(
    @SerializedName("id") val id: Int,
    @SerializedName("cast") val cast: List<CastDto>?
)

data class CastDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("character") val character: String?,
    @SerializedName("profile_path") val profilePath: String?,
    @SerializedName("order") val order: Int?
) {
    fun toDomain(): CastMember {
        return CastMember(
            id = id,
            name = name ?: "Unknown",
            character = character ?: "",
            profileUrl = profilePath?.takeIf(String::isNotBlank)?.let { "${ApiConstants.TMDB_PROFILE_BASE_W185}$it" },
            order = order ?: 999
        )
    }
}
