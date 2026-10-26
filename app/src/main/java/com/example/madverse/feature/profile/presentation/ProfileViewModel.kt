package com.example.madverse.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.data.account.AccountDeletionJournal
import com.example.madverse.core.data.account.DeletionStage
import com.example.madverse.core.data.account.AccountDeletionCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.madverse.core.data.account.InMemoryAccountDeletionJournal
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
    private val cloudSyncService: CloudSyncService,
    private val accountDeletionJournal: AccountDeletionJournal = InMemoryAccountDeletionJournal(),
    @param:com.example.madverse.core.common.dispatchers.IoDispatcher private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private var deletionJob: Job? = null

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeProfileData()
    }

    private fun observeProfileData() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                val pending = withContext(ioDispatcher) {
                    if (deletionJob?.isActive != true) {
                        // Only explicit remote confirmation permits cleanup after a restart/null session.
                        for (entry in accountDeletionJournal.pendingEntries()) {
                            if (entry.stage == DeletionStage.REMOTE_AUTH_CONFIRMED || entry.stage == DeletionStage.ACCOUNT_DATA_CLEANED) {
                                AccountDeletionCoordinator(authRepository, cloudSyncService, accountDeletionJournal).delete(entry.userId)
                            }
                        }
                    }
                    accountDeletionJournal.pendingEntries()
                }
                if (pending.isNotEmpty()) {
                    _uiState.update { it.copy(errorMessage = "Account deletion is unfinished. Local account data is preserved and sync is paused for that account. Sign in to the same account and retry deletion; if sign-in is unavailable, request administrator verification. Signing out does not prove deletion.") }
                }
                when (authState) {
                    is AuthState.Authenticated -> {
                        _uiState.update {
                            it.copy(
                                authUser = authState.user,
                                isGuest = false,
                                editedName = authState.user.displayName ?: "",
                                isLoading = false
                            )
                        }
                    }
                    is AuthState.Guest -> {
                        _uiState.update {
                            it.copy(
                                authUser = null,
                                isGuest = true,
                                editedName = "",
                                isLoading = false
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
        if (deletionJob?.isActive == true) return
        deletionJob = viewModelScope.launch {
            _uiState.update { it.copy(showDeleteAccountDialog = false, isLoading = true, errorMessage = null) }
            try {
                val result = withContext(ioDispatcher) {
                    val uid = authRepository.currentUserId
                    AccountDeletionCoordinator(authRepository, cloudSyncService, accountDeletionJournal).delete(uid)
                }
                result.onSuccess { onDeleted() }.onFailure { error ->
                    val recentLogin = error is FirebaseAuthRecentLoginRequiredException || error.message?.contains("recent", ignoreCase = true) == true
                    val message = if (recentLogin) {
                        "This operation requires recent authentication. Sign out, sign in to the same account, and retry deletion. Cloud saved data may already be removed; local data and the recovery journal are preserved."
                    } else {
                        "Account deletion is not confirmed complete. Local account data and the recovery journal are preserved. Sign in to the same account to retry; if sign-in is unavailable, request administrator verification."
                    }
                    _uiState.update { it.copy(errorMessage = message) }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
