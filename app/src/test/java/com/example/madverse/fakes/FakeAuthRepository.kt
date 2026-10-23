package com.example.madverse.fakes

import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeAuthRepository(
    initialState: AuthState = AuthState.Guest
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(initialState)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    override val currentUserId: String
        get() = currentUser?.uid ?: "guest"

    override val currentUserIdFlow: Flow<String> = authState.map { state ->
        when (state) {
            is AuthState.Authenticated -> state.user.uid
            else -> "guest"
        }
    }

    var shouldFailNextOperation: Boolean = false
    var failureException: Exception = Exception("Simulated authentication error")
    var emailVerificationSent: Boolean = false
    var passwordResetEmailsSent = mutableListOf<String>()

    fun setAuthenticated(user: AuthUser) {
        _authState.value = AuthState.Authenticated(user)
    }

    fun setGuest() {
        _authState.value = AuthState.Guest
    }

    fun setLoading() {
        _authState.value = AuthState.Loading
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        if (shouldFailNextOperation) return Result.failure(failureException)
        val user = AuthUser(
            uid = "user_${email.hashCode()}",
            email = email,
            displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
            photoUrl = null,
            isEmailVerified = true,
            providerId = "password"
        )
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?
    ): Result<AuthUser> {
        if (shouldFailNextOperation) return Result.failure(failureException)
        emailVerificationSent = true
        val user = AuthUser(
            uid = "user_${email.hashCode()}",
            email = email,
            displayName = displayName ?: email.substringBefore("@"),
            photoUrl = null,
            isEmailVerified = false,
            providerId = "password"
        )
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> {
        if (shouldFailNextOperation) return Result.failure(failureException)
        val user = AuthUser(
            uid = "google_user_123",
            email = "user@gmail.com",
            displayName = "Google User",
            photoUrl = "https://lh3.googleusercontent.com/photo.jpg",
            isEmailVerified = true,
            providerId = "google.com"
        )
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        if (shouldFailNextOperation) return Result.failure(failureException)
        passwordResetEmailsSent.add(email)
        return Result.success(Unit)
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        if (shouldFailNextOperation) return Result.failure(failureException)
        emailVerificationSent = true
        return Result.success(Unit)
    }

    override suspend fun updateDisplayName(displayName: String): Result<Unit> {
        if (shouldFailNextOperation) return Result.failure(failureException)
        val current = currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val updated = current.copy(displayName = displayName)
        _authState.value = AuthState.Authenticated(updated)
        return Result.success(Unit)
    }

    override suspend fun reloadUser(): Result<AuthUser?> {
        return Result.success(currentUser)
    }

    override suspend fun signOut() {
        _authState.value = AuthState.Guest
    }

    override suspend fun deleteAccount(): Result<Unit> {
        if (shouldFailNextOperation) return Result.failure(failureException)
        _authState.value = AuthState.Guest
        return Result.success(Unit)
    }
}
