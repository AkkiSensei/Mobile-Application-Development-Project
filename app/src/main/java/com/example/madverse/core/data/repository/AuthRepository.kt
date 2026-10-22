package com.example.madverse.core.data.repository

import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication repository contract decoupling MADVerse domain and UI
 * from concrete Firebase Authentication implementations.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUser: AuthUser?
    val currentUserId: String
    val currentUserIdFlow: Flow<String>

    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String? = null): Result<AuthUser>
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun sendEmailVerification(): Result<Unit>
    suspend fun updateDisplayName(displayName: String): Result<Unit>
    suspend fun reloadUser(): Result<AuthUser?>
    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
}
