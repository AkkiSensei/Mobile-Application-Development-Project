package com.example.madverse.core.model

/**
 * Real-time authentication state representation.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object Guest : AuthState
    data class Authenticated(val user: AuthUser) : AuthState
}
