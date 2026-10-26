package com.example.madverse

import com.example.madverse.core.data.sync.SyncSnapshotReconciler
import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.DirectTransactionRunner
import com.example.madverse.core.database.dao.NoOpPendingSyncDao
import com.example.madverse.core.database.entity.*
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SnapshotReliabilityRepairTest {
    private val favorites = ReliabilityAndOfflineFoundationsTest.TestFavoriteDao()
    private val watchlist = ReliabilityAndOfflineFoundationsTest.TestWatchlistDao()
    private val preferences = ReliabilityAndOfflineFoundationsTest.TestUserPreferenceDao()
    private val pending = NoOpPendingSyncDao()
    private fun reconciler(transactions: DatabaseTransactionRunner = DirectTransactionRunner()) =
        SyncSnapshotReconciler(favorites, watchlist, preferences, pending, transactions)
    private fun favorite(id: Int, title: String = "local") = FavoriteEntity(id, "A", title, null, null, null, 7.0, null, 1)
    private fun watch(id: Int, title: String = "local") = WatchlistEntity(id, "A", title, null, null, null, 7.0, null, 1)
    private suspend fun protect(id: Int, type: String, operation: String = "UPSERT") {
        pending.insertAction(PendingSyncEntity(userId = "A", actionType = operation, entityType = type, itemId = id.toString()))
    }

    @Test fun emptySnapshotsKeepPendingAdditionsAndRemoveOnlyUnrelatedRows() = runTest {
        favorites.insertFavorite(favorite(1)); favorites.insertFavorite(favorite(2))
        favorites.insertFavorite(favorite(3).copy(userId = "B"))
        watchlist.insertMovie(watch(1)); watchlist.insertMovie(watch(2))
        protect(1, "FAVORITE"); protect(1, "WATCHLIST")
        reconciler().favorites("A", emptyList()) { true }
        reconciler().watchlist("A", emptyList()) { true }
        assertEquals(listOf(1), favorites.getAllFavoritesOnce("A").map { it.movieId })
        assertEquals(listOf(1), watchlist.getAllWatchlistOnce("A").map { it.movieId })
        assertNotNull(favorites.getFavoriteById(3, "B"))
    }

    @Test fun nonemptySnapshotsCannotOverwritePendingUpdatesOrResurrectDeletes() = runTest {
        favorites.insertFavorite(favorite(1)); watchlist.insertMovie(watch(1))
        for (type in listOf("FAVORITE", "WATCHLIST")) { protect(1, type); protect(2, type, "DELETE") }
        reconciler().favorites("A", listOf(favorite(1, "stale"), favorite(2), favorite(3, "remote"))) { true }
        reconciler().watchlist("A", listOf(watch(1, "stale"), watch(2), watch(3, "remote"))) { true }
        assertEquals("local", favorites.getFavoriteById(1, "A")!!.title)
        assertEquals("local", watchlist.getWatchlistMovieById(1, "A")!!.title)
        assertNull(favorites.getFavoriteById(2, "A")); assertNull(watchlist.getWatchlistMovieById(2, "A"))
        assertEquals("remote", favorites.getFavoriteById(3, "A")!!.title)
        assertEquals("remote", watchlist.getWatchlistMovieById(3, "A")!!.title)
    }

    @Test fun pendingPreferenceSaveAndDeleteBeatRemoteSnapshots() = runTest {
        val local = UserPreferenceEntity.fromDomain(UserPreferenceProfile(minRatingThreshold = 9.0), "A")
        preferences.saveUserPreferenceProfile(local)
        val id = pending.insertAction(PendingSyncEntity(userId = "A", actionType = "UPSERT", entityType = "PREFERENCE", itemId = "current"))
        reconciler().preferences("A", local.copy(minRatingThreshold = 2.0)) { true }
        reconciler().preferences("A", null) { true }
        assertEquals(local, preferences.getUserPreferenceProfileOnce("A"))
        pending.deleteActionById(id)
        pending.insertAction(PendingSyncEntity(userId = "A", actionType = "DELETE", entityType = "PREFERENCE", itemId = "current"))
        preferences.clearUserPreferenceProfile("A")
        reconciler().preferences("A", local) { true }
        assertNull(preferences.getUserPreferenceProfileOnce("A"))
    }

    @Test fun authoritativePreferenceAbsenceClearsOnlyUnprotectedAccount() = runTest {
        val local = UserPreferenceEntity.fromDomain(UserPreferenceProfile(), "A")
        preferences.saveUserPreferenceProfile(local)
        preferences.saveUserPreferenceProfile(local.copy(userId = "B"))
        reconciler().preferences("A", null) { true }
        assertNull(preferences.getUserPreferenceProfileOnce("A"))
        assertNotNull(preferences.getUserPreferenceProfileOnce("B"))
    }

    @Test fun pendingIntentInsertedBeforeTransactionStartsIsProtected() = runTest {
        favorites.insertFavorite(favorite(1))
        val runner = object : DatabaseTransactionRunner {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R {
                protect(1, "FAVORITE") // Simulate a local transaction completing before reconciliation acquires Room.
                return block()
            }
        }
        reconciler(runner).favorites("A", emptyList()) { true }
        assertNotNull(favorites.getFavoriteById(1, "A"))
    }

    @Test fun staleSessionCannotApplySnapshot() = runTest {
        favorites.insertFavorite(favorite(1))
        try { reconciler().favorites("A", emptyList()) { false }; fail("Expected stale session cancellation") }
        catch (_: CancellationException) { }
        assertNotNull(favorites.getFavoriteById(1, "A"))
    }
}
