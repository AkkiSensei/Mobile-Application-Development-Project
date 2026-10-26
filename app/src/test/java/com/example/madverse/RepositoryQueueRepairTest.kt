package com.example.madverse

import com.example.madverse.core.data.repository.*
import com.example.madverse.core.data.repository.impl.*
import com.example.madverse.core.data.sync.SyncOperation
import com.example.madverse.core.database.DirectTransactionRunner
import com.example.madverse.core.database.dao.NoOpPendingSyncDao
import com.example.madverse.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RepositoryQueueRepairTest {
    @Test fun allLocalWritersQueueImmutableIntentWithoutBypassingOlderActions() = runTest {
        val queue = NoOpPendingSyncDao()
        var wakes = 0
        val cloud = object : CloudSyncService by NoOpCloudSyncService() {
            override suspend fun pushFavorite(userId: String, movie: Movie, enqueueOnFailure: Boolean): Result<Unit> = error("Must use queue")
            override suspend fun deleteFavorite(userId: String, movieId: Int, enqueueOnFailure: Boolean): Result<Unit> = error("Must use queue")
            override suspend fun pushWatchlist(userId: String, movie: Movie, enqueueOnFailure: Boolean): Result<Unit> = error("Must use queue")
            override suspend fun deleteWatchlist(userId: String, movieId: Int, enqueueOnFailure: Boolean): Result<Unit> = error("Must use queue")
            override suspend fun pushPreferences(userId: String, profile: UserPreferenceProfile, enqueueOnFailure: Boolean): Result<Unit> = error("Must use queue")
            override suspend fun processPendingSyncActions(userId: String) { wakes++ }
        }
        val session = object : SessionManager {
            override val currentUserId = "A"
            override val currentUserIdFlow = MutableStateFlow("A")
            override val authState = MutableStateFlow<AuthState>(AuthState.Guest)
            override val isGuest = false
            override suspend fun hasGuestData() = false
        }
        val favorites = FavoritesRepositoryImpl(ReliabilityAndOfflineFoundationsTest.TestFavoriteDao(), session, cloud, queue, DirectTransactionRunner())
        val watchlist = WatchlistRepositoryImpl(ReliabilityAndOfflineFoundationsTest.TestWatchlistDao(), session, cloud, queue, DirectTransactionRunner())
        val quiz = QuizRepositoryImpl(ReliabilityAndOfflineFoundationsTest.TestUserPreferenceDao(), session, cloud, queue, DirectTransactionRunner())
        val movie = Movie(1, "first", "", null, null, null, 7.0, 0, emptyList(), 0.0)
        favorites.addToFavorites(movie)
        favorites.addToFavorites(movie.copy(title = "second"))
        favorites.removeFromFavorites(1)
        watchlist.addToWatchlist(movie)
        watchlist.removeFromWatchlist(1)
        quiz.saveUserPreferenceProfile(UserPreferenceProfile(minRatingThreshold = 8.0))
        quiz.clearUserPreferenceProfile()
        val rows = queue.getPendingActionsForUser("A")
        assertEquals(7, wakes)
        assertEquals(7, rows.size)
        val decoded = rows.map { requireNotNull(SyncOperation.decode(it)) }
        assertEquals("first", decoded[0].movie!!.title)
        assertEquals("second", decoded[1].movie!!.title)
        assertTrue(decoded[2].delete)
        assertEquals("watchlist", decoded[3].collection)
        assertTrue(decoded[4].delete)
        assertEquals(8.0, decoded[5].preference!!.minRatingThreshold, 0.0)
        assertTrue(decoded[6].delete)
    }
}
