package com.example.madverse.core.model

/**
 * Domain representation of cast and crew credits.
 */
data class CastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String?,
    val order: Int
)
