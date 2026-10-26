package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.common.dispatchers.IoDispatcher
import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FirebaseAuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = firebaseAuth.currentUser?.toAuthUser()

    override val currentUserId: String
        get() = currentUser?.uid ?: "guest"

    override val currentUserIdFlow: Flow<String> = authState.map { state ->
        when (state) {
            is AuthState.Authenticated -> state.user.uid
            else -> "guest"
        }
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user != null) {
            _authState.value = AuthState.Authenticated(user.toAuthUser())
        } else {
            _authState.value = AuthState.Guest
        }
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
        val initialUser = firebaseAuth.currentUser
        _authState.value = if (initialUser != null) {
            AuthState.Authenticated(initialUser.toAuthUser())
        } else {
            AuthState.Guest
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> =
        withContext(ioDispatcher) {
            runCatching {
                val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).awaitTask()
                val user = result.user?.toAuthUser() ?: error("User not returned from sign-in")
                _authState.value = AuthState.Authenticated(user)
                user
            }
        }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?
    ): Result<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).awaitTask()
            val user = result.user ?: error("User not created")

            if (!displayName.isNullOrBlank()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(profileUpdates).awaitTask()
            }

            // Optionally send email verification
            runCatching { user.sendEmailVerification().awaitTask() }

            val authUser = user.toAuthUser()
            _authState.value = AuthState.Authenticated(authUser)
            authUser
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> =
        withContext(ioDispatcher) {
            runCatching {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = firebaseAuth.signInWithCredential(credential).awaitTask()
                val user = result.user?.toAuthUser() ?: error("User not returned from Google sign-in")
                _authState.value = AuthState.Authenticated(user)
                user
            }
        }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                firebaseAuth.sendPasswordResetEmail(email.trim()).awaitTask()
                Unit
            }
        }

    override suspend fun sendEmailVerification(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val user = firebaseAuth.currentUser ?: error("No authenticated user to verify")
            user.sendEmailVerification().awaitTask()
            Unit
        }
    }

    override suspend fun updateDisplayName(displayName: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val user = firebaseAuth.currentUser ?: error("No authenticated user to update")
                val updates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(updates).awaitTask()
                user.reload().awaitTask()
                _authState.value = AuthState.Authenticated(user.toAuthUser())
                Unit
            }
        }

    override suspend fun reloadUser(): Result<AuthUser?> = withContext(ioDispatcher) {
        runCatching {
            val user = firebaseAuth.currentUser
            if (user != null) {
                user.reload().awaitTask()
                val updated = user.toAuthUser()
                _authState.value = AuthState.Authenticated(updated)
                updated
            } else {
                _authState.value = AuthState.Guest
                null
            }
        }
    }

    override suspend fun signOut() {
        withContext(ioDispatcher) {
            firebaseAuth.signOut()
            _authState.value = AuthState.Guest
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = deleteAccountForUser(currentUserId)

    override suspend fun deleteAccountForUser(expectedUserId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val user = firebaseAuth.currentUser ?: error("No authenticated user to delete")
            check(user.uid == expectedUserId) { "Deletion target is not signed in" }
            withTimeout(30000) { user.delete().awaitTask() }
            if (firebaseAuth.currentUser == null) _authState.value = AuthState.Guest
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Identity deletion outcome is unconfirmed. Sign in to the same account to retry or request administrator verification.", e))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
}

/**
 * Extension mapping FirebaseUser to domain AuthUser.
 */
internal fun FirebaseUser.toAuthUser(): AuthUser {
    val primaryProvider = providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "firebase"
    return AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
        isEmailVerified = isEmailVerified,
        isAnonymous = isAnonymous,
        providerId = primaryProvider
    )
}

/**
 * Cancellation-aware coroutine converter for Google Tasks API.
 */
internal suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }
