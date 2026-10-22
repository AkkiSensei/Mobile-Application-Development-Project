package com.example.madverse.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import com.example.madverse.core.model.SyncState
import com.example.madverse.core.model.UserPreferenceProfile
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val authUser: AuthUser? = null,
    val isGuest: Boolean = true,
    val favoritesCount: Int = 0,
    val watchlistCount: Int = 0,
    val preferences: UserPreferenceProfile = UserPreferenceProfile(),
    val hasSavedPreferences: Boolean = false,
    val syncState: SyncState = SyncState.Idle,
    val isEditingName: Boolean = false,
    val editedName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val showSignOutDialog: Boolean = false,
    val showDeleteAccountDialog: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val favoritesRepository: FavoritesRepository,
    private val watchlistRepository: WatchlistRepository,
    private val quizRepository: QuizRepository,
    private val cloudSyncService: CloudSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeProfileData()
    }

    private fun observeProfileData() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.Authenticated -> {
                        _uiState.update {
                            it.copy(
                                authUser = authState.user,
                                isGuest = false,
                                editedName = authState.user.displayName ?: ""
                            )
                        }
                    }
                    is AuthState.Guest -> {
                        _uiState.update {
                            it.copy(
                                authUser = null,
                                isGuest = true,
                                editedName = ""
                            )
                        }
                    }
                    is AuthState.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }

        viewModelScope.launch {
            favoritesRepository.getFavoriteMovies().collect { favs ->
                _uiState.update { it.copy(favoritesCount = favs.size) }
            }
        }

        viewModelScope.launch {
            watchlistRepository.getWatchlistMovies().collect { watch ->
                _uiState.update { it.copy(watchlistCount = watch.size) }
            }
        }

        viewModelScope.launch {
            quizRepository.getUserPreferenceProfile().collect { pref ->
                _uiState.update { it.copy(preferences = pref) }
            }
        }

        viewModelScope.launch {
            quizRepository.hasSavedProfile().collect { hasSaved ->
                _uiState.update { it.copy(hasSavedPreferences = hasSaved) }
            }
        }

        viewModelScope.launch {
            cloudSyncService.syncState.collect { sync ->
                _uiState.update { it.copy(syncState = sync) }
            }
        }
    }

    fun startEditingName() {
        _uiState.update {
            it.copy(
                isEditingName = true,
                editedName = it.authUser?.displayName ?: ""
            )
        }
    }

    fun onEditedNameChanged(name: String) {
        _uiState.update { it.copy(editedName = name) }
    }

    fun cancelEditingName() {
        _uiState.update { it.copy(isEditingName = false) }
    }

    fun saveDisplayName() {
        val newName = _uiState.value.editedName.trim()
        if (newName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name cannot be empty.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.updateDisplayName(newName)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditingName = false,
                        infoMessage = "Display name updated successfully."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Failed to update display name."
                    )
                }
            }
        }
    }

    fun sendEmailVerification() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.sendEmailVerification()
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Verification email sent. Please check your inbox."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Failed to send verification email."
                    )
                }
            }
        }
    }

    fun refreshUser() {
        viewModelScope.launch {
            authRepository.reloadUser()
        }
    }

    fun triggerManualSync() {
        val uid = authRepository.currentUserId
        if (uid != "guest") {
            viewModelScope.launch {
                cloudSyncService.processPendingSyncActions(uid)
                cloudSyncService.syncAll(uid)
            }
        }
    }

    fun showSignOutDialog(show: Boolean) {
        _uiState.update { it.copy(showSignOutDialog = show) }
    }

    fun showDeleteAccountDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteAccountDialog = show) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(showSignOutDialog = false, isLoading = true) }
            authRepository.signOut()
            _uiState.update { it.copy(isLoading = false) }
            onSignedOut()
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteAccountDialog = false, isLoading = true, errorMessage = null) }
            val uid = authRepository.currentUserId
            if (uid.isNotBlank() && uid != "guest") {
                // Delete cloud and local data
                cloudSyncService.deleteAllUserData(uid)
                val result = authRepository.deleteAccount()
                result.onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    onDeleted()
                }.onFailure { error ->
                    val isRecentLogin = error is FirebaseAuthRecentLoginRequiredException ||
                        error.message?.contains("recent", ignoreCase = true) == true ||
                        error.javaClass.simpleName.contains("RecentLogin", ignoreCase = true)
                    val message = if (isRecentLogin) {
                        "This operation is sensitive and requires recent authentication. Please sign out, log back in, and try deleting your account again."
                    } else {
                        error.localizedMessage ?: "Failed to delete account. Please try again."
                    }
                    _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                }
            }
        }
    }
}
