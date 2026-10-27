package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.common.dispatchers.IoDispatcher
import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.SessionManager
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.model.AuthState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SessionManagerImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val favoriteDao: FavoriteDao,
    private val watchlistDao: WatchlistDao,
    private val userPreferenceDao: UserPreferenceDao,
    private val syncServiceProvider: Provider<CloudSyncService>,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SessionManager {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        scope.launch {
            authRepository.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        syncServiceProvider.get().startUserSession(state.user.uid)
                    }
                    else -> {
                        syncServiceProvider.get().stopUserSession()
                    }
                }
            }
        }
    }

    override val currentUserId: String
        get() = authRepository.currentUserId

    override val currentUserIdFlow: Flow<String>
        get() = authRepository.currentUserIdFlow

    override val authState: StateFlow<AuthState>
        get() = authRepository.authState

    override val isGuest: Boolean
        get() = authRepository.currentUser == null

    override suspend fun hasGuestData(): Boolean {
        val favCount = favoriteDao.getFavoritesCountOnce("guest")
        if (favCount > 0) return true
        val watchCount = watchlistDao.getWatchlistCountOnce("guest")
        if (watchCount > 0) return true
        val pref = userPreferenceDao.getUserPreferenceProfileOnce("guest")
        // Row existence matches QuizRepository.hasSavedProfile, including unrestricted answers.
        return pref != null
    }
}
