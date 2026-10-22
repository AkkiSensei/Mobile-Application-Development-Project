package com.example.madverse.core.model

/**
 * Domain representation of an authenticated Firebase user.
 * Decouples Compose UI and repositories from the concrete FirebaseUser SDK type.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isEmailVerified: Boolean,
    val isAnonymous: Boolean = false,
    val providerId: String = "firebase"
) {
    /**
     * Initials for fallback avatars when photoUrl is null or unavailable.
     */
    val initials: String
        get() {
            val name = displayName?.trim()
            if (!name.isNullOrBlank()) {
                val parts = name.split("\\s+".toRegex()).filter { it.isNotBlank() }
                return if (parts.size >= 2) {
                    "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
                } else {
                    parts[0].take(2).uppercase()
                }
            }
            val mail = email?.trim()
            if (!mail.isNullOrBlank()) {
                return mail.take(2).uppercase()
            }
            return "MV"
        }
}
