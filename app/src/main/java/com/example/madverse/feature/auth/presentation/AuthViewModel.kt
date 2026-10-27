package com.example.madverse.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode {
    WELCOME,
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.WELCOME,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val displayName: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val showMergeDialog: Boolean = false,
    val isGoogleSignInLoading: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val cloudSyncService: CloudSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: AuthMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null, infoMessage = null) }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun onDisplayNameChanged(value: String) {
        _uiState.update { it.copy(displayName = value, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun signInWithEmail(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank() || !emailRegex.matches(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.signInWithEmail(email, password)
            result.onSuccess {
                handleAuthSuccess(onSuccess)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Sign-in failed. Please check your credentials."
                    )
                }
            }
        }
    }

    fun signUpWithEmail(onSuccess: () -> Unit) {
        val displayName = _uiState.value.displayName.trim()
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val confirm = _uiState.value.confirmPassword

        if (displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name.") }
            return
        }
        if (email.isBlank() || !emailRegex.matches(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }
        if (password != confirm) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.signUpWithEmail(email, password, displayName)
            result.onSuccess {
                handleAuthSuccess(onSuccess)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Registration failed. An account with this email may already exist."
                    )
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleSignInLoading = true, errorMessage = null) }
            val result = authRepository.signInWithGoogle(idToken)
            result.onSuccess {
                handleAuthSuccess(onSuccess)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGoogleSignInLoading = false,
                        errorMessage = error.localizedMessage ?: "Google sign-in failed."
                    )
                }
            }
        }
    }

    fun sendPasswordResetEmail() {
        val email = _uiState.value.email.trim()
        if (email.isBlank() || !emailRegex.matches(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.sendPasswordResetEmail(email)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Password reset email sent to $email. Please check your inbox."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Could not send reset email. Verify your email address."
                    )
                }
            }
        }
    }

    private suspend fun handleAuthSuccess(onSuccess: () -> Unit) {
        val hasGuest = sessionManager.hasGuestData()
        if (hasGuest) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isGoogleSignInLoading = false,
                    showMergeDialog = true
                )
            }
        } else {
            _uiState.update { it.copy(isLoading = false, isGoogleSignInLoading = false) }
            onSuccess()
        }
    }

    fun onMergeConfirmed(onFinish: () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, showMergeDialog = true, errorMessage = null) }
        viewModelScope.launch {
            val result = try {
                cloudSyncService.mergeGuestDataIntoAccount(authRepository.currentUserId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, showMergeDialog = false) }
                onFinish()
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "Could not merge guest data. Your guest data is still available. Retry or keep it separate."
                ) }
            }
        }
    }

    fun onMergeDismissed(onFinish: () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(showMergeDialog = false, errorMessage = null) }
        onFinish()
    }
}
