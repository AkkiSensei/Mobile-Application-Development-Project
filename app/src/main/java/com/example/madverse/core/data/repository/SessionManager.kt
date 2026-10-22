package com.example.madverse.core.data.repository

import com.example.madverse.core.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages active user identity context and guest status across repository and UI layers.
 */
interface SessionManager {
    val currentUserId: String
    val currentUserIdFlow: Flow<String>
    val authState: StateFlow<AuthState>
    val isGuest: Boolean

    suspend fun hasGuestData(): Boolean
}

/**
 * Lightweight default session manager used in isolated unit test harnesses.
 */
class DefaultSessionManager(
    override val currentUserId: String = "guest"
) : SessionManager {
    override val currentUserIdFlow: Flow<String> = kotlinx.coroutines.flow.flowOf(currentUserId)
    override val authState: StateFlow<AuthState> = kotlinx.coroutines.flow.MutableStateFlow(AuthState.Guest)
    override val isGuest: Boolean = (currentUserId == "guest")

    override suspend fun hasGuestData(): Boolean = false
}
